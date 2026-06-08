package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.service.SoundcheckSessionItem
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.wav.SessionWaveformOverview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val PlayheadColor = Color(0xFFE53935)
private val LoopFillColor = Color(0xFF1E88E5)
private val LoopHandleColor = Color(0xFF64B5F6)
private val MinStripHeight = 44.dp
private val ScrollStripHeight = 72.dp
private val TimeRulerHeight = 22.dp

@Composable
fun SoundcheckPanel(
    session: MixerSessionUiState,
    normalized: Boolean,
    showWaveforms: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    onSelectSession: (String) -> Unit,
    onSeek: (Float) -> Unit,
    onPanView: (Float) -> Unit,
    onZoomView: (Float, Float) -> Unit,
    onSetLoopRegion: (Float, Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        SoundcheckSessionSelector(
            sessions = session.soundcheckSessions,
            selectedDir = session.selectedSoundcheckDir,
            onSelectSession = onSelectSession,
        )
        Spacer(Modifier.height(6.dp))
        when {
            session.soundcheckSessions.isEmpty() -> SoundcheckEmptyState()
            session.selectedSoundcheckDir == null -> SoundcheckEmptyState("Select a session to preview.")
            session.soundcheckWaveformsLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            session.soundcheckWaveforms != null -> SoundcheckWaveformStripList(
                session = session,
                overview = session.soundcheckWaveforms,
                normalized = normalized,
                showWaveforms = showWaveforms,
                numberMode = numberMode,
                iconMode = iconMode,
                hideArm = hideArm,
                hideMonitor = hideMonitor,
                hideSolo = hideSolo,
                onSeek = onSeek,
                onPanView = onPanView,
                onZoomView = onZoomView,
                onSetLoopRegion = onSetLoopRegion,
            )
            else -> SoundcheckEmptyState("Could not load waveforms for this session.")
        }
    }
}

@Composable
private fun SoundcheckSessionSelector(
    sessions: List<SoundcheckSessionItem>,
    selectedDir: String?,
    onSelectSession: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selected = sessions.firstOrNull { it.sessionDir == selectedDir }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = sessions.isNotEmpty()) { menuOpen = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        selected?.title ?: "Select multitrack recording",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            selected == null -> "Tap to choose a completed session"
                            else -> {
                                val date = dateFmt.format(Date(selected.startedAtEpochMs))
                                "$date · ${selected.channelCount} ch · ${formatDuration(selected.durationSec)}"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Open session list")
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            sessions.forEach { item ->
                val date = dateFmt.format(Date(item.startedAtEpochMs))
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "$date · ${item.channelCount} ch · ${formatDuration(item.durationSec)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        menuOpen = false
                        onSelectSession(item.sessionDir)
                    },
                )
            }
        }
    }
}

