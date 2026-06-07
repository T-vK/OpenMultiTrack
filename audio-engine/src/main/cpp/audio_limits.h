#pragma once

#include <cstdint>

namespace openmultitrack {

// X32 = 32ch, XR18 = 18ch; leave headroom for future consoles.
constexpr int32_t kMaxAudioChannels = 64;

}  // namespace openmultitrack
