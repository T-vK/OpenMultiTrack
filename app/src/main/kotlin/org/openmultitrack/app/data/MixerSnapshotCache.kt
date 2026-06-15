package org.openmultitrack.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.openmultitrack.mixer.behringer.MixerSnapshotOption
import java.io.File

/** Persists XR18 snapshot names per mixer for instant settings UI while refreshing. */
class MixerSnapshotCache(context: Context) {
    private val cacheDir = File(context.filesDir, "snapshot_cache").apply { mkdirs() }

    fun load(mixerId: String, oscHost: String): List<MixerSnapshotOption>? {
        val file = cacheFile(mixerId)
        if (!file.isFile) return null
        return runCatching {
            val root = JSONObject(file.readText())
            if (root.optString("oscHost") != oscHost) return null
            val arr = root.getJSONArray("snapshots")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MixerSnapshotOption(
                    slot = o.getInt("slot"),
                    name = o.optString("name", ""),
                )
            }
        }.getOrNull()
    }

    fun save(mixerId: String, oscHost: String, snapshots: List<MixerSnapshotOption>) {
        val root = JSONObject().apply {
            put("oscHost", oscHost)
            put("cachedAtMs", System.currentTimeMillis())
            put(
                "snapshots",
                JSONArray().apply {
                    snapshots.forEach { option ->
                        put(
                            JSONObject().apply {
                                put("slot", option.slot)
                                put("name", option.name)
                            },
                        )
                    }
                },
            )
        }
        cacheFile(mixerId).writeText(root.toString())
    }

    fun delete(mixerId: String) {
        cacheFile(mixerId).delete()
    }

    private fun cacheFile(mixerId: String): File = File(cacheDir, "$mixerId.json")
}
