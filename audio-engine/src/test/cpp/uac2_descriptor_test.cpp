#include "uac2/uac2_descriptor.h"

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <string>
#include <vector>

namespace {

int g_failures = 0;

void expect(bool condition, const char* message) {
    if (!condition) {
        std::fprintf(stderr, "FAIL: %s\n", message);
        ++g_failures;
    }
}

std::vector<uint8_t> readFile(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) return {};
    return std::vector<uint8_t>(std::istreambuf_iterator<char>(in), {});
}

uint8_t maxChannels(const std::vector<openmultitrack::uac2::Uac2AltSetting>& alts) {
    uint8_t max = 0;
    for (const auto& alt : alts) {
        if (alt.format.valid && alt.format.channels > max) {
            max = alt.format.channels;
        }
    }
    return max;
}

void testFlow8Fixture(const std::string& fixture_path) {
    const std::vector<uint8_t> bytes = readFile(fixture_path);
    expect(!bytes.empty(), "flow8 fixture readable");
    if (bytes.empty()) return;

    const openmultitrack::uac2::Uac2DeviceCaps caps =
        openmultitrack::uac2::parseConfigDescriptor(bytes.data(), bytes.size());

    expect(caps.parse_ok, "flow8 parse_ok");
    expect(caps.uac_version == 2, "flow8 UAC2");
    expect(maxChannels(caps.capture_alts) >= 10, "flow8 >=10 capture channels");
    expect(maxChannels(caps.playback_alts) >= 4, "flow8 >=4 playback channels");

    const openmultitrack::uac2::Uac2AltSetting best_capture =
        openmultitrack::uac2::selectBestAlt(caps.capture_alts, 10, 48'000);
    expect(best_capture.format.valid, "flow8 best capture alt");
    expect(best_capture.format.channels >= 10, "flow8 best capture >=10ch");
    expect(best_capture.is_input, "flow8 capture is input");

    const openmultitrack::uac2::Uac2AltSetting best_playback =
        openmultitrack::uac2::selectBestAlt(caps.playback_alts, 4, 48'000);
    expect(best_playback.format.valid, "flow8 best playback alt");
    expect(best_playback.format.channels >= 4, "flow8 best playback >=4ch");
    expect(!best_playback.is_input, "flow8 playback is output");
}

// Minimal synthetic UAC2 config: 18ch capture + 18ch playback (XR18 stand-in).
std::vector<uint8_t> buildSyntheticXr18Descriptor() {
    std::vector<uint8_t> d;

    auto add = [&](std::initializer_list<uint8_t> bytes) {
        d.insert(d.end(), bytes.begin(), bytes.end());
    };

  // Config header (9 bytes) — total length patched later.
    const size_t cfg_off = d.size();
    add({0x09, 0x02, 0x00, 0x00, 0x01, 0x01, 0x00, 0x80, 0xFA});

    auto add_iface = [&](uint8_t num, uint8_t alt, uint8_t endpoints, uint8_t protocol) {
        add({0x09, 0x04, num, alt, endpoints, 0x01, 0x02, protocol, 0x00});
    };

    auto add_as_general = [&](uint8_t terminal, uint8_t channels) {
        add({0x10, 0x24, 0x01, terminal, 0x00, 0x01,
             0x01, 0x00, 0x00, 0x00,  // bmFormats (4 bytes)
             channels,
             0x00, 0x00, 0x00, 0x00, 0x00});
    };

    auto add_type_i = [&](uint8_t subslot, uint8_t bits) {
        add({0x06, 0x24, 0x02, 0x01, subslot, bits});
    };

    auto add_ep = [&](uint8_t addr, uint16_t max_packet) {
        add({0x07, 0x05, addr, 0x05,
             static_cast<uint8_t>(max_packet & 0xFF),
             static_cast<uint8_t>((max_packet >> 8) & 0xFF),
             0x01});
    };

    // Playback streaming alt 1 — 18ch OUT.
    add_iface(1, 1, 1, openmultitrack::uac2::kUsbProtocolUac2);
    add_as_general(0x02, 18);
    add_type_i(4, 32);
    add_ep(0x01, 512);

    // Capture streaming alt 1 — 18ch IN.
    add_iface(2, 1, 1, openmultitrack::uac2::kUsbProtocolUac2);
    add_as_general(0x0B, 18);
    add_type_i(4, 32);
    add_ep(0x81, 1024);

    const uint16_t total = static_cast<uint16_t>(d.size());
    d[cfg_off + 2] = static_cast<uint8_t>(total & 0xFF);
    d[cfg_off + 3] = static_cast<uint8_t>((total >> 8) & 0xFF);
    return d;
}

void testSyntheticXr18() {
    const std::vector<uint8_t> bytes = buildSyntheticXr18Descriptor();
    const openmultitrack::uac2::Uac2DeviceCaps caps =
        openmultitrack::uac2::parseConfigDescriptor(bytes.data(), bytes.size());

    expect(caps.parse_ok, "xr18 synthetic parse_ok");
    expect(maxChannels(caps.capture_alts) == 18, "xr18 synthetic 18ch capture");
    expect(maxChannels(caps.playback_alts) == 18, "xr18 synthetic 18ch playback");

    const auto playback =
        openmultitrack::uac2::selectBestAlt(caps.playback_alts, 18, 48'000);
    expect(playback.format.channels == 18, "xr18 virtual soundcheck playback alt");
}

void testPrefersLargeIsochPacketOverDummyAlt() {
    openmultitrack::uac2::Uac2AltSetting dummy{};
    dummy.format.valid = true;
    dummy.format.channels = 18;
    dummy.format.subframe_bytes = 4;
    dummy.format.bit_resolution = 24;
    dummy.format.sample_rate_hz = 48'000;
    dummy.interface_number = 1;
    dummy.endpoint_address = 0x81;
    dummy.max_packet_size = 4;
    dummy.is_input = true;

    openmultitrack::uac2::Uac2AltSetting streaming = dummy;
    streaming.interface_number = 2;
    streaming.endpoint_address = 0x82;
    streaming.max_packet_size = 576;

    const std::vector<openmultitrack::uac2::Uac2AltSetting> alts = {dummy, streaming};
    const auto best = openmultitrack::uac2::selectBestAlt(alts, 18, 48'000);
    expect(best.format.valid, "xr18 dummy+streaming best alt valid");
    expect(best.max_packet_size == 576, "xr18 prefers streaming isoch packet size");
    expect(best.endpoint_address == 0x82, "xr18 streaming endpoint");
}

}  // namespace

int main(int argc, char** argv) {
    std::string fixture = "audio-engine/src/test/resources/uac2/flow8_recording_mode.bin";
    if (argc > 1) {
        fixture = argv[1];
    }

    testFlow8Fixture(fixture);
    testSyntheticXr18();
    testPrefersLargeIsochPacketOverDummyAlt();

    if (g_failures > 0) {
        std::fprintf(stderr, "%d test(s) failed\n", g_failures);
        return EXIT_FAILURE;
    }
    std::printf("uac2_descriptor_test: all passed\n");
    return EXIT_SUCCESS;
}
