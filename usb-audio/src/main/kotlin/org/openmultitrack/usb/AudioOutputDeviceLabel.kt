package org.openmultitrack.usb

import android.media.AudioDeviceInfo
import android.os.Build

data class LabeledAudioDevice(
    val device: AudioDeviceInfo,
    val label: String,
    val shortLabel: String,
)

/** Builds unique, human-readable labels for monitor output selection. */
object AudioOutputDeviceLabel {
    fun label(device: AudioDeviceInfo): String {
        val type = typeName(device.type)
        val product = device.productName?.toString()?.takeIf { it.isNotBlank() }
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            device.address?.takeIf { it.isNotBlank() && it != "0" }
        } else {
            null
        }
        val parts = buildList {
            add(type)
            if (product != null) add(product)
            if (address != null) add(address)
            add("#${device.id}")
        }
        return parts.joinToString(" · ")
    }

    fun shortLabel(device: AudioDeviceInfo): String {
        val product = device.productName?.toString()?.takeIf { it.isNotBlank() }
        return product ?: "${typeName(device.type)} #${device.id}"
    }

    fun labelAll(devices: List<AudioDeviceInfo>): List<LabeledAudioDevice> {
        val counts = devices.groupingBy { shortLabel(it) }.eachCount()
        return devices.map { d ->
            val base = label(d)
            val disambiguated = if ((counts[shortLabel(d)] ?: 0) > 1) base else shortLabel(d)
            LabeledAudioDevice(d, base, disambiguated)
        }
    }

    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line out"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital out"
        AudioDeviceInfo.TYPE_DOCK -> "Dock"
        AudioDeviceInfo.TYPE_AUX_LINE -> "Aux"
        else -> "Output"
    }
}
