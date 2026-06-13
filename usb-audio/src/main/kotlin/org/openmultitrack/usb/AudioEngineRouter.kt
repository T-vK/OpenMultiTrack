package org.openmultitrack.usb

import android.os.SystemClock
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.audio.NativeEngineStatus
import org.openmultitrack.audio.NativeUac2AltSetting
import org.openmultitrack.audio.NativeUac2Engine
import org.openmultitrack.audio.NativeUac2Probe
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.AudioEndpointProbe

enum class AudioBackend { OBOE, UAC2 }

data class CaptureRoute(
    val backend: AudioBackend,
    val oboeDeviceId: Int = -1,
    val usbStream: UsbAudioStreamHandle? = null,
    val uac2Alt: NativeUac2AltSetting? = null,
    val channelCount: Int = 0,
    val sampleRate: Int = 48_000,
) {
    /** Interleaved PCM bytes per audio frame on the USB wire (e.g. Flow 8 = 10ch × 4 = 40). */
    fun pcmBytesPerFrame(): Int = when (backend) {
        AudioBackend.UAC2 -> {
            val alt = uac2Alt
            if (alt != null) alt.channels * alt.subframeBytes else channelCount * 4
        }
        AudioBackend.OBOE -> channelCount * 4
    }
}

data class PlaybackRoute(
    val backend: AudioBackend,
    val oboeDeviceId: Int = -1,
    val usbStream: UsbAudioStreamHandle? = null,
    val uac2Alt: NativeUac2AltSetting? = null,
    val channelCount: Int = 0,
    val sampleRate: Int = 48_000,
)

/**
 * Picks Oboe vs UAC2 isoch based on probe results and requested channel count.
 */
object AudioEngineRouter {
    @Volatile
    var suppressGlobalCaptureTeardown: (() -> Boolean)? = null

    fun resolveCaptureRoute(
        probe: FullUsbProbeResult,
        stream: UsbAudioStreamHandle?,
        requestedChannels: Int,
        sampleRateHz: Int = 48_000,
        maxChannels: Int? = null,
    ): CaptureRoute? {
        fun clampChannels(count: Int): Int {
            val capped = maxChannels?.let { minOf(count, it) } ?: count
            return capped.coerceAtLeast(minOf(requestedChannels, maxChannels ?: requestedChannels))
        }
        val uac2 = probe.uac2Caps
        val oboeIn = probe.input?.takeIf { it.isSuccess }
        val oboeChannels = oboeIn?.channelCount ?: 0

        if (stream != null && uac2 != null && uac2.parseOk) {
            val alt = NativeUac2Probe.selectBestCaptureAlt(uac2, requestedChannels, sampleRateHz)
            if (alt != null && alt.formatValid && alt.channels >= requestedChannels &&
                (oboeChannels < requestedChannels || oboeIn == null)
            ) {
                OmtLog.i(
                    "Router",
                    "capture → UAC2 ${alt.channels}ch (Oboe=${oboeChannels}ch, requested=$requestedChannels)",
                )
                return CaptureRoute(
                    backend = AudioBackend.UAC2,
                    usbStream = stream,
                    uac2Alt = alt,
                    channelCount = clampChannels(alt.channels),
                    sampleRate = alt.sampleRateHz.takeIf { it > 0 } ?: sampleRateHz,
                )
            }
        }

        if (oboeIn != null && oboeChannels >= 1) {
            val channels = clampChannels(minOf(requestedChannels, oboeChannels))
            OmtLog.i("Router", "capture → Oboe ${channels}ch deviceId=${oboeIn.deviceId}")
            return CaptureRoute(
                backend = AudioBackend.OBOE,
                oboeDeviceId = oboeIn.deviceId,
                channelCount = channels,
                sampleRate = oboeIn.sampleRate.takeIf { it > 0 } ?: sampleRateHz,
            )
        }

        if (stream != null && uac2 != null && uac2.parseOk) {
            val alt = NativeUac2Probe.selectBestCaptureAlt(uac2, minOf(requestedChannels, 2), sampleRateHz)
                ?: NativeUac2Probe.selectBestCaptureAlt(uac2, 1, sampleRateHz)
            if (alt != null && alt.formatValid) {
                OmtLog.i("Router", "capture → UAC2 fallback ${alt.channels}ch (Oboe unavailable)")
                return CaptureRoute(
                    backend = AudioBackend.UAC2,
                    usbStream = stream,
                    uac2Alt = alt,
                    channelCount = clampChannels(alt.channels),
                    sampleRate = alt.sampleRateHz.takeIf { it > 0 } ?: sampleRateHz,
                )
            }
        }

        OmtLog.w("Router", "no capture route for requested=$requestedChannels")
        return null
    }

