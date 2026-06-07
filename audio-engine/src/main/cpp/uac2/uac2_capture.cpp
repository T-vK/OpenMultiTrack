#include "uac2_capture.h"

#include "../audio_log.h"
#include "../spsc_ring_buffer.h"
#include "android_usb_io.h"
#include "uac2_format.h"
#include "uac2_urb.h"

#include <chrono>
#include <cerrno>
#include <cstring>
#include <future>
#include <poll.h>
#include <unistd.h>
#include <vector>

namespace openmultitrack::uac2 {

namespace {

constexpr int kNumUrbs = 8;

}  // namespace

Uac2Capture& Uac2Capture::instance() {
    static Uac2Capture capture;
    return capture;
}

CaptureStatus Uac2Capture::open(int usb_fd, const Uac2AltSetting& alt, bool java_interface_claimed) {
    std::lock_guard<std::mutex> lock(mutex_);
    close();

    dropped_frames_.store(0);

    if (usb_fd < 0 || !alt.format.valid) {
        CaptureStatus status;
        status.error = "invalid usb fd or alt";
        return status;
    }

    java_interface_claimed_ = java_interface_claimed;
    const UsbIoStatus io = java_interface_claimed
        ? setAltOnClaimedInterface(usb_fd, alt)
        : claimAndSetAlt(usb_fd, alt);
    if (!io.ok) {
        CaptureStatus status;
        status.error = io.error;
        return status;
    }

    usb_fd_ = usb_fd;
    alt_ = alt;
    channel_count_ = alt.format.channels;
    sample_rate_ = static_cast<int32_t>(alt.format.sample_rate_hz);
    ring_ = std::make_unique<openmultitrack::SpscRingBuffer>(48'000, channel_count_);

    bool opened = false;
    for (const bool micro_packets : {false, true}) {
        urb_layout_ = layoutForAlt(alt, true, micro_packets);
        OMT_LOGI("uac2 capture trying layout micro=%d urbs=%d x %d pkt=%u",
                 micro_packets ? 1 : 0,
                 urb_layout_.num_packets,
                 urb_layout_.per_packet_bytes,
                 alt.max_packet_size);

        std::promise<bool> init_promise;
        std::future<bool> init_future = init_promise.get_future();
        running_.store(true);
        worker_ = std::thread(&Uac2Capture::workerLoop, this, std::move(init_promise));

        if (init_future.wait_for(std::chrono::seconds(2)) == std::future_status::ready &&
            init_future.get()) {
            opened = true;
            break;
        }

        running_.store(false);
        if (worker_.joinable()) {
            worker_.join();
        }
    }

    if (!opened) {
        close();
        CaptureStatus status;
        status.error = "isoch URB submit failed";
        return status;
    }

    OMT_LOGI("uac2 capture open %dch @ %dHz ep=0x%02x pkt=%u urbs=%d x %d",
             channel_count_,
             sample_rate_,
             alt.endpoint_address,
             alt.max_packet_size,
             urb_layout_.num_packets,
             urb_layout_.per_packet_bytes);

    CaptureStatus status;
    status.running = true;
    status.channel_count = channel_count_;
    status.sample_rate = sample_rate_;
    return status;
}

void Uac2Capture::close() {
    running_.store(false);
    if (worker_.joinable()) {
        worker_.join();
    }

    if (usb_fd_ >= 0 && alt_.format.valid && !java_interface_claimed_) {
        releaseInterface(usb_fd_, alt_.interface_number);
    }

    ring_.reset();
    usb_fd_ = -1;
    java_interface_claimed_ = false;
    alt_ = {};
    channel_count_ = 0;
    sample_rate_ = 0;
}

size_t Uac2Capture::readFrames(float* dest, size_t max_frames) {
    if (ring_ == nullptr) return 0;
    return ring_->popFrames(dest, max_frames);
}

void Uac2Capture::workerLoop(std::promise<bool> init_promise) {
    const IsoUrbLayout layout = urb_layout_;
    const uint8_t channels = alt_.format.channels;
    const uint8_t subframe = alt_.format.subframe_bytes;
    const uint8_t bits = alt_.format.bit_resolution;

    std::vector<UrbContext> contexts(kNumUrbs);
    std::vector<float> frame(static_cast<size_t>(channels));
    bool init_reported = false;

    for (int i = 0; i < kNumUrbs; ++i) {
        usbdevfs_urb* urb = allocIsoUrb(layout, &contexts[static_cast<size_t>(i)]);
        initIsoUrb(urb, layout, alt_.endpoint_address, &contexts[static_cast<size_t>(i)]);
        if (ioctl(usb_fd_, USBDEVFS_SUBMITURB, urb) < 0) {
            OMT_LOGE("uac2 capture SUBMITURB init failed: %s", std::strerror(errno));
            if (!init_reported) {
                init_promise.set_value(false);
                init_reported = true;
            }
            running_.store(false);
            return;
        }
        if (!init_reported) {
            init_promise.set_value(true);
            init_reported = true;
        }
    }

    while (running_.load()) {
        usbdevfs_urb* reaped = nullptr;
        if (ioctl(usb_fd_, USBDEVFS_REAPURBNDELAY, &reaped) < 0) {
            if (errno == EAGAIN) {
                pollfd pfd{};
                pfd.fd = usb_fd_;
                pfd.events = POLLOUT | POLLIN;
                poll(&pfd, 1, 10);
                continue;
            }
            if (running_.load()) {
                OMT_LOGE("uac2 capture REAPURB failed: %s", std::strerror(errno));
            }
            break;
        }

        if (reaped == nullptr) continue;

        auto* bytes = static_cast<uint8_t*>(reaped->buffer);
        size_t byte_offset = 0;
        for (int p = 0; p < reaped->number_of_packets; ++p) {
            const int actual = reaped->iso_frame_desc[p].actual_length;
            const size_t num_frames = framesInBytes(static_cast<size_t>(actual), alt_.format);
            for (size_t f = 0; f < num_frames; ++f) {
                pcmFrameToFloat(
                    bytes + byte_offset + f * bytesPerFrame(alt_.format),
                    frame.data(),
                    channels,
                    subframe,
                    bits);
                if (ring_ != nullptr && !ring_->pushFrame(frame.data())) {
                    dropped_frames_.fetch_add(1);
                }
            }
            byte_offset += static_cast<size_t>(reaped->iso_frame_desc[p].length);
            reaped->iso_frame_desc[p].actual_length = 0;
            reaped->iso_frame_desc[p].status = 0;
        }

        if (!running_.load()) break;
        if (ioctl(usb_fd_, USBDEVFS_SUBMITURB, reaped) < 0) {
            OMT_LOGE("uac2 capture resubmit failed: %s", std::strerror(errno));
            break;
        }
    }

    if (!init_reported) {
        init_promise.set_value(false);
    }

    for (auto& ctx : contexts) {
        if (ctx.urb != nullptr) {
            ioctl(usb_fd_, USBDEVFS_DISCARDURB, ctx.urb);
        }
    }
}

}  // namespace openmultitrack::uac2
