package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.openmultitrack.app.util.LogDisplayEntry

private val clearlyAwayFromBottomThreshold = 72.dp
private val nearBottomThreshold = 120.dp
private const val programmaticScrollGraceNs = 150_000_000L

@Composable
private fun logLevelColorComposable(level: Char): Color {
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
    val debugColor = logLevelColorComposable('D')
    val infoColor = logLevelColorComposable('I')
    val warnColor = logLevelColorComposable('W')
    val errorColor = logLevelColorComposable('E')

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
    scrollToIndex: Int? = null,
) {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val sectionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val debugColor = logLevelColorComposable('D')
    val infoColor = logLevelColorComposable('I')
    val warnColor = logLevelColorComposable('W')
    val errorColor = logLevelColorComposable('E')
    val listState = rememberLazyListState()
    var stickToBottom by remember { mutableStateOf(true) }
    var ignoreUserScrollUntilNs by remember { mutableLongStateOf(0L) }
    var lastScrolledEntryCount by remember { mutableIntStateOf(0) }
    val freezeUpdatesState by rememberUpdatedState(freezeUpdates)
    val entriesState by rememberUpdatedState(entries)
    val density = LocalDensity.current
    val awayThresholdPx = with(density) { clearlyAwayFromBottomThreshold.roundToPx() }
    val nearBottomThresholdPx = with(density) { nearBottomThreshold.roundToPx() }

    suspend fun scrollToTailIfNeeded() {
        val current = entriesState
        if (current.isEmpty()) return
        ignoreUserScrollUntilNs = System.nanoTime() + programmaticScrollGraceNs
        listState.scrollToItem(current.lastIndex, scrollOffset = Int.MAX_VALUE)
    }

    LaunchedEffect(listState, awayThresholdPx, nearBottomThresholdPx) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (wasScrolling && !scrolling) {
                    if (System.nanoTime() < ignoreUserScrollUntilNs) {
                        wasScrolling = scrolling
                        return@collect
                    }
                    when {
                        isClearlyAwayFromBottom(listState, awayThresholdPx) -> {
                            stickToBottom = false
                        }
                        isNearBottom(listState, nearBottomThresholdPx) -> {
                            stickToBottom = true
                        }
                    }
                }
                wasScrolling = scrolling
            }
    }

    LaunchedEffect(entries.size, stickToBottom, freezeUpdatesState) {
        if (freezeUpdatesState || !stickToBottom) {
            lastScrolledEntryCount = entries.size
            return@LaunchedEffect
        }
        if (entries.size > lastScrolledEntryCount) {
            scrollToTailIfNeeded()
        }
        lastScrolledEntryCount = entries.size
    }

    LaunchedEffect(scrollToIndex) {
        val target = scrollToIndex ?: return@LaunchedEffect
        if (target in entries.indices) {
            ignoreUserScrollUntilNs = System.nanoTime() + programmaticScrollGraceNs
            listState.animateScrollToItem(target)
        }
    }

    LaunchedEffect(listState, stickToBottom, freezeUpdatesState, nearBottomThresholdPx) {
        snapshotFlow {
            val info = listState.layoutInfo
            Triple(info.viewportStartOffset, info.viewportEndOffset, info.totalItemsCount)
        }
            .distinctUntilChanged()
            .collect {
                if (
                    !freezeUpdatesState &&
                    stickToBottom &&
                    !isNearBottom(listState, nearBottomThresholdPx)
                ) {
                    scrollToTailIfNeeded()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(
            items = entries,
            key = { _, entry -> logEntryStableKey(entry) },
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

private fun isClearlyAwayFromBottom(state: LazyListState, awayThresholdPx: Int): Boolean {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return false
    val lastIndex = total - 1
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return false
    if (lastVisible.index < lastIndex - 1) return true
    if (lastVisible.index < lastIndex) return true
    val gapBelow = info.viewportEndOffset - (lastVisible.offset + lastVisible.size)
    return gapBelow > awayThresholdPx
}

private fun isNearBottom(state: LazyListState, nearThresholdPx: Int): Boolean {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return true
    val lastIndex = total - 1
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return true
    if (lastVisible.index < lastIndex) return false
    val gapBelow = info.viewportEndOffset - (lastVisible.offset + lastVisible.size)
    return gapBelow <= nearThresholdPx
}

private fun logEntryStableKey(entry: LogDisplayEntry): String = when (entry) {
    is LogDisplayEntry.Section -> "s:${entry.text}"
    is LogDisplayEntry.Line -> {
        val parsed = entry.parsed
        "l:${parsed.timestamp}:${parsed.level}:${parsed.tag}:${parsed.message}"
    }
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
