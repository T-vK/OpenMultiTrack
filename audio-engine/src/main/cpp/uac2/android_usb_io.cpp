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

void logDriver(int usb_fd, uint8_t interface_number) {
    usbdevfs_getdriver gd{};
    gd.interface = interface_number;
    if (ioctl(usb_fd, USBDEVFS_GETDRIVER, &gd) == 0) {
        OMT_LOGI("usb_io iface=%u kernel driver=%s", interface_number, gd.driver);
    }
}

bool usbdevfsSupportsDriverDetach(int usb_fd) {
    usbdevfs_getdriver gd{};
    gd.interface = 0;
    if (ioctl(usb_fd, USBDEVFS_GETDRIVER, &gd) == 0) {
        return true;
    }
    return errno != ENOTTY && errno != EINVAL;
}

UsbIoStatus tryDisconnectClaim(int usb_fd, uint8_t interface_number, const char* driver) {
    usbdevfs_disconnect_claim dc{};
    dc.interface = interface_number;
    if (driver != nullptr && driver[0] != '\0') {
        dc.flags = USBDEVFS_DISCONNECT_CLAIM_IF_DRIVER;
        std::strncpy(dc.driver, driver, sizeof(dc.driver) - 1);
    } else {
        dc.flags = USBDEVFS_DISCONNECT_CLAIM_EXCEPT_DRIVER;
        dc.driver[0] = '\0';
    }
    if (ioctl(usb_fd, USBDEVFS_DISCONNECT_CLAIM, &dc) < 0) {
        return fail("DISCONNECT_CLAIM", errno);
    }
    UsbIoStatus ok;
    ok.ok = true;
    return ok;
}

UsbIoStatus detachKernelDriver(int usb_fd, uint8_t interface_number) {
    logDriver(usb_fd, interface_number);

    unsigned int iface = interface_number;
    (void)ioctl(usb_fd, USBDEVFS_RELEASEINTERFACE, &iface);

    static const char* kDrivers[] = {"snd-usb-audio", "usb", nullptr};
    for (const char* driver : kDrivers) {
        const UsbIoStatus dc = tryDisconnectClaim(usb_fd, interface_number, driver);
        if (dc.ok) {
            OMT_LOGI("usb_io detached iface=%u via driver=%s",
                     interface_number,
                     driver != nullptr ? driver : "(except)");
            return dc;
        }
    }

    return fail("DISCONNECT_CLAIM", EBUSY);
}

UsbIoStatus clearEndpointHalt(int usb_fd, uint8_t endpoint_address) {
    unsigned int ep = endpoint_address;
    if (ioctl(usb_fd, USBDEVFS_CLEAR_HALT, &ep) < 0) {
        return fail("CLEAR_HALT", errno);
    }
    UsbIoStatus ok;
    ok.ok = true;
    return ok;
}

bool isInEndpoint(uint8_t endpoint_address) {
    return (endpoint_address & 0x80) != 0;
}

}  // namespace

void detachForeignDrivers(int usb_fd, uint8_t keep_iface) {
    if (!usbdevfsSupportsDriverDetach(usb_fd)) {
        return;
    }
    for (unsigned int ifno = 0; ifno < 16; ++ifno) {
        if (static_cast<uint8_t>(ifno) == keep_iface) {
            continue;
        }
        usbdevfs_ioctl cmd{};
        cmd.ifno = static_cast<int>(ifno);
        cmd.ioctl_code = USBDEVFS_DISCONNECT;
        cmd.data = nullptr;
        if (ioctl(usb_fd, USBDEVFS_IOCTL, &cmd) == 0) {
            OMT_LOGI("usb_io disconnected foreign driver on iface=%u", ifno);
        }
    }
}

UsbIoStatus setAltOnClaimedInterface(int usb_fd, const Uac2AltSetting& alt) {
    if (usb_fd < 0) {
        return fail("invalid fd", EINVAL);
    }
    if (!alt.format.valid || alt.endpoint_address == 0) {
        return fail("invalid alt", EINVAL);
    }

    // Emulator UsbDeviceConnection fds reject usbdevfs SETINTERFACE (EHOSTUNREACH).
    // When Java already claimed + setInterface, skip ioctl and submit URBs directly.
    if (!usbdevfsSupportsDriverDetach(usb_fd)) {
        OMT_LOGI("usb_io Java-claimed iface=%u alt=%u (skip ioctl set alt)",
                 alt.interface_number,
                 alt.alternate_setting);
        UsbIoStatus ok;
        ok.ok = true;
        return ok;
    }

    detachForeignDrivers(usb_fd, alt.interface_number);

    usbdevfs_setinterface si{};
    si.interface = alt.interface_number;
    si.altsetting = alt.alternate_setting;
    if (ioctl(usb_fd, USBDEVFS_SETINTERFACE, &si) < 0) {
        return fail("SETINTERFACE", errno);
    }

    const UsbIoStatus halt = isInEndpoint(alt.endpoint_address)
        ? clearEndpointHalt(usb_fd, alt.endpoint_address)
        : UsbIoStatus{.ok = true};
    if (!halt.ok) {
        OMT_LOGW("usb_io CLEAR_HALT ep=0x%02x failed (%s)",
                 alt.endpoint_address,
                 halt.error.c_str());
    }

    OMT_LOGI("usb_io set alt on Java-claimed iface=%u alt=%u ep=0x%02x",
             alt.interface_number,
             alt.alternate_setting,
             alt.endpoint_address);
    UsbIoStatus ok;
    ok.ok = true;
    return ok;
}