    fun resolvePlaybackRoute(
        probe: FullUsbProbeResult,
        stream: UsbAudioStreamHandle?,
        requestedChannels: Int,
        sampleRateHz: Int = 48_000,
    ): PlaybackRoute? {
        val uac2 = probe.uac2Caps
        val effectiveChannels = if (Flow8UsbPlaybackProfile.isFlow8(probe.usb)) {
            Flow8UsbPlaybackProfile.playbackChannelsFromProbe(uac2?.maxPlaybackChannels ?: 0)
        } else {
            requestedChannels
        }
        val oboeOut = probe.output?.takeIf { it.isSuccess }
        val oboeChannels = oboeOut?.channelCount ?: 0

        if (stream != null && uac2 != null && uac2.parseOk) {
            val alt = NativeUac2Probe.selectBestPlaybackAlt(uac2, effectiveChannels, sampleRateHz)
            if (alt != null && alt.formatValid && alt.channels >= effectiveChannels &&
                (oboeChannels < effectiveChannels || oboeOut == null)
            ) {
                OmtLog.i(
                    "Router",
                    "playback → UAC2 ${alt.channels}ch (Oboe=${oboeChannels}ch, requested=$effectiveChannels)",
                )
                return PlaybackRoute(
                    backend = AudioBackend.UAC2,
                    usbStream = stream,
                    uac2Alt = alt,
                    channelCount = alt.channels,
                    sampleRate = alt.sampleRateHz.takeIf { it > 0 } ?: sampleRateHz,
                )
            }
        }

        if (oboeOut != null && oboeChannels >= 1) {
            val channels = minOf(effectiveChannels, oboeChannels)
            OmtLog.i("Router", "playback → Oboe ${channels}ch deviceId=${oboeOut.deviceId}")
            return PlaybackRoute(
                backend = AudioBackend.OBOE,
                oboeDeviceId = oboeOut.deviceId,
                channelCount = channels,
                sampleRate = oboeOut.sampleRate.takeIf { it > 0 } ?: sampleRateHz,
            )
        }

        if (stream != null && uac2 != null && uac2.parseOk) {
            val alt = NativeUac2Probe.selectBestPlaybackAlt(uac2, minOf(effectiveChannels, 2), sampleRateHz)
                ?: NativeUac2Probe.selectBestPlaybackAlt(uac2, 1, sampleRateHz)
            if (alt != null && alt.formatValid) {
                OmtLog.i("Router", "playback → UAC2 fallback ${alt.channels}ch (Oboe unavailable)")
                return PlaybackRoute(
                    backend = AudioBackend.UAC2,
                    usbStream = stream,
                    uac2Alt = alt,
                    channelCount = alt.channels,
                    sampleRate = alt.sampleRateHz.takeIf { it > 0 } ?: sampleRateHz,
                )
            }
        }

        OmtLog.w("Router", "no playback route for requested=$effectiveChannels")
        return null
    }

    fun startRecording(
        route: CaptureRoute,
        ownerId: String,
        usbDevice: android.hardware.usb.UsbDevice? = null,
    ): NativeEngineStatus = NativeAudioCaptureRegistry.start(ownerId, route, usbDevice)

    fun stopRecording(backend: AudioBackend, ownerId: String) {
        NativeAudioCaptureRegistry.release(backend, ownerId)
    }

    fun stopAllRecording() {
        if (suppressGlobalCaptureTeardown?.invoke() == true) {
            OmtLog.w("Router", "stopAllRecording suppressed — capture session active")
            return
        }
        NativeAudioCaptureRegistry.releaseAll()
    }

    fun readRecordedFrames(dest: FloatArray, maxFrames: Int, backend: AudioBackend): Int =
        when (backend) {
            AudioBackend.OBOE -> NativeAudioEngine.readRecordedFrames(dest, maxFrames)
            AudioBackend.UAC2 -> NativeUac2Engine.readCapturedFrames(dest, maxFrames)
        }

    fun readRecordedPcm(dest: ByteArray, maxFrames: Int, backend: AudioBackend): Int =
        when (backend) {
            AudioBackend.OBOE -> 0
            AudioBackend.UAC2 -> NativeUac2Engine.readCapturedPcmBytes(dest, maxFrames)
        }

    fun capturePcmBytesPerFrame(backend: AudioBackend): Int =
        when (backend) {
            AudioBackend.OBOE -> 0
            AudioBackend.UAC2 -> NativeUac2Engine.captureBytesPerFrame()
        }

