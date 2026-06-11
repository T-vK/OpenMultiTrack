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
import org.openmultitrack.domain.mixer.DemoBandChannels
import org.openmultitrack.domain.mixer.VirtualMixer
import java.io.File

@RunWith(AndroidJUnit4::class)
class VirtualDemoCaptureInstrumentedTest {
    @Test
    fun syntheticCapture_emitsStableMeterAndWaveformPeaks() {
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val engine = CaptureSessionEngine("virtual-demo-test")
            engine.setWaveformConfig(15f, 30)
            engine.startSyntheticCapture(
                scope,
                VirtualMixer.DEMO_CHANNEL_COUNT,
                VirtualMixer.SAMPLE_RATE_HZ,
                generator = SyntheticCaptureGenerator.fromDemoBand(VirtualMixer.SAMPLE_RATE_HZ),
            ).getOrThrow()

            delay(300)
            val meters = engine.debugRawMeterPeaks()
            assertThat(meters[0]).isWithin(0.06f).of(0.38f)
            assertThat(meters[7]).isWithin(0.06f).of(0.48f)

            val tmp = File.createTempFile("omt-demo", null)
            tmp.delete()
            tmp.mkdirs()
            val strips = DemoBandChannels.channelStripStates()
            engine.startRecording(
                CaptureSessionEngine.RecordingConfig(
                    mixerId = "virtual",
                    mixerFolderName = "Demo",
                    storageRoot = tmp,
                    channelStrips = strips,
                ),
            ).getOrThrow()

            delay(2_000)
            val snap = engine.waveformSnapshots(normalize = false)[0]
            assertThat(snap).isNotNull()
            val peaks = snap!!.peaks.filter { it > 0.15f }
            assertThat(peaks.size).isAtLeast(20)

            engine.stopRecording()
            engine.stopCapture()
            tmp.deleteRecursively()
        }
    }
}
