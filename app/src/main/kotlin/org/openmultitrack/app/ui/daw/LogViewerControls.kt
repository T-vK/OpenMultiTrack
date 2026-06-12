package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.openmultitrack.app.util.DevLogLevelMask
import org.openmultitrack.app.util.LogCustomFilter
import org.openmultitrack.app.util.LogCustomFilterMode

private val menuBarHeight = 32.dp
private val toggleSize = 30.dp
private val menuButtonPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)

@Composable
fun LogViewerMenuBar(
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
) {
    val labelStyle = MaterialTheme.typography.labelSmall
    var levelMenuOpen by remember { mutableStateOf(false) }
    var tagDialogOpen by remember { mutableStateOf(false) }
    var regexDialogOpen by remember { mutableStateOf(false) }
    val lineCountLabel = if (filterActive && visibleLineCount != totalLineCount) {
        "$visibleLineCount/$totalLineCount"
    } else {
        "$totalLineCount"
    }
    val levelFilterActive = levelFilterMask != DevLogLevelMask.ALL
    val tagFilterActive = disabledTags.isNotEmpty()
    val regexFilterActive = customFilters.any { it.enabled }

    Surface(tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(menuBarHeight)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TextButton(onClick = onCopy, contentPadding = menuButtonPadding) {
                Text("Copy", style = labelStyle)
            }
            TextButton(onClick = onClear, contentPadding = menuButtonPadding) {
                Text("Clear", style = labelStyle)
            }
            MenuBarDivider()
            LogIconToggle(
                checked = autoPersist,
                onCheckedChange = onAutoPersistChange,
                icon = {
                Icon(Icons.Default.Save, contentDescription = "Persist logs", modifier = Modifier.size(16.dp))
            },
            )
            LogIconToggle(
                checked = hideTimestamps,
                onCheckedChange = onHideTimestampsChange,
                icon = {
                Icon(Icons.Default.Schedule, contentDescription = "Hide timestamps", modifier = Modifier.size(16.dp))
            },
            )
            LogIconToggle(
                checked = coloredLevels,
                onCheckedChange = onColoredLevelsChange,
                icon = {
                Icon(Icons.Default.Palette, contentDescription = "Colored levels", modifier = Modifier.size(16.dp))
            },
            )
            LogIconToggle(
                checked = wordWrap,
                onCheckedChange = onWordWrapChange,
                icon = {
                Icon(Icons.Default.WrapText, contentDescription = "Word wrap", modifier = Modifier.size(16.dp))
            },
            )
            MenuBarDivider()
            Box {
                FilterTextButton(
                    label = if (levelFilterActive) "Levels*" else "Levels",
                    active = levelFilterActive,
                    onClick = { levelMenuOpen = true },
                )
                DropdownMenu(expanded = levelMenuOpen, onDismissRequest = { levelMenuOpen = false }) {
                    LogLevelMenuItem('D', "Debug", DevLogLevelMask.DEBUG, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelMenuItem('I', "Info", DevLogLevelMask.INFO, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelMenuItem('W', "Warn", DevLogLevelMask.WARN, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelMenuItem('E', "Error", DevLogLevelMask.ERROR, levelFilterMask, onLevelFilterMaskChange)
                }
            }
            FilterTextButton(
                label = if (tagFilterActive) "Tags*" else "Tags",
                active = tagFilterActive,
                onClick = { tagDialogOpen = true },
            )
            FilterTextButton(
                label = if (regexFilterActive) "Regex*" else "Regex",
                active = regexFilterActive,
                onClick = { regexDialogOpen = true },
            )
            Text(
                "$lineCountLabel lines",
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
                maxLines = 1,
            )
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

@Composable
private fun LogIconToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
) {
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.size(toggleSize),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
            }
        }
    }
}

@Composable
private fun FilterTextButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, contentPadding = menuButtonPadding) {
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (enabled) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Spacer(Modifier.size(18.dp))
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
    ) {
        LogIconToggle(
            checked = filter.enabled,
            onCheckedChange = { onChange(filter.copy(enabled = it)) },
            icon = {
                Text(
                    if (filter.mode == LogCustomFilterMode.ONLY_SHOW) "+" else "−",
                    style = MaterialTheme.typography.labelMedium,
                )
            },
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
        TextButton(
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
            contentPadding = menuButtonPadding,
        ) {
            Text(
                if (filter.mode == LogCustomFilterMode.ONLY_SHOW) "Show" else "Hide",
                style = MaterialTheme.typography.labelSmall,
            )
        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterTextButton(
                        label = "Only show",
                        active = mode == LogCustomFilterMode.ONLY_SHOW,
                        onClick = { mode = LogCustomFilterMode.ONLY_SHOW },
                    )
                    FilterTextButton(
                        label = "Hide",
                        active = mode == LogCustomFilterMode.HIDE,
                        onClick = { mode = LogCustomFilterMode.HIDE },
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
            .width(1.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}