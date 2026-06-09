package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.remote.RemoteRole
import org.openmultitrack.remote.RemoteDiscoveredHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlSheet(
    role: RemoteRole,
    connectionState: RemoteConnectionState,
    hostName: String?,
    connectedHost: String?,
    localHostIp: String?,
    pairingUri: String?,
    pairingPin: String?,
    discoveredHosts: List<RemoteDiscoveredHost>,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onEnterRemoteMode: () -> Unit,
    onDisconnect: () -> Unit,
    onScanLan: () -> Unit,
    onConnectHost: (RemoteDiscoveredHost) -> Unit,
    onConnectManual: (host: String, pin: String) -> Unit,
    onScanQr: () -> Unit,
    onEnableHosting: () -> Unit,
) {
    var manualHost by remember { mutableStateOf("") }
    var manualPin by remember { mutableStateOf("") }
    val isRemoteClient = role == RemoteRole.CLIENT &&
        connectionState == RemoteConnectionState.CONNECTED

    ExpandedBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Remote control",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (isRemoteClient) {
                Text(
                    "Controlling ${hostName ?: connectedHost}",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect")
                }
            } else {
                Text(
                    "Control another OpenMultiTrack instance on the same Wi‑Fi. Pair once with QR or PIN — rediscovery works after IP changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                if (role != RemoteRole.HOST) {
                    OutlinedButton(
                        onClick = onEnableHosting,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Text("Allow remote control on this device")
                    }
                }

                if (role == RemoteRole.HOST) {
                    Text(
                        "This device is hosting on ${localHostIp ?: "…"}:8765",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    pairingPin?.let { pin ->
                        Text("Pairing PIN: $pin", style = MaterialTheme.typography.titleMedium)
                    }
                    pairingUri?.let { uri ->
                        QrCodeImage(
                            content = uri,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 12.dp),
                        )
                        Text(
                            uri,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEnterRemoteMode, modifier = Modifier.weight(1f)) {
                        Text("Use as remote")
                    }
                    OutlinedButton(onClick = onScanQr) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                    }
                }

                OutlinedButton(
                    onClick = onScanLan,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(
                        when (connectionState) {
                            RemoteConnectionState.DISCOVERING -> "Scanning LAN…"
                            else -> "Scan for paired hosts"
                        },
                    )
                }

                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }

                if (discoveredHosts.isNotEmpty()) {
                    Text(
                        "Found on LAN",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    discoveredHosts.forEach { host ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(host.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${host.host}:${host.port}" +
                                        if (host.isPaired) " · paired" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(onClick = { onConnectHost(host) }) { Text("Connect") }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Manual connect", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it },
                    label = { Text("Host IP") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = manualPin,
                    onValueChange = { manualPin = it },
                    label = { Text("PIN") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                )
                Button(
                    onClick = { onConnectManual(manualHost, manualPin) },
                    enabled = manualHost.isNotBlank() && manualPin.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
