package org.openmultitrack.app.routing

import org.json.JSONObject
import org.openmultitrack.app.data.RoutingAutomationMethod
import org.openmultitrack.mixer.behringer.XAirChannelInputState

enum class RoutingOverrideKind {
    RECORD,
    SOUNDCHECK,
}

/**
 * Persisted routing transaction — survives process death.
 * Baseline is captured immediately before any mixer write.
 */
data class PendingRoutingRestore(
    val transactionId: String,
    val mixerId: String,
    val oscHost: String,
    val kind: RoutingOverrideKind,
    val affectedChannels: Set<Int>,
    val baselineByChannel: Map<Int, XAirChannelInputState>,
    val overrideByChannel: Map<Int, XAirChannelInputState>,
    val method: RoutingAutomationMethod,
    val snapshotSlot: Int = 0,
    val capturedAtEpochMs: Long,
    val recordingWasActive: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("transactionId", transactionId)
        put("mixerId", mixerId)
        put("oscHost", oscHost)
        put("kind", kind.name)
        put("affectedChannels", JSONArrayInt(affectedChannels))
        put("baseline", encodeStates(baselineByChannel))
        put("override", encodeStates(overrideByChannel))
        put("method", method.name)
        put("snapshotSlot", snapshotSlot)
        put("capturedAtEpochMs", capturedAtEpochMs)
        put("recordingWasActive", recordingWasActive)
    }

    companion object {
        fun fromJson(raw: String): PendingRoutingRestore? = runCatching {
            val o = JSONObject(raw)
            PendingRoutingRestore(
                transactionId = o.getString("transactionId"),
                mixerId = o.getString("mixerId"),
                oscHost = o.getString("oscHost"),
                kind = RoutingOverrideKind.valueOf(o.getString("kind")),
                affectedChannels = decodeIntSet(o.getJSONArray("affectedChannels")),
                baselineByChannel = decodeStates(o.getJSONObject("baseline")),
                overrideByChannel = decodeStates(o.getJSONObject("override")),
                method = RoutingAutomationMethod.valueOf(o.getString("method")),
                snapshotSlot = o.optInt("snapshotSlot", 0),
                capturedAtEpochMs = o.getLong("capturedAtEpochMs"),
                recordingWasActive = o.optBoolean("recordingWasActive", false),
            )
        }.getOrNull()

        private fun encodeStates(map: Map<Int, XAirChannelInputState>): JSONObject =
            JSONObject().apply {
                map.forEach { (ch, st) ->
                    put(
                        ch.toString(),
                        JSONObject()
                            .put("insrc", st.insrc)
                            .put("rtnsrc", st.rtnsrc)
                            .put("rtnSw", st.rtnSw),
                    )
                }
            }

        private fun decodeStates(o: JSONObject): Map<Int, XAirChannelInputState> =
            buildMap {
                o.keys().forEach { key ->
                    val ch = key.toIntOrNull() ?: return@forEach
                    val st = o.getJSONObject(key)
                    put(
                        ch,
                        XAirChannelInputState(
                            insrc = st.getInt("insrc"),
                            rtnsrc = st.getInt("rtnsrc"),
                            rtnSw = st.getInt("rtnSw"),
                        ),
                    )
                }
            }

        private fun JSONArrayInt(values: Set<Int>) =
            org.json.JSONArray().apply { values.sorted().forEach { put(it) } }

        private fun decodeIntSet(arr: org.json.JSONArray): Set<Int> =
            buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.getInt(i))
                }
            }
    }
}
