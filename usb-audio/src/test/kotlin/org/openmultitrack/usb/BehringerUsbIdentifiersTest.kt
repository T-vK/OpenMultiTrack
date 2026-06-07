package org.openmultitrack.usb

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BehringerUsbIdentifiersTest {
    @Test
    fun detectsBehringerX32ByName() {
        assertThat(
            BehringerUsbIdentifiers.isLikelyBehringerMixer(
                BehringerUsbIdentifiers.VENDOR_ID_BEHINGER,
                "BEHRINGER X32",
            ),
        ).isTrue()
        assertThat(BehringerUsbIdentifiers.guessModel("BEHRINGER X32")).isEqualTo("X32")
    }

    @Test
    fun detectsXr18() {
        assertThat(BehringerUsbIdentifiers.guessModel("XR18")).isEqualTo("XR18")
    }

    @Test
    fun detectsFlow8() {
        assertThat(
            BehringerUsbIdentifiers.isLikelyBehringerMixer(
                BehringerUsbIdentifiers.VENDOR_ID_BEHINGER,
                "FLOW 8",
            ),
        ).isTrue()
        assertThat(BehringerUsbIdentifiers.guessModel("FLOW 8")).isEqualTo("FLOW8")
    }

    @Test
    fun ignoresOtherVendors() {
        assertThat(
            BehringerUsbIdentifiers.isLikelyBehringerMixer(0x1234, "X32"),
        ).isFalse()
    }
}
