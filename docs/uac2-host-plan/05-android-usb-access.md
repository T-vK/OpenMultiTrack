# Android USB Access

## The kernel conflict problem

When a USB mixer attaches on Android:

1. Kernel **`snd-usb-audio`** may bind Audio interfaces
2. Android **AudioFlinger** exposes `AudioDeviceInfo` (what Oboe uses)
3. A userspace UAC2 host needs **exclusive claim** of the AS interface for isochronous I/O

**You cannot stream the same AS interface through both Oboe and omt-uac2 simultaneously.**

## Access paths

### Path A — Android USB Host API (primary)

```kotlin
val connection = usbManager.openDevice(device)
val fd = connection.fileDescriptor
// Pass fd + interface index to native via JNI
```

Native side:

- `ioctl` / `usbdevice_fs` bulk/interrupt/isochronous via **libusb `libusb_wrap_sys_device`** OR direct `android_usb_device` ioctls
- `USB_REQ_SET_INTERFACE` to select alt setting
- Queue isochronous transfers on endpoint address

**Pros:** No root, works in F-Droid apps, same approach as libuac/eXtream.  
**Cons:** Must detach/avoid kernel driver on that interface; not all devices allow this.

### Path B — Oboe/AAudio only (fallback)

Use when:

- UAC2 claim fails
- Descriptor shows ≤2ch and Oboe works
- User opts into "compatibility mode"

### Path C — Root / tinycap (out of scope)

`/dev/snd/pcmC*D*c` via root shell — not a product path.

## Implementation strategy for interface claim

1. Parse descriptor → find AS interface number + required alt setting
2. Open `UsbDeviceConnection` in Kotlin for the whole device
3. Native: `claimInterface(audioInterface, true)` via JNI callback to Java **or** libusb claim after wrap
4. `SET_INTERFACE` to target alt setting
5. Start isochronous transfers
6. On stop: release interface, close connection

## JNI bridge design

```kotlin
// usb-audio module
class AndroidUsbConnection(
    val fd: Int,
    val interfaceNumber: Int,
    val configDescriptor: ByteArray,
)
```

Passed to:

```cpp
Uac2Capture::open(const AndroidUsbConnection& conn, const Uac2StreamConfig& cfg);
```

## Isochronous I/O on Android

Options (evaluate in Phase 2 spike):

| Approach | Notes |
|----------|-------|
| **libusb** with `libusb_wrap_sys_device` | Proven on Android; add NDK build of libusb or use AOSP external/libusb |
| **Raw `usbfs` ioctls** | What libusb uses under the hood; fewer deps |
| **Java `UsbRequest`** | Isochronous support limited; likely insufficient for multichannel |

**Recommendation:** Start with **libusb 1.0.26+** as static lib in `third_party/libusb`, wrapped from `UsbDeviceConnection` FD — matches libuac and eXtream ecosystem.

## Permissions

Existing app flow unchanged:

- `RECORD_AUDIO` for Android policy
- `UsbManager.requestPermission` before open
- Explicit package-scoped `PendingIntent` on API 34+ (already fixed)

## Descriptor without claim

`UsbDeviceConnection.getRawDescriptors()` (API 12+, we have minSdk 26) provides config descriptor after `openDevice()` **without** claiming an interface — use for Phase 1 probe UI:

> "Device offers 10ch @ 48kHz AS alt 3; Oboe reports 2ch"
