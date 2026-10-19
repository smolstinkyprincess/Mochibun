# Mochibun (もちぶん) ♡

A lightweight Android launcher designed from scratch for physical T9 keypad flip phones[cite: 7]. Built with Jetpack Compose and Material 3, tailored specifically for the **Chinoe AT-M120** (Android 12, MediaTek MT6761, 480 × 800 display)[cite: 2, 7].

Mochibun pairs a dreamy pastel aesthetic with first-class D-pad navigation, physical T9 app filtering, and a built-in virtual mouse cursor for apps that demand touch input[cite: 1, 2, 3].

---

## ✨ Features

- **D-Pad & Keypad First**: Every menu, card, and setting is reachable using `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, and `DPAD_RIGHT`[cite: 2, 3]. The custom pastel selection halo makes it clear where focus is without clipping card borders[cite: 2, 8].
- **Compact Flip-Phone Layout**: Engineered strictly within a 480 × 800 portrait envelope with scroll-contained sheets, ensuring no buttons or labels are cut off[cite: 2, 3].
- **Virtual Mouse Cursor Service**: An accessibility overlay cursor toggled via the side button (`F4`)[cite: 3]. Supports custom animated `.gif` or `.png` pointers, speed acceleration, and click dispatching[cite: 3].
- **T9 App Drawer & Filtering**: Launch apps quickly by pressing physical numeric keys (2–9) directly from the keypad without tapping a search bar[cite: 1, 2].
- **Cohesive Pastel Theming**: Includes 4 built-in pastel themes (*Sakura Dream*, *Lavender Mist*, *Minty Matcha*, and *Milky Peach*) with instant preview in Theme Studio[cite: 2].
- **Dynamic Now Playing**: A lightweight media deck that appears only when music is playing and tucks away when audio stops[cite: 3].
- **Pixel-Art Accents & Micro-FX**: Handcrafted pixel-art ribbon bows, bunny badges, sparkle accents, and zero-allocation canvas particle layers (`PixelSplat`)[cite: 2, 3, 21].
- **Custom Icon Pack Support**: Dynamic ADW/Nova/Apex compatible icon pack detection with bounded memory caching to protect low-RAM hardware[cite: 22].

---

## 📱 Hardware & Compatibility

Mochibun was engineered to respect the tight hardware budget of modern 4G feature phones:

| Specification | Target Details |
| :--- | :--- |
| **Primary Device** | Chinoe AT-M120[cite: 7] |
| **OS / API** | Android 12 (API 31)[cite: 2, 7] |
| **SoC / Architecture** | MediaTek MT6761 (quad-core ARMv7 / 32-bit)[cite: 2, 7] |
| **Display** | 480 × 800 portrait[cite: 2, 7] |
| **Memory** | ~1.8 GB RAM[cite: 2, 7] |

> **Performance Note**: Mochibun avoids GPU-heavy runtime blurs (`Modifier.blur()`) and unbounded bitmap buffers[cite: 2, 3]. Cloud trims and decorative elements are drawn via lightweight vector canvas paths to guarantee smooth 60 FPS scrolling on 4-core hardware[cite: 2, 3].

---

## 🚀 Installation & Permissions

### 1. Install the APK
Download the latest `app-release.apk` from the [Releases](https://github.com/smolstinkyprincess/Mochibun/releases) tab and install it via ADB or a file manager[cite: 1]:

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
