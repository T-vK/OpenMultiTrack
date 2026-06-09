package org.openmultitrack.remote

import org.json.JSONObject

/** Public JSON encode/decode for the remote sync protocol. */
object RemoteJsonCodec {
    fun encodeSnapshot(snapshot: RemoteMirrorSnapshot): String = RemoteJson.encodeSnapshot(snapshot)

    fun decodeSnapshot(json: String): RemoteMirrorSnapshot = RemoteJson.decodeSnapshot(json)

    fun encodeDelta(delta: RemoteDeltaFrame): String = RemoteJson.encodeDelta(delta)

    fun decodeDelta(json: String): RemoteDeltaFrame = RemoteJson.decodeDelta(json)

    fun encodeCommand(type: String, payload: JSONObject = JSONObject()): String =
        RemoteJson.encodeCommand(type, payload)

    fun decodeCommand(json: String): Pair<String, JSONObject> = RemoteJson.decodeCommand(json)

    fun encodeWaveformChunk(channel: Int, startSec: Float, peaks: FloatArray): String =
        RemoteJson.encodeWaveformChunk(channel, startSec, peaks)

    fun decodeWaveformChunk(json: String): Triple<Int, Float, FloatArray> =
        RemoteJson.decodeWaveformChunk(json)

    fun encodeWaveformRequest(
        mixerId: String,
        sessionDir: String,
        channel: Int,
        startSec: Float,
        windowSec: Float,
    ): String = RemoteJson.encodeWaveformRequest(mixerId, sessionDir, channel, startSec, windowSec)
}
