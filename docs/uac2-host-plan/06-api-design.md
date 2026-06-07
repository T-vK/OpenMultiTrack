# API Design

## C++ public API (`namespace openmultitrack::uac2`)

### Descriptor / probe (Phase 1)

```cpp
struct Uac2Format {
    uint8_t channels;
    uint32_t sample_rate_hz;
    uint8_t bit_resolution;
    uint8_t subframe_bytes;
    bool valid;
};

struct Uac2AltSetting {
    uint8_t alternate_setting;
    uint8_t interface_number;
    uint8_t endpoint_address;
    uint16_t max_packet_size;
    uint8_t channels;
    Uac2Format format;
};

struct Uac2DeviceCaps {
    std::vector<Uac2AltSetting> capture_alts;
    std::vector<Uac2AltSetting> playback_alts;
    uint16_t vendor_id;
    uint16_t product_id;
    uint8_t uac_version;  // 1 or 2
};

Uac2DeviceCaps parseConfigDescriptor(const uint8_t* data, size_t length);
Uac2AltSetting selectBestAlt(const std::vector<Uac2AltSetting>& alts,
                             uint8_t min_channels, uint32_t sample_rate_hz);
```

### Streaming (Phase 2–3)

```cpp
class Uac2Capture {
public:
    enum class State { Idle, Running, Error };
    Status open(int usb_fd, const Uac2AltSetting& alt);
    void start();
    void stop();
    size_t readFrames(float* dest, size_t max_frames);
    uint64_t droppedFrames() const;
};

class Uac2Playback {
public:
    Status open(int usb_fd, const Uac2AltSetting& alt);
    size_t writeFrames(const float* src, size_t frame_count);
    uint64_t underrunFrames() const;
};
```

## JNI (`org.openmultitrack.audio.NativeUac2Probe`)

```kotlin
object NativeUac2Probe {
    fun parseConfigDescriptor(raw: ByteArray): Uac2DeviceCaps
    fun selectCaptureAlt(caps: Uac2DeviceCaps, minChannels: Int, sampleRate: Int): Uac2AltSetting?
}
```

## Kotlin domain (`domain` module)

```kotlin
data class Uac2DeviceCaps(
    val captureAlts: List<Uac2AltSetting>,
    val playbackAlts: List<Uac2AltSetting>,
    val uacVersion: Int,
)

data class AudioBackendCapabilities(
    val oboe: AudioEndpointProbe?,
    val uac2: Uac2DeviceCaps?,
    val recommendedRecordBackend: AudioBackend,
)
```

## Integration with probe UI

Extend `FullUsbProbeResult`:

```kotlin
data class FullUsbProbeResult(
    val usb: UsbAudioDeviceDescriptor,
    val input: AudioEndpointProbe?,
    val output: AudioEndpointProbe?,
    val uac2: Uac2DeviceCaps? = null,  // new
    val note: String? = null,
)
```

UI shows:

```
Oboe input:  2ch @ 48kHz (start failed)
UAC2 offers: 10ch @ 48kHz (AS iface 2, alt 3)
Backend:     UAC2 recommended for recording
```

## Error codes

| Code | Meaning |
|------|---------|
| `OK` | Success |
| `PARSE_ERROR` | Malformed descriptor |
| `NO_STREAM` | No matching AS interface |
| `CLAIM_FAILED` | Interface busy (kernel held) |
| `TRANSFER_ERROR` | Isochronous stall |
| `UNSUPPORTED_FORMAT` | Non-PCM Type I |
