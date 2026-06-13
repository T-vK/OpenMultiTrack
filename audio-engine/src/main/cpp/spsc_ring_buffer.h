#pragma once

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstring>
#include <vector>

namespace openmultitrack {

/** Single-producer single-consumer float ring buffer (audio callback → writer thread). */
class SpscRingBuffer {
public:
    explicit SpscRingBuffer(size_t capacityFrames, int32_t channelCount)
        : channelCount_(channelCount),
          capacity_(capacityFrames),
          buffer_(capacityFrames * static_cast<size_t>(channelCount), 0.0f) {}

    /** Producer: write one frame (interleaved). Returns false if full. */
    bool pushFrame(const float* interleavedFrame) {
        return pushFrames(interleavedFrame, 1) == 1;
    }

    /** Producer: write up to frameCount interleaved frames. Returns frames written. */
    size_t pushFrames(const float* interleavedFrames, size_t frameCount) {
        if (frameCount == 0) return 0;
        const size_t write = writeIndex_.load(std::memory_order_relaxed);
        const size_t read = readIndex_.load(std::memory_order_acquire);
        const size_t freeFrames = capacity_ - (write - read);
        const size_t toWrite = frameCount < freeFrames ? frameCount : freeFrames;
        if (toWrite == 0) return 0;

        const size_t channels = static_cast<size_t>(channelCount_);
        const size_t frameSamples = channels;
        size_t srcFrame = 0;
        size_t dstWrite = write;
        size_t remaining = toWrite;
        while (remaining > 0) {
            const size_t dstIdx = (dstWrite % capacity_) * frameSamples;
            const size_t contiguous =
                std::min(remaining, capacity_ - (dstWrite % capacity_));
            const size_t sampleCount = contiguous * frameSamples;
            std::memcpy(
                buffer_.data() + dstIdx,
                interleavedFrames + srcFrame * frameSamples,
                sampleCount * sizeof(float));
            srcFrame += contiguous;
            dstWrite += contiguous;
            remaining -= contiguous;
        }
        writeIndex_.store(write + toWrite, std::memory_order_release);
        return toWrite;
    }

    /** Consumer: read up to maxFrames. Returns frames read. */
    size_t popFrames(float* dest, size_t maxFrames) {
        if (maxFrames == 0) return 0;
        const size_t write = writeIndex_.load(std::memory_order_acquire);
        size_t read = readIndex_.load(std::memory_order_relaxed);
        const size_t available = write - read;
        const size_t toRead = available < maxFrames ? available : maxFrames;
        if (toRead == 0) return 0;

        const size_t channels = static_cast<size_t>(channelCount_);
        const size_t frameSamples = channels;
        size_t dstFrame = 0;
        size_t remaining = toRead;
        while (remaining > 0) {
            const size_t srcIdx = (read % capacity_) * frameSamples;
            const size_t contiguous =
                std::min(remaining, capacity_ - (read % capacity_));
            const size_t sampleCount = contiguous * frameSamples;
            std::memcpy(
                dest + dstFrame * frameSamples,
                buffer_.data() + srcIdx,
                sampleCount * sizeof(float));
            read += contiguous;
            dstFrame += contiguous;
            remaining -= contiguous;
        }
        readIndex_.store(read, std::memory_order_release);
        return toRead;
    }

    size_t availableFrames() const {
        const size_t write = writeIndex_.load(std::memory_order_acquire);
        const size_t read = readIndex_.load(std::memory_order_acquire);
        return write > read ? write - read : 0;
    }

    void reset() {
        writeIndex_.store(0, std::memory_order_relaxed);
        readIndex_.store(0, std::memory_order_relaxed);
    }

    int32_t channelCount() const { return channelCount_; }

private:
    int32_t channelCount_;
    size_t capacity_;
    std::vector<float> buffer_;
    std::atomic<size_t> writeIndex_{0};
    std::atomic<size_t> readIndex_{0};
};

}  // namespace openmultitrack
