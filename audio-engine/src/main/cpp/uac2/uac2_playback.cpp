#include "uac2_playback.h"

#include "../audio_log.h"
#include "../spsc_ring_buffer.h"
#include "android_usb_fs.h"
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

Uac2Playback& Uac2Playback::instance() {
    static Uac2Playback playback;
    return playback;
}

PlaybackStatus Uac2Playback::open(int usb_fd, const Uac2AltSetting& alt, bool java_interface_claimed) {
    std::lock_guard<std::mutex> lock(mutex_);
    close();

    underrun_frames_.store(0);

    if (usb_fd < 0 || !alt.format.valid) {
        PlaybackStatus status;
        status.error = "invalid usb fd or alt";
        return status;
    }

    java_interface_claimed_ = java_interface_claimed;
    const UsbIoStatus io = java_interface_claimed
        ? setAltOnClaimedInterface(usb_fd, alt)
        : claimAndSetAlt(usb_fd, alt);
    if (!io.ok) {
        PlaybackStatus status;
        status.error = io.error;
        return status;
    }

    usb_fd_ = usb_fd;
    alt_ = alt;
    channel_count_ = alt.format.channels;
    sample_rate_ = static_cast<int32_t>(alt.format.sample_rate_hz);
    ring_ = std::make_unique<openmultitrack::SpscRingBuffer>(48'000, channel_count_);
    urb_layout_ = layoutForAlt(alt, false);

    std::promise<bool> init_promise;
    std::future<bool> init_future = init_promise.get_future();
    running_.store(true);
    worker_ = std::thread(&Uac2Playback::workerLoop, this, std::move(init_promise));

    if (init_future.wait_for(std::chrono::seconds(2)) != std::future_status::ready ||
        !init_future.get()) {
        running_.store(false);
        if (worker_.joinable()) {
            worker_.join();
        }
        close();
        PlaybackStatus status;
        status.error = "isoch URB submit failed";
        return status;
    }

    OMT_LOGI("uac2 playback open %dch @ %dHz ep=0x%02x pkt=%u",
             channel_count_,
             sample_rate_,
             alt.endpoint_address,
             alt.max_packet_size);

    PlaybackStatus status;
    status.running = true;
    status.channel_count = channel_count_;
    status.sample_rate = sample_rate_;
    return status;
}

void Uac2Playback::close() {
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
    urb_layout_ = {};
}

size_t Uac2Playback::writeFrames(const float* src, size_t frame_count) {
    if (ring_ == nullptr) return 0;
    const int32_t channels = ring_->channelCount();
    size_t accepted = 0;
    std::vector<float> frame(static_cast<size_t>(channels));
    for (size_t f = 0; f < frame_count; ++f) {
        for (int32_t c = 0; c < channels; ++c) {
            frame[static_cast<size_t>(c)] = src[f * static_cast<size_t>(channels) + static_cast<size_t>(c)];
        }
        if (!ring_->pushFrame(frame.data())) {
            break;
        }
        ++accepted;
    }
    return accepted;
}

bool Uac2Playback::fillUrbBuffer(uint8_t* dest, size_t byte_capacity, size_t* frames_written) {
    const size_t bpf = bytesPerFrame(alt_.format);
    if (bpf == 0) return false;

    const size_t max_frames = byte_capacity / bpf;
    std::vector<float> scratch(max_frames * alt_.format.channels);
    const size_t got = ring_ != nullptr ? ring_->popFrames(scratch.data(), max_frames) : 0;

    std::vector<float> frame(alt_.format.channels);
    for (size_t f = 0; f < max_frames; ++f) {
        if (f < got) {
            for (uint8_t c = 0; c < alt_.format.channels; ++c) {
                frame[c] = scratch[f * alt_.format.channels + c];
            }
        } else {
            underrun_frames_.fetch_add(1);
            std::fill(frame.begin(), frame.end(), 0.0f);
        }
        floatFrameToPcm(
            frame.data(),
            dest + f * bpf,
            alt_.format.channels,
            alt_.format.subframe_bytes,
            alt_.format.bit_resolution);
    }
    *frames_written = max_frames;
    return true;
}

void Uac2Playback::workerLoop(std::promise<bool> init_promise) {
    const IsoUrbLayout layout = urb_layout_;

    std::vector<UrbContext> contexts(kNumUrbs);
    bool init_reported = false;

    for (int i = 0; i < kNumUrbs; ++i) {
        usbdevfs_urb* urb = allocIsoUrb(layout, &contexts[static_cast<size_t>(i)]);
        size_t frames = 0;
        fillUrbBuffer(static_cast<uint8_t*>(urb->buffer), static_cast<size_t>(layout.buffer_length), &frames);
        initIsoUrb(urb, layout, alt_.endpoint_address, &contexts[static_cast<size_t>(i)]);
        if (ioctl(usb_fd_, USBDEVFS_SUBMITURB, urb) < 0) {
            OMT_LOGE("uac2 playback SUBMITURB init failed: %s", std::strerror(errno));
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
                OMT_LOGE("uac2 playback REAPURB failed: %s", std::strerror(errno));
            }
            break;
        }

        if (reaped == nullptr) continue;

        size_t frames = 0;
        fillUrbBuffer(static_cast<uint8_t*>(reaped->buffer),
                      static_cast<size_t>(reaped->buffer_length),
                      &frames);

        for (int p = 0; p < reaped->number_of_packets; ++p) {
            reaped->iso_frame_desc[p].actual_length = 0;
            reaped->iso_frame_desc[p].status = 0;
        }
        if (!running_.load()) break;
        if (ioctl(usb_fd_, USBDEVFS_SUBMITURB, reaped) < 0) {
            OMT_LOGE("uac2 playback resubmit failed: %s", std::strerror(errno));
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
