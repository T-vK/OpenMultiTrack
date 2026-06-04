#pragma once

#include <cstdint>
#include <optional>
#include <string>

namespace openmultitrack {

enum class ProbeDirection { Input, Output };

struct ProbeResult {
    int32_t deviceId = 0;
    ProbeDirection direction = ProbeDirection::Input;
    int32_t sampleRate = 0;
    int32_t channelCount = 0;
    int32_t framesPerBurst = 0;
    std::optional<std::string> error;
};

ProbeResult probeUsbAudioEndpoint(int32_t deviceId, ProbeDirection direction);

}  // namespace openmultitrack
