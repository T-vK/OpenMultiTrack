package org.openmultitrack.app.test

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresUsbDevice(
    val vendorId: Int = 0,
    val productId: Int = 0,
)
