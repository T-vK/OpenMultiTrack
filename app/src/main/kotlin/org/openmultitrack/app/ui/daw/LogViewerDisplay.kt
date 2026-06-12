package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.openmultitrack.app.util.LogDisplayEntry

@Composable
private fun logLevelColor(level: Char): Color {
    val scheme = MaterialTheme.colorScheme
    return when (level.uppercaseChar()) {
        'D' -> Color(0xFF9E9E9E)
        'I' -> Color(0xFF66BB6A)
        'W' -> Color(0xFFFFA726)
        'E' -> scheme.error
        else -> scheme.onSurface
    }
}

@Composable
fun rememberLogDisplayText(
    entries: List<LogDisplayEntry>,
    hideTimestamps: Boolean,
    coloredLevels: Boolean,
): AnnotatedString {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val sectionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val debugColor = logLevelColor('D')
    val infoColor = logLevelColor('I')
    val warnColor = logLevelColor('W')
    val errorColor = logLevelColor('E')

    return remember(
        entries,
        hideTimestamps,
        coloredLevels,
        defaultColor,
        sectionColor,
        debugColor,
        infoColor,
        warnColor,
        errorColor,
    ) {
        buildLogAnnotatedString(
            entries = entries,
            hideTimestamps = hideTimestamps,
            coloredLevels = coloredLevels,
            defaultColor = defaultColor,
            sectionColor = sectionColor,
            debugColor = debugColor,
            infoColor = infoColor,
            warnColor = warnColor,
            errorColor = errorColor,
        )
    }
}

@Composable
fun LogViewerLazyList(
    entries: List<LogDisplayEntry>,
    hideTimestamps: Boolean,
    coloredLevels: Boolean,
    textStyle: TextStyle,
    wordWrap: Boolean,
    freezeUpdates: Boolean,
    modifier: Modifier = Modifier,
) {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val sectionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val debugColor = logLevelColor('D')
    val infoColor = logLevelColor('I')
    val warnColor = logLevelColor('W')
    val errorColor = logLevelColor('E')
    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisible.index >= info.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + 24
        }
    }
    var stickToBottom by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    stickToBottom = atBottom
                }
            }
    }

    LaunchedEffect(entries.size, freezeUpdates, stickToBottom) {
        if (!freezeUpdates && stickToBottom && entries.isNotEmpty()) {
            listState.scrollToItem(entries.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(
            items = entries,
            key = { index, entry -> logEntryKey(index, entry) },
        ) { _, entry ->
            val line = remember(
                entry,
                hideTimestamps,
                coloredLevels,
                defaultColor,
                sectionColor,
                debugColor,
                infoColor,
                warnColor,
                errorColor,
            ) {
                buildLogAnnotatedString(
                    entries = listOf(entry),
                    hideTimestamps = hideTimestamps,
                    coloredLevels = coloredLevels,
                    defaultColor = defaultColor,
                    sectionColor = sectionColor,
                    debugColor = debugColor,
                    infoColor = infoColor,
                    warnColor = warnColor,
                    errorColor = errorColor,
                )
            }
            Text(
                text = line,
                style = textStyle,
                softWrap = wordWrap,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun logEntryKey(index: Int, entry: LogDisplayEntry): String = when (entry) {
    is LogDisplayEntry.Section -> "s:$index:${entry.text.hashCode()}"
    is LogDisplayEntry.Line -> "l:$index:${entry.parsed.timestamp}:${entry.parsed.tag}:${entry.parsed.message.hashCode()}"
}

private fun buildLogAnnotatedString(
    entries: List<LogDisplayEntry>,
    hideTimestamps: Boolean,
    coloredLevels: Boolean,
    defaultColor: Color,
    sectionColor: Color,
    debugColor: Color,
    infoColor: Color,
    warnColor: Color,
    errorColor: Color,
): AnnotatedString {
    fun levelColor(level: Char): Color = when (level.uppercaseChar()) {
        'D' -> debugColor
        'I' -> infoColor
        'W' -> warnColor
        'E' -> errorColor
        else -> defaultColor
    }

    return buildAnnotatedString {
        entries.forEachIndexed { index, entry ->
            if (index > 0) append('\n')
            when (entry) {
                is LogDisplayEntry.Section -> {
                    withStyle(SpanStyle(color = sectionColor)) {
                        append(entry.text)
                    }
                }
                is LogDisplayEntry.Line -> {
                    val parsed = entry.parsed
                    val lineColor = if (coloredLevels) levelColor(parsed.level) else defaultColor
                    if (!hideTimestamps) {
                        withStyle(SpanStyle(color = sectionColor)) {
                            append(parsed.timestamp)
                            append(' ')
                        }
                    }
                    withStyle(SpanStyle(color = lineColor)) {
                        if (coloredLevels) {
                            append(parsed.tag)
                            append(": ")
                            append(parsed.message)
                        } else {
                            append(parsed.level)
                            append('/')
                            append(parsed.tag)
                            append(": ")
                            append(parsed.message)
                        }
                    }
                }
            }
        }
    }
}
