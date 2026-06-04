#include "audio_player.h"

#include <oboe/Oboe.h>

#include <cstring>
#include <vector>

namespace openmultitrack {

namespace {
constexpr size_t kRingCapacityFrames = 48000;
}

AudioPlayer& AudioPlayer::instance() {
    static AudioPlayer player;
    return player;
}

PlayerStatus AudioPlayer::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    return stopUnlocked();
}

PlayerStatus AudioPlayer::stopUnlocked() {
    playing_.store(false);
    if (stream_ != nullptr) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
    ring_.reset();
    PlayerStatus status;
    status.playing = false;
    return status;
}

PlayerStatus AudioPlayer::start(int32_t deviceId, int32_t channelCount, int32_t sampleRate) {
    std::lock_guard<std::mutex> lock(mutex_);
    stopUnlocked();
    underrunFrames_.store(0);
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
        PlayerStatus status;
        status.error = std::string("openStream: ") + oboe::convertToText(openResult);
        return status;
    }
    stream_.reset(raw);
    deviceId_ = deviceId;
    channelCount_ = stream_->getChannelCount();
    sampleRate_ = stream_->getSampleRate();

    const oboe::Result startResult = stream_->start();
    if (startResult != oboe::Result::OK) {
        stream_.reset();
        PlayerStatus status;
        status.error = std::string("start: ") + oboe::convertToText(startResult);
        return status;
    }

    playing_.store(true);
    PlayerStatus status;
    status.playing = true;
    status.deviceId = deviceId_;
    status.channelCount = channelCount_;
    status.sampleRate = sampleRate_;
    return status;
}

size_t AudioPlayer::writeFrames(const float* src, size_t frameCount) {
    if (ring_ == nullptr) return 0;
    const int32_t channels = ring_->channelCount();
    size_t accepted = 0;
    float frame[32];
    for (size_t f = 0; f < frameCount; ++f) {
        for (int32_t c = 0; c < channels; ++c) {
            frame[c] = src[f * static_cast<size_t>(channels) + static_cast<size_t>(c)];
        }
        if (!ring_->pushFrame(frame)) {
            break;
        }
        ++accepted;
    }
    return accepted;
}

size_t AudioPlayer::availableWriteCapacity() const {
    if (ring_ == nullptr) return 0;
    // Approximate free space
    return kRingCapacityFrames - ring_->availableFrames();
}

PlayerStatus AudioPlayer::status() const {
    std::lock_guard<std::mutex> lock(mutex_);
    PlayerStatus s;
    s.playing = playing_.load();
    s.deviceId = deviceId_;
    s.channelCount = channelCount_;
    s.sampleRate = sampleRate_;
    s.underrunFrames = underrunFrames_.load();
    return s;
}

oboe::DataCallbackResult AudioPlayer::onAudioReady(oboe::AudioStream* /*stream*/,
                                                   void* audioData,
                                                   int32_t numFrames) {
    if (!playing_.load() || ring_ == nullptr) {
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
