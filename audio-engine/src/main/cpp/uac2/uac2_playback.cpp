#include "uac2_playback.h"

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

Uac2Playback& Uac2Playback::instance() {
    static Uac2Playback playback;
    return playback;
}

PlaybackStatus Uac2Playback::open(int usb_fd, const Uac2AltSetting& alt) {
    std::lock_guard<std::mutex> lock(mutex_);
    close();

    underrun_frames_.store(0);

    if (usb_fd < 0 || !alt.format.valid) {
        PlaybackStatus status;
        status.error = "invalid usb fd or alt";
        return status;
    }

    const UsbIoStatus io = claimAndSetAlt(usb_fd, alt);
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

    running_.store(true);
    worker_ = std::thread(&Uac2Playback::workerLoop, this);

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

    if (usb_fd_ >= 0 && alt_.format.valid) {
        releaseInterface(usb_fd_, alt_.interface_number);
    }

    ring_.reset();
    usb_fd_ = -1;
    alt_ = {};
    channel_count_ = 0;
    sample_rate_ = 0;
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

void Uac2Playback::workerLoop() {
    const size_t packet_size = alt_.max_packet_size;

    std::vector<UrbContext> contexts(kNumUrbs);

    for (int i = 0; i < kNumUrbs; ++i) {
        usbdevfs_urb* urb = allocIsoUrb(packet_size, &contexts[static_cast<size_t>(i)]);
        size_t frames = 0;
        fillUrbBuffer(static_cast<uint8_t*>(urb->buffer), packet_size, &frames);
        urb->type = USBDEVFS_URB_TYPE_ISO;
        urb->endpoint = alt_.endpoint_address;
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        urb->buffer_length = static_cast<int>(packet_size);
        urb->number_of_packets = 1;
        urb->iso_frame_desc[0].length = static_cast<unsigned int>(packet_size);
        urb->usercontext = &contexts[static_cast<size_t>(i)];
        if (ioctl(usb_fd_, USBDEVFS_SUBMITURB, urb) < 0) {
            OMT_LOGE("uac2 playback SUBMITURB init failed: %s", std::strerror(errno));
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
                OMT_LOGE("uac2 playback REAPURB failed: %s", std::strerror(errno));
            }
            break;
        }

        if (reaped == nullptr) continue;

        size_t frames = 0;
        fillUrbBuffer(static_cast<uint8_t*>(reaped->buffer),
                      static_cast<size_t>(reaped->buffer_length),
                      &frames);

        reaped->iso_frame_desc[0].actual_length = 0;
        reaped->iso_frame_desc[0].status = 0;
        if (!running_.load()) break;
        if (ioctl(usb_fd_, USBDEVFS_SUBMITURB, reaped) < 0) {
            OMT_LOGE("uac2 playback resubmit failed: %s", std::strerror(errno));
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
