# Mochibun Theme, Mascot & Lid LED Formats

Reference guide for creating custom themes, pocket-pet sprite sheets, `.mochibundle` packs, and `.mochibunled` light animations. If you prefer a visual editor, try the browser-based **[Mochibun Community Workshop](https://crinklecod.one/Mochibun/community/workshop.html)**.

---

## 🎀 Shareable Theme Packs (`.mochibundle`)

A `.mochibundle` file (or legacy `.mochibun`) is a standard ZIP archive containing `manifest.json` and optional assets:

- `manifest.json` — bundle metadata, greeting config, palette tokens (including optional `outlineColorHex`, `textColorHex`, `fontFamilyId`, `cardRoundnessDp`, `borderStyle`, `cardBorderSlicePx`), cursor tuning, and optional `mascot` metadata / dialogue.
- `wallpaper.png` — custom Home wallpaper.
- `cursor.gif` or `cursor.png` — custom virtual pointer sprite.
- `mascot_atlas.png` — custom pocket-pet sprite sheet (see below).
- `card_border.png` — custom 9-slice card frame (`hasCustomCardBorder` + `cardBorderSlicePx`).

Opening a `.mochibundle` on your phone displays a live color/asset preview card and lets you choose whether to reset unbundled wallpaper/cursor/mascot assets on apply.

---

## 🐰 Custom Mochibunny Sprites (`mascot_atlas.png`)

Mochibunny lives on one PNG sprite sheet arranged as **8 rows × 4 columns** (default) of square frames, one row per mood or action, top to bottom:

1. `idle`
2. `talk`
3. `happy`
4. `hungry`
5. `sulky`
6. `sleepy`
7. `eat`
8. `pet`

Pick yours in *Settings > Appearance > Custom Mascot* (and **Reset Mascot** brings her back).

- Frames can be **16–256 px** square, up to **16 frames per row** (configured per row via `mascot.slots` in `manifest.json`), with the full sheet up to **2048 × 2048** and **1.5 MB**.
- Sprites are sampled with nearest-neighbor filtering (`FilterQuality.None`), so crisp pixel art stays sharp.
- Inside a `.mochibundle` pack, `manifest.json` can also include optional `mascot.dialogue` pools (`happy`, `content`, `hungry`, `sulky`, `sleepy`, `fed`, `noTreats`; up to 20 lines of 48 characters each) so your custom mascot talks in character.
- Invalid or oversized files are rejected safely with a short note while leaving your current mascot intact.

---

## 📝 Hand-Crafted YAML Themes (`kaml`)

Mochibun reads YAML theme files from `/sdcard/Android/data/one.crinklecod.mochibun/files/themes/` *(to bundle images like `mascot_atlas.png`, wallpapers, or cursors, pack a `.mochibundle` instead)*:

```yaml
# /sdcard/Android/data/one.crinklecod.mochibun/files/themes/sweet_sakura.yaml
id: "sweet_sakura"
name: "Sweet Sakura ♡"
author: "emii"
version: "1.0"

colors:
  primary: "#FFB7C5"
  container: "#FFF0F5"
  cardBorder: "#F3D1DC"
  accentDeep: "#8B3A62"
  background: "#FFF5F8"
  outline: "#F8BBD0"
  selectionGlow: "#FF8DA1"
  textColor: "#4A154B"

decorations:
  greetingText: "— ♡ Sweet Sakura ♡ —"
  glowExpansionDp: 4.0
  cardRoundnessDp: 24.0
```

---

## 💡 Lid Light Animations (`.mochibunled` / `.yaml`)

Lid LED Studio animations are YAML (or JSON) files. Each step lists the five outer lid windows (`W1`–`W5`, left to right); `#000000` means off. The physical lid LED lights each step in one color (the most-used active color in that step):

```yaml
name: "Sakura Chase ♡"
author: "emii"
step_ms: 500        # 100–5000 ms per step
loop: true          # false = play once
steps:              # up to 16 steps
  - windows: ["#FF1493", "#000000", "#000000", "#000000", "#000000"]
  - windows: ["#000000", "#FF1493", "#000000", "#000000", "#000000"]
  - windows: ["#000000", "#000000", "#FF1493", "#000000", "#000000"]
```

Exports save as `.mochibunled` (the same YAML format with a dedicated extension so tapping one on your phone imports it straight into **Lid LED Studio**). Plain `.yaml` or `.json` files with the same fields can also be loaded via **Import** in the Studio.
