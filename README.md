# Mochibun (もちぶん) ♡

A lightweight Android launcher designed from scratch for physical T9 keypad flip phones. Built with Jetpack Compose and Material 3, tailored specifically for the **ALT Mive Style Folder AT-M120** (Android 12, MediaTek MT6761, 480 × 800 display).

Mochibun pairs a dreamy pastel aesthetic with first-class D-pad navigation, physical T9 app filtering, and a built-in virtual mouse cursor for apps that demand touch input.

---

## ✨ Features

- **D-Pad & Keypad First**: Every menu, card, and setting is reachable using `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, and `DPAD_RIGHT`. The custom pastel selection halo makes it clear where focus is without clipping card borders.
- **Compact Flip-Phone Layout**: Engineered strictly within a 480 × 800 portrait envelope with scroll-contained sheets, ensuring no buttons or labels are cut off.
- **Virtual Mouse Cursor Service**: An accessibility overlay cursor toggled via the side button (`F4`). Supports custom animated `.gif` or `.png` pointers, speed acceleration, and click dispatching.
- **T9 App Drawer & Filtering**: Launch apps quickly by pressing physical numeric keys (2–9) directly from the keypad without tapping a search bar.
- **Cohesive Pastel Theming**: Includes 4 built-in pastel themes (*Sakura Dream*, *Lavender Mist*, *Minty Matcha*, and *Milky Peach*) with instant preview in Theme Studio.
- **Dynamic Now Playing**: A lightweight media deck that appears only when music is playing and tucks away when audio stops.
- **Pixel-Art Accents & Micro-FX**: Handcrafted pixel-art ribbon bows, bunny badges, sparkle accents, and zero-allocation canvas particle layers (`PixelSplat`).
- **Custom Icon Pack Support**: Dynamic ADW/Nova/Apex compatible icon pack detection with bounded memory caching to protect low-RAM hardware.

---

## 📱 Hardware & Compatibility

Mochibun was engineered to respect the tight hardware budget of modern 4G feature phones:

| Specification | Target Details |
| :--- | :--- |
| **Primary Device** | ALT Mive Style Folder AT-M120 |
| **OS / API** | Android 12 (API 31) |
| **SoC / Architecture** | MediaTek MT6761 (quad-core ARMv7 / 32-bit) |
| **Display** | 480 × 800 portrait |
| **Memory** | ~1.8 GB RAM |

> **Performance Note**: Mochibun avoids GPU-heavy runtime blurs (`Modifier.blur()`) and unbounded bitmap buffers. Cloud trims and decorative elements are drawn via lightweight vector canvas paths to guarantee smooth 60 FPS scrolling on 4-core hardware.

---

## 🚀 Installation & Permissions

### 1. Install the APK
Download the latest `app-release.apk` from the [Releases](https://github.com/smolstinkyprincess/Mochibun/releases) tab and install it via ADB or a file manager:

```bash
adb install -r app-release.apk

```

### 2. Configure Android Permissions

To enable all hardware and overlay features, grant the following on your device:

1. **Default Home App**: Go to *Settings > Apps > Default Apps > Home app* and choose **Mochibun**.
2. **Virtual Cursor Overlay**: Enable **Mochibun Virtual Cursor** in *Settings > Accessibility*. When prompted, allow the *Display over other apps* (`SYSTEM_ALERT_WINDOW`) permission.


3. **Root Access (Optional)**: If your device is rooted with Magisk, granting superuser access enables asynchronous shell click fallbacks (`su -c input tap`) and custom recents bridging.



---

## ⌨️ Physical Key Map

| Key / Hardware Button | Action |
| --- | --- |
| **D-Pad Directional Keys** | Spatial 2D navigation across cards, apps, and steppers

 |
| **D-Pad Center / Enter** | Launch selected app or activate focused control

 |
| **Side Key (`F4`)** | Toggle Virtual Mouse Cursor overlay on/off

 |
| **Back / `F4` (In Menus)** | Dismiss active folder, App Drawer, or Settings modal to return Home

 |
| **Number Keys (`2`–`9`)** | Filter installed applications via T9 keypad search

 |

---

## 🛠️ Building from Source

Mochibun is built using standard Gradle tooling with Kotlin and Jetpack Compose.

```bash
# Clone the repository
git clone [https://github.com/smolstinkyprincess/Mochibun.git](https://github.com/smolstinkyprincess/Mochibun.git)
cd Mochibun

# Build a release APK
./gradlew assembleRelease

```

The compiled binary will be located at `app/build/outputs/apk/release/app-release.apk`.

### On-Device Speed Compilation (Optional)

To lock in instant startup times on low-memory devices, run ahead-of-time (AOT) machine code compilation over ADB:

```bash
adb shell su -c "setprop dalvik.vm.dex2oat-threads 1"
adb shell su -c "cmd package compile -m speed -f com.tnyemii.mochibun"

```

---

## 📄 License

Distributed under the [MIT License](https://www.google.com/search?q=LICENSE). Feel free to fork, customize, and adapt for your own physical-key Android devices!

```

```