    fun recordingDroppedFrames(backend: AudioBackend): Long =
        when (backend) {
            AudioBackend.OBOE -> NativeAudioEngine.recordingDroppedFrames()
            AudioBackend.UAC2 -> NativeUac2Engine.captureDroppedFrames()
        }

    fun startNativePcmFileRecording(path: String, backend: AudioBackend): Boolean =
        when (backend) {
            AudioBackend.UAC2 -> NativeUac2Engine.startPcmFileRecording(path)
            AudioBackend.OBOE -> false
        }

    fun stopNativePcmFileRecording(backend: AudioBackend) {
        when (backend) {
            AudioBackend.UAC2 -> NativeUac2Engine.stopPcmFileRecording()
            AudioBackend.OBOE -> Unit
        }
    }

    fun nativePcmFileFramesWritten(backend: AudioBackend): Long =
        when (backend) {
            AudioBackend.UAC2 -> NativeUac2Engine.pcmFileFramesWritten()
            AudioBackend.OBOE -> 0L
        }

    fun startPlayback(route: PlaybackRoute, usbDevice: android.hardware.usb.UsbDevice? = null): NativeEngineStatus {
        val t0 = SystemClock.elapsedRealtime()
        fun mark(phase: String) {
            OmtLog.i("Router", "startPlayback +${SystemClock.elapsedRealtime() - t0}ms $phase")
        }
        mark("begin backend=${route.backend}")
        stopPlayback()
        mark("stopPlayback done")
        return when (route.backend) {
            AudioBackend.OBOE -> {
                val status = NativeAudioEngine.startPlayback(route.oboeDeviceId, route.channelCount, route.sampleRate)
                mark("Oboe start active=${status.active}")
                status
            }
            AudioBackend.UAC2 -> {
                val stream = route.usbStream ?: return failed("missing usb stream")
                val alt = route.uac2Alt ?: return failed("missing uac2 alt")
                mark("claiming UAC2 iface=${alt.interfaceNumber} alt=${alt.alternateSetting} fd=${stream.fd}")
                val javaClaimed = claimUac2Interface(stream, usbDevice, alt)
                mark("claim done javaClaimed=$javaClaimed, starting native UAC2")
                val status = NativeUac2Engine.startPlayback(stream.fd, alt, javaClaimed)
                mark("UAC2 start active=${status.active}")
                status
            }
        }
    }

    fun stopPlayback() {
        val t0 = SystemClock.elapsedRealtime()
        NativeAudioEngine.stopPlayback()
        NativeUac2Engine.stopPlayback()
        OmtLog.i("Router", "stopPlayback +${SystemClock.elapsedRealtime() - t0}ms done")
    }

    /** Opens the usb fd and claims the playback interface without starting isoch streaming. */
    fun preclaimPlaybackRoute(
        route: PlaybackRoute,
        usbDevice: android.hardware.usb.UsbDevice?,
    ): Boolean {
        if (route.backend != AudioBackend.UAC2) return true
        val stream = route.usbStream ?: return false
        val alt = route.uac2Alt ?: return false
        val t0 = SystemClock.elapsedRealtime()
        val ok = claimUac2Interface(stream, usbDevice, alt)
        OmtLog.i(
            "Router",
            "preclaimPlaybackRoute +${SystemClock.elapsedRealtime() - t0}ms iface=${alt.interfaceNumber} → $ok",
        )
        return ok
    }

    fun writePlaybackFrames(src: FloatArray, frameCount: Int, backend: AudioBackend): Int =
        when (backend) {
            AudioBackend.OBOE -> NativeAudioEngine.writePlaybackFrames(src, frameCount)
            AudioBackend.UAC2 -> NativeUac2Engine.writePlaybackFrames(src, frameCount)
        }

    fun playbackUnderrunFrames(backend: AudioBackend): Long =
        when (backend) {
            AudioBackend.OBOE -> NativeAudioEngine.playbackUnderrunFrames()
            AudioBackend.UAC2 -> NativeUac2Engine.playbackUnderrunFrames()
        }

    private fun claimUac2Interface(
        stream: UsbAudioStreamHandle,
        usbDevice: android.hardware.usb.UsbDevice?,
        alt: NativeUac2AltSetting,
    ): Boolean {
        if (usbDevice == null) return false
        val ok = stream.claimInterface(
            usbDevice,
            alt.interfaceNumber,
            alternateSetting = alt.alternateSetting,
        )
        OmtLog.i("Router", "Java claimInterface ${alt.interfaceNumber} alt=${alt.alternateSetting} → $ok")
        return ok
    }

    private fun failed(message: String): NativeEngineStatus =
        NativeEngineStatus(active = false, channelCount = 0, sampleRate = 0, errorMessage = message)
}
