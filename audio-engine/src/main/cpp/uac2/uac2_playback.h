#pragma once

#include "../spsc_ring_buffer.h"
#include "uac2_types.h"

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <thread>

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

    PlaybackStatus open(int usb_fd, const Uac2AltSetting& alt);
    void close();

    size_t writeFrames(const float* src, size_t frame_count);
    uint64_t underrunFrames() const { return underrun_frames_.load(); }

private:
    Uac2Playback() = default;
    ~Uac2Playback() { close(); }

    void workerLoop();
    bool fillUrbBuffer(uint8_t* dest, size_t byte_capacity, size_t* frames_written);

    std::mutex mutex_;
    std::thread worker_;
    std::atomic<bool> running_{false};
    std::atomic<uint64_t> underrun_frames_{0};

    int usb_fd_ = -1;
    Uac2AltSetting alt_{};
    int32_t channel_count_ = 0;
    int32_t sample_rate_ = 0;

    std::unique_ptr<openmultitrack::SpscRingBuffer> ring_;
};

}  // namespace openmultitrack::uac2
