#include "uac2_descriptor.h"

#include "../audio_log.h"

#include <algorithm>

namespace openmultitrack::uac2 {

namespace {

bool parseAsGeneral(const uint8_t* data, size_t len, uint8_t protocol, Uac2Format* out) {
    if (len < 2 || data[1] != kCsInterface || data[2] != kAsSubtypeGeneral) return false;

    // UAC2 AS_GENERAL (16 bytes): bmFormats is 4 bytes; bNrChannels follows at offset 10.
    if (protocol == kUsbProtocolUac2 && len >= 16) {
        out->channels = data[10];
        out->valid = out->channels > 0;
        return out->valid;
    }

    // UAC1 AS_GENERAL does not carry channel count; format type follows.
    return false;
}

bool parseTypeIFormat(const uint8_t* data, size_t len, Uac2Format* in_out) {
    if (len < 6 || data[0] < 6) return false;
    if (data[1] != kCsInterface || data[2] != kAsSubtypeFormatType) return false;
    if (data[3] != kFormatTypeI) return false;

    Uac2Format fmt = *in_out;

    // UAC2 Type I format is 6 bytes: subslot + bit resolution only.
    if (len == 6) {
        fmt.subframe_bytes = data[4];
        fmt.bit_resolution = data[5];
    } else if (len >= 8) {
        // UAC1 Type I: channels, subslot, bit resolution, then sample rate(s).
        fmt.channels = data[4];
        fmt.subframe_bytes = data[5];
        fmt.bit_resolution = data[6];

        size_t offset = 8;
        if (len >= offset + 3) {
            fmt.sample_rate_hz = static_cast<uint32_t>(data[offset]) |
                                 (static_cast<uint32_t>(data[offset + 1]) << 8) |
                                 (static_cast<uint32_t>(data[offset + 2]) << 16);
        }
    } else {
        return false;
    }

    if (fmt.sample_rate_hz == 0) {
        fmt.sample_rate_hz = 48'000;
    }

    fmt.valid = fmt.channels > 0 && fmt.subframe_bytes > 0;
    *in_out = fmt;
    return fmt.valid;
}

void commitAlt(const Uac2AltSetting& alt, Uac2DeviceCaps* caps, uint8_t protocol) {
    if (!alt.format.valid || alt.endpoint_address == 0) return;
    if (caps->uac_version == 0) {
        caps->uac_version = (protocol == kUsbProtocolUac2) ? 2 : 1;
    }
    if (alt.is_input) {
        caps->capture_alts.push_back(alt);
    } else {
        caps->playback_alts.push_back(alt);
    }
}

}  // namespace

Uac2DeviceCaps parseConfigDescriptor(const uint8_t* data, size_t length) {
    Uac2DeviceCaps caps;
    if (data == nullptr || length < 9) {
        OMT_LOGE("uac2 parse: descriptor too short (%zu)", length);
        return caps;
    }

    uint8_t iface_num = 0;
    uint8_t iface_alt = 0;
    uint8_t iface_subclass = 0;
    uint8_t iface_protocol = 0;
    Uac2Format iface_format{};
    Uac2AltSetting pending{};

    size_t offset = 0;
    while (offset + 2 <= length) {
        const uint8_t desc_len = data[offset];
        if (desc_len < 2 || offset + desc_len > length) {
            OMT_LOGW("uac2 parse: invalid desc len %u at offset %zu", desc_len, offset);
            break;
        }

        const uint8_t desc_type = data[offset + 1];

        if (desc_type == 0x04) {
            if (iface_subclass == kUsbSubclassAudioStreaming && iface_alt > 0) {
                commitAlt(pending, &caps, iface_protocol);
            }
            pending = {};

            iface_num = data[offset + 2];
            iface_alt = data[offset + 3];
            iface_subclass = data[offset + 6];
            iface_protocol = data[offset + 7];
            iface_format = {};
            pending.interface_number = iface_num;
            pending.alternate_setting = iface_alt;
            pending.format = {};
        } else if (desc_type == kCsInterface && iface_subclass == kUsbSubclassAudioStreaming) {
            const uint8_t subtype = data[offset + 2];
            if (subtype == kAsSubtypeGeneral) {
                Uac2Format fmt = pending.format;
                if (parseAsGeneral(data + offset, desc_len, iface_protocol, &fmt)) {
                    iface_format = fmt;
                    pending.format = fmt;
                }
            } else if (subtype == kAsSubtypeFormatType) {
                Uac2Format fmt = pending.format;
                if (parseTypeIFormat(data + offset, desc_len, &fmt)) {
                    iface_format = fmt;
                    pending.format = fmt;
                }
            }
        } else if (desc_type == 0x05 && iface_subclass == kUsbSubclassAudioStreaming &&
                   iface_alt > 0) {
            const uint8_t addr = data[offset + 2];
            const uint8_t attr = data[offset + 3];
            if ((attr & 0x03) == kEndpointAttrIsochronous) {
                pending.endpoint_address = addr;
                pending.max_packet_size = static_cast<uint16_t>(data[offset + 4]) |
                                          (static_cast<uint16_t>(data[offset + 5]) << 8);
                pending.is_input = (addr & kEndpointDirInMask) != 0;
                pending.format = iface_format;
                commitAlt(pending, &caps, iface_protocol);
            }
        }

        offset += desc_len;
    }

    caps.parse_ok = !caps.capture_alts.empty() || !caps.playback_alts.empty();

    OMT_LOGI("uac2 parse: uac=%d capture_alts=%zu playback_alts=%zu ok=%d",
             caps.uac_version,
             caps.capture_alts.size(),
             caps.playback_alts.size(),
             caps.parse_ok ? 1 : 0);

    for (const auto& alt : caps.capture_alts) {
        OMT_LOGI("  capture iface=%u alt=%u ep=0x%02x %uch @ %uHz %ubit pkt=%u",
                 alt.interface_number,
                 alt.alternate_setting,
                 alt.endpoint_address,
                 alt.format.channels,
                 alt.format.sample_rate_hz,
                 alt.format.bit_resolution,
                 alt.max_packet_size);
    }
    for (const auto& alt : caps.playback_alts) {
        OMT_LOGI("  playback iface=%u alt=%u ep=0x%02x %uch @ %uHz %ubit pkt=%u",
                 alt.interface_number,
                 alt.alternate_setting,
                 alt.endpoint_address,
                 alt.format.channels,
                 alt.format.sample_rate_hz,
                 alt.format.bit_resolution,
                 alt.max_packet_size);
    }

    return caps;
}

Uac2AltSetting selectBestAlt(const std::vector<Uac2AltSetting>& alts,
                             uint8_t min_channels,
                             uint32_t sample_rate_hz) {
    const Uac2AltSetting* best = nullptr;
    for (const auto& alt : alts) {
        if (!alt.format.valid) continue;
        if (alt.format.channels < min_channels) continue;
        if (sample_rate_hz > 0 && alt.format.sample_rate_hz > 0 &&
            alt.format.sample_rate_hz != sample_rate_hz) {
            continue;
        }
        const size_t bytes_per_frame =
            static_cast<size_t>(alt.format.channels) * alt.format.subframe_bytes;
        if (bytes_per_frame > 0 && alt.max_packet_size < bytes_per_frame) {
            continue;
        }
        // sample_rate_hz == 0 on alt means "any" (UAC2 implicit 48 kHz default).
        if (best == nullptr || alt.format.channels > best->format.channels ||
            (alt.format.channels == best->format.channels &&
             alt.max_packet_size > best->max_packet_size)) {
            best = &alt;
        }
    }
    return best != nullptr ? *best : Uac2AltSetting{};
}

}  // namespace openmultitrack::uac2
