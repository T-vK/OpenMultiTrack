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
    } else {
        OMT_LOGI("usb_io iface=%u kernel driver=(none) errno=%s",
                 interface_number,
                 std::strerror(errno));
    }
}

void logCapabilities(int usb_fd) {
    unsigned int caps = 0;
    if (ioctl(usb_fd, USBDEVFS_GET_CAPABILITIES, &caps) == 0) {
        OMT_LOGI("usb_io capabilities=0x%x", caps);
    }
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
        OMT_LOGW("usb_io DISCONNECT_CLAIM driver=%s failed (%s)",
                 driver != nullptr ? driver : "(except)",
                 dc.error.c_str());
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

}  // namespace

void detachForeignDrivers(int usb_fd, uint8_t keep_iface) {
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

    detachForeignDrivers(usb_fd, alt.interface_number);

    usbdevfs_setinterface si{};
    si.interface = alt.interface_number;
    si.altsetting = alt.alternate_setting;
    if (ioctl(usb_fd, USBDEVFS_SETINTERFACE, &si) < 0) {
        return fail("SETINTERFACE", errno);
    }

    const UsbIoStatus halt = clearEndpointHalt(usb_fd, alt.endpoint_address);
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

    logCapabilities(usb_fd);
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

    const UsbIoStatus halt = clearEndpointHalt(usb_fd, alt.endpoint_address);
    if (!halt.ok) {
        OMT_LOGW("usb_io CLEAR_HALT ep=0x%02x failed (%s)",
                 alt.endpoint_address,
                 halt.error.c_str());
    }

    logDriver(usb_fd, alt.interface_number);
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
