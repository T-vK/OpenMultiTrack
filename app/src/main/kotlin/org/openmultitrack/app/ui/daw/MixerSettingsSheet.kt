package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerRoutingConfig
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.mixer.behringer.MixingStationIcons
import org.openmultitrack.mixer.behringer.ScribbleStripLabel
import org.openmultitrack.usb.LabeledAudioDevice

private sealed interface MixerSettingsPage {
    data object Menu : MixerSettingsPage
    data object Monitor : MixerSettingsPage
    data object InputMatrix : MixerSettingsPage
    data object OutputMatrix : MixerSettingsPage
    data object HiddenSoundcheck : MixerSettingsPage
    data object HiddenRecord : MixerSettingsPage
}

private val LabelColumnWidth = 112.dp
private val MatrixCellSize = 36.dp
private val MatrixGap = 3.dp
private val MonitorBlue = Color(0xFF1E88E5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerSettingsSheet(
    mixerName: String,
    channelCount: Int,
    usbChannelCount: Int,
    usbPlaybackChannelCount: Int = usbChannelCount,
    strips: List<ChannelStripState>,
    config: MixerRoutingConfig,
    appMode: AppMode?,
    isMonitoring: Boolean,
    monitorEnabled: Boolean,
    outputDevices: List<LabeledAudioDevice>,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
    onSetMonitorOutput: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: (MixerRoutingConfig) -> Unit,
) {
    var page by remember { mutableStateOf<MixerSettingsPage>(MixerSettingsPage.Menu) }
    var draft by remember(config) { mutableStateOf(config) }

    fun persist(updated: MixerRoutingConfig) {
        draft = updated
        onSave(updated)
    }

    val logicalCount = channelCount.coerceAtLeast(strips.size).coerceAtLeast(1)
    val usbInputCount = maxOf(
        usbChannelCount,
        logicalCount,
        (draft.inputMap.values.maxOrNull() ?: 0) + 1,
    ).coerceAtLeast(1)
    val usbOutputCount = maxOf(
        usbPlaybackChannelCount,
        (draft.outputMap.values.maxOrNull() ?: 0) + 1,
    ).coerceAtLeast(1)

    val stripByIndex = strips.associateBy { it.index }

    ExpandedBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (page != MixerSettingsPage.Menu) {
                    IconButton(onClick = { page = MixerSettingsPage.Menu }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        mixerName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        pageTitle(page),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (page) {
                MixerSettingsPage.Menu -> SettingsMenu(
                    onMonitor = { page = MixerSettingsPage.Monitor },
                    onInputMatrix = { page = MixerSettingsPage.InputMatrix },
                    onOutputMatrix = { page = MixerSettingsPage.OutputMatrix },
                    onHiddenSoundcheck = { page = MixerSettingsPage.HiddenSoundcheck },
                    onHiddenRecord = { page = MixerSettingsPage.HiddenRecord },
                )
                MixerSettingsPage.Monitor -> MonitorSettingsPage(
                    appMode = appMode,
                    isMonitoring = isMonitoring,
                    monitorEnabled = monitorEnabled,
                    outputDevices = outputDevices,
                    onStartMonitor = onStartMonitor,
                    onStopMonitor = onStopMonitor,
                    onSetMonitorOutput = onSetMonitorOutput,
                )
                MixerSettingsPage.InputMatrix -> RoutingMatrix(
                    title = "Tap a cell to route each strip to a USB input.",
                    logicalCount = logicalCount,
                    usbCount = usbInputCount,
                    stripByIndex = stripByIndex,
                    selectedUsb = { logical -> draft.inputSource(logical) },
                    onSelect = { logical, usb ->
                        persist(draft.copy(inputMap = draft.inputMap + (logical to usb)))
                    },
                )
                MixerSettingsPage.OutputMatrix -> RoutingMatrix(
                    title = "Tap a cell to route playback to USB outputs. Some mixers expose fewer playback channels than inputs (e.g. Flow 8: 10 in, 4 out).",
                    logicalCount = logicalCount,
                    usbCount = usbOutputCount,
                    stripByIndex = stripByIndex,
                    selectedUsb = { logical -> draft.outputTarget(logical) },
                    onSelect = { logical, usb ->
                        persist(draft.copy(outputMap = draft.outputMap + (logical to usb)))
                    },
                )
                MixerSettingsPage.HiddenSoundcheck -> HiddenChannelsPage(
                    logicalCount = logicalCount,
                    stripByIndex = stripByIndex,
                    hidden = draft.hiddenSoundcheck,
                    onChange = { hidden -> persist(draft.copy(hiddenSoundcheck = hidden)) },
                )
                MixerSettingsPage.HiddenRecord -> HiddenChannelsPage(
                    logicalCount = logicalCount,
                    stripByIndex = stripByIndex,
                    hidden = draft.hiddenRecord,
                    onChange = { hidden -> persist(draft.copy(hiddenRecord = hidden)) },
                )
            }
        }
    }
}

private fun pageTitle(page: MixerSettingsPage): String = when (page) {
    MixerSettingsPage.Menu -> "Mixer settings"
    MixerSettingsPage.Monitor -> "Monitor output"
    MixerSettingsPage.InputMatrix -> "Input matrix"
    MixerSettingsPage.OutputMatrix -> "Output matrix"
    MixerSettingsPage.HiddenSoundcheck -> "Hidden channels (soundcheck)"
    MixerSettingsPage.HiddenRecord -> "Hidden channels (recording mode)"
}

@Composable
private fun SettingsMenu(
    onMonitor: () -> Unit,
    onInputMatrix: () -> Unit,
    onOutputMatrix: () -> Unit,
    onHiddenSoundcheck: () -> Unit,
    onHiddenRecord: () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        SettingsMenuItem("Monitor output", onMonitor)
        SettingsMenuItem("Input matrix", onInputMatrix)
        SettingsMenuItem("Output matrix", onOutputMatrix)
        SettingsMenuItem("Hidden channels (soundcheck)", onHiddenSoundcheck)
        SettingsMenuItem("Hidden channels (recording mode)", onHiddenRecord)
    }
}

