package org.openmultitrack.app.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.openmultitrack.app.data.StorageAccessHelper
import org.openmultitrack.app.device.DevicePrerequisites
import org.openmultitrack.app.device.NetworkSnapshot
import org.openmultitrack.app.scribble.Flow8BlePermissions
import org.openmultitrack.app.scribble.ScribbleImportSupport
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.audio.RecordAudioPermissions
import org.openmultitrack.domain.mixer.MixerHealthSnapshot
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.mixer.ProbeState
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.remote.RemoteRole
import java.text.DateFormat
import java.util.Date

object MixerConnectivityStatusBuilder {
    fun build(
        mixer: MixerProfile,
        health: MixerHealthSnapshot,
        session: MixerSessionUiState?,
        network: NetworkSnapshot,
        supportsOsc: Boolean,
        supportsFlow8Ble: Boolean,
        channelLabelsCached: Boolean,
        channelLabelsCachedAtMs: Long?,
        storageWritable: Boolean,
        storagePath: String,
        batteryOptimizationIgnored: Boolean,
        remoteRole: RemoteRole,
        remoteConnectionState: RemoteConnectionState,
        remoteHostLabel: String?,
        remoteClientCount: Int,
        snapshotCount: Int,
        snapshotsLoading: Boolean,
        context: Context,
    ): ConnectivityChecklist {
        val items = mutableListOf<ConnectivityCheckItem>()
        val usb = health.usb
        val audio = health.audio
        val osc = health.osc
        val probe = session?.probe

        // --- USB ---
        items += item(
            id = "usb_attached",
            group = ConnectivityGroup.USB,
            label = "USB device attached",
            status = when {
                usb.attached -> ConnectivityStatus.OK
                else -> ConnectivityStatus.ERROR
            },
            detail = if (usb.attached) "Mixer detected on USB" else "Plug in the USB cable",
        )
        items += item(
            id = "usb_permission",
            group = ConnectivityGroup.USB,
            label = "USB device permission",
            status = when {
                !usb.attached -> ConnectivityStatus.NOT_APPLICABLE
                usb.permissionGranted -> ConnectivityStatus.OK
                else -> ConnectivityStatus.ERROR
            },
            detail = when {
                !usb.attached -> null
                usb.permissionGranted -> "Access granted"
                else -> "Android must allow USB access for multitrack audio"
            },
            action = if (usb.attached && !usb.permissionGranted) ConnectivityAction.GRANT_USB else null,
            actionLabel = "Grant",
        )
        items += item(
            id = "usb_probe",
            group = ConnectivityGroup.USB,
            label = "USB audio probe",
            status = when (usb.probeState) {
                ProbeState.OK -> ConnectivityStatus.OK
                ProbeState.PROBING -> ConnectivityStatus.PENDING
                ProbeState.FAILED -> ConnectivityStatus.ERROR
                ProbeState.NONE -> when {
                    !usb.attached -> ConnectivityStatus.NOT_APPLICABLE
                    !usb.permissionGranted -> ConnectivityStatus.OFF
                    else -> ConnectivityStatus.WARNING
                }
            },
            detail = when (usb.probeState) {
                ProbeState.OK -> usb.probeSummary
                ProbeState.PROBING -> "Detecting channels and sample rate…"
                ProbeState.FAILED -> "Could not open USB audio"
                ProbeState.NONE -> if (usb.permissionGranted) "Tap Refresh or start record/playback" else null
            },
            action = if (usb.probeState == ProbeState.NONE && usb.permissionGranted) {
                ConnectivityAction.REFRESH
            } else {
                null
            },
            actionLabel = "Refresh",
        )
        val uac2 = probe?.uac2Caps != null
        items += item(
            id = "usb_uac2",
            group = ConnectivityGroup.USB,
            label = "UAC 2.0 multitrack",
            status = when {
                usb.probeState != ProbeState.OK -> ConnectivityStatus.NOT_APPLICABLE
                uac2 -> ConnectivityStatus.OK
                else -> ConnectivityStatus.WARNING
            },
            detail = when {
                usb.probeState != ProbeState.OK -> null
                uac2 -> "Native UAC2 path available"
                else -> "Using Android audio fallback"
            },
        )
        items += item(
            id = "usb_capture_open",
            group = ConnectivityGroup.USB,
            label = "USB recording interface",
            status = when {
                audio == null -> ConnectivityStatus.UNKNOWN
                audio.captureChannels > 0 || audio.isRecording || audio.isMonitoring -> ConnectivityStatus.OK
                usb.probeState == ProbeState.OK -> ConnectivityStatus.OFF
                else -> ConnectivityStatus.NOT_APPLICABLE
            },
            detail = when {
                audio == null -> null
                audio.captureChannels > 0 -> "${audio.captureChannels} capture channels open"
                audio.isRecording -> "Recording active"
                audio.isMonitoring -> "Monitor path open"
                usb.probeState == ProbeState.OK -> "Ready — starts on record or monitor"
                else -> null
            },
        )
        items += item(
            id = "usb_capture_streaming",
            group = ConnectivityGroup.USB,
            label = "USB recording stream",
            status = when {
                audio == null -> ConnectivityStatus.UNKNOWN
                audio.isUsbDegraded -> ConnectivityStatus.ERROR
                audio.isRecording -> ConnectivityStatus.OK
                audio.isMonitoring -> ConnectivityStatus.OK
                else -> ConnectivityStatus.OFF
            },
            detail = when {
                audio?.isUsbDegraded == true -> "Stream interrupted — waiting for device"
                audio?.isRecording == true -> "Sending multitrack audio to disk"
                audio?.isMonitoring == true -> "Live inputs on headphone/USB monitor"
                else -> "Idle"
            },
        )
        items += item(
            id = "usb_playback_open",
            group = ConnectivityGroup.USB,
            label = "USB playback interface",
            status = when {
                audio == null -> ConnectivityStatus.UNKNOWN
                audio.playbackChannels > 0 || audio.isPlaying -> ConnectivityStatus.OK
                usb.probeState == ProbeState.OK -> ConnectivityStatus.OFF
                else -> ConnectivityStatus.NOT_APPLICABLE
            },
            detail = when {
                audio == null -> null
                audio.playbackChannels > 0 -> "${audio.playbackChannels} playback channels open"
                audio.isPlaying -> "Soundcheck playback route active"
                usb.probeState == ProbeState.OK -> "Ready — opens on play"
                else -> null
            },
        )
        items += item(
            id = "usb_playback_streaming",
            group = ConnectivityGroup.USB,
            label = "USB playback stream",
            status = when {
                audio == null -> ConnectivityStatus.UNKNOWN
                audio.isUsbDegraded -> ConnectivityStatus.ERROR
                audio.isPlaying -> ConnectivityStatus.OK
                else -> ConnectivityStatus.OFF
            },
            detail = when {
                audio?.isPlaying == true -> "Sending audio to mixer USB returns"
                else -> "Idle"
            },
        )
        items += item(
            id = "monitor",
            group = ConnectivityGroup.AUDIO,
            label = "Input monitor",
            status = when {
                audio == null -> ConnectivityStatus.UNKNOWN
                audio.isMonitoring -> ConnectivityStatus.OK
                else -> ConnectivityStatus.OFF
            },
            detail = if (audio?.isMonitoring == true) "Monitoring live USB inputs" else "Off",
        )

        // --- Network / OSC ---
        items += item(
            id = "lan_transport",
            group = ConnectivityGroup.NETWORK,
            label = "Wi‑Fi / LAN connected",
            status = if (network.hasLanTransport) ConnectivityStatus.OK else ConnectivityStatus.WARNING,
            detail = network.transport,
        )
        items += item(
            id = "internet",
            group = ConnectivityGroup.NETWORK,
            label = "Internet access",
            status = if (network.hasValidatedInternet) ConnectivityStatus.OK else ConnectivityStatus.OFF,
            detail = if (network.hasValidatedInternet) {
                "Validated — not required for mixer OSC"
            } else {
                "Offline is fine for local mixer control"
            },
        )
        if (osc != null) {
            items += item(
                id = "osc_ip",
                group = ConnectivityGroup.NETWORK,
                label = "Mixer on network (OSC IP)",
                status = when {
                    !osc.supported -> ConnectivityStatus.NOT_APPLICABLE
                    osc.configured -> ConnectivityStatus.OK
                    else -> ConnectivityStatus.WARNING
                },
                detail = osc.host ?: "Set mixer IP in mixer settings",
                action = if (osc.supported && !osc.configured) ConnectivityAction.SET_OSC_IP else null,
                actionLabel = "Set IP",
            )
            items += item(
                id = "osc_ready",
                group = ConnectivityGroup.NETWORK,
                label = "OSC routing & labels",
                status = when {
                    !osc.configured -> ConnectivityStatus.OFF
                    session?.channelStrips?.any { it.displayName.isNotBlank() } == true -> ConnectivityStatus.OK
                    else -> ConnectivityStatus.WARNING
                },
                detail = when {
                    !osc.configured -> "Requires mixer IP"
                    channelLabelsCached -> "Channel names available"
                    else -> "Import labels from mixer when ready"
                },
            )
        }

        // --- Bluetooth (Flow 8) ---
        if (supportsFlow8Ble) {
            val btPerm = Flow8BlePermissions.hasAll(context)
            val locNeeded = Flow8BlePermissions.needsLocationForBleScan(context)
            val locGranted = !locNeeded || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val btOn = DevicePrerequisites.isBluetoothEnabled(context)
            items += item(
                id = "bt_permission",
                group = ConnectivityGroup.BLUETOOTH,
                label = "Bluetooth permission",
                status = if (btPerm) ConnectivityStatus.OK else ConnectivityStatus.ERROR,
                detail = if (btPerm) "Nearby devices allowed" else "Needed for FLOW 8 label sync",
                action = if (!btPerm) ConnectivityAction.GRANT_BLUETOOTH else null,
                actionLabel = "Allow",
            )
            items += item(
                id = "location_permission",
                group = ConnectivityGroup.BLUETOOTH,
                label = "Location / nearby devices",
                status = when {
                    !locNeeded -> ConnectivityStatus.NOT_APPLICABLE
                    locGranted -> ConnectivityStatus.OK
                    else -> ConnectivityStatus.WARNING
                },
                detail = if (locNeeded && !locGranted) {
                    "Android requires this for BLE scan on this OS version"
                } else {
                    "Not required on this Android version"
                },
                action = if (locNeeded && !locGranted) ConnectivityAction.GRANT_LOCATION else null,
                actionLabel = "Allow",
            )
            items += item(
                id = "bt_radio",
                group = ConnectivityGroup.BLUETOOTH,
                label = "Bluetooth radio",
                status = if (btOn) ConnectivityStatus.OK else ConnectivityStatus.ERROR,
                detail = if (btOn) "On" else "Turn on to sync FLOW 8 names",
                action = if (!btOn) ConnectivityAction.ENABLE_BLUETOOTH else null,
                actionLabel = "Turn on",
            )
            items += item(
                id = "bt_session",
                group = ConnectivityGroup.BLUETOOTH,
                label = "Bluetooth mixer session",
                status = ConnectivityStatus.OFF,
                detail = "On-demand only — connects briefly to read names, then disconnects",
            )
        }

        // --- Sync ---
        items += item(
            id = "channel_labels",
            group = ConnectivityGroup.SYNC,
            label = "Channel name sync",
            status = when {
                channelLabelsCached -> ConnectivityStatus.OK
                session?.channelStrips?.any { it.displayName.isNotBlank() } == true -> ConnectivityStatus.OK
                supportsOsc || supportsFlow8Ble -> ConnectivityStatus.WARNING
                else -> ConnectivityStatus.NOT_APPLICABLE
            },
            detail = when {
                channelLabelsCachedAtMs != null -> {
                    val whenStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(channelLabelsCachedAtMs))
                    "Last cached $whenStr"
                }
                session?.channelStrips?.any { it.displayName.isNotBlank() } == true -> "Loaded in this session"
                supportsOsc -> "OSC auto-sync when mixer is reachable"
                supportsFlow8Ble -> "Tap Sync in mixer picker for FLOW 8"
                else -> null
            },
            action = if (supportsOsc || supportsFlow8Ble) ConnectivityAction.SYNC_LABELS else null,
            actionLabel = "Sync now",
        )
        if (supportsOsc) {
            items += item(
                id = "snapshots",
                group = ConnectivityGroup.SYNC,
                label = "Mixer snapshots",
                status = when {
                    snapshotsLoading -> ConnectivityStatus.PENDING
                    snapshotCount > 0 -> ConnectivityStatus.OK
                    osc?.configured != true -> ConnectivityStatus.OFF
                    else -> ConnectivityStatus.WARNING
                },
                detail = when {
                    snapshotsLoading -> "Reading snapshot list…"
                    snapshotCount > 0 -> "$snapshotCount snapshots known"
                    osc?.configured != true -> "Set OSC IP first"
                    else -> "Refresh to load routing snapshot names"
                },
                action = if (osc?.configured == true) ConnectivityAction.REFRESH_SNAPSHOTS else null,
                actionLabel = "Refresh",
            )
        }

