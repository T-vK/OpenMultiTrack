package org.openmultitrack.sessionio.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelFileNamingTest {
    @Test
    fun fileName_withLabel() {
        assertThat(ChannelFileNaming.fileName(0, "Guitar Frank")).isEqualTo("channel01 - Guitar Frank.wav")
    }

    @Test
    fun fileName_withoutLabel() {
        assertThat(ChannelFileNaming.fileName(17, null)).isEqualTo("channel18.wav")
    }

    @Test
    fun displayName() {
        assertThat(ChannelFileNaming.displayName(0, "Guitar Frank")).isEqualTo("#1 Guitar Frank")
    }
}
