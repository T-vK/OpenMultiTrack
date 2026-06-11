package org.openmultitrack.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.mixer.VirtualMixer
import java.util.UUID

/** Persists user-added mixer profiles across app restarts. */
class MixerDeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun listMixers(): List<MixerProfile> {
        val raw = prefs.getString(KEY_MIXERS, null) ?: return emptyList()
        val parsed = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
        val deduped = deduplicate(parsed)
        if (deduped.size != parsed.size) {
            persist(deduped)
        }
        return deduped
    }

    fun isAlreadyAdded(descriptor: UsbAudioDeviceDescriptor): Boolean =
        findMatchingMixer(descriptor) != null

    fun findMatchingMixer(descriptor: UsbAudioDeviceDescriptor): MixerProfile? =
        listMixers().firstOrNull { matchesSameDevice(it, descriptor) }

    fun addMixer(descriptor: UsbAudioDeviceDescriptor, displayName: String? = null): MixerProfile {
        val existing = listMixers().firstOrNull { matchesSameDevice(it, descriptor) }
        if (existing != null) {
            val updated = existing.copy(
                usbDeviceName = descriptor.deviceName,
                productName = descriptor.productName,
                serialNumber = existing.serialNumber ?: descriptor.serialNumber,
            )
            saveMixer(updated)
            return updated
        }
        val profile = MixerProfile(
            id = UUID.randomUUID().toString(),
            usbDeviceName = descriptor.deviceName,
            vendorId = descriptor.vendorId,
            productId = descriptor.productId,
            serialNumber = descriptor.serialNumber,
            productName = descriptor.productName,
            displayName = displayName ?: descriptor.productName ?: descriptor.guessedModel ?: "Mixer",
        )
        saveMixer(profile)
        return profile
    }

    fun saveMixer(profile: MixerProfile) {
        val list = listMixers().filter { it.id != profile.id } + profile
        persist(deduplicate(list))
    }

    fun removeMixer(id: String) {
        persist(listMixers().filter { it.id != id })
    }

    fun isBehringerAudioInterface(descriptor: UsbAudioDeviceDescriptor): Boolean {
        if (descriptor.isLikelyBehringerMixer) return true
        return descriptor.vendorId == 0x1397
    }

    fun findVirtualSineMixer(): MixerProfile? =
        listMixers().firstOrNull { VirtualMixer.isSineGenerator(it) }

    /** Built-in sine-wave test source (no USB hardware). */
    fun addVirtualSineMixer(): MixerProfile {
        findVirtualSineMixer()?.let { return it }
        val profile = MixerProfile(
            id = UUID.randomUUID().toString(),
            usbDeviceName = "virtual:sine",
            vendorId = VirtualMixer.VENDOR_ID,
            productId = VirtualMixer.PRODUCT_ID_SINE,
            serialNumber = "virtual-sine",
            productName = "OMT Test Signal",
            displayName = VirtualMixer.DISPLAY_NAME,
        )
        saveMixer(profile)
        return profile
    }

    private fun matchesSameDevice(profile: MixerProfile, descriptor: UsbAudioDeviceDescriptor): Boolean {
        if (profile.vendorId != descriptor.vendorId || profile.productId != descriptor.productId) {
            return false
        }
        val profileSerial = profile.serialNumber
        val descriptorSerial = descriptor.serialNumber
        if (profileSerial != null && descriptorSerial != null) {
            return profileSerial == descriptorSerial
        }
        if (profile.usbDeviceName != null && profile.usbDeviceName == descriptor.deviceName) {
            return true
        }
        return profileSerial == null && descriptorSerial == null
    }

    private fun deduplicate(list: List<MixerProfile>): List<MixerProfile> {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<MixerProfile>(list.size)
        for (profile in list) {
            val key = deviceKey(profile)
            if (seen.add(key)) {
                result.add(profile)
            }
        }
        return result
    }

    private fun deviceKey(profile: MixerProfile): String = when {
        profile.serialNumber != null -> "s:${profile.vendorId}:${profile.productId}:${profile.serialNumber}"
        profile.usbDeviceName != null -> "n:${profile.vendorId}:${profile.productId}:${profile.usbDeviceName}"
        else -> "p:${profile.vendorId}:${profile.productId}"
    }

    private fun persist(list: List<MixerProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_MIXERS, arr.toString()).apply()
    }

    private fun toJson(p: MixerProfile): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("usbDeviceName", p.usbDeviceName)
        put("vendorId", p.vendorId)
        put("productId", p.productId)
        put("serialNumber", p.serialNumber)
        put("productName", p.productName)
        put("displayName", p.displayName)
        put("oscHost", p.oscHost)
        put("scribbleImported", p.scribbleImported)
    }

    private fun fromJson(o: JSONObject): MixerProfile = MixerProfile(
        id = o.getString("id"),
        usbDeviceName = o.optString("usbDeviceName").takeIf { it.isNotBlank() },
        vendorId = o.getInt("vendorId"),
        productId = o.getInt("productId"),
        serialNumber = o.optString("serialNumber").takeIf { it.isNotBlank() },
        productName = o.optString("productName").takeIf { it.isNotBlank() },
        displayName = o.getString("displayName"),
        oscHost = o.optString("oscHost").takeIf { it.isNotBlank() },
        scribbleImported = o.optBoolean("scribbleImported", false),
    )

    companion object {
        private const val PREFS = "omt_mixer_devices"
        private const val KEY_MIXERS = "mixers"
    }
}
