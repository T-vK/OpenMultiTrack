#include "libusb_session.h"

#include "../audio_log.h"
#include "android_usb_io.h"
#include "libusb.h"

#include <mutex>

namespace openmultitrack::uac2 {

namespace {

std::once_flag g_ctx_once;
libusb_context* g_ctx = nullptr;

void initLibusbContext() {
    (void)libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, nullptr);
    const int r = libusb_init(&g_ctx);
    if (r != 0) {
        OMT_LOGE("libusb_init failed: %s", libusb_error_name(r));
        g_ctx = nullptr;
        return;
    }
    OMT_LOGI("libusb context ready (NO_DEVICE_DISCOVERY)");
}

}  // namespace

libusb_context* libusbAndroidContext() {
    std::call_once(g_ctx_once, initLibusbContext);
    return g_ctx;
}

const char* libusbErrorName(int err) {
    return libusb_error_name(err);
}

bool libusbPrepareStreaming(int usb_fd,
                            const Uac2AltSetting& alt,
                            bool java_interface_claimed,
                            libusb_device_handle** out_handle,
                            bool* out_owns_interface) {
    if (out_handle == nullptr || out_owns_interface == nullptr || usb_fd < 0) {
        return false;
    }
    *out_handle = nullptr;
    *out_owns_interface = false;

    libusb_context* ctx = libusbAndroidContext();
    if (ctx == nullptr) {
        return false;
    }

    if (java_interface_claimed) {
        const UsbIoStatus alt_status = setAltOnClaimedInterface(usb_fd, alt);
        if (!alt_status.ok) {
            OMT_LOGE("libusb ioctl set alt failed: %s", alt_status.error.c_str());
            return false;
        }
    }

    libusb_device_handle* handle = nullptr;
    const int wrap_r =
        libusb_wrap_sys_device(ctx, static_cast<intptr_t>(usb_fd), &handle);
    if (wrap_r != 0) {
        OMT_LOGE("libusb_wrap_sys_device failed: %s", libusb_error_name(wrap_r));
        return false;
    }

    if (!java_interface_claimed) {
        detachForeignDrivers(usb_fd, alt.interface_number);
        if (libusb_kernel_driver_active(handle, alt.interface_number) == 1) {
            const int detach_r = libusb_detach_kernel_driver(handle, alt.interface_number);
            if (detach_r != 0) {
                OMT_LOGW("libusb_detach_kernel_driver iface=%u: %s",
                         alt.interface_number,
                         libusb_error_name(detach_r));
            }
        }
        int r = libusb_claim_interface(handle, alt.interface_number);
        if (r != 0) {
            OMT_LOGE("libusb_claim_interface failed: %s", libusb_error_name(r));
            libusb_close(handle);
            return false;
        }
        r = libusb_set_interface_alt_setting(
            handle, alt.interface_number, alt.alternate_setting);
        if (r != 0) {
            OMT_LOGE("libusb_set_interface_alt_setting failed: %s", libusb_error_name(r));
            libusb_release_interface(handle, alt.interface_number);
            libusb_close(handle);
            return false;
        }
        *out_owns_interface = true;
        OMT_LOGI("libusb claimed iface=%u alt=%u ep=0x%02x",
                 alt.interface_number,
                 alt.alternate_setting,
                 alt.endpoint_address);
    } else {
        OMT_LOGI("libusb wrap on Java-claimed iface=%u alt=%u ep=0x%02x (ioctl set alt)",
                 alt.interface_number,
                 alt.alternate_setting,
                 alt.endpoint_address);
    }

    *out_handle = handle;
    return true;
}

void libusbReleaseStreaming(libusb_device_handle* handle,
                            uint8_t interface_number,
                            bool owns_interface) {
    if (handle == nullptr) {
        return;
    }
    if (owns_interface) {
        (void)libusb_release_interface(handle, interface_number);
    }
    libusb_close(handle);
}

}  // namespace openmultitrack::uac2
