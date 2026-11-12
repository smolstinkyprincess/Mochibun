#!/usr/bin/env python3
"""
Exports Mochibun's source code and key configuration/documentation files into
4 compact Markdown files on ~/Desktop for AI review without truncation.
"""

from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SRC_ROOT = REPO_ROOT / "app/src/main/java/com/crinklecodone/mochibun"
OUT_DIR = Path.home() / "Desktop"


def write_bundle(out_name: str, files: list[Path], default_lang: str = "kotlin") -> None:
    out_path = OUT_DIR / out_name
    seen: set[Path] = set()
    unique_files: list[Path] = []
    for f in files:
        resolved = f.resolve()
        if resolved.is_file() and resolved not in seen:
            seen.add(resolved)
            unique_files.append(f)

    chunks: list[str] = []
    for f in unique_files:
        rel = f.relative_to(REPO_ROOT)
        lang = {
            ".kt": "kotlin",
            ".kts": "kotlin",
            ".xml": "xml",
            ".md": "markdown",
            ".pro": "proguard",
        }.get(f.suffix.lower(), default_lang)

        content = f.read_text(encoding="utf-8", errors="replace").rstrip()
        chunks.append(f"\n### File: `{rel}`\n```{lang}\n{content}\n```\n")

    out_path.write_text("".join(chunks), encoding="utf-8")
    line_count = out_path.read_text(encoding="utf-8").count("\n")
    print(f"Wrote {out_path} ({len(unique_files)} files, {line_count} lines)")


def collect_kt(*subdirs: str, max_depth: int | None = None) -> list[Path]:
    found: list[Path] = []
    for sub in subdirs:
        base = SRC_ROOT / sub if sub else SRC_ROOT
        if not base.exists():
            continue
        if max_depth == 1:
            found.extend(sorted(base.glob("*.kt")))
        else:
            found.extend(sorted(base.rglob("*.kt")))
    return found


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # 1. Core + Data + Root App class
    write_bundle(
        "mochibun_1_core_data.md",
        collect_kt("core", "data") + collect_kt("", max_depth=1),
    )

    # 2. UI Screens (MainLauncher, AppDrawer, Settings, ThemeStudio, etc.)
    write_bundle(
        "mochibun_2_ui_screens.md",
        collect_kt("ui/screens"),
    )

    # 3. Remaining UI + Xposed Hooks + IME + Services
    write_bundle(
        "mochibun_3_ui_hooks_services.md",
        collect_kt("ui/components", "ui/theme", "ui/overlay", "ui/dialogs", "xposed", "ime", "service")
        + collect_kt("ui", max_depth=1),
    )

    # 4. README + Manifest + Gradle + ProGuard rules
    config_candidates = [
        REPO_ROOT / "README.md",
        REPO_ROOT / "app/src/main/AndroidManifest.xml",
        REPO_ROOT / "app/build.gradle.kts",
        REPO_ROOT / "app/proguard-rules.pro",
    ]
    write_bundle("mochibun_4_readme_configs.md", config_candidates, default_lang="")


if __name__ == "__main__":
    main()
