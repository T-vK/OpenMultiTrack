#pragma once

#include "uac2_types.h"

#include <cstddef>
#include <cstdint>

namespace openmultitrack::uac2 {

/** Parse a USB configuration descriptor blob (from UsbDeviceConnection.getRawDescriptors()). */
Uac2DeviceCaps parseConfigDescriptor(const uint8_t* data, size_t length);

/** Pick the alt setting with the most channels at or above min_channels and sample rate. */
Uac2AltSetting selectBestAlt(const std::vector<Uac2AltSetting>& alts,
                             uint8_t min_channels,
                             uint32_t sample_rate_hz);

}  // namespace openmultitrack::uac2
