package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.service.SoundcheckSessionItem
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerRoutingConfig
import org.openmultitrack.domain.session.AppMode
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
    routing: MixerRoutingConfig = MixerRoutingConfig(),
    normalized: Boolean,
    showWaveforms: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    hideRoutingBadges: Boolean,
    onSelectSession: (String) -> Unit,
    onSeek: (Float) -> Unit,
    onPanView: (Float) -> Unit,
    onZoomView: (Float, Float) -> Unit,
    onSetView: (Float, Float) -> Unit,
    onSetLoopRegion: (Float, Float) -> Unit,
    onOpenStripControls: (Int) -> Unit = {},
    onToggleMute: (Int) -> Unit = {},
    onToggleSolo: (Int) -> Unit = {},
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
        if (session.soundcheckWaveformsLoading) {
            LinearProgressIndicator(
                progress = { session.soundcheckWaveformProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
            if (session.soundcheckWaveformChannelsTotal > 0) {
                Text(
                    "Drawing waveforms ${session.soundcheckWaveformChannelsLoaded}/${session.soundcheckWaveformChannelsTotal}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
            } else {
                Spacer(Modifier.height(6.dp))
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }
        when {
            session.soundcheckSessions.isEmpty() -> SoundcheckEmptyState()
            session.selectedSoundcheckDir == null -> SoundcheckEmptyState("Select a session to preview.")
            session.channelStrips.isEmpty() -> SoundcheckEmptyState("Loading session channels…")
            else -> {
                val overview = session.soundcheckWaveforms ?: SessionWaveformOverview(
                    peaksByChannel = emptyMap(),
                    peaksPerSec = 4f,
                    durationSec = session.playbackDurationSec,
                )
                SoundcheckWaveformStripList(
                    strips = session.channelStrips.filter {
                        !routing.isHidden(it.index, soundcheckMode = true)
                    },
                    routing = routing,
                    appMode = session.appMode,
                    playbackChannelCount = session.playbackChannelCount,
                    durationSec = session.playbackDurationSec,
                    viewStartSec = session.soundcheckViewStartSec,
                    viewWindowSec = session.soundcheckViewWindowSec,
                    playheadSec = session.playbackPositionSec,
                    loopStartSec = session.soundcheckLoopStartSec,
                    loopEndSec = session.soundcheckLoopEndSec,
                    loopEnabled = session.soundcheckLoopEnabled,
                    loopSelecting = session.soundcheckLoopSelecting,
                    isPlaying = session.isPlaying,
                    meterLevels = session.soundcheckMeterLevels,
                    overview = overview,
                    normalized = normalized,
                    showWaveforms = showWaveforms,
                    numberMode = numberMode,
                    iconMode = iconMode,
                    hideArm = hideArm,
                    hideMonitor = hideMonitor,
                    hideSolo = hideSolo,
                    hideRoutingBadges = hideRoutingBadges,
                    onSeek = onSeek,
                    onSetView = onSetView,
                    onSetLoopRegion = onSetLoopRegion,
                    onOpenStripControls = onOpenStripControls,
                    onToggleMute = onToggleMute,
                    onToggleSolo = onToggleSolo,
                )
            }
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
                Column(modifier = Modifier.weight(1f)) {
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
    strips: List<ChannelStripState>,
    routing: MixerRoutingConfig,
    appMode: AppMode,
    playbackChannelCount: Int,
    durationSec: Float,
    viewStartSec: Float,
    viewWindowSec: Float,
    playheadSec: Float,
    loopStartSec: Float?,
    loopEndSec: Float?,
    loopEnabled: Boolean,
    loopSelecting: Boolean,
    isPlaying: Boolean,
    meterLevels: Map<Int, Float>,
    overview: SessionWaveformOverview,
    normalized: Boolean,
    showWaveforms: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    hideRoutingBadges: Boolean,
    onSeek: (Float) -> Unit,
    onSetView: (Float, Float) -> Unit,
    onSetLoopRegion: (Float, Float) -> Unit,
    onOpenStripControls: (Int) -> Unit,
    onToggleMute: (Int) -> Unit,
    onToggleSolo: (Int) -> Unit,
) {
    if (strips.isEmpty()) {
        SoundcheckEmptyState("No channel data in this session.")
        return
    }
    val duration = durationSec

    var gestureViewStart by remember { mutableFloatStateOf(viewStartSec) }
    var gestureViewWindow by remember { mutableFloatStateOf(viewWindowSec) }
    var gestureActive by remember { mutableStateOf(false) }
    LaunchedEffect(viewStartSec, viewWindowSec) {
        if (!gestureActive) {
            gestureViewStart = viewStartSec
            gestureViewWindow = viewWindowSec
        }
    }
    val viewStart = gestureViewStart
    val viewWindow = gestureViewWindow

    val durationState by rememberUpdatedState(duration)
    val viewStartState by rememberUpdatedState(viewStart)
    val viewWindowState by rememberUpdatedState(viewWindow)
    val loopSelectingState by rememberUpdatedState(loopSelecting)
    val loopStartState by rememberUpdatedState(loopStartSec)
    val loopEndState by rememberUpdatedState(loopEndSec)
    val onSeekState by rememberUpdatedState(onSeek)
    val onSetViewState by rememberUpdatedState(onSetView)
    val onSetLoopRegionState by rememberUpdatedState(onSetLoopRegion)

    val pendingLoopStartState = remember { mutableStateOf<Float?>(null) }
    val pendingLoopStart = pendingLoopStartState.value
    LaunchedEffect(loopSelecting) {
        if (!loopSelecting) pendingLoopStartState.value = null
    }

    var dragLoopStart by remember { mutableFloatStateOf(0f) }
    var dragLoopEnd by remember { mutableFloatStateOf(0f) }
    var loopDragActive by remember { mutableStateOf(false) }
    var draggedHandle by remember { mutableStateOf<LoopHandle?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val count = strips.size.coerceAtLeast(1)
        val gap = 2.dp
        val totalGaps = gap * (count - 1).coerceAtLeast(0)
        val rulerAndGap = TimeRulerHeight + gap
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
            controlMode = StripControlMode.PLAYBACK,
            hideRoutingBadges = hideRoutingBadges,
        )
        val labelGap = innerPad / 2
        val waveformAreaStart = innerPad + labelColumnWidth + labelGap + StripVuMeterWidth + labelGap
        var waveformWidthPx by remember { mutableFloatStateOf(0f) }
        val waveformWidthState by rememberUpdatedState(waveformWidthPx)

        fun secFromX(x: Float, widthPx: Float, start: Float, window: Float): Float {
            if (widthPx <= 0f) return start
            return (start + (x / widthPx) * window).coerceIn(0f, durationState)
        }

        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            if (waveformWidthPx <= 0f) return@rememberTransformableState
            gestureActive = true
            if (zoomChange != 1f) {
                val focalSec = gestureViewStart + gestureViewWindow / 2f
                val newWindow = (gestureViewWindow / zoomChange).coerceIn(30f, 600f)
                gestureViewWindow = newWindow
                val maxStart = max(0f, durationState - newWindow)
                gestureViewStart = (focalSec - newWindow / 2f).coerceIn(0f, maxStart)
            }
            if (panChange.x != 0f) {
                val deltaSec = -(panChange.x / waveformWidthPx) * gestureViewWindow
                val maxStart = max(0f, durationState - gestureViewWindow)
                gestureViewStart = (gestureViewStart + deltaSec).coerceIn(0f, maxStart)
            }
        }
        LaunchedEffect(transformState.isTransformInProgress) {
            if (!transformState.isTransformInProgress && gestureActive) {
                gestureActive = false
                onSetViewState(gestureViewStart, gestureViewWindow)
            }
        }

        val stripScroll = rememberScrollState()
        val waveformGestureModifier = Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val widthPx = waveformWidthState
                if (widthPx <= 0f) return@detectTapGestures
                val sec = secFromX(offset.x, widthPx, viewStartState, viewWindowState)
                if (loopSelectingState) {
                    val pending = pendingLoopStartState.value
                    if (pending == null) {
                        pendingLoopStartState.value = sec
                    } else {
                        onSetLoopRegionState(pending, sec)
                        pendingLoopStartState.value = null
                    }
                } else {
                    onSeekState(sec)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TimeRulerHeight)
                    .padding(horizontal = innerPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(labelGap),
            ) {
                Spacer(Modifier.width(labelColumnWidth))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(TimeRulerHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .onSizeChanged { waveformWidthPx = it.width.toFloat() }
                        .then(waveformGestureModifier)
                        .then(
                            if (!loopSelecting) {
                                Modifier.transformable(
                                    state = transformState,
                                    lockRotationOnZoomPan = true,
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawTimeRuler(viewStartSec = viewStart, viewWindowSec = viewWindow)
                    }
                    SoundcheckPlayheadOverlay(
                        viewStartSec = viewStart,
                        viewWindowSec = viewWindow,
                        playheadSec = playheadSec,
                        loopStartSec = loopStartSec,
                        loopEndSec = loopEndSec,
                        loopEnabled = loopEnabled,
                        pendingLoopStartSec = pendingLoopStart,
                    )
                }
            }
            if (loopSelecting && pendingLoopStart != null) {
                Text(
                    "Tap end point for loop region",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = innerPad, bottom = 2.dp),
                )
            } else if (loopSelecting) {
                Text(
                    "Tap loop start, then tap loop end (or drag on waveforms)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = innerPad, bottom = 2.dp),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val stripListModifier = if (useScroll) {
                    Modifier.fillMaxSize().verticalScroll(stripScroll)
                } else {
                    Modifier.fillMaxSize()
                }
                Column(modifier = stripListModifier, verticalArrangement = Arrangement.spacedBy(gap)) {
                    strips.forEach { strip ->
                        key(strip.index) {
                        SoundcheckStripRow(
                            strip = strip,
                            routing = routing,
                            appMode = appMode,
                            playbackChannelCount = playbackChannelCount,
                            stripHeight = stripHeight,
                            innerPad = innerPad,
                            labelFontSize = labelFontSize,
                            labelColumnWidth = labelColumnWidth,
                            labelGap = labelGap,
                            overview = overview,
                            viewStartSec = viewStart,
                            viewWindowSec = viewWindow,
                            meterLevel = if (isPlaying) {
                                meterLevels[strip.index] ?: 0f
                            } else {
                                peakLevelAtTime(overview, strip.index, playheadSec, normalized)
                            },
                            normalized = normalized,
                            showWaveform = showWaveforms,
                            numberMode = numberMode,
                            iconMode = iconMode,
                            hideArm = hideArm,
                            hideMonitor = hideMonitor,
                            hideSolo = hideSolo,
                            hideRoutingBadges = hideRoutingBadges,
                            onOpenControls = { onOpenStripControls(strip.index) },
                            onToggleMute = { onToggleMute(strip.index) },
                            onToggleSolo = { onToggleSolo(strip.index) },
                        )
                        }
                    }
                }
                var stripGestureModifier = Modifier
                    .matchParentSize()
                    .padding(start = waveformAreaStart, end = innerPad)
                if (!loopSelecting) {
                    stripGestureModifier = stripGestureModifier.transformable(
                        state = transformState,
                        lockRotationOnZoomPan = true,
                    )
                }
                Box(
                    modifier = stripGestureModifier
                        .then(waveformGestureModifier)
                        .pointerInput(Unit) {
                            var loopAnchor: Float? = null
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val widthPx = waveformWidthState
                                    if (widthPx <= 0f) return@detectDragGestures
                                    val sec = secFromX(offset.x, widthPx, viewStartState, viewWindowState)
                                    val window = viewWindowState
                                    val loopStart = loopStartState
                                    val loopEnd = loopEndState
                                    draggedHandle = when {
                                        loopStart != null && loopEnd != null &&
                                            abs(sec - loopStart) * widthPx / window < 28f ->
                                            LoopHandle.START
                                        loopStart != null && loopEnd != null &&
                                            abs(sec - loopEnd) * widthPx / window < 28f ->
                                            LoopHandle.END
                                        else -> null
                                    }
                                    if (draggedHandle == null && loopSelectingState) {
                                        loopAnchor = sec
                                        dragLoopStart = sec
                                        dragLoopEnd = sec
                                        loopDragActive = true
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    val widthPx = waveformWidthState
                                    if (widthPx <= 0f) return@detectDragGestures
                                    val window = viewWindowState
                                    val secDelta = -(dragAmount.x / widthPx) * window
                                    val loopStart = loopStartState
                                    val loopEnd = loopEndState
                                    when (draggedHandle) {
                                        LoopHandle.START -> {
                                            val end = loopEnd ?: durationState
                                            onSetLoopRegionState(
                                                (loopStart!! + secDelta).coerceIn(0f, end - 0.1f),
                                                end,
                                            )
                                        }
                                        LoopHandle.END -> {
                                            val start = loopStart ?: 0f
                                            onSetLoopRegionState(
                                                start,
                                                (loopEnd!! + secDelta).coerceIn(start + 0.1f, durationState),
                                            )
                                        }
                                        null -> if (loopDragActive && loopAnchor != null) {
                                            dragLoopEnd = secFromX(
                                                change.position.x,
                                                widthPx,
                                                viewStartState,
                                                viewWindowState,
                                            )
                                            onSetLoopRegionState(dragLoopStart, dragLoopEnd)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggedHandle = null
                                    loopDragActive = false
                                    loopAnchor = null
                                },
                                onDragCancel = {
                                    draggedHandle = null
                                    loopDragActive = false
                                    loopAnchor = null
                                },
                            )
                        },
                ) {
                    SoundcheckPlayheadOverlay(
                        viewStartSec = viewStart,
                        viewWindowSec = viewWindow,
                        playheadSec = playheadSec,
                        loopStartSec = loopStartSec,
                        loopEndSec = loopEndSec,
                        loopEnabled = loopEnabled,
                        pendingLoopStartSec = pendingLoopStart,
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundcheckPlayheadOverlay(
    viewStartSec: Float,
    viewWindowSec: Float,
    playheadSec: Float,
    loopStartSec: Float?,
    loopEndSec: Float?,
    loopEnabled: Boolean,
    pendingLoopStartSec: Float? = null,
) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        drawPlayheadAndLoop(
            viewStartSec = viewStartSec,
            viewWindowSec = viewWindowSec,
            playheadSec = playheadSec,
            loopStartSec = loopStartSec,
            loopEndSec = loopEndSec,
            loopEnabled = loopEnabled,
            pendingLoopStartSec = pendingLoopStartSec,
        )
    }
}

private enum class LoopHandle { START, END }

@Composable
private fun SoundcheckStripRow(
    strip: ChannelStripState,
    routing: MixerRoutingConfig,
    appMode: AppMode,
    playbackChannelCount: Int,
    stripHeight: androidx.compose.ui.unit.Dp,
    innerPad: androidx.compose.ui.unit.Dp,
    labelFontSize: Float,
    labelColumnWidth: androidx.compose.ui.unit.Dp,
    labelGap: androidx.compose.ui.unit.Dp,
    overview: SessionWaveformOverview,
    viewStartSec: Float,
    viewWindowSec: Float,
    meterLevel: Float,
    normalized: Boolean,
    showWaveform: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    hideRoutingBadges: Boolean,
    onOpenControls: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
) {
    val unrouted = appMode == AppMode.VIRTUAL_SOUNDCHECK &&
        !routing.hasValidPlaybackRoute(strip.index, playbackChannelCount)
    val colorBarHeight = stripHeight - innerPad * 2
    val displayPeaks = rememberViewportPeaks(
        overview = overview,
        channelIndex = strip.index,
        viewStartSec = viewStartSec,
        viewWindowSec = viewWindowSec,
        normalized = normalized,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = innerPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(labelGap),
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
            routing = routing,
            soundcheckMode = true,
            hideRoutingBadges = hideRoutingBadges,
            controlMode = StripControlMode.PLAYBACK,
            onClick = onOpenControls,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (unrouted) Modifier.alpha(0.42f) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(labelGap),
        ) {
        StripVuMeter(
            level = meterLevel,
            height = colorBarHeight,
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
                    drawSoundcheckWaveformPeaks(
                        peaks = displayPeaks,
                        color = Color(strip.colorArgb),
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        }
    }
}

@Composable
private fun rememberViewportPeaks(
    overview: SessionWaveformOverview,
    channelIndex: Int,
    viewStartSec: Float,
    viewWindowSec: Float,
    normalized: Boolean,
): FloatArray {
    val peaks = overview.peaksByChannel[channelIndex]
    val quantizedStart = (viewStartSec * 2f).roundToInt() / 2f
    val quantizedWindow = (viewWindowSec * 2f).roundToInt() / 2f
    return remember(overview, channelIndex, quantizedStart, quantizedWindow, normalized) {
        if (peaks == null || peaks.isEmpty()) {
            FloatArray(0)
        } else {
            val sampled = visiblePeaksForViewport(
                overview = overview,
                channelIndex = channelIndex,
                viewStartSec = viewStartSec,
                viewWindowSec = viewWindowSec,
                pixelWidth = 512,
            )
            scalePeaksForDisplay(sampled, normalized)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeRuler(
    viewStartSec: Float,
    viewWindowSec: Float,
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
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoundcheckWaveformPeaks(
    peaks: FloatArray,
    color: Color,
) {
    if (peaks.isEmpty()) return
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val slotWidth = w / peaks.size
    val stroke = (slotWidth * 0.75f).coerceIn(1f, 4f)
    val minBar = h * 0.06f
    peaks.forEachIndexed { i, peak ->
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
    pendingLoopStartSec: Float? = null,
    drawHandles: Boolean = true,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || viewWindowSec <= 0f) return
    fun secToX(sec: Float): Float = ((sec - viewStartSec) / viewWindowSec) * w

    pendingLoopStartSec?.let { pending ->
        if (pending in viewStartSec..(viewStartSec + viewWindowSec)) {
            val px = secToX(pending)
            drawLine(
                color = LoopHandleColor.copy(alpha = 0.85f),
                start = Offset(px, 0f),
                end = Offset(px, h),
                strokeWidth = 2f,
            )
        }
    }

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

internal fun peakLevelAtTime(
    overview: SessionWaveformOverview,
    channelIndex: Int,
    timeSec: Float,
    normalized: Boolean,
): Float {
    val peaks = overview.peaksByChannel[channelIndex] ?: return 0f
    if (peaks.isEmpty() || overview.peaksPerSec <= 0f) return 0f
    val idx = (timeSec * overview.peaksPerSec).toInt().coerceIn(0, peaks.size - 1)
    return scalePeaksForDisplay(floatArrayOf(peaks[idx]), normalized).first()
}

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
