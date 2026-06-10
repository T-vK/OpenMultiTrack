package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(20.dp)
private val ContentMaxWidth = 880.dp

@Composable
fun WelcomeEmptyState(
    pairedHostName: String?,
    onAddMixer: () -> Unit,
    onScanRemoteQr: () -> Unit,
    onReconnectRemote: () -> Unit,
    onOpenRemoteControl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val useSideBySide = maxWidth >= 640.dp
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WelcomeHero()
            Spacer(Modifier.height(28.dp))

            if (pairedHostName != null) {
                PairedHostBanner(
                    hostName = pairedHostName,
                    onReconnect = onReconnectRemote,
                )
                Spacer(Modifier.height(20.dp))
            }

            if (useSideBySide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LocalMixerPathCard(
                        modifier = Modifier.weight(1f),
                        onAddMixer = onAddMixer,
                    )
                    RemoteControlPathCard(
                        modifier = Modifier.weight(1f),
                        showReconnect = pairedHostName != null,
                        onScanRemoteQr = onScanRemoteQr,
                        onReconnectRemote = onReconnectRemote,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LocalMixerPathCard(onAddMixer = onAddMixer)
                    RemoteControlPathCard(
                        showReconnect = pairedHostName != null,
                        onScanRemoteQr = onScanRemoteQr,
                        onReconnectRemote = onReconnectRemote,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onOpenRemoteControl) {
                Text("Remote control settings")
            }
        }
    }
}

@Composable
private fun WelcomeHero() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Get started",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connect a USB interface on this device, or control a host remotely on your network.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun PairedHostBanner(
    hostName: String,
    onReconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Paired with $hostName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Reconnect without scanning the QR code again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onReconnect) {
                Text("Reconnect")
            }
        }
    }
}

@Composable
private fun LocalMixerPathCard(
    onAddMixer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WelcomePathCard(
        modifier = modifier,
        icon = Icons.Default.Usb,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconTint = MaterialTheme.colorScheme.primary,
        title = "USB audio interface",
        description = "Record and mix with a mixer or multichannel interface plugged into this device.",
        primaryLabel = "Add device",
        onPrimaryClick = onAddMixer,
    )
}

@Composable
private fun RemoteControlPathCard(
    showReconnect: Boolean,
    onScanRemoteQr: () -> Unit,
    onReconnectRemote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WelcomePathCard(
        modifier = modifier,
        icon = Icons.Default.Cast,
        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
        iconTint = MaterialTheme.colorScheme.tertiary,
        title = "Remote control",
        description = "Use this tablet or phone as a wireless controller for OpenMultiTrack on another device.",
        primaryLabel = "Scan host QR",
        primaryIcon = Icons.Default.QrCodeScanner,
        onPrimaryClick = onScanRemoteQr,
        secondaryLabel = if (showReconnect) "Reconnect" else null,
        onSecondaryClick = if (showReconnect) onReconnectRemote else null,
    )
}

@Composable
private fun WelcomePathCard(
    icon: ImageVector,
    iconContainerColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    description: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryIcon: ImageVector? = null,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor.copy(alpha = 0.7f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = iconTint,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (secondaryLabel != null && onSecondaryClick != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPrimaryClick,
                        modifier = Modifier.weight(1f),
                    ) {
                        PathCardButtonLabel(primaryLabel, primaryIcon)
                    }
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(secondaryLabel)
                    }
                }
            } else {
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PathCardButtonLabel(primaryLabel, primaryIcon)
                }
            }
        }
    }
}

@Composable
private fun PathCardButtonLabel(label: String, icon: ImageVector?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(label)
    }
}
