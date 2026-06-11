package org.openmultitrack.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
    val redundantRecordingRoots: List<String> = emptyList(),
    val alwaysIncludeOpenMultiTrackFolders: Boolean = true,
    val localSpillBufferEnabled: Boolean = true,
    val localSpillBufferMinutes: Int = 5,
    val minFreeStorageBytes: Long = 0L,
    val batteryOptimizationIgnored: Boolean = true,
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
    onAddRedundantRecordingRoot: (String) -> Unit = {},
    onRemoveRedundantRecordingRoot: (String) -> Unit = {},
    onAlwaysIncludeOpenMultiTrackFoldersChange: (Boolean) -> Unit = {},
    onLocalSpillBufferEnabledChange: (Boolean) -> Unit = {},
    onLocalSpillBufferMinutesChange: (Int) -> Unit = {},
    onMinFreeStorageBytesChange: (Long) -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
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
            onAddRedundantRecordingRoot = onAddRedundantRecordingRoot,
            onRemoveRedundantRecordingRoot = onRemoveRedundantRecordingRoot,
            onAlwaysIncludeOpenMultiTrackFoldersChange = onAlwaysIncludeOpenMultiTrackFoldersChange,
            onLocalSpillBufferEnabledChange = onLocalSpillBufferEnabledChange,
            onLocalSpillBufferMinutesChange = onLocalSpillBufferMinutesChange,
            onMinFreeStorageBytesChange = onMinFreeStorageBytesChange,
            onOpenBatterySettings = onOpenBatterySettings,
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
    onAddRedundantRecordingRoot: (String) -> Unit = {},
    onRemoveRedundantRecordingRoot: (String) -> Unit = {},
    onAlwaysIncludeOpenMultiTrackFoldersChange: (Boolean) -> Unit = {},
    onLocalSpillBufferEnabledChange: (Boolean) -> Unit = {},
    onLocalSpillBufferMinutesChange: (Int) -> Unit = {},
    onMinFreeStorageBytesChange: (Long) -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var openCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    val normalizedQuery = query.trim().lowercase()
    val isSearching = normalizedQuery.isNotEmpty()

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

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.isNotBlank()) openCategory = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search settings") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        when {
            isSearching -> SettingsSearchResults(
                query = query,
                normalizedQuery = normalizedQuery,
                rows = rows,
                state = state,
                onSetStorageRootPath = onSetStorageRootPath,
                onAddAdditionalLibraryRoot = onAddAdditionalLibraryRoot,
                onRemoveAdditionalLibraryRoot = onRemoveAdditionalLibraryRoot,
                onAddRedundantRecordingRoot = onAddRedundantRecordingRoot,
                onRemoveRedundantRecordingRoot = onRemoveRedundantRecordingRoot,
                onAutoScanRemovableMediaChange = onAutoScanRemovableMediaChange,
                onAlwaysIncludeOpenMultiTrackFoldersChange = onAlwaysIncludeOpenMultiTrackFoldersChange,
                onLocalSpillBufferEnabledChange = onLocalSpillBufferEnabledChange,
                onLocalSpillBufferMinutesChange = onLocalSpillBufferMinutesChange,
                onMinFreeStorageBytesChange = onMinFreeStorageBytesChange,
                onOpenBatterySettings = onOpenBatterySettings,
            )
            openCategory == null -> SettingsCategoryList(
                onSelect = { openCategory = it },
            )
            else -> SettingsCategoryDetail(
                category = openCategory!!,
                state = state,
                rows = rows.filter { it.category == openCategory },
                onBack = { openCategory = null },
                onSetStorageRootPath = onSetStorageRootPath,
                onAddAdditionalLibraryRoot = onAddAdditionalLibraryRoot,
                onRemoveAdditionalLibraryRoot = onRemoveAdditionalLibraryRoot,
                onAddRedundantRecordingRoot = onAddRedundantRecordingRoot,
                onRemoveRedundantRecordingRoot = onRemoveRedundantRecordingRoot,
                onAutoScanRemovableMediaChange = onAutoScanRemovableMediaChange,
                onAlwaysIncludeOpenMultiTrackFoldersChange = onAlwaysIncludeOpenMultiTrackFoldersChange,
                onLocalSpillBufferEnabledChange = onLocalSpillBufferEnabledChange,
                onLocalSpillBufferMinutesChange = onLocalSpillBufferMinutesChange,
                onMinFreeStorageBytesChange = onMinFreeStorageBytesChange,
                onOpenBatterySettings = onOpenBatterySettings,
            )
        }
    }
}

private enum class SettingsCategory(
    val title: String,
    val subtitle: String,
) {
    RECORDING("Recording", "After stop, storage hints"),
    DISPLAY("Display", "Waveforms, meters, zoom"),
    PLAYBACK("Playback", "Soundcheck and chapters"),
    CHANNEL_STRIPS("Channel strips", "Icons, labels, visibility"),
    AUDIO("Audio", "Monitor level"),
    STORAGE("Storage", "Paths, mirrors, spill buffer"),
    RELIABILITY("Background", "Battery and uninterrupted recording"),
}

