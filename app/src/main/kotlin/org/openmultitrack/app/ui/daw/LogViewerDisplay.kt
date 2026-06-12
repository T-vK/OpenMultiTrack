package org.openmultitrack.app.ui.daw

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.openmultitrack.app.util.LogDisplayEntry

@Composable
private fun logLevelColor(level: Char): Color {
    val scheme = MaterialTheme.colorScheme
    return when (level.uppercaseChar()) {
        'D' -> Color(0xFF9E9E9E)
        'I' -> Color(0xFF66BB6A)
        'W' -> Color(0xFFFFA726)
        'E' -> scheme.error
        else -> scheme.onSurface
    }
}

@Composable
fun rememberLogDisplayText(
    entries: List<LogDisplayEntry>,
    hideTimestamps: Boolean,
    coloredLevels: Boolean,
): AnnotatedString {
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val sectionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val debugColor = logLevelColor('D')
    val infoColor = logLevelColor('I')
    val warnColor = logLevelColor('W')
    val errorColor = logLevelColor('E')

    return remember(
        entries,
        hideTimestamps,
        coloredLevels,
        defaultColor,
        sectionColor,
        debugColor,
        infoColor,
        warnColor,
        errorColor,
    ) {
        fun levelColor(level: Char): Color = when (level.uppercaseChar()) {
            'D' -> debugColor
            'I' -> infoColor
            'W' -> warnColor
            'E' -> errorColor
            else -> defaultColor
        }

        buildAnnotatedString {
            entries.forEachIndexed { index, entry ->
                if (index > 0) append('\n')
                when (entry) {
                    is LogDisplayEntry.Section -> {
                        withStyle(SpanStyle(color = sectionColor)) {
                            append(entry.text)
                        }
                    }
                    is LogDisplayEntry.Line -> {
                        val parsed = entry.parsed
                        val lineColor = if (coloredLevels) levelColor(parsed.level) else defaultColor
                        if (!hideTimestamps) {
                            withStyle(SpanStyle(color = sectionColor)) {
                                append(parsed.timestamp)
                                append(' ')
                            }
                        }
                        withStyle(SpanStyle(color = lineColor)) {
                            if (coloredLevels) {
                                append(parsed.tag)
                                append(": ")
                                append(parsed.message)
                            } else {
                                append(parsed.level)
                                append('/')
                                append(parsed.tag)
                                append(": ")
                                append(parsed.message)
                            }
                        }
                    }
                }
            }
        }
    }
}
