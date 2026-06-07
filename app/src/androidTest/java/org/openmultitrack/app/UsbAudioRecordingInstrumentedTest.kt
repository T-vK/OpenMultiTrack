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
import org.openmultitrack.audio.NativeUac2Probe
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService
import org.openmultitrack.usb.UsbAudioStreamHandle
import java.io.File

/**
 * End-to-end USB audio tests with Flow 8 attached to the emulator.
 *
 * Uses UAC2 isoch when Oboe cannot expose multichannel USB audio (typical on emulator passthrough).
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x050c)
class UsbAudioRecordingInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    private val context get() = usbAppProcessRule.appContext

    @Test
    fun probeShowsMultichannelDescriptorEvenWhenOboeIsLimited() {
        val result = probeOnAppProcess()

        assertThat(result.uac2Caps).isNotNull()
        assertThat(result.uac2Caps!!.maxCaptureChannels).isAtLeast(10)
        assertThat(result.uac2Caps!!.maxPlaybackChannels).isAtLeast(4)

        val oboeIn = result.input?.channelCount ?: 0
        if (oboeIn in 1..2) {
            assertThat(result.uac2Caps!!.maxCaptureChannels).isGreaterThan(oboeIn)
        }
    }

    @Test
    fun recordAndPlaybackRoundTrip() = runBlocking {
        withFlow8Stream { probe, stream, device ->
            val uac2In = probe.uac2Caps?.maxCaptureChannels ?: 0
            assertThat(uac2In).isAtLeast(10)

            val requestedChannels = uac2In.coerceAtMost(10)
            val captureRoute = AudioEngineRouter.resolveCaptureRoute(probe, stream, requestedChannels)
                ?: error("No capture route")
            val playbackRoute = AudioEngineRouter.resolvePlaybackRoute(
                probe,
                stream,
                minOf(4, requestedChannels),
            ) ?: error("No playback route")

            val sessionsDir = File(context.getExternalFilesDir(null), "instrumented-sessions").apply { mkdirs() }
            val recorder = SessionRecorder()
            val player = SessionPlayer()

            val start = recorder.start(
                scope = this,
                route = captureRoute,
                outputDir = sessionsDir,
                usbDevice = device,
            )
            assertThat(start.isSuccess).isTrue()
            delay(2_000)
            val session = recorder.stop()
            assertThat(session).isNotNull()
            assertThat(session!!.framesRecorded).isGreaterThan(0)

            val wavFormat = WavReader(File(session.filePath)).use { it.format }
            assertThat(wavFormat.channelCount).isEqualTo(session.channelCount)
            assertThat(session.channelCount).isAtLeast(2)

            if (requestedChannels >= 10) {
                assertThat(session.channelCount).isAtLeast(10)
                assertThat(wavFormat.channelCount).isAtLeast(10)
            }

            val playResult = player.play(this, File(session.filePath), playbackRoute, usbDevice = device)
            assertThat(playResult.isSuccess).isTrue()
            delay(500)
            player.stop()

            assertThat(probe.uac2Caps!!.maxPlaybackChannels).isAtLeast(4)
        }
    }

    @Test
    fun uac2DescriptorMatchesDirectParse() {
        usbAppProcessRule.runOnActivity { activity ->
            val enumerator = UsbAudioEnumerator(activity)
            val flow8 = enumerator.listUsbDevices().first {
                it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
            }
            val raw = enumerator.getRawConfigDescriptor(flow8.deviceName)
                ?: error("No config descriptor — run scripts/grant-usb-permission.sh")
            val direct = NativeUac2Probe.parseConfigDescriptor(raw)!!
            val viaProbe = UsbAudioProbeService(enumerator).probe(flow8).uac2Caps!!

            assertThat(viaProbe.maxCaptureChannels).isEqualTo(direct.maxCaptureChannels)
            assertThat(viaProbe.maxPlaybackChannels).isEqualTo(direct.maxPlaybackChannels)
        }
    }

    private fun probeOnAppProcess() = usbAppProcessRule.runOnActivity { activity ->
        val flow8 = UsbAudioEnumerator(activity).listUsbDevices().first {
            it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
        }
        UsbAudioProbeService(UsbAudioEnumerator(activity)).probe(flow8)
    }

    private fun <T> withFlow8Stream(
        block: suspend (FullUsbProbeResult, UsbAudioStreamHandle, android.hardware.usb.UsbDevice) -> T,
    ): T {
        val probe = probeOnAppProcess()
        val deviceName = usbAppProcessRule.runOnActivity { activity ->
            UsbAudioEnumerator(activity).listUsbDevices().first {
                it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
            }.deviceName
        }
        return runBlocking {
            val stream = usbAppProcessRule.runOnActivity { activity ->
                val usbManager = activity.getSystemService(UsbManager::class.java)
                val device = usbManager.deviceList[deviceName]
                    ?: error("Flow 8 not in UsbManager device list")
                UsbInstrumentedPermission.ensure(activity, usbManager, device)
                UsbAudioStreamHandle.open(activity, usbManager, device)
            } ?: error("Could not open USB stream — run scripts/grant-usb-permission.sh")
            val device = usbAppProcessRule.runOnActivity { activity ->
                (activity.getSystemService(UsbManager::class.java)).deviceList[deviceName]!!
            }
            try {
                block(probe, stream, device)
            } finally {
                stream.close()
            }
        }
    }

    private companion object {
        const val FLOW8_VENDOR_ID = 0x1397
        const val FLOW8_PRODUCT_ID = 0x050c
    }
}
