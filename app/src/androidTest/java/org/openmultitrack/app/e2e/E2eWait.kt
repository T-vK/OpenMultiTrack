package org.openmultitrack.app.e2e

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import org.openmultitrack.app.remote.RemoteControlUiState
import org.openmultitrack.app.service.MixerSessionController
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.remote.RemoteConnectionState
import kotlin.math.abs

object E2eWait {
    suspend fun untilMixerState(
        ctrl: MixerSessionController,
        timeoutMs: Long = 30_000,
        predicate: (MixerSessionUiState) -> Boolean,
    ): MixerSessionUiState = withTimeout(timeoutMs) {
        ctrl.state.first(predicate)
    }

    suspend fun untilRemoteState(
        stateFlow: kotlinx.coroutines.flow.StateFlow<RemoteControlUiState>,
        timeoutMs: Long = 60_000,
        predicate: (RemoteControlUiState) -> Boolean,
    ): RemoteControlUiState = withTimeout(timeoutMs) {
        stateFlow.first(predicate)
    }

    suspend fun untilRecording(ctrl: MixerSessionController, timeoutMs: Long = 30_000) {
        untilMixerState(ctrl, timeoutMs) { it.isRecording }
    }

    suspend fun untilNotRecording(ctrl: MixerSessionController, timeoutMs: Long = 30_000) {
        untilMixerState(ctrl, timeoutMs) { !it.isRecording }
    }

    suspend fun untilPlaying(ctrl: MixerSessionController, timeoutMs: Long = 60_000) {
        untilMixerState(ctrl, timeoutMs) { it.isPlaying }
    }

    suspend fun untilNotPlaying(ctrl: MixerSessionController, timeoutMs: Long = 30_000) {
        untilMixerState(ctrl, timeoutMs) { !it.isPlaying }
    }

    suspend fun untilSoundcheckReady(ctrl: MixerSessionController, timeoutMs: Long = 120_000) {
        untilMixerState(ctrl, timeoutMs) { state ->
            state.selectedSoundcheckDir != null &&
                state.playbackDurationSec > 0f &&
                !state.soundcheckWaveformsLoading &&
                state.soundcheckWaveforms != null
        }
    }

    suspend fun untilPlaybackNear(
        ctrl: MixerSessionController,
        targetSec: Float,
        toleranceSec: Float = 1.0f,
        timeoutMs: Long = 30_000,
    ) {
        withTimeout(timeoutMs) {
            while (true) {
                val pos = ctrl.state.value.playbackPositionSec
                if (abs(pos - targetSec) <= toleranceSec) return@withTimeout
                delay(100)
            }
        }
    }

    /**
     * Fails if transport reports playing but the playhead does not move — catches silent USB stalls.
     */
    suspend fun untilRemotePlaybackAdvances(
        remote: E2eRemoteHarness,
        mixerId: String,
        minAdvanceSec: Float = 1.0f,
        observeMs: Long = 3_000,
        timeoutMs: Long = 60_000,
    ) {
        untilRemoteState(remote.state(), timeoutMs) {
            it.sessionByMixer[mixerId]?.isPlaying == true
        }
        val startPos = remote.state().value.sessionByMixer[mixerId]?.playbackPositionSec ?: 0f
        val deadline = System.currentTimeMillis() + observeMs
        withTimeout(timeoutMs) {
            while (System.currentTimeMillis() < deadline) {
                val session = remote.state().value.sessionByMixer[mixerId]
                    ?: error("Remote session missing for $mixerId")
                check(session.isPlaying) {
                    "Playback stopped before advancing (pos=${session.playbackPositionSec}s)"
                }
                if (session.playbackPositionSec >= startPos + minAdvanceSec) {
                    return@withTimeout
                }
                delay(100)
            }
        }
        val endPos = remote.state().value.sessionByMixer[mixerId]?.playbackPositionSec ?: 0f
        check(endPos >= startPos + minAdvanceSec) {
            "Remote playhead did not advance by ${minAdvanceSec}s (start=$startPos end=$endPos)"
        }
    }

