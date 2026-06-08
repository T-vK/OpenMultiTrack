package org.openmultitrack.app.root

import android.media.AudioDeviceInfo
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.usb.UsbAudioEnumerator

/**
 * Loads snd-aloop on rooted devices and exposes loopback PCM nodes to Oboe via AudioManager.
 * Other apps may see the loopback capture side as a microphone if the ROM audio policy allows it.
 */
object LoopbackSetup {
    data class LoopbackDevices(
        val playbackDeviceId: Int?,
        val captureDeviceId: Int?,
        val cardNumber: Int?,
        val statusMessage: String,
    )

    fun setup(enumerator: UsbAudioEnumerator): Result<LoopbackDevices> {
        if (!RootShell.isAvailable()) {
            return Result.failure(IllegalStateException("Root access not available"))
        }

        RootShell.runCommand("modprobe snd-aloop pcm_substreams=1 enable=1 index=1").getOrElse {
            RootShell.runCommand("modprobe snd-aloop").getOrElse { modprobeErr ->
                return Result.failure(
                    IllegalStateException("Could not load snd-aloop: ${modprobeErr.message}"),
                )
            }
        }

        val cards = RootShell.runCommand("cat /proc/asound/cards").getOrElse {
            return Result.failure(IllegalStateException("Could not read ALSA cards: ${it.message}"))
        }
        val cardLine = cards.lineSequence()
            .firstOrNull { it.contains("Loopback", ignoreCase = true) }
            ?: return Result.failure(IllegalStateException("Loopback card not found after modprobe"))

        val cardNumber = Regex("""^(\d+)\s""").find(cardLine.trim())?.groupValues?.get(1)?.toIntOrNull()
        if (cardNumber != null) {
            RootShell.runCommand(
                "chmod 666 /dev/snd/pcmC${cardNumber}D0p /dev/snd/pcmC${cardNumber}D0c 2>/dev/null; true",
            )
        }

        val outputs = enumerator.listAudioOutputDevices()
        val inputs = enumerator.listAudioInputDevices()
        val playback = findLoopbackDevice(outputs)
        val capture = findLoopbackDevice(inputs)

        val message = buildString {
            append("snd-aloop loaded")
            cardNumber?.let { append(" (card $it)") }
            append(". ")
            if (capture != null) {
                append("Loopback input id=${capture.id}. ")
                append("Select it as microphone in other apps. ")
            } else {
                append("Loopback input not visible to apps — ROM may need audio policy patch. ")
            }
            if (playback == null) {
                append("Loopback output not found in AudioManager.")
            }
        }
        OmtLog.i("Loopback", message)

        return Result.success(
            LoopbackDevices(
                playbackDeviceId = playback?.id,
                captureDeviceId = capture?.id,
                cardNumber = cardNumber,
                statusMessage = message.trim(),
            ),
        )
    }

    private fun findLoopbackDevice(devices: List<AudioDeviceInfo>): AudioDeviceInfo? =
        devices.firstOrNull { info ->
            val product = info.productName?.toString()?.lowercase().orEmpty()
            product.contains("loopback") || product.contains("aloop")
        }
}
