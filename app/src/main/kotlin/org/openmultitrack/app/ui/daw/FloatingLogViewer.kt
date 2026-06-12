package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.openmultitrack.app.util.AppLogBuffer

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
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    Surface(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .widthIn(min = 240.dp)
            .then(
                with(density) {
                    Modifier.size(widthDp.dp, heightDp.dp)
                },
            )
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
    var includePersisted by rememberSaveable { mutableStateOf(false) }
    var persistMessage by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            refreshTick = AppLogBuffer.revision
        }
    }

    val logText = remember(refreshTick, includePersisted) {
        AppLogBuffer.displayText(context, includePersisted)
    }
    val scrollState = rememberScrollState()

    LaunchedEffect(logText) {
        if (scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
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
                )
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            IconButton(onClick = onMinimize, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Minimize, contentDescription = "Minimize")
            }
            IconButton(onClick = onMaximize, modifier = Modifier.size(36.dp)) {
                Icon(maximizeIcon, contentDescription = maximizeContentDescription)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(logText)) }) {
                Text("Copy")
            }
            TextButton(
                onClick = {
                    val saved = AppLogBuffer.persistSession(context)
                    persistMessage = if (saved) "Session saved" else "Nothing to save"
                },
            ) {
                Text("Persist")
            }
            TextButton(onClick = { AppLogBuffer.clearCurrentSession() }) {
                Text("Clear")
            }
            Text(
                "${AppLogBuffer.lineCount()} lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = includePersisted, onCheckedChange = { includePersisted = it })
            Text("Previous sessions", style = MaterialTheme.typography.bodySmall)
        }
        persistMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        Text(
            logText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
