package org.openmultitrack.remote

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.openmultitrack.domain.remote.RemoteProtocol

class RemoteJsonCodecTest {
    @Test
    fun snapshotRoundTrip() {
        val snapshot = RemoteMirrorSnapshot(
            protocolVersion = RemoteProtocol.VERSION,
            hostName = "Tablet",
            activeMixerId = "m1",
            settings = RemoteSettingsSnapshot(showVuMeters = false),
            mixers = listOf(RemoteMixerProfileSnapshot("m1", "Flow 8")),
            sessions = mapOf(
                "m1" to RemoteMixerSnapshot(
                    mixerId = "m1",
                    displayName = "Flow 8",
                    transportState = "RECORDING",
                    captureChannelCount = 8,
                    channelStrips = listOf(
                        RemoteChannelStripSnapshot(index = 0, label = "Kick", armed = true),
                    ),
                ),
            ),
        )
        val decoded = RemoteJsonCodec.decodeSnapshot(RemoteJsonCodec.encodeSnapshot(snapshot))
        assertThat(decoded.hostName).isEqualTo("Tablet")
        assertThat(decoded.activeMixerId).isEqualTo("m1")
        assertThat(decoded.sessions["m1"]?.transportState).isEqualTo("RECORDING")
        assertThat(decoded.sessions["m1"]?.channelStrips?.first()?.label).isEqualTo("Kick")
    }

    @Test
    fun deltaRoundTrip() {
        val delta = RemoteDeltaFrame(
            activeMixerId = "m1",
            sessions = mapOf(
                "m1" to RemoteMixerDelta(
                    mixerId = "m1",
                    recordElapsedSec = 12.5f,
                    captureMeterLevels = mapOf(0 to 0.75f),
                ),
            ),
            liveWaveforms = mapOf(
                "m1" to mapOf(
                    0 to RemoteLiveWaveformTail(
                        generation = 3,
                        peaksU8 = byteArrayOf(10, 20, 30),
                    ),
                ),
            ),
        )
        val decoded = RemoteJsonCodec.decodeDelta(RemoteJsonCodec.encodeDelta(delta))
        assertThat(decoded.sessions["m1"]?.recordElapsedSec).isEqualTo(12.5f)
        assertThat(decoded.liveWaveforms["m1"]?.get(0)?.generation).isEqualTo(3)
        assertThat(decoded.liveWaveforms["m1"]?.get(0)?.peaksU8).isEqualTo(byteArrayOf(10, 20, 30))
    }

    @Test
    fun commandRoundTrip() {
        val payload = JSONObject().put("mixerId", "m1").put("index", 2)
        val (command, decoded) = RemoteJsonCodec.decodeCommand(
            RemoteJsonCodec.encodeCommand("toggle_arm", payload),
        )
        assertThat(command).isEqualTo("toggle_arm")
        assertThat(decoded.getString("mixerId")).isEqualTo("m1")
        assertThat(decoded.getInt("index")).isEqualTo(2)
    }
}
