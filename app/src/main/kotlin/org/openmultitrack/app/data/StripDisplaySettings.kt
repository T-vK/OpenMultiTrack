package org.openmultitrack.app.data

/** How channel index numbers appear on strips when scribble labels exist. */
enum class StripNumberMode(val label: String) {
    /** Show number and label (default). */
    BOTH("Number + label"),
    /** Hide the channel number when a label is present. */
    HIDE_WHEN_LABELED("Hide number when labeled"),
    /** Always show only the channel number, ignore labels in the strip text. */
    NUMBERS_ONLY("Numbers only"),
}

/** How Mixing Station icon ids render on channel strips. */
enum class StripIconMode(val label: String) {
    /** Show icon emoji when the scribble name encodes one. */
    SHOW("Show icon"),
    /** Show only the icon (no label text in the strip). */
    ICON_ONLY("Icon only"),
    /** Never show icons. */
    HIDE("Hide icon"),
}
