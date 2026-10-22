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

- **Theme Studio & YAML Formatting**: Pick from 4 built-in pastel themes (*Sakura Dream*, *Lavender Mist*, *Minty Matcha*, and *Milky Peach*) with instant live previews, or design your own clean, human-readable YAML themes powered by `kaml`.

- **Backup, Restore & Setup Sharing**: Full disaster-recovery system backups (`.json`) alongside lightweight, shareable theme packs (`.mochibun` bundles) to swap aesthetic setups with the flip-phone community.

- **Icon Pack Support**: Compatible with standard icon packs (Nova, Apex, ADW) with smart caching so it stays light on memory.

---

## 📸 Screenshots

<div align="center">

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_home.png" width="22%" alt="Home Screen" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_folder.png" width="22%" alt="App Drawer" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_appdrawer.png" width="22%" alt="Theme Studio" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_recents.png" width="22%" alt="Virtual Cursor" />

</div>

---

## 🎨 Themes, Backups & Setup Sharing

Mochibun makes it easy to preserve your layout or share aesthetic presets with the dumbphone community:

### 📦 Full System Backup & Restore (`.json`)
Located in **Settings > Backup & Restore**, this generates a timestamped `.json` snapshot using Android's Storage Access Framework:
- **What's Saved**: Pinned favorites, folders and custom icons, hidden bloatware, hardware key remaps, T9 settings, and virtual cursor physics.
- **Hot-Reloading**: Restoring or resetting applies immediately across your home screen and drawer without restarting the app or killing your session.

### 🎀 Shareable Theme Bundles (`.mochibun`)
Want to share your look on Reddit or Discord without exposing private app lists or shortcuts? 
- Exports an archive containing your color palette, greeting text, custom cursor (`.gif`/`.png`), and wallpaper.
- **Deep Link Ready**: Tapping any `.mochibun` file from a download or file manager opens a live preview card with color swatches and instant one-click apply.

### 📝 YAML Theme Editing (`kaml`)
Typing JSON on a 480 × 800 flip-phone keyboard is clumsy and prone to bracket errors. Custom community themes use clean, human-readable YAML parsed via `kaml`:

```yaml
# /sdcard/Mochibun/themes/sweet_sakura.yaml
id: "sweet_sakura"
name: "Sweet Sakura ♡"
author: "emii"

palette:
  primary: "#FFB7C5"
  surface: "#FFF0F5"
  cardBorder: "#F3D1DC"
  accentDeep: "#8B3A62"
  selectionGlow: "#FF8DA1"

greeting:
  template: "♡ Welcome home, %s ♡"
  defaultName: "Princess"
  sparkles: true
```

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

### 📥 Install & Updates

| Platform | Status | Link |
| :--- | :--- | :--- |
| **GitHub Releases** | Official / Direct APK | [Download Latest](https://github.com/smolstinkyprincess/Mochibun/releases/latest) |
| **Obtainium** | One-Click Auto-Updates | [![Add to Obtainium](https://img.shields.io/badge/Obtainium-Add%20App-007ACC?style=for-the-badge&logo=android&logoColor=white)](obtainium://app/https%3A%2F%2Fgithub.com%2Fsmolstinkyprincess%2FMochibun) |

### 🗝️ Set Up Permissions

1. **Default Home App**: Go to *Settings > Apps > Default Apps > Home app* and select **Mochibun**.
2. **Media / Notification Access**: Enable notification access for Mochibun in *Settings > Apps > Special app access > Notification access* so the Now Playing widget can display live track info and album art.
3. **Virtual Mouse Cursor**: Turn on **Mochibun Virtual Cursor** in *Settings > Accessibility*. When Android asks, grant the *Display over other apps* (`SYSTEM_ALERT_WINDOW`) permission.
4. **Root Access (Optional)**: If your phone is rooted with Magisk, granting superuser access lets the cursor use faster shell tap fallbacks (`libsu`) and integrates with custom recents providers.
5. **QuickSwitch Integration (Optional)**: If you use QuickSwitch for physical recents/multitasking, set Mochibun as your provider:
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

## 🌸 Sweet Credits & Special Thanks ♡

Mochibun stays tiny, breezy, and cute thanks to these wonderful open-source libraries:

- **[Coil](https://github.com/coil-kt/coil)** — Keeps our cute animated `.gif` and `.png` cursors dancing smoothly with an ultra-lightweight memory footprint so our flip phone RAM never gets sad! (˶ᵔ ᵕ ᵔ˶)
- **[libsu](https://github.com/topjohnwu/libsu)** by [@topjohnwu](https://github.com/topjohnwu) — A rock-solid, persistent root bridge daemon that powers super-fast privileged clicks and smooth recents task management without dropping a single frame! (•̀ᴗ•́)و
- **[kaml](https://github.com/charleskorn/kaml)** — Brings clean, whitespace-friendly YAML parsing to `kotlinx.serialization` so creating and editing custom themes on a flip phone is sweet and painless! ♡

---

## 🤖 Disclaimer
Just a girl, her flip phone, and a sprinkle of AI magic to make the code cute! 🌸✨📱

## 📄 License

Distributed under the [Apache 2.0 License](LICENSE). Feel free to fork, customize, and adapt it for your own physical-key Android devices!
