#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

namespace openmultitrack {

/**
 * Single-producer single-consumer interleaved PCM ring (USB callback → capture reader).
 * Conversion to float happens on the consumer side in [popFramesAsFloat].
 */
class SpscPcmRing {
public:
    SpscPcmRing(size_t capacityFrames, uint8_t channels, uint8_t subframeBytes)
        : channels_(channels),
          subframeBytes_(subframeBytes),
          bytesPerFrame_(static_cast<size_t>(channels) * subframeBytes),
          capacity_(capacityFrames),
          buffer_(capacityFrames * bytesPerFrame_, 0) {}

    size_t pushBytes(const uint8_t* src, size_t byteCount) {
        if (byteCount == 0 || bytesPerFrame_ == 0) return 0;
        const size_t wholeBytes = (byteCount / bytesPerFrame_) * bytesPerFrame_;
        if (wholeBytes == 0) return 0;

        const size_t write = writeIndex_.load(std::memory_order_relaxed);
        const size_t read = readIndex_.load(std::memory_order_acquire);
        const size_t freeBytes = (capacity_ * bytesPerFrame_) - (write - read);
        const size_t toWrite = wholeBytes < freeBytes ? wholeBytes : freeBytes;
        if (toWrite == 0) return 0;

        size_t srcOffset = 0;
        size_t dstWrite = write;
        size_t remaining = toWrite;
        while (remaining > 0) {
            const size_t ringBytes = capacity_ * bytesPerFrame_;
            const size_t dstIdx = dstWrite % ringBytes;
            const size_t contiguous = std::min(remaining, ringBytes - dstIdx);
            std::memcpy(buffer_.data() + dstIdx, src + srcOffset, contiguous);
            srcOffset += contiguous;
            dstWrite += contiguous;
            remaining -= contiguous;
        }
        writeIndex_.store(write + toWrite, std::memory_order_release);
        return toWrite;
    }

    size_t popPcmBytes(uint8_t* dest, size_t maxFrames) {
        if (maxFrames == 0 || bytesPerFrame_ == 0) return 0;
        const size_t write = writeIndex_.load(std::memory_order_acquire);
        size_t read = readIndex_.load(std::memory_order_relaxed);
        const size_t availableBytes = write - read;
        const size_t availableFrames = availableBytes / bytesPerFrame_;
        const size_t toRead = availableFrames < maxFrames ? availableFrames : maxFrames;
        if (toRead == 0) return 0;

        const size_t ringBytes = capacity_ * bytesPerFrame_;
        const size_t byteCount = toRead * bytesPerFrame_;
        size_t dstOffset = 0;
        size_t remaining = byteCount;
        while (remaining > 0) {
            const size_t srcIdx = read % ringBytes;
            const size_t contiguous = std::min(remaining, ringBytes - srcIdx);
            std::memcpy(dest + dstOffset, buffer_.data() + srcIdx, contiguous);
            read += contiguous;
            dstOffset += contiguous;
            remaining -= contiguous;
        }
        readIndex_.store(read, std::memory_order_release);
        return toRead;
    }

    size_t popFramesAsFloat(float* dest,
                            size_t maxFrames,
                            uint8_t bitResolution) {
        if (maxFrames == 0 || bytesPerFrame_ == 0) return 0;
        const size_t write = writeIndex_.load(std::memory_order_acquire);
        size_t read = readIndex_.load(std::memory_order_relaxed);
        const size_t availableBytes = write - read;
        const size_t availableFrames = availableBytes / bytesPerFrame_;
        const size_t toRead = availableFrames < maxFrames ? availableFrames : maxFrames;
        if (toRead == 0) return 0;

        size_t dstFrame = 0;
        size_t remainingFrames = toRead;
        while (remainingFrames > 0) {
            const size_t ringBytes = capacity_ * bytesPerFrame_;
            const size_t srcIdx = read % ringBytes;
            const size_t contiguousBytes =
                std::min(remainingFrames * bytesPerFrame_, ringBytes - srcIdx);
            const size_t contiguousFrames = contiguousBytes / bytesPerFrame_;
            pcmBytesToFloatInterleaved(
                buffer_.data() + srcIdx,
                contiguousBytes,
                dest + dstFrame * static_cast<size_t>(channels_),
                channels_,
                subframeBytes_,
                bitResolution);
            read += contiguousFrames * bytesPerFrame_;
            dstFrame += contiguousFrames;
            remainingFrames -= contiguousFrames;
        }
        readIndex_.store(read, std::memory_order_release);
        return toRead;
    }

    size_t availableFrames() const {
        const size_t write = writeIndex_.load(std::memory_order_acquire);
        const size_t read = readIndex_.load(std::memory_order_acquire);
        if (write <= read || bytesPerFrame_ == 0) return 0;
        return (write - read) / bytesPerFrame_;
    }

    void reset() {
        writeIndex_.store(0, std::memory_order_relaxed);
        readIndex_.store(0, std::memory_order_relaxed);
    }

    uint8_t channelCount() const { return channels_; }

private:
    static void pcmBytesToFloatInterleaved(const uint8_t* src,
                                           size_t byteCount,
                                           float* dest,
                                           uint8_t channels,
                                           uint8_t subframeBytes,
                                           uint8_t bitResolution) {
        const size_t bpf = static_cast<size_t>(channels) * subframeBytes;
        if (bpf == 0) return;
        const size_t frameCount = byteCount / bpf;
        for (size_t f = 0; f < frameCount; ++f) {
            const uint8_t* frame = src + f * bpf;
            float* out = dest + f * static_cast<size_t>(channels);
            for (uint8_t c = 0; c < channels; ++c) {
                const uint8_t* p = frame + static_cast<size_t>(c) * subframeBytes;
                if (subframeBytes == 4 && bitResolution >= 24) {
                    int32_t v;
                    std::memcpy(&v, p, sizeof(v));
                    out[c] = static_cast<float>(v) / 2147483648.0f;
                } else if (subframeBytes == 3) {
                    int32_t v = static_cast<int32_t>(p[0]) |
                                (static_cast<int32_t>(p[1]) << 8) |
                                (static_cast<int32_t>(p[2]) << 16);
                    if (v & 0x800000) {
                        v |= ~0xffffff;
                    }
                    out[c] = static_cast<float>(v) / 8388608.0f;
                } else if (subframeBytes == 2) {
                    int16_t v;
                    std::memcpy(&v, p, sizeof(v));
                    out[c] = static_cast<float>(v) / 32768.0f;
                } else {
                    out[c] = 0.0f;
                }
            }
        }
    }

    uint8_t channels_;
    uint8_t subframeBytes_;
    size_t bytesPerFrame_;
    size_t capacity_;
    std::vector<uint8_t> buffer_;
    std::atomic<size_t> writeIndex_{0};
    std::atomic<size_t> readIndex_{0};
};

}  // namespace openmultitrack
