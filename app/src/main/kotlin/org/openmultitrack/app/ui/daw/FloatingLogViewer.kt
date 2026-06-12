package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
private val RESIZE_HANDLE_THICKNESS = 14.dp
private val RESIZE_CORNER_SIZE = 22.dp
private val TITLE_BAR_HEIGHT = 32.dp
private val TITLE_BAR_BUTTON_SIZE = 28.dp
private val MENU_BAR_HEIGHT = 28.dp

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

/**
 * Developer-only floating log overlay. Stays above the DAW while mixing/recording.
 */
@Composable
fun FloatingLogViewerOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var mode by rememberSaveable { mutableStateOf(FloatingLogMode.Window) }
    var offsetX by rememberSaveable { mutableFloatStateOf(16f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(96f) }
    var windowWidthDp by rememberSaveable { mutableFloatStateOf(340f) }
    var windowHeightDp by rememberSaveable { mutableFloatStateOf(260f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f),
    ) {
        when (mode) {
            FloatingLogMode.Minimized -> MinimizedLogFab(
                offsetX = offsetX,
                offsetY = offsetY,
                onMove = { dx, dy ->
                    offsetX = (offsetX + dx).coerceAtLeast(0f)
                    offsetY = (offsetY + dy).coerceAtLeast(0f)
                },
                onRestore = { mode = FloatingLogMode.Window },
            )
            FloatingLogMode.Window -> FloatingLogWindow(
                offsetX = offsetX,
                offsetY = offsetY,
                widthDp = windowWidthDp,
                heightDp = windowHeightDp,
                onMove = { dx, dy ->
                    offsetX = (offsetX + dx).coerceAtLeast(0f)
                    offsetY = (offsetY + dy).coerceAtLeast(0f)
                },
                onResize = { newX, newY, newWidthDp, newHeightDp ->
                    offsetX = newX.coerceAtLeast(0f)
                    offsetY = newY.coerceAtLeast(0f)
                    windowWidthDp = newWidthDp.coerceAtLeast(MIN_WINDOW_WIDTH_DP)
                    windowHeightDp = newHeightDp.coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
                },
                onMinimize = { mode = FloatingLogMode.Minimized },
                onMaximize = { mode = FloatingLogMode.Maximized },
                onClose = onDismiss,
            )
            FloatingLogMode.Maximized -> FloatingLogMaximized(
                onRestore = { mode = FloatingLogMode.Window },
                onMinimize = { mode = FloatingLogMode.Minimized },
                onClose = onDismiss,
            )
        }
    }
}

