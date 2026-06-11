package org.openmultitrack.app.ui.daw

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Usb
import androidx.compose.ui.graphics.vector.ImageVector
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.mixer.VirtualMixer

fun mixerProfileIcon(profile: MixerProfile?): ImageVector = when {
    profile == null -> Icons.Default.GraphicEq
    VirtualMixer.isSineGenerator(profile) -> Icons.Default.Science
    profile.vendorId == 0x1397 -> Icons.Default.GraphicEq
    else -> Icons.Default.Usb
}
