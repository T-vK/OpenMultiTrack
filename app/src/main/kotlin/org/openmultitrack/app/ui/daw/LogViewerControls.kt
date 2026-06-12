package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.openmultitrack.app.util.DevLogLevelMask
import org.openmultitrack.app.util.LogCustomFilter
import org.openmultitrack.app.util.LogCustomFilterMode

@Composable
fun LogViewerToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 36.dp, height = 24.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LogViewerToolbar(
    visibleLineCount: Int,
    totalLineCount: Int,
    filterActive: Boolean,
    autoPersist: Boolean,
    hideTimestamps: Boolean,
    coloredLevels: Boolean,
    wordWrap: Boolean,
    levelFilterMask: Int,
    disabledTags: Set<String>,
    allTags: List<String>,
    customFilters: List<LogCustomFilter>,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onAutoPersistChange: (Boolean) -> Unit,
    onHideTimestampsChange: (Boolean) -> Unit,
    onColoredLevelsChange: (Boolean) -> Unit,
    onWordWrapChange: (Boolean) -> Unit,
    onLevelFilterMaskChange: (Int) -> Unit,
    onDisabledTagsChange: (Set<String>) -> Unit,
    onCustomFiltersChange: (List<LogCustomFilter>) -> Unit,
    compact: Boolean = false,
) {
    val menuLabelStyle = MaterialTheme.typography.labelSmall
    var levelMenuExpanded by remember { mutableStateOf(false) }
    var tagMenuExpanded by remember { mutableStateOf(false) }
    var customMenuExpanded by remember { mutableStateOf(false) }
    val lineCountLabel = if (filterActive && visibleLineCount != totalLineCount) {
        "$visibleLineCount/$totalLineCount"
    } else {
        "$totalLineCount"
    }
    val tagFilterActive = disabledTags.isNotEmpty()
    val customFilterActive = customFilters.any { it.enabled }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onCopy, contentPadding = toolbarButtonPadding) {
                Text("Copy", style = menuLabelStyle)
            }
            TextButton(onClick = onClear, contentPadding = toolbarButtonPadding) {
                Text("Clear", style = menuLabelStyle)
            }
            if (!compact) {
                ToolbarMenuButton(
                    label = if (levelFilterMask != DevLogLevelMask.ALL) "Levels*" else "Levels",
                    active = levelFilterMask != DevLogLevelMask.ALL,
                    expanded = levelMenuExpanded,
                    onExpandedChange = { levelMenuExpanded = it },
                ) {
                    LogLevelFilterRow('D', "Debug", DevLogLevelMask.DEBUG, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelFilterRow('I', "Info", DevLogLevelMask.INFO, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelFilterRow('W', "Warn", DevLogLevelMask.WARN, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelFilterRow('E', "Error", DevLogLevelMask.ERROR, levelFilterMask, onLevelFilterMaskChange)
                }
            }
            ToolbarMenuButton(
                label = if (tagFilterActive) "Tags*" else "Tags",
                active = tagFilterActive,
                expanded = tagMenuExpanded,
                onExpandedChange = { tagMenuExpanded = it },
            ) {
                LogTagFilterMenu(
                    allTags = allTags,
                    disabledTags = disabledTags,
                    onDisabledTagsChange = onDisabledTagsChange,
                    onDismiss = { tagMenuExpanded = false },
                )
            }
            ToolbarMenuButton(
                label = if (customFilterActive) "Regex*" else "Regex",
                active = customFilterActive,
                expanded = customMenuExpanded,
                onExpandedChange = { customMenuExpanded = it },
            ) {
                LogCustomFilterMenu(
                    filters = customFilters,
                    onFiltersChange = onCustomFiltersChange,
                    onDismiss = { customMenuExpanded = false },
                )
            }
            Text(
                "$lineCountLabel lines",
                style = menuLabelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (compact) {
            Row(Modifier.fillMaxWidth()) {
                ToolbarMenuButton(
                    label = if (levelFilterMask != DevLogLevelMask.ALL) "Levels*" else "Levels",
                    active = levelFilterMask != DevLogLevelMask.ALL,
                    expanded = levelMenuExpanded,
                    onExpandedChange = { levelMenuExpanded = it },
                ) {
                    LogLevelFilterRow('D', "Debug", DevLogLevelMask.DEBUG, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelFilterRow('I', "Info", DevLogLevelMask.INFO, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelFilterRow('W', "Warn", DevLogLevelMask.WARN, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelFilterRow('E', "Error", DevLogLevelMask.ERROR, levelFilterMask, onLevelFilterMaskChange)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            LogViewerToggle(
                label = "Persist",
                checked = autoPersist,
                onCheckedChange = onAutoPersistChange,
                modifier = Modifier.weight(1f),
            )
            LogViewerToggle(
                label = "No time",
                checked = hideTimestamps,
                onCheckedChange = onHideTimestampsChange,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            LogViewerToggle(
                label = "Colors",
                checked = coloredLevels,
                onCheckedChange = onColoredLevelsChange,
                modifier = Modifier.weight(1f),
            )
            LogViewerToggle(
                label = "Wrap",
                checked = wordWrap,
                onCheckedChange = onWordWrapChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val toolbarButtonPadding =
    androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)

@Composable
private fun ToolbarMenuButton(
    label: String,
    active: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable () -> Unit,
) {
    Box {
        TextButton(
            onClick = { onExpandedChange(true) },
            contentPadding = toolbarButtonPadding,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            menuContent()
        }
    }
}

@Composable
fun LogLevelFilterRow(
    level: Char,
    label: String,
    bit: Int,
    levelFilterMask: Int,
    onLevelFilterMaskChange: (Int) -> Unit,
) {
    val checked = levelFilterMask and bit != 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val updated = if (checked) levelFilterMask and bit.inv() else levelFilterMask or bit
                onLevelFilterMaskChange(updated)
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { enabled ->
                val updated = if (enabled) levelFilterMask or bit else levelFilterMask and bit.inv()
                onLevelFilterMaskChange(updated)
            },
            modifier = Modifier.size(width = 36.dp, height = 24.dp),
        )
        Text(
            "$level  $label",
            style = MaterialTheme.typography.labelMedium,
            color = logLevelColor(level),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LogTagFilterMenu(
    allTags: List<String>,
    disabledTags: Set<String>,
    onDisabledTagsChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(allTags, search) {
        val query = search.trim()
        if (query.isEmpty()) {
            allTags
        } else {
            allTags.filter { it.contains(query, ignoreCase = true) }
        }
    }
    Column(
        modifier = Modifier
            .width(260.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search tags") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = { onDisabledTagsChange(emptySet()) },
                contentPadding = toolbarButtonPadding,
            ) {
                Text("All", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(
                onClick = { onDisabledTagsChange(allTags.toSet()) },
                contentPadding = toolbarButtonPadding,
            ) {
                Text("None", style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
        ) {
            items(filtered, key = { it }) { tag ->
                val enabled = tag !in disabledTags
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDisabledTagsChange(
                                if (enabled) {
                                    disabledTags + tag
                                } else {
                                    disabledTags - tag
                                },
                            )
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            onDisabledTagsChange(
                                if (checked) disabledTags - tag else disabledTags + tag,
                            )
                        },
                    )
                    Text(tag, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun LogCustomFilterMenu(
    filters: List<LogCustomFilter>,
    onFiltersChange: (List<LogCustomFilter>) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(300.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            "Custom regex filters (match log tag)",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Add", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    }
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
    var modeMenuExpanded by remember { mutableStateOf(false) }
    val regexValid = remember(filter.pattern) { filter.compiledRegex != null }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = filter.enabled,
                onCheckedChange = { onChange(filter.copy(enabled = it)) },
                modifier = Modifier.size(width = 36.dp, height = 24.dp),
            )
            Text(
                filter.pattern,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                color = if (regexValid) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Box {
                TextButton(
                    onClick = { modeMenuExpanded = true },
                    contentPadding = toolbarButtonPadding,
                ) {
                    Text(
                        if (filter.mode == LogCustomFilterMode.ONLY_SHOW) "Show" else "Hide",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                DropdownMenu(
                    expanded = modeMenuExpanded,
                    onDismissRequest = { modeMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Only show") },
                        onClick = {
                            onChange(filter.copy(mode = LogCustomFilterMode.ONLY_SHOW))
                            modeMenuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hide") },
                        onClick = {
                            onChange(filter.copy(mode = LogCustomFilterMode.HIDE))
                            modeMenuExpanded = false
                        },
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete filter")
            }
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
                    label = { Text("Regex (matches tag)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = pattern.isNotBlank() && !valid,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogViewerToggle(
                        label = "Only show",
                        checked = mode == LogCustomFilterMode.ONLY_SHOW,
                        onCheckedChange = {
                            mode = if (it) LogCustomFilterMode.ONLY_SHOW else LogCustomFilterMode.HIDE
                        },
                    )
                    LogViewerToggle(
                        label = "Hide",
                        checked = mode == LogCustomFilterMode.HIDE,
                        onCheckedChange = {
                            mode = if (it) LogCustomFilterMode.HIDE else LogCustomFilterMode.ONLY_SHOW
                        },
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
