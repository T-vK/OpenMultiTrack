#include "audio_recorder.h"

#include "audio_limits.h"
#include "audio_log.h"

#include <oboe/Oboe.h>

#include <vector>

namespace openmultitrack {

namespace {
constexpr size_t kRingCapacityFrames = 48000;  // ~1s @ 48 kHz
}

AudioRecorder& AudioRecorder::instance() {
    static AudioRecorder recorder;
    return recorder;
}

RecorderStatus AudioRecorder::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    return stopUnlocked();
}

RecorderStatus AudioRecorder::stopUnlocked() {
    const uint64_t dropped = droppedFrames_.load();
    running_.store(false);
    if (stream_ != nullptr) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
    ring_.reset();
    OMT_LOGI("recorder stopped deviceId=%d channels=%d sampleRate=%d droppedFrames=%llu",
             deviceId_, channelCount_, sampleRate_,
             static_cast<unsigned long long>(dropped));
    RecorderStatus status;
    status.running = false;
    return status;
}

RecorderStatus AudioRecorder::start(int32_t deviceId, int32_t channelCount, int32_t sampleRate) {
    std::lock_guard<std::mutex> lock(mutex_);
    stopUnlocked();
    droppedFrames_.store(0);

    if (channelCount < 1 || channelCount > kMaxAudioChannels) {
        RecorderStatus status;
        status.error = "channelCount out of range";
        OMT_LOGE("recorder start rejected deviceId=%d channels=%d: %s",
                 deviceId, channelCount, status.error->c_str());
        return status;
    }

    OMT_LOGI("recorder start deviceId=%d requestedChannels=%d sampleRate=%d",
             deviceId, channelCount, sampleRate);

    ring_ = std::make_unique<SpscRingBuffer>(kRingCapacityFrames, channelCount);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input);
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
        RecorderStatus status;
        status.error = std::string("openStream: ") + oboe::convertToText(openResult);
        OMT_LOGE("recorder openStream failed deviceId=%d: %s", deviceId, status.error->c_str());
        return status;
    }
    stream_.reset(raw);
    deviceId_ = deviceId;
    channelCount_ = stream_->getChannelCount();
    sampleRate_ = stream_->getSampleRate();

    const oboe::Result startResult = stream_->start();
    if (startResult != oboe::Result::OK) {
        stream_.reset();
        RecorderStatus status;
        status.error = std::string("start: ") + oboe::convertToText(startResult);
        OMT_LOGE("recorder start failed deviceId=%d: %s", deviceId, status.error->c_str());
        return status;
    }

    OMT_LOGI("recorder running deviceId=%d actualChannels=%d actualSampleRate=%d",
             deviceId_, channelCount_, sampleRate_);
    running_.store(true);
    RecorderStatus status;
    status.running = true;
    status.deviceId = deviceId_;
    status.channelCount = channelCount_;
    status.sampleRate = sampleRate_;
    return status;
}

size_t AudioRecorder::readFrames(float* dest, size_t maxFrames) {
    if (ring_ == nullptr) return 0;
    return ring_->popFrames(dest, maxFrames);
}

RecorderStatus AudioRecorder::status() const {
    std::lock_guard<std::mutex> lock(mutex_);
    RecorderStatus s;
    s.running = running_.load();
    s.deviceId = deviceId_;
    s.channelCount = channelCount_;
    s.sampleRate = sampleRate_;
    s.droppedFrames = droppedFrames_.load();
    return s;
}

oboe::DataCallbackResult AudioRecorder::onAudioReady(oboe::AudioStream* /*stream*/,
                                                     void* audioData,
                                                     int32_t numFrames) {
    if (!running_.load() || ring_ == nullptr) {
        return oboe::DataCallbackResult::Continue;
    }
    const auto* data = static_cast<const float*>(audioData);
    const int32_t channels = ring_->channelCount();
    std::vector<float> frame(static_cast<size_t>(channels));
    for (int32_t i = 0; i < numFrames; ++i) {
        for (int32_t c = 0; c < channels; ++c) {
            frame[static_cast<size_t>(c)] = data[i * channels + c];
        }
        if (!ring_->pushFrame(frame.data())) {
            droppedFrames_.fetch_add(1);
        }
    }
    return oboe::DataCallbackResult::Continue;
}

}  // namespace openmultitrack
