package org.openmultitrack.app.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.openmultitrack.app.audio.RecordAudioPermissions
import org.openmultitrack.app.scribble.Flow8BlePermissions
import org.openmultitrack.app.device.DevicePrerequisites
import org.openmultitrack.app.device.NetworkSnapshot
import org.openmultitrack.domain.mixer.MixerHealthSnapshot
import org.openmultitrack.domain.mixer.ProbeState

enum class ConnectivitySummaryLevel {
    OK,
    WARNING,
    ERROR,
    PENDING,
}

enum class ConnectivitySummaryKind {
    USB,
    OSC,
    NETWORK,
    BLUETOOTH,
    BATTERY,
    MIC,
    STORAGE,
    NOTIFICATION,
}

data class ConnectivitySummaryIcon(
    val kind: ConnectivitySummaryKind,
    val level: ConnectivitySummaryLevel,
    val contentDescription: String,
)

data class ConnectivitySummary(
    val icons: List<ConnectivitySummaryIcon>,
)

object ConnectivitySummaryBuilder {
    fun build(
        health: MixerHealthSnapshot,
        network: NetworkSnapshot,
        supportsOsc: Boolean,
        supportsFlow8Ble: Boolean,
        storageWritable: Boolean,
        batteryOptimizationIgnored: Boolean,
        context: Context,
    ): ConnectivitySummary {
        val icons = mutableListOf<ConnectivitySummaryIcon>()
        val usb = health.usb
        val osc = health.osc

        val usbLevel = when {
            !usb.attached || !usb.permissionGranted -> ConnectivitySummaryLevel.ERROR
            usb.probeState == ProbeState.PROBING -> ConnectivitySummaryLevel.PENDING
            usb.probeState == ProbeState.FAILED -> ConnectivitySummaryLevel.ERROR
            usb.probeState == ProbeState.OK -> ConnectivitySummaryLevel.OK
            else -> ConnectivitySummaryLevel.WARNING
        }
        icons += ConnectivitySummaryIcon(
            kind = ConnectivitySummaryKind.USB,
            level = usbLevel,
            contentDescription = when (usbLevel) {
                ConnectivitySummaryLevel.OK -> "USB audio ready"
                ConnectivitySummaryLevel.PENDING -> "Detecting USB audio"
                ConnectivitySummaryLevel.ERROR -> when {
                    !usb.attached -> "USB mixer not connected"
                    !usb.permissionGranted -> "USB permission required"
                    else -> "USB audio probe failed"
                }
                ConnectivitySummaryLevel.WARNING -> "USB audio not probed yet"
            },
        )

        if (supportsOsc) {
            val oscConfigured = osc?.configured == true
            val oscLevel = when {
                !network.hasLanTransport -> ConnectivitySummaryLevel.WARNING
                !oscConfigured -> ConnectivitySummaryLevel.WARNING
                else -> ConnectivitySummaryLevel.OK
            }
            icons += ConnectivitySummaryIcon(
                kind = ConnectivitySummaryKind.OSC,
                level = oscLevel,
                contentDescription = when {
                    !network.hasLanTransport -> "No Wi‑Fi or LAN — OSC unavailable"
                    !oscConfigured -> "Mixer OSC IP not configured"
                    else -> "OSC configured"
                },
            )
        }

        if (!network.hasLanTransport) {
            icons += ConnectivitySummaryIcon(
                kind = ConnectivitySummaryKind.NETWORK,
                level = ConnectivitySummaryLevel.WARNING,
                contentDescription = "No Wi‑Fi or LAN connection",
            )
        }

        if (supportsFlow8Ble) {
            val btPerm = Flow8BlePermissions.hasAll(context)
            val btOn = DevicePrerequisites.isBluetoothEnabled(context)
            val locNeeded = Flow8BlePermissions.needsLocationForBleScan(context)
            val locGranted = !locNeeded || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val btOk = btPerm && btOn && locGranted
            if (!btOk) {
                icons += ConnectivitySummaryIcon(
                    kind = ConnectivitySummaryKind.BLUETOOTH,
                    level = ConnectivitySummaryLevel.ERROR,
                    contentDescription = when {
                        !btPerm -> "Bluetooth permission required for FLOW 8 labels"
                        !btOn -> "Bluetooth is off"
                        else -> "Location permission required for BLE scan"
                    },
                )
            }
        }

        if (!batteryOptimizationIgnored) {
            icons += ConnectivitySummaryIcon(
                kind = ConnectivitySummaryKind.BATTERY,
                level = ConnectivitySummaryLevel.WARNING,
                contentDescription = "Battery optimization may interrupt recording",
            )
        }

        if (!RecordAudioPermissions.hasPermission(context)) {
            icons += ConnectivitySummaryIcon(
                kind = ConnectivitySummaryKind.MIC,
                level = ConnectivitySummaryLevel.ERROR,
                contentDescription = "Microphone permission required",
            )
        }

        if (!storageWritable) {
            icons += ConnectivitySummaryIcon(
                kind = ConnectivitySummaryKind.STORAGE,
                level = ConnectivitySummaryLevel.ERROR,
                contentDescription = "Recording storage not writable",
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifOk = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifOk) {
                icons += ConnectivitySummaryIcon(
                    kind = ConnectivitySummaryKind.NOTIFICATION,
                    level = ConnectivitySummaryLevel.WARNING,
                    contentDescription = "Notification permission recommended",
                )
            }
        }

        return ConnectivitySummary(icons = icons)
    }
}
