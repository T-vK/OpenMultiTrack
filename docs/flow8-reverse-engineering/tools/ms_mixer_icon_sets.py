"""Mixing Station channel icon picker layouts (from MS APK 3.0.1).

Mixing Station bundles two distinct scribble picker artwork sets in its atlas:

* **X32 / M32 / X-Air / XR** — canonical desk ids **1–74** map to **73** ``wing_ch_*``
  textures (id **1** is blank). See ``blob.b9.e`` / ``C1727b.m()`` in the APK.
* **WING** — **130** categorized ``wing_ch_*`` slots (types 0–6 plus tail block 50xx).
  See ``blob.t8.C1787a`` in the APK.

Other supported mixers (HD96, Allen & Heath, Yamaha, …) use icons supplied by the
connected desk; their artwork is not shipped in the Mixing Station APK.
"""

from __future__ import annotations

from pathlib import Path

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

MIXER_FAMILIES_X32 = ("X32", "M32", "X-Air", "XR", "HD96")


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


def ordered_x32_ms_entries(
    wing_assets: Path,
) -> list[tuple[int, str | None, str, Path | None]]:
    """Return (icon_id, wing_key, label, png_path) for MS X32-family ids 1–74."""
    from mixing_station_display_labels import display_label
    from mixing_station_icons import ICON_LABELS

    rows: list[tuple[int, str | None, str, Path | None]] = []
    for icon_id in range(1, 75):
        wing_key = x32_icon_id_to_wing_key(icon_id)
        label = display_label(icon_id) or ICON_LABELS[icon_id]
        path = wing_assets / f"{wing_key}.png" if wing_key else None
        rows.append((icon_id, wing_key, label, path))
    return rows


def ordered_wing_picker_entries(
    wing_assets: Path,
) -> list[tuple[str, str, Path]]:
    """WING picker order (130 slots) with labels."""
    from wing_icon_labels import wing_label

    rows: list[tuple[str, str, Path]] = []
    for wing_key in wing_picker_keys():
        rows.append((wing_key, wing_label(wing_key), wing_assets / f"{wing_key}.png"))
    return rows
