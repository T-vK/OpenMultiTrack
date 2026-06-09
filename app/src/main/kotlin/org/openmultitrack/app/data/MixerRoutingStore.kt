package org.openmultitrack.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.openmultitrack.domain.mixer.MixerRoutingConfig

class MixerRoutingStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(mixerId: String): MixerRoutingConfig =
        loadAll()[mixerId] ?: MixerRoutingConfig()

    fun save(mixerId: String, config: MixerRoutingConfig) {
        val all = loadAll().toMutableMap()
        all[mixerId] = config
        persist(all)
    }

    fun loadAll(): Map<String, MixerRoutingConfig> {
        val raw = prefs.getString(KEY_ROUTING, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { mixerId ->
                    put(mixerId, decode(root.getJSONObject(mixerId)))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun remove(mixerId: String) {
        val all = loadAll().toMutableMap()
        if (all.remove(mixerId) != null) persist(all)
    }

    private fun persist(all: Map<String, MixerRoutingConfig>) {
        val root = JSONObject()
        all.forEach { (id, cfg) -> root.put(id, encode(cfg)) }
        prefs.edit().putString(KEY_ROUTING, root.toString()).apply()
    }

    private fun encode(cfg: MixerRoutingConfig): JSONObject =
        JSONObject().apply {
            put("inputMap", intMapToJson(cfg.inputMap))
            put("outputMap", intMapToJson(cfg.outputMap))
            put("hiddenRecord", intSetToJson(cfg.hiddenRecord))
            put("hiddenSoundcheck", intSetToJson(cfg.hiddenSoundcheck))
        }

    private fun decode(obj: JSONObject): MixerRoutingConfig =
        MixerRoutingConfig(
            inputMap = jsonToIntMap(obj.optJSONObject("inputMap")),
            outputMap = jsonToIntMap(obj.optJSONObject("outputMap")),
            hiddenRecord = jsonToIntSet(obj.optJSONArray("hiddenRecord")),
            hiddenSoundcheck = jsonToIntSet(obj.optJSONArray("hiddenSoundcheck")),
        )

    private fun intMapToJson(map: Map<Int, Int>): JSONObject =
        JSONObject().apply { map.forEach { (k, v) -> put(k.toString(), v) } }

    private fun jsonToIntMap(obj: JSONObject?): Map<Int, Int> {
        if (obj == null) return emptyMap()
        return buildMap {
            obj.keys().forEach { key -> put(key.toInt(), obj.getInt(key)) }
        }
    }

    private fun intSetToJson(set: Set<Int>): JSONArray =
        JSONArray().apply { set.sorted().forEach { put(it) } }

    private fun jsonToIntSet(arr: JSONArray?): Set<Int> {
        if (arr == null) return emptySet()
        return buildSet {
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
    }

    companion object {
        private const val PREFS = "omt_mixer_routing"
        private const val KEY_ROUTING = "routing_by_mixer"
    }
}
