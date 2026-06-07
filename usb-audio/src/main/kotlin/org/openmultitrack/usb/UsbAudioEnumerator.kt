package org.openmultitrack.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor

/**
 * Lists USB devices and correlates them with Android [AudioDeviceInfo] when possible.
 */
class UsbAudioEnumerator(
    private val context: Context,
) {
    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun listUsbDevices(): List<UsbAudioDeviceDescriptor> {
        val devices = usbManager.deviceList.values.map { usbDevice ->
            toDescriptor(usbDevice)
        }.sortedBy { it.productName ?: it.deviceName }
        OmtLog.d("Usb", "listUsbDevices count=${devices.size}")
        devices.forEach { d ->
            OmtLog.d(
                "Usb",
                "  ${d.productName} vid=${d.vendorId} pid=${d.productId} " +
                    "behringer=${d.isLikelyBehringerMixer} audioId=${d.androidAudioDeviceId}",
            )
        }
        return devices
    }

    fun listAudioOutputDevices(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()

    fun listAudioInputDevices(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()

  /**
     * Best-effort mapping: USB product name substring match against audio device product name.
     * Returns AAudio device id suitable for Oboe setDeviceId().
     */
    fun findAndroidAudioDeviceId(usb: UsbAudioDeviceDescriptor, input: Boolean): Int? {
        val direction = if (input) "input" else "output"
        val pool = if (input) listAudioInputDevices() else listAudioOutputDevices()
        OmtLog.d(
            "Usb",
            "findAndroidAudioDeviceId ${usb.productName} direction=$direction pool=${pool.size}",
        )
        pool.forEach { info ->
            val product = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.productName?.toString()
            } else {
                null
            }
            OmtLog.d("Usb", "  candidate id=${info.id} type=${info.type} product=$product")
        }
        val key = usb.productName?.lowercase()?.takeIf { it.isNotBlank() } ?: run {
            OmtLog.w("Usb", "no product name for ${usb.deviceName}, cannot match by name")
            return null
        }
        val match = pool.firstOrNull { info ->
            info.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                info.type == AudioDeviceInfo.TYPE_USB_HEADSET
            val product = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.productName?.toString()?.lowercase()
            } else {
                null
            }
            product != null && (product.contains(key) || key.contains(product))
        } ?: pool.firstOrNull { info ->
            info.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                info.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (match != null) {
            OmtLog.i("Usb", "mapped ${usb.productName} → audio device id=${match.id} ($direction)")
        } else {
            OmtLog.w("Usb", "no audio device id for ${usb.productName} ($direction)")
        }
        return match?.id
    }

    fun hasUsbPermission(deviceName: String): Boolean {
        val device = usbManager.deviceList[deviceName] ?: return false
        return usbManager.hasPermission(device)
    }

    fun getUsbDevice(deviceName: String): UsbDevice? = usbManager.deviceList[deviceName]

    /**
     * Raw USB configuration descriptor via [android.hardware.usb.UsbDeviceConnection].
     * Requires USB permission. Opens the device briefly; does not claim audio interfaces.
     */
    fun getRawConfigDescriptor(deviceName: String): ByteArray? {
        val device = getUsbDevice(deviceName) ?: return null
        if (!usbManager.hasPermission(device)) {
            OmtLog.d("Usb", "getRawConfigDescriptor: no permission for $deviceName")
            return null
        }
        val connection = usbManager.openDevice(device) ?: run {
            OmtLog.w("Usb", "getRawConfigDescriptor: openDevice failed for $deviceName")
            return null
        }
        return try {
            runCatching { connection.rawDescriptors }
                .onFailure { OmtLog.w("Usb", "getRawConfigDescriptor failed for $deviceName", it) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
        } finally {
            connection.close()
        }
    }

    private fun toDescriptor(device: UsbDevice): UsbAudioDeviceDescriptor {
        val product = device.productName
        val manufacturer = device.manufacturerName
        val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            usbManager.hasPermission(device)
        ) {
            runCatching { device.serialNumber }
                .onFailure { OmtLog.w("Usb", "serial read failed for ${device.deviceName}", it) }
                .getOrNull()
        } else {
            null
        }
        val likely = BehringerUsbIdentifiers.isLikelyBehringerMixer(device.vendorId, product)
        val audioId = if (likely) {
            findAndroidAudioDeviceId(
                UsbAudioDeviceDescriptor(
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    manufacturerName = manufacturer,
                    productName = product,
                    serialNumber = serial,
                    isLikelyBehringerMixer = likely,
                    guessedModel = BehringerUsbIdentifiers.guessModel(product),
                    androidAudioDeviceId = null,
                ),
                input = true,
            )
        } else {
            null
        }
        return UsbAudioDeviceDescriptor(
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            manufacturerName = manufacturer,
            productName = product,
            serialNumber = serial,
            isLikelyBehringerMixer = likely,
            guessedModel = BehringerUsbIdentifiers.guessModel(product),
            androidAudioDeviceId = audioId,
        )
    }
}
