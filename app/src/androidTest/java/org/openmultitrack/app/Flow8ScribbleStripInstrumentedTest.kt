package org.openmultitrack.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.scribble.Flow8BleScribbleImporter
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbAppProcessRule
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.mixer.behringer.ScribbleStripLabel

/**
 * Validates FLOW 8 channel names over BLE while USB is attached.
 * Enable FLOW 8 pairing mode when the test starts; it only stays active ~15 s per press.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x050c)
class Flow8ScribbleStripInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    @Test(timeout = 360_000)
    fun importChannelLabelsFromBle() {
        // Importer posts BLE work to the main looper — never runBlocking on the activity thread.
        runBlocking(Dispatchers.IO) {
            val context = usbAppProcessRule.runOnActivity { it.applicationContext }
            val labels = Flow8BleScribbleImporter(
                context,
                discoveryTimeoutMs = Flow8BleScribbleImporter.DEFAULT_DISCOVERY_TIMEOUT_MS,
            ).fetchChannelLabels().getOrThrow()
            assertThat(labels).isNotEmpty()
            assertThat(labels.size).isAtMost(7)
            assertThat(labels.count { !it.name.isNullOrBlank() }).isAtLeast(1)

            val named = labels.filter { !it.name.isNullOrBlank() }
            assertThat(named.first().name).isNotEmpty()
            // Names should parse without raw _icon suffix in display form when present.
            named.forEach { label ->
                val parsed = ScribbleStripLabel.parse(label.name)
                assertThat(parsed.displayName).doesNotContainMatch("""_\d{1,2}$""")
            }
        }
    }

}
