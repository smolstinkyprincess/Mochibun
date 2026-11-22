# 🛠️ Mochibun Tools

Standalone build-time, asset-generation, validation, and AT-M120 device benchmarking scripts for **Mochibun** (`one.crinklecod.mochibun`). Nothing in `tools/` is compiled into the Android app or shipped in Gradle / F-Droid builds.

---

## 📁 Directory Map

```text
tools/
├── README.md                       # You are here ♡
├── benchmark_m120.sh               # APK/dex sizes + adb launch, gfxinfo, meminfo & Settings stress
├── capture_baseline_profile.sh     # Captures on-device ART profile -> app/src/main/baseline-prof.txt
├── gen_t9_dict.py                  # Builds offline T9 wordlist -> app/src/main/assets/t9/en.tsv
├── t9_extra_words.txt              # Committed everyday/texting words merged by gen_t9_dict.py
├── validate_community_packs.py     # Validates docs/community/catalog.json & .mochibundle/.mochibunled packs
├── export_src_bundle.py            # Exports source & config files to ~/Desktop/*.md for review
├── docomo_overlay/                 # 480×800 pixel-art wallpaper overlay generator (`make_overlays.py`)
├── mochibun_bootanim/              # Custom sleepy Mochibunny bootanimation.zip builder (`build_bootanimation.py`)
└── input/                          # [gitignored] Drop en_50k.txt & scowl.txt here to rebuild en.tsv
```

---

## 🧰 Script-by-Script Reference

### 1. AT-M120 Benchmark & Settings Stress (`benchmark_m120.sh`)
Measures APK + `classes*.dex` sizes under `app/build/outputs/apk/` and, when an `adb` device with Mochibun installed is connected, records cold/relaunch time (via `logcat` `Displayed`), Home idle frame stats, App Drawer fling frame stats (`dumpsys gfxinfo`), and memory footprint (`dumpsys meminfo`). Passing `--stress-settings` instead runs an unmeasured JIT warm-up followed by fail-safe Settings Hub idle, 8-destination navigation, D-pad bursts/toggles, and memory checks (verifying window focus and `uiautomator dump` screen titles before every batch so injected keys never leak to Home/Drawer).

- **Prerequisites:** `bash`, `unzip` (for `classes*.dex` sizes), `adb` (optional when `NO_DEVICE=1`). Keep the phone unlocked with the screen on and lid open.
- **Commands & Options:**
  ```bash
  tools/benchmark_m120.sh                              # APK/dex sizes + full device run
  NO_DEVICE=1 tools/benchmark_m120.sh                  # APK/dex sizes only
  RUNS=7 tools/benchmark_m120.sh                       # Override launch sample count (default: 5)
  ANDROID_SERIAL=<serial> tools/benchmark_m120.sh      # Target a specific adb device
  tools/benchmark_m120.sh --stress-settings            # Settings Hub stress only
  STRESS_ROUNDS=5 tools/benchmark_m120.sh --stress-settings  # Stress rounds (default: 3)
  tools/benchmark_m120.sh --help                       # Print inline usage header
  ```
- **Inputs:** Built APKs in `app/build/outputs/apk/*/*.apk` and `adb` telemetry (`dumpsys`, `logcat`, and `/data/local/tmp/mochibun_ui.xml` during `--stress-settings`).
- **Output:** Printed summary report to stdout.

---

### 2. Baseline Profile Capture (`capture_baseline_profile.sh`)
Dumps Mochibun's real on-device ART profile (`profman --dump-classes-and-methods`), filters to `one/crinklecod/mochibun` and `com/android/quickstep` rules, collapses compiler-numbered lambda/synthetic/ComposableSingletons classes to `$**` wildcards, merges `H`/`S`/`P` flags, and writes `app/src/main/baseline-prof.txt`.

- **Prerequisites:** `bash`, `adb`, and a **rooted** device (`su`) with a **debug** build of `one.crinklecod.mochibun` installed and exercised beforehand (debug builds keep unminified class/method names).
- **Command:**
  ```bash
  tools/capture_baseline_profile.sh
  ```
- **Inputs:** `/data/misc/profiles/cur/0/one.crinklecod.mochibun/primary.prof` and the installed APK path on device.
- **Output:** `app/src/main/baseline-prof.txt`.

---

### 3. Offline English T9 Dictionary Generator (`gen_t9_dict.py` + `t9_extra_words.txt`)
Builds Mochibun's offline English T9 dictionary by filtering FrequencyWords `en_50k.txt` against SCOWL (`scowl.txt`), capping at 30,000 base words (`MAX_WORD_LEN = 32`), synthesizing frequencies for 39 common contractions, and merging casual/texting words from `t9_extra_words.txt` and an optional local `tools/input/extra_words.txt`.

- **Prerequisites:** Python 3 (standard library only).
- **Command:**
  ```bash
  python3 tools/gen_t9_dict.py
  ```
