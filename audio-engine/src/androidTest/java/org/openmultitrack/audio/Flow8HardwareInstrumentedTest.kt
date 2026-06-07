package org.openmultitrack.audio

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.audio.test.RequiresUsbDevice
import org.openmultitrack.audio.test.UsbDeviceRule

/**
 * Live USB tests — run on an emulator with Flow 8 passthrough:
 * `scripts/run-emulator-with-flow8.sh`
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x050c)
class Flow8HardwareInstrumentedTest {
    @get:Rule
    val usbDeviceRule = UsbDeviceRule()

    @Test
    fun liveDescriptorReportsTenCaptureFourPlayback() {
        val device = findFlow8()
        val raw = readRawDescriptors(device)
        val caps = NativeUac2Probe.parseConfigDescriptor(raw)

        assertThat(caps).isNotNull()
        assertThat(caps!!.parseOk).isTrue()
        assertThat(caps.maxCaptureChannels).isAtLeast(10)
        assertThat(caps.maxPlaybackChannels).isAtLeast(4)
    }

    @Test
    fun liveSelectBestAltsMatchFlow8RecordingMode() {
        val device = findFlow8()
        val caps = NativeUac2Probe.parseConfigDescriptor(readRawDescriptors(device))!!

        val capture = NativeUac2Probe.selectBestCaptureAlt(caps, minChannels = 10)!!
        val playback = NativeUac2Probe.selectBestPlaybackAlt(caps, minChannels = 4)!!

        assertThat(capture.channels).isAtLeast(10)
        assertThat(capture.bitResolution).isEqualTo(32)
        assertThat(capture.subframeBytes).isEqualTo(4)
        assertThat(playback.channels).isAtLeast(4)
        assertThat(playback.bitResolution).isEqualTo(32)
    }

    private fun findFlow8(): UsbDevice {
        val usbManager = instrumentationContext.getSystemService(UsbManager::class.java)
        return usbManager.deviceList.values.first { device ->
            device.vendorId == FLOW8_VENDOR_ID && device.productId == FLOW8_PRODUCT_ID
        }
    }

    private fun readRawDescriptors(device: UsbDevice): ByteArray {
        val usbManager = instrumentationContext.getSystemService(UsbManager::class.java)
        org.junit.Assume.assumeTrue(
            "Grant USB permission to ${instrumentationContext.packageName} (see scripts/run-emulator-with-flow8.sh)",
            usbManager.hasPermission(device),
        )
        val connection = usbManager.openDevice(device)
            ?: error("openDevice failed after permission grant")
        return try {
            connection.rawDescriptors
        } finally {
            connection.close()
        }
    }

    private val instrumentationContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val FLOW8_VENDOR_ID = 0x1397
        const val FLOW8_PRODUCT_ID = 0x050c
    }
}
