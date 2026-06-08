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

            val channels = Regex(""""index"\s*:\s*(\d+).*?"fileName"\s*:\s*"([^"]*)".*?"displayName"\s*:\s*"([^"]*)".*?"colorArgb"\s*:\s*(\d+)""")
                .findAll(text)
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
                startedAtEpochMs = intField("startedAtEpochMs").toLong(),
                incomplete = boolField("incomplete"),
            )
        }

        private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
