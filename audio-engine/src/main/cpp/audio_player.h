#pragma once

#include "spsc_ring_buffer.h"

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <optional>
#include <string>

namespace openmultitrack {

struct PlayerStatus {
    bool playing = false;
    int32_t deviceId = 0;
    int32_t channelCount = 0;
    int32_t sampleRate = 0;
    uint64_t underrunFrames = 0;
    std::optional<std::string> error;
};

class AudioPlayer : public oboe::AudioStreamDataCallback {
public:
    static AudioPlayer& instance();

    PlayerStatus start(int32_t deviceId, int32_t channelCount, int32_t sampleRate);
    PlayerStatus stop();

    PlayerStatus stopUnlocked();

    /** Feed interleaved frames for playback. Returns frames accepted. */
    size_t writeFrames(const float* src, size_t frameCount);

    size_t availableWriteCapacity() const;

    PlayerStatus status() const;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData,
                                          int32_t numFrames) override;

private:
    AudioPlayer() = default;

    mutable std::mutex mutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<SpscRingBuffer> ring_;
    std::atomic<uint64_t> underrunFrames_{0};
    std::atomic<bool> playing_{false};
    int32_t channelCount_ = 0;
    int32_t sampleRate_ = 0;
    int32_t deviceId_ = 0;
};

}  // namespace openmultitrack
