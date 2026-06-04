#pragma once

#include <atomic>
#include <cstddef>
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
        const size_t write = writeIndex_.load(std::memory_order_relaxed);
        const size_t read = readIndex_.load(std::memory_order_acquire);
        if (write - read >= capacity_) {
            return false;
        }
        const size_t idx = (write % capacity_) * static_cast<size_t>(channelCount_);
        for (int32_t c = 0; c < channelCount_; ++c) {
            buffer_[idx + static_cast<size_t>(c)] = interleavedFrame[c];
        }
        writeIndex_.store(write + 1, std::memory_order_release);
        return true;
    }

    /** Consumer: read up to maxFrames. Returns frames read. */
    size_t popFrames(float* dest, size_t maxFrames) {
        const size_t write = writeIndex_.load(std::memory_order_acquire);
        size_t read = readIndex_.load(std::memory_order_relaxed);
        const size_t available = write - read;
        const size_t toRead = available < maxFrames ? available : maxFrames;
        const size_t channels = static_cast<size_t>(channelCount_);
        for (size_t f = 0; f < toRead; ++f) {
            const size_t src = (read % capacity_) * channels;
            const size_t dst = f * channels;
            for (size_t c = 0; c < channels; ++c) {
                dest[dst + c] = buffer_[src + c];
            }
            ++read;
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