@Composable
private fun SoundcheckEmptyState(message: String = "No completed sessions yet. Record in Recording mode first.") {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SoundcheckWaveformStripList(
    session: MixerSessionUiState,
    overview: SessionWaveformOverview,
    normalized: Boolean,
    showWaveforms: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    onSeek: (Float) -> Unit,
    onPanView: (Float) -> Unit,
    onZoomView: (Float, Float) -> Unit,
    onSetLoopRegion: (Float, Float) -> Unit,
) {
    val strips = session.channelStrips.filter { overview.peaksByChannel.containsKey(it.index) }
    if (strips.isEmpty()) {
        SoundcheckEmptyState("No channel data in this session.")
        return
    }
    val viewStart = session.soundcheckViewStartSec
    val viewWindow = session.soundcheckViewWindowSec
    val duration = session.playbackDurationSec
    val playheadSec = session.playbackPositionSec
    val loopStart = session.soundcheckLoopStartSec
    val loopEnd = session.soundcheckLoopEndSec
    val loopSelecting = session.soundcheckLoopSelecting
    val loopEnabled = session.soundcheckLoopEnabled

    var dragLoopStart by remember { mutableFloatStateOf(0f) }
    var dragLoopEnd by remember { mutableFloatStateOf(0f) }
    var loopDragActive by remember { mutableStateOf(false) }
    var panAccum by remember { mutableFloatStateOf(0f) }
    var draggedHandle by remember { mutableStateOf<LoopHandle?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val count = strips.size.coerceAtLeast(1)
        val gap = 2.dp
        val totalGaps = gap * (count - 1).coerceAtLeast(0)
        val rulerAndGap = TimeRulerHeight + 4.dp
        val naturalHeight = (maxHeight - rulerAndGap - totalGaps) / count
        val useScroll = naturalHeight < MinStripHeight
        val stripHeight = if (useScroll) ScrollStripHeight else naturalHeight
        val innerPad = (stripHeight * 0.12f).coerceIn(3.dp, 10.dp)
        val labelFontSize = (stripHeight.value * 0.30f).coerceIn(10f, 16f)
        val labelColumnWidth = stripLabelColumnWidth(
            strips,
            numberMode,
            iconMode,
            labelFontSize,
            stripHeight,
            hideArm,
            hideMonitor,
            hideSolo,
        )
        val density = LocalDensity.current
        val waveformWidthPx = with(density) { (maxWidth - labelColumnWidth - innerPad * 2).toPx() }

        val listModifier = if (useScroll) {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = listModifier, verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(TimeRulerHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(labelColumnWidth + innerPad))
                    Text(
                        formatDuration(viewWindow),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp),
                    )
                    Box(modifier = Modifier.weight(1f).height(TimeRulerHeight)) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            drawTimeRuler(
                                viewStartSec = viewStart,
                                viewWindowSec = viewWindow,
                                playheadSec = playheadSec,
                                loopStartSec = loopStart,
                                loopEndSec = loopEnd,
                                loopEnabled = loopEnabled,
                            )
                        }
                    }
                }
                strips.forEach { strip ->
                    SoundcheckStripRow(
                        strip = strip,
                        stripHeight = stripHeight,
                        innerPad = innerPad,
                        labelFontSize = labelFontSize,
                        labelColumnWidth = labelColumnWidth,
                        overview = overview,
                        viewStartSec = viewStart,
                        viewWindowSec = viewWindow,
                        playheadSec = playheadSec,
                        loopStartSec = loopStart,
                        loopEndSec = loopEnd,
                        loopEnabled = loopEnabled,
                        normalized = normalized,
                        showWaveform = showWaveforms,
                        numberMode = numberMode,
                        iconMode = iconMode,
                        hideArm = hideArm,
                        hideMonitor = hideMonitor,
                        hideSolo = hideSolo,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = labelColumnWidth + innerPad * 2)
                    .pointerInput(
                        viewStart,
                        viewWindow,
                        duration,
                        loopSelecting,
                        loopStart,
                        loopEnd,
                        waveformWidthPx,
                    ) {
                        detectTransformGestures { centroid, _, zoom, _ ->
                            if (zoom != 1f && waveformWidthPx > 0f) {
                                val focalSec = viewStart + (centroid.x / waveformWidthPx) * viewWindow
                                onZoomView(zoom, focalSec)
                            }
                        }
                    }
                    .pointerInput(
                        viewStart,
                        viewWindow,
                        duration,
                        loopSelecting,
                        loopStart,
                        loopEnd,
                        waveformWidthPx,
                    ) {
                        var totalDragX = 0f
                        var loopAnchor: Float? = null
                        detectDragGestures(
                            onDragStart = { offset ->
                                totalDragX = 0f
                                panAccum = 0f
                                loopDragActive = false
                                loopAnchor = null
                                if (waveformWidthPx <= 0f) return@detectDragGestures
                                val sec = viewStart + (offset.x / waveformWidthPx) * viewWindow
                                draggedHandle = when {
                                    loopStart != null && loopEnd != null &&
                                        abs(sec - loopStart) * waveformWidthPx / viewWindow < 24f ->
                                        LoopHandle.START
                                    loopStart != null && loopEnd != null &&
                                        abs(sec - loopEnd) * waveformWidthPx / viewWindow < 24f ->
                                        LoopHandle.END
                                    else -> null
                                }
                                if (draggedHandle == null && loopSelecting) {
                                    loopAnchor = sec.coerceIn(0f, duration)
                                    dragLoopStart = loopAnchor!!
                                    dragLoopEnd = loopAnchor!!
                                    loopDragActive = true
                                }
                            },
                            onDrag = { change, dragAmount ->
                                totalDragX += dragAmount.x
                                if (waveformWidthPx <= 0f) return@detectDragGestures
                                val secDelta = (dragAmount.x / waveformWidthPx) * viewWindow
                                when (draggedHandle) {
                                    LoopHandle.START -> {
                                        val end = loopEnd ?: duration
                                        onSetLoopRegion(
                                            (loopStart!! + secDelta).coerceIn(0f, end - 0.1f),
                                            end,
                                        )
                                    }
                                    LoopHandle.END -> {
                                        val start = loopStart ?: 0f
                                        onSetLoopRegion(
                                            start,
                                            (loopEnd!! + secDelta).coerceIn(start + 0.1f, duration),
                                        )
                                    }
                                    null -> when {
                                        loopDragActive && loopAnchor != null -> {
                                            val sec = viewStart + (change.position.x / waveformWidthPx) * viewWindow
                                            dragLoopEnd = sec.coerceIn(0f, duration)
                                            onSetLoopRegion(dragLoopStart, dragLoopEnd)
                                        }
                                        abs(totalDragX) > 8f -> {
                                            panAccum += secDelta
                                            onPanView(secDelta)
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedHandle = null
                                loopDragActive = false
                                loopAnchor = null
                            },
                        )
                    }
                    .pointerInput(viewStart, viewWindow, duration, loopSelecting, waveformWidthPx) {
                        detectTapGestures { offset ->
                            if (waveformWidthPx <= 0f) return@detectTapGestures
                            val sec = (viewStart + (offset.x / waveformWidthPx) * viewWindow)
                                .coerceIn(0f, duration)
                            if (!loopSelecting) {
                                onSeek(sec)
                            } else {
                                onSetLoopRegion(sec, (sec + 4f).coerceAtMost(duration))
                            }
                        }
                    },
            )
        }
    }
}

