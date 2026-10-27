# 🐰 Mochibun Boot Animation Builder (`build_bootanimation.py`)

A standalone Python utility that generates a custom boot animation tailored for **Mochibun** on the **ALT Mive Style Folder (AT-M120)** (480 × 800 portrait).

---

## 🎬 Animation Preview

<div align="center">
  <img src="preview.gif" alt="Mochibun Boot Animation Preview" width="240" />
</div>

---

## 🛠️ Prerequisites

Requires Python 3 and Pillow:

```bash
pip install pillow
```

---

## 📁 Directory Structure

```text
tools/bootanim/
├── build_bootanimation.py
├── README.md
├── preview.gif                  # Generated local animation preview
├── assets/
│   ├── logo.png                 # Upper sky logo banner (alpha transparency)
│   ├── mochibunny_sleep_anim.gif # Mascot snoozing sprite frames
│   ├── mochibunny_music_anim.gif # Optional fallback/alternate
│   └── mochibunny_idle_anim.gif  # Optional fallback/alternate
└── output/                      # Build artifacts (ignored by git)
    ├── desc.txt
    ├── bootanimation.zip        # Stored (0% compression) archive for Android
    ├── part0/                   # Rising clouds & logo fade-in intro frames
    └── part1/                   # Infinite sleepy loop frames
```

---

## 🚀 Building the Animation

From within the `tools/mochibun_bootanim/` folder:

```bash
python3 build_bootanimation.py
```

* **`output/bootanimation.zip`**: The ready-to-flash package with uncompressed frames and `desc.txt` instruction headers.
* **`preview.gif`**: Rendered in the root of the folder so you can preview timing, layers, and positioning on your computer before pushing to the device.

---

## 📱 Device Installation (Magisk Systemless)

The AT-M120 uses a read-only system partition. Installing via a lightweight local Magisk module provides clean systemless overlay persistence without modifying `/system` directly.

### 1. Stage the Zip on Device
```bash
adb push output/bootanimation.zip /sdcard/bootanimation.zip
```

### 2. Install via Root ADB Shell
Connect to the phone and install to Magisk's module path:

```bash
adb shell
su
```

Run this block directly in your `#` root prompt:

```bash
MODPATH="/data/adb/modules/mochibun_bootanim"
mkdir -p "$MODPATH/system/media"

# Write module descriptor
cat << 'EOF' > "$MODPATH/module.prop"
id=mochibun_bootanim
name=Mochibun Boot Animation
version=v1.0
versionCode=1
author=emii
description=Custom sleepy Mochibun boot animation ♡
EOF

# Move archive into place and configure permissions
cp /sdcard/bootanimation.zip "$MODPATH/system/media/bootanimation.zip"
chmod 755 "$MODPATH"
chmod -R 755 "$MODPATH/system"
chmod 644 "$MODPATH/system/media/bootanimation.zip"
chmod 644 "$MODPATH/module.prop"
rm -f /sdcard/bootanimation.zip
```

### 3. Test & Reboot
Test the animation on the live screen without rebooting:
```bash
setprop ctl.start bootanim    # Starts animation preview
setprop ctl.stop bootanim     # Returns to home screen
```

To enable across all future boots, simply restart:
```bash
reboot
```
