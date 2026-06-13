#include "uac2_playback.h"

#include "../audio_log.h"
#include "../spsc_ring_buffer.h"
#include "android_usb_fs.h"
#include "android_usb_io.h"
#include "libusb.h"
#include "libusb_session.h"
#include "uac2_format.h"
#include "uac2_urb.h"

#include <chrono>
#include <cerrno>
#include <cstring>
#include <future>
#include <poll.h>
#include <unistd.h>
#include <vector>
#include <algorithm>

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
    closeUnlocked();

    underrun_frames_.store(0);

    if (usb_fd < 0 || !alt.format.valid) {
        PlaybackStatus status;
        status.error = "invalid usb fd or alt";
        return status;
    }

    usb_fd_ = usb_fd;
    alt_ = alt;
    java_interface_claimed_ = java_interface_claimed;
    channel_count_ = alt.format.channels;
    sample_rate_ = static_cast<int32_t>(alt.format.sample_rate_hz);
    ring_ = std::make_unique<openmultitrack::SpscRingBuffer>(48'000, channel_count_);
    urb_layout_ = layoutForAlt(alt, false);

    bool opened = tryOpenUsbdevfs(usb_fd, alt, java_interface_claimed);
    if (!opened) {
        opened = tryOpenLibusb(usb_fd, alt, java_interface_claimed);
    }

    if (!opened) {
        closeUnlocked();
        PlaybackStatus status;
        status.error = "isoch URB submit failed";
        return status;
    }

    OMT_LOGI("uac2 playback open %s %dch @ %dHz ep=0x%02x pkt=%u",
             backend_ == IoBackend::Libusb ? "libusb" : "usbdevfs",
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

bool Uac2Playback::tryOpenLibusb(int usb_fd,
                                 const Uac2AltSetting& alt,
                                 bool java_interface_claimed) {
    libusb_ctx_ = libusbAndroidContext();
    if (libusb_ctx_ == nullptr) {
        return false;
    }

    if (!libusbPrepareStreaming(
            usb_fd, alt, java_interface_claimed, &libusb_handle_, &libusb_owns_interface_)) {
        libusb_handle_ = nullptr;
        return false;
    }

    for (const bool micro_packets : {false, true}) {
        if (ring_ != nullptr) {
            ring_->reset();
        }
        urb_layout_ = layoutForAlt(alt, false, micro_packets);
        OMT_LOGI("uac2 playback libusb trying layout micro=%d urbs=%d x %d",
                 micro_packets ? 1 : 0,
                 urb_layout_.num_packets,
                 urb_layout_.per_packet_bytes);

        std::promise<bool> init_promise;
        std::future<bool> init_future = init_promise.get_future();
        running_.store(true);
        libusb_event_thread_ = std::thread(&Uac2Playback::libusbEventLoop, this);
        worker_ = std::thread(&Uac2Playback::workerLoopLibusb, this, std::move(init_promise));

        if (init_future.wait_for(std::chrono::seconds(2)) == std::future_status::ready &&
            init_future.get()) {
            backend_ = IoBackend::Libusb;
            return true;
        }

        running_.store(false);
        if (worker_.joinable()) {
            worker_.join();
        }
        if (libusb_event_thread_.joinable()) {
            libusb_event_thread_.join();
        }
        freeLibusbTransfers();
    }

    libusbReleaseStreaming(libusb_handle_, alt.interface_number, libusb_owns_interface_);
    libusb_handle_ = nullptr;
    libusb_owns_interface_ = false;
    libusb_ctx_ = nullptr;
    return false;
}

bool Uac2Playback::tryOpenUsbdevfs(int usb_fd,
                                   const Uac2AltSetting& alt,
                                   bool java_interface_claimed) {
    UsbIoStatus io = java_interface_claimed
        ? setAltOnClaimedInterface(usb_fd, alt)
        : claimAndSetAlt(usb_fd, alt);
    if (!io.ok && !java_interface_claimed) {
        OMT_LOGW("uac2 playback usbdevfs claim failed (%s), trying driver detach",
                 io.error.c_str());
        io = claimAndSetAltWithDriverDetach(usb_fd, alt);
    }
    if (!io.ok) {
        OMT_LOGE("uac2 playback usbdevfs setup failed: %s", io.error.c_str());
        return false;
    }

    for (const bool micro_packets : {true, false}) {
        if (ring_ != nullptr) {
            ring_->reset();
        }
        urb_layout_ = layoutForAlt(alt, false, micro_packets);
        OMT_LOGI("uac2 playback usbdevfs trying layout micro=%d urbs=%d x %d",
                 micro_packets ? 1 : 0,
                 urb_layout_.num_packets,
                 urb_layout_.per_packet_bytes);

        std::promise<bool> init_promise;
        std::future<bool> init_future = init_promise.get_future();
        running_.store(true);
        worker_ = std::thread(&Uac2Playback::workerLoopUsbdevfs, this, std::move(init_promise));

        if (init_future.wait_for(std::chrono::seconds(2)) == std::future_status::ready &&
            init_future.get()) {
            backend_ = IoBackend::Usbdevfs;
            return true;
        }

        running_.store(false);
        if (worker_.joinable()) {
            worker_.join();
        }
    }

    if (!java_interface_claimed) {
        releaseInterface(usb_fd, alt.interface_number);
    }
    return false;
}

void Uac2Playback::close() {
    std::lock_guard<std::mutex> lock(mutex_);
    closeUnlocked();
}

void Uac2Playback::closeUnlocked() {
    if (backend_ == IoBackend::None && libusb_handle_ == nullptr && usb_fd_ < 0) {
        return;
    }

    running_.store(false);

    if (backend_ == IoBackend::Libusb && libusb_ctx_ != nullptr) {
        for (libusb_transfer* transfer : libusb_transfers_) {
            if (transfer != nullptr) {
                libusb_cancel_transfer(transfer);
            }
        }
        timeval tv{};
        tv.tv_sec = 0;
        tv.tv_usec = 100'000;
        for (int i = 0; i < 50; ++i) {
            (void)libusb_handle_events_timeout_completed(libusb_ctx_, &tv, nullptr);
        }
    }

    if (worker_.joinable()) {
        worker_.join();
    }
    if (libusb_event_thread_.joinable()) {
        libusb_event_thread_.join();
    }

    for (libusb_transfer* transfer : libusb_transfers_) {
        if (transfer != nullptr) {
            libusb_free_transfer(transfer);
        }
    }
    libusb_transfers_.clear();
    libusb_buffers_.clear();

    if (backend_ == IoBackend::Libusb && libusb_handle_ != nullptr) {
        libusbReleaseStreaming(
            libusb_handle_, alt_.interface_number, libusb_owns_interface_);
        libusb_handle_ = nullptr;
        libusb_owns_interface_ = false;
        libusb_ctx_ = nullptr;
    } else if (backend_ == IoBackend::Usbdevfs && usb_fd_ >= 0 && alt_.format.valid &&
               !java_interface_claimed_) {
        releaseInterface(usb_fd_, alt_.interface_number);
    }

    ring_.reset();
    backend_ = IoBackend::None;
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

void Uac2Playback::freeLibusbTransfers() {
    if (libusb_ctx_ != nullptr) {
        for (libusb_transfer* transfer : libusb_transfers_) {
            if (transfer != nullptr) {
                libusb_cancel_transfer(transfer);
            }
        }
        timeval tv{};
        tv.tv_sec = 0;
        tv.tv_usec = 100'000;
        for (int i = 0; i < 50; ++i) {
            (void)libusb_handle_events_timeout_completed(libusb_ctx_, &tv, nullptr);
        }
    }
    for (libusb_transfer* transfer : libusb_transfers_) {
        if (transfer != nullptr) {
            libusb_free_transfer(transfer);
        }
    }
    libusb_transfers_.clear();
    libusb_buffers_.clear();
}

void Uac2Playback::libusbEventLoop() {
    timeval tv{};
    tv.tv_sec = 0;
    tv.tv_usec = 50'000;
    while (running_.load()) {
        if (libusb_ctx_ == nullptr) {
            break;
        }
        (void)libusb_handle_events_timeout_completed(libusb_ctx_, &tv, nullptr);
    }
}

void Uac2Playback::LIBUSB_CALL Uac2Playback::libusbPlaybackCallback(struct libusb_transfer* transfer) {
    if (transfer == nullptr || transfer->user_data == nullptr) {
        return;
    }
    auto* self = static_cast<Uac2Playback*>(transfer->user_data);

    if (transfer->status != LIBUSB_TRANSFER_COMPLETED && transfer->status != LIBUSB_TRANSFER_CANCELLED &&
        self->running_.load()) {
        OMT_LOGW("uac2 playback libusb transfer status=%d", transfer->status);
    }

    if (!self->running_.load()) {
        return;
    }

    size_t frames = 0;
    self->fillUrbBuffer(transfer->buffer,
                        static_cast<size_t>(transfer->length),
                        &frames);
    for (int p = 0; p < transfer->num_iso_packets; ++p) {
        transfer->iso_packet_desc[p].actual_length = 0;
    }

    const int r = libusb_submit_transfer(transfer);
    if (r != 0 && self->running_.load()) {
        OMT_LOGE("uac2 playback libusb resubmit failed: %s", libusb_error_name(r));
        self->running_.store(false);
    }
}

void Uac2Playback::workerLoopLibusb(std::promise<bool> init_promise) {
    const IsoUrbLayout layout = urb_layout_;
    bool init_reported = false;

    // Wait for the Kotlin read loop to prime the ring — otherwise isoch OUT drains silence.
    const size_t min_prime = std::max<size_t>(static_cast<size_t>(sample_rate_ / 5), 2400);
    for (int i = 0; i < 150 && running_.load(); ++i) {
        if (ring_ != nullptr && ring_->availableFrames() >= min_prime) {
            break;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }

    libusb_transfers_.clear();
    libusb_buffers_.clear();
    libusb_transfers_.reserve(kNumUrbs);
    libusb_buffers_.reserve(static_cast<size_t>(kNumUrbs));

    for (int i = 0; i < kNumUrbs; ++i) {
        libusb_transfer* transfer = libusb_alloc_transfer(layout.num_packets);
        if (transfer == nullptr) {
            if (!init_reported) {
                init_promise.set_value(false);
            }
            running_.store(false);
            return;
        }

        libusb_buffers_.emplace_back(static_cast<size_t>(layout.buffer_length));
        unsigned char* buffer = libusb_buffers_.back().data();
        size_t frames = 0;
        fillUrbBuffer(buffer, static_cast<size_t>(layout.buffer_length), &frames);

        libusb_fill_iso_transfer(transfer,
                                 libusb_handle_,
                                 alt_.endpoint_address,
                                 buffer,
                                 layout.buffer_length,
                                 layout.num_packets,
                                 libusbPlaybackCallback,
                                 this,
                                 0);
        libusb_set_iso_packet_lengths(transfer, static_cast<unsigned int>(layout.per_packet_bytes));

        const int r = libusb_submit_transfer(transfer);
        if (r != 0) {
            OMT_LOGE("uac2 playback libusb SUBMIT failed: %s", libusb_error_name(r));
            libusb_free_transfer(transfer);
            if (!init_reported) {
                init_promise.set_value(false);
                init_reported = true;
            }
            running_.store(false);
            return;
        }

        libusb_transfers_.push_back(transfer);
        if (!init_reported) {
            init_promise.set_value(true);
            init_reported = true;
        }
    }

    if (!init_reported) {
        init_promise.set_value(false);
    }

    while (running_.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

void Uac2Playback::workerLoopUsbdevfs(std::promise<bool> init_promise) {
    const IsoUrbLayout layout = urb_layout_;

    std::vector<UrbContext> contexts(kNumUrbs);
    bool init_reported = false;

    for (int i = 0; i < kNumUrbs; ++i) {
        usbdevfs_urb* urb = allocIsoUrb(layout, &contexts[static_cast<size_t>(i)]);
        initIsoUrb(urb, layout, alt_.endpoint_address, &contexts[static_cast<size_t>(i)]);
        size_t frames = 0;
        fillUrbBuffer(static_cast<uint8_t*>(urb->buffer), static_cast<size_t>(layout.buffer_length), &frames);
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
