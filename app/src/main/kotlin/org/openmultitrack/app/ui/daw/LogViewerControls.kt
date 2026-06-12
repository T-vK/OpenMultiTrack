package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.app.util.DevLogLevelMask
import org.openmultitrack.app.util.LogCustomFilter
import org.openmultitrack.app.util.LogCustomFilterMode

private val barHeight = 40.dp
private val chipHeight = 28.dp
private val chipIconSize = 16.dp
private val chipWithLabelWidth = 76.dp
private val chipIconOnlyWidth = 34.dp
private val searchExpandedMinWidth = 140.dp

private val maxLineOptions = listOf(200, 400, 600, 1000, 1_500)

@Composable
fun LogViewerMenuBar(
    autoPersist: Boolean,
    hideTimestamps: Boolean,
    coloredLevels: Boolean,
    wordWrap: Boolean,
    levelFilterMask: Int,
    disabledTags: Set<String>,
    allTags: List<String>,
    customFilters: List<LogCustomFilter>,
    maxDisplayLines: Int,
    searchQuery: String,
    searchMatchIndex: Int,
    searchMatchCount: Int,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onAutoPersistChange: (Boolean) -> Unit,
    onHideTimestampsChange: (Boolean) -> Unit,
    onColoredLevelsChange: (Boolean) -> Unit,
    onWordWrapChange: (Boolean) -> Unit,
    onLevelFilterMaskChange: (Int) -> Unit,
    onDisabledTagsChange: (Set<String>) -> Unit,
    onCustomFiltersChange: (List<LogCustomFilter>) -> Unit,
    onMaxDisplayLinesChange: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchNext: () -> Unit,
    onSearchPrevious: () -> Unit,
) {
    var levelMenuOpen by remember { mutableStateOf(false) }
    var tagDialogOpen by remember { mutableStateOf(false) }
    var regexDialogOpen by remember { mutableStateOf(false) }
    var maxLinesMenuOpen by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(searchQuery.isNotBlank()) }

    val levelFilterActive = levelFilterMask != DevLogLevelMask.ALL
    val tagFilterActive = disabledTags.isNotEmpty()
    val regexFilterActive = customFilters.any { it.enabled }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
        ) {
            val toolbarItems = remember(
                maxWidth,
                searchExpanded,
                searchQuery,
                autoPersist,
                hideTimestamps,
                coloredLevels,
                wordWrap,
                levelFilterActive,
                tagFilterActive,
                regexFilterActive,
                maxDisplayLines,
            ) {
                buildToolbarItems(
                    searchExpanded = searchExpanded,
                    searchQuery = searchQuery,
                    autoPersist = autoPersist,
                    hideTimestamps = hideTimestamps,
                    coloredLevels = coloredLevels,
                    wordWrap = wordWrap,
                    levelFilterActive = levelFilterActive,
                    tagFilterActive = tagFilterActive,
                    regexFilterActive = regexFilterActive,
                    maxDisplayLines = maxDisplayLines,
                )
            }
            val labelBudget = (maxWidth - 12.dp) / chipWithLabelWidth
            val labelsToHide = (toolbarItems.size - labelBudget.toInt()).coerceAtLeast(0)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                toolbarItems.forEachIndexed { index, item ->
                    val showLabel = index < toolbarItems.size - labelsToHide
                    when (item) {
                        ToolbarItem.Copy -> LogToolbarChip(
                            label = "Copy",
                            icon = Icons.Default.ContentCopy,
                            selected = false,
                            showLabel = showLabel,
                            onClick = onCopy,
                        )
                        ToolbarItem.Clear -> LogToolbarChip(
                            label = "Clear",
                            icon = Icons.Default.Clear,
                            selected = false,
                            showLabel = showLabel,
                            onClick = onClear,
                        )
                        ToolbarItem.Divider -> MenuBarDivider()
                        ToolbarItem.Persist -> LogToolbarChip(
                            label = "Save",
                            icon = Icons.Default.Save,
                            selected = autoPersist,
                            showLabel = showLabel,
                            onClick = { onAutoPersistChange(!autoPersist) },
                        )
                        ToolbarItem.Time -> LogToolbarChip(
                            label = "Time",
                            icon = Icons.Default.Schedule,
                            selected = !hideTimestamps,
                            showLabel = showLabel,
                            onClick = { onHideTimestampsChange(!hideTimestamps) },
                        )
                        ToolbarItem.Color -> LogToolbarChip(
                            label = "Color",
                            icon = Icons.Default.Palette,
                            selected = coloredLevels,
                            showLabel = showLabel,
                            onClick = { onColoredLevelsChange(!coloredLevels) },
                        )
                        ToolbarItem.Wrap -> LogToolbarChip(
                            label = "Wrap",
                            icon = Icons.Default.WrapText,
                            selected = wordWrap,
                            showLabel = showLabel,
                            onClick = { onWordWrapChange(!wordWrap) },
                        )
                        ToolbarItem.Levels -> Box {
                            LogToolbarChip(
                                label = "Levels",
                                icon = Icons.Default.FilterList,
                                selected = levelFilterActive,
                                showLabel = showLabel,
                                showDropdownArrow = true,
                                onClick = { levelMenuOpen = true },
                            )
                            DropdownMenu(
                                expanded = levelMenuOpen,
                                onDismissRequest = { levelMenuOpen = false },
                            ) {
                                LogLevelMenuItem('D', "Debug", DevLogLevelMask.DEBUG, levelFilterMask, onLevelFilterMaskChange)
                                LogLevelMenuItem('I', "Info", DevLogLevelMask.INFO, levelFilterMask, onLevelFilterMaskChange)
                                LogLevelMenuItem('W', "Warn", DevLogLevelMask.WARN, levelFilterMask, onLevelFilterMaskChange)
                                LogLevelMenuItem('E', "Error", DevLogLevelMask.ERROR, levelFilterMask, onLevelFilterMaskChange)
                            }
                        }
                        ToolbarItem.Tags -> LogToolbarChip(
                            label = "Tags",
                            icon = Icons.Default.Label,
                            selected = tagFilterActive,
                            showLabel = showLabel,
                            showDropdownArrow = true,
                            onClick = { tagDialogOpen = true },
                        )
                        ToolbarItem.Regex -> LogToolbarChip(
                            label = "Regex",
                            icon = Icons.Default.FilterList,
                            selected = regexFilterActive,
                            showLabel = showLabel,
                            showDropdownArrow = true,
                            onClick = { regexDialogOpen = true },
                        )
                        ToolbarItem.Search -> {
                            if (searchExpanded) {
                                LogSearchBar(
                                    query = searchQuery,
                                    matchIndex = searchMatchIndex,
                                    matchCount = searchMatchCount,
                                    showLabel = showLabel,
                                    onQueryChange = {
                                        onSearchQueryChange(it)
                                        if (it.isBlank()) searchExpanded = false
                                    },
                                    onCollapse = {
                                        searchExpanded = false
                                        onSearchQueryChange("")
                                    },
                                    onNext = onSearchNext,
                                    onPrevious = onSearchPrevious,
                                )
                            } else {
                                LogToolbarChip(
                                    label = "Search",
                                    icon = Icons.Default.Search,
                                    selected = searchQuery.isNotBlank(),
                                    showLabel = showLabel,
                                    onClick = { searchExpanded = true },
                                )
                            }
                        }
                        ToolbarItem.MaxLines -> Box {
                            LogToolbarChip(
                                label = maxDisplayLines.toString(),
                                icon = Icons.Default.TableRows,
                                selected = maxDisplayLines != AppLogBuffer.MAX_DISPLAY_LINES,
                                showLabel = showLabel,
                                showDropdownArrow = true,
                                onClick = { maxLinesMenuOpen = true },
                            )
                            DropdownMenu(
                                expanded = maxLinesMenuOpen,
                                onDismissRequest = { maxLinesMenuOpen = false },
                            ) {
                                maxLineOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("$option lines") },
                                        leadingIcon = if (option == maxDisplayLines) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            onMaxDisplayLinesChange(option)
                                            maxLinesMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (tagDialogOpen) {
        LogTagFilterDialog(
            allTags = allTags,
            disabledTags = disabledTags,
            onDisabledTagsChange = onDisabledTagsChange,
            onDismiss = { tagDialogOpen = false },
        )
    }
    if (regexDialogOpen) {
        LogCustomFilterDialog(
            filters = customFilters,
            onFiltersChange = onCustomFiltersChange,
            onDismiss = { regexDialogOpen = false },
        )
    }
}

private sealed interface ToolbarItem {
    data object Copy : ToolbarItem
    data object Clear : ToolbarItem
    data object Divider : ToolbarItem
    data object Persist : ToolbarItem
    data object Time : ToolbarItem
    data object Color : ToolbarItem
    data object Wrap : ToolbarItem
    data object Levels : ToolbarItem
    data object Tags : ToolbarItem
    data object Regex : ToolbarItem
    data object Search : ToolbarItem
    data object MaxLines : ToolbarItem
}

private fun buildToolbarItems(
    searchExpanded: Boolean,
    searchQuery: String,
    autoPersist: Boolean,
    hideTimestamps: Boolean,
    coloredLevels: Boolean,
    wordWrap: Boolean,
    levelFilterActive: Boolean,
    tagFilterActive: Boolean,
    regexFilterActive: Boolean,
    maxDisplayLines: Int,
): List<ToolbarItem> = listOf(
    ToolbarItem.Copy,
    ToolbarItem.Clear,
    ToolbarItem.Divider,
    ToolbarItem.Persist,
    ToolbarItem.Time,
    ToolbarItem.Color,
    ToolbarItem.Wrap,
    ToolbarItem.Divider,
    ToolbarItem.Levels,
    ToolbarItem.Tags,
    ToolbarItem.Regex,
    ToolbarItem.Search,
    ToolbarItem.MaxLines,
)

@Composable
private fun LogToolbarChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    showLabel: Boolean,
    showDropdownArrow: Boolean = false,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            if (showLabel) {
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        },
        leadingIcon = {
            Icon(icon, contentDescription = label, modifier = Modifier.size(chipIconSize))
        },
        trailingIcon = if (showDropdownArrow) {
            {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(chipIconSize),
                )
            }
        } else {
            null
        },
        modifier = Modifier
            .height(chipHeight)
            .then(
                if (showLabel) {
                    Modifier.widthIn(min = chipWithLabelWidth - 8.dp)
                } else {
                    Modifier.width(chipIconOnlyWidth)
                },
            ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
        ),
    )
}

