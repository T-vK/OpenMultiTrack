package org.openmultitrack.app.ui.daw

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerRoutingConfig
import org.openmultitrack.mixer.behringer.MixingStationIcons
import org.openmultitrack.mixer.behringer.ScribbleStripLabel

private val RecordRed = Color(0xFFE53935)
private val MonitorBlue = Color(0xFF1E88E5)
private val SoloAmber = Color(0xFFFFB300)
private val VuGreen = Color(0xFF43A047)
private val VuYellow = Color(0xFFFFB300)
private val VuRed = Color(0xFFE53935)

val StripVuMeterWidth = 5.dp

enum class StripControlMode {
    RECORD,
    PLAYBACK,
}

@Composable
internal fun StripVuMeter(
    level: Float,
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp = StripVuMeterWidth,
) {
    val target = level.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 60),
        label = "stripVu",
    )
    val fillColor = when {
        animated >= 0.92f -> VuRed
        animated >= 0.72f -> VuYellow
        else -> VuGreen
    }
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
    ) {
        if (animated > 0.005f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animated)
                    .align(Alignment.BottomCenter)
                    .background(fillColor.copy(alpha = 0.92f)),
            )
        }
    }
}

/** Peak level from the newest samples in a live waveform ring buffer. */
internal fun liveVuLevel(
    waveform: org.openmultitrack.app.audio.LiveWaveformSnapshot?,
    normalized: Boolean,
): Float {
    val peaks = waveform?.peaks ?: return 0f
    val count = waveform.peakCount
    if (count <= 0) return 0f
    val from = (count - 3).coerceAtLeast(0)
    var raw = 0f
    for (i in from until count) {
        raw = maxOf(raw, peaks[i])
    }
    return scalePeaksForDisplay(floatArrayOf(raw), normalized).first()
}

private val ColorBarIconGap = 12.dp

private fun iconContainerSize(colorBarHeight: Dp): Dp =
    (colorBarHeight * 0.68f).coerceIn(24.dp, 44.dp)

@Composable
internal fun stripLabelColumnWidth(
    strips: List<ChannelStripState>,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    labelFontSize: Float,
    stripHeight: Dp,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    controlMode: StripControlMode = StripControlMode.RECORD,
    hideRoutingBadges: Boolean = false,
): Dp {
    if (numberMode == StripNumberMode.NUMBERS_ONLY) return 28.dp
    val hasLabels = strips.any { it.displayName.isNotBlank() || it.label.isNotBlank() }
    if (!hasLabels && iconMode == StripIconMode.HIDE) return 28.dp

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = TextStyle(fontSize = labelFontSize.sp, fontWeight = FontWeight.Normal)
    val maxTextPx = strips.maxOfOrNull { strip ->
        val line = buildStripLine(strip, numberMode, iconMode)
        if (line.isEmpty()) 0 else textMeasurer.measure(line, textStyle).size.width
    } ?: 0

    val showsIcon = iconMode != StripIconMode.HIDE &&
        strips.any { MixingStationIcons.emoji(it.iconId) != null }
    val innerPad = (stripHeight * 0.12f).coerceIn(3.dp, 10.dp)
    val colorBarHeight = stripHeight - innerPad * 2
    val iconGap = 8.dp
    val bigIconWidth = if (showsIcon) iconContainerSize(colorBarHeight) else 0.dp
    val showsGlyphs = when (controlMode) {
        StripControlMode.RECORD -> !hideArm || !hideMonitor || !hideSolo
        StripControlMode.PLAYBACK -> !hideSolo
    }
    val glyphWidth = if (showsGlyphs) {
        with(density) {
            when (controlMode) {
                StripControlMode.RECORD -> (labelFontSize * 3.6f).sp.toDp()
                StripControlMode.PLAYBACK -> (labelFontSize * 2.4f).sp.toDp()
            }
        }
    } else {
        0.dp
    }
    val badgeWidth = if (!hideRoutingBadges) 30.dp else 0.dp
    val labelWidth = with(density) { maxTextPx.toDp() }

    val colorBarGap = if (showsIcon) ColorBarIconGap else 0.dp
    return (3.dp + colorBarGap + bigIconWidth + iconGap +
        maxOf(labelWidth, glyphWidth + badgeWidth) + 14.dp)
        .coerceAtLeast(36.dp)
}

