package org.openmultitrack.app.health

import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.mixer.HealthIssue
import org.openmultitrack.domain.mixer.HealthLevel
import org.openmultitrack.domain.mixer.MixerHealthSnapshot
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.mixer.ProbeState
import org.openmultitrack.domain.mixer.UsbHealth
import org.openmultitrack.domain.session.isPlaybackMode
import org.openmultitrack.usb.MixerUsbChannelCounts

object MixerHealthCollector {
    fun collect(
        profile: MixerProfile,
        session: MixerSessionUiState?,
        availableUsb: List<UsbAudioDeviceDescriptor>,
        usbPermissionGranted: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): MixerHealthSnapshot {
        val usbAttached = availableUsb.any { device ->
            device.vendorId == profile.vendorId &&
                device.productId == profile.productId &&
                (profile.serialNumber == null || device.serialNumber == profile.serialNumber)
        }
        val probeState = when {
            session?.probing == true -> ProbeState.PROBING
            session?.probe != null -> ProbeState.OK
            else -> ProbeState.NONE
        }
        val probeSummary = session?.probe?.let { probe ->
            val capture = probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
                ?: probe.input?.takeIf { it.isSuccess }?.channelCount
                ?: 0
            val playback = MixerUsbChannelCounts.playbackChannels(probe)
            val rate = probe.uac2Caps?.captureAlts?.firstOrNull()?.sampleRateHz
                ?: probe.input?.sampleRate?.takeIf { it > 0 }
                ?: 48_000
            "$capture in / $playback out @ ${rate / 1000} kHz"
        }
        val usb = UsbHealth(
            attached = usbAttached,
            permissionGranted = usbPermissionGranted,
            probeState = probeState,
            probeSummary = probeSummary,
        )
        val issues = buildIssues(session, usb)
        val overall = when {
            issues.any { it.severity == HealthLevel.BLOCKED } -> HealthLevel.BLOCKED
            issues.any { it.severity == HealthLevel.DEGRADED } -> HealthLevel.DEGRADED
            probeState == ProbeState.OK && usbAttached -> HealthLevel.OK
            else -> HealthLevel.UNKNOWN
        }
        return MixerHealthSnapshot(
            mixerId = profile.id,
            updatedAtMs = nowMs,
            overall = overall,
            usb = usb,
            issues = issues,
        )
    }

    fun playbackBlockingIssue(
        session: MixerSessionUiState?,
        usb: UsbHealth,
        isRemoteClient: Boolean,
    ): HealthIssue? {
        if (isRemoteClient) return null
        if (session?.selectedSoundcheckDir == null) return null
        if (session.appMode.isPlaybackMode != true) return null
        return buildPlaybackIssues(session, usb).firstOrNull()
    }

    private fun buildIssues(session: MixerSessionUiState?, usb: UsbHealth): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()
        if (session?.isUsbDegraded == true) {
            issues += HealthIssue(
                code = "usb_degraded",
                severity = HealthLevel.DEGRADED,
                title = "USB audio interrupted",
                detail = session.warningMessage ?: "Waiting for the mixer to reconnect.",
            )
        }
        if (session?.appMode?.isPlaybackMode == true && session.selectedSoundcheckDir != null) {
            issues += buildPlaybackIssues(session, usb)
        }
        return issues.sortedByDescending { it.severity.ordinal }
    }

    private fun buildPlaybackIssues(session: MixerSessionUiState, usb: UsbHealth): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()
        when {
            !usb.attached ->
                issues += HealthIssue(
                    code = "usb_missing",
                    severity = HealthLevel.BLOCKED,
                    title = "Mixer not on USB",
                    detail = "Reconnect the USB cable to play back to the mixer.",
                )
            !usb.permissionGranted ->
                issues += HealthIssue(
                    code = "usb_permission",
                    severity = HealthLevel.BLOCKED,
                    title = "USB permission required",
                    detail = "Allow USB access when Android prompts, then tap Play again.",
                )
            usb.probeState == ProbeState.PROBING ->
                issues += HealthIssue(
                    code = "probe_in_progress",
                    severity = HealthLevel.DEGRADED,
                    title = "Connecting to mixer",
                    detail = "USB probe in progress — playback will be ready shortly.",
                )
            usb.probeState == ProbeState.NONE ->
                issues += HealthIssue(
                    code = "probe_missing",
                    severity = HealthLevel.BLOCKED,
                    title = "Mixer not ready",
                    detail = "Tap Play to connect USB audio, or unplug and replug the cable.",
                )
        }
        return issues
    }
}
