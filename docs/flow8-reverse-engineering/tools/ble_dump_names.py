#!/usr/bin/env python3
"""Connect to FLOW 8 over BLE, trigger a state dump, decode and print channel names."""
import asyncio, os, sys
from bleak import BleakScanner, BleakClient

CHAR = "0034594a-a8e7-4b1a-a6b1-cd5243059a57"

# Command type codes (from libcom_musicgroup_xairbt.so descriptor table).
T_HANDSHAKE_HOST   = 0x35  # device -> client broadcast (host id, pairing flag, fw)
T_HANDSHAKE_REPLY  = 0x36  # device -> client (empty payload)
T_GET_MIXER_STATE  = 0x37  # client -> device (empty)  : triggers the state dump
T_MIXER_STATE      = 0x38  # device -> client          : the state dump
T_HANDSHAKE_CLIENT = 0x39  # client -> device (16-byte client id)

CLIENT_ID_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                              ".flow8_client_id")

CHANNEL_COUNT = 7
DUMP_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                         "flow8_dump.bin")


def frame(cmd_type: int, payload: bytes = b"") -> bytes:
    """XAIR-BT wire framing for a single-fragment command:
    [type][chunk_count=0x01][payload...][checksum], checksum = sum(preceding) & 0xFF."""
    body = bytes([cmd_type, 0x01]) + payload
    return body + bytes([sum(body) & 0xFF])


def load_or_make_client_id() -> bytes:
    """16-byte client id. Any non-zero value is accepted (xairbt_is_uid_valid).
    Persisted so a paired device keeps recognising us across runs."""
    try:
        with open(CLIENT_ID_FILE, "rb") as fh:
            cid = fh.read(16)
        if len(cid) == 16 and any(cid):
            return cid
    except FileNotFoundError:
        pass
    cid = os.urandom(16)
    while not any(cid):
        cid = os.urandom(16)
    with open(CLIENT_ID_FILE, "wb") as fh:
        fh.write(cid)
    return cid

def extract_names(buf, min_len=2, max_len=18):
    """Pull length-prefixed ASCII strings from the raw MixerState structure.
    Each channel name is stored as [len][len printable-ASCII bytes]."""
    names = []
    i = 0
    n = len(buf)
    while i < n:
        L = buf[i]
        if min_len <= L <= max_len and i + 1 + L <= n:
            s = buf[i + 1 : i + 1 + L]
            if all(0x20 <= b <= 0x7E for b in s):
                names.append(s.decode("ascii"))
                i += 1 + L
                continue
        i += 1
    return names

