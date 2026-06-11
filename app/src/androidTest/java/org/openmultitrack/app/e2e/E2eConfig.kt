package org.openmultitrack.app.e2e

import androidx.test.platform.app.InstrumentationRegistry

/** Instrumentation arguments for dual-device e2e orchestration. */
object E2eConfig {
    const val TAG = "OmtE2e"
    const val SYNC_PORT = 8767
    const val DEFAULT_TEST_PIN = "424242"
    const val RECORD_SECONDS = 5
    /** Minimum session length for soundcheck zoom (view window floor is 30s). */
    const val ZOOM_RECORD_SECONDS = 40
    const val XR18_VENDOR_ID = 0x1397
    const val XR18_PRODUCT_ID = 0x00d4

    private val args get() = InstrumentationRegistry.getArguments()

    val hostIp: String? get() = args.getString("host_ip")?.takeIf { it.isNotBlank() }
    /** Preferred XR18 OSC IP (optional — discovery runs when omitted). */
    val oscHost: String? get() = args.getString("osc_host")?.takeIf { it.isNotBlank() }
    val pairingPin: String get() = args.getString("pairing_pin")?.takeIf { it.isNotBlank() } ?: DEFAULT_TEST_PIN
    val e2eRole: String? get() = args.getString("e2e_role")?.takeIf { it.isNotBlank() }
}