@Composable
private fun SettingsCategoryList(onSelect: (SettingsCategory) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(SettingsCategory.entries.size) { index ->
            val category = SettingsCategory.entries[index]
            Surface(
                onClick = { onSelect(category) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(category.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            category.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryDetail(
    category: SettingsCategory,
    state: SettingsUiState,
    rows: List<SettingsRowModel>,
    onBack: () -> Unit,
    onSetStorageRootPath: (String?) -> Unit,
    onAddAdditionalLibraryRoot: (String) -> Unit,
    onRemoveAdditionalLibraryRoot: (String) -> Unit,
    onAddRedundantRecordingRoot: (String) -> Unit,
    onRemoveRedundantRecordingRoot: (String) -> Unit,
    onAutoScanRemovableMediaChange: (Boolean) -> Unit,
    onAlwaysIncludeOpenMultiTrackFoldersChange: (Boolean) -> Unit,
    onLocalSpillBufferEnabledChange: (Boolean) -> Unit,
    onLocalSpillBufferMinutesChange: (Int) -> Unit,
    onMinFreeStorageBytesChange: (Long) -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to categories")
            }
            Text(
                category.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when (category) {
                SettingsCategory.STORAGE -> item(key = "storage") {
                    SettingsStorageSection(
                        effectiveStorageRootPath = state.effectiveStorageRootPath,
                        storageRootPath = state.storageRootPath,
                        additionalLibraryRoots = state.additionalLibraryRoots,
                        redundantRecordingRoots = state.redundantRecordingRoots,
                        autoScanRemovableMedia = state.autoScanRemovableMedia,
                        alwaysIncludeOpenMultiTrackFolders = state.alwaysIncludeOpenMultiTrackFolders,
                        localSpillBufferEnabled = state.localSpillBufferEnabled,
                        localSpillBufferMinutes = state.localSpillBufferMinutes,
                        minFreeStorageBytes = state.minFreeStorageBytes,
                        storageVolumeOptions = state.storageVolumeOptions,
                        onSetStorageRootPath = onSetStorageRootPath,
                        onAddAdditionalLibraryRoot = onAddAdditionalLibraryRoot,
                        onRemoveAdditionalLibraryRoot = onRemoveAdditionalLibraryRoot,
                        onAddRedundantRecordingRoot = onAddRedundantRecordingRoot,
                        onRemoveRedundantRecordingRoot = onRemoveRedundantRecordingRoot,
                        onAutoScanRemovableMediaChange = onAutoScanRemovableMediaChange,
                        onAlwaysIncludeOpenMultiTrackFoldersChange = onAlwaysIncludeOpenMultiTrackFoldersChange,
                        onLocalSpillBufferEnabledChange = onLocalSpillBufferEnabledChange,
                        onLocalSpillBufferMinutesChange = onLocalSpillBufferMinutesChange,
                        onMinFreeStorageBytesChange = onMinFreeStorageBytesChange,
                    )
                }
                SettingsCategory.RELIABILITY -> item(key = "reliability") {
                    SettingsReliabilitySection(
                        batteryOptimizationIgnored = state.batteryOptimizationIgnored,
                        onOpenBatterySettings = onOpenBatterySettings,
                    )
                }
                else -> items(rows, key = { it.id }) { row ->
                    SettingsRow(row)
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchResults(
    query: String,
    normalizedQuery: String,
    rows: List<SettingsRowModel>,
    state: SettingsUiState,
    onSetStorageRootPath: (String?) -> Unit,
    onAddAdditionalLibraryRoot: (String) -> Unit,
    onRemoveAdditionalLibraryRoot: (String) -> Unit,
    onAddRedundantRecordingRoot: (String) -> Unit,
    onRemoveRedundantRecordingRoot: (String) -> Unit,
    onAutoScanRemovableMediaChange: (Boolean) -> Unit,
    onAlwaysIncludeOpenMultiTrackFoldersChange: (Boolean) -> Unit,
    onLocalSpillBufferEnabledChange: (Boolean) -> Unit,
    onLocalSpillBufferMinutesChange: (Int) -> Unit,
    onMinFreeStorageBytesChange: (Long) -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val visibleRows = rows.filter { row ->
        row.searchText.contains(normalizedQuery) ||
            row.category.title.lowercase().contains(normalizedQuery)
    }
    val showStorage = normalizedQuery.contains("storage") ||
        normalizedQuery.contains("mirror") ||
        normalizedQuery.contains("spill") ||
        normalizedQuery.contains("library") ||
        visibleRows.any { it.category == SettingsCategory.STORAGE }
    val showReliability = normalizedQuery.contains("battery") ||
        normalizedQuery.contains("background") ||
        normalizedQuery.contains("reliability")

    if (visibleRows.isEmpty() && !showStorage && !showReliability) {
        Text(
            "No settings match \"$query\"",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(visibleRows, key = { it.id }) { row -> SettingsRow(row) }
        if (showStorage) {
            item(key = "search-storage") {
                SettingsStorageSection(
                    effectiveStorageRootPath = state.effectiveStorageRootPath,
                    storageRootPath = state.storageRootPath,
                    additionalLibraryRoots = state.additionalLibraryRoots,
                    redundantRecordingRoots = state.redundantRecordingRoots,
                    autoScanRemovableMedia = state.autoScanRemovableMedia,
                    alwaysIncludeOpenMultiTrackFolders = state.alwaysIncludeOpenMultiTrackFolders,
                    localSpillBufferEnabled = state.localSpillBufferEnabled,
                    localSpillBufferMinutes = state.localSpillBufferMinutes,
                    minFreeStorageBytes = state.minFreeStorageBytes,
                    storageVolumeOptions = state.storageVolumeOptions,
                    onSetStorageRootPath = onSetStorageRootPath,
                    onAddAdditionalLibraryRoot = onAddAdditionalLibraryRoot,
                    onRemoveAdditionalLibraryRoot = onRemoveAdditionalLibraryRoot,
                    onAddRedundantRecordingRoot = onAddRedundantRecordingRoot,
                    onRemoveRedundantRecordingRoot = onRemoveRedundantRecordingRoot,
                    onAutoScanRemovableMediaChange = onAutoScanRemovableMediaChange,
                    onAlwaysIncludeOpenMultiTrackFoldersChange = onAlwaysIncludeOpenMultiTrackFoldersChange,
                    onLocalSpillBufferEnabledChange = onLocalSpillBufferEnabledChange,
                    onLocalSpillBufferMinutesChange = onLocalSpillBufferMinutesChange,
                    onMinFreeStorageBytesChange = onMinFreeStorageBytesChange,
                )
            }
        }
        if (showReliability) {
            item(key = "search-reliability") {
                SettingsReliabilitySection(
                    batteryOptimizationIgnored = state.batteryOptimizationIgnored,
                    onOpenBatterySettings = onOpenBatterySettings,
                )
            }
        }
    }
}

@Composable
private fun SettingsReliabilitySection(
    batteryOptimizationIgnored: Boolean,
    onOpenBatterySettings: () -> Unit,
) {
    SettingsSubsectionHeader(
        title = "Battery optimization",
        description = "Prevent Android from pausing recording when the app is in the background.",
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (batteryOptimizationIgnored) {
                "OpenMultiTrack is allowed to run in the background during recording."
            } else {
                "Android may pause recording in the background. Disable battery optimization for uninterrupted multitrack capture."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
            Text(if (batteryOptimizationIgnored) "Review battery settings" else "Disable battery optimization")
        }
    }
    Spacer(Modifier.height(8.dp))
}

private sealed interface SettingsRowModel {
    val id: String
    val category: SettingsCategory
    val searchText: String
}

private data class SettingsToggleRow(
    override val id: String,
    override val category: SettingsCategory,
    val title: String,
    val description: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
) : SettingsRowModel {
    override val searchText: String = "$title $description".lowercase()
}

private data class SettingsSliderRow(
    override val id: String,
    override val category: SettingsCategory,
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
    override val category: SettingsCategory,
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
    override val category: SettingsCategory,
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
        category = SettingsCategory.RECORDING,
        title = "After recording stops",
        description = "Choose automatic behavior or show a prompt when a recording finishes.",
        options = PostRecordBehavior.entries,
        selected = state.postRecordBehavior,
        label = { it.label },
        onSelect = onPostRecordBehaviorChange,
    ),
    SettingsToggleRow(
        id = "show_recording_storage_info",
        category = SettingsCategory.RECORDING,
        title = "Show storage info button while recording",
        description = "Display the info button next to the record timer for free space and estimated recording time.",
        checked = state.showRecordingStorageInfoButton,
        onCheckedChange = onShowRecordingStorageInfoButtonChange,
    ),
    SettingsToggleRow(
        id = "auto_show_recording_storage_tooltip",
        category = SettingsCategory.RECORDING,
        title = "Show storage tooltip when recording starts",
        description = "Automatically show free space and estimated recording time for five seconds when recording begins.",
        checked = state.autoShowRecordingStorageTooltip,
        onCheckedChange = onAutoShowRecordingStorageTooltipChange,
    ),
    SettingsToggleRow(
        id = "record_waveform_normalized",
        category = SettingsCategory.DISPLAY,
        title = "Normalize live recording waveforms",
        description = "Scale each channel waveform to full height while recording or monitoring.",
        checked = state.recordWaveformNormalized,
        onCheckedChange = onRecordWaveformNormalizedChange,
    ),
    SettingsToggleRow(
        id = "playback_waveform_normalized",
        category = SettingsCategory.DISPLAY,
        title = "Normalize playback waveforms",
        description = "Scale each channel waveform to full height in Simple Play and Virtual Soundcheck.",
        checked = state.playbackWaveformNormalized,
        onCheckedChange = onPlaybackWaveformNormalizedChange,
    ),
    SettingsToggleRow(
        id = "chapter_support",
        category = SettingsCategory.PLAYBACK,
        title = "Chapter / trackmark support",
        description = "Enable trackmarks stored in a session .cue file, with previous/next navigation in playback modes.",
        checked = state.chapterSupportEnabled,
        onCheckedChange = onChapterSupportEnabledChange,
    ),
    SettingsSliderRow(
        id = "monitor_gain",
        category = SettingsCategory.AUDIO,
        title = "Monitor gain",
        description = "Output level for the monitor bus when monitoring is active.",
        value = monitorGain,
        valueLabel = "${"%.1f".format(monitorGain)}×",
        onValueChange = onMonitorGainChange,
    ),
    SettingsSliderRow(
        id = "record_waveform_window",
        category = SettingsCategory.DISPLAY,
        title = "Live waveform window",
        description = "How many seconds of recent audio each strip waveform shows while recording or monitoring.",
        value = state.recordWaveformWindowSec,
        valueRange = 5f..120f,
        valueLabel = "${state.recordWaveformWindowSec.toInt()} s",
        onValueChange = onRecordWaveformWindowChange,
    ),
    SettingsSliderRow(
        id = "playback_waveform_window",
        category = SettingsCategory.DISPLAY,
        title = "Soundcheck waveform zoom",
        description = "Default visible time span on session waveforms in Virtual soundcheck mode.",
        value = state.playbackWaveformWindowSec,
        valueRange = 30f..600f,
        valueLabel = "${state.playbackWaveformWindowSec.toInt()} s",
        onValueChange = onPlaybackWaveformWindowChange,
    ),
    SettingsToggleRow(
        id = "show_waveforms",
        category = SettingsCategory.DISPLAY,
        title = "Show waveforms",
        description = "Draw live level waveforms on each channel strip.",
        checked = state.showWaveforms,
        onCheckedChange = onShowWaveformsChange,
    ),
    SettingsToggleRow(
        id = "show_vu_meters",
        category = SettingsCategory.DISPLAY,
        title = "Show VU meters",
        description = "Live input level bars on each strip. Works without monitor; uses a light USB capture stream.",
        checked = state.showVuMeters,
        onCheckedChange = onShowVuMetersChange,
    ),
    SettingsToggleRow(
        id = "hide_arm_icon",
        category = SettingsCategory.CHANNEL_STRIPS,
        title = "Hide arm icon",
        description = "Hide the record-arm indicator (●) on strips in the main view.",
        checked = state.hideArmIcon,
        onCheckedChange = onHideArmChange,
    ),
    SettingsToggleRow(
        id = "hide_monitor_icon",
        category = SettingsCategory.CHANNEL_STRIPS,
        title = "Hide monitor icon",
        description = "Hide the monitor indicator (🎧) on strips in the main view.",
        checked = state.hideMonitorIcon,
        onCheckedChange = onHideMonitorChange,
    ),
    SettingsToggleRow(
        id = "hide_solo_icon",
        category = SettingsCategory.CHANNEL_STRIPS,
        title = "Hide solo icon",
        description = "Hide the solo indicator (S) on strips in the main view.",
        checked = state.hideSoloIcon,
        onCheckedChange = onHideSoloChange,
    ),
    SettingsToggleRow(
        id = "hide_routing_badges",
        category = SettingsCategory.CHANNEL_STRIPS,
        title = "Hide IN/OUT badges",
        description = "Hide the small USB routing badges on each channel strip.",
        checked = state.hideRoutingBadges,
        onCheckedChange = onHideRoutingBadgesChange,
    ),
    SettingsPickerRow(
        id = "strip_numbers",
        category = SettingsCategory.CHANNEL_STRIPS,
        title = "Channel numbers",
        description = "How channel index numbers appear next to scribble labels.",
        options = StripNumberMode.entries,
        selected = state.stripNumberMode,
        label = { it.label },
        onSelect = onStripNumberModeChange,
    ),
    SettingsPickerRow(
        id = "strip_icons",
        category = SettingsCategory.CHANNEL_STRIPS,
        title = "Scribble icons",
        description = "How mixer icon emojis appear on imported strip labels.",
        options = StripIconMode.entries,
        selected = state.stripIconMode,
        label = { it.label },
        onSelect = onStripIconModeChange,
    ),
)
