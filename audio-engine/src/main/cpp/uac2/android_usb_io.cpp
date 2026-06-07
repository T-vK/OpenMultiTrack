#include "android_usb_io.h"

#include "../audio_log.h"
#include "android_usb_fs.h"

#include <cerrno>
#include <cstring>
#include <unistd.h>

namespace openmultitrack::uac2 {

namespace {

UsbIoStatus fail(const char* step, int err) {
    UsbIoStatus status;
    status.error = std::string(step) + ": " + std::strerror(err);
    OMT_LOGE("usb_io %s", status.error.c_str());
    return status;
}

UsbIoStatus tryDisconnectClaim(int usb_fd, uint8_t interface_number) {
    usbdevfs_disconnect_claim dc{};
    dc.interface = interface_number;
    dc.flags = USBDEVFS_DISCONNECT_CLAIM_EXCEPT_DRIVER;
    if (ioctl(usb_fd, USBDEVFS_DISCONNECT_CLAIM, &dc) < 0) {
        return fail("DISCONNECT_CLAIM", errno);
    }
    UsbIoStatus ok;
    ok.ok = true;
    return ok;
}

}  // namespace

UsbIoStatus claimAndSetAlt(int usb_fd, const Uac2AltSetting& alt) {
    if (usb_fd < 0) {
        return fail("invalid fd", EINVAL);
    }
    if (!alt.format.valid || alt.endpoint_address == 0) {
        return fail("invalid alt", EINVAL);
    }

    unsigned int iface = alt.interface_number;
    if (ioctl(usb_fd, USBDEVFS_CLAIMINTERFACE, &iface) < 0) {
        const int claim_err = errno;
        if (claim_err == EBUSY) {
            OMT_LOGI("usb_io iface=%u already claimed, continuing", alt.interface_number);
        } else {
            OMT_LOGW("usb_io CLAIMINTERFACE failed (%s), trying DISCONNECT_CLAIM",
                     std::strerror(claim_err));
            const UsbIoStatus dc = tryDisconnectClaim(usb_fd, alt.interface_number);
            if (!dc.ok) {
                // Java UsbDeviceConnection.claimInterface may have claimed already.
                OMT_LOGW("usb_io DISCONNECT_CLAIM failed, attempting SET_INTERFACE anyway");
            } else if (ioctl(usb_fd, USBDEVFS_CLAIMINTERFACE, &iface) < 0 && errno != EBUSY) {
                return fail("CLAIMINTERFACE", errno);
            }
        }
    }

    usbdevfs_setinterface si{};
    si.interface = alt.interface_number;
    si.altsetting = alt.alternate_setting;
    if (ioctl(usb_fd, USBDEVFS_SETINTERFACE, &si) < 0) {
        releaseInterface(usb_fd, alt.interface_number);
        return fail("SETINTERFACE", errno);
    }

    OMT_LOGI("usb_io claimed iface=%u alt=%u ep=0x%02x",
             alt.interface_number,
             alt.alternate_setting,
             alt.endpoint_address);
    UsbIoStatus ok;
    ok.ok = true;
    return ok;
}

UsbIoStatus releaseInterface(int usb_fd, uint8_t interface_number) {
    unsigned int iface = interface_number;
    if (ioctl(usb_fd, USBDEVFS_RELEASEINTERFACE, &iface) < 0) {
        return fail("RELEASEINTERFACE", errno);
    }
    UsbIoStatus ok;
    ok.ok = true;
    return ok;
}

}  // namespace openmultitrack::uac2
