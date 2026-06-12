package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.app.util.DevLogLevelMask
import org.openmultitrack.app.util.LogDisplayEntry

private const val MIN_WINDOW_WIDTH_DP = 220f
private const val MIN_WINDOW_HEIGHT_DP = 160f
private const val DEFAULT_WINDOW_WIDTH_DP = 340f
private const val DEFAULT_WINDOW_HEIGHT_DP = DEFAULT_WINDOW_WIDTH_DP * 9f / 16f
private const val TERMINAL_SCREEN_MARGIN_DP = 2.5f
private const val FAB_SIZE_DP = 48f
private const val UNSET_FAB_POSITION = -1f
private val LOG_CONTENT_PADDING_H = 3.dp
private val LOG_CONTENT_PADDING_V = 2.dp
private val RESIZE_HANDLE_THICKNESS = 12.dp
private val RESIZE_HIT_OUTSET = 18.dp
private val RESIZE_CORNER_SIZE = 40.dp
private val TITLE_BAR_HEIGHT = 36.dp
private val TITLE_BAR_BUTTON_SIZE = 28.dp
private val TerminalWindowShape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 4.dp,
    bottomStart = 0.dp,
    bottomEnd = 0.dp,
)

/** All position and size fields are in dp. */
private data class WindowBounds(
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)

private fun clampWindowBounds(
    xDp: Float,
    yDp: Float,
    widthDp: Float,
    heightDp: Float,
    screenWidthDp: Float,
    screenHeightDp: Float,
): WindowBounds {
    val margin = TERMINAL_SCREEN_MARGIN_DP
    val width = widthDp.coerceIn(MIN_WINDOW_WIDTH_DP, screenWidthDp - 2f * margin)
    val maxHeight = (screenHeightDp - margin).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
    val height = heightDp.coerceIn(MIN_WINDOW_HEIGHT_DP, maxHeight)
    val maxX = (screenWidthDp - width - margin).coerceAtLeast(margin)
    val clampedX = xDp.coerceIn(margin, maxX)
    val maxY = (screenHeightDp - height - margin).coerceAtLeast(0f)
    val clampedY = yDp.coerceIn(0f, maxY)
    return WindowBounds(clampedX, clampedY, width, height)
}

private fun defaultFabPosition(screenHeightDp: Float): WindowBounds {
    val margin = TERMINAL_SCREEN_MARGIN_DP
    return WindowBounds(
        xDp = margin,
        yDp = screenHeightDp - FAB_SIZE_DP - margin,
        widthDp = FAB_SIZE_DP,
        heightDp = FAB_SIZE_DP,
    )
}

