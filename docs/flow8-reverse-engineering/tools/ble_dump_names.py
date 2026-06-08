#!/usr/bin/env python3
"""Connect to FLOW 8 over BLE, capture MixerState, query icon config, print channel info."""
import asyncio, os, sys
from bleak import BleakScanner, BleakClient

from extract_flow8_channels import decode_entries, print_scribble_report

CHAR = "0034594a-a8e7-4b1a-a6b1-cd5243059a57"

# Command type codes (from libcom_musicgroup_xairbt.so descriptor table).
T_HANDSHAKE_HOST   = 0x35  # device -> client broadcast (host id, pairing flag, fw)
T_HANDSHAKE_REPLY  = 0x36  # device -> client (empty payload)
T_GET_MIXER_STATE  = 0x37  # client -> device (empty)  : triggers the state dump
T_MIXER_STATE      = 0x38  # device -> client          : the state dump
T_HANDSHAKE_CLIENT = 0x39  # client -> device (16-byte client id)
T_PARAM_RESPONSE   = 0x25  # device -> client (setting / icon config reply)
T_PARAM_QUERY      = 0x26  # client -> device (query setting by id)
T_QUERY_ICON_CONFIG = 0x80

CLIENT_ID_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                              ".flow8_client_id")

CHANNEL_COUNT = 6
DUMP_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                         "flow8_dump.bin")
ICON_CONFIG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                "icon_config.bin")
ICON_CONFIG_TIMEOUT_S = 2.0
WRITE_GAP_S = 0.2
SERVICE_UUID_SHORT = "14839ad4-0000-1000-8000-00805f9b34fb"
MAX_ATTEMPTS = 5


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


def is_flow_device(name: str | None) -> bool:
    if not name:
        return False
    upper = name.upper()
    return "FLOW 8" in upper or "FLOW8" in upper or upper == "FLOW"


def is_in_pairing_mode(adv) -> bool:
    """Flow Mix: pairing active when service data first byte is non-zero."""
    data = adv.service_data.get(SERVICE_UUID_SHORT) if adv else None
    return bool(data) and data[0] != 0


async def find_flow_in_pairing(timeout: float = 12.0):
    """Return (device, pairing_seen) — prefer advertisements with pairing flag set."""
    deadline = asyncio.get_event_loop().time() + timeout
    best = None
    while asyncio.get_event_loop().time() < deadline:
        remaining = deadline - asyncio.get_event_loop().time()
        devices = await BleakScanner.discover(
            timeout=min(4.0, max(1.0, remaining)),
            return_adv=True,
        )
        for addr, (dev, adv) in devices.items():
            name = dev.name or (adv.local_name if adv else None)
            if not is_flow_device(name):
                continue
            pairing = is_in_pairing_mode(adv)
            print(f"  saw {name} {addr} pairing={pairing} rssi={adv.rssi if adv else '?'}",
                  flush=True)
            if pairing:
                return dev, True
            best = dev
        if best and asyncio.get_event_loop().time() + 2 > deadline:
            return best, False
    return best, False


