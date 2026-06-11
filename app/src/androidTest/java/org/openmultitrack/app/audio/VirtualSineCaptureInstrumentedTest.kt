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
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.VirtualMixer
import java.io.File

@RunWith(AndroidJUnit4::class)
class VirtualSineCaptureInstrumentedTest {
    @Test
    fun syntheticCapture_emitsStableMeterAndWaveformPeaks() {
        runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = CaptureSessionEngine("virtual-sine-test")
        engine.setWaveformConfig(15f, 30)
        engine.startSyntheticCapture(
            scope,
            VirtualMixer.SINE_CHANNEL_COUNT,
            VirtualMixer.SAMPLE_RATE_HZ,
        ).getOrThrow()

        delay(300)
        val meters = engine.debugRawMeterPeaks()
        assertThat(meters[0]).isWithin(0.05f).of(0.35f)

        val tmp = File.createTempFile("omt-sine", null)
        tmp.delete()
        tmp.mkdirs()
        val strips = (0 until VirtualMixer.SINE_CHANNEL_COUNT).map { ChannelStripState(index = it, armed = true) }
        engine.startRecording(
            CaptureSessionEngine.RecordingConfig(
                mixerId = "virtual",
                mixerFolderName = "TestSignal",
                storageRoot = tmp,
                channelStrips = strips,
            ),
        ).getOrThrow()

        delay(2_000)
        val snap = engine.waveformSnapshots(normalize = false)[0]
        assertThat(snap).isNotNull()
        val peaks = snap!!.peaks.filter { it > 0.15f }
        assertThat(peaks.size).isAtLeast(20)
        val spread = (peaks.maxOrNull() ?: 0f) - (peaks.minOrNull() ?: 0f)
        assertThat(spread).isLessThan(0.08f)

        engine.stopRecording()
        engine.stopCapture()
        tmp.deleteRecursively()
        }
    }
}