private fun applyResizeDelta(
    start: WindowBounds,
    edge: ResizeEdge,
    dxDp: Float,
    dyDp: Float,
): WindowBounds {
    var newX = start.xDp
    var newY = start.yDp
    var newWidth = start.widthDp
    var newHeight = start.heightDp
    when (edge) {
        ResizeEdge.Right -> newWidth = (start.widthDp + dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
        ResizeEdge.Bottom -> newHeight = (start.heightDp + dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
        ResizeEdge.Left -> {
            val target = (start.widthDp - dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
            val appliedDp = start.widthDp - target
            newWidth = target
            newX = start.xDp + appliedDp
        }
        ResizeEdge.Top -> {
            val target = (start.heightDp - dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
            val appliedDp = start.heightDp - target
            newHeight = target
            newY = start.yDp + appliedDp
        }
        ResizeEdge.BottomRight -> {
            newWidth = (start.widthDp + dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
            newHeight = (start.heightDp + dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
        }
        ResizeEdge.BottomLeft -> {
            val target = (start.widthDp - dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
            val appliedDp = start.widthDp - target
            newWidth = target
            newX = start.xDp + appliedDp
            newHeight = (start.heightDp + dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
        }
        ResizeEdge.TopRight -> {
            newWidth = (start.widthDp + dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
            val target = (start.heightDp - dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
            val appliedDp = start.heightDp - target
            newHeight = target
            newY = start.yDp + appliedDp
        }
        ResizeEdge.TopLeft -> {
            val targetW = (start.widthDp - dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
            val appliedWDp = start.widthDp - targetW
            newWidth = targetW
            newX = start.xDp + appliedWDp
            val targetH = (start.heightDp - dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
            val appliedHDp = start.heightDp - targetH
            newHeight = targetH
            newY = start.yDp + appliedHDp
        }
    }
    return WindowBounds(newX, newY, newWidth, newHeight)
}

private enum class ResizeEdge {
    Top,
    Bottom,
    Left,
    Right,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

private enum class FloatingLogMode {
    Minimized,
    Window,
    Maximized,
}

private fun FloatingLogMode.toStoredOrdinal(): Int = ordinal

private fun floatingLogModeFromStored(ordinal: Int): FloatingLogMode =
    FloatingLogMode.entries.getOrElse(ordinal.coerceIn(0, FloatingLogMode.entries.lastIndex)) {
        FloatingLogMode.Window
    }

/**
 * Developer-only floating log overlay. Stays above the DAW while mixing/recording.
 */
@Composable
fun FloatingLogViewerOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettingsStore(context) }

    var mode by remember {
        mutableStateOf(floatingLogModeFromStored(settings.devLogViewerMode))
    }
    var windowOffsetXDp by remember { mutableFloatStateOf(settings.devLogWindowXDp) }
    var windowOffsetYDp by remember { mutableFloatStateOf(settings.devLogWindowYDp) }
    var windowWidthDp by remember { mutableFloatStateOf(settings.devLogWindowWidthDp) }
    var windowHeightDp by remember { mutableFloatStateOf(settings.devLogWindowHeightDp) }
    var fabOffsetXDp by remember { mutableFloatStateOf(settings.devLogFabXDp) }
    var fabOffsetYDp by remember { mutableFloatStateOf(settings.devLogFabYDp) }
    var textScale by remember { mutableFloatStateOf(settings.devLogTextScale) }

    LaunchedEffect(
        mode,
        windowOffsetXDp,
        windowOffsetYDp,
        windowWidthDp,
        windowHeightDp,
        fabOffsetXDp,
        fabOffsetYDp,
        textScale,
    ) {
        settings.devLogViewerMode = mode.toStoredOrdinal()
        settings.devLogWindowXDp = windowOffsetXDp
        settings.devLogWindowYDp = windowOffsetYDp
        settings.devLogWindowWidthDp = windowWidthDp
        settings.devLogWindowHeightDp = windowHeightDp
        settings.devLogFabXDp = fabOffsetXDp
        settings.devLogFabYDp = fabOffsetYDp
        settings.devLogTextScale = textScale
    }

    if (!visible) return

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f),
    ) {
        val screenWidthDp = maxWidth.value
        val screenHeightDp = maxHeight.value

        fun clampFab(xDp: Float, yDp: Float): Pair<Float, Float> {
            val margin = TERMINAL_SCREEN_MARGIN_DP
            val maxX = (screenWidthDp - FAB_SIZE_DP - margin).coerceAtLeast(margin)
            val maxY = (screenHeightDp - FAB_SIZE_DP - margin).coerceAtLeast(margin)
            return xDp.coerceIn(margin, maxX) to yDp.coerceIn(margin, maxY)
        }

        fun ensureFabPosition() {
            if (fabOffsetXDp != UNSET_FAB_POSITION && fabOffsetYDp != UNSET_FAB_POSITION) return
            val defaultFab = defaultFabPosition(screenHeightDp)
            fabOffsetXDp = defaultFab.xDp
            fabOffsetYDp = defaultFab.yDp
        }

        when (mode) {
            FloatingLogMode.Minimized -> {
                ensureFabPosition()
                MinimizedLogFab(
                    offsetXDp = fabOffsetXDp,
                    offsetYDp = fabOffsetYDp,
                    onMove = { dxPx, dyPx ->
                        val (x, y) = clampFab(
                            fabOffsetXDp + dxPx / density.density,
                            fabOffsetYDp + dyPx / density.density,
                        )
                        fabOffsetXDp = x
                        fabOffsetYDp = y
                    },
                    onRestore = { mode = FloatingLogMode.Window },
                )
            }
            FloatingLogMode.Window -> FloatingLogWindow(
                offsetXDp = windowOffsetXDp,
                offsetYDp = windowOffsetYDp,
                widthDp = windowWidthDp,
                heightDp = windowHeightDp,
                textScale = textScale,
                onTextScaleChange = { textScale = it },
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                onMove = { dxPx, dyPx ->
                    val moved = clampWindowBounds(
                        xDp = windowOffsetXDp + dxPx / density.density,
                        yDp = windowOffsetYDp + dyPx / density.density,
                        widthDp = windowWidthDp,
                        heightDp = windowHeightDp,
                        screenWidthDp = screenWidthDp,
                        screenHeightDp = screenHeightDp,
                    )
                    windowOffsetXDp = moved.xDp
                    windowOffsetYDp = moved.yDp
                },
                onResizeCommitted = { bounds ->
                    val clamped = clampWindowBounds(
                        xDp = bounds.xDp,
                        yDp = bounds.yDp,
                        widthDp = bounds.widthDp,
                        heightDp = bounds.heightDp,
                        screenWidthDp = screenWidthDp,
                        screenHeightDp = screenHeightDp,
                    )
                    windowOffsetXDp = clamped.xDp
                    windowOffsetYDp = clamped.yDp
                    windowWidthDp = clamped.widthDp
                    windowHeightDp = clamped.heightDp
                },
                onMinimize = {
                    ensureFabPosition()
                    mode = FloatingLogMode.Minimized
                },
                onMaximize = { mode = FloatingLogMode.Maximized },
                onClose = onDismiss,
            )
            FloatingLogMode.Maximized -> FloatingLogMaximized(
                textScale = textScale,
                onTextScaleChange = { textScale = it },
                onRestore = { mode = FloatingLogMode.Window },
                onMinimize = {
                    ensureFabPosition()
                    mode = FloatingLogMode.Minimized
                },
                onClose = onDismiss,
            )
        }
    }
}

@Composable
private fun MinimizedLogFab(
    offsetXDp: Float,
    offsetYDp: Float,
    onMove: (dxPx: Float, dyPx: Float) -> Unit,
    onRestore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(offsetXDp.dp, offsetYDp.dp)
            .size(FAB_SIZE_DP.dp)
            .shadow(8.dp, CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onRestore() })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Article, contentDescription = "Open debug log")
    }
}

@Composable
private fun FloatingLogWindow(
    offsetXDp: Float,
    offsetYDp: Float,
    widthDp: Float,
    heightDp: Float,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    screenWidthDp: Float,
    screenHeightDp: Float,
    onMove: (dxPx: Float, dyPx: Float) -> Unit,
    onResizeCommitted: (WindowBounds) -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    var isResizing by remember { mutableStateOf(false) }
    var displayBounds by remember {
        mutableStateOf(WindowBounds(offsetXDp, offsetYDp, widthDp, heightDp))
    }
    var resizeStartBounds by remember { mutableStateOf<WindowBounds?>(null) }
    var resizeAccumDxDp by remember { mutableFloatStateOf(0f) }
    var resizeAccumDyDp by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(offsetXDp, offsetYDp, widthDp, heightDp) {
        if (!isResizing) {
            displayBounds = WindowBounds(offsetXDp, offsetYDp, widthDp, heightDp)
        }
    }

    fun clampLocal(bounds: WindowBounds): WindowBounds =
        clampWindowBounds(
            xDp = bounds.xDp,
            yDp = bounds.yDp,
            widthDp = bounds.widthDp,
            heightDp = bounds.heightDp,
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
        )

    val bounds = displayBounds
    Box(
        modifier = Modifier
            .offset(bounds.xDp.dp, bounds.yDp.dp)
            .size(bounds.widthDp.dp, bounds.heightDp.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .shadow(6.dp, TerminalWindowShape),
            shape = TerminalWindowShape,
            tonalElevation = 4.dp,
        ) {
            LogViewerContent(
                title = "Debug log",
                onDragTitleBar = onMove,
                freezeLogUpdates = isResizing,
                textScale = textScale,
                onTextScaleChange = onTextScaleChange,
                onMinimize = onMinimize,
                onMaximize = onMaximize,
                onClose = onClose,
                maximizeIcon = Icons.Default.Fullscreen,
                maximizeContentDescription = "Maximize",
            )
        }
        val resizeCallbacks = object {
            fun onStart() {
                isResizing = true
                resizeStartBounds = displayBounds
                resizeAccumDxDp = 0f
                resizeAccumDyDp = 0f
            }

            fun onDelta(edge: ResizeEdge, dxDp: Float, dyDp: Float) {
                val start = resizeStartBounds ?: return
                resizeAccumDxDp += dxDp
                resizeAccumDyDp += dyDp
                displayBounds = clampLocal(
                    applyResizeDelta(start, edge, resizeAccumDxDp, resizeAccumDyDp),
                )
            }

            fun onEnd() {
                if (!isResizing) return
                isResizing = false
                resizeStartBounds = null
                onResizeCommitted(displayBounds)
            }
        }
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(RESIZE_HANDLE_THICKNESS + RESIZE_HIT_OUTSET)
                .offset(y = RESIZE_HIT_OUTSET / 2),
            edge = ResizeEdge.Bottom,
            density = density.density,
            onResizeStart = resizeCallbacks::onStart,
            onResizeDelta = resizeCallbacks::onDelta,
            onResizeEnd = resizeCallbacks::onEnd,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(RESIZE_HANDLE_THICKNESS + RESIZE_HIT_OUTSET)
                .padding(top = TITLE_BAR_HEIGHT)
                .offset(x = -RESIZE_HIT_OUTSET / 2),
            edge = ResizeEdge.Left,
            density = density.density,
            onResizeStart = resizeCallbacks::onStart,
            onResizeDelta = resizeCallbacks::onDelta,
            onResizeEnd = resizeCallbacks::onEnd,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(RESIZE_HANDLE_THICKNESS + RESIZE_HIT_OUTSET)
                .padding(top = TITLE_BAR_HEIGHT)
                .offset(x = RESIZE_HIT_OUTSET / 2),
            edge = ResizeEdge.Right,
            density = density.density,
            onResizeStart = resizeCallbacks::onStart,
            onResizeDelta = resizeCallbacks::onDelta,
            onResizeEnd = resizeCallbacks::onEnd,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(RESIZE_CORNER_SIZE + RESIZE_HIT_OUTSET)
                .offset(x = -RESIZE_HIT_OUTSET / 2, y = RESIZE_HIT_OUTSET / 2)
                .zIndex(3f),
            edge = ResizeEdge.BottomLeft,
            density = density.density,
            onResizeStart = resizeCallbacks::onStart,
            onResizeDelta = resizeCallbacks::onDelta,
            onResizeEnd = resizeCallbacks::onEnd,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(RESIZE_CORNER_SIZE + RESIZE_HIT_OUTSET)
                .offset(x = RESIZE_HIT_OUTSET / 2, y = RESIZE_HIT_OUTSET / 2)
                .zIndex(3f),
            edge = ResizeEdge.BottomRight,
            density = density.density,
            onResizeStart = resizeCallbacks::onStart,
            onResizeDelta = resizeCallbacks::onDelta,
            onResizeEnd = resizeCallbacks::onEnd,
        )
    }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier,
    edge: ResizeEdge,
    density: Float,
    onResizeStart: () -> Unit,
    onResizeDelta: (ResizeEdge, Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
) {
    val onResizeStartState by rememberUpdatedState(onResizeStart)
    val onResizeDeltaState by rememberUpdatedState(onResizeDelta)
    val onResizeEndState by rememberUpdatedState(onResizeEnd)

    Box(
        modifier = modifier.pointerInput(edge) {
            detectDragGestures(
                onDragStart = {
                    onResizeStartState()
                },
                onDragEnd = {
                    onResizeEndState()
                },
                onDragCancel = {
                    onResizeEndState()
                },
            ) { change, dragAmount ->
                change.consume()
                onResizeDeltaState(
                    edge,
                    dragAmount.x / density,
                    dragAmount.y / density,
                )
            }
        },
    )
}

@Composable
private fun FloatingLogMaximized(
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    onRestore: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(
                    start = TERMINAL_SCREEN_MARGIN_DP.dp,
                    end = TERMINAL_SCREEN_MARGIN_DP.dp,
                    bottom = TERMINAL_SCREEN_MARGIN_DP.dp,
                )
                .shadow(6.dp, TerminalWindowShape),
            shape = TerminalWindowShape,
            tonalElevation = 4.dp,
        ) {
            LogViewerContent(
                title = "Debug log",
                onDragTitleBar = null,
                textScale = textScale,
                onTextScaleChange = onTextScaleChange,
                onMinimize = onMinimize,
                onMaximize = onRestore,
                onClose = onClose,
                maximizeIcon = Icons.Default.FullscreenExit,
                maximizeContentDescription = "Restore window",
            )
        }
    }
}

@Composable
private fun LogViewerContent(
    title: String,
    onDragTitleBar: ((dxPx: Float, dyPx: Float) -> Unit)?,
    freezeLogUpdates: Boolean = false,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    maximizeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    maximizeContentDescription: String,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings = remember(context) { AppSettingsStore(context) }

    var autoPersist by remember { mutableStateOf(settings.devLogAutoPersist) }
    var hideTimestamps by remember { mutableStateOf(settings.devLogHideTimestamps) }
    var coloredLevels by remember { mutableStateOf(settings.devLogColoredLevels) }
    var levelFilterMask by remember { mutableIntStateOf(settings.devLogLevelFilterMask) }
    var showMenuBar by remember { mutableStateOf(settings.devLogShowMenuBar) }
    var wordWrap by remember { mutableStateOf(settings.devLogWordWrap) }
    var disabledTags by remember { mutableStateOf(settings.devLogDisabledTags) }
    var customFilters by remember { mutableStateOf(settings.devLogCustomFilters) }
    var refreshTick by remember { mutableIntStateOf(AppLogBuffer.revision) }

    LaunchedEffect(autoPersist) {
        settings.devLogAutoPersist = autoPersist
        AppLogBuffer.setAutoPersist(context, autoPersist)
    }
    LaunchedEffect(disabledTags) {
        settings.devLogDisabledTags = disabledTags
    }
    LaunchedEffect(customFilters) {
        settings.devLogCustomFilters = customFilters
    }

    val allTags = remember(refreshTick, autoPersist) {
        AppLogBuffer.discoverTags(context, autoPersist)
    }
    val logEntries = remember(refreshTick, autoPersist, levelFilterMask, disabledTags, customFilters) {
        AppLogBuffer.collectDisplayEntries(
            context = context,
            includePersisted = autoPersist,
            levelMask = levelFilterMask,
            disabledTags = disabledTags,
            customFilters = customFilters,
        )
    }
    val logText = remember(logEntries, hideTimestamps, coloredLevels) {
        if (logEntries.isEmpty()) {
            "(empty)"
        } else {
            logEntries.joinToString("\n") { entry ->
                when (entry) {
                    is LogDisplayEntry.Section -> entry.text
                    is LogDisplayEntry.Line -> AppLogBuffer.formatPlainLine(
                        parsed = entry.parsed,
                        hideTimestamps = hideTimestamps,
                        coloredLevels = coloredLevels,
                    )
                }
            }
        }
    }
    val visibleLogLineCount = remember(logEntries) {
        logEntries.count { it is LogDisplayEntry.Line }
    }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        if (zoomChange != 1f) {
            onTextScaleChange((textScale * zoomChange).coerceIn(0.5f, 4f))
        }
    }

    val freezeLogUpdatesState by rememberUpdatedState(freezeLogUpdates)
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            if (!freezeLogUpdatesState) {
                refreshTick = AppLogBuffer.revision
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TITLE_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .zIndex(10f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    showMenuBar = !showMenuBar
                    settings.devLogShowMenuBar = showMenuBar
                },
                modifier = Modifier.size(TITLE_BAR_BUTTON_SIZE),
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = if (showMenuBar) "Hide menu bar" else "Show menu bar",
                    tint = if (showMenuBar) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (onDragTitleBar != null) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onDragTitleBar(dragAmount.x, dragAmount.y)
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = onMinimize, modifier = Modifier.size(TITLE_BAR_BUTTON_SIZE)) {
                Icon(Icons.Default.Minimize, contentDescription = "Minimize")
            }
            IconButton(onClick = onMaximize, modifier = Modifier.size(TITLE_BAR_BUTTON_SIZE)) {
                Icon(maximizeIcon, contentDescription = maximizeContentDescription)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(TITLE_BAR_BUTTON_SIZE)) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        if (showMenuBar) {
            HorizontalDivider()
            LogViewerToolbar(
                visibleLineCount = visibleLogLineCount,
                totalLineCount = AppLogBuffer.lineCount(),
                filterActive = levelFilterMask != DevLogLevelMask.ALL ||
                    disabledTags.isNotEmpty() ||
                    customFilters.any { it.enabled },
                autoPersist = autoPersist,
                hideTimestamps = hideTimestamps,
                coloredLevels = coloredLevels,
                wordWrap = wordWrap,
                levelFilterMask = levelFilterMask,
                disabledTags = disabledTags,
                allTags = allTags,
                customFilters = customFilters,
                onCopy = { clipboard.setText(AnnotatedString(logText)) },
                onClear = {
                    AppLogBuffer.clearCurrentSession(context)
                    refreshTick = AppLogBuffer.revision
                },
                onAutoPersistChange = { autoPersist = it },
                onHideTimestampsChange = {
                    hideTimestamps = it
                    settings.devLogHideTimestamps = it
                },
                onColoredLevelsChange = {
                    coloredLevels = it
                    settings.devLogColoredLevels = it
                },
                onWordWrapChange = {
                    wordWrap = it
                    settings.devLogWordWrap = it
                },
                onLevelFilterMaskChange = {
                    levelFilterMask = it
                    settings.devLogLevelFilterMask = it
                },
                onDisabledTagsChange = { disabledTags = it },
                onCustomFiltersChange = { customFilters = it },
                compact = true,
            )
            HorizontalDivider()
        }

        val baseFontSize = MaterialTheme.typography.bodySmall.fontSize
        val scaledFontSize = (baseFontSize.value * textScale).sp
        val logTextStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = scaledFontSize,
            lineHeight = scaledFontSize,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Top,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .transformable(
                    state = transformState,
                    lockRotationOnZoomPan = true,
                ),
        ) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = LOG_CONTENT_PADDING_H,
                        vertical = LOG_CONTENT_PADDING_V,
                    ),
            ) {
                LogViewerLazyList(
                    entries = logEntries,
                    hideTimestamps = hideTimestamps,
                    coloredLevels = coloredLevels,
                    textStyle = logTextStyle,
                    wordWrap = wordWrap,
                    freezeUpdates = freezeLogUpdates,
                    revision = refreshTick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