@Composable
private fun SettingsMenuItem(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

@Composable
private fun MonitorSettingsPage(
    appMode: AppMode?,
    isMonitoring: Boolean,
    monitorEnabled: Boolean,
    outputDevices: List<LabeledAudioDevice>,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
    onSetMonitorOutput: (Int) -> Unit,
) {
    var deviceMenuOpen by remember { mutableStateOf(false) }
    val recordMode = appMode == AppMode.MULTITRACK_RECORD

    Text(
        "Route a local output device for low-latency cue monitoring while recording.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    if (!recordMode) {
        Text(
            "Monitoring is only available in recording mode.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    OutlinedButton(
        onClick = { if (isMonitoring) onStopMonitor() else onStartMonitor() },
        enabled = monitorEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Default.Headphones,
            contentDescription = null,
            tint = if (isMonitoring) MonitorBlue else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (isMonitoring) "Stop monitoring" else "Start monitoring",
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        OutlinedButton(
            onClick = { deviceMenuOpen = true },
            enabled = outputDevices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Monitor output device", modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = deviceMenuOpen, onDismissRequest = { deviceMenuOpen = false }) {
            outputDevices.forEach { labeled ->
                DropdownMenuItem(
                    text = { Text(labeled.label, maxLines = 2) },
                    onClick = {
                        deviceMenuOpen = false
                        onSetMonitorOutput(labeled.device.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RoutingMatrix(
    title: String,
    logicalCount: Int,
    usbCount: Int,
    stripByIndex: Map<Int, ChannelStripState>,
    selectedUsb: (Int) -> Int,
    onSelect: (logical: Int, usb: Int) -> Unit,
) {
    val usbScroll = rememberScrollState()

    Text(
        title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )

    Row(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .width(LabelColumnWidth)
                .padding(end = MatrixGap),
        ) {
            Box(
                Modifier.height(MatrixCellSize),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column {
                    Text(
                        "Channel",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "USB →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(MatrixGap)) {
                repeat(logicalCount) { logical ->
                    val strip = stripByIndex[logical] ?: ChannelStripState(index = logical)
                    ChannelStripLabel(
                        strip = strip,
                        modifier = Modifier.height(MatrixCellSize),
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .horizontalScroll(usbScroll),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MatrixGap)) {
                repeat(usbCount) { usb ->
                    Box(
                        Modifier.size(MatrixCellSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            formatUsbColumn(usb + 1),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(top = MatrixGap),
                verticalArrangement = Arrangement.spacedBy(MatrixGap),
            ) {
                repeat(logicalCount) { logical ->
                    val mappedUsb = selectedUsb(logical)
                    Row(horizontalArrangement = Arrangement.spacedBy(MatrixGap)) {
                        repeat(usbCount) { usb ->
                            MatrixCell(
                                selected = mappedUsb == usb,
                                onClick = { onSelect(logical, usb) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatUsbColumn(index: Int): String =
    if (index < 10) "0$index" else index.toString()

@Composable
private fun MatrixCell(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .size(MatrixCellSize)
            .clip(shape)
            .background(bg)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = if (selected) 0f else 0.25f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text("●", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ChannelStripLabel(
    strip: ChannelStripState,
    modifier: Modifier = Modifier,
) {
    val name = channelDisplayName(strip)
    val iconEmoji = MixingStationIcons.emoji(strip.iconId)
    val barColor = Color(strip.colorArgb)

    Row(
        modifier = modifier.widthIn(max = LabelColumnWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor),
        )
        if (iconEmoji != null) {
            Text(iconEmoji, fontSize = 14.sp)
        }
        Column {
            Text(
                formatChannelLabel(strip.index),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatChannelLabel(index: Int): String =
    "Channel %02d".format(index + 1)

@Composable
private fun HiddenChannelsPage(
    logicalCount: Int,
    stripByIndex: Map<Int, ChannelStripState>,
    hidden: Set<Int>,
    onChange: (Set<Int>) -> Unit,
) {
    Text(
        "Checked channels are hidden in this mode.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    repeat(logicalCount) { logical ->
        val strip = stripByIndex[logical] ?: ChannelStripState(index = logical)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onChange(if (logical in hidden) hidden - logical else hidden + logical)
                }
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = logical in hidden,
                onCheckedChange = { checked ->
                    onChange(if (checked) hidden + logical else hidden - logical)
                },
            )
            ChannelStripLabel(strip = strip)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    }
}

private fun channelDisplayName(strip: ChannelStripState): String {
    val parsed = strip.displayName.ifBlank { ScribbleStripLabel.parse(strip.label).displayName }
    return parsed.ifBlank { "Channel ${strip.index + 1}" }
}
