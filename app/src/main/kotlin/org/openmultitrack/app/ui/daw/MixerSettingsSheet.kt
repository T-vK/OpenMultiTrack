package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import org.openmultitrack.mixer.behringer.MixingStationIcons
import org.openmultitrack.mixer.behringer.ScribbleStripLabel

private sealed interface MixerSettingsPage {
    data object Menu : MixerSettingsPage
    data object InputMatrix : MixerSettingsPage
    data object OutputMatrix : MixerSettingsPage
    data object HiddenSoundcheck : MixerSettingsPage
    data object HiddenRecord : MixerSettingsPage
}

private val LabelColumnWidth = 148.dp
private val MatrixCellSize = 40.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerSettingsSheet(
    mixerName: String,
    channelCount: Int,
    usbChannelCount: Int,
    strips: List<ChannelStripState>,
    config: MixerRoutingConfig,
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
    val usbCount = maxOf(
        usbChannelCount,
        logicalCount,
        (draft.inputMap.values.maxOrNull() ?: 0) + 1,
        (draft.outputMap.values.maxOrNull() ?: 0) + 1,
    ).coerceAtLeast(1)

    val stripByIndex = strips.associateBy { it.index }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
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
                    onInputMatrix = { page = MixerSettingsPage.InputMatrix },
                    onOutputMatrix = { page = MixerSettingsPage.OutputMatrix },
                    onHiddenSoundcheck = { page = MixerSettingsPage.HiddenSoundcheck },
                    onHiddenRecord = { page = MixerSettingsPage.HiddenRecord },
                )
                MixerSettingsPage.InputMatrix -> RoutingMatrix(
                    title = "Tap a cell to route each strip to a USB input.",
                    logicalCount = logicalCount,
                    usbCount = usbCount,
                    stripByIndex = stripByIndex,
                    selectedUsb = { logical -> draft.inputSource(logical) },
                    onSelect = { logical, usb ->
                        persist(draft.copy(inputMap = draft.inputMap + (logical to usb)))
                    },
                )
                MixerSettingsPage.OutputMatrix -> RoutingMatrix(
                    title = "Tap a cell to route each strip to a USB output.",
                    logicalCount = logicalCount,
                    usbCount = usbCount,
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
    MixerSettingsPage.InputMatrix -> "Input matrix"
    MixerSettingsPage.OutputMatrix -> "Output matrix"
    MixerSettingsPage.HiddenSoundcheck -> "Hidden channels (soundcheck)"
    MixerSettingsPage.HiddenRecord -> "Hidden channels (recording mode)"
}

@Composable
private fun SettingsMenu(
    onInputMatrix: () -> Unit,
    onOutputMatrix: () -> Unit,
    onHiddenSoundcheck: () -> Unit,
    onHiddenRecord: () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
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
        Box(Modifier.width(LabelColumnWidth).height(MatrixCellSize)) {
            Text(
                "Strip",
                modifier = Modifier.align(Alignment.CenterStart),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(usbScroll),
        ) {
            repeat(usbCount) { usb ->
                Box(
                    Modifier
                        .size(MatrixCellSize)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "USB ${usb + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 4.dp))

    repeat(logicalCount) { logical ->
        val strip = stripByIndex[logical] ?: ChannelStripState(index = logical)
        val mappedUsb = selectedUsb(logical)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelStripLabel(
                strip = strip,
                modifier = Modifier.width(LabelColumnWidth),
            )
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(usbScroll),
            ) {
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
            .padding(2.dp)
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
        modifier = modifier.padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor),
        )
        if (iconEmoji != null) {
            Text(iconEmoji, fontSize = 16.sp)
        }
        Column {
            Text(
                "${strip.index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

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