@Composable
internal fun StripIdentityCell(
    strip: ChannelStripState,
    columnWidth: Dp,
    labelFontSize: Float,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    colorBarHeight: Dp,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    routing: MixerRoutingConfig,
    soundcheckMode: Boolean,
    hideRoutingBadges: Boolean,
    controlMode: StripControlMode = StripControlMode.RECORD,
    onClick: () -> Unit,
) {
    val parsed = remember(strip.label, strip.displayName, strip.iconId, numberMode, iconMode) {
        StripTextParts(strip, numberMode, iconMode)
    }
    val iconEmoji = parsed.iconEmoji
    val lineText = parsed.lineText
    val iconGap = 8.dp
    val bigIconSize = iconContainerSize(colorBarHeight)
    val controlIconSize = (labelFontSize * 0.95f).coerceIn(10f, 16f).dp
    val colorBarWidth = 3.dp
    val colorBarGap = if (iconEmoji != null) ColorBarIconGap else 0.dp
    val iconColumnWidth = if (iconEmoji != null) bigIconSize else 0.dp
    val textAreaWidth = (columnWidth - colorBarWidth - colorBarGap - iconColumnWidth - iconGap - 2.dp)
        .coerceAtLeast(24.dp)

    Row(
        modifier = Modifier
            .width(columnWidth)
            .height(colorBarHeight)
            .clickable(onClick = onClick)
            .padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(colorBarWidth)
                .height(colorBarHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(strip.colorArgb)),
        )
        if (iconEmoji != null) {
            Spacer(Modifier.width(colorBarGap))
            ScribbleIconEmoji(emoji = iconEmoji, containerSize = bigIconSize)
            Spacer(Modifier.width(iconGap))
        }
        Column(
            modifier = Modifier.width(textAreaWidth),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val showGlyphs = when (controlMode) {
                    StripControlMode.RECORD -> !hideArm || !hideMonitor || !hideSolo
                    StripControlMode.PLAYBACK -> !hideSolo
                }
                if (showGlyphs) {
                    StripStatusGlyphs(
                        strip = strip,
                        iconSize = controlIconSize,
                        hideArm = hideArm,
                        hideMonitor = hideMonitor,
                        hideSolo = hideSolo,
                        controlMode = controlMode,
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                if (!hideRoutingBadges) {
                    val usbIndex = if (soundcheckMode) {
                        routing.outputTarget(strip.index)
                    } else {
                        routing.inputSource(strip.index)
                    }
                    RoutingBadge(
                        usbIndex = usbIndex,
                        isOutput = soundcheckMode,
                        highlighted = usbIndex != strip.index,
                    )
                }
            }
            if (lineText.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    lineText,
                    fontSize = labelFontSize.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ScribbleIconEmoji(
    emoji: String,
    containerSize: Dp,
) {
    BoxWithConstraints(
        modifier = Modifier.size(containerSize),
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = with(LocalDensity.current) {
            minOf(maxWidth, maxHeight).times(0.82f).toSp()
        }
        Text(
            text = emoji,
            fontSize = fontSize,
            lineHeight = fontSize,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun StripStatusGlyphs(
    strip: ChannelStripState,
    iconSize: Dp,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    controlMode: StripControlMode,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        when (controlMode) {
            StripControlMode.RECORD -> {
                if (!hideArm) {
                    StripStatusIcon(
                        icon = Icons.Filled.FiberManualRecord,
                        active = strip.armed,
                        activeColor = RecordRed,
                        inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        iconSize = iconSize,
                        contentDescription = "Arm",
                    )
                }
                if (!hideMonitor) {
                    StripStatusIcon(
                        icon = Icons.Filled.Headphones,
                        active = strip.monitoring,
                        activeColor = MonitorBlue,
                        inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        iconSize = iconSize,
                        contentDescription = "Monitor",
                    )
                }
                if (!hideSolo) {
                    StripSoloStatusGlyph(
                        active = strip.solo,
                        activeColor = SoloAmber,
                        inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        size = iconSize,
                    )
                }
            }
            StripControlMode.PLAYBACK -> {
                StripStatusIcon(
                    icon = Icons.Filled.VolumeOff,
                    active = strip.muted,
                    activeColor = MaterialTheme.colorScheme.error,
                    inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    iconSize = iconSize,
                    contentDescription = "Mute",
                )
                if (!hideSolo) {
                    StripSoloStatusGlyph(
                        active = strip.solo,
                        activeColor = SoloAmber,
                        inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        size = iconSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun StripSoloStatusGlyph(
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    size: Dp,
) {
    SoloCircledGlyph(
        tint = if (active) activeColor else inactiveColor,
        size = size,
        contentDescription = "Solo",
    )
}

@Composable
private fun SoloCircledGlyph(
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String = "Solo",
) {
    // Material icons have intrinsic padding; shrink the circle so "S" matches arm/monitor/mute weight.
    val innerSize = size * 0.78f
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .border(1.dp, tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "S",
                color = tint,
                fontSize = (innerSize.value * 0.58f).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (innerSize.value * 0.58f).sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StripStatusIcon(
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    iconSize: Dp,
    contentDescription: String,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(iconSize),
        tint = if (active) activeColor else inactiveColor,
    )
}

@Composable
private fun RoutingBadge(
    usbIndex: Int,
    isOutput: Boolean,
    highlighted: Boolean,
) {
    val prefix = if (isOutput) "OUT" else "IN"
    val bg = if (highlighted) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    }
    val textColor = if (highlighted) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = bg,
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = if (highlighted) 0.35f else 0.18f),
        ),
    ) {
        Text(
            "$prefix${usbIndex + 1}",
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.dp),
            fontSize = 7.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            lineHeight = 8.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsbChannelDropdown(
    label: String,
    selectedUsbIndex: Int,
    usbChannelCount: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val count = usbChannelCount.coerceAtLeast(1)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = "USB ${selectedUsbIndex + 1}",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            repeat(count) { usb ->
                DropdownMenuItem(
                    text = { Text("USB ${usb + 1}") },
                    onClick = {
                        onSelect(usb)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun ChannelStripControlDialog(
    strip: ChannelStripState,
    routing: MixerRoutingConfig,
    usbChannelCount: Int,
    usbPlaybackChannelCount: Int = usbChannelCount,
    controlMode: StripControlMode = StripControlMode.RECORD,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    onDismiss: () -> Unit,
    onArm: () -> Unit = {},
    onMonitor: () -> Unit = {},
    onSolo: () -> Unit = {},
    onMute: () -> Unit = {},
    onInputSourceChange: (Int) -> Unit,
    onOutputTargetChange: (Int) -> Unit,
    onHiddenRecordChange: (Boolean) -> Unit,
    onHiddenSoundcheckChange: (Boolean) -> Unit,
    hidePlaybackRouting: Boolean = false,
) {
    val hiddenRecord = strip.index in routing.hiddenRecord
    val hiddenSoundcheck = strip.index in routing.hiddenSoundcheck
    val parsed = StripTextParts(
        strip,
        StripNumberMode.BOTH,
        StripIconMode.SHOW,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(6.dp, 28.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(strip.colorArgb)),
                )
                Column {
                    Text("Channel ${strip.index + 1}", style = MaterialTheme.typography.titleMedium)
                    val subtitle = buildString {
                        parsed.iconEmoji?.let { append("$it ") }
                        val name = strip.displayName.ifBlank { strip.label }
                        if (name.isNotBlank()) append(name)
                    }
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Channel controls",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (controlMode) {
                        StripControlMode.RECORD -> {
                            if (!hideArm) {
                                OverlayChannelToggle(
                                    checked = strip.armed,
                                    icon = Icons.Filled.FiberManualRecord,
                                    activeColor = RecordRed,
                                    iconTintWhenOff = RecordRed,
                                    label = "Arm",
                                    onClick = onArm,
                                )
                            }
                            if (!hideMonitor) {
                                OverlayChannelToggle(
                                    checked = strip.monitoring,
                                    icon = Icons.Filled.Headphones,
                                    activeColor = MonitorBlue,
                                    iconTintWhenOff = MaterialTheme.colorScheme.onSurfaceVariant,
                                    label = "Monitor",
                                    onClick = onMonitor,
                                )
                            }
                            if (!hideSolo) {
                                OverlaySoloToggle(
                                    checked = strip.solo,
                                    activeColor = SoloAmber,
                                    iconTintWhenOff = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = onSolo,
                                )
                            }
                        }
                        StripControlMode.PLAYBACK -> {
                            OverlayChannelToggle(
                                checked = strip.muted,
                                icon = Icons.Filled.VolumeOff,
                                activeColor = MaterialTheme.colorScheme.error,
                                iconTintWhenOff = MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "Mute",
                                onClick = onMute,
                            )
                            if (!hideSolo) {
                                OverlaySoloToggle(
                                    checked = strip.solo,
                                    activeColor = SoloAmber,
                                    iconTintWhenOff = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = onSolo,
                                )
                            }
                        }
                    }
                }
                if (controlMode == StripControlMode.RECORD) {
                    Text(
                        "Routing",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    UsbChannelDropdown(
                        label = "USB input (recording mode)",
                        selectedUsbIndex = routing.inputSource(strip.index),
                        usbChannelCount = usbChannelCount,
                        onSelect = onInputSourceChange,
                    )
                }
                if (controlMode == StripControlMode.PLAYBACK && !hidePlaybackRouting) {
                    Text(
                        "Routing",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    UsbChannelDropdown(
                        label = "USB output (playback)",
                        selectedUsbIndex = routing.outputTarget(strip.index),
                        usbChannelCount = usbPlaybackChannelCount.coerceAtLeast(1),
                        onSelect = onOutputTargetChange,
                    )
                }
                if (controlMode == StripControlMode.PLAYBACK && hidePlaybackRouting) {
                    Text(
                        "Simple Play mixes all unmuted channels to USB outputs 1+2.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hiddenRecord, onCheckedChange = onHiddenRecordChange)
                    Text("Hide in recording mode")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hiddenSoundcheck, onCheckedChange = onHiddenSoundcheckChange)
                    Text("Hide in virtual soundcheck mode")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun OverlaySoloToggle(
    checked: Boolean,
    activeColor: Color,
    iconTintWhenOff: Color,
    onClick: () -> Unit,
) {
    val iconTint = if (checked) Color.White else iconTintWhenOff
    OverlayChannelToggle(
        checked = checked,
        activeColor = activeColor,
        iconTintWhenOff = iconTintWhenOff,
        label = "Solo",
        onClick = onClick,
    ) {
        SoloCircledGlyph(tint = iconTint, size = 24.dp)
    }
}

@Composable
private fun OverlayChannelToggle(
    checked: Boolean,
    icon: ImageVector,
    activeColor: Color,
    iconTintWhenOff: Color,
    label: String,
    onClick: () -> Unit,
) {
    OverlayChannelToggle(
        checked = checked,
        activeColor = activeColor,
        iconTintWhenOff = iconTintWhenOff,
        label = label,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (checked) Color.White else iconTintWhenOff,
        )
    }
}

@Composable
private fun OverlayChannelToggle(
    checked: Boolean,
    activeColor: Color,
    iconTintWhenOff: Color,
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit,
) {
    val bg = if (checked) activeColor else MaterialTheme.colorScheme.surfaceVariant
    val border = if (checked) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = bg,
            border = BorderStroke(1.dp, border),
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                iconContent()
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

private data class StripTextParts(
    val iconEmoji: String?,
    val lineText: String,
) {
    constructor(strip: ChannelStripState, numberMode: StripNumberMode, iconMode: StripIconMode) : this(
        iconEmoji = when (iconMode) {
            StripIconMode.HIDE -> null
            else -> MixingStationIcons.emoji(strip.iconId)
        },
        lineText = buildStripLine(strip, numberMode, iconMode),
    )
}

private fun buildStripLine(
    strip: ChannelStripState,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
): String {
    val number = "${strip.index + 1}"
    val name = strip.displayName.ifBlank { ScribbleStripLabel.parse(strip.label).displayName }
    val hasLabel = name.isNotBlank()

    return when (numberMode) {
        StripNumberMode.NUMBERS_ONLY -> number
        StripNumberMode.HIDE_WHEN_LABELED -> when {
            hasLabel && iconMode != StripIconMode.ICON_ONLY -> name
            hasLabel -> ""
            else -> number
        }
        StripNumberMode.BOTH -> when {
            iconMode == StripIconMode.ICON_ONLY && hasLabel -> number
            hasLabel -> "$number $name"
            else -> number
        }
    }
}