    suspend fun untilPlaybackAdvances(
        ctrl: MixerSessionController,
        minAdvanceSec: Float = 1.0f,
        observeMs: Long = 3_000,
        timeoutMs: Long = 30_000,
    ) {
        untilPlaying(ctrl, timeoutMs)
        val startPos = ctrl.state.value.playbackPositionSec
        val deadline = System.currentTimeMillis() + observeMs
        withTimeout(timeoutMs) {
            while (System.currentTimeMillis() < deadline) {
                val state = ctrl.state.value
                check(state.isPlaying) {
                    "Playback stopped before advancing (pos=${state.playbackPositionSec}s, " +
                        "warning=${state.warningMessage})"
                }
                if (state.playbackPositionSec >= startPos + minAdvanceSec) {
                    return@withTimeout
                }
                delay(100)
            }
        }
        val end = ctrl.state.value
        check(end.playbackPositionSec >= startPos + minAdvanceSec) {
            "Playhead did not advance by ${minAdvanceSec}s while playing " +
                "(start=${startPos}s, end=${end.playbackPositionSec}s, warning=${end.warningMessage})"
        }
    }

    suspend fun untilRemoteConnected(
        stateFlow: kotlinx.coroutines.flow.StateFlow<RemoteControlUiState>,
        timeoutMs: Long = 60_000,
    ) {
        untilRemoteState(stateFlow, timeoutMs) {
            it.connectionState == RemoteConnectionState.CONNECTED &&
                it.sessionByMixer.isNotEmpty() &&
                it.uiSettings != null
        }
    }

    suspend fun pollUntil(
        timeoutMs: Long = 30_000,
        intervalMs: Long = 200,
        predicate: suspend () -> Boolean,
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        while (!predicate()) {
            delay(intervalMs)
        }
        true
    } == true

    suspend fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int = 2_000): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
            }.isSuccess
        }

    /**
     * Wait until the remote client mirror is usable again after a brief socket drop.
     * Gives auto-reconnect time to finish before opening a second connection.
     */
    suspend fun awaitRemoteReady(
        remote: E2eRemoteHarness,
        hostIp: String,
        mixerId: String? = null,
        timeoutMs: Long = 120_000,
    ) {
        withTimeout(timeoutMs) {
            while (true) {
                val state = remote.state().value
                val sessionOk = mixerId == null || state.sessionByMixer[mixerId] != null
                if (state.connectionState == RemoteConnectionState.CONNECTED &&
                    state.uiSettings != null &&
                    state.sessionByMixer.isNotEmpty() &&
                    sessionOk
                ) {
                    return@withTimeout
                }
                when (state.connectionState) {
                    RemoteConnectionState.CONNECTING -> delay(500)
                    RemoteConnectionState.DISCONNECTED, RemoteConnectionState.ERROR -> {
                        val deadline = System.currentTimeMillis() + 10_000
                        while (System.currentTimeMillis() < deadline) {
                            val current = remote.state().value.connectionState
                            if (current == RemoteConnectionState.CONNECTED) break
                            if (current == RemoteConnectionState.CONNECTING) {
                                delay(500)
                                continue
                            }
                            delay(500)
                        }
                        if (remote.state().value.connectionState != RemoteConnectionState.CONNECTED) {
                            remote.connectClient(hostIp)
                        }
                    }
                    else -> delay(500)
                }
            }
        }
    }

    /** Wait until the host remote server is up (UDP sync or TCP probe). */
    suspend fun awaitHostRemoteReady(hostIp: String, timeoutMs: Long = 180_000) {
        withTimeout(timeoutMs) {
            while (true) {
                val sync = runCatching {
                    E2eLanSync.await(E2eLanSync.HOST_READY, timeoutMs = 1_000)
                }.getOrNull()
                if (sync != null || isTcpPortOpen(hostIp, org.openmultitrack.domain.remote.RemoteProtocol.HTTP_PORT)) {
                    return@withTimeout
                }
                delay(2_000)
            }
        }
    }
}
