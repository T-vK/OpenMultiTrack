package org.openmultitrack.usb

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class UsbPermissionCoordinatorStableKeyTest {
    @After
    fun tearDown() {
        UsbPermissionCoordinator.resetForTest()
    }

    @Test
    fun stableKeyUsesSerialWhenPresent() {
        val key = UsbPermissionCoordinator.stableKeyForParts(
            vendorId = 0x1397,
            productId = 0x00d4,
            serial = "ABC123",
            deviceName = "/dev/bus/usb/001/002",
        )
        assertThat(key).isEqualTo("5015:212:ABC123")
    }

    @Test
    fun stableKeyFallsBackToDeviceNameWithoutSerial() {
        val key = UsbPermissionCoordinator.stableKeyForParts(
            vendorId = 0x1397,
            productId = 0x050c,
            serial = null,
            deviceName = "/dev/bus/usb/001/003",
        )
        assertThat(key).isEqualTo("5015:1292:/dev/bus/usb/001/003")
    }
}
