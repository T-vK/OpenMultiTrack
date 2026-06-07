package org.openmultitrack.domain.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Smoke test that compute-semver.sh runs and returns parseable output.
 * Full bump logic is validated in shell on CI and locally via git tags.
 */
class SemverScriptTest {
    @Test
    fun computeSemverScript_existsAndRuns() {
        val root = File(System.getProperty("user.dir"))
        val script = File(root, "scripts/compute-semver.sh")
        if (!script.exists()) {
            // Running from a module subdir in IDE — skip.
            return
        }
        val process = ProcessBuilder("bash", script.absolutePath)
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        assertTrue("compute-semver failed: $output", code == 0)
        assertTrue(output.contains("version="))
        assertTrue(output.contains("tag=v"))
    }
}
