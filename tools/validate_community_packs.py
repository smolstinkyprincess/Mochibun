#!/usr/bin/env python3
"""Validate docs/community/catalog.json and all referenced .mochibundle / .mochibunled packs."""

import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COMMUNITY_DIR = ROOT / "docs" / "community"
CATALOG_PATH = COMMUNITY_DIR / "catalog.json"

PNG_SIG = b"\x89PNG\r\n\x1a\n"
SFNT_SIGS = (b"\x00\x01\x00\x00", b"true", b"OTTO")
OGG_SIG = b"OggS"
SOUND_CUES = frozenset({"nav", "click", "select", "back", "boundary", "snap", "feed", "pet"})
ALLOWED_STATIC_ENTRIES = frozenset({
    "manifest.json",
    "wallpaper.png",
    "cursor.gif",
    "cursor.png",
    "mascot_atlas.png",
    "card_border.png",
    "font.ttf",
    "font.otf",
})
ALLOWED_SOUND_ENTRIES = frozenset(f"sounds/{cue}.ogg" for cue in SOUND_CUES)

MAX_BUNDLE_BYTES = 8 * 1024 * 1024
MAX_MANIFEST_BYTES = 512 * 1024
MAX_WALLPAPER_BYTES = 5 * 1024 * 1024
MAX_CURSOR_BYTES = 2 * 1024 * 1024
MAX_MASCOT_BYTES = 1536 * 1024
MAX_BORDER_BYTES = 512 * 1024
MAX_FONT_BYTES = 4 * 1024 * 1024
MAX_SOUND_BYTES = 256 * 1024
HEX_RE = re.compile(r"^#?(?:0x)?(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")


def _is_unsafe_entry_name(name: str) -> bool:
    return (
        not name
        or name.startswith("/")
        or "\\" in name
        or ":" in name
        or "\x00" in name
        or ".." in name.split("/")
    )


def validate_led_file(path: Path) -> None:
    text = path.read_text(encoding="utf-8").lstrip("\ufeff \t\r\n")
    if not text:
        raise ValueError(f"{path}: empty .mochibunled file")

    if text.startswith("{"):
        data = json.loads(text)
        step_ms = int(data.get("step_ms", data.get("stepMs", 500)))
        steps = [s.get("windows", []) for s in data.get("steps", [])]
    else:
        step_ms = 500
        steps = []
        for raw in text.splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            m_win = re.match(r"^-\s*windows\s*:\s*\[([^\]]*)\]", line)
            if m_win:
                vals = [v.strip().strip("\"'") for v in m_win.group(1).split(",") if v.strip()]
                steps.append(vals)
                continue
            m_kv = re.match(r"^(step_ms|stepMs)\s*:\s*(\d+)", line)
            if m_kv:
                step_ms = int(m_kv.group(2))

    if not (100 <= step_ms <= 5000):
        raise ValueError(f"{path}: step_ms {step_ms} out of range 100..5000")
    if not (1 <= len(steps) <= 16):
        raise ValueError(f"{path}: expected 1..16 steps, found {len(steps)}")
    for idx, windows in enumerate(steps):
        if len(windows) != 5:
            raise ValueError(f"{path}: step {idx + 1} has {len(windows)} windows (expected 5)")
        for w in windows:
            if not HEX_RE.match(w):
                raise ValueError(f"{path}: step {idx + 1} invalid hex '{w}'")


