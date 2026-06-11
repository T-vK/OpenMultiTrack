package org.openmultitrack.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.data.PostRecordBehavior
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode

data class SettingsUiState(
    val hideArmIcon: Boolean,
    val hideMonitorIcon: Boolean,
    val hideSoloIcon: Boolean,
    val hideRoutingBadges: Boolean,
    val showWaveforms: Boolean,
    val showVuMeters: Boolean,
    val recordWaveformWindowSec: Float,
    val playbackWaveformWindowSec: Float,
    val stripNumberMode: StripNumberMode,
    val stripIconMode: StripIconMode,
    val postRecordBehavior: PostRecordBehavior = PostRecordBehavior.FULL_PROMPT,
    val showRecordingStorageInfoButton: Boolean = true,
    val autoShowRecordingStorageTooltip: Boolean = true,
    val chapterSupportEnabled: Boolean = false,
    val recordWaveformNormalized: Boolean = true,
    val playbackWaveformNormalized: Boolean = false,
    val storageRootPath: String? = null,
    val effectiveStorageRootPath: String = "",
    val additionalLibraryRoots: List<String> = emptyList(),
    val autoScanRemovableMedia: Boolean = true,
    val storageVolumeOptions: List<org.openmultitrack.app.data.StorageVolumeOption> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    monitorGain: Float,
    onMonitorGainChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onHideArmChange: (Boolean) -> Unit,
    onHideMonitorChange: (Boolean) -> Unit,
    onHideSoloChange: (Boolean) -> Unit,
    onHideRoutingBadgesChange: (Boolean) -> Unit,
    onShowWaveformsChange: (Boolean) -> Unit,
    onShowVuMetersChange: (Boolean) -> Unit,
    onRecordWaveformWindowChange: (Float) -> Unit,
    onPlaybackWaveformWindowChange: (Float) -> Unit,
    onStripNumberModeChange: (StripNumberMode) -> Unit,
    onStripIconModeChange: (StripIconMode) -> Unit,
    onPostRecordBehaviorChange: (PostRecordBehavior) -> Unit = {},
    onShowRecordingStorageInfoButtonChange: (Boolean) -> Unit = {},
    onAutoShowRecordingStorageTooltipChange: (Boolean) -> Unit = {},
    onChapterSupportEnabledChange: (Boolean) -> Unit = {},
    onRecordWaveformNormalizedChange: (Boolean) -> Unit = {},
    onPlaybackWaveformNormalizedChange: (Boolean) -> Unit = {},
    onSetStorageRootPath: (String?) -> Unit = {},
    onAddAdditionalLibraryRoot: (String) -> Unit = {},
    onRemoveAdditionalLibraryRoot: (String) -> Unit = {},
    onAutoScanRemovableMediaChange: (Boolean) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        SettingsContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = state,
            monitorGain = monitorGain,
            onMonitorGainChange = onMonitorGainChange,
            onHideArmChange = onHideArmChange,
            onHideMonitorChange = onHideMonitorChange,
            onHideSoloChange = onHideSoloChange,
            onHideRoutingBadgesChange = onHideRoutingBadgesChange,
            onShowWaveformsChange = onShowWaveformsChange,
            onShowVuMetersChange = onShowVuMetersChange,
            onRecordWaveformWindowChange = onRecordWaveformWindowChange,
            onPlaybackWaveformWindowChange = onPlaybackWaveformWindowChange,
            onStripNumberModeChange = onStripNumberModeChange,
            onStripIconModeChange = onStripIconModeChange,
            onPostRecordBehaviorChange = onPostRecordBehaviorChange,
            onShowRecordingStorageInfoButtonChange = onShowRecordingStorageInfoButtonChange,
            onAutoShowRecordingStorageTooltipChange = onAutoShowRecordingStorageTooltipChange,
            onChapterSupportEnabledChange = onChapterSupportEnabledChange,
            onRecordWaveformNormalizedChange = onRecordWaveformNormalizedChange,
            onPlaybackWaveformNormalizedChange = onPlaybackWaveformNormalizedChange,
            onSetStorageRootPath = onSetStorageRootPath,
            onAddAdditionalLibraryRoot = onAddAdditionalLibraryRoot,
            onRemoveAdditionalLibraryRoot = onRemoveAdditionalLibraryRoot,
            onAutoScanRemovableMediaChange = onAutoScanRemovableMediaChange,
        )
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    monitorGain: Float,
    onMonitorGainChange: (Float) -> Unit,
    onHideArmChange: (Boolean) -> Unit,
    onHideMonitorChange: (Boolean) -> Unit,
    onHideSoloChange: (Boolean) -> Unit,
    onHideRoutingBadgesChange: (Boolean) -> Unit,
    onShowWaveformsChange: (Boolean) -> Unit,
    onShowVuMetersChange: (Boolean) -> Unit,
    onRecordWaveformWindowChange: (Float) -> Unit,
    onPlaybackWaveformWindowChange: (Float) -> Unit,
    onStripNumberModeChange: (StripNumberMode) -> Unit,
    onStripIconModeChange: (StripIconMode) -> Unit,
    onPostRecordBehaviorChange: (PostRecordBehavior) -> Unit = {},
    onShowRecordingStorageInfoButtonChange: (Boolean) -> Unit = {},
    onAutoShowRecordingStorageTooltipChange: (Boolean) -> Unit = {},
    onChapterSupportEnabledChange: (Boolean) -> Unit = {},
    onRecordWaveformNormalizedChange: (Boolean) -> Unit = {},
    onPlaybackWaveformNormalizedChange: (Boolean) -> Unit = {},
    onSetStorageRootPath: (String?) -> Unit = {},
    onAddAdditionalLibraryRoot: (String) -> Unit = {},
    onRemoveAdditionalLibraryRoot: (String) -> Unit = {},
    onAutoScanRemovableMediaChange: (Boolean) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()

    val rows = remember(state, monitorGain) {
        buildSettingsRows(
            state = state,
            monitorGain = monitorGain,
            onHideArmChange = onHideArmChange,
            onHideMonitorChange = onHideMonitorChange,
            onHideSoloChange = onHideSoloChange,
            onHideRoutingBadgesChange = onHideRoutingBadgesChange,
            onShowWaveformsChange = onShowWaveformsChange,
            onShowVuMetersChange = onShowVuMetersChange,
            onRecordWaveformWindowChange = onRecordWaveformWindowChange,
            onPlaybackWaveformWindowChange = onPlaybackWaveformWindowChange,
            onStripNumberModeChange = onStripNumberModeChange,
            onStripIconModeChange = onStripIconModeChange,
            onMonitorGainChange = onMonitorGainChange,
            onPostRecordBehaviorChange = onPostRecordBehaviorChange,
            onShowRecordingStorageInfoButtonChange = onShowRecordingStorageInfoButtonChange,
            onAutoShowRecordingStorageTooltipChange = onAutoShowRecordingStorageTooltipChange,
            onChapterSupportEnabledChange = onChapterSupportEnabledChange,
            onRecordWaveformNormalizedChange = onRecordWaveformNormalizedChange,
            onPlaybackWaveformNormalizedChange = onPlaybackWaveformNormalizedChange,
        )
    }
    val visibleRows = if (normalizedQuery.isEmpty()) {
        rows
    } else {
        rows.filter { row ->
            row.searchText.contains(normalizedQuery) ||
                row.section.lowercase().contains(normalizedQuery)
        }
    }
    val showStorageSection = normalizedQuery.isEmpty() ||
        "storage".contains(normalizedQuery) ||
        "recording".contains(normalizedQuery)

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search settings") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        if (visibleRows.isEmpty() && !showStorageSection) {
            Text(
                "No settings match \"$query\"",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val listItems = remember(visibleRows) {
                buildList {
                    var lastSection: String? = null
                    for (row in visibleRows) {
                        if (row.section != lastSection) {
                            if (lastSection != null) add(SettingsListItem.Divider)
                            add(SettingsListItem.Header(row.section))
                            lastSection = row.section
                        }
                        add(SettingsListItem.Entry(row))
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    count = listItems.size,
                    key = { index ->
                        when (val item = listItems[index]) {
                            is SettingsListItem.Divider -> "divider-$index"
                            is SettingsListItem.Header -> "header-${item.section}-$index"
                            is SettingsListItem.Entry -> item.row.id
                        }
                    },
                ) { index ->
                    val item = listItems[index]
                    when (item) {
                        SettingsListItem.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        is SettingsListItem.Header -> Text(
                            item.section,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        is SettingsListItem.Entry -> SettingsRow(item.row)
                    }
                }
                if (showStorageSection) {
                    item(key = "storage-section") {
                        SettingsStorageSection(
                            effectiveStorageRootPath = state.effectiveStorageRootPath,
                            storageRootPath = state.storageRootPath,
                            additionalLibraryRoots = state.additionalLibraryRoots,
                            autoScanRemovableMedia = state.autoScanRemovableMedia,
                            storageVolumeOptions = state.storageVolumeOptions,
                            onSetStorageRootPath = onSetStorageRootPath,
                            onAddAdditionalLibraryRoot = onAddAdditionalLibraryRoot,
                            onRemoveAdditionalLibraryRoot = onRemoveAdditionalLibraryRoot,
                            onAutoScanRemovableMediaChange = onAutoScanRemovableMediaChange,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface SettingsListItem {
    data object Divider : SettingsListItem
    data class Header(val section: String) : SettingsListItem
    data class Entry(val row: SettingsRowModel) : SettingsListItem
}

private sealed interface SettingsRowModel {
    val id: String
    val section: String
    val searchText: String
}

private data class SettingsToggleRow(
    override val id: String,
    override val section: String,
    val title: String,
    val description: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
) : SettingsRowModel {
    override val searchText: String = "$title $description".lowercase()
}

private data class SettingsSliderRow(
    override val id: String,
    override val section: String,
    val title: String,
    val description: String,
    val value: Float,
    val valueLabel: String,
    val valueRange: ClosedFloatingPointRange<Float> = 0.5f..8f,
    val onValueChange: (Float) -> Unit,
) : SettingsRowModel {
    override val searchText: String = "$title $description $valueLabel".lowercase()
}

private data class SettingsDropdownRow<T>(
    override val id: String,
    override val section: String,
    val title: String,
    val description: String,
    val options: List<T>,
    val selected: T,
    val label: (T) -> String,
    val onSelect: (T) -> Unit,
) : SettingsRowModel {
    override val searchText: String =
        "$title $description ${options.joinToString { label(it) }}".lowercase()
}

private data class SettingsPickerRow<T>(
    override val id: String,
    override val section: String,
    val title: String,
    val description: String,
    val options: List<T>,
    val selected: T,
    val label: (T) -> String,
    val onSelect: (T) -> Unit,
) : SettingsRowModel {
    override val searchText: String =
        "$title $description ${options.joinToString { label(it) }}".lowercase()
}

@Composable
private fun SettingsRow(row: SettingsRowModel) {
    when (row) {
        is SettingsToggleRow -> SettingsToggleItem(row)
        is SettingsSliderRow -> SettingsSliderItem(row)
        is SettingsPickerRow<*> -> SettingsPickerItem(row)
        is SettingsDropdownRow<*> -> SettingsDropdownItem(row)
    }
}

@Composable
private fun SettingsToggleItem(row: SettingsToggleRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = row.checked, onCheckedChange = row.onCheckedChange)
    }
}

@Composable
private fun SettingsSliderItem(row: SettingsSliderRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            Text(row.valueLabel, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            row.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = row.value,
            onValueChange = row.onValueChange,
            valueRange = row.valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun <T> SettingsDropdownItem(row: SettingsDropdownRow<T>) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(row.label(row.selected))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                row.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(row.label(option)) },
                        onClick = {
                            expanded = false
                            if (option != row.selected) row.onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SettingsPickerItem(row: SettingsPickerRow<T>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            row.options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == row.selected,
                    onClick = { row.onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, row.options.size),
                    modifier = Modifier.widthIn(min = 72.dp),
                ) {
                    Text(
                        row.label(option),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

private fun buildSettingsRows(
    state: SettingsUiState,
    monitorGain: Float,
    onHideArmChange: (Boolean) -> Unit,
    onHideMonitorChange: (Boolean) -> Unit,
    onHideSoloChange: (Boolean) -> Unit,
    onHideRoutingBadgesChange: (Boolean) -> Unit,
    onShowWaveformsChange: (Boolean) -> Unit,
    onShowVuMetersChange: (Boolean) -> Unit,
    onRecordWaveformWindowChange: (Float) -> Unit,
    onPlaybackWaveformWindowChange: (Float) -> Unit,
    onStripNumberModeChange: (StripNumberMode) -> Unit,
    onStripIconModeChange: (StripIconMode) -> Unit,
    onMonitorGainChange: (Float) -> Unit,
    onPostRecordBehaviorChange: (PostRecordBehavior) -> Unit,
    onShowRecordingStorageInfoButtonChange: (Boolean) -> Unit,
    onAutoShowRecordingStorageTooltipChange: (Boolean) -> Unit,
    onChapterSupportEnabledChange: (Boolean) -> Unit,
    onRecordWaveformNormalizedChange: (Boolean) -> Unit,
    onPlaybackWaveformNormalizedChange: (Boolean) -> Unit,
): List<SettingsRowModel> = listOf(
    SettingsDropdownRow(
        id = "post_record_behavior",
        section = "Recording",
        title = "After recording stops",
        description = "Choose automatic behavior or show a prompt when a recording finishes.",
        options = PostRecordBehavior.entries,
        selected = state.postRecordBehavior,
        label = { it.label },
        onSelect = onPostRecordBehaviorChange,
    ),
    SettingsToggleRow(
        id = "show_recording_storage_info",
        section = "Recording",
        title = "Show storage info button while recording",
        description = "Display the info button next to the record timer for free space and estimated recording time.",
        checked = state.showRecordingStorageInfoButton,
        onCheckedChange = onShowRecordingStorageInfoButtonChange,
    ),
    SettingsToggleRow(
        id = "auto_show_recording_storage_tooltip",
        section = "Recording",
        title = "Show storage tooltip when recording starts",
        description = "Automatically show free space and estimated recording time for five seconds when recording begins.",
        checked = state.autoShowRecordingStorageTooltip,
        onCheckedChange = onAutoShowRecordingStorageTooltipChange,
    ),
    SettingsToggleRow(
        id = "record_waveform_normalized",
        section = "Display",
        title = "Normalize live recording waveforms",
        description = "Scale each channel waveform to full height while recording or monitoring.",
        checked = state.recordWaveformNormalized,
        onCheckedChange = onRecordWaveformNormalizedChange,
    ),
    SettingsToggleRow(
        id = "playback_waveform_normalized",
        section = "Display",
        title = "Normalize playback waveforms",
        description = "Scale each channel waveform to full height in Simple Play and Virtual Soundcheck.",
        checked = state.playbackWaveformNormalized,
        onCheckedChange = onPlaybackWaveformNormalizedChange,
    ),
    SettingsToggleRow(
        id = "chapter_support",
        section = "Playback",
        title = "Chapter / trackmark support",
        description = "Enable trackmarks stored in a session .cue file, with previous/next navigation in playback modes.",
        checked = state.chapterSupportEnabled,
        onCheckedChange = onChapterSupportEnabledChange,
    ),
    SettingsSliderRow(
        id = "monitor_gain",
        section = "Audio",
        title = "Monitor gain",
        description = "Output level for the monitor bus when monitoring is active.",
        value = monitorGain,
        valueLabel = "${"%.1f".format(monitorGain)}×",
        onValueChange = onMonitorGainChange,
    ),
    SettingsSliderRow(
        id = "record_waveform_window",
        section = "Display",
        title = "Live waveform window",
        description = "How many seconds of recent audio each strip waveform shows while recording or monitoring.",
        value = state.recordWaveformWindowSec,
        valueRange = 5f..120f,
        valueLabel = "${state.recordWaveformWindowSec.toInt()} s",
        onValueChange = onRecordWaveformWindowChange,
    ),
    SettingsSliderRow(
        id = "playback_waveform_window",
        section = "Display",
        title = "Soundcheck waveform zoom",
        description = "Default visible time span on session waveforms in Virtual soundcheck mode.",
        value = state.playbackWaveformWindowSec,
        valueRange = 30f..600f,
        valueLabel = "${state.playbackWaveformWindowSec.toInt()} s",
        onValueChange = onPlaybackWaveformWindowChange,
    ),
    SettingsToggleRow(
        id = "show_waveforms",
        section = "Display",
        title = "Show waveforms",
        description = "Draw live level waveforms on each channel strip.",
        checked = state.showWaveforms,
        onCheckedChange = onShowWaveformsChange,
    ),
    SettingsToggleRow(
        id = "show_vu_meters",
        section = "Display",
        title = "Show VU meters",
        description = "Live input level bars on each strip. Works without monitor; uses a light USB capture stream.",
        checked = state.showVuMeters,
        onCheckedChange = onShowVuMetersChange,
    ),
    SettingsToggleRow(
        id = "hide_arm_icon",
        section = "Channel strips",
        title = "Hide arm icon",
        description = "Hide the record-arm indicator (●) on strips in the main view.",
        checked = state.hideArmIcon,
        onCheckedChange = onHideArmChange,
    ),
    SettingsToggleRow(
        id = "hide_monitor_icon",
        section = "Channel strips",
        title = "Hide monitor icon",
        description = "Hide the monitor indicator (🎧) on strips in the main view.",
        checked = state.hideMonitorIcon,
        onCheckedChange = onHideMonitorChange,
    ),
    SettingsToggleRow(
        id = "hide_solo_icon",
        section = "Channel strips",
        title = "Hide solo icon",
        description = "Hide the solo indicator (S) on strips in the main view.",
        checked = state.hideSoloIcon,
        onCheckedChange = onHideSoloChange,
    ),
    SettingsToggleRow(
        id = "hide_routing_badges",
        section = "Channel strips",
        title = "Hide IN/OUT badges",
        description = "Hide the small USB routing badges on each channel strip.",
        checked = state.hideRoutingBadges,
        onCheckedChange = onHideRoutingBadgesChange,
    ),
    SettingsPickerRow(
        id = "strip_numbers",
        section = "Channel strips",
        title = "Channel numbers",
        description = "How channel index numbers appear next to scribble labels.",
        options = StripNumberMode.entries,
        selected = state.stripNumberMode,
        label = { it.label },
        onSelect = onStripNumberModeChange,
    ),
    SettingsPickerRow(
        id = "strip_icons",
        section = "Channel strips",
        title = "Scribble icons",
        description = "How mixer icon emojis appear on imported strip labels.",
        options = StripIconMode.entries,
        selected = state.stripIconMode,
        label = { it.label },
        onSelect = onStripIconModeChange,
    ),
)
