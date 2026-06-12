package org.openmultitrack.app.util

import org.json.JSONArray
import org.json.JSONObject

enum class LogCustomFilterMode {
    ONLY_SHOW,
    HIDE,
}

data class LogCustomFilter(
    val id: String,
    val pattern: String,
    val mode: LogCustomFilterMode,
    val enabled: Boolean = true,
) {
    val compiledRegex: Regex? by lazy {
        runCatching { Regex(pattern) }.getOrNull()
    }

    fun matchesTag(tag: String): Boolean = compiledRegex?.containsMatchIn(tag) == true
}

object LogTagCatalog {
    /** Tags used in [org.openmultitrack.audio.OmtLog] calls across the app. */
    val knownTags: List<String> = listOf(
        "Activity",
        "App",
        "CaptureRegistry",
        "CaptureSession",
        "Flow8Ble",
        "MixerSession",
        "OscDiscovery",
        "OscLan",
        "Player",
        "Probe",
        "Recorder",
        "Remote",
        "Router",
        "RoutingHooks",
        "Scribble",
        "Service",
        "ServiceClient",
        "SpillSync",
        "Transport",
        "Usb",
        "UsbPerm",
        "UsbStream",
        "ViewModel",
        "VuMeter",
        "Xr18Routing",
    )

    fun discoverTags(rawLines: List<String>): Set<String> {
        val tags = linkedSetOf<String>()
        for (raw in rawLines) {
            AppLogBuffer.parseLogLine(raw)?.tag?.let(tags::add)
        }
        return tags
    }

    fun allTags(rawLines: List<String>): List<String> =
        (knownTags + discoverTags(rawLines)).distinct().sorted()
}

object LogCustomFilterCodec {
    fun encode(filters: List<LogCustomFilter>): String {
        val arr = JSONArray()
        for (filter in filters) {
            arr.put(
                JSONObject()
                    .put("id", filter.id)
                    .put("pattern", filter.pattern)
                    .put("mode", filter.mode.name)
                    .put("enabled", filter.enabled),
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<LogCustomFilter> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val mode = runCatching {
                        LogCustomFilterMode.valueOf(obj.getString("mode"))
                    }.getOrDefault(LogCustomFilterMode.HIDE)
                    add(
                        LogCustomFilter(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            pattern = obj.getString("pattern"),
                            mode = mode,
                            enabled = obj.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

object LogTagFilterCodec {
    fun encodeDisabledTags(tags: Set<String>): String = JSONArray(tags.sorted()).toString()

    fun decode(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet(arr.length()) {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
        }.getOrDefault(emptySet())
    }
}
