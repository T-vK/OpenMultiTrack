"""Mixing Station / X32 scribble icon ids (1–74). Mirrors MixingStationIcons.kt."""

from __future__ import annotations

ICON_MIN = 1
ICON_MAX = 74

# Named constants (MixingStationIcons.kt)
HANDHELD_MIC = 50
ELECTRIC_BASS = 17
ACOUSTIC_GUITAR = 23
VIOLIN = 39
TAPE = 60
PC = 62
SPEAKER_RIGHT = 64
SPEAKER_LEFT = 65

ICON_CONSTANTS: dict[int, str] = {
    17: "ELECTRIC_BASS",
    23: "ACOUSTIC_GUITAR",
    39: "VIOLIN",
    50: "HANDHELD_MIC",
    60: "TAPE",
    62: "PC",
    64: "SPEAKER_RIGHT",
    65: "SPEAKER_LEFT",
}

# X32 / Mixing Station icon labels (behringer-icons / forum list)
ICON_LABELS: list[str] = [
    "",  # 0 unused
    "blank",
    "kick-back",
    "kick-front",
    "snare-top",
    "snare-bottom",
    "tom-high",
    "tom-medium",
    "floor-tom",
    "hi-hat",
    "crash",
    "drum-kit",
    "cowbell",
    "bongos",
    "congas",
    "tambourine",
    "vibraphone",
    "electric-bass",
    "acoustic-bass",
    "contrabass",
    "les-paul",
    "ibanez",
    "washburn",
    "acoustic-guitar",
    "bass-amp",
    "guitar-amp",
    "amp-cabinet",
    "piano",
    "organ",
    "harpsichord",
    "keyboard",
    "synthesizer-1",
    "synthesizer-2",
    "synthesizer-3",
    "keytar",
    "trumpet",
    "trombone",
    "saxophone",
    "clarinet",
    "violin",
    "cello",
    "male-vocal",
    "female-vocal",
    "choir",
    "hand-sign",
    "talk-a",
    "talk-b",
    "large-diaphragm-mic",
    "condenser-mic-left",
    "condenser-mic-right",
    "handheld-mic",
    "wireless-mic",
    "podium-mic",
    "headset-mic",
    "xlr",
    "trs",
    "trs-left",
    "trs-right",
    "rca-left",
    "rca-right",
    "tape",
    "fx",
    "computer",
    "wedge",
    "speaker-right",
    "speaker-left",
    "speaker-array",
    "speaker-on-pole",
    "amp-rack",
    "controls",
    "fader",
    "mix-bus",
    "matrix",
    "routing",
    "smiley",
]

ICON_EMOJI: list[str] = [
    "",
    "",
    "🥁", "🥁", "🪘", "🪘", "🥁", "🥁", "🥁", "🎩", "🔔",
    "🥁", "🔔", "🪘", "🪘", "🎵", "🎵", "🎸", "🎸", "🎸", "🎸",
    "🎸", "🎸", "🎸", "🔊", "🔊", "🔊", "🎹", "🎹", "🎹", "🎹",
    "🎹", "🎹", "🎹", "🎹", "🎺", "🎺", "🎷", "🎷", "🎻", "🎻",
    "🎤", "🎤", "👥", "✋", "🗣", "🗣", "🎙", "🎙", "🎙", "🎤",
    "🎤", "🎤", "🎧", "🔌", "🔌", "🔌", "🔌", "🔌", "🔌", "📼",
    "✨", "💻", "🔊", "🔈", "🔉", "🔊", "🔊", "🎛", "🎛", "🎚",
    "🔀", "🔀", "🔀", "😊",
]


def icon_label(icon_id: int | None) -> str | None:
    if icon_id is None or not (ICON_MIN <= icon_id <= ICON_MAX):
        return None
    return ICON_LABELS[icon_id] or None


def icon_constant(icon_id: int | None) -> str | None:
    if icon_id is None:
        return None
    return ICON_CONSTANTS.get(icon_id)


def icon_emoji(icon_id: int | None) -> str | None:
    if icon_id is None or not (ICON_MIN <= icon_id <= ICON_MAX):
        return None
    ch = ICON_EMOJI[icon_id]
    return ch or None


def format_icon(icon_id: int | None, flow_label: str | None = None) -> str:
    """Human-readable icon field for script output."""
    if icon_id is None:
        return "—"
    const = icon_constant(icon_id)
    label = icon_label(icon_id)
    emoji = icon_emoji(icon_id)
    parts = [f"{icon_id:02d}"]
    if flow_label:
        parts.append(flow_label)
    elif const:
        parts.append(const)
    elif label:
        parts.append(label)
    if emoji:
        parts.append(emoji)
    return " ".join(parts)
