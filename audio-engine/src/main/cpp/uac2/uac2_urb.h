#pragma once

#include "android_usb_fs.h"
#include "uac2_types.h"

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

namespace openmultitrack::uac2 {

struct IsoUrbLayout {
    int num_packets = 1;
    int per_packet_bytes = 0;
    int buffer_length = 0;
};

struct UrbContext {
    std::vector<uint8_t> storage;
    usbdevfs_urb* urb = nullptr;
};

inline IsoUrbLayout layoutForAlt(const Uac2AltSetting& alt,
                                 bool capture,
                                 bool micro_packets = false) {
    IsoUrbLayout layout;
    const int max_packet = static_cast<int>(alt.max_packet_size);
    layout.num_packets = 1;
    layout.per_packet_bytes = max_packet;
    layout.buffer_length = max_packet;

    if (!capture || !micro_packets) {
        return layout;
    }

    // Some hosts accept IN isoch only when split into frame-sized micro-packets.
    const size_t bpf =
        static_cast<size_t>(alt.format.channels) * alt.format.subframe_bytes;
    if (bpf > 0 && max_packet >= static_cast<int>(bpf) && (max_packet % bpf) == 0) {
        const int frames_per_packet = max_packet / static_cast<int>(bpf);
        if (frames_per_packet > 1) {
            layout.num_packets = frames_per_packet;
            layout.per_packet_bytes = static_cast<int>(bpf);
            layout.buffer_length = max_packet;
        }
    }
    return layout;
}

inline usbdevfs_urb* allocIsoUrb(const IsoUrbLayout& layout, UrbContext* ctx) {
    constexpr size_t kPageSize = 4096;
    const size_t header = sizeof(usbdevfs_urb) +
        sizeof(usbdevfs_iso_packet_desc) * static_cast<size_t>(layout.num_packets);
    const size_t raw_buf = header + static_cast<size_t>(layout.buffer_length);
    const size_t padded = ((raw_buf + kPageSize - 1) / kPageSize) * kPageSize + kPageSize;
    ctx->storage.resize(padded);
    ctx->urb = reinterpret_cast<usbdevfs_urb*>(ctx->storage.data());
    const uintptr_t buf_addr =
        (reinterpret_cast<uintptr_t>(ctx->storage.data() + header) + kPageSize - 1) &
        ~(static_cast<uintptr_t>(kPageSize) - 1);
    ctx->urb->buffer = reinterpret_cast<void*>(buf_addr);
    return ctx->urb;
}

inline void initIsoUrb(usbdevfs_urb* urb,
                       const IsoUrbLayout& layout,
                       uint8_t endpoint,
                       void* usercontext) {
    std::memset(urb, 0, sizeof(usbdevfs_urb));
    urb->type = USBDEVFS_URB_TYPE_ISO;
    urb->endpoint = endpoint;
    urb->flags = USBDEVFS_URB_ISO_ASAP;
    urb->start_frame = -1;
    urb->buffer_length = layout.buffer_length;
    urb->number_of_packets = layout.num_packets;
    urb->usercontext = usercontext;
    for (int p = 0; p < layout.num_packets; ++p) {
        urb->iso_frame_desc[p].length = static_cast<unsigned int>(layout.per_packet_bytes);
        urb->iso_frame_desc[p].actual_length = 0;
        urb->iso_frame_desc[p].status = 0;
    }
}

}  // namespace openmultitrack::uac2
