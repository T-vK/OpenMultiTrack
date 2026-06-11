package org.openmultitrack.app.e2e

import android.content.Context
import org.junit.rules.ExternalResource
import org.openmultitrack.app.data.AppSettingsStore

/**
 * Saves and restores display prefs so waveform e2e can opt in without clobbering the user profile.
 */
object E2eDisplaySettings {
    private data class Snapshot(
        val showWaveforms: Boolean,
        val showVuMeters: Boolean,
        val recordWaveformNormalized: Boolean,
    )

    fun guard(context: Context, enableForTest: Boolean = true): AutoCloseable {
        val store = AppSettingsStore(context)
        val saved = Snapshot(
            showWaveforms = store.showWaveforms,
            showVuMeters = store.showVuMeters,
            recordWaveformNormalized = store.recordWaveformNormalized,
        )
        if (enableForTest) {
            store.showWaveforms = true
            store.showVuMeters = true
            store.recordWaveformNormalized = true
        }
        return AutoCloseable {
            store.showWaveforms = saved.showWaveforms
            store.showVuMeters = saved.showVuMeters
            store.recordWaveformNormalized = saved.recordWaveformNormalized
        }
    }
}

/** Enables waveforms/VU/normalization for one test class, then restores prior values. */
class E2eWaveformDisplayRule(
    private val contextProvider: () -> Context,
) : ExternalResource() {
    private var guard: AutoCloseable? = null

    override fun before() {
        guard = E2eDisplaySettings.guard(contextProvider())
    }

    override fun after() {
        guard?.close()
        guard = null
    }
}
