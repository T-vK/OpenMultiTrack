#pragma once

#include "../spsc_pcm_ring.h"
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

struct CaptureStatus {
    bool running = false;
    int32_t channel_count = 0;
    int32_t sample_rate = 0;
    uint64_t dropped_frames = 0;
    std::optional<std::string> error;
};

class Uac2Capture {
public:
    static Uac2Capture& instance();

    CaptureStatus open(int usb_fd, const Uac2AltSetting& alt, bool java_interface_claimed = false);
    void close();

    size_t readFrames(float* dest, size_t max_frames);
    size_t readPcmBytes(uint8_t* dest, size_t max_frames);
    int32_t captureBytesPerFrame() const;
    uint64_t droppedFrames() const { return dropped_frames_.load(); }

    /** Drains the capture ring to a raw interleaved PCM file on a native thread (high throughput). */
    bool startPcmFileRecording(const std::string& path);
    void stopPcmFileRecording();
    uint64_t pcmFileFramesWritten() const { return file_frames_written_.load(); }
    bool isPcmFileRecording() const { return file_recording_.load(); }
    bool isRunning() const { return running_.load(); }

private:
    enum class IoBackend { None, Libusb, Usbdevfs };

    Uac2Capture() = default;
    ~Uac2Capture() { close(); }

    bool tryOpenLibusb(int usb_fd, const Uac2AltSetting& alt, bool java_interface_claimed);
    bool tryOpenUsbdevfs(int usb_fd, const Uac2AltSetting& alt, bool java_interface_claimed);
    /** Returns true once at least one frame lands in the capture ring. */
    bool waitForIncomingFrames(int timeout_ms);
    void stopLibusbWorkersUnlocked();
    void closeUnlocked();

    void workerLoopUsbdevfs(std::promise<bool> init_promise);
    void workerLoopLibusb(std::promise<bool> init_promise);
    void libusbEventLoop();
    void freeLibusbTransfers();

    static void libusbCaptureCallback(struct libusb_transfer* transfer);

    void ingestPcmBytes(const uint8_t* bytes, size_t byte_count);
    void pcmFileWriterLoop();
    void stopPcmFileRecordingUnlocked();

    std::mutex mutex_;
    std::thread worker_;
    std::thread libusb_event_thread_;
    std::atomic<bool> running_{false};
    std::atomic<uint64_t> dropped_frames_{0};

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

    std::unique_ptr<openmultitrack::SpscPcmRing> ring_;
    /** Dedicated drain path for native file recording (avoids competing with JNI/VU reads). */
    std::unique_ptr<openmultitrack::SpscPcmRing> file_ring_;

    std::atomic<bool> file_recording_{false};
    std::atomic<uint64_t> file_frames_written_{0};
    std::thread file_writer_;
    FILE* file_handle_ = nullptr;
};

}  // namespace openmultitrack::uac2
