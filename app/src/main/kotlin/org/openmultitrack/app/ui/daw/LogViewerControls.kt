package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.openmultitrack.app.util.DevLogLevelMask
import org.openmultitrack.app.util.LogCustomFilter
import org.openmultitrack.app.util.LogCustomFilterMode

private val barHeight = 40.dp
private val iconBtnSize = 32.dp

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

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LogActionChip(label = "Copy", onClick = onCopy)
            LogActionChip(label = "Clear", onClick = onClear)
            MenuBarDivider()
            LogIconToggleButton(
                checked = autoPersist,
                onCheckedChange = onAutoPersistChange,
                icon = Icons.Default.Save,
                contentDescription = "Persist logs",
            )
            LogIconToggleButton(
                checked = hideTimestamps,
                onCheckedChange = onHideTimestampsChange,
                icon = Icons.Default.Schedule,
                contentDescription = "Hide timestamps",
            )
            LogIconToggleButton(
                checked = coloredLevels,
                onCheckedChange = onColoredLevelsChange,
                icon = Icons.Default.Palette,
                contentDescription = "Colored levels",
            )
            LogIconToggleButton(
                checked = wordWrap,
                onCheckedChange = onWordWrapChange,
                icon = Icons.Default.WrapText,
                contentDescription = "Word wrap",
            )
            MenuBarDivider()
            Box {
                LogFilterChip(
                    label = "Levels",
                    active = levelFilterActive,
                    icon = Icons.Default.FilterList,
                    onClick = { levelMenuOpen = true },
                )
                DropdownMenu(expanded = levelMenuOpen, onDismissRequest = { levelMenuOpen = false }) {
                    LogLevelMenuItem('D', "Debug", DevLogLevelMask.DEBUG, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelMenuItem('I', "Info", DevLogLevelMask.INFO, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelMenuItem('W', "Warn", DevLogLevelMask.WARN, levelFilterMask, onLevelFilterMaskChange)
                    LogLevelMenuItem('E', "Error", DevLogLevelMask.ERROR, levelFilterMask, onLevelFilterMaskChange)
                }
            }
            LogFilterChip(
                label = "Tags",
                active = tagFilterActive,
                icon = Icons.Default.Label,
                onClick = { tagDialogOpen = true },
            )
            LogFilterChip(
                label = "Regex",
                active = regexFilterActive,
                icon = Icons.Default.FilterList,
                onClick = { regexDialogOpen = true },
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$lineCountLabel lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun LogActionChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(label, style = MaterialTheme.typography.labelSmall)
        },
        modifier = Modifier.height(28.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = false,
        ),
    )
}

@Composable
private fun LogFilterChip(
    label: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = {
            Text(label, style = MaterialTheme.typography.labelSmall)
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        modifier = Modifier.height(28.dp),
    )
}

@Composable
private fun LogIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    if (checked) {
        FilledTonalIconButton(
            onClick = { onCheckedChange(false) },
            modifier = Modifier.size(iconBtnSize),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
        }
    } else {
        IconButton(
            onClick = { onCheckedChange(true) },
            modifier = Modifier.size(iconBtnSize),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
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
        LogIconToggleButton(
            checked = filter.enabled,
            onCheckedChange = { onChange(filter.copy(enabled = it)) },
            icon = Icons.Default.FilterList,
            contentDescription = "Enable filter",
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
            modifier = Modifier.height(28.dp),
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(iconBtnSize)) {
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