        // --- Permissions ---
        items += item(
            id = "mic_permission",
            group = ConnectivityGroup.PERMISSIONS,
            label = "Microphone permission",
            status = if (RecordAudioPermissions.hasPermission(context)) ConnectivityStatus.OK else ConnectivityStatus.ERROR,
            detail = "Required by Android for USB multitrack capture",
            action = if (!RecordAudioPermissions.hasPermission(context)) ConnectivityAction.GRANT_MIC else null,
            actionLabel = "Allow",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifOk = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            items += item(
                id = "notification_permission",
                group = ConnectivityGroup.PERMISSIONS,
                label = "Notification permission",
                status = if (notifOk) ConnectivityStatus.OK else ConnectivityStatus.WARNING,
                detail = "Shows recording control in the shade",
                action = if (!notifOk) ConnectivityAction.GRANT_NOTIFICATIONS else null,
                actionLabel = "Allow",
            )
        }
        items += item(
            id = "storage",
            group = ConnectivityGroup.PERMISSIONS,
            label = "Recording storage",
            status = if (storageWritable) ConnectivityStatus.OK else ConnectivityStatus.ERROR,
            detail = storagePath,
            action = if (!storageWritable) ConnectivityAction.CHOOSE_STORAGE else null,
            actionLabel = "Choose folder",
        )
        items += item(
            id = "battery",
            group = ConnectivityGroup.PERMISSIONS,
            label = "Battery optimization",
            status = if (batteryOptimizationIgnored) ConnectivityStatus.OK else ConnectivityStatus.WARNING,
            detail = if (batteryOptimizationIgnored) {
                "Unrestricted — safer for long recordings"
            } else {
                "May stop background recording on some devices"
            },
            action = if (!batteryOptimizationIgnored) ConnectivityAction.BATTERY_SETTINGS else null,
            actionLabel = "Fix",
        )