- **Inputs:**
  - `tools/input/en_50k.txt` *(gitignored, required — `word count` per line)*
  - `tools/input/scowl.txt` *(gitignored, required — one word per line)*
  - `tools/t9_extra_words.txt` *(committed — one word per line, `#` comments allowed)*
  - `tools/input/extra_words.txt` *(gitignored, optional — local extra words)*
- **Output:** `app/src/main/assets/t9/en.tsv` (`<digits>\t<word>\t<freq>`).

---

### 4. Community Pack Validator (`validate_community_packs.py`)
Validates `docs/community/catalog.json` and every referenced `.mochibundle` (ZIP) and `.mochibunled` (JSON/YAML LED animation) file—checking unique IDs, safe ZIP paths, magic headers (PNG, SFNT/TTF/OTF, Ogg), size caps (`8 MB` uncompressed bundle, `512 KB` manifest/border, `5 MB` wallpaper, `2 MB` cursor, `1.5 MB` mascot atlas, `4 MB` font, `256 KB` per sound cue), and LED bounds (`100–5000 ms`, `1–16` steps × `5` windows). Run automatically on every push/PR by `.github/workflows/ci.yml`.

- **Prerequisites:** Python 3 (standard library only).
- **Command:**
  ```bash
  python3 tools/validate_community_packs.py
  ```
- **Inputs:** `docs/community/catalog.json` and referenced pack/preview files under `docs/community/`.
- **Output:** Validation status on stdout (exits non-zero with `::error::...` on any failure).

---

### 5. Source Bundle Exporter (`export_src_bundle.py`)
Collects Mochibun's Kotlin sources (`core/`, `data/`, root `*.kt`, `ui/screens/`, `ui/components/`, `ui/theme/`, `ui/overlay/`, `ui/dialogs/`, `xposed/`, `ime/`, `service/`, `ui/*.kt`) plus `README.md`, `AndroidManifest.xml`, `app/build.gradle.kts`, and `app/proguard-rules.pro` into four Markdown files for external or AI review.

- **Prerequisites:** Python 3 (standard library only).
- **Command:**
  ```bash
  python3 tools/export_src_bundle.py
  ```
- **Inputs:** Project files under `app/src/main/java/one/crinklecod/mochibun/` and repo config/README files.
- **Outputs:** `~/Desktop/mochibun_1_core_data.md`, `~/Desktop/mochibun_2_ui_screens.md`, `~/Desktop/mochibun_3_ui_hooks_services.md`, `~/Desktop/mochibun_4_readme_configs.md`.

---

### 6. Wallpaper Overlay Generator (`docomo_overlay/make_overlays.py`)
Nearest-neighbor scales raw silhouette PNGs (`SCALE_FACTOR = 2` in script), tints them from Mochibun's 6-color `PASTEL_PALETTE`, and tiles them into a staggered `48×48` grid on transparent 480 × 800 PNGs ready for `app/src/main/res/drawable-nodpi/`. See [`docomo_overlay/README.md`](docomo_overlay/README.md) for layout constants.

- **Prerequisites:** Python 3 and Pillow (`pip install pillow`).
- **Command** *(uses relative `./assets` and `./output`, so run inside `tools/docomo_overlay/`)*:
  ```bash
  cd tools/docomo_overlay && python3 make_overlays.py
  ```
- **Inputs:** `tools/docomo_overlay/assets/*.png` *(gitignored)*.
- **Outputs:** `tools/docomo_overlay/output/wallpaper_overlay_1.png` through `wallpaper_overlay_4.png` *(gitignored)*.

---

### 7. Sleepy Boot Animation Builder (`mochibun_bootanim/build_bootanimation.py`)
Renders a 480 × 800 @ 6 FPS sleepy Mochibunny boot sequence (`part0` rising clouds + logo fade-in, `part1` snoozing loop with drifting pixel `Z`s and twinkling stars), writes `desc.txt`, packs an uncompressed (`ZIP_STORED`) `bootanimation.zip`, and exports `preview.gif`. See [`mochibun_bootanim/README.md`](mochibun_bootanim/README.md) for live testing (`setprop ctl.start bootanim`) and Magisk systemless installation steps.

- **Prerequisites:** Python 3 and Pillow (`pip install pillow`).
- **Command** *(uses relative `assets/`, `output/`, and `preview.gif`, so run inside `tools/mochibun_bootanim/`)*:
  ```bash
  cd tools/mochibun_bootanim && python3 build_bootanimation.py
  ```
- **Inputs:** `tools/mochibun_bootanim/assets/mochibunny_sleep_anim.gif` and optional `assets/logo.png`.
- **Outputs:** `tools/mochibun_bootanim/output/` (`desc.txt`, `part0/`, `part1/`, `bootanimation.zip`, all gitignored) and `tools/mochibun_bootanim/preview.gif`.

---
