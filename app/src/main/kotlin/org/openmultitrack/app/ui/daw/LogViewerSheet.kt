package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.util.AppLogBuffer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings = remember(context) { AppSettingsStore(context) }
    var autoPersist by remember { mutableStateOf(settings.devLogAutoPersist) }
    var hideTimestamps by remember { mutableStateOf(settings.devLogHideTimestamps) }
    val logText = remember(autoPersist, hideTimestamps) {
        AppLogBuffer.displayText(
            context = context,
            includePersisted = autoPersist,
            hideTimestamps = hideTimestamps,
        )
    }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug log") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(logText)) },
                ) {
                    Text("Copy")
                }
                TextButton(onClick = { AppLogBuffer.clearCurrentSession(context) }) {
                    Text("Clear")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = autoPersist,
                    onCheckedChange = {
                        autoPersist = it
                        settings.devLogAutoPersist = it
                        AppLogBuffer.setAutoPersist(context, it)
                    },
                )
                Text("Persist logs", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hideTimestamps,
                    onCheckedChange = {
                        hideTimestamps = it
                        settings.devLogHideTimestamps = it
                    },
                )
                Text("Hide timestamps", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                logText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 16.dp),
            )
        }
    }
}
