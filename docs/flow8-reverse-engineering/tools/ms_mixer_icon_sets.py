"""Mixing Station mixer lines and icon picker layouts (MS APK 3.0.1).

Each supported mixer product line gets a **reserved slot block** on the sprite sheet:

* **x32** — 74 canonical scribble ids (MS ``wing_ch_*`` artwork, ids 2–74)
* **wing** — 130 WING picker slots
* **brand** — 74 reserved slots; channel icons are not bundled in the APK, so
  cells stay empty except slot 1 may show the MS mixer branding image
  (``mt_x32``, ``ah_sq``, …) when ``brand_fallback`` is enabled

Order follows Mixing Station's manufacturer list (``blob.F3.a.c()``), then FLOW 8.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Literal

IconFill = Literal["x32", "wing", "brand"]

X32_SLOT_COUNT = 74
WING_SLOT_COUNT = 130

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

# blob.t8.C1787a — WING picker pages (preset ranges per input type).
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
class MixerSection:
    key: str
    name: str
    brand_atlas: str
    slot_count: int
    fill: IconFill


# Mixing Station supported product lines (mixingstation.app feature list).
MIXER_SECTIONS: tuple[MixerSection, ...] = (
    # Behringer
    MixerSection("behringer-x32", "Behringer X32 / M32", "mt_x32", X32_SLOT_COUNT, "x32"),
    MixerSection("behringer-xair", "Behringer X-Air / XR", "mt_xair", X32_SLOT_COUNT, "x32"),
    MixerSection("behringer-wing", "Behringer Wing", "b_wing", WING_SLOT_COUNT, "wing"),
    # Midas
    MixerSection("midas-hd96", "Midas HD96", "m_hd", X32_SLOT_COUNT, "brand"),
    # Allen & Heath
    MixerSection("ah-dlive", "Allen & Heath dLive", "ah_dlive", X32_SLOT_COUNT, "brand"),
    MixerSection("ah-ilive", "Allen & Heath iLive", "ah_ilive", X32_SLOT_COUNT, "brand"),
    MixerSection("ah-avantis", "Allen & Heath Avantis", "ah_avantis", X32_SLOT_COUNT, "brand"),
    MixerSection("ah-gld", "Allen & Heath GLD", "ah_gld", X32_SLOT_COUNT, "brand"),
    MixerSection("ah-sq", "Allen & Heath SQ", "ah_sq", X32_SLOT_COUNT, "brand"),
    MixerSection("ah-sqp", "Allen & Heath SQ+", "ah_sqp", X32_SLOT_COUNT, "brand"),
    MixerSection("ah-qu", "Allen & Heath Qu", "ah_qu", X32_SLOT_COUNT, "brand"),
    MixerSection("ah-qu5", "Allen & Heath Qu-5", "ah_qu5", X32_SLOT_COUNT, "brand"),
    MixerSection("ah-cq", "Allen & Heath CQ", "ah_cq", X32_SLOT_COUNT, "brand"),
    # Soundcraft
    MixerSection("soundcraft-si", "Soundcraft Si", "sc_si", X32_SLOT_COUNT, "brand"),
    MixerSection("soundcraft-vi", "Soundcraft Vi", "sc_vi", X32_SLOT_COUNT, "brand"),
    MixerSection("soundcraft-ui", "Soundcraft Ui", "sc_ui", X32_SLOT_COUNT, "brand"),
    # Mackie
    MixerSection("mackie-dl", "Mackie DL", "mackie_dl", X32_SLOT_COUNT, "brand"),
    # Yamaha
    MixerSection("yamaha-dm7", "Yamaha DM7", "yam_dm7", X32_SLOT_COUNT, "brand"),
    MixerSection("yamaha-dm3", "Yamaha DM3", "yam_dm3", X32_SLOT_COUNT, "brand"),
    MixerSection("yamaha-cl", "Yamaha CL", "yam_cl", X32_SLOT_COUNT, "brand"),
    MixerSection("yamaha-ql", "Yamaha QL", "yam_ql", X32_SLOT_COUNT, "brand"),
    MixerSection("yamaha-tf", "Yamaha TF", "yam_tf", X32_SLOT_COUNT, "brand"),
    # PreSonus
    MixerSection("presonus-sl3", "PreSonus StudioLive III", "ps_sl", X32_SLOT_COUNT, "brand"),
    # QSC
    MixerSection("qsc-touchmix", "QSC TouchMix", "qsc_tm", X32_SLOT_COUNT, "brand"),
    # TASCAM
    MixerSection("tascam-sonicview", "TASCAM Sonicview", "tc_sv", X32_SLOT_COUNT, "brand"),
    # DiGiCo
    MixerSection("digico", "DiGiCo", "dc_sd", X32_SLOT_COUNT, "brand"),
    # Violet Audio
    MixerSection("violet-dmix", "Violet Audio dMix", "va_dmix", X32_SLOT_COUNT, "brand"),
    # Blackmagic
    MixerSection("blackmagic-fairlight", "Blackmagic Fairlight Live", "bfl", X32_SLOT_COUNT, "brand"),
)


def all_brand_keys() -> set[str]:
    return {section.brand_atlas for section in MIXER_SECTIONS}


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
    mixer_key: str
    mixer_name: str
    fill: IconFill
    slot_index: int
    label: str
    path: Path | None
    icon_id: int | None = None
    wing_key: str | None = None
    brand_atlas: str | None = None
    use_brand: bool = False


def section_slots(
    section: MixerSection,
    wing_assets: Path,
    brand_assets: Path,
    *,
    brand_fallback: bool = True,
) -> list[SheetSlot]:
    """Build reserved slots for one mixer line."""
    brand_path = brand_assets / f"{section.brand_atlas}.png"
    brand_exists = brand_path.is_file()
    rows: list[SheetSlot] = []

    if section.fill == "wing":
        from wing_icon_labels import wing_label

        for index, wing_key in enumerate(wing_picker_keys()):
            icon_path = wing_assets / f"{wing_key}.png"
            rows.append(
                SheetSlot(
                    mixer_key=section.key,
                    mixer_name=section.name,
                    fill=section.fill,
                    slot_index=index,
                    label=wing_label(wing_key),
                    path=icon_path if icon_path.is_file() else None,
                    wing_key=wing_key,
                    brand_atlas=section.brand_atlas,
                )
            )
        return rows

    for icon_id in range(1, section.slot_count + 1):
        wing_key = x32_icon_id_to_wing_key(icon_id) if section.fill == "x32" else None
        icon_path = wing_assets / f"{wing_key}.png" if wing_key else None
        if icon_path is not None and not icon_path.is_file():
            icon_path = None

        use_brand = False
        path: Path | None = icon_path
        if path is None and section.fill == "brand" and brand_fallback and brand_exists and icon_id == 1:
            path = brand_path
            use_brand = True
        elif path is None and section.fill == "x32" and icon_id == 1 and brand_fallback and brand_exists:
            path = brand_path
            use_brand = True

        label = _x32_icon_label(icon_id) if section.fill in ("x32", "brand") and not use_brand else section.name

        rows.append(
            SheetSlot(
                mixer_key=section.key,
                mixer_name=section.name,
                fill=section.fill,
                slot_index=icon_id - 1,
                label=label,
                path=path,
                icon_id=icon_id,
                wing_key=wing_key,
                brand_atlas=section.brand_atlas,
                use_brand=use_brand,
            )
        )
    return rows


def all_mixer_slots(
    wing_assets: Path,
    brand_assets: Path,
    *,
    brand_fallback: bool = True,
) -> list[SheetSlot]:
    rows: list[SheetSlot] = []
    for section in MIXER_SECTIONS:
        rows.extend(section_slots(section, wing_assets, brand_assets, brand_fallback=brand_fallback))
    return rows


def ms_slot_count() -> int:
    return sum(section.slot_count for section in MIXER_SECTIONS)
