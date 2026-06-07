#pragma once

#include <cstdint>
#include <vector>

namespace openmultitrack::uac2 {

constexpr uint8_t kUsbClassAudio = 0x01;
constexpr uint8_t kUsbSubclassAudioControl = 0x01;
constexpr uint8_t kUsbSubclassAudioStreaming = 0x02;
constexpr uint8_t kUsbProtocolUac2 = 0x20;
constexpr uint8_t kUsbProtocolUac1 = 0x00;

constexpr uint8_t kCsInterface = 0x24;
constexpr uint8_t kCsEndpoint = 0x25;

constexpr uint8_t kAsSubtypeFormatType = 0x02;
constexpr uint8_t kAsSubtypeGeneral = 0x01;

constexpr uint8_t kFormatTypeI = 0x01;

constexpr uint8_t kEndpointAttrIsochronous = 0x01;
constexpr uint8_t kEndpointDirInMask = 0x80;

struct Uac2Format {
    uint8_t channels = 0;
    uint32_t sample_rate_hz = 0;
    uint8_t bit_resolution = 0;
    uint8_t subframe_bytes = 0;
    bool valid = false;
};

struct Uac2AltSetting {
    uint8_t interface_number = 0;
    uint8_t alternate_setting = 0;
    uint8_t endpoint_address = 0;
    uint16_t max_packet_size = 0;
    bool is_input = false;
    Uac2Format format{};
};

struct Uac2DeviceCaps {
    uint8_t uac_version = 0;
    std::vector<Uac2AltSetting> capture_alts;
    std::vector<Uac2AltSetting> playback_alts;
    bool parse_ok = false;
};

}  // namespace openmultitrack::uac2