def validate_bundle_file(path: Path) -> None:
    with zipfile.ZipFile(path, "r") as zf:
        infos = zf.infolist()
        if sum(info.file_size for info in infos) > MAX_BUNDLE_BYTES:
            raise ValueError(f"{path}: uncompressed size exceeds 8 MB limit")

        names = set()
        total = 0

        for info in infos:
            name = info.filename
            if _is_unsafe_entry_name(name):
                raise ValueError(f"{path}: unsafe ZIP entry '{name}'")
            if info.is_dir():
                if name != "sounds/":
                    raise ValueError(f"{path}: unexpected directory entry '{name}'")
                continue
            if name not in ALLOWED_STATIC_ENTRIES and name not in ALLOWED_SOUND_ENTRIES:
                raise ValueError(f"{path}: unsupported ZIP entry '{name}'")
            names.add(name)

            raw = zf.read(info)
            size = len(raw)
            total += size
            if total > MAX_BUNDLE_BYTES:
                raise ValueError(f"{path}: uncompressed size {total} exceeds 8 MB limit")

            if name == "manifest.json":
                if size > MAX_MANIFEST_BYTES:
                    raise ValueError(f"{path}: manifest.json exceeds 512 KB")
                manifest = json.loads(raw.decode("utf-8"))
                if not manifest.get("bundleName") or not isinstance(manifest.get("palette"), dict):
                    raise ValueError(f"{path}: manifest.json missing bundleName or palette")
            elif name == "wallpaper.png":
                if size > MAX_WALLPAPER_BYTES:
                    raise ValueError(f"{path}: wallpaper.png exceeds 5 MB")
            elif name in ("cursor.gif", "cursor.png"):
                if size > MAX_CURSOR_BYTES:
                    raise ValueError(f"{path}: {name} exceeds 2 MB")
            elif name == "mascot_atlas.png":
                if size > MAX_MASCOT_BYTES:
                    raise ValueError(f"{path}: mascot_atlas.png exceeds 1.5 MB")
                if not raw.startswith(PNG_SIG):
                    raise ValueError(f"{path}: mascot_atlas.png is not a valid PNG")
            elif name == "card_border.png":
                if size > MAX_BORDER_BYTES:
                    raise ValueError(f"{path}: card_border.png exceeds 512 KB")
                if not raw.startswith(PNG_SIG):
                    raise ValueError(f"{path}: card_border.png is not a valid PNG")
            elif name in ("font.ttf", "font.otf"):
                if size == 0 or size > MAX_FONT_BYTES:
                    raise ValueError(f"{path}: {name} size {size} out of range 1..{MAX_FONT_BYTES}")
                if not raw.startswith(SFNT_SIGS):
                    raise ValueError(f"{path}: {name} is not a valid TrueType/OpenType font")
            elif name in ALLOWED_SOUND_ENTRIES:
                if size == 0 or size > MAX_SOUND_BYTES:
                    raise ValueError(f"{path}: {name} size {size} out of range 1..{MAX_SOUND_BYTES}")
                if not raw.startswith(OGG_SIG):
                    raise ValueError(f"{path}: {name} is not a valid Ogg file")

        if "manifest.json" not in names:
            raise ValueError(f"{path}: missing manifest.json")


def main() -> int:
    if not CATALOG_PATH.is_file():
        print(f"Missing {CATALOG_PATH}", file=sys.stderr)
        return 1

    catalog = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    items = catalog if isinstance(catalog, list) else catalog.get("items", [])
    if not isinstance(items, list) or not items:
        print("catalog.json contains no items", file=sys.stderr)
        return 1

    seen_ids = set()
    for item in items:
        item_id = item.get("id")
        item_type = item.get("type")
        file_rel = item.get("file")
        if not item_id or not item.get("name") or item_type not in ("theme", "led") or not file_rel:
            raise ValueError(f"Invalid catalog entry: {item}")
        if item_id in seen_ids:
            raise ValueError(f"Duplicate catalog id: {item_id}")
        seen_ids.add(item_id)

        pack_path = (COMMUNITY_DIR / file_rel).resolve()
        if not str(pack_path).startswith(str(COMMUNITY_DIR.resolve())) or not pack_path.is_file():
            raise ValueError(f"Missing pack file for {item_id}: {file_rel}")

        preview_rel = item.get("preview")
        if preview_rel:
            preview_path = (COMMUNITY_DIR / preview_rel).resolve()
            if not str(preview_path).startswith(str(COMMUNITY_DIR.resolve())) or not preview_path.is_file():
                raise ValueError(f"Missing preview file for {item_id}: {preview_rel}")

        if item_type == "led":
            validate_led_file(pack_path)
        else:
            validate_bundle_file(pack_path)

    print(f"Validated {len(items)} community packs in {CATALOG_PATH.relative_to(ROOT)} OK")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"::error::{exc}", file=sys.stderr)
        sys.exit(1)
