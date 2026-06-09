package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
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
import org.openmultitrack.domain.remote.RemotePairedHost
import org.openmultitrack.domain.remote.RemoteRole
import org.openmultitrack.remote.RemoteDiscoveredHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    role: RemoteRole,
    connectionState: RemoteConnectionState,
    hostName: String?,
    connectedHost: String?,
    localHostIp: String?,
    pairingUri: String?,
    pairingPin: String?,
    discoveredHosts: List<RemoteDiscoveredHost>,
    pairedHosts: List<RemotePairedHost> = emptyList(),
    errorMessage: String?,
    onDismiss: () -> Unit,
    onEnterRemoteMode: () -> Unit,
    onDisconnect: () -> Unit,
    onScanLan: () -> Unit,
    onConnectHost: (RemoteDiscoveredHost) -> Unit,
    onConnectManual: (host: String, pin: String) -> Unit,
    onScanQr: () -> Unit,
    onEnableHosting: () -> Unit,
    onStopHosting: () -> Unit = {},
    onUnpairHost: (String) -> Unit = {},
    connectedClientCount: Int = 0,
) {
    var manualHost by remember { mutableStateOf("") }
    var manualPin by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    val isRemoteClient = role == RemoteRole.CLIENT &&
        connectionState == RemoteConnectionState.CONNECTED
    val isRemoteHost = role == RemoteRole.HOST
    val isConnecting = role == RemoteRole.CLIENT &&
        (connectionState == RemoteConnectionState.CONNECTING ||
            connectionState == RemoteConnectionState.DISCOVERING)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote control") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (isRemoteClient) {
                Text(
                    "REMOTE MODE — controlling ${hostName ?: connectedHost}",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Transport, mixers, and soundcheck are mirrored from the host device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Exit remote mode")
                }
            } else {
                Text(
                    "Control another OpenMultiTrack on the same Wi‑Fi. Scan the host QR code once — it connects automatically and remembers the host.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                if (isConnecting) {
                    Text(
                        when (connectionState) {
                            RemoteConnectionState.DISCOVERING -> "Looking for paired host…"
                            else -> "Connecting to ${hostName ?: connectedHost ?: "host"}…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (role != RemoteRole.HOST) {
                    OutlinedButton(
                        onClick = onEnableHosting,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Text("Allow remote control on this device")
                    }
                }

                if (isRemoteHost) {
                    Text(
                        "HOST MODE — accepting remotes on ${localHostIp ?: "…"}:8765",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (connectedClientCount > 0) {
                        Text(
                            "$connectedClientCount remote device(s) connected",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
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
                            "Scan this QR on the remote device — no IP or PIN entry needed.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onStopHosting,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Stop hosting")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onScanQr, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Text("Scan host QR", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onEnterRemoteMode) {
                        Text("Reconnect")
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

                if (pairedHosts.isNotEmpty()) {
                    Text(
                        "Paired hosts",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    pairedHosts.forEach { paired ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(paired.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    paired.lastHost?.let { "$it:${paired.lastPort ?: 8765}" }
                                        ?: "Address saved after next connect",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { onUnpairHost(paired.hostId) }) {
                                Text("Unpair")
                            }
                        }
                    }
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

                OutlinedButton(
                    onClick = { showAdvanced = !showAdvanced },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(if (showAdvanced) "Hide advanced" else "Advanced (manual IP + PIN)")
                }
                if (showAdvanced) {
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
                        Text("Connect manually")
                    }
                }
            }
        }
    }
}
