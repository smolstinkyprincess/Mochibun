# <img src="mochibun.png" width="64" height="64" valign="middle" alt="Mochibun Icon" /> Mochibun (もちぶん) ♡

A lightweight, pastel Android launcher built with Jetpack Compose and Material 3, tailored specifically for the **ALT Mive Style Folder (AT-M120)** (Android 12, MediaTek MT6761, 480 × 800 display).

Mochibun pairs a **dreamy pastel aesthetic** with comfy D-pad navigation, physical T9 app filtering, and a built-in virtual mouse cursor for apps that demand touch input.

---

## ✨ Features

- **Keypad First**: Built from the ground up for physical buttons. Use your directional pad to move through every card, drawer, and menu. Selection rings have a soft pastel glow that won't get cut off by card edges.
- **Made for Small Screens**: Designed specifically for 480 × 800 flip phone displays—no awkward overlapping cards or text running off the screen.
- **Virtual Mouse Cursor**: A built-in mouse pointer you can turn on and off anytime using your side button. Great for apps that demand touchscreen taps. Supports animated `.gif` or `.png` cursors and adjustable speeds.
- **T9 App Search**: Start typing on your number pad to filter and launch apps right away—no tapping a tiny search bar.
- **Theme Studio**: Pick from 4 built-in pastel themes (*Sakura Dream*, *Lavender Mist*, *Minty Matcha*, and *Milky Peach*) with instant live previews.
- **Widgets Page**: A dedicated widget screen featuring a dynamic Now Playing media card that automatically appears during active playback and tucks away when audio stops, alongside support for your own Android widgets.
- **Icon Pack Support**: Compatible with standard icon packs (Nova, Apex, ADW) with smart caching so it stays light on memory.

---

## 📱 Hardware & Compatibility

Mochibun is tuned to run smoothly on modest flip phone hardware:

| Specification | Target Details |
| :--- | :--- |
| **Primary Device** | ALT Mive Style Folder AT-M120 |
| **Android Version** | Android 12 (API 31) |
| **Chipset** | MediaTek MT6761 (ARMv7 / 32-bit) |
| **Display** | 480 × 800 portrait |
| **RAM** | ~1.8 GB |

> **Performance Note**: To keep animations smooth and scrolling responsive, Mochibun avoids expensive real-time blur shaders (`Modifier.blur()`) and heavy image buffers. Decorative cloud trims and accents are drawn using lightweight vector paths.

---

## 🚀 Installation & Permissions

### 1. Grab the APK
Download the latest APK from the [Releases](https://github.com/smolstinkyprincess/Mochibun/releases/latest) tab and install it using ADB or your phone's file manager:

```bash
adb install -r mochibun.apk
