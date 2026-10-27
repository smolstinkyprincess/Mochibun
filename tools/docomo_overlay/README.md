# 🍡 Wallpaper Overlay Generator (`make_overlays.py`)

A standalone Python utility that generates retro keitai-style pixel-art wallpaper overlays from white silhouette sprites.

---

## 🛠️ Prerequisites

Requires Python 3 and Pillow:

```bash
pip install pillow
```

---

## 📁 Directory Structure

```text
tools/
├── make_overlays.py
├── assets/       # Put your white silhouette PNGs here
└── output/     # Generated 480x800 transparent PNGs
```

---

## 🚀 Usage

1. Place your transparent, whited-out silhouette sprites into `tools/assets/`.
2. Run the script:

```bash
python3 make_overlays.py
```

Generated overlays are saved directly to `tools/output/`.

---

## ⚙️ Configuration

Key constants can be tweaked directly at the top of `make_overlays.py`:

| Setting | Default | Description |
| :--- | :--- | :--- |
| `CANVAS_WIDTH` / `HEIGHT` | `480` × `800` | Target screen resolution (1:1 with AT-M120). |
| `SCALE_FACTOR` | `3` | Nearest-neighbor integer scale multiplier ($12 \times 12 \rightarrow 36 \times 36\text{ px}$). |
| `PASTEL_PALETTE` | Mochibun Tokens | Predefined hex colors used for random tinting. |
| `cell_w` / `cell_h` | `64` × `64` | Spacing per cell in the staggered grid layout. |

---

## 📦 Adding to Mochibun

Copy your favorite generated PNGs directly into the launcher's drawable directory:

```bash
cp output/wallpaper_overlay_1.png ../app/src/main/res/drawable-nodpi/wallpaper_overlay_1.png
```
