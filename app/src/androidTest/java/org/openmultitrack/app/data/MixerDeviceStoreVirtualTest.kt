package org.openmultitrack.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.domain.mixer.VirtualMixer

@RunWith(AndroidJUnit4::class)
class MixerDeviceStoreVirtualTest {
    private val store = MixerDeviceStore(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun addVirtualSineMixer_isIdempotent() {
        val first = store.addVirtualSineMixer()
        val second = store.addVirtualSineMixer()
        assertThat(second.id).isEqualTo(first.id)
        assertThat(store.listMixers().count { VirtualMixer.isSineGenerator(it) }).isEqualTo(1)
    }
}
