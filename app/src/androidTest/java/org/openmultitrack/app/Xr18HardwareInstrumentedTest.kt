package org.openmultitrack.app

import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.audio.SessionPlayer
import org.openmultitrack.app.audio.SessionRecorder
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbAppProcessRule
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.app.test.UsbInstrumentedPermission
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.sessionio.wav.WavWriter
import kotlin.math.sin
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService
import org.openmultitrack.usb.UsbAudioStreamHandle
import java.io.File

/**
 * XR18 hardware validation: multichannel USB record + playback via UAC2/Oboe.
 *
 * Does not touch mixer OSC snapshots or routing — USB audio only.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x00d4)
class Xr18HardwareInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    private val context get() = usbAppProcessRule.appContext

    @Test
    fun probeReportsEighteenChannelUsbAudio() = runBlocking {
        withXr18Stream { probe, _, _ ->
            assertThat(probe.uac2Caps).isNotNull()
            assertThat(probe.uac2Caps!!.parseOk).isTrue()
            assertThat(probe.uac2Caps!!.maxCaptureChannels).isAtLeast(XR18_CHANNELS)
            assertThat(probe.uac2Caps!!.maxPlaybackChannels).isAtLeast(XR18_CHANNELS)
        }
    }

    @Test
    fun playEighteenChannelTestToneToUsbReturns() = runBlocking {
        withXr18Stream { probe, stream, device ->
            val route = AudioEngineRouter.resolvePlaybackRoute(probe, stream, XR18_CHANNELS)
                ?: error("No playback route for XR18")
            val wavFile = File(context.getExternalFilesDir(null), "instrumented-sessions/xr18/18ch_tone.wav")
                .apply { parentFile?.mkdirs() }
            writeTestTone(wavFile, route.sampleRate, XR18_CHANNELS)
            val player = SessionPlayer()
            val result = player.play(this, wavFile, route, usbDevice = device)
            assertThat(result.isSuccess).isTrue()
            delay(2_000)
            player.stop()
        }
    }

    @Test
    fun recordAllChannelsAndPlayBackToUsbReturns() = runBlocking {
        withXr18Stream { probe, stream, device ->
            val uac2In = probe.uac2Caps?.maxCaptureChannels ?: 0
            val requestedCapture = uac2In.coerceAtMost(XR18_CHANNELS)
            assertThat(requestedCapture).isAtLeast(XR18_CHANNELS)

            val captureRoute = AudioEngineRouter.resolveCaptureRoute(probe, stream, requestedCapture)
                ?: error("No capture route for XR18")
            assertThat(captureRoute.channelCount).isAtLeast(XR18_CHANNELS)

            val sessionsDir = File(context.getExternalFilesDir(null), "instrumented-sessions/xr18").apply {
                mkdirs()
            }
            val recorder = SessionRecorder()
            val recordStart = recorder.start(
                scope = this,
                route = captureRoute,
                outputDir = sessionsDir,
                usbDevice = device,
            )
            assertThat(recordStart.isSuccess).isTrue()

            delay(3_000)
            val session = recorder.stop()
            assertThat(session).isNotNull()
            assertThat(session!!.framesRecorded).isGreaterThan(0)
            assertThat(session.channelCount).isAtLeast(XR18_CHANNELS)

            val wavFormat = WavReader(File(session.filePath)).use { it.format }
            assertThat(wavFormat.channelCount).isEqualTo(session.channelCount)
            assertThat(wavFormat.channelCount).isAtLeast(XR18_CHANNELS)

            val playbackChannels = minOf(XR18_CHANNELS, wavFormat.channelCount)
            val playbackRoute = AudioEngineRouter.resolvePlaybackRoute(
                probe,
                stream,
                playbackChannels,
            ) ?: error("No playback route for XR18")
            assertThat(playbackRoute.channelCount).isAtLeast(playbackChannels)

            val player = SessionPlayer()
            val playResult = player.play(
                this,
                File(session.filePath),
                playbackRoute,
                usbDevice = device,
            )
            assertThat(playResult.isSuccess).isTrue()
            delay(2_000)
            player.stop()
        }
    }

    private fun findXr18Device(enumerator: UsbAudioEnumerator) =
        enumerator.listUsbDevices().first {
            it.vendorId == XR18_VENDOR_ID && it.productId == XR18_PRODUCT_ID
        }

    private fun <T> withXr18Stream(
        block: suspend (FullUsbProbeResult, UsbAudioStreamHandle, android.hardware.usb.UsbDevice) -> T,
    ): T = runBlocking {
        val deviceName = usbAppProcessRule.runOnActivity { activity ->
            findXr18Device(UsbAudioEnumerator(activity)).deviceName
        }
        val stream = usbAppProcessRule.runOnActivity { activity ->
            val usbManager = activity.getSystemService(UsbManager::class.java)
            val device = usbManager.deviceList[deviceName]
                ?: error("XR18 not in UsbManager device list")
            val granted = UsbInstrumentedPermission.ensure(activity, usbManager, device)
            if (!granted && !usbManager.hasPermission(device)) {
                error("USB permission not granted for XR18 — accept the system dialog")
            }
            UsbAudioStreamHandle.open(activity, usbManager, device)
        } ?: error("Could not open USB stream for XR18 — grant USB permission")
        val device = usbAppProcessRule.runOnActivity { activity ->
            activity.getSystemService(UsbManager::class.java).deviceList[deviceName]!!
        }
        val probe = usbAppProcessRule.runOnActivity { activity ->
            val xr18 = findXr18Device(UsbAudioEnumerator(activity))
            UsbAudioProbeService(UsbAudioEnumerator(activity)).probe(xr18)
        }
        try {
            block(probe, stream, device)
        } finally {
            stream.close()
        }
    }

    private fun writeTestTone(file: File, sampleRate: Int, channels: Int, frames: Int = sampleRate / 2) {
        WavWriter(file, channelCount = channels, sampleRate = sampleRate).use { writer ->
            val buffer = FloatArray(frames * channels)
            for (frame in 0 until frames) {
                for (ch in 0 until channels) {
                    val freq = 220.0 * (ch + 1)
                    buffer[frame * channels + ch] =
                        (sin(2.0 * Math.PI * freq * frame / sampleRate) * 0.1).toFloat()
                }
            }
            writer.writeInterleavedFloat(buffer, frames)
        }
    }

    private companion object {
        const val XR18_VENDOR_ID = 0x1397
        const val XR18_PRODUCT_ID = 0x00d4 // lsusb / dumpsys: 5015:212
        const val XR18_CHANNELS = 18
    }
}