@Composable
private fun LogSearchBar(
    query: String,
    matchIndex: Int,
    matchCount: Int,
    showLabel: Boolean,
    onQueryChange: (String) -> Unit,
    onCollapse: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val matchLabel = if (matchCount > 0) {
        "${matchIndex + 1}/$matchCount"
    } else if (query.isNotBlank()) {
        "0/0"
    } else {
        ""
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .height(chipHeight)
            .widthIn(min = searchExpandedMinWidth, max = 220.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 2.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                modifier = Modifier.size(chipIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty() && showLabel) {
                            Text(
                                "Search",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            if (matchLabel.isNotBlank()) {
                Text(
                    matchLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(24.dp), enabled = matchCount > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous match",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(24.dp), enabled = matchCount > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next match",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onCollapse, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Clear, contentDescription = "Close search", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LogLevelMenuItem(
    level: Char,
    label: String,
    bit: Int,
    levelFilterMask: Int,
    onLevelFilterMaskChange: (Int) -> Unit,
) {
    val checked = levelFilterMask and bit != 0
    DropdownMenuItem(
        text = {
            Text(
                "$level  $label",
                color = logLevelColor(level),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = if (checked) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        onClick = {
            val updated = if (checked) levelFilterMask and bit.inv() else levelFilterMask or bit
            onLevelFilterMaskChange(updated)
        },
    )
}

@Composable
private fun LogTagFilterDialog(
    allTags: List<String>,
    disabledTags: Set<String>,
    onDisabledTagsChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(allTags, search) {
        val query = search.trim()
        if (query.isEmpty()) allTags else allTags.filter { it.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log tags") },
        text = {
            Column(modifier = Modifier.width(280.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { onDisabledTagsChange(emptySet()) }) { Text("All") }
                    TextButton(onClick = { onDisabledTagsChange(allTags.toSet()) }) { Text("None") }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    filtered.forEach { tag ->
                        val enabled = tag !in disabledTags
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDisabledTagsChange(
                                        if (enabled) disabledTags + tag else disabledTags - tag,
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                if (enabled) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(2.dp)
                                            .size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Text(
                                tag,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun LogCustomFilterDialog(
    filters: List<LogCustomFilter>,
    onFiltersChange: (List<LogCustomFilter>) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Regex filters") },
        text = {
            Column(modifier = Modifier.width(300.dp)) {
                Text(
                    "Match log tag (before colon)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (filters.isEmpty()) {
                        Text(
                            "No custom filters",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        filters.forEach { filter ->
                            LogCustomFilterRow(
                                filter = filter,
                                onChange = { updated ->
                                    onFiltersChange(filters.map { if (it.id == filter.id) updated else it })
                                },
                                onDelete = {
                                    onFiltersChange(filters.filterNot { it.id == filter.id })
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                TextButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Add filter")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
    if (showAddDialog) {
        AddCustomFilterDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { pattern, mode ->
                onFiltersChange(
                    filters + LogCustomFilter(
                        id = UUID.randomUUID().toString(),
                        pattern = pattern,
                        mode = mode,
                        enabled = true,
                    ),
                )
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun LogCustomFilterRow(
    filter: LogCustomFilter,
    onChange: (LogCustomFilter) -> Unit,
    onDelete: () -> Unit,
) {
    val regexValid = remember(filter.pattern) { filter.compiledRegex != null }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LogToolbarChip(
            label = "Filter",
            icon = Icons.Default.FilterList,
            selected = filter.enabled,
            showLabel = false,
            onClick = { onChange(filter.copy(enabled = !filter.enabled)) },
        )
        Text(
            filter.pattern,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            color = if (regexValid) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        FilterChip(
            selected = filter.mode == LogCustomFilterMode.ONLY_SHOW,
            onClick = {
                onChange(
                    filter.copy(
                        mode = if (filter.mode == LogCustomFilterMode.ONLY_SHOW) {
                            LogCustomFilterMode.HIDE
                        } else {
                            LogCustomFilterMode.ONLY_SHOW
                        },
                    ),
                )
            },
            label = {
                Text(
                    if (filter.mode == LogCustomFilterMode.ONLY_SHOW) "Show" else "Hide",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            modifier = Modifier.height(chipHeight),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete filter")
        }
    }
}

@Composable
private fun AddCustomFilterDialog(
    onDismiss: () -> Unit,
    onAdd: (pattern: String, mode: LogCustomFilterMode) -> Unit,
) {
    var pattern by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(LogCustomFilterMode.HIDE) }
    val valid = remember(pattern) { runCatching { Regex(pattern) }.isSuccess && pattern.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add regex filter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Regex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = pattern.isNotBlank() && !valid,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == LogCustomFilterMode.ONLY_SHOW,
                        onClick = { mode = LogCustomFilterMode.ONLY_SHOW },
                        label = { Text("Only show") },
                    )
                    FilterChip(
                        selected = mode == LogCustomFilterMode.HIDE,
                        onClick = { mode = LogCustomFilterMode.HIDE },
                        label = { Text("Hide") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(pattern.trim(), mode) }, enabled = valid) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun logLevelColor(level: Char): androidx.compose.ui.graphics.Color {
    val scheme = MaterialTheme.colorScheme
    return when (level.uppercaseChar()) {
        'D' -> androidx.compose.ui.graphics.Color(0xFF9E9E9E)
        'I' -> androidx.compose.ui.graphics.Color(0xFF66BB6A)
        'W' -> androidx.compose.ui.graphics.Color(0xFFFFA726)
        'E' -> scheme.error
        else -> scheme.onSurface
    }
}

@Composable
fun MenuBarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(22.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

fun logSearchMatchIndices(
    entries: List<org.openmultitrack.app.util.LogDisplayEntry>,
    query: String,
): List<Int> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    return entries.mapIndexedNotNull { index, entry ->
        when (entry) {
            is org.openmultitrack.app.util.LogDisplayEntry.Line -> {
                val haystack = "${entry.parsed.tag}: ${entry.parsed.message}"
                if (haystack.contains(trimmed, ignoreCase = true)) index else null
            }
            is org.openmultitrack.app.util.LogDisplayEntry.Section -> {
                if (entry.text.contains(trimmed, ignoreCase = true)) index else null
            }
        }
    }
}
