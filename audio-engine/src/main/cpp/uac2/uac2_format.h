#pragma once

#include "uac2_types.h"

#include <cstddef>
#include <cstdint>

namespace openmultitrack::uac2 {

/** Convert one interleaved PCM frame to float (Type I). */
void pcmFrameToFloat(const uint8_t* src,
                     float* dest,
                     uint8_t channels,
                     uint8_t subframe_bytes,
                     uint8_t bit_resolution);

/** Convert contiguous interleaved PCM bytes to float frames. Returns frames converted. */
size_t pcmInterleavedToFloat(const uint8_t* src,
                             size_t byte_count,
                             float* dest,
                             uint8_t channels,
                             uint8_t subframe_bytes,
                             uint8_t bit_resolution);

/** Convert float interleaved frame to PCM for isoch OUT. */
void floatFrameToPcm(const float* src,
                     uint8_t* dest,
                     uint8_t channels,
                     uint8_t subframe_bytes,
                     uint8_t bit_resolution);

size_t bytesPerFrame(const Uac2Format& format);

size_t framesInBytes(size_t byte_count, const Uac2Format& format);

}  // namespace openmultitrack::uac2
