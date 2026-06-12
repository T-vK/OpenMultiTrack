package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.app.util.DevLogLevelMask
import org.openmultitrack.app.util.LogDisplayEntry

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
    var coloredLevels by remember { mutableStateOf(settings.devLogColoredLevels) }
    var levelFilterMask by remember { mutableIntStateOf(settings.devLogLevelFilterMask) }
    var refreshTick by remember { mutableIntStateOf(AppLogBuffer.revision) }
    val logEntries = remember(refreshTick, autoPersist, levelFilterMask) {
        AppLogBuffer.collectDisplayEntries(
            context = context,
            includePersisted = autoPersist,
            levelMask = levelFilterMask,
        )
    }
    val logText = remember(logEntries, hideTimestamps, coloredLevels) {
        if (logEntries.isEmpty()) {
            "(empty)"
        } else {
            logEntries.joinToString("\n") { entry ->
                when (entry) {
                    is LogDisplayEntry.Section -> entry.text
                    is LogDisplayEntry.Line -> AppLogBuffer.formatPlainLine(
                        parsed = entry.parsed,
                        hideTimestamps = hideTimestamps,
                        coloredLevels = coloredLevels,
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            refreshTick = AppLogBuffer.revision
        }
    }

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
                TextButton(
                    onClick = {
                        AppLogBuffer.clearCurrentSession(context)
                        refreshTick = AppLogBuffer.revision
                    },
                ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = coloredLevels,
                    onCheckedChange = {
                        coloredLevels = it
                        settings.devLogColoredLevels = it
                    },
                )
                Text("Colors", style = MaterialTheme.typography.bodyMedium)
            }
            LogLevelFilterControls(
                levelFilterMask = levelFilterMask,
                onLevelFilterMaskChange = {
                    levelFilterMask = it
                    settings.devLogLevelFilterMask = it
                },
            )
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
            ) {
                LogViewerLazyList(
                    entries = logEntries,
                    hideTimestamps = hideTimestamps,
                    coloredLevels = coloredLevels,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    wordWrap = true,
                    freezeUpdates = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LogLevelFilterControls(
    levelFilterMask: Int,
    onLevelFilterMaskChange: (Int) -> Unit,
) {
    Text("Level filter", style = MaterialTheme.typography.labelMedium)
    listOf(
        Triple('D', "Debug", DevLogLevelMask.DEBUG),
        Triple('I', "Info", DevLogLevelMask.INFO),
        Triple('W', "Warn", DevLogLevelMask.WARN),
        Triple('E', "Error", DevLogLevelMask.ERROR),
    ).forEach { (level, label, bit) ->
        val checked = levelFilterMask and bit != 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = { enabled ->
                    val updated = if (enabled) levelFilterMask or bit else levelFilterMask and bit.inv()
                    onLevelFilterMaskChange(updated)
                },
                modifier = Modifier.size(20.dp),
            )
            Text("$level  $label", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