private enum class LoopHandle { START, END }

@Composable
private fun SoundcheckStripRow(
    strip: ChannelStripState,
    stripHeight: androidx.compose.ui.unit.Dp,
    innerPad: androidx.compose.ui.unit.Dp,
    labelFontSize: Float,
    labelColumnWidth: androidx.compose.ui.unit.Dp,
    overview: SessionWaveformOverview,
    viewStartSec: Float,
    viewWindowSec: Float,
    playheadSec: Float,
    loopStartSec: Float?,
    loopEndSec: Float?,
    loopEnabled: Boolean,
    normalized: Boolean,
    showWaveform: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
) {
    val colorBarHeight = stripHeight - innerPad * 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = innerPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(innerPad / 2),
    ) {
        StripIdentityCell(
            strip = strip,
            columnWidth = labelColumnWidth,
            labelFontSize = labelFontSize,
            numberMode = numberMode,
            iconMode = iconMode,
            colorBarHeight = colorBarHeight,
            hideArm = hideArm,
            hideMonitor = hideMonitor,
            hideSolo = hideSolo,
            onClick = {},
        )
        if (showWaveform) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(colorBarHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val peaks = visiblePeaksForViewport(
                        overview = overview,
                        channelIndex = strip.index,
                        viewStartSec = viewStartSec,
                        viewWindowSec = viewWindowSec,
                        pixelWidth = size.width.toInt().coerceAtLeast(1),
                    )
                    drawSoundcheckWaveform(
                        peaks = peaks,
                        color = Color(strip.colorArgb),
                        normalized = normalized,
                    )
                    drawPlayheadAndLoop(
                        viewStartSec = viewStartSec,
                        viewWindowSec = viewWindowSec,
                        playheadSec = playheadSec,
                        loopStartSec = loopStartSec,
                        loopEndSec = loopEndSec,
                        loopEnabled = loopEnabled,
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeRuler(
    viewStartSec: Float,
    viewWindowSec: Float,
    playheadSec: Float,
    loopStartSec: Float?,
    loopEndSec: Float?,
    loopEnabled: Boolean,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || viewWindowSec <= 0f) return
    val tickStep = chooseTickStepSec(viewWindowSec)
    var t = (viewStartSec / tickStep).toInt() * tickStep
    if (t < viewStartSec) t += tickStep
    val textPaint = androidx.compose.ui.graphics.Paint().asFrameworkPaint().apply {
        color = android.graphics.Color.GRAY
        textSize = 10f * density
        isAntiAlias = true
    }
    while (t <= viewStartSec + viewWindowSec) {
        val x = ((t - viewStartSec) / viewWindowSec) * w
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = Offset(x, h * 0.55f),
            end = Offset(x, h),
            strokeWidth = 1f,
        )
        drawContext.canvas.nativeCanvas.drawText(
            formatRulerTime(t),
            x + 2f,
            h * 0.5f,
            textPaint,
        )
        t += tickStep
    }
    drawPlayheadAndLoop(
        viewStartSec = viewStartSec,
        viewWindowSec = viewWindowSec,
        playheadSec = playheadSec,
        loopStartSec = loopStartSec,
        loopEndSec = loopEndSec,
        loopEnabled = loopEnabled,
        drawHandles = false,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoundcheckWaveform(
    peaks: FloatArray,
    color: Color,
    normalized: Boolean,
) {
    if (peaks.isEmpty()) return
    val display = scalePeaksForDisplay(peaks, normalized)
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val slotWidth = w / display.size
    val stroke = (slotWidth * 0.75f).coerceIn(1f, 4f)
    val minBar = h * 0.06f
    display.forEachIndexed { i, peak ->
        val amp = peak.coerceIn(0f, 1f)
        val barH = maxOf(amp * h * 0.9f, if (amp > 0.02f) minBar else 0f)
        val x = (i + 0.5f) * slotWidth
        drawLine(
            color = color.copy(alpha = 0.9f),
            start = Offset(x, mid - barH / 2f),
            end = Offset(x, mid + barH / 2f),
            strokeWidth = stroke,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlayheadAndLoop(
    viewStartSec: Float,
    viewWindowSec: Float,
    playheadSec: Float,
    loopStartSec: Float?,
    loopEndSec: Float?,
    loopEnabled: Boolean,
    drawHandles: Boolean = true,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || viewWindowSec <= 0f) return
    fun secToX(sec: Float): Float = ((sec - viewStartSec) / viewWindowSec) * w

    if (loopStartSec != null && loopEndSec != null && loopEndSec > loopStartSec) {
        val x0 = secToX(loopStartSec).coerceIn(0f, w)
        val x1 = secToX(loopEndSec).coerceIn(0f, w)
        if (x1 > x0) {
            drawRect(
                color = LoopFillColor.copy(alpha = if (loopEnabled) 0.22f else 0.12f),
                topLeft = Offset(x0, 0f),
                size = androidx.compose.ui.geometry.Size(x1 - x0, h),
            )
            if (drawHandles) {
                drawLine(LoopHandleColor, Offset(x0, 0f), Offset(x0, h), strokeWidth = 3f)
                drawLine(LoopHandleColor, Offset(x1, 0f), Offset(x1, h), strokeWidth = 3f)
            }
        }
    }
    if (playheadSec in viewStartSec..(viewStartSec + viewWindowSec)) {
        val px = secToX(playheadSec)
        drawLine(
            color = PlayheadColor,
            start = Offset(px, 0f),
            end = Offset(px, h),
            strokeWidth = 2f,
        )
    }
}

internal fun visiblePeaksForViewport(
    overview: SessionWaveformOverview,
    channelIndex: Int,
    viewStartSec: Float,
    viewWindowSec: Float,
    pixelWidth: Int,
): FloatArray {
    val peaks = overview.peaksByChannel[channelIndex] ?: return FloatArray(0)
    if (peaks.isEmpty() || viewWindowSec <= 0f || pixelWidth <= 0) return FloatArray(0)
    val pps = overview.peaksPerSec
    val startIdx = (viewStartSec * pps).toInt().coerceIn(0, peaks.size)
    val endIdx = ((viewStartSec + viewWindowSec) * pps).toInt().coerceIn(startIdx, peaks.size)
    if (endIdx <= startIdx) return FloatArray(0)
    val slice = peaks.copyOfRange(startIdx, endIdx)
    return if (slice.size <= pixelWidth) slice else downsamplePeaksMax(slice, pixelWidth)
}

private fun downsamplePeaksMax(source: FloatArray, targetCount: Int): FloatArray {
    if (source.size <= targetCount) return source
    val out = FloatArray(targetCount)
    val bucketSize = source.size.toFloat() / targetCount
    for (i in 0 until targetCount) {
        val start = (i * bucketSize).toInt()
        val end = ((i + 1) * bucketSize).toInt().coerceAtMost(source.size)
        var maxVal = 0f
        for (j in start until end) maxVal = max(maxVal, source[j])
        out[i] = maxVal
    }
    return out
}

private fun chooseTickStepSec(viewWindowSec: Float): Float = when {
    viewWindowSec <= 60f -> 5f
    viewWindowSec <= 180f -> 15f
    viewWindowSec <= 600f -> 60f
    else -> 120f
}

private fun formatRulerTime(sec: Float): String {
    val total = sec.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

internal fun formatDuration(sec: Float): String = formatRulerTime(sec)

/** USB peaks are often very small floats; scale for display so waveforms stay visible. */
internal fun scalePeaksForDisplay(peaks: FloatArray, normalized: Boolean): FloatArray {
    if (peaks.isEmpty()) return peaks
    val maxPeak = peaks.max()
    if (maxPeak <= 1e-6f) return peaks
    if (normalized) {
        return FloatArray(peaks.size) { i -> (peaks[i] / maxPeak).coerceIn(0f, 1f) }
    }
    val gain = if (maxPeak < 0.15f) 1f / maxPeak else 1f
    return FloatArray(peaks.size) { i -> (peaks[i] * gain).coerceIn(0f, 1f) }
}