async def capture_once(dev, client_id: bytes):
    got_handshake_host = asyncio.Event()
    got_handshake_reply = asyncio.Event()
    dump_done = asyncio.Event()
    icon_done = asyncio.Event()
    disconnected = asyncio.Event()
    session_done = asyncio.Event()
    session_error = [None]

    frags = {}
    frag_total = [0]
    icon_config = [None]
    notif_count = 0
    phase = ["handshake"]
    write_queue = asyncio.Queue()
    pending_writes = []

    async def ble_write(c, char_obj, data: bytes, label: str):
        print(f"  WRITE {label}: {data.hex()}", flush=True)
        await asyncio.sleep(WRITE_GAP_S)
        await c.write_gatt_char(char_obj, data, response=True)

    async def write_worker(c, char_obj):
        while not session_done.is_set():
            try:
                data, label = await asyncio.wait_for(write_queue.get(), timeout=0.5)
            except asyncio.TimeoutError:
                continue
            try:
                await ble_write(c, char_obj, data, label)
            except Exception as exc:
                session_error[0] = exc
                session_done.set()
                return

    def schedule_write(data: bytes, label: str):
        write_queue.put_nowait((data, label))

    def on_notify(char, data: bytearray):
        nonlocal notif_count
        notif_count += 1
        typ = data[0] if data else 0xFF
        print(f"  NOTIF 0x{typ:02X} len={len(data)} {data.hex()}", flush=True)

        if typ == T_HANDSHAKE_HOST and phase[0] == "handshake":
            if got_handshake_host.is_set():
                return
            got_handshake_host.set()
            # Official app never writes from the notify callback directly; wait ~200 ms.
            async def after_host():
                await asyncio.sleep(WRITE_GAP_S)
                schedule_write(
                    frame(T_HANDSHAKE_CLIENT, client_id),
                    "HandshakeClient(0x39)",
                )
            pending_writes.append(asyncio.create_task(after_host()))

        elif typ == T_HANDSHAKE_REPLY and not got_handshake_reply.is_set():
            got_handshake_reply.set()
            async def after_reply():
                await asyncio.sleep(WRITE_GAP_S)
                phase[0] = "dump"
                schedule_write(frame(T_GET_MIXER_STATE), "GetMixerState(0x37)")
            pending_writes.append(asyncio.create_task(after_reply()))

        elif typ == T_MIXER_STATE and phase[0] in ("dump", "icon") and len(data) >= 4:
            count = data[1]
            seq = data[3]
            frag_total[0] = count
            frags[seq] = bytes(data[4:])
            if count and len(frags) >= count and not dump_done.is_set():
                dump_done.set()
                async def after_dump():
                    await asyncio.sleep(WRITE_GAP_S)
                    phase[0] = "icon"
                    schedule_write(
                        frame(T_PARAM_QUERY, bytes([T_QUERY_ICON_CONFIG])),
                        "ParamQuery icon(0x26/0x80)",
                    )
                pending_writes.append(asyncio.create_task(after_dump()))

        elif typ == T_PARAM_RESPONSE and phase[0] == "icon" and len(data) >= 4:
            if (data[2] & 0xFF) == T_QUERY_ICON_CONFIG:
                length = data[3] & 0xFF
                if 4 + length <= len(data):
                    icon_config[0] = bytes(data[4:4 + length])
                    icon_done.set()
                    session_done.set()

    def on_disconnect(_client):
        print("  [disconnected]", flush=True)
        disconnected.set()
        got_handshake_host.set()
        got_handshake_reply.set()
        dump_done.set()
        icon_done.set()
        session_done.set()

    async with BleakClient(dev, timeout=25, disconnected_callback=on_disconnect) as c:
        char_obj = c.services.get_characteristic(CHAR)
        if not char_obj:
            raise RuntimeError("FLOW 8 GATT characteristic not found")
        print(f"Characteristic props: {char_obj.properties}", flush=True)

        writer = asyncio.create_task(write_worker(c, char_obj))
        await c.start_notify(CHAR, on_notify)

        print("Waiting for HandshakeHost (0x35) from device (up to 15s)...", flush=True)
        try:
            await asyncio.wait_for(got_handshake_host.wait(), timeout=15)
        except asyncio.TimeoutError:
            raise RuntimeError("no 0x35 HandshakeHost — enable MENU → PAIRING → PAIR APP")
        if disconnected.is_set():
            raise RuntimeError("disconnected before handshake")

        print("Waiting for HandshakeReply (0x36) from device (up to 10s)...", flush=True)
        try:
            await asyncio.wait_for(got_handshake_reply.wait(), timeout=10)
        except asyncio.TimeoutError:
            raise RuntimeError("no 0x36 HandshakeReply — pairing may have expired")

        print("Waiting for state dump (0x38 chunks, up to 30s)...", flush=True)
        try:
            await asyncio.wait_for(dump_done.wait(), timeout=30)
        except asyncio.TimeoutError:
            raise RuntimeError("incomplete MixerState dump")

        print(f"Waiting for icon config (0x25/0x80, up to {ICON_CONFIG_TIMEOUT_S}s)...",
              flush=True)
        try:
            await asyncio.wait_for(icon_done.wait(), timeout=ICON_CONFIG_TIMEOUT_S)
        except asyncio.TimeoutError:
            print("Icon config query timed out — continuing with slot bytes only.", flush=True)
            session_done.set()

        await session_done.wait()
        for task in pending_writes:
            task.cancel()
        writer.cancel()
        await c.stop_notify(CHAR)

    if session_error[0]:
        raise session_error[0]

    buf = b"".join(frags[i] for i in sorted(frags))
    print(f"\nNotifications received: {notif_count}, "
          f"fragments: {len(frags)}/{frag_total[0]}, dump bytes: {len(buf)}")
    if not buf:
        raise RuntimeError("no MixerState data received")
    return buf, icon_config[0]


async def main():
    client_id = load_or_make_client_id()
    print(f"Client ID: {client_id.hex()}", flush=True)
    print("Enable pairing on FLOW 8: MENU → PAIRING → PAIR APP (~15 s window).",
          flush=True)

    last_err = None
    for attempt in range(1, MAX_ATTEMPTS + 1):
        print(f"\n=== Attempt {attempt}/{MAX_ATTEMPTS} ===", flush=True)
        print("Scanning for FLOW 8 in pairing mode...", flush=True)
        dev, pairing = await find_flow_in_pairing(timeout=15)
        if not dev:
            print("FLOW 8 not found. Close the phone app and retry.", flush=True)
            last_err = "device not found"
            continue
        if not pairing:
            print("WARNING: pairing flag not seen in advertisement — trying anyway.",
                  flush=True)
        print(f"Connecting to {dev.name} {dev.address}...", flush=True)
        try:
            buf, icfg = await capture_once(dev, client_id)
            break
        except Exception as exc:
            last_err = str(exc)
            print(f"Attempt failed: {exc}", flush=True)
            await asyncio.sleep(1.0)
    else:
        print(f"\nAll attempts failed. Last error: {last_err}")
        print("Re-enable pairing on the mixer and run again.")
        sys.exit(1)

    with open(DUMP_FILE, "wb") as fh:
        fh.write(buf)
    print(f"Raw dump saved to {DUMP_FILE}")

    if icfg:
        with open(ICON_CONFIG_FILE, "wb") as fh:
            fh.write(icfg)
        print(f"Icon config saved to {ICON_CONFIG_FILE} ({len(icfg)} bytes)")

    entries = decode_entries(buf, icfg)
    if not any(e["name"] for e in entries):
        print(f"No names parsed. Dump preview: {buf[:64].hex()}")
        sys.exit(3)

    print_scribble_report(entries)

if __name__ == "__main__":
    asyncio.run(main())
