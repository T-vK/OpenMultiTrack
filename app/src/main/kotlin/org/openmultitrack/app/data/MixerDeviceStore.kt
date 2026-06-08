package org.openmultitrack.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.mixer.MixerProfile
import java.util.UUID

/** Persists user-added mixer profiles across app restarts. */
class MixerDeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun listMixers(): List<MixerProfile> {
        val raw = prefs.getString(KEY_MIXERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun addMixer(descriptor: UsbAudioDeviceDescriptor, displayName: String? = null): MixerProfile {
        val existing = listMixers().firstOrNull {
            it.vendorId == descriptor.vendorId &&
                it.productId == descriptor.productId &&
                it.serialNumber != null &&
                it.serialNumber == descriptor.serialNumber
        }
        if (existing != null) {
            val updated = existing.copy(
                usbDeviceName = descriptor.deviceName,
                productName = descriptor.productName,
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
        persist(list)
    }

    fun removeMixer(id: String) {
        persist(listMixers().filter { it.id != id })
    }

    fun isBehringerAudioInterface(descriptor: UsbAudioDeviceDescriptor): Boolean {
        if (descriptor.isLikelyBehringerMixer) return true
        return descriptor.vendorId == 0x1397
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
