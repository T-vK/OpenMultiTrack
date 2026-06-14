package org.openmultitrack.app

import android.hardware.usb.UsbManager
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.audio.Flow8StereoToneGenerator
import org.openmultitrack.app.e2e.E2eLogcat
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbAppProcessRule
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.app.test.UsbInstrumentedPermission
import org.openmultitrack.usb.AudioBackend
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.Flow8UsbPlaybackProfile
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService
import org.openmultitrack.usb.UsbAudioStreamHandle

/**
 * Isolated FLOW 8 stereo playback — no UI, recording, routing, or soundcheck session.
 *
 * Opens UAC2 playback OUT (4ch), starts the IFB capture feeder, and streams
 * 440 Hz (U01) + 554 Hz (U02) for [PLAY_MS]. Listen on the mixer USB returns while
 * the test runs.
 *
 * Run on the USB host tablet:
 * `./scripts/run-flow8-stereo-playback-test.sh --serial 192.168.3.62:45551`
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x050c)
class Flow8StereoPlaybackInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    @Test
    fun stereoToneOnUsbReturnsU01AndU02() = runBlocking {
        withFlow8Stream { probe, stream, device ->
            AudioEngineRouter.forceStopAllRecording()
            delay(Flow8UsbPlaybackProfile.PRE_PLAYBACK_DELAY_MS)

            val playbackRoute = AudioEngineRouter.resolvePlaybackRoute(
                probe,
                stream,
                Flow8UsbPlaybackProfile.USB_PLAYBACK_CHANNELS,
            ) ?: error("No UAC2 playback route")
            assertThat(playbackRoute.backend).isEqualTo(AudioBackend.UAC2)
            assertThat(playbackRoute.channelCount).isAtLeast(2)

            val captureChannels = probe.uac2Caps?.maxCaptureChannels?.coerceAtLeast(10) ?: 10
            val ifbRoute = AudioEngineRouter.resolveCaptureRoute(probe, stream, captureChannels)
                ?: error("No UAC2 capture route for IFB feeder")

            Log.i(
                TAG,
                "starting stereo playback ${playbackRoute.channelCount}ch @ ${playbackRoute.sampleRate}Hz " +
                    "(U01=${LEFT_HZ.toInt()}Hz U02=${RIGHT_HZ.toInt()}Hz for ${PLAY_MS}ms)",
            )
            val started = AudioEngineRouter.startPlayback(playbackRoute, device, ifbRoute)
            assertThat(started.active).isTrue()
            assertThat(started.errorMessage).isNull()
            assertThat(AudioEngineRouter.isIfbFeederActive()).isTrue()

            val sampleRate = playbackRoute.sampleRate.coerceAtLeast(48_000)
            val channelCount = playbackRoute.channelCount
            val generator = Flow8StereoToneGenerator(
                channelCount = channelCount,
                sampleRate = sampleRate,
                leftHz = LEFT_HZ,
                rightHz = RIGHT_HZ,
            )
            val scratch = FloatArray(2048 * channelCount)
            val chunk = FloatArray(2048 * channelCount)

            val primeTarget = (sampleRate / 10).coerceIn(2_400, 4_800)
            var primed = 0
            while (primed < primeTarget) {
                val frames = generator.fill(scratch, 2048)
                if (frames <= 0) break
                primed += writeFrames(scratch, frames, channelCount, chunk)
            }
            Log.i(TAG, "ring primed with $primed frames")

            var submittedFrames = 0L
            var paceAnchorNs = System.nanoTime()
            val paceStartFrames = 0L
            val deadlineMs = SystemClock.elapsedRealtime() + PLAY_MS
            while (SystemClock.elapsedRealtime() < deadlineMs) {
                val frames = generator.fill(scratch, 2048)
                if (frames <= 0) break
                var offset = 0
                while (offset < frames) {
                    throttleUac2IfAhead(submittedFrames, sampleRate, paceAnchorNs, paceStartFrames)
                    val left = frames - offset
                    val sampleStart = offset * channelCount
                    val sampleCount = left * channelCount
                    System.arraycopy(scratch, sampleStart, chunk, 0, sampleCount)
                    val written = AudioEngineRouter.writePlaybackFrames(chunk, left, AudioBackend.UAC2)
                    if (written <= 0) {
                        delay(1)
                        continue
                    }
                    offset += written
                    submittedFrames += written
                }
            }

            val underruns = AudioEngineRouter.playbackUnderrunFrames(AudioBackend.UAC2)
            Log.i(TAG, "stereo playback done submitted=$submittedFrames underruns=$underruns")

            assertThat(submittedFrames).isAtLeast((sampleRate * PLAY_MS / 1000L) / 2)
            assertThat(underruns).isLessThan(submittedFrames / 4)

            val log = E2eLogcat.dumpRecent(400, TAG, "Router", "Audio", "OpenMultiTrack")
            E2eLogcat.assertNoPlaybackFaults(log)
            assertThat(log).doesNotContain("IFB feeder failed")
        }
    }

    private suspend fun writeFrames(
        scratch: FloatArray,
        frames: Int,
        channelCount: Int,
        chunk: FloatArray,
    ): Int {
        var offset = 0
        var writtenTotal = 0
        while (offset < frames) {
            val left = frames - offset
            val sampleStart = offset * channelCount
            val sampleCount = left * channelCount
            System.arraycopy(scratch, sampleStart, chunk, 0, sampleCount)
            val written = AudioEngineRouter.writePlaybackFrames(chunk, left, AudioBackend.UAC2)
            if (written <= 0) {
                delay(1)
                continue
            }
            offset += written
            writtenTotal += written
        }
        return writtenTotal
    }

    private suspend fun throttleUac2IfAhead(
        submittedFrames: Long,
        sampleRate: Int,
        paceAnchorNs: Long,
        paceStartFrames: Long,
    ) {
        val headroomFrames = (sampleRate / 10).coerceAtLeast(2_400).toLong()
        while (true) {
            val elapsedNs = System.nanoTime() - paceAnchorNs
            val targetFrames = paceStartFrames + elapsedNs * sampleRate / 1_000_000_000L
            val ahead = submittedFrames - targetFrames
            if (ahead <= headroomFrames) return
            val sleepMs = ((ahead - headroomFrames) * 1_000L / sampleRate).coerceIn(1L, 50L)
            delay(sleepMs)
        }
    }

    private fun probeOnAppProcess(): FullUsbProbeResult = usbAppProcessRule.runOnActivity { activity ->
        val flow8 = UsbAudioEnumerator(activity).listUsbDevices().first {
            it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
        }
        UsbAudioProbeService(UsbAudioEnumerator(activity)).probe(flow8)
    }

    private suspend fun <T> withFlow8Stream(
        block: suspend (FullUsbProbeResult, UsbAudioStreamHandle, android.hardware.usb.UsbDevice) -> T,
    ): T {
        val probe = probeOnAppProcess()
        val deviceName = usbAppProcessRule.runOnActivity { activity ->
            UsbAudioEnumerator(activity).listUsbDevices().first {
                it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
            }.deviceName
        }
        val stream = usbAppProcessRule.runOnActivity { activity ->
            val usbManager = activity.getSystemService(UsbManager::class.java)
            val device = usbManager.deviceList[deviceName]
                ?: error("Flow 8 not in UsbManager device list")
            UsbInstrumentedPermission.ensure(activity, usbManager, device)
            UsbAudioStreamHandle.open(activity, usbManager, device)
        } ?: error("Could not open USB stream — grant USB permission first")
        val device = usbAppProcessRule.runOnActivity { activity ->
            activity.getSystemService(UsbManager::class.java).deviceList[deviceName]!!
        }
        return try {
            block(probe, stream, device)
        } finally {
            AudioEngineRouter.stopPlayback()
            AudioEngineRouter.forceStopAllRecording()
            stream.close()
        }
    }

    private companion object {
        const val TAG = "Flow8StereoPlayback"
        const val FLOW8_VENDOR_ID = 0x1397
        const val FLOW8_PRODUCT_ID = 0x050c
        const val PLAY_MS = 8_000L
        const val LEFT_HZ = 440.0
        const val RIGHT_HZ = 554.0
    }
}
