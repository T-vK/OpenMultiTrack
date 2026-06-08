#include "audio_monitor.h"

#include "audio_limits.h"
#include "audio_log.h"

#include <oboe/Oboe.h>

#include <cstring>
#include <vector>

namespace openmultitrack {

namespace {
constexpr size_t kRingCapacityFrames = 48000;
}

AudioMonitor& AudioMonitor::instance() {
    static AudioMonitor monitor;
    return monitor;
}

MonitorStatus AudioMonitor::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    return stopUnlocked();
}

MonitorStatus AudioMonitor::stopUnlocked() {
    const uint64_t underruns = underrunFrames_.load();
    running_.store(false);
    if (stream_ != nullptr) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
    ring_.reset();
    OMT_LOGI("monitor stopped deviceId=%d channels=%d sampleRate=%d underrunFrames=%llu",
             deviceId_, channelCount_, sampleRate_,
             static_cast<unsigned long long>(underruns));
    MonitorStatus status;
    status.running = false;
    return status;
}

MonitorStatus AudioMonitor::start(int32_t deviceId, int32_t channelCount, int32_t sampleRate) {
    std::lock_guard<std::mutex> lock(mutex_);
    stopUnlocked();
    underrunFrames_.store(0);

    if (channelCount < 1 || channelCount > 2) {
        MonitorStatus status;
        status.error = "monitor supports mono or stereo output only";
        OMT_LOGE("monitor start rejected deviceId=%d channels=%d", deviceId, channelCount);
        return status;
    }

    OMT_LOGI("monitor start deviceId=%d channels=%d sampleRate=%d", deviceId, channelCount, sampleRate);

    ring_ = std::make_unique<SpscRingBuffer>(kRingCapacityFrames, channelCount);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setDeviceId(deviceId);
    builder.setSampleRate(sampleRate);
    builder.setChannelCount(channelCount);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setDataCallback(this);

    oboe::AudioStream* raw = nullptr;
    const oboe::Result openResult = builder.openStream(&raw);
    if (openResult != oboe::Result::OK || raw == nullptr) {
        MonitorStatus status;
        status.error = std::string("openStream: ") + oboe::convertToText(openResult);
        OMT_LOGE("monitor openStream failed deviceId=%d: %s", deviceId, status.error->c_str());
        return status;
    }
    stream_.reset(raw);
    deviceId_ = deviceId;
    channelCount_ = stream_->getChannelCount();
    sampleRate_ = stream_->getSampleRate();

    const oboe::Result startResult = stream_->start();
    if (startResult != oboe::Result::OK) {
        stream_.reset();
        MonitorStatus status;
        status.error = std::string("start: ") + oboe::convertToText(startResult);
        OMT_LOGE("monitor start failed deviceId=%d: %s", deviceId, status.error->c_str());
        return status;
    }

    OMT_LOGI("monitor running deviceId=%d actualChannels=%d actualSampleRate=%d",
             deviceId_, channelCount_, sampleRate_);
    running_.store(true);
    MonitorStatus status;
    status.running = true;
    status.deviceId = deviceId_;
    status.channelCount = channelCount_;
    status.sampleRate = sampleRate_;
    return status;
}

size_t AudioMonitor::writeFrames(const float* src, size_t frameCount) {
    if (ring_ == nullptr) return 0;
    const int32_t channels = ring_->channelCount();
    size_t accepted = 0;
    std::vector<float> frame(static_cast<size_t>(channels));
    for (size_t f = 0; f < frameCount; ++f) {
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

MonitorStatus AudioMonitor::status() const {
    std::lock_guard<std::mutex> lock(mutex_);
    MonitorStatus s;
    s.running = running_.load();
    s.deviceId = deviceId_;
    s.channelCount = channelCount_;
    s.sampleRate = sampleRate_;
    s.underrunFrames = underrunFrames_.load();
    return s;
}

oboe::DataCallbackResult AudioMonitor::onAudioReady(oboe::AudioStream* /*stream*/,
                                                      void* audioData,
                                                      int32_t numFrames) {
    if (!running_.load() || ring_ == nullptr) {
        return oboe::DataCallbackResult::Continue;
    }
    auto* data = static_cast<float*>(audioData);
    const int32_t channels = ring_->channelCount();
    std::vector<float> chunk(static_cast<size_t>(numFrames * channels));
    const size_t read = ring_->popFrames(chunk.data(), static_cast<size_t>(numFrames));
    if (read < static_cast<size_t>(numFrames)) {
        underrunFrames_.fetch_add(static_cast<uint64_t>(numFrames - read));
    }
    std::memset(data, 0, static_cast<size_t>(numFrames * channels) * sizeof(float));
    for (size_t f = 0; f < read; ++f) {
        for (int32_t c = 0; c < channels; ++c) {
            data[f * channels + c] = chunk[f * static_cast<size_t>(channels) + static_cast<size_t>(c)];
        }
    }
    return oboe::DataCallbackResult::Continue;
}

}  // namespace openmultitrack
