package org.openmultitrack.app.service

enum class SessionActivityKind {
    GENERIC,
    USB,
    LAN,
    DISK,
    ROUTING,
    ;

    val glyph: String
        get() = when (this) {
            USB -> "🔌"
            LAN -> "📡"
            DISK -> "💾"
            ROUTING -> "🔀"
            GENERIC -> "⏳"
        }
}

/** Short-lived UI feedback while transport or session work runs off the hot path. */
data class SessionActivityStatus(
    val label: String,
    val kind: SessionActivityKind = SessionActivityKind.GENERIC,
    val showSpinner: Boolean = true,
    val progress: Float? = null,
    /** When set, [MixerSessionController.clearActivity] only clears matching activity. */
    val tag: String? = null,
) {
    val displayLabel: String get() = "${kind.glyph} $label"

    /** True when this activity should disable record/play until it clears. */
    val blocksTransport: Boolean
        get() = tag != "usb-probe"
}
