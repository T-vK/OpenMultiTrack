package org.openmultitrack.app.ui.daw

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.app.util.LogCustomFilter
import org.openmultitrack.app.util.LogDisplayEntry

private const val LOG_REFRESH_INTERVAL_MS = 250L
private const val LOG_TAG_DISCOVERY_INTERVAL_MS = 5_000L

@Immutable
data class LogViewerDisplaySnapshot(
    val entries: List<LogDisplayEntry> = emptyList(),
    val allTags: List<String> = emptyList(),
    val searchMatches: List<Int> = emptyList(),
    val revision: Int = 0,
)

@Stable
class LogViewerDisplayController {
    var snapshot by mutableStateOf(LogViewerDisplaySnapshot())
        private set

    internal fun publish(snapshot: LogViewerDisplaySnapshot) {
        this.snapshot = snapshot
    }
}

@Composable
fun rememberLogViewerDisplayController(
    context: Context,
    autoPersist: Boolean,
    levelFilterMask: Int,
    disabledTags: Set<String>,
    customFilters: List<LogCustomFilter>,
    maxDisplayLines: Int,
    searchQuery: String,
    freezeUpdates: Boolean,
): LogViewerDisplayController {
    val controller = remember { LogViewerDisplayController() }
    val freezeUpdatesState by rememberUpdatedState(freezeUpdates)
    val searchQueryState by rememberUpdatedState(searchQuery)
    val filterKey = remember(
        autoPersist,
        levelFilterMask,
        disabledTags,
        customFilters,
        maxDisplayLines,
    ) {
        LogViewerFilterKey(
            autoPersist = autoPersist,
            levelFilterMask = levelFilterMask,
            disabledTags = disabledTags,
            customFilters = customFilters,
            maxDisplayLines = maxDisplayLines,
        )
    }

    androidx.compose.runtime.LaunchedEffect(filterKey) {
        controller.publish(
            buildLogViewerSnapshot(
                context = context,
                filterKey = filterKey,
                searchQuery = searchQueryState,
            ),
        )
    }

    androidx.compose.runtime.LaunchedEffect(searchQueryState, controller.snapshot.entries) {
        val matches = withContext(Dispatchers.Default) {
            logSearchMatchIndices(controller.snapshot.entries, searchQueryState)
        }
        if (matches != controller.snapshot.searchMatches) {
            controller.publish(controller.snapshot.copy(searchMatches = matches))
        }
    }

    androidx.compose.runtime.LaunchedEffect(filterKey, freezeUpdatesState) {
        var lastRenderedRevision = -1
        var lastTagDiscoveryMs = 0L
        var cachedTags = controller.snapshot.allTags
        while (isActive) {
            delay(LOG_REFRESH_INTERVAL_MS)
            if (freezeUpdatesState) continue
            val revision = AppLogBuffer.revision
            if (revision == lastRenderedRevision) continue
            lastRenderedRevision = revision
            val nowMs = System.currentTimeMillis()
            val refreshTags = cachedTags.isEmpty() ||
                nowMs - lastTagDiscoveryMs >= LOG_TAG_DISCOVERY_INTERVAL_MS
            if (refreshTags) {
                lastTagDiscoveryMs = nowMs
            }
            val snapshot = withContext(Dispatchers.Default) {
                val entries = AppLogBuffer.collectDisplayEntries(
                    context = context,
                    includePersisted = filterKey.autoPersist,
                    levelMask = filterKey.levelFilterMask,
                    disabledTags = filterKey.disabledTags,
                    customFilters = filterKey.customFilters,
                    maxDisplayLines = filterKey.maxDisplayLines,
                )
                val tags = if (refreshTags) {
                    AppLogBuffer.discoverTags(context, filterKey.autoPersist)
                } else {
                    cachedTags
                }
                val matches = logSearchMatchIndices(entries, searchQueryState)
                LogViewerDisplaySnapshot(
                    entries = entries,
                    allTags = tags,
                    searchMatches = matches,
                    revision = revision,
                )
            }
            cachedTags = snapshot.allTags
            controller.publish(snapshot)
        }
    }

    return controller
}

fun buildLogViewerPlainText(
    entries: List<LogDisplayEntry>,
    hideTimestamps: Boolean,
    coloredLevels: Boolean,
): String {
    if (entries.isEmpty()) return "(empty)"
    return entries.joinToString("\n") { entry ->
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

private data class LogViewerFilterKey(
    val autoPersist: Boolean,
    val levelFilterMask: Int,
    val disabledTags: Set<String>,
    val customFilters: List<LogCustomFilter>,
    val maxDisplayLines: Int,
)

private suspend fun buildLogViewerSnapshot(
    context: Context,
    filterKey: LogViewerFilterKey,
    searchQuery: String,
): LogViewerDisplaySnapshot = withContext(Dispatchers.Default) {
    val entries = AppLogBuffer.collectDisplayEntries(
        context = context,
        includePersisted = filterKey.autoPersist,
        levelMask = filterKey.levelFilterMask,
        disabledTags = filterKey.disabledTags,
        customFilters = filterKey.customFilters,
        maxDisplayLines = filterKey.maxDisplayLines,
    )
    val tags = AppLogBuffer.discoverTags(context, filterKey.autoPersist)
    val matches = logSearchMatchIndices(entries, searchQuery)
    LogViewerDisplaySnapshot(
        entries = entries,
        allTags = tags,
        searchMatches = matches,
        revision = AppLogBuffer.revision,
    )
}
