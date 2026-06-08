package org.openmultitrack.app.root

import org.openmultitrack.audio.OmtLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Best-effort root command helper for optional virtual-microphone setup. */
object RootShell {
  private var cachedAvailable: Boolean? = null

    fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val ok = runCommand("id -u", timeoutSeconds = 3)
            .map { it.trim() == "0" }
            .getOrDefault(false)
        cachedAvailable = ok
        OmtLog.i("Root", "root available=$ok")
        return ok
    }

    fun runCommand(command: String, timeoutSeconds: Long = 10): Result<String> {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                error("su command timed out: $command")
            }
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            if (process.exitValue() != 0) {
                error("su exit ${process.exitValue()}: $output")
            }
            output
        }.onFailure { OmtLog.w("Root", "command failed: $command", it) }
    }

    fun invalidateCache() {
        cachedAvailable = null
    }
}
