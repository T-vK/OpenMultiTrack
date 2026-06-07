package org.openmultitrack.usb

import org.openmultitrack.audio.OmtLog
import java.io.File

/**
 * Emulator passthrough cannot always open [android.hardware.usb.UsbDeviceConnection] for UAC2
 * audio devices (permission + system_server quirks). The setup scripts publish the live config
 * descriptor from host sysfs into a world-readable cache file.
 */
internal object UsbEmulatorDescriptorCache {
    private const val CACHE_DIR = "/data/local/tmp"

    fun pathFor(vendorId: Int, productId: Int): String =
        "$CACHE_DIR/omt_usb_${vendorId}_${productId}.bin"

    fun read(vendorId: Int, productId: Int): ByteArray? {
        val file = File(pathFor(vendorId, productId))
        if (!file.canRead()) {
            return null
        }
        return runCatching {
            file.readBytes().takeIf { it.isNotEmpty() }
        }.onFailure {
            OmtLog.w("Usb", "emulator descriptor cache read failed: ${file.path}", it)
        }.getOrNull()?.also {
            OmtLog.i("Usb", "read ${it.size}B config descriptor from emulator cache ${file.path}")
        }
    }
}
