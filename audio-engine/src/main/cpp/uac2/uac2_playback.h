#pragma once

#include "../spsc_ring_buffer.h"
#include "uac2_types.h"
#include "uac2_urb.h"

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <future>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <vector>

struct libusb_context;
struct libusb_device_handle;
struct libusb_transfer;

namespace openmultitrack::uac2 {

struct PlaybackStatus {
    bool running = false;
    int32_t channel_count = 0;
    int32_t sample_rate = 0;
    uint64_t underrun_frames = 0;
    std::optional<std::string> error;
};

class Uac2Playback {
public:
    static Uac2Playback& instance();

    PlaybackStatus open(int usb_fd, const Uac2AltSetting& alt, bool java_interface_claimed = false);
    void close();

    size_t writeFrames(const float* src, size_t frame_count);
    uint64_t underrunFrames() const { return underrun_frames_.load(); }

private:
    enum class IoBackend { None, Libusb, Usbdevfs };

    Uac2Playback() = default;
    ~Uac2Playback() { close(); }

    bool tryOpenLibusb(int usb_fd, const Uac2AltSetting& alt, bool java_interface_claimed);
    bool tryOpenUsbdevfs(int usb_fd, const Uac2AltSetting& alt, bool java_interface_claimed);
    void closeUnlocked();

    void workerLoopUsbdevfs(std::promise<bool> init_promise);
    void workerLoopLibusb(std::promise<bool> init_promise);
    void libusbEventLoop();
    void freeLibusbTransfers();
    bool fillUrbBuffer(uint8_t* dest, size_t byte_capacity, size_t* frames_written);

    static void libusbPlaybackCallback(struct libusb_transfer* transfer);

    std::mutex mutex_;
    std::thread worker_;
    std::thread libusb_event_thread_;
    std::atomic<bool> running_{false};
    std::atomic<uint64_t> underrun_frames_{0};

    IoBackend backend_ = IoBackend::None;
    int usb_fd_ = -1;
    bool java_interface_claimed_ = false;
    bool libusb_owns_interface_ = false;
    Uac2AltSetting alt_{};
    int32_t channel_count_ = 0;
    int32_t sample_rate_ = 0;
    IsoUrbLayout urb_layout_{};

    libusb_context* libusb_ctx_ = nullptr;
    libusb_device_handle* libusb_handle_ = nullptr;
    std::vector<libusb_transfer*> libusb_transfers_;
    std::vector<std::vector<uint8_t>> libusb_buffers_;
    std::atomic<uint32_t> libusb_in_flight_mask_{0};
    std::atomic<int64_t> libusb_submitted_frames_{0};

    std::unique_ptr<openmultitrack::SpscRingBuffer> ring_;
};

}  // namespace openmultitrack::uac2
