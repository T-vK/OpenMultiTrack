#pragma once

#include "uac2_types.h"

#include <optional>
#include <string>

namespace openmultitrack::uac2 {

/** Result of a usbdevfs setup step. */
struct UsbIoStatus {
    bool ok = false;
    std::string error;
};

/** Claim AS interface and select alternate setting on an open usbfs fd. */
UsbIoStatus claimAndSetAlt(int usb_fd, const Uac2AltSetting& alt);

/** claimAndSetAlt with kernel driver detach (physical OTG hosts). */
UsbIoStatus claimAndSetAltWithDriverDetach(int usb_fd, const Uac2AltSetting& alt);

/** Select alt setting when Java already called UsbDeviceConnection.claimInterface. */
UsbIoStatus setAltOnClaimedInterface(int usb_fd, const Uac2AltSetting& alt);

/** Release a previously claimed interface. */
UsbIoStatus releaseInterface(int usb_fd, uint8_t interface_number);

/** Disconnect kernel drivers on interfaces other than the streaming AS iface. */
void detachForeignDrivers(int usb_fd, uint8_t keep_iface);

}  // namespace openmultitrack::uac2
