package org.openmultitrack.app.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.data.RecordingWritePlan
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.DemoBandChannels
import org.openmultitrack.domain.mixer.VirtualMixer
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.session.SessionPlaybackDuration
import org.openmultitrack.sessionio.wav.PerChannelWavReader
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * End-to-end timing: UI elapsed, captured frames, on-disk duration, and playback length
 * must track real wall-clock seconds during synthetic multichannel capture.
 */
@RunWith(AndroidJUnit4::class)
class RecordingSessionTimingInstrumentedTest {
    private fun assertElapsedMatchesWall(elapsedSec: Float, wallSec: Float, toleranceSec: Float = 1.5f) {
        assertThat(elapsedSec).isWithin(toleranceSec).of(wallSec)
    }

    private fun assertDurationMatchesWall(durationSec: Float, wallSec: Float, toleranceSec: Float = 1.5f) {
        assertThat(durationSec).isWithin(toleranceSec).of(wallSec)
    }

    @Test
    fun recordingElapsed_tracksWallClock_andSoundcheckDurationMatches() {
        runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = CaptureSessionEngine("recording-timing-test")
        val sampleRate = VirtualMixer.SAMPLE_RATE_HZ
        val channelCount = VirtualMixer.DEMO_CHANNEL_COUNT
        engine.setWaveformConfig(15f, 30)
        engine.startSyntheticCapture(
            scope = scope,
            channelCount = channelCount,
            sampleRateHz = sampleRate,
            generator = SyntheticCaptureGenerator.fromDemoBand(sampleRate),
        ).getOrThrow()

        val root = File.createTempFile("omt-timing-root", null).apply {
            delete()
            mkdirs()
        }
        val sessionDir = File(root, "Demo/session-timing").apply { mkdirs() }
        val mirrorDir = File(root, "mirror/session-timing").apply { mkdirs() }
        val spillDir = File(root, "spill/session-timing").apply { mkdirs() }
        val strips = DemoBandChannels.channelStripStates().map { it.copy(armed = true) }
        val writePlan = RecordingWritePlan(
            primarySessionDir = sessionDir,
            mirrorSessionDirs = listOf(mirrorDir),
            spillSessionDir = spillDir,
            primaryRoot = root,
            minFreeBytes = 0L,
            liveCaptureStagingFile = null,
        )

        engine.startRecording(
            CaptureSessionEngine.RecordingConfig(
                mixerId = "virtual",
                mixerFolderName = "Demo",
                storageRoot = root,
                channelStrips = strips,
                writePlan = writePlan,
            ),
        ).getOrThrow()
        engine.anchorRecordingTimeline()

        val targetWallSec = 12f
        val wallStartMs = System.currentTimeMillis()
        while (true) {
            val wallSec = (System.currentTimeMillis() - wallStartMs) / 1000f
            if (wallSec >= targetWallSec) break
            val elapsed = engine.recordElapsedSec()
            assertElapsedMatchesWall(elapsed, wallSec, toleranceSec = 2f)
            val snapshot = engine.debugTimingSnapshot()
            assertElapsedMatchesWall(snapshot.capturedSec, wallSec, toleranceSec = 2f)
            assertThat(snapshot.droppedFrames).isEqualTo(0)
            delay(400)
        }

        val totalWallSec = (System.currentTimeMillis() - wallStartMs) / 1000f
        val capturedBeforeStop = engine.debugTimingSnapshot()
        val stopElapsedMs = measureTimeMillis {
            val session = engine.stopRecording()
            assertThat(session).isNotNull()
        }
        engine.stopCapture()

        val metadata = SessionMetadata.read(sessionDir)
        assertThat(metadata).isNotNull()
        val diskDurationSec = SessionPlaybackDuration.durationSec(sessionDir, metadata!!)

        assertDurationMatchesWall(diskDurationSec, totalWallSec)
        assertThat(diskDurationSec).isWithin(1.5f).of(capturedBeforeStop.capturedSec)

        PerChannelWavReader.open(sessionDir, metadata).use { reader ->
            val playbackDurationSec = reader.frameCount.toFloat() / reader.sampleRate
            assertDurationMatchesWall(playbackDurationSec, totalWallSec)
            assertThat(playbackDurationSec).isWithin(1f).of(diskDurationSec)
        }

        assertThat(stopElapsedMs).isLessThan(30_000L)

        root.deleteRecursively()
        }
    }
}
