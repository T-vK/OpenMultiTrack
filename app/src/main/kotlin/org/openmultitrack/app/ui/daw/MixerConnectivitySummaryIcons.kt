package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.health.ConnectivitySummary
import org.openmultitrack.app.health.ConnectivitySummaryIcon
import org.openmultitrack.app.health.ConnectivitySummaryKind
import org.openmultitrack.app.health.ConnectivitySummaryLevel

@Composable
fun MixerConnectivitySummaryIcons(
    summary: ConnectivitySummary,
    modifier: Modifier = Modifier,
) {
    val displayIcons = summary.icons.filter { icon ->
        when (icon.level) {
            ConnectivitySummaryLevel.OK -> icon.kind == ConnectivitySummaryKind.USB ||
                icon.kind == ConnectivitySummaryKind.OSC
            ConnectivitySummaryLevel.PENDING,
            ConnectivitySummaryLevel.WARNING,
            ConnectivitySummaryLevel.ERROR,
            -> true
        }
    }
    if (displayIcons.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayIcons.forEach { icon ->
            ConnectivitySummaryIconGlyph(icon = icon)
        }
    }
}

@Composable
private fun ConnectivitySummaryIconGlyph(icon: ConnectivitySummaryIcon) {
    val tint = when (icon.level) {
        ConnectivitySummaryLevel.OK -> MaterialTheme.colorScheme.primary
        ConnectivitySummaryLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        ConnectivitySummaryLevel.ERROR -> MaterialTheme.colorScheme.error
        ConnectivitySummaryLevel.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(contentAlignment = Alignment.Center) {
        if (icon.level == ConnectivitySummaryLevel.PENDING && icon.kind == ConnectivitySummaryKind.USB) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon.kind.toImageVector(),
                contentDescription = icon.contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            if (icon.level == ConnectivitySummaryLevel.WARNING ||
                icon.level == ConnectivitySummaryLevel.ERROR
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp),
                )
            }
        }
    }
}

private fun ConnectivitySummaryKind.toImageVector(): ImageVector = when (this) {
    ConnectivitySummaryKind.USB -> Icons.Default.Usb
    ConnectivitySummaryKind.OSC -> Icons.Outlined.GraphicEq
    ConnectivitySummaryKind.NETWORK -> Icons.Default.Wifi
    ConnectivitySummaryKind.BLUETOOTH -> Icons.Default.Bluetooth
    ConnectivitySummaryKind.BATTERY -> Icons.Default.BatteryAlert
    ConnectivitySummaryKind.MIC -> Icons.Default.Mic
    ConnectivitySummaryKind.STORAGE -> Icons.Default.SdStorage
    ConnectivitySummaryKind.NOTIFICATION -> Icons.Default.Notifications
}
