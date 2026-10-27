# 🛠️ Mochibun Companion Tools

Standalone desktop Python utilities used to generate, compose, and package custom visual assets for the **Mochibun** launcher on the **ALT Mive Style Folder (AT-M120)** (480 × 800 portrait)[cite: 3].

These tools operate independently from the Android Gradle build pipeline, ensuring F-Droid and local `./gradlew assembleRelease` tasks remain clean, fast, and unbloated[cite: 3].

---

## 📁 Directory Structure

```text
tools/
├── README.md                  # You are here ♡
├── docomo_overlay/            # 1:1 Pixel-art wallpaper overlay grid generator
│   ├── make_overlays.py
│   ├── assets/                # Source white silhouette sprite PNGs
│   └── output/                # Generated 480x800 transparent overlays
└── mochibun_bootanim/         # Custom sleepy boot animation builder
    ├── build_bootanimation.py
    ├── README.md              # Detailed animation & Magisk installation guide
    ├── preview.gif            # Local desktop GIF preview
    ├── assets/                # Source mascot GIFs & logo PNGs
    └── output/                # Android-compliant uncompressed bootanimation.zip
```

---

## 🧰 Available Utilities

### 1. 🍡 Wallpaper Overlay Generator (`docomo_overlay/`)
* **Script**: `docomo_overlay/make_overlays.py`[cite: 3]
* **Purpose**: Generates staggered, retro keitai-style wallpaper overlay grids from raw 12×12 silhouette sprites[cite: 3]. It applies Mochibun's signature pastel palette tints and sharp nearest-neighbor integer scaling to output transparent 480 × 800 PNGs ready for `drawable-nodpi/`[cite: 3].
* **Quick Run**:
  ```bash
  cd tools/docomo_overlay
  python3 make_overlays.py
  ```

---

### 2. 🐰 Sleepy Boot Animation Builder (`mochibun_bootanim/`)
* **Script**: `mochibun_bootanim/build_bootanimation.py`
* **Purpose**: Generates a custom OEM-style boot sequence featuring layered cloud-puff shelves, a floating logo with delayed alpha fade, retro chunky $z$'s, and a snoozing Mochibunny loaf.
* **Output**: Packages a zero-compression (STORE mode) `bootanimation.zip` along with `desc.txt` headers formatted specifically for Android's native boot display service[cite: 3].
* **Quick Run**:
  ```bash
  cd tools/mochibun_bootanim
  python3 build_bootanimation.py
  ```
  *(See `mochibun_bootanim/README.md` for live-testing with `setprop ctl.start bootanim` and systemless Magisk module flashing steps).*

---

## 🛠️ General Environment Requirements

All generator tools run on Python 3 and use Pillow for image processing:

```bash
pip install pillow
```

---

## 🧹 Repository Hygiene (`.gitignore`)

Generated frame scratchpads, unpacked bitmaps, and compiled binary zip archives should remain untracked by Git[cite: 3]. Ensure your root `.gitignore` includes[cite: 3]:

```gitignore
# Tools output & scratchpads
tools/docomo_overlay/output_overlays/
tools/mochibun_bootanim/output/
tools/mochibun_bootanim/preview.gif
```