        // --- Remote ---
        if (remoteRole != RemoteRole.OFF) {
            items += item(
                id = "remote",
                group = ConnectivityGroup.REMOTE,
                label = when (remoteRole) {
                    RemoteRole.HOST -> "Remote control hosting"
                    RemoteRole.CLIENT -> "Remote control client"
                    RemoteRole.OFF -> "Remote control"
                },
                status = when (remoteConnectionState) {
                    RemoteConnectionState.CONNECTED -> ConnectivityStatus.OK
                    RemoteConnectionState.CONNECTING,
                    RemoteConnectionState.DISCOVERING,
                    -> ConnectivityStatus.PENDING
                    RemoteConnectionState.ERROR -> ConnectivityStatus.ERROR
                    RemoteConnectionState.DISCONNECTED -> ConnectivityStatus.OFF
                },
                detail = when (remoteRole) {
                    RemoteRole.HOST -> when {
                        remoteClientCount > 0 -> "$remoteClientCount client(s) connected"
                        else -> "Hosting — waiting for clients"
                    }
                    RemoteRole.CLIENT -> remoteHostLabel ?: "Not connected to a host"
                    RemoteRole.OFF -> null
                },
                action = ConnectivityAction.OPEN_REMOTE,
                actionLabel = "Open",
            )
        }

