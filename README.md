# <img src="mochibun.png" width="64" height="64" valign="middle" alt="Mochibun Icon" /> Mochibun (もちぶん) ♡

A lightweight, pastel Android launcher built with Jetpack Compose and Material 3, tailored specifically for the **ALT Mive Style Folder (AT-M120)** (Android 12, MediaTek MT6761, 480 × 800 display).

Mochibun pairs a **dreamy pastel aesthetic** with comfy D-pad navigation, physical T9 app filtering, and a built-in virtual mouse cursor for apps that demand touch input.

---

## ✨ Features

- **Keypad First**: Built from the ground up for physical buttons. Use your directional pad to move through every card, drawer, and menu. Selection rings have a soft pastel glow that won't get cut off by card edges.
- **Made for Small Screens**: Designed specifically for 480 × 800 flip phone displays—no awkward overlapping cards or text running off the screen.
- **Virtual Mouse Cursor**: A built-in mouse pointer you can turn on and off anytime using your side button. Great for apps that demand touchscreen taps. Supports animated `.gif` or `.png` cursors and adjustable speeds.
- **T9 App Search**: Start typing on your number pad to filter and launch apps right away—no tapping a tiny search bar.
- **Widgets Page**: A dedicated widget screen featuring a dynamic Now Playing media card that automatically appears during active playback and tucks away when audio stops, alongside support for your own Android widgets.
- **Theme Studio**: Pick from 4 built-in pastel themes (*Sakura Dream*, *Lavender Mist*, *Minty Matcha*, and *Milky Peach*) with instant live previews.
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
```

### 2. Set Up Permissions

1. **Default Home App**: Go to *Settings > Apps > Default Apps > Home app* and select **Mochibun**.
2. **App Permissions**: Go to *Settings > Apps > Mochibun > Permissions* and enable:
   - **Location**: Needed for weather updates on your widget deck.
   - **Files & Media**: Needed to pick custom cursor images (`.gif`/`.png`) and load wallpapers.
3. **Media / Notification Access**: Enable notification access for Mochibun in *Settings > Apps > Special app access > Notification access* so the Now Playing widget can display live track info and album art.
4. **Virtual Mouse Cursor**: Turn on **Mochibun Virtual Cursor** in *Settings > Accessibility*. When Android asks, grant the *Display over other apps* (`SYSTEM_ALERT_WINDOW`) permission.
5. **Root Access (Optional)**: If your phone is rooted with Magisk, granting superuser access lets the cursor use faster shell tap fallbacks (`su -c input tap`) and integrates with custom recents providers.
6. **QuickSwitch Integration (Optional)**: If you use QuickSwitch for physical recents/multitasking, set Mochibun as your provider:
   ```bash
   su -c "/data/adb/modules/quickswitch/quickswitch --ch=com.crinklecodone.mochibun"
   ```

---

## ⌨️ Physical Key Map

| Button | What It Does |
| :--- | :--- |
| **D-Pad (Arrows)** | Move around cards, menus, and apps |
| **Center / Enter** | Open selected item or press a button |
| **Side Button** | Turn mouse pointer on / off |
| **Back Button** | Close menus, folders, or app drawer and go Home |
| **Number Pad** | Type to search for apps |

---

## 🛠️ Building from Source

Mochibun builds with standard Gradle tooling:

```bash
# Clone the repository
git clone [https://github.com/smolstinkyprincess/Mochibun.git](https://github.com/smolstinkyprincess/Mochibun.git)
cd Mochibun

# Build release APK
./gradlew assembleRelease
```

The compiled APK will be located at `app/build/outputs/apk/release/app-release.apk`.

---

## 🤖 Disclaimer
Just a girl, her flip phone, and a sprinkle of AI magic to make the code cute! 🌸✨📱

## 📄 License

Distributed under the [Apache 2.0 License](LICENSE). Feel free to fork, customize, and adapt it for your own physical-key Android devices!
