package org.openmultitrack.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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
        return usbManager.deviceList.values.map { usbDevice ->
            toDescriptor(usbDevice)
        }.sortedBy { it.productName ?: it.deviceName }
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
        val pool = if (input) listAudioInputDevices() else listAudioOutputDevices()
        val key = usb.productName?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
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
        return match?.id
    }

    fun hasUsbPermission(deviceName: String): Boolean {
        val device = usbManager.deviceList[deviceName] ?: return false
        return usbManager.hasPermission(device)
    }

    fun getUsbDevice(deviceName: String): UsbDevice? = usbManager.deviceList[deviceName]

    private fun toDescriptor(device: UsbDevice): UsbAudioDeviceDescriptor {
        val product = device.productName
        val manufacturer = device.manufacturerName
        val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            device.serialNumber
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
