package org.openmultitrack.sessionio.session

import org.openmultitrack.domain.session.SessionFormat
import java.io.File

data class ChannelMetadata(
    val index: Int,
    val fileName: String,
    val displayName: String,
    val colorArgb: Int,
)

data class SessionMetadata(
    val mixerId: String,
    val mixerFolderName: String,
    val customTitle: String?,
    val sampleRate: Int,
    val format: SessionFormat,
    val channels: List<ChannelMetadata>,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val incomplete: Boolean = true,
    /** Logical timeline position in frames (includes silence gaps). */
    val timelineFramesWritten: Long = 0,
) {
    fun writeTo(dir: File) {
        File(dir, "session.json").writeText(toJson())
    }

    fun markComplete(dir: File) {
        copy(incomplete = false).writeTo(dir)
    }

    private fun toJson(): String {
        val chJson = channels.joinToString(",") { ch ->
            """{"index":${ch.index},"fileName":"${esc(ch.fileName)}","displayName":"${esc(ch.displayName)}","colorArgb":${ch.colorArgb}}"""
        }
        return """
            {
              "mixerId": "${esc(mixerId)}",
              "mixerFolderName": "${esc(mixerFolderName)}",
              "customTitle": ${customTitle?.let { "\"${esc(it)}\"" } ?: "null"},
              "sampleRate": $sampleRate,
              "format": "${format.name}",
              "startedAtEpochMs": $startedAtEpochMs,
              "incomplete": $incomplete,
              "timelineFramesWritten": $timelineFramesWritten,
              "channels": [$chJson]
            }
        """.trimIndent()
    }

    companion object {
        fun read(dir: File): SessionMetadata? {
            val file = File(dir, "session.json")
            if (!file.exists()) return null
            return runCatching { parseSimple(file.readText()) }.getOrNull()
        }

        private fun parseSimple(text: String): SessionMetadata {
            fun field(name: String): String? =
                Regex(""""$name"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1)
            fun intField(name: String): Int =
                Regex(""""$name"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toInt() ?: 0
            fun boolField(name: String): Boolean =
                Regex(""""$name"\s*:\s*(true|false)""").find(text)?.groupValues?.get(1) == "true"
            fun longField(name: String): Long =
                Regex(""""$name"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1)?.toLong() ?: 0L

            val channels = CHANNEL_JSON_PATTERN.findAll(text)
                .map { m ->
                    ChannelMetadata(
                        index = m.groupValues[1].toInt(),
                        fileName = m.groupValues[2],
                        displayName = m.groupValues[3],
                        colorArgb = m.groupValues[4].toInt(),
                    )
                }.toList()

            return SessionMetadata(
                mixerId = field("mixerId") ?: "",
                mixerFolderName = field("mixerFolderName") ?: "",
                customTitle = field("customTitle"),
                sampleRate = intField("sampleRate"),
                format = SessionFormat.valueOf(field("format") ?: SessionFormat.PER_CHANNEL_WAV.name),
                channels = channels,
                startedAtEpochMs = longField("startedAtEpochMs"),
                incomplete = boolField("incomplete"),
                timelineFramesWritten = longField("timelineFramesWritten"),
            )
        }

        private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

        /** Android colors are signed ints; the legacy regex only matched positive digits. */
        private val CHANNEL_JSON_PATTERN = Regex(
            """"index"\s*:\s*(\d+).*?"fileName"\s*:\s*"([^"]*)".*?"displayName"\s*:\s*"([^"]*)".*?"colorArgb"\s*:\s*(-?\d+)""",
        )

        fun discoverChannelsFromDisk(sessionDir: File): List<ChannelMetadata> {
            val channelFilePattern = Regex("""channel(\d+)(?:\s*-.*)?\.wav""", RegexOption.IGNORE_CASE)
            return sessionDir.listFiles()
                ?.asSequence()
                ?.filter { it.isFile && channelFilePattern.matches(it.name) }
                ?.mapNotNull { file ->
                    val index = channelFilePattern.matchEntire(file.name)?.groupValues?.get(1)
                        ?.toIntOrNull()?.minus(1) ?: return@mapNotNull null
                    val label = labelFromFileName(file.name)
                    ChannelMetadata(
                        index = index,
                        fileName = file.name,
                        displayName = ChannelFileNaming.displayName(index, label),
                        colorArgb = defaultStripColor(index),
                    )
                }
                ?.sortedBy { it.index }
                ?.toList()
                ?: emptyList()
        }

        private fun labelFromFileName(fileName: String): String? {
            val withoutExt = fileName.removeSuffix(".wav")
            val dash = withoutExt.indexOf(" - ")
            return if (dash >= 0) withoutExt.substring(dash + 3).takeIf { it.isNotBlank() } else null
        }

        private fun defaultStripColor(index: Int): Int {
            val palette = intArrayOf(
                0xFFE53935.toInt(),
                0xFF1E88E5.toInt(),
                0xFF43A047.toInt(),
                0xFFFFB300.toInt(),
                0xFF8E24AA.toInt(),
                0xFF00ACC1.toInt(),
            )
            return palette[index % palette.size]
        }
    }

    fun withResolvedChannels(sessionDir: File): SessionMetadata {
        if (channels.isNotEmpty()) return this
        val discovered = discoverChannelsFromDisk(sessionDir)
        return if (discovered.isEmpty()) this else copy(channels = discovered)
    }

    fun resolvedChannels(sessionDir: File): List<ChannelMetadata> =
        channels.ifEmpty { discoverChannelsFromDisk(sessionDir) }
}
