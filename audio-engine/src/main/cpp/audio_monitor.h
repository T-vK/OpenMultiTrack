#pragma once

#include "spsc_ring_buffer.h"

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <optional>
#include <string>

namespace openmultitrack {

struct MonitorStatus {
    bool running = false;
    int32_t deviceId = 0;
    int32_t channelCount = 0;
    int32_t sampleRate = 0;
    uint64_t underrunFrames = 0;
    std::optional<std::string> error;
};

/** Local Oboe output for live input monitoring (headphones / speaker). */
class AudioMonitor : public oboe::AudioStreamDataCallback {
public:
    static AudioMonitor& instance();

    MonitorStatus start(int32_t deviceId, int32_t channelCount, int32_t sampleRate);
    MonitorStatus stop();

    size_t writeFrames(const float* src, size_t frameCount);

    MonitorStatus status() const;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData,
                                          int32_t numFrames) override;

private:
    AudioMonitor() = default;
    MonitorStatus stopUnlocked();

    mutable std::mutex mutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<SpscRingBuffer> ring_;
    std::atomic<uint64_t> underrunFrames_{0};
    std::atomic<bool> running_{false};
    int32_t channelCount_ = 0;
    int32_t sampleRate_ = 0;
    int32_t deviceId_ = 0;
};

}  // namespace openmultitrack
