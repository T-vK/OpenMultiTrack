package org.openmultitrack.sessionio.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class SessionCueFileTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun roundTripCueFile() {
        val dir = temp.newFolder("session")
        val marks = listOf(
            SessionTrackmark(1, "Track 01", 0f),
            SessionTrackmark(2, "Verse", 90f),
        )
        SessionCueFile.write(dir, marks, "channel01.wav")
        val read = SessionCueFile.read(dir)
        assertThat(read).hasSize(2)
        assertThat(read[0].title).isEqualTo("Track 01")
        assertThat(read[1].startSec).isWithin(0.05f).of(90f)
    }

    @Test
    fun parseUserTimestampAcceptsCommonFormats() {
        assertThat(SessionCueFile.parseUserTimestamp("1:30")).isWithin(0.01f).of(90f)
        assertThat(SessionCueFile.parseUserTimestamp("0:00")).isWithin(0.01f).of(0f)
        assertThat(SessionCueFile.parseUserTimestamp("12.5")).isWithin(0.01f).of(12.5f)
    }
}
