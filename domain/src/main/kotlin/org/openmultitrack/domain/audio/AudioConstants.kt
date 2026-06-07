package org.openmultitrack.domain.audio

object AudioConstants {
    /** Maximum channels supported by native engine and WAV writer (X32 = 32, headroom for future). */
    const val MAX_CHANNELS = 64

    /** Minimum sensible multitrack session. */
    const val MIN_CHANNELS = 1

    const val DEFAULT_SAMPLE_RATE = 48_000
}
