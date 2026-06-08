package org.openmultitrack.app.ui.daw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class StatusToast(
    val message: String,
    val id: Long = System.currentTimeMillis(),
)

@Composable
fun StatusToastHost(
    toast: StatusToast?,
    onDismiss: () -> Unit,
    dismissAfterMs: Long = 3_000L,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var displayedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toast?.id) {
        val message = toast?.message
        if (message.isNullOrBlank()) {
            visible = false
            displayedMessage = null
            return@LaunchedEffect
        }
        displayedMessage = message
        visible = true
        kotlinx.coroutines.delay(dismissAfterMs)
        visible = false
        onDismiss()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible && displayedMessage != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Text(
                    displayedMessage.orEmpty(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
