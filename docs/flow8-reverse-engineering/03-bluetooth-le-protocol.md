# 03 — Bluetooth LE Protocol

The official *Behringer FLOW Mixer* app talks to the FLOW 8 over **Bluetooth Low
Energy (BLE)** using a single proprietary GATT service with one read/write/notify
characteristic. This is the **only** transport that provides full state read/write,
including channel-strip names. USB MIDI alone cannot retrieve names (see doc 01).

All information in this document was verified against firmware **v11749** by
disassembling `libcom_musicgroup_xairbt.so` from `Flowmix_v1.9.apk`.

## GATT layout

| Item            | UUID                                     |
| --------------- | ---------------------------------------- |
| Service         | `14839ad4-8d7e-415c-9a42-167340cf2339`   |
| Characteristic  | `0034594a-a8e7-4b1a-a6b1-cd5243059a57`   |

The single characteristic is used **bidirectionally**:

- **Write with response** — commands sent from client to mixer.
- **Notify (subscribe)** — responses and streamed state from the mixer.

Scan for the peripheral by BLE advertised name containing `FLOW`, or by the service
UUID above.

## Packet framing

All packets use the same wire format, verified from `xairbt_out_queue_peek_raw` in
the native library:

```
[type : u8][0x01 : u8][payload : N bytes][checksum : u8]
```

- `type` — command type byte (see table below).
- `0x01` — fixed "chunk count" field (always 1 for single-fragment commands).
- `payload` — zero or more bytes depending on the command.
- `checksum` — `(type + 0x01 + sum(payload)) & 0xFF`.

### Building a packet (pseudocode)

```
function frame(type, payload):
    body = [type, 0x01] + payload
    checksum = sum(body) & 0xFF
    return body + [checksum]
```

### Command type codes

| Code   | Direction      | Name               | Payload              |
| ------ | -------------- | ------------------ | -------------------- |
| `0x35` | device → client| HandshakeHost      | host info + fw ver   |
| `0x36` | device → client| HandshakeReply     | empty                |
| `0x37` | client → device| GetMixerState      | empty                |
| `0x38` | device → client| MixerState (chunk) | state data fragment  |
| `0x39` | client → device| HandshakeClient    | 16-byte client ID    |
| `0x4B` | client → device| DumpTrigger        | empty (SysEx on USB) |

### Worked examples

| Purpose             | Packet (hex)                                                | Checksum derivation            |
| ------------------- | ----------------------------------------------------------- | ------------------------------ |
| GetMixerState       | `37 01 38`                                                  | 0x37+0x01 = 0x38               |
| DumpTrigger         | `4B 01 4C`                                                  | 0x4B+0x01 = 0x4C               |
| HandshakeClient     | `39 01 <16 bytes> <cs>`                                     | 0x39+0x01+sum(id) & 0xFF       |

## Pairing mode

The device only broadcasts the `0x35` HandshakeHost notification when **pairing mode
is active** on the mixer. Pairing mode is enabled from the mixer's physical controls
and times out after **~15 seconds** per press (re-enable as needed). Without a `0x35`
the handshake cannot proceed.

## Handshake state machine (event-driven)

The authentication is **not** a blind key exchange — it is a reactive protocol driven
by notifications from the device. You **must** subscribe to notifications before
attempting to authenticate; writing commands before receiving `0x35` causes an ATT
error (`UNLIKELY_ERROR`, code `0x0E`).

```
  Client                          FLOW 8 (pairing mode active)
  ──────                          ────────────────────────────
  connect + subscribe
                    ←─── 0x35 HandshakeHost ───  (device broadcasts on connect)
  send frame(0x39, client_id)
                    ───────────────────────────→
                    ←─── 0x36 HandshakeReply ───  (auth accepted)
  send frame(0x37)  [GetMixerState]
                    ───────────────────────────→
                    ←─── 0x38 chunk 1 of N  ───  (state dump begins)
                    ←─── 0x38 chunk 2 of N  ───
                    ←─── 0x38 chunk N of N  ───  (state dump complete)
```

### Step-by-step

1. **Scan** — find the device by name `"FLOW"` or service UUID.
2. **Connect** and discover services/characteristics.
3. **Subscribe** to notifications on the characteristic.
4. **Wait** for a `0x35` HandshakeHost notification (up to ~15 s; device must be in
   pairing mode).
5. **Send** `frame(0x39, client_id)` — the 16-byte client ID (see below).
6. **Wait** for a `0x36` HandshakeReply notification (up to ~10 s).
7. **Send** `frame(0x37)` — GetMixerState (no payload). `= bytes [0x37, 0x01, 0x38]`.
8. **Collect** `0x38` MixerState notification chunks until all fragments arrive.

## Client ID

The 16-byte client ID in step 5 is **self-generated** — there is no secret key
embedded in the app or device. The only constraint enforced by the mixer firmware
(`xairbt_is_uid_valid`) is that the ID must **not be all zeros**. Any 16-byte
non-zero value works.

Persist the ID across runs so the device keeps recognising the same client without
needing to re-enter pairing mode:

```
# First run: generate and save
client_id = random_bytes(16)   # ensure at least one non-zero byte
save_to_file(".flow8_client_id", client_id)

# Subsequent runs: reload
client_id = load_from_file(".flow8_client_id")
```

## Receiving the MixerState dump (0x38 fragments)

The state dump is split across multiple `0x38` notifications. Each fragment has the
form:

```
[0x38][total_count : u8][0x01 : u8][seq_index : u8][payload ...]
```

- `total_count` — total number of fragments.
- `seq_index` — 0-based index of this fragment.
- `payload` — the fragment data.

Reassembly pseudocode:

```
fragments = {}
on notification(data):
    if data[0] == 0x38 and len(data) >= 4:
        total = data[1]
        seq   = data[3]
        fragments[seq] = data[4:]
        if len(fragments) == total:
            full_dump = concat(fragments[0..total-1])
            process(full_dump)
```

The reassembled buffer contains the raw mixer state including length-prefixed ASCII
channel names. See [`04-channel-name-extraction.md`](./04-channel-name-extraction.md).

## Optional: SysEx dump via USB MIDI (DumpTrigger 0x4B)

As an alternative to `GetMixerState` (0x37), you can trigger the mixer to emit a
full SysEx dump over its **USB MIDI** port:

```
Send: frame(0x4B)  →  [0x4B, 0x01, 0x4C]
```

The mixer then outputs `F0 00 20 32 21 … F7` on USB MIDI. Capture with `amidi -r`.
This path requires **both** BLE (to trigger) and USB (to receive). The 0x37 → 0x38
BLE-only path is simpler and sufficient for reading channel names.

## Implementation notes for other languages

Any BLE library that supports:
- GATT characteristic write with response
- GATT characteristic notifications (subscribe)

is sufficient. Tested with Python `bleak`; equivalent libraries:

| Language   | Library                          |
| ---------- | -------------------------------- |
| Python     | `bleak`                          |
| JavaScript | `@abandonware/noble`, Web BT API |
| Swift      | `CoreBluetooth` (macOS/iOS)      |
| Kotlin     | `Android BLE API`                |
| Rust       | `btleplug`                       |
| Go         | `tinygo-org/bluetooth`, `go-ble` |

The protocol is identical across all platforms: same UUIDs, same packet framing,
same handshake order.

See [`tools/ble_dump_names.py`](./tools/ble_dump_names.py) for a complete,
working reference implementation in Python.
