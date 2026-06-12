package org.openmultitrack.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.mixer.MixerProfile

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

    /** True if any connected node matching [profile] already has USB permission. */
    fun hasUsbPermissionForProfile(profile: MixerProfile): Boolean =
        usbManager.deviceList.values.any { device ->
            profileMatchesDevice(profile, device) && usbManager.hasPermission(device)
        }

    /** Lists only USB nodes that match saved mixer profiles (avoids touching unrelated USB gadgets). */
    fun listDevicesForProfiles(profiles: List<MixerProfile>): List<UsbAudioDeviceDescriptor> {
        if (profiles.isEmpty()) return emptyList()
        val connected = usbManager.deviceList.values
        return profiles.mapNotNull { profile ->
            connected.firstOrNull { device -> profileMatchesDevice(profile, device) }
                ?.let { toDescriptor(it) }
        }.distinctBy { it.deviceName }.also { matches ->
            OmtLog.d("Usb", "listDevicesForProfiles saved=${profiles.size} matched=${matches.size}")
        }
    }

    fun findMatchingDevice(
        vendorId: Int,
        productId: Int,
        serialNumber: String? = null,
        preferredDeviceName: String? = null,
    ): UsbAudioDeviceDescriptor? {
        val devices = usbManager.deviceList.values.map { toDescriptor(it) }
        preferredDeviceName?.let { name ->
            devices.firstOrNull { it.deviceName == name }?.let { return it }
        }
        return devices.firstOrNull { device ->
            device.vendorId == vendorId &&
                device.productId == productId &&
                (serialNumber == null || device.serialNumber == serialNumber)
        }
    }

    fun getUsbDevice(deviceName: String): UsbDevice? = usbManager.deviceList[deviceName]

    /**
     * Raw USB configuration descriptor via [android.hardware.usb.UsbDeviceConnection].
     * Requires USB permission. Opens the device briefly; does not claim audio interfaces.
     */
    fun getRawConfigDescriptor(deviceName: String): ByteArray? {
        val device = getUsbDevice(deviceName) ?: return null
        val connection = usbManager.openDevice(device)
        if (connection != null) {
            return try {
                runCatching { connection.rawDescriptors }
                    .onFailure { OmtLog.w("Usb", "getRawConfigDescriptor failed for $deviceName", it) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            } finally {
                connection.close()
            }
        }
        UsbEmulatorDescriptorCache.read(device.vendorId, device.productId)?.let { return it }
        if (!usbManager.hasPermission(device)) {
            OmtLog.d("Usb", "getRawConfigDescriptor: no permission for $deviceName")
        } else {
            OmtLog.w("Usb", "getRawConfigDescriptor: openDevice failed for $deviceName")
        }
        return null
    }

    private fun profileMatchesDevice(profile: MixerProfile, device: UsbDevice): Boolean {
        if (profile.vendorId != device.vendorId || profile.productId != device.productId) {
            return false
        }
        profile.usbDeviceName?.let { if (it == device.deviceName) return true }
        val serial = if (usbManager.hasPermission(device)) {
            runCatching { device.serialNumber }.getOrNull()
        } else {
            null
        }
        profile.serialNumber?.let { saved ->
            if (serial != null) return saved == serial
        }
        return profile.serialNumber == null
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
