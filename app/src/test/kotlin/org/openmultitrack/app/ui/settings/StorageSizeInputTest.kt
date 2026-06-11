package org.openmultitrack.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageSizeInputTest {
    @Test
    fun parsesMegabytesAndGigabytes() {
        assertEquals(500L * 1024 * 1024, StorageSizeInput.parse("500 MB"))
        assertEquals(1024L * 1024 * 1024, StorageSizeInput.parse("1 GB"))
        assertEquals(0L, StorageSizeInput.parse(""))
        assertEquals(0L, StorageSizeInput.parse("off"))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(StorageSizeInput.parse("lots of space"))
    }

    @Test
    fun formatsRoundTrip() {
        val bytes = 500L * 1024 * 1024
        assertEquals("500 MB", StorageSizeInput.format(bytes))
        assertEquals(bytes, StorageSizeInput.parse(StorageSizeInput.format(bytes)))
    }
}
