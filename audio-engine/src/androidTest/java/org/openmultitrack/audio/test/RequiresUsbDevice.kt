package org.openmultitrack.audio.test

/**
 * Marks a test that needs a USB audio device attached (e.g. Flow 8 via emulator passthrough).
 * Skipped when [UsbDeviceRule] finds no matching hardware.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresUsbDevice(
    val vendorId: Int = 0,
    val productId: Int = 0,
)