async def main():
    print("Scanning for FLOW 8 (up to 15s)...", flush=True)
    dev = await BleakScanner.find_device_by_filter(
        lambda d, a: "FLOW" in (d.name or "").upper(), timeout=15)
    if not dev:
        print("ERROR: FLOW 8 not found. Close the phone app and retry.")
        sys.exit(1)
    print(f"Found: {dev.name}  {dev.address}", flush=True)

    client_id = load_or_make_client_id()
    print(f"Client ID: {client_id.hex()}", flush=True)

    # Events for the event-driven state machine.
    got_handshake_host  = asyncio.Event()  # device sent 0x35
    got_handshake_reply = asyncio.Event()  # device sent 0x36
    dump_done           = asyncio.Event()  # full SysEx assembled from 0x38 chunks

    frags = {}            # seq index -> payload bytes
    frag_total = [0]      # expected fragment count (from chunk header)
    notif_count = 0

    loop = asyncio.get_event_loop()

    def on_notify(char, data: bytearray):
        nonlocal notif_count
        notif_count += 1
        typ = data[0] if data else 0xFF
        print(f"  NOTIF 0x{typ:02X} len={len(data)} {data.hex()}", flush=True)

        if typ == T_HANDSHAKE_HOST:
            got_handshake_host.set()

        elif typ == T_HANDSHAKE_REPLY:
            got_handshake_reply.set()

        elif typ == T_MIXER_STATE and len(data) >= 4:
            # Fragment header: [0x38][count][0x01][seq], payload follows.
            count = data[1]
            seq   = data[3]
            frag_total[0] = count
            frags[seq] = bytes(data[4:])
            if count and len(frags) >= count:
                loop.call_soon_threadsafe(dump_done.set)

    disconnected = asyncio.Event()

    def on_disconnect(client):
        print("  [disconnected]", flush=True)
        disconnected.set()
        # unblock any pending wait
        loop.call_soon_threadsafe(got_handshake_host.set)
        loop.call_soon_threadsafe(got_handshake_reply.set)
        loop.call_soon_threadsafe(dump_done.set)

    async with BleakClient(dev, timeout=20, disconnected_callback=on_disconnect) as c:
        char_obj = c.services.get_characteristic(CHAR)
        if not char_obj:
            print("Characteristic not found on device."); sys.exit(1)
        print(f"Characteristic props: {char_obj.properties}", flush=True)

        async def ble_write(data: bytes, label: str):
            print(f"  WRITE {label}: {data.hex()}", flush=True)
            await c.write_gatt_char(char_obj, data, response=True)

        # Step 1: subscribe so we receive the device's 0x35 HandshakeHost broadcast.
        print("Subscribing to notifications...", flush=True)
        await c.start_notify(CHAR, on_notify)

        # Step 2: wait for the device to send its 0x35 HandshakeHost.
        print("Waiting for HandshakeHost (0x35) from device (up to 15s)...", flush=True)
        try:
            await asyncio.wait_for(got_handshake_host.wait(), timeout=15)
        except asyncio.TimeoutError:
            print("Timeout: no 0x35 HandshakeHost received. "
                  "Is pairing mode enabled on the mixer?")
            sys.exit(5)
        if disconnected.is_set():
            print("Device disconnected before handshake."); sys.exit(4)

        # Step 3: send 0x39 HandshakeClient with our 16-byte client ID.
        await ble_write(frame(T_HANDSHAKE_CLIENT, client_id), "HandshakeClient(0x39)")
        await asyncio.sleep(0.15)
        if disconnected.is_set():
            print("Device disconnected after HandshakeClient."); sys.exit(4)

        # Step 4: wait for the device's 0x36 HandshakeReply.
        print("Waiting for HandshakeReply (0x36) from device (up to 10s)...", flush=True)
        try:
            await asyncio.wait_for(got_handshake_reply.wait(), timeout=10)
        except asyncio.TimeoutError:
            print("Timeout: no 0x36 HandshakeReply. Auth may have been rejected.")
            sys.exit(5)
        if disconnected.is_set():
            print("Device disconnected after HandshakeClient."); sys.exit(4)

        # Step 5: send 0x37 GetMixerState — device will stream 0x38 chunks.
        await ble_write(frame(T_GET_MIXER_STATE), "GetMixerState(0x37)")

        # Step 6: collect 0x38 MixerState chunks until SysEx is complete.
        print("Waiting for state dump (0x38 chunks, up to 30s)...", flush=True)
        try:
            await asyncio.wait_for(dump_done.wait(), timeout=30)
        except asyncio.TimeoutError:
            print("Timeout waiting for complete SysEx dump.", flush=True)

        await c.stop_notify(CHAR)

    buf = b"".join(frags[i] for i in sorted(frags))
    print(f"\nNotifications received: {notif_count}, "
          f"fragments: {len(frags)}/{frag_total[0]}, dump bytes: {len(buf)}")
    if not buf:
        print("No MixerState data received.")
        sys.exit(2)

    with open(DUMP_FILE, "wb") as fh:
        fh.write(buf)
    print(f"Raw dump saved to {DUMP_FILE}")

    names = extract_names(buf)
    if not names:
        print(f"No names parsed. Dump preview: {buf[:64].hex()}")
        sys.exit(3)

    print("\nChannel names:")
    print("  " + "-" * 28)
    labels = ["Ch1", "Ch2", "Ch3", "Ch4", "Ch5", "Ch6", "Ch7/USB-BT"]
    for i, lbl in enumerate(labels):
        name = names[i] if i < len(names) else "(?)"
        print(f"  {lbl:>10}  \"{name}\"")
    if len(names) > CHANNEL_COUNT:
        print("\n  Other strings found in dump:")
        for extra in names[CHANNEL_COUNT:]:
            print(f"             \"{extra}\"")

if __name__ == "__main__":
    asyncio.run(main())