        val advanced = listOfNotNull(
            usb.deviceName?.let {
                item(
                    id = "adv_usb_path",
                    group = ConnectivityGroup.USB,
                    label = "USB device path",
                    status = ConnectivityStatus.UNKNOWN,
                    detail = it,
                    advanced = true,
                )
            },
            usb.stableId?.let {
                item(
                    id = "adv_stable_id",
                    group = ConnectivityGroup.USB,
                    label = "USB stable ID",
                    status = ConnectivityStatus.UNKNOWN,
                    detail = it,
                    advanced = true,
                )
            },
            usb.probeSummary?.let {
                item(
                    id = "adv_probe_summary",
                    group = ConnectivityGroup.USB,
                    label = "USB capabilities",
                    status = ConnectivityStatus.UNKNOWN,
                    detail = it,
                    advanced = true,
                )
            },
            audio?.activityLabel?.let {
                item(
                    id = "adv_activity",
                    group = ConnectivityGroup.AUDIO,
                    label = "Session activity",
                    status = ConnectivityStatus.UNKNOWN,
                    detail = it,
                    advanced = true,
                )
            },
        )

        val mainItems = items.filter { !it.advanced }
        val sections = ConnectivityGroup.entries.mapNotNull { group ->
            val groupItems = mainItems.filter { it.group == group }
            if (groupItems.isEmpty()) return@mapNotNull null
            ConnectivitySection(
                group = group,
                title = group.title(),
                items = groupItems,
            )
        }

        return ConnectivityChecklist(
            mixerName = mixer.displayName,
            overall = health.overall,
            updatedAtMs = health.updatedAtMs,
            sections = sections,
            advancedItems = advanced,
        )
    }

    private fun item(
        id: String,
        group: ConnectivityGroup,
        label: String,
        status: ConnectivityStatus,
        detail: String? = null,
        action: ConnectivityAction? = null,
        actionLabel: String? = null,
        advanced: Boolean = false,
    ) = ConnectivityCheckItem(
        id = id,
        group = group,
        label = label,
        status = status,
        detail = detail,
        action = action,
        actionLabel = actionLabel,
        advanced = advanced,
    )

    private fun ConnectivityGroup.title(): String = when (this) {
        ConnectivityGroup.USB -> "USB"
        ConnectivityGroup.NETWORK -> "Network & OSC"
        ConnectivityGroup.BLUETOOTH -> "Bluetooth"
        ConnectivityGroup.SYNC -> "Mixer data sync"
        ConnectivityGroup.AUDIO -> "Live audio"
        ConnectivityGroup.PERMISSIONS -> "App permissions"
        ConnectivityGroup.REMOTE -> "Remote control"
    }
}
