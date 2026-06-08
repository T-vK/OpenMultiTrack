package org.openmultitrack.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.openmultitrack.mixer.behringer.UsbChannelScribble
import java.io.File
import java.security.MessageDigest

/**
 * Persists imported scribble strip labels per mixer for fast reload without BLE/OSC.
 */
class ScribbleStripCache(context: Context) {
    private val cacheDir = File(context.filesDir, "scribble_cache").apply { mkdirs() }

    fun hasCache(mixerId: String): Boolean = cacheFile(mixerId).isFile

    fun load(mixerId: String): List<UsbChannelScribble>? {
        val file = cacheFile(mixerId)
        if (!file.isFile) return null
        return runCatching {
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("labels")
            (0 until arr.length()).map { i -> labelFromJson(arr.getJSONObject(i)) }
        }.getOrNull()
    }

    fun loadFingerprint(mixerId: String): String? {
        val file = cacheFile(mixerId)
        if (!file.isFile) return null
        return runCatching {
            JSONObject(file.readText()).optString("fingerprint").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun save(mixerId: String, labels: List<UsbChannelScribble>) {
        val fingerprint = fingerprint(labels)
        val root = JSONObject().apply {
            put("version", CACHE_VERSION)
            put("fingerprint", fingerprint)
            put("cachedAtMs", System.currentTimeMillis())
            put("labels", JSONArray().apply { labels.forEach { put(labelToJson(it)) } })
        }
        cacheFile(mixerId).writeText(root.toString())
    }

    fun delete(mixerId: String) {
        cacheFile(mixerId).delete()
    }

    private fun cacheFile(mixerId: String): File = File(cacheDir, "$mixerId.json")

    private fun labelToJson(label: UsbChannelScribble): JSONObject = JSONObject().apply {
        put("usbChannel", label.usbChannel)
        put("sourceLabel", label.sourceLabel)
        put("name", label.name)
        put("colorIndex", label.colorIndex)
        put("iconId", label.iconId)
    }

    private fun labelFromJson(o: JSONObject): UsbChannelScribble = UsbChannelScribble(
        usbChannel = o.getInt("usbChannel"),
        sourceLabel = o.getString("sourceLabel"),
        name = o.optString("name").takeIf { it.isNotBlank() },
        colorIndex = if (o.has("colorIndex") && !o.isNull("colorIndex")) o.getInt("colorIndex") else null,
        iconId = if (o.has("iconId") && !o.isNull("iconId")) o.getInt("iconId") else null,
    )

    companion object {
        private const val CACHE_VERSION = 1

        fun fingerprint(labels: List<UsbChannelScribble>): String {
            val canonical = labels.sortedBy { it.usbChannel }.joinToString("|") { label ->
                buildString {
                    append(label.usbChannel)
                    append(':')
                    append(label.name.orEmpty())
                    append(':')
                    append(label.colorIndex ?: -1)
                    append(':')
                    append(label.iconId ?: -1)
                }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
