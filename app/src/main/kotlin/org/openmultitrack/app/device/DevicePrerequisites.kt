package org.openmultitrack.app.device

import android.bluetooth.BluetoothManager
import android.content.Context
import org.openmultitrack.app.audio.RecordAudioPermissions
import org.openmultitrack.app.scribble.Flow8BlePermissions
import org.openmultitrack.app.scribble.ScribbleImportSupport
import org.openmultitrack.domain.mixer.MixerProfile

enum class PrerequisiteKind {
    BLUETOOTH_OFF,
    BLUETOOTH_PERMISSION,
    LOCATION_PERMISSION,
    RECORD_AUDIO_PERMISSION,
}

data class PrerequisiteItem(
    val kind: PrerequisiteKind,
    val title: String,
    val message: String,
    val actionLabel: String,
)

object DevicePrerequisites {
    fun unmet(context: Context, mixers: List<MixerProfile>): List<PrerequisiteItem> {
        if (mixers.isEmpty()) return emptyList()

        val items = mutableListOf<PrerequisiteItem>()
        val needsBle = mixers.any { ScribbleImportSupport.supportsFlow8(it) }

        if (needsBle) {
            if (!Flow8BlePermissions.hasAll(context)) {
                items += PrerequisiteItem(
                    kind = PrerequisiteKind.BLUETOOTH_PERMISSION,
                    title = "Bluetooth permission needed",
                    message = "FLOW 8 channel-name sync uses Bluetooth. Allow nearby-device access for OpenMultiTrack.",
                    actionLabel = "Allow Bluetooth",
                )
            } else if (Flow8BlePermissions.needsLocationForBleScan(context)) {
                items += PrerequisiteItem(
                    kind = PrerequisiteKind.LOCATION_PERMISSION,
                    title = "Location permission needed",
                    message = "Android requires location access while scanning for FLOW 8 over Bluetooth on this OS version.",
                    actionLabel = "Allow location",
                )
            } else if (!isBluetoothEnabled(context)) {
                items += PrerequisiteItem(
                    kind = PrerequisiteKind.BLUETOOTH_OFF,
                    title = "Bluetooth is off",
                    message = "Turn on Bluetooth to sync channel names from your FLOW 8 mixer.",
                    actionLabel = "Turn on Bluetooth",
                )
            }
        }

        if (!RecordAudioPermissions.hasPermission(context)) {
            items += PrerequisiteItem(
                kind = PrerequisiteKind.RECORD_AUDIO_PERMISSION,
                title = "Microphone permission needed",
                message = "USB multitrack recording and monitoring require microphone permission on Android.",
                actionLabel = "Allow microphone",
            )
        }

        return items
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }
}
