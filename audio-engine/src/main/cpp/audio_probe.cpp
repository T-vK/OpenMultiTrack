#include "audio_probe.h"

#include <oboe/Oboe.h>

#include <algorithm>
#include <sstream>

namespace openmultitrack {

namespace {

constexpr int32_t kPreferredSampleRate = 48000;

oboe::Direction toOboeDirection(ProbeDirection direction) {
    return direction == ProbeDirection::Input ? oboe::Direction::Input
                                                : oboe::Direction::Output;
}

}  // namespace

ProbeResult probeUsbAudioEndpoint(int32_t deviceId, ProbeDirection direction) {
    ProbeResult result{};
    result.deviceId = deviceId;
    result.direction = direction;
    result.sampleRate = 0;
    result.channelCount = 0;
    result.framesPerBurst = 0;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(toOboeDirection(direction));
    builder.setDeviceId(deviceId);
    builder.setSampleRate(kPreferredSampleRate);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setChannelCount(oboe::ChannelCount::Unspecified);

    oboe::AudioStream* rawStream = nullptr;
    const oboe::Result openResult = builder.openStream(&rawStream);
    if (openResult != oboe::Result::OK || rawStream == nullptr) {
        std::ostringstream message;
        message << "openStream failed: " << oboe::convertToText(openResult);
        result.error = message.str();
        return result;
    }

    std::unique_ptr<oboe::AudioStream> stream(rawStream);
    result.sampleRate = stream->getSampleRate();
    result.channelCount = stream->getChannelCount();
    result.framesPerBurst = stream->getFramesPerBurst();

    const oboe::Result startResult = stream->start();
    if (startResult != oboe::Result::OK) {
        std::ostringstream message;
        message << "start failed: " << oboe::convertToText(startResult);
        result.error = message.str();
        return result;
    }

    const oboe::Result stopResult = stream->stop();
    if (stopResult != oboe::Result::OK) {
        std::ostringstream message;
        message << "stop failed: " << oboe::convertToText(stopResult);
        result.error = message.str();
        return result;
    }

    stream->close();
    return result;
}

}  // namespace openmultitrack
