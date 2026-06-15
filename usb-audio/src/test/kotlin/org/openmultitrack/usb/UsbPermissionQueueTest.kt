package org.openmultitrack.usb

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class UsbPermissionQueueTest {
    @After
    fun tearDown() {
        UsbPermissionCoordinator.resetForTest()
    }

    @Test
    fun startsIdle() {
        val queue = UsbPermissionQueue()
        assertThat(queue.hasPending).isFalse()
    }

    @Test
    fun resetForTestClearsInFlightState() {
        val queue = UsbPermissionQueue()
        queue.resetForTest()
        assertThat(queue.hasPending).isFalse()
    }
}
