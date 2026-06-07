#include "uac2_format.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace openmultitrack::uac2 {

namespace {

int32_t readPcm32(const uint8_t* p) {
    int32_t v;
    std::memcpy(&v, p, sizeof(v));
    return v;
}

void writePcm32(uint8_t* p, int32_t v) {
    std::memcpy(p, &v, sizeof(v));
}

}  // namespace

void pcmFrameToFloat(const uint8_t* src,
                     float* dest,
                     uint8_t channels,
                     uint8_t subframe_bytes,
                     uint8_t bit_resolution) {
    for (uint8_t c = 0; c < channels; ++c) {
        const uint8_t* p = src + static_cast<size_t>(c) * subframe_bytes;
        if (subframe_bytes == 4 && bit_resolution >= 24) {
            dest[c] = static_cast<float>(readPcm32(p)) / 2147483648.0f;
        } else if (subframe_bytes == 3) {
            int32_t v = (static_cast<int32_t>(p[0])) |
                        (static_cast<int32_t>(p[1]) << 8) |
                        (static_cast<int32_t>(p[2]) << 16);
            if (v & 0x800000) {
                v |= ~0xffffff;
            }
            dest[c] = static_cast<float>(v) / 8388608.0f;
        } else if (subframe_bytes == 2) {
            int16_t v;
            std::memcpy(&v, p, sizeof(v));
            dest[c] = static_cast<float>(v) / 32768.0f;
        } else {
            dest[c] = 0.0f;
        }
    }
}

void floatFrameToPcm(const float* src,
                     uint8_t* dest,
                     uint8_t channels,
                     uint8_t subframe_bytes,
                     uint8_t bit_resolution) {
    for (uint8_t c = 0; c < channels; ++c) {
        uint8_t* p = dest + static_cast<size_t>(c) * subframe_bytes;
        const float clamped = std::max(-1.0f, std::min(1.0f, src[c]));
        if (subframe_bytes == 4 && bit_resolution >= 24) {
            const int32_t v = static_cast<int32_t>(clamped * 2147483647.0f);
            writePcm32(p, v);
        } else if (subframe_bytes == 3) {
            const int32_t v = static_cast<int32_t>(clamped * 8388607.0f);
            p[0] = static_cast<uint8_t>(v & 0xff);
            p[1] = static_cast<uint8_t>((v >> 8) & 0xff);
            p[2] = static_cast<uint8_t>((v >> 16) & 0xff);
        } else if (subframe_bytes == 2) {
            const int16_t v = static_cast<int16_t>(clamped * 32767.0f);
            std::memcpy(p, &v, sizeof(v));
        } else {
            std::memset(p, 0, subframe_bytes);
        }
    }
}

size_t bytesPerFrame(const Uac2Format& format) {
    return static_cast<size_t>(format.channels) * format.subframe_bytes;
}

size_t framesInBytes(size_t byte_count, const Uac2Format& format) {
    const size_t bpf = bytesPerFrame(format);
    if (bpf == 0) return 0;
    return byte_count / bpf;
}

}  // namespace openmultitrack::uac2
