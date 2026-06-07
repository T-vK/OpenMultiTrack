#include "uac2_capture.h"

#include "../audio_log.h"
#include "../spsc_ring_buffer.h"
#include "android_usb_fs.h"
#include "android_usb_io.h"
#include "uac2_format.h"

#include <cerrno>
#include <cstring>
#include <poll.h>
#include <unistd.h>
#include <vector>

namespace openmultitrack::uac2 {

namespace {

constexpr int kNumUrbs = 8;

struct UrbContext {
    std::vector<uint8_t> storage;
    usbdevfs_urb* urb = nullptr;
};

usbdevfs_urb* allocIsoUrb(size_t packet_size, UrbContext* ctx) {
    const size_t total = sizeof(usbdevfs_urb) + sizeof(usbdevfs_iso_packet_desc);
    ctx->storage.resize(total + packet_size);
    ctx->urb = reinterpret_cast<usbdevfs_urb*>(ctx->storage.data());
    ctx->urb->buffer = ctx->storage.data() + total;
    return ctx->urb;
}

}  // namespace

Uac2Capture& Uac2Capture::instance() {
    static Uac2Capture capture;
    return capture;
}

CaptureStatus Uac2Capture::open(int usb_fd, const Uac2AltSetting& alt) {
    std::lock_guard<std::mutex> lock(mutex_);
    close();

    dropped_frames_.store(0);

    if (usb_fd < 0 || !alt.format.valid) {
        CaptureStatus status;
        status.error = "invalid usb fd or alt";
        return status;
    }

    const UsbIoStatus io = claimAndSetAlt(usb_fd, alt);
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

    running_.store(true);
    worker_ = std::thread(&Uac2Capture::workerLoop, this);

    OMT_LOGI("uac2 capture open %dch @ %dHz ep=0x%02x pkt=%u",
             channel_count_,
             sample_rate_,
             alt.endpoint_address,
             alt.max_packet_size);

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

    if (usb_fd_ >= 0 && alt_.format.valid) {
        releaseInterface(usb_fd_, alt_.interface_number);
    }

    ring_.reset();
    usb_fd_ = -1;
    alt_ = {};
    channel_count_ = 0;
    sample_rate_ = 0;
}

size_t Uac2Capture::readFrames(float* dest, size_t max_frames) {
    if (ring_ == nullptr) return 0;
    return ring_->popFrames(dest, max_frames);
}

void Uac2Capture::workerLoop() {
    const size_t packet_size = alt_.max_packet_size;
    const uint8_t channels = alt_.format.channels;
    const uint8_t subframe = alt_.format.subframe_bytes;
    const uint8_t bits = alt_.format.bit_resolution;

    std::vector<UrbContext> contexts(kNumUrbs);
    std::vector<float> frame(static_cast<size_t>(channels));

    for (int i = 0; i < kNumUrbs; ++i) {
        usbdevfs_urb* urb = allocIsoUrb(packet_size, &contexts[static_cast<size_t>(i)]);
        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = alt_.endpoint_address;
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        urb->buffer_length = static_cast<int>(packet_size);
        urb->number_of_packets = 1;
        urb->iso_frame_desc[0].length = static_cast<unsigned int>(packet_size);
        urb->usercontext = &contexts[static_cast<size_t>(i)];
        if (ioctl(usb_fd_, USBDEVFS_SUBMITURB, urb) < 0) {
            OMT_LOGE("uac2 capture SUBMITURB init failed: %s", std::strerror(errno));
            running_.store(false);
            return;
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

        const int actual = reaped->iso_frame_desc[0].actual_length;
        auto* bytes = static_cast<uint8_t*>(reaped->buffer);
        const size_t num_frames = framesInBytes(static_cast<size_t>(actual), alt_.format);
        for (size_t f = 0; f < num_frames; ++f) {
            pcmFrameToFloat(
                bytes + f * bytesPerFrame(alt_.format),
                frame.data(),
                channels,
                subframe,
                bits);
            if (ring_ != nullptr && !ring_->pushFrame(frame.data())) {
                dropped_frames_.fetch_add(1);
            }
        }

        reaped->iso_frame_desc[0].actual_length = 0;
        reaped->iso_frame_desc[0].status = 0;
        if (!running_.load()) break;
        if (ioctl(usb_fd_, USBDEVFS_SUBMITURB, reaped) < 0) {
            OMT_LOGE("uac2 capture resubmit failed: %s", std::strerror(errno));
            break;
        }
    }

    for (auto& ctx : contexts) {
        if (ctx.urb != nullptr) {
            ioctl(usb_fd_, USBDEVFS_DISCARDURB, ctx.urb);
        }
    }
}

}  // namespace openmultitrack::uac2
