package org.openmultitrack.remote

data class RemoteDiscoveredHost(
    val name: String,
    val host: String,
    val port: Int,
    val protocolVersion: Int,
    val hostId: String? = null,
    val isPaired: Boolean = false,
)

data class RemoteSettingsSnapshot(
    val hideArmButton: Boolean = false,
    val hideMonitorButton: Boolean = false,
    val hideSoloButton: Boolean = false,
    val showWaveforms: Boolean = true,
    val showVuMeters: Boolean = true,
    val recordWaveformWindowSec: Float = 15f,
    val playbackWaveformWindowSec: Float = 180f,
    val stripNumberMode: Int = 1,
    val stripIconMode: Int = 0,
    val monitorGainLinear: Float = 2.5f,
)

data class RemoteRoutingSnapshot(
    val inputMap: Map<Int, Int> = emptyMap(),
    val outputMap: Map<Int, Int> = emptyMap(),
    val hiddenRecord: Set<Int> = emptySet(),
    val hiddenSoundcheck: Set<Int> = emptySet(),
)

data class RemoteChannelStripSnapshot(
    val index: Int,
    val displayName: String = "",
    val label: String = "",
    val colorArgb: Int = 0,
    val iconId: Int? = null,
    val armed: Boolean = false,
    val monitoring: Boolean = false,
    val solo: Boolean = false,
    val muted: Boolean = false,
)

data class RemoteSoundcheckSessionSnapshot(
    val sessionDir: String,
    val title: String,
    val durationSec: Float,
    val channelCount: Int,
)

data class RemoteSoundcheckWaveformMeta(
    val durationSec: Float,
    val peaksPerSec: Int,
    val channelCount: Int,
    val loading: Boolean,
    val progress: Float,
)

data class RemoteMixerSnapshot(
    val mixerId: String,
    val displayName: String = "",
    val appMode: Int = 0,
    val isRecording: Boolean = false,
    val isMonitoring: Boolean = false,
    val isPlaying: Boolean = false,
    val isVuMetering: Boolean = false,
    val transportState: String = "IDLE",
    val recordElapsedSec: Float = 0f,
    val playbackPositionSec: Float = 0f,
    val playbackDurationSec: Float = 0f,
    val captureChannelCount: Int = 0,
    val channelStrips: List<RemoteChannelStripSnapshot> = emptyList(),
    val captureMeterLevels: Map<Int, Float> = emptyMap(),
    val soundcheckMeterLevels: Map<Int, Float> = emptyMap(),
    val soundcheckSessions: List<RemoteSoundcheckSessionSnapshot> = emptyList(),
    val selectedSoundcheckDir: String? = null,
    val soundcheckWaveformMeta: RemoteSoundcheckWaveformMeta? = null,
    val soundcheckViewStartSec: Float = 0f,
    val soundcheckViewWindowSec: Float = 180f,
    val soundcheckLoopStartSec: Float? = null,
    val soundcheckLoopEndSec: Float? = null,
    val soundcheckLoopEnabled: Boolean = false,
    val statusMessage: String? = null,
    val warningMessage: String? = null,
    val lastRecordingPath: String? = null,
    val hostMixerReady: Boolean = false,
    val routing: RemoteRoutingSnapshot? = null,
)

data class RemoteMirrorSnapshot(
    val protocolVersion: Int,
    val hostName: String,
    val activeMixerId: String? = null,
    val settings: RemoteSettingsSnapshot = RemoteSettingsSnapshot(),
    val mixers: List<RemoteMixerProfileSnapshot> = emptyList(),
    val sessions: Map<String, RemoteMixerSnapshot> = emptyMap(),
)

data class RemoteMixerProfileSnapshot(
    val id: String,
    val displayName: String,
)

data class WaveformChunkMessage(
    val mixerId: String,
    val sessionDir: String,
    val channel: Int,
    val startSec: Float,
    val peaks: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveformChunkMessage) return false
        return mixerId == other.mixerId &&
            sessionDir == other.sessionDir &&
            channel == other.channel &&
            startSec == other.startSec &&
            peaks.contentEquals(other.peaks)
    }

    override fun hashCode(): Int {
        var result = mixerId.hashCode()
        result = 31 * result + sessionDir.hashCode()
        result = 31 * result + channel
        result = 31 * result + startSec.hashCode()
        result = 31 * result + peaks.contentHashCode()
        return result
    }
}

data class RemoteLiveWaveformTail(
    val generation: Int,
    val peaksU8: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteLiveWaveformTail) return false
        return generation == other.generation && peaksU8.contentEquals(other.peaksU8)
    }

    override fun hashCode(): Int = 31 * generation + peaksU8.contentHashCode()
}

data class RemoteDeltaFrame(
    val activeMixerId: String? = null,
    val settings: RemoteSettingsSnapshot? = null,
    val sessions: Map<String, RemoteMixerDelta> = emptyMap(),
    val liveWaveforms: Map<String, Map<Int, RemoteLiveWaveformTail>> = emptyMap(),
)

data class RemoteMixerDelta(
    val mixerId: String,
    val appMode: Int? = null,
    val transportState: String? = null,
    val isRecording: Boolean? = null,
    val isMonitoring: Boolean? = null,
    val isPlaying: Boolean? = null,
    val isVuMetering: Boolean? = null,
    val recordElapsedSec: Float? = null,
    val playbackPositionSec: Float? = null,
    val playbackDurationSec: Float? = null,
    val captureMeterLevels: Map<Int, Float>? = null,
    val soundcheckMeterLevels: Map<Int, Float>? = null,
    val channelStrips: List<RemoteChannelStripSnapshot>? = null,
    val soundcheckSessions: List<RemoteSoundcheckSessionSnapshot>? = null,
    val selectedSoundcheckDir: String? = null,
    val soundcheckWaveformMeta: RemoteSoundcheckWaveformMeta? = null,
    val soundcheckViewStartSec: Float? = null,
    val soundcheckViewWindowSec: Float? = null,
    val soundcheckLoopStartSec: Float? = null,
    val soundcheckLoopEndSec: Float? = null,
    val soundcheckLoopEnabled: Boolean? = null,
    val statusMessage: String? = null,
    val warningMessage: String? = null,
    val lastRecordingPath: String? = null,
    val hostMixerReady: Boolean? = null,
    val routing: RemoteRoutingSnapshot? = null,
)