@Composable
private fun MinimizedLogFab(
    offsetX: Float,
    offsetY: Float,
    onMove: (dx: Float, dy: Float) -> Unit,
    onRestore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(48.dp)
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
    offsetX: Float,
    offsetY: Float,
    widthDp: Float,
    heightDp: Float,
    onMove: (dx: Float, dy: Float) -> Unit,
    onResize: (x: Float, y: Float, widthDp: Float, heightDp: Float) -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(widthDp.dp, heightDp.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .shadow(12.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
        ) {
            LogViewerContent(
                title = "Debug log",
                onDragTitleBar = onMove,
                onMinimize = onMinimize,
                onMaximize = onMaximize,
                onClose = onClose,
                maximizeIcon = Icons.Default.Fullscreen,
                maximizeContentDescription = "Maximize",
            )
        }
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(RESIZE_HANDLE_THICKNESS),
            edge = ResizeEdge.Top,
            offsetX = offsetX,
            offsetY = offsetY,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density.density,
            onResize = onResize,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(RESIZE_HANDLE_THICKNESS),
            edge = ResizeEdge.Bottom,
            offsetX = offsetX,
            offsetY = offsetY,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density.density,
            onResize = onResize,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(RESIZE_HANDLE_THICKNESS),
            edge = ResizeEdge.Left,
            offsetX = offsetX,
            offsetY = offsetY,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density.density,
            onResize = onResize,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(RESIZE_HANDLE_THICKNESS),
            edge = ResizeEdge.Right,
            offsetX = offsetX,
            offsetY = offsetY,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density.density,
            onResize = onResize,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(RESIZE_CORNER_SIZE),
            edge = ResizeEdge.TopLeft,
            offsetX = offsetX,
            offsetY = offsetY,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density.density,
            onResize = onResize,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(RESIZE_CORNER_SIZE),
            edge = ResizeEdge.TopRight,
            offsetX = offsetX,
            offsetY = offsetY,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density.density,
            onResize = onResize,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(RESIZE_CORNER_SIZE),
            edge = ResizeEdge.BottomLeft,
            offsetX = offsetX,
            offsetY = offsetY,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density.density,
            onResize = onResize,
        )
        ResizeHandle(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(RESIZE_CORNER_SIZE),
            edge = ResizeEdge.BottomRight,
            offsetX = offsetX,
            offsetY = offsetY,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density.density,
            onResize = onResize,
        )
    }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier,
    edge: ResizeEdge,
    offsetX: Float,
    offsetY: Float,
    widthDp: Float,
    heightDp: Float,
    density: Float,
    onResize: (x: Float, y: Float, widthDp: Float, heightDp: Float) -> Unit,
) {
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentWidthDp by rememberUpdatedState(widthDp)
    val currentHeightDp by rememberUpdatedState(heightDp)
    val onResizeState by rememberUpdatedState(onResize)

    Box(
        modifier = modifier.pointerInput(edge) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val dxDp = dragAmount.x / density
                val dyDp = dragAmount.y / density
                var newX = currentOffsetX
                var newY = currentOffsetY
                var newWidth = currentWidthDp
                var newHeight = currentHeightDp
                when (edge) {
                    ResizeEdge.Right -> newWidth = (currentWidthDp + dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
                    ResizeEdge.Bottom -> newHeight = (currentHeightDp + dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
                    ResizeEdge.Left -> {
                        val target = (currentWidthDp - dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
                        val appliedDp = currentWidthDp - target
                        newWidth = target
                        newX = currentOffsetX + appliedDp * density
                    }
                    ResizeEdge.Top -> {
                        val target = (currentHeightDp - dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
                        val appliedDp = currentHeightDp - target
                        newHeight = target
                        newY = currentOffsetY + appliedDp * density
                    }
                    ResizeEdge.BottomRight -> {
                        newWidth = (currentWidthDp + dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
                        newHeight = (currentHeightDp + dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
                    }
                    ResizeEdge.BottomLeft -> {
                        val target = (currentWidthDp - dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
                        val appliedDp = currentWidthDp - target
                        newWidth = target
                        newX = currentOffsetX + appliedDp * density
                        newHeight = (currentHeightDp + dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
                    }
                    ResizeEdge.TopRight -> {
                        newWidth = (currentWidthDp + dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
                        val target = (currentHeightDp - dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
                        val appliedDp = currentHeightDp - target
                        newHeight = target
                        newY = currentOffsetY + appliedDp * density
                    }
                    ResizeEdge.TopLeft -> {
                        val targetW = (currentWidthDp - dxDp).coerceAtLeast(MIN_WINDOW_WIDTH_DP)
                        val appliedWDp = currentWidthDp - targetW
                        newWidth = targetW
                        newX = currentOffsetX + appliedWDp * density
                        val targetH = (currentHeightDp - dyDp).coerceAtLeast(MIN_WINDOW_HEIGHT_DP)
                        val appliedHDp = currentHeightDp - targetH
                        newHeight = targetH
                        newY = currentOffsetY + appliedHDp * density
                    }
                }
                onResizeState(newX, newY, newWidth, newHeight)
            }
        },
    )
}

@Composable
private fun FloatingLogMaximized(
    onRestore: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .shadow(16.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 8.dp,
    ) {
        LogViewerContent(
            title = "Debug log",
            onDragTitleBar = null,
            onMinimize = onMinimize,
            onMaximize = onRestore,
            onClose = onClose,
            maximizeIcon = Icons.Default.FullscreenExit,
            maximizeContentDescription = "Restore window",
        )
    }
}

@Composable
private fun LogViewerContent(
    title: String,
    onDragTitleBar: ((dx: Float, dy: Float) -> Unit)?,
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
    var refreshTick by remember { mutableIntStateOf(0) }
    var textScale by rememberSaveable { mutableFloatStateOf(1f) }

    LaunchedEffect(autoPersist) {
        settings.devLogAutoPersist = autoPersist
        AppLogBuffer.setAutoPersist(context, autoPersist)
    }

    val logEntries = remember(refreshTick, autoPersist, levelFilterMask) {
        AppLogBuffer.collectDisplayEntries(
            context = context,
            includePersisted = autoPersist,
            levelMask = levelFilterMask,
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
    val logDisplayText = rememberLogDisplayText(
        entries = logEntries,
        hideTimestamps = hideTimestamps,
        coloredLevels = coloredLevels,
    )
    val visibleLogLineCount = remember(logEntries) {
        logEntries.count { it is LogDisplayEntry.Line }
    }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        if (zoomChange != 1f) {
            textScale = (textScale * zoomChange).coerceIn(0.5f, 4f)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            refreshTick = AppLogBuffer.revision
        }
    }

    LaunchedEffect(logEntries.size, logDisplayText) {
        if (verticalScroll.maxValue > 0) {
            verticalScroll.animateScrollTo(verticalScroll.maxValue)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TITLE_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium)
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
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
        }

        if (showMenuBar) {
            HorizontalDivider()
            LogViewerMenuBar(
                visibleLineCount = visibleLogLineCount,
                totalLineCount = AppLogBuffer.lineCount(),
                autoPersist = autoPersist,
                hideTimestamps = hideTimestamps,
                coloredLevels = coloredLevels,
                levelFilterMask = levelFilterMask,
                onCopy = { clipboard.setText(AnnotatedString(logText)) },
                onClear = { AppLogBuffer.clearCurrentSession(context) },
                onAutoPersistChange = { autoPersist = it },
                onHideTimestampsChange = {
                    hideTimestamps = it
                    settings.devLogHideTimestamps = it
                },
                onColoredLevelsChange = {
                    coloredLevels = it
                    settings.devLogColoredLevels = it
                },
                onLevelFilterMaskChange = {
                    levelFilterMask = it
                    settings.devLogLevelFilterMask = it
                },
                wordWrap = wordWrap,
                onWordWrapChange = {
                    wordWrap = it
                    settings.devLogWordWrap = it
                },
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
            Text(
                logDisplayText,
                style = logTextStyle,
                softWrap = wordWrap,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(verticalScroll)
                    .then(
                        if (wordWrap) {
                            Modifier
                        } else {
                            Modifier.horizontalScroll(horizontalScroll)
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun LogViewerMenuBar(
    visibleLineCount: Int,
    totalLineCount: Int,
    autoPersist: Boolean,
    hideTimestamps: Boolean,
    coloredLevels: Boolean,
    levelFilterMask: Int,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onAutoPersistChange: (Boolean) -> Unit,
    onHideTimestampsChange: (Boolean) -> Unit,
    onColoredLevelsChange: (Boolean) -> Unit,
    onLevelFilterMaskChange: (Int) -> Unit,
    wordWrap: Boolean,
    onWordWrapChange: (Boolean) -> Unit,
) {
    val menuLabelStyle = MaterialTheme.typography.labelSmall
    val menuItemPadding = Modifier.padding(horizontal = 2.dp)
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val filterActive = levelFilterMask != DevLogLevelMask.ALL
    val lineCountLabel = if (filterActive && visibleLineCount != totalLineCount) {
        "$visibleLineCount/$totalLineCount lines"
    } else {
        "$totalLineCount lines"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(
            onClick = onCopy,
            modifier = menuItemPadding.height(MENU_BAR_HEIGHT),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            Text("Copy", style = menuLabelStyle)
        }
        MenuBarDivider()
        TextButton(
            onClick = onClear,
            modifier = menuItemPadding.height(MENU_BAR_HEIGHT),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            Text("Clear", style = menuLabelStyle)
        }
        MenuBarDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 2.dp),
        ) {
            Checkbox(
                checked = autoPersist,
                onCheckedChange = onAutoPersistChange,
                modifier = Modifier.size(20.dp),
            )
            Text("Persist logs", style = menuLabelStyle)
        }
        MenuBarDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = hideTimestamps,
                onCheckedChange = onHideTimestampsChange,
                modifier = Modifier.size(20.dp),
            )
            Text("Hide timestamps", style = menuLabelStyle)
        }
        MenuBarDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = coloredLevels,
                onCheckedChange = onColoredLevelsChange,
                modifier = Modifier.size(20.dp),
            )
            Text("Colors", style = menuLabelStyle)
        }
        MenuBarDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = wordWrap,
                onCheckedChange = onWordWrapChange,
                modifier = Modifier.size(20.dp),
            )
            Text("Word wrap", style = menuLabelStyle)
        }
        MenuBarDivider()
        Box {
            TextButton(
                onClick = { filterMenuExpanded = true },
                modifier = menuItemPadding.height(MENU_BAR_HEIGHT),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    if (filterActive) "Filter*" else "Filter",
                    style = menuLabelStyle,
                    color = if (filterActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            DropdownMenu(
                expanded = filterMenuExpanded,
                onDismissRequest = { filterMenuExpanded = false },
            ) {
                LogLevelFilterRow('D', "Debug", DevLogLevelMask.DEBUG, levelFilterMask, onLevelFilterMaskChange)
                LogLevelFilterRow('I', "Info", DevLogLevelMask.INFO, levelFilterMask, onLevelFilterMaskChange)
                LogLevelFilterRow('W', "Warn", DevLogLevelMask.WARN, levelFilterMask, onLevelFilterMaskChange)
                LogLevelFilterRow('E', "Error", DevLogLevelMask.ERROR, levelFilterMask, onLevelFilterMaskChange)
            }
        }
        Text(
            lineCountLabel,
            style = menuLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 6.dp),
        )
    }
}

@Composable
private fun LogLevelFilterRow(
    level: Char,
    label: String,
    bit: Int,
    levelFilterMask: Int,
    onLevelFilterMaskChange: (Int) -> Unit,
) {
    val checked = levelFilterMask and bit != 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val updated = if (checked) levelFilterMask and bit.inv() else levelFilterMask or bit
                onLevelFilterMaskChange(updated)
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { enabled ->
                val updated = if (enabled) levelFilterMask or bit else levelFilterMask and bit.inv()
                onLevelFilterMaskChange(updated)
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            "$level  $label",
            style = MaterialTheme.typography.labelMedium,
            color = logLevelColor(level),
        )
    }
}

@Composable
private fun logLevelColor(level: Char): androidx.compose.ui.graphics.Color {
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
private fun MenuBarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
