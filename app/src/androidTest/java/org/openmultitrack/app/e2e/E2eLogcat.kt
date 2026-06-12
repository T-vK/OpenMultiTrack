package org.openmultitrack.app.e2e

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream

/** Reads recent device logcat from instrumented tests (for playback stall diagnosis). */
object E2eLogcat {
    fun dumpRecent(lineCount: Int = 400, vararg tags: String): String {
        val filter = tags.joinToString(" ") { "$it:I" }.trim()
        val command = buildList {
            add("logcat")
            add("-d")
            add("-t")
            add(lineCount.toString())
            if (filter.isNotEmpty()) {
                addAll(filter.split(" "))
            }
        }.joinToString(" ")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return pfd.use { readUtf8(it) }
    }

    fun assertNoPlaybackFaults(log: String) {
        val faults = listOf(
            "LIBUSB_ERROR_IO",
            "playback stalled",
            "libusb resubmit failed",
            "uac2 playback libusb transfer status",
        ).filter { fault -> log.contains(fault, ignoreCase = true) }
        check(faults.isEmpty()) {
            "Logcat contains playback fault(s): ${faults.joinToString()}\n---\n$log"
        }
    }

    private fun readUtf8(pfd: ParcelFileDescriptor): String =
        FileInputStream(pfd.fileDescriptor).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
}
