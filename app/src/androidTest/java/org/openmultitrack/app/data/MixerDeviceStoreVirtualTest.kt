package org.openmultitrack.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.domain.mixer.VirtualMixer

@RunWith(AndroidJUnit4::class)
class MixerDeviceStoreVirtualTest {
    @Test
    fun addVirtualDemoMixer_isIdempotent() {
        val store = MixerDeviceStore(ApplicationProvider.getApplicationContext())
        val first = store.addVirtualDemoMixer()
        val second = store.addVirtualDemoMixer()
        assertThat(first.id).isEqualTo(second.id)
        assertThat(store.listMixers().count { VirtualMixer.isDemoMixer(it) }).isEqualTo(1)
        assertThat(first.displayName).isEqualTo(VirtualMixer.DISPLAY_NAME)
    }
}
