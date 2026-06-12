package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var wordWrap by remember { mutableStateOf(settings.devLogWordWrap) }
    var disabledTags by remember { mutableStateOf(settings.devLogDisabledTags) }
    var customFilters by remember { mutableStateOf(settings.devLogCustomFilters) }
    var refreshTick by remember { mutableIntStateOf(AppLogBuffer.revision) }

    LaunchedEffect(autoPersist) {
        settings.devLogAutoPersist = autoPersist
        AppLogBuffer.setAutoPersist(context, autoPersist)
    }
    LaunchedEffect(disabledTags) {
        settings.devLogDisabledTags = disabledTags
    }
    LaunchedEffect(customFilters) {
        settings.devLogCustomFilters = customFilters
    }

    val allTags = remember(refreshTick, autoPersist) {
        AppLogBuffer.discoverTags(context, autoPersist)
    }
    val logEntries = remember(refreshTick, autoPersist, levelFilterMask, disabledTags, customFilters) {
        AppLogBuffer.collectDisplayEntries(
            context = context,
            includePersisted = autoPersist,
            levelMask = levelFilterMask,
            disabledTags = disabledTags,
            customFilters = customFilters,
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
    val visibleLogLineCount = remember(logEntries) {
        logEntries.count { it is LogDisplayEntry.Line }
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
                .padding(padding),
        ) {
            LogViewerMenuBar(
                visibleLineCount = visibleLogLineCount,
                totalLineCount = AppLogBuffer.lineCount(),
                filterActive = levelFilterMask != DevLogLevelMask.ALL ||
                    disabledTags.isNotEmpty() ||
                    customFilters.any { it.enabled },
                autoPersist = autoPersist,
                hideTimestamps = hideTimestamps,
                coloredLevels = coloredLevels,
                wordWrap = wordWrap,
                levelFilterMask = levelFilterMask,
                disabledTags = disabledTags,
                allTags = allTags,
                customFilters = customFilters,
                onCopy = { clipboard.setText(AnnotatedString(logText)) },
                onClear = {
                    AppLogBuffer.clearCurrentSession(context)
                    refreshTick = AppLogBuffer.revision
                },
                onAutoPersistChange = { autoPersist = it },
                onHideTimestampsChange = {
                    hideTimestamps = it
                    settings.devLogHideTimestamps = it
                },
                onColoredLevelsChange = {
                    coloredLevels = it
                    settings.devLogColoredLevels = it
                },
                onWordWrapChange = {
                    wordWrap = it
                    settings.devLogWordWrap = it
                },
                onLevelFilterMaskChange = {
                    levelFilterMask = it
                    settings.devLogLevelFilterMask = it
                },
                onDisabledTagsChange = { disabledTags = it },
                onCustomFiltersChange = { customFilters = it },
            )
            HorizontalDivider()
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                LogViewerLazyList(
                    entries = logEntries,
                    hideTimestamps = hideTimestamps,
                    coloredLevels = coloredLevels,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    wordWrap = wordWrap,
                    freezeUpdates = false,
                    revision = refreshTick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
