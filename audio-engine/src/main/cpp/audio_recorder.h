#pragma once

#include "spsc_ring_buffer.h"

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <optional>
#include <string>

namespace openmultitrack {

struct RecorderStatus {
    bool running = false;
    int32_t deviceId = 0;
    int32_t channelCount = 0;
    int32_t sampleRate = 0;
    uint64_t droppedFrames = 0;
    std::optional<std::string> error;
};

class AudioRecorder : public oboe::AudioStreamDataCallback {
public:
    static AudioRecorder& instance();

    RecorderStatus start(int32_t deviceId, int32_t channelCount, int32_t sampleRate);
    RecorderStatus stop();

    RecorderStatus stopUnlocked();

    /** Drain captured frames into dest (interleaved). Returns frame count. */
    size_t readFrames(float* dest, size_t maxFrames);

    RecorderStatus status() const;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData,
                                          int32_t numFrames) override;

private:
    AudioRecorder() = default;

    mutable std::mutex mutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<SpscRingBuffer> ring_;
    std::atomic<uint64_t> droppedFrames_{0};
    std::atomic<bool> running_{false};
    int32_t channelCount_ = 0;
    int32_t sampleRate_ = 0;
    int32_t deviceId_ = 0;
};

}  // namespace openmultitrack