UsbIoStatus claimAndSetAlt(int usb_fd, const Uac2AltSetting& alt) {
    if (usb_fd < 0) {
        return fail("invalid fd", EINVAL);
    }
    if (!alt.format.valid || alt.endpoint_address == 0) {
        return fail("invalid alt", EINVAL);
    }

    bool native_claimed = false;
    unsigned int iface = alt.interface_number;
    if (ioctl(usb_fd, USBDEVFS_CLAIMINTERFACE, &iface) < 0) {
        const int claim_err = errno;
        if (claim_err == EBUSY) {
            OMT_LOGI("usb_io iface=%u already claimed, continuing", alt.interface_number);
        } else {
            OMT_LOGW("usb_io CLAIMINTERFACE failed (%s), trying DISCONNECT_CLAIM",
                     std::strerror(claim_err));
            const UsbIoStatus dc = tryDisconnectClaim(usb_fd, alt.interface_number, nullptr);
            if (!dc.ok) {
                OMT_LOGW("usb_io DISCONNECT_CLAIM failed, attempting SET_INTERFACE anyway");
            } else if (ioctl(usb_fd, USBDEVFS_CLAIMINTERFACE, &iface) == 0) {
                native_claimed = true;
            }
        }
    } else {
        native_claimed = true;
    }

    usbdevfs_setinterface si{};
    si.interface = alt.interface_number;
    si.altsetting = alt.alternate_setting;
    if (ioctl(usb_fd, USBDEVFS_SETINTERFACE, &si) < 0) {
        const int alt_err = errno;
        if (alt_err == EHOSTUNREACH || alt_err == ENOENT || alt_err == ENOTTY) {
            OMT_LOGW("usb_io SETINTERFACE failed (%s), continuing to isoch",
                     std::strerror(alt_err));
        } else {
            if (native_claimed) {
                releaseInterface(usb_fd, alt.interface_number);
            }
            return fail("SETINTERFACE", alt_err);
        }
    }

    const UsbIoStatus halt = isInEndpoint(alt.endpoint_address)
        ? clearEndpointHalt(usb_fd, alt.endpoint_address)
        : UsbIoStatus{.ok = true};
    if (!halt.ok) {
        OMT_LOGW("usb_io CLEAR_HALT ep=0x%02x failed (%s)",
                 alt.endpoint_address,
                 halt.error.c_str());
    }

    OMT_LOGI("usb_io claimed iface=%u alt=%u ep=0x%02x",
             alt.interface_number,
             alt.alternate_setting,
             alt.endpoint_address);
    UsbIoStatus ok;
    ok.ok = true;
    return ok;
}

UsbIoStatus claimAndSetAltWithDriverDetach(int usb_fd, const Uac2AltSetting& alt) {
    if (usb_fd < 0 || !alt.format.valid || alt.endpoint_address == 0) {
        return fail("invalid fd or alt", EINVAL);
    }
    if (!usbdevfsSupportsDriverDetach(usb_fd)) {
        return fail("driver detach unsupported", ENOTSUP);
    }

    detachForeignDrivers(usb_fd, alt.interface_number);
    const UsbIoStatus detach = detachKernelDriver(usb_fd, alt.interface_number);
    if (!detach.ok) {
        OMT_LOGW("usb_io kernel detach failed (%s), trying CLAIMINTERFACE anyway",
                 detach.error.c_str());
    }

    unsigned int iface = alt.interface_number;
    if (ioctl(usb_fd, USBDEVFS_CLAIMINTERFACE, &iface) < 0) {
        return fail("CLAIMINTERFACE", errno);
    }

    usbdevfs_setinterface si{};
    si.interface = alt.interface_number;
    si.altsetting = alt.alternate_setting;
    if (ioctl(usb_fd, USBDEVFS_SETINTERFACE, &si) < 0) {
        releaseInterface(usb_fd, alt.interface_number);
        return fail("SETINTERFACE", errno);
    }

    if (isInEndpoint(alt.endpoint_address)) {
        (void)clearEndpointHalt(usb_fd, alt.endpoint_address);
    }
    OMT_LOGI("usb_io claimed iface=%u alt=%u ep=0x%02x (driver detach)",
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
