#pragma once

#include "uac2_types.h"

struct libusb_context;
struct libusb_device_handle;

namespace openmultitrack::uac2 {

/** Shared libusb context with NO_DEVICE_DISCOVERY for Android fd wrap. */
libusb_context* libusbAndroidContext();

/** Wrap UsbDeviceConnection fd and prepare AS interface for isoch I/O. */
bool libusbPrepareStreaming(int usb_fd,
                            const Uac2AltSetting& alt,
                            bool java_interface_claimed,
                            libusb_device_handle** out_handle,
                            bool* out_owns_interface);

void libusbReleaseStreaming(libusb_device_handle* handle,
                            uint8_t interface_number,
                            bool owns_interface);

const char* libusbErrorName(int err);

}  // namespace openmultitrack::uac2
