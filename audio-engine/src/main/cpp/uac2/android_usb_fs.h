#pragma once

#include <linux/ioctl.h>
#include <sys/ioctl.h>

#include <cstdint>

// Minimal usbdevfs definitions for Android NDK (linux/usbdevice_fs.h is not shipped).

struct usbdevfs_iso_packet_desc {
    unsigned int length;
    unsigned int actual_length;
    unsigned int status;
};

struct usbdevfs_urb {
    unsigned char type;
    unsigned char endpoint;
    int status;
    unsigned int flags;
    void* buffer;
    int buffer_length;
    int actual_length;
    int start_frame;
    int number_of_packets;
    int error_count;
    unsigned int signr;
    void* usercontext;
    struct usbdevfs_iso_packet_desc iso_frame_desc[];
};

struct usbdevfs_setinterface {
    unsigned int interface;
    unsigned int altsetting;
};

struct usbdevfs_getdriver {
    unsigned int interface;
    char driver[256];
};

struct usbdevfs_ioctl {
    int ifno;
    int ioctl_code;
    void* data;
};

#define USBDEVFS_URB_TYPE_ISO 1
#define USBDEVFS_URB_ISO_ASAP 2

#define USBDEVFS_IOCTL _IO('U', 21)
#define USBDEVFS_SETINTERFACE _IOR('U', 4, struct usbdevfs_setinterface)
#define USBDEVFS_CLAIMINTERFACE _IOR('U', 15, unsigned int)
#define USBDEVFS_RELEASEINTERFACE _IOR('U', 16, unsigned int)
#define USBDEVFS_DISCONNECT _IO('U', 17)
#define USBDEVFS_CONNECT _IO('U', 18)
#define USBDEVFS_SUBMITURB _IOR('U', 10, struct usbdevfs_urb)
#define USBDEVFS_DISCARDURB _IOW('U', 11, void*)
#define USBDEVFS_REAPURB _IOW('U', 12, void*)
#define USBDEVFS_REAPURBNDELAY _IOW('U', 13, void*)
#define USBDEVFS_DISCSIGNAL _IOR('U', 14, unsigned int)
#define USBDEVFS_CLEAR_HALT _IOR('U', 20, unsigned int)
#define USBDEVFS_GET_CAPABILITIES _IOR('U', 22, unsigned int)
#define USBDEVFS_DISCONNECT_CLAIM _IOR('U', 27, struct usbdevfs_disconnect_claim)
#define USBDEVFS_GETDRIVER _IOR('U', 8, struct usbdevfs_getdriver)

struct usbdevfs_disconnect_claim {
    unsigned int interface;
    unsigned int flags;
    char driver[256];
};

#define USBDEVFS_DISCONNECT_CLAIM_IF_DRIVER 0x01
#define USBDEVFS_DISCONNECT_CLAIM_EXCEPT_DRIVER 0x02
