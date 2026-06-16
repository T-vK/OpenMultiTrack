"""Mixing Station mixer list and channel icon mapping (MS APK 3.0.1).

Sprite sheet layout (see ``make_contact_sheet.py``):

1. **74** canonical channel scribble icons (`mixing-station/{id}.png`, X32 BMP art)
2. **100** FLOW 8 picker icons
3. **28** mixer branding images (one per MS-supported product line)
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

X32_SLOT_COUNT = 74

# blob.b9.e — X32/M32/X-Air icon id 2..74 → wing_ch suffix (73 entries).
_X32_WING_SUFFIXES: tuple[str, ...] = (
    "0200",
    "0201",
    "0202",
    "0203",
    "0206",
    "0207",
    "0209",
    "0205",
    "0212",
    "0210",
    "0213",
    "0215",
    "0216",
    "0214",
    "0220",
    "0300",
    "0301",
    "0302",
    "0305",
    "0306",
    "0307",
    "0304",
    "0500",
    "0502",
    "0501",
    "0400",
    "0401",
    "0407",
    "0408",
    "0405",
    "0402",
    "0404",
    "0406",
    "0314",
    "0313",
    "0312",
    "0311",
    "0308",
    "0310",
    "0114",
    "0113",
    "0112",
    "0600",
    "0603",
    "0604",
    "0100",
    "0103",
    "5001",
    "0101",
    "0102",
    "0105",
    "0601",
    "0001",
    "0002",
    "5002",
    "5003",
    "5004",
    "5005",
    "0612",
    "5006",
    "0605",
    "0517",
    "0515",
    "0516",
    "0523",
    "0512",
    "0614",
    "0007",
    "0005",
    "0012",
    "0010",
    "0011",
    "0013",
)

# blob.t8.C1787a — WING picker pages (for wing_icon_labels / extraction only).
_WING_PICKER_RANGES: tuple[tuple[int, int, int], ...] = (
    (0, 1, 14),
    (1, 0, 14),
    (2, 0, 24),
    (3, 0, 19),
    (4, 0, 9),
    (5, 0, 24),
    (6, 0, 14),
    (50, 1, 6),
)


@dataclass(frozen=True)
class MixerLine:
    key: str
    name: str
    brand_atlas: str


# Mixing Station supported product lines (mixingstation.app feature list).
MIXER_LINES: tuple[MixerLine, ...] = (
    MixerLine("behringer-x32", "Behringer X32 / M32", "mt_x32"),
    MixerLine("behringer-xair", "Behringer X-Air / XR", "mt_xair"),
    MixerLine("behringer-wing", "Behringer Wing", "b_wing"),
    MixerLine("midas-hd96", "Midas HD96", "m_hd"),
    MixerLine("ah-dlive", "Allen & Heath dLive", "ah_dlive"),
    MixerLine("ah-ilive", "Allen & Heath iLive", "ah_ilive"),
    MixerLine("ah-avantis", "Allen & Heath Avantis", "ah_avantis"),
    MixerLine("ah-gld", "Allen & Heath GLD", "ah_gld"),
    MixerLine("ah-sq", "Allen & Heath SQ", "ah_sq"),
    MixerLine("ah-sqp", "Allen & Heath SQ+", "ah_sqp"),
    MixerLine("ah-qu", "Allen & Heath Qu", "ah_qu"),
    MixerLine("ah-qu5", "Allen & Heath Qu-5", "ah_qu5"),
    MixerLine("ah-cq", "Allen & Heath CQ", "ah_cq"),
    MixerLine("soundcraft-si", "Soundcraft Si", "sc_si"),
    MixerLine("soundcraft-vi", "Soundcraft Vi", "sc_vi"),
    MixerLine("soundcraft-ui", "Soundcraft Ui", "sc_ui"),
    MixerLine("mackie-dl", "Mackie DL", "mackie_dl"),
    MixerLine("yamaha-dm7", "Yamaha DM7", "yam_dm7"),
    MixerLine("yamaha-dm3", "Yamaha DM3", "yam_dm3"),
    MixerLine("yamaha-cl", "Yamaha CL", "yam_cl"),
    MixerLine("yamaha-ql", "Yamaha QL", "yam_ql"),
    MixerLine("yamaha-tf", "Yamaha TF", "yam_tf"),
    MixerLine("presonus-sl3", "PreSonus StudioLive III", "ps_sl"),
    MixerLine("qsc-touchmix", "QSC TouchMix", "qsc_tm"),
    MixerLine("tascam-sonicview", "TASCAM Sonicview", "tc_sv"),
    MixerLine("digico", "DiGiCo", "dc_sd"),
    MixerLine("violet-dmix", "Violet Audio dMix", "va_dmix"),
    MixerLine("blackmagic-fairlight", "Blackmagic Fairlight Live", "bfl"),
)


def all_brand_keys() -> set[str]:
    return {line.brand_atlas for line in MIXER_LINES}


def wing_ch_key(type_code: int, preset: int) -> str:
    code = type_code * 100 + preset
    if code < 10:
        suffix = f"000{code}"
    elif code < 100:
        suffix = f"00{code}"
    elif code < 1000:
        suffix = f"0{code}"
    else:
        suffix = str(code)
    return f"wing_ch_{suffix}"


def x32_icon_id_to_wing_key(icon_id: int) -> str | None:
    if icon_id == 1:
        return None
    if 2 <= icon_id <= 74:
        return f"wing_ch_{_X32_WING_SUFFIXES[icon_id - 2]}"
    return None


def wing_picker_keys() -> list[str]:
    keys: list[str] = []
    for type_code, start, end in _WING_PICKER_RANGES:
        for preset in range(start, end + 1):
            keys.append(wing_ch_key(type_code, preset))
    return keys


def _x32_icon_label(icon_id: int) -> str:
    from mixing_station_display_labels import display_label
    from mixing_station_icons import ICON_LABELS

    return display_label(icon_id) or ICON_LABELS[icon_id]


@dataclass
class SheetSlot:
    source: str
    label: str
    path: Path | None
    icon_id: int | None = None
    wing_key: str | None = None
    mixer_key: str | None = None
    brand_atlas: str | None = None


def ms_channel_icon_slots(ms_assets: Path) -> list[SheetSlot]:
    """Canonical MS / X32 channel scribble icons (ids 1–74), once."""
    rows: list[SheetSlot] = []
    for icon_id in range(1, X32_SLOT_COUNT + 1):
        icon_path = ms_assets / f"{icon_id}.png"
        if not icon_path.is_file():
            icon_path = None
        rows.append(
            SheetSlot(
                source="mixing-station",
                label=_x32_icon_label(icon_id),
                path=icon_path,
                icon_id=icon_id,
            )
        )
    return rows


def mixer_brand_slots(brand_assets: Path) -> list[SheetSlot]:
    """One MS mixer branding image per supported product line."""
    rows: list[SheetSlot] = []
    for line in MIXER_LINES:
        brand_path = brand_assets / f"{line.brand_atlas}.png"
        rows.append(
            SheetSlot(
                source="mixer-brand",
                label=line.name,
                path=brand_path if brand_path.is_file() else None,
                mixer_key=line.key,
                brand_atlas=line.brand_atlas,
            )
        )
    return rows
