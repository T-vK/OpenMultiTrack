package org.openmultitrack.app.routing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.app.data.MixerRoutingAutomationConfig
import org.openmultitrack.app.data.RoutingAutomationMethod
import org.openmultitrack.app.data.RoutingAutomationTrigger
import org.openmultitrack.domain.mixer.MixerProfile

class RoutingTransportOscTest {
    private val xr18 = MixerProfile(
        id = "xr18",
        usbDeviceName = null,
        vendorId = 0x1397,
        productId = 0x7508,
        serialNumber = null,
        productName = "XR18",
        displayName = "XR18",
        oscHost = "192.168.1.100",
    )

    @Test
    fun willApplyOnRecordButton_falseWhenModeEnterTrigger() {
        val config = MixerRoutingAutomationConfig(
            method = RoutingAutomationMethod.SNAPSHOT_SLOT,
            trigger = RoutingAutomationTrigger.ON_MODE_ENTER,
            recordSnapshotSlot = 3,
        )
        assertThat(RoutingTransportOsc.willApplyOnRecordButton(xr18, config, setOf(0))).isFalse()
    }

    @Test
    fun willApplyOnRecordButton_trueWhenTransportTriggerAndSnapshotConfigured() {
        val config = MixerRoutingAutomationConfig(
            method = RoutingAutomationMethod.SNAPSHOT_SLOT,
            trigger = RoutingAutomationTrigger.ON_TRANSPORT_BUTTON,
            recordSnapshotSlot = 3,
        )
        assertThat(RoutingTransportOsc.willApplyOnRecordButton(xr18, config, emptySet())).isTrue()
    }

    @Test
    fun willRestoreOnTransportStop_falseWhenModeEnterTrigger() {
        val config = MixerRoutingAutomationConfig(
            method = RoutingAutomationMethod.SNAPSHOT_SLOT,
            trigger = RoutingAutomationTrigger.ON_MODE_ENTER,
            idleSnapshotSlot = 1,
        )
        assertThat(RoutingTransportOsc.willRestoreOnTransportStop(config)).isFalse()
    }
}
