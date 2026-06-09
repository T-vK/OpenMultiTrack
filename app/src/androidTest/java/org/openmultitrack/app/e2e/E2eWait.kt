package org.openmultitrack.app.e2e

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.openmultitrack.app.service.MixerSessionController
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.app.remote.RemoteControlUiState
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

    suspend fun untilRemoteConnected(
        stateFlow: kotlinx.coroutines.flow.StateFlow<RemoteControlUiState>,
        timeoutMs: Long = 60_000,
    ) {
        untilRemoteState(stateFlow, timeoutMs) {
            it.connectionState == RemoteConnectionState.CONNECTED &&
                it.sessionByMixer.isNotEmpty()
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
}
