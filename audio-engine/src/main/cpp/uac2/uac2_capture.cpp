#include "uac2_capture.h"

#include "../audio_log.h"
#include "../spsc_pcm_ring.h"
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

namespace openmultitrack::uac2 {

namespace {

constexpr int kNumUrbs = 16;
constexpr int kOpenVerifyTimeoutMs = 1500;
constexpr size_t kMinVerifyFrames = 48;

}  // namespace

bool Uac2Capture::waitForIncomingFrames(int timeout_ms) {
    const auto deadline =
        std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms);
    while (std::chrono::steady_clock::now() < deadline && running_.load()) {
        if (ring_ != nullptr && ring_->availableFrames() >= kMinVerifyFrames) {
            OMT_LOGI("uac2 capture verify ok frames=%zu", ring_->availableFrames());
            return true;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    const size_t available = ring_ != nullptr ? ring_->availableFrames() : 0;
    if (available > 0) {
        OMT_LOGW("uac2 capture verify timeout with only %zu frames (need %zu)",
                 available,
                 kMinVerifyFrames);
    }
    return available >= kMinVerifyFrames;
}

void Uac2Capture::stopLibusbWorkersUnlocked() {
    running_.store(false);
    if (worker_.joinable()) {
        worker_.join();
    }
    if (libusb_event_thread_.joinable()) {
        libusb_event_thread_.join();
    }
    freeLibusbTransfers();
}

Uac2Capture& Uac2Capture::instance() {
    static Uac2Capture capture;
    return capture;
}

CaptureStatus Uac2Capture::open(int usb_fd, const Uac2AltSetting& alt, bool java_interface_claimed) {
    std::lock_guard<std::mutex> lock(mutex_);
    closeUnlocked();

    dropped_frames_.store(0);

    if (usb_fd < 0 || !alt.format.valid) {
        CaptureStatus status;
        status.error = "invalid usb fd or alt";
        return status;
    }

    usb_fd_ = usb_fd;
    alt_ = alt;
    java_interface_claimed_ = java_interface_claimed;
    channel_count_ = alt.format.channels;
    sample_rate_ = static_cast<int32_t>(alt.format.sample_rate_hz);
    ring_ = std::make_unique<openmultitrack::SpscPcmRing>(
        144'000,
        alt.format.channels,
        alt.format.subframe_bytes);

    // Prefer usbdevfs (emulator / hosts where SUBMITURB works). Fall back to libusb
    // when claim or isoch submit fails (e.g. XR18 on Samsung tablets).
    bool opened = tryOpenUsbdevfs(usb_fd, alt, java_interface_claimed);
    if (!opened) {
        opened = tryOpenLibusb(usb_fd, alt, java_interface_claimed);
    }

    if (!opened) {
        closeUnlocked();
        CaptureStatus status;
        status.error = "isoch URB submit failed";
        return status;
    }

    OMT_LOGI("uac2 capture open %s %dch @ %dHz ep=0x%02x pkt=%u urbs=%d x %d",
             backend_ == IoBackend::Libusb ? "libusb" : "usbdevfs",
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

bool Uac2Capture::tryOpenLibusb(int usb_fd,
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

    // Samsung tablets often accept IN isoch only with frame-sized micro-packets; try that first.
    for (const bool micro_packets : {true, false}) {
        if (ring_ != nullptr) {
            ring_->reset();
        }
        urb_layout_ = layoutForAlt(alt, true, micro_packets);
        OMT_LOGI("uac2 capture libusb trying layout micro=%d urbs=%d x %d",
                 micro_packets ? 1 : 0,
                 urb_layout_.num_packets,
                 urb_layout_.per_packet_bytes);

        std::promise<bool> init_promise;
        std::future<bool> init_future = init_promise.get_future();
        running_.store(true);
        libusb_event_thread_ = std::thread(&Uac2Capture::libusbEventLoop, this);
        worker_ = std::thread(&Uac2Capture::workerLoopLibusb, this, std::move(init_promise));

        const bool submitted = init_future.wait_for(std::chrono::seconds(2)) ==
                                   std::future_status::ready &&
                               init_future.get();
        if (submitted && waitForIncomingFrames(kOpenVerifyTimeoutMs)) {
            backend_ = IoBackend::Libusb;
            return true;
        }

        if (submitted) {
            OMT_LOGW(
                "uac2 capture libusb layout micro=%d submitted but no frames in %dms",
                micro_packets ? 1 : 0,
                kOpenVerifyTimeoutMs);
        }
        stopLibusbWorkersUnlocked();
    }

    libusbReleaseStreaming(libusb_handle_, alt.interface_number, libusb_owns_interface_);
    libusb_handle_ = nullptr;
    libusb_owns_interface_ = false;
    libusb_ctx_ = nullptr;
    return false;
}

bool Uac2Capture::tryOpenUsbdevfs(int usb_fd,
                                  const Uac2AltSetting& alt,
                                  bool java_interface_claimed) {
    UsbIoStatus io = java_interface_claimed
        ? setAltOnClaimedInterface(usb_fd, alt)
        : claimAndSetAlt(usb_fd, alt);
    if (!io.ok && !java_interface_claimed) {
        OMT_LOGW("uac2 capture usbdevfs claim failed (%s), trying driver detach",
                 io.error.c_str());
        io = claimAndSetAltWithDriverDetach(usb_fd, alt);
    }
    if (!io.ok) {
        OMT_LOGE("uac2 capture usbdevfs setup failed: %s", io.error.c_str());
        return false;
    }

    for (const bool micro_packets : {true, false}) {
        if (ring_ != nullptr) {
            ring_->reset();
        }
        urb_layout_ = layoutForAlt(alt, true, micro_packets);
        OMT_LOGI("uac2 capture usbdevfs trying layout micro=%d urbs=%d x %d",
                 micro_packets ? 1 : 0,
                 urb_layout_.num_packets,
                 urb_layout_.per_packet_bytes);

        std::promise<bool> init_promise;
        std::future<bool> init_future = init_promise.get_future();
        running_.store(true);
        worker_ = std::thread(&Uac2Capture::workerLoopUsbdevfs, this, std::move(init_promise));

        const bool submitted = init_future.wait_for(std::chrono::seconds(2)) ==
                                   std::future_status::ready &&
                               init_future.get();
        if (submitted && waitForIncomingFrames(kOpenVerifyTimeoutMs)) {
            backend_ = IoBackend::Usbdevfs;
            return true;
        }

        if (submitted) {
            OMT_LOGW(
                "uac2 capture usbdevfs layout micro=%d submitted but no frames in %dms",
                micro_packets ? 1 : 0,
                kOpenVerifyTimeoutMs);
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

void Uac2Capture::close() {
    std::lock_guard<std::mutex> lock(mutex_);
    closeUnlocked();
}

void Uac2Capture::closeUnlocked() {
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
}

size_t Uac2Capture::readFrames(float* dest, size_t max_frames) {
    if (ring_ == nullptr) return 0;
    return ring_->popFramesAsFloat(dest, max_frames, alt_.format.bit_resolution);
}

size_t Uac2Capture::readPcmBytes(uint8_t* dest, size_t max_frames) {
    if (ring_ == nullptr) return 0;
    return ring_->popPcmBytes(dest, max_frames);
}

void Uac2Capture::ingestPcmBytes(const uint8_t* bytes, size_t byte_count) {
    if (ring_ == nullptr || byte_count == 0) return;
    const size_t bpf = bytesPerFrame(alt_.format);
    if (bpf == 0) return;
    const size_t wholeBytes = (byte_count / bpf) * bpf;
    if (wholeBytes == 0) return;
    const size_t pushed = ring_->pushBytes(bytes, wholeBytes);
    if (pushed < wholeBytes) {
        dropped_frames_.fetch_add((wholeBytes - pushed) / bpf);
    }
}

void Uac2Capture::freeLibusbTransfers() {
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

void Uac2Capture::libusbEventLoop() {
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

void Uac2Capture::LIBUSB_CALL Uac2Capture::libusbCaptureCallback(struct libusb_transfer* transfer) {
    if (transfer == nullptr || transfer->user_data == nullptr) {
        return;
    }
    auto* self = static_cast<Uac2Capture*>(transfer->user_data);
    if (!self->running_.load()) {
        return;
    }

    if (transfer->status == LIBUSB_TRANSFER_COMPLETED) {
        auto* bytes = transfer->buffer;
        size_t byte_offset = 0;
        for (int p = 0; p < transfer->num_iso_packets; ++p) {
            const int actual = transfer->iso_packet_desc[p].actual_length;
            if (actual > 0) {
                self->ingestPcmBytes(bytes + byte_offset, static_cast<size_t>(actual));
            }
            byte_offset += static_cast<size_t>(transfer->iso_packet_desc[p].length);
        }
    } else if (transfer->status != LIBUSB_TRANSFER_CANCELLED && self->running_.load()) {
        OMT_LOGW("uac2 capture libusb transfer status=%d", transfer->status);
    }

    if (!self->running_.load()) {
        return;
    }

    for (int p = 0; p < transfer->num_iso_packets; ++p) {
        transfer->iso_packet_desc[p].actual_length = 0;
    }
    const int r = libusb_submit_transfer(transfer);
    if (r != 0 && self->running_.load()) {
        OMT_LOGE("uac2 capture libusb resubmit failed: %s", libusb_error_name(r));
        self->running_.store(false);
    }
}

void Uac2Capture::workerLoopLibusb(std::promise<bool> init_promise) {
    const IsoUrbLayout layout = urb_layout_;
    bool init_reported = false;

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

        libusb_fill_iso_transfer(transfer,
                                 libusb_handle_,
                                 alt_.endpoint_address,
                                 buffer,
                                 layout.buffer_length,
                                 layout.num_packets,
                                 libusbCaptureCallback,
                                 this,
                                 0);
        libusb_set_iso_packet_lengths(transfer, static_cast<unsigned int>(layout.per_packet_bytes));

        const int r = libusb_submit_transfer(transfer);
        if (r != 0) {
            OMT_LOGE("uac2 capture libusb SUBMIT failed: %s", libusb_error_name(r));
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

void Uac2Capture::workerLoopUsbdevfs(std::promise<bool> init_promise) {
    const IsoUrbLayout layout = urb_layout_;

    std::vector<UrbContext> contexts(kNumUrbs);
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
            if (actual > 0) {
                ingestPcmBytes(bytes + byte_offset, static_cast<size_t>(actual));
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
