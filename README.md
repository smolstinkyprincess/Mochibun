<div align="center">

<img src="assets/mochibunheader.png" alt="Mochibun Header" width="100%" style="max-width: 680px;" />

A lightweight pastel Android launcher built from scratch for physical keypad and D-pad flip phones ♡<br/>
Lovingly tuned for the **ALT Mive Style Folder (AT-M120)**.

[Download Latest APK](https://github.com/smolstinkyprincess/Mochibun/releases/latest) • [Setup](#-downloads--setup) • [Screenshots](#-gallery--showcase-) • [Keypad Guide](#️-physical-key-map)

</div>

---

> ♡ **Quick heads up:** Mochibun works out of the box as a launcher and keyboard. The hardware-key magic (side-key mouse pointer, key remapping, system-wide key handling, Media Volume Lock) and SystemUI theming run through a small **LSPosed module** that lives in the same APK, and LSPosed/Vector needs a Magisk-rooted phone. Full steps are in [Setup](#-downloads--setup).

---

## ✨ Features

## ✨ Features

### Everyday
- **Made for real buttons**: Everything works with the d-pad and center key, and a soft pastel glow shows what's selected so you never lose your place.
- **Keitai keyboard**: A system-wide multi-tap keypad keyboard in a little floating pill. Tap a number to cycle its letters, hold for the digit, `*` for shift, `#` for space. It hops out of the way of what you're typing in.
- **Kaomoji wheel**: Long-press `#` in any app to pop up a wheel of text faces and paste one into your chat `( ˶ˆᗜˆ˵ ) ♡`
- **Widgets & music deck**: A widget page with a Now Playing card that appears when music plays and hides when it stops. Third-party widgets work too.
- **Clamshell tricks**: Choose what opens when you flip the phone open (Home, Resume Last App, or Notifications), plus an optional snap sound when you close it.
- **Offline & private**: No internet permission, no camera permission, no telemetry.

### Make it yours
- **5 pastel themes**: *Sakura Dream*, *Lavender Mist*, *Minty Matcha*, *Milky Peach*, and *Midnight Mochi* for night owls 🌙. Switch live, or write your own in simple YAML.
- **Keitai click sounds**: Generated on-device, so the app stays tiny. Pick *Kawaii Pop*, *Retro Keitai*, *8-Bit Chiptune*, *Cyber Pager*, *Soft Thock*, or *Water Drop*.
- **Share your setup**: Back up pinned apps, folders, and settings to `.json`, or pack colors, cursor, and wallpaper into a `.mochibun` bundle to swap with friends.
- **Icon pack support**: Works with Nova, Apex, and ADW icon packs.

### Root / LSPosed extras
- **Virtual mouse pointer**: Press the side key for a little cursor in touch-only apps. Use your own `.png` or `.gif` sprite and scroll with `2`/`8` and `4`/`6`.
- **Key remapping**: Remap extra physical buttons to launch apps or shortcuts, in every app, with no accessibility service running.
- **Keypad recents switcher**: Hop between open apps with the arrow keys.
- **Media volume lock**: The volume rocker always changes *media* volume, even in Silent Mode.
- **Pastel SystemUI**: Your palette also colors Quick Settings, the volume dialog, notification cards, and the status and nav bars.
- **Lid LED pulses**: Notification shimmers on the outer lid lights.

---

## 📸 Gallery & Showcase ♡

<div align="center">

| 🌸 Home Screen | 🍡 App Drawer | 🗂️ Recents | 🎀 Folders |
| :---: | :---: | :---: | :---: |
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_home.png" width="165" style="border-radius: 16px; border: 1.5px solid #ecd0d8;" alt="Home Screen" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_appdrawer.png" width="165" style="border-radius: 16px; border: 1.5px solid #ecd0d8;" alt="App Drawer" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_recents.png" width="165" style="border-radius: 16px; border: 1.5px solid #ecd0d8;" alt="Recents" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_folder.png" width="165" style="border-radius: 16px; border: 1.5px solid #ecd0d8;" alt="Folders" /> |

| 📊 System Stats | 💌 Notifications | 🌸 Kaomoji Wheel | ⚙️ Settings Hub |
| :---: | :---: | :---: | :---: |
| <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_stats.png" width="165" style="border-radius: 16px; border: 1.5px solid #ecd0d8;" alt="System Stats" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6_notifs.png" width="165" style="border-radius: 16px; border: 1.5px solid #ecd0d8;" alt="Notifications" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7_wheel.png" width="165" style="border-radius: 16px; border: 1.5px solid #ecd0d8;" alt="Kaomoji Wheel" /> | <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8_settings.png" width="165" style="border-radius: 16px; border: 1.5px solid #ecd0d8;" alt="Settings Hub" /> |

</div>

---

## ⌨️ Physical Key Map

| Button | Click | Hold (~400 ms) |
| :--- | :--- | :--- |
| **Physical Arrows** | Move around cards, drawers & wheel wedges | Fast-scroll |
| **Center Key** | Open app, select item, or click | Quick menu (pin, edit icon, app info) |
| **`#` (Pound)** | Open settings & theme switcher | **Kaomoji Wheel 🌸** |
| **`*` (Star)** | Context actions | Toggle silent / vibrate |
| **`0`** | Jump to top / input | **Flashlight 🔦** |
| **`2`–`9`** | T9 search (in drawer) | **Speed dial** |
| *(while typing)* | See the [Keitai Keyboard](#-mochibun-keitai-keyboard) keys | — |
| **Side Key (F4)** | Mouse pointer on / off | Dismiss popup / dialog |
| **Call Key** | Phone dialer | — |
| **Back** | Close dialogs, folders, or drawer & return Home | — |
| **Home** | Return Home from anywhere; on Home, open the app drawer | — |
| **Recents (LSK)** | On Home, go to the Stats page | On Home, open Recents |

> 🗝️ The system-wide parts of this map (grabbing the side key, remapping, keys working in every app) come from the [LSPosed module](#-lsposed-module-hardware-keys--cursor).

---

## 🔤 Mochibun Keitai Keyboard

Mochibun ships its own keypad keyboard, so typing feels like a real 2000s keitai, in any app on the phone:

| Key | Tap | Hold |
| :--- | :--- | :--- |
| **`2`–`9`** | Cycle through the key's letters | Type the digit |
| **`*`** | Shift: `abc` → `Abc` → `ABC` | — |
| **`#`** | Space | **Kaomoji Wheel 🌸** |
| **Left / Right** | Pick a different letter in the candidate strip | — |
| **Center** | Commit the current letter (or send / go when nothing is pending) | — |
| **Up / Down** | Commit the letter and move on | — |
| **Back / Delete** | Cancel the pending letter, or delete | — |

- The keyboard is a little floating pill, so it never squishes the app behind it. When you're not pressing keys, it shrinks down to a tiny `abc` / `Abc` / `ABC` badge.
- **It dodges your text box.** If the box you're typing in is down at the bottom of the screen (hi, Discord), the pill hops to the top so you can see your words, then hops back down when the coast is clear. Some apps don't tell Android where the cursor is, and in those the pill just stays at the bottom.
- **It fades when you pause.** After a moment with no key presses the pill turns soft and see-through, then comes back to full strength on your next press.
- A pending letter commits on its own after a short pause (1.5 s by default, adjustable from 0.5 to 3 s), so you can just keep typing.
- Sentences auto-capitalize.
- Number, phone, and date fields get plain digits, so dialing works the way you'd expect.
- Works in terminals like **Termux** too. Delete and Enter go straight to the terminal as plain keys.
- Tune the **hold delay** (250–800 ms), **letter auto-commit** timer, and **hold vibration** under **Settings > Keyboard**. Both are saved in your backups.

**Setup**: Open **Settings > Keyboard** and follow the steps there, or go to *Settings > System > Languages & input > On-screen keyboard* and enable **Mochibun Keitai IME**. Rooted? **Settings > Permissions & Root** can switch it on in one step.

---

## 🌸 Kaomoji Wheel (`#` Long-Press)

Never wrestle with tiny touch keyboards just to send a cute face:

1. **Pop it open**: Hold **`#`** for a split second, anywhere on the phone, even mid-chat in another app.
2. **Pick a mood**: Press the **left / right arrows** (or keys **`1`–`6`**) to spin through *Happy*, *Love*, *Cute*, *Sleepy*, *Surprised*, or *Silly*.
3. **Pick a face**: Press the **up / down arrows** to browse the kaomojis in the middle.
4. **Send it**: Click the **center key** and your face pops right into the text box you were typing in.
5. **Close**: Tap **Back**, your side key, or **`#`** again to tuck it away.

---

## 🎨 Themes & Custom Styling

### 🎀 Shareable Theme Packs (`.mochibun`)
Want to show off your setup on r/flipphones or Discord? Export a `.mochibun` bundle to pack up your colors, greeting text, wallpaper, and custom mouse cursor (`.gif`/`.png`). Opening a `.mochibun` file on your phone shows a live color swatch card, and it applies in one click!

### 📝 Hand-Crafted YAML Themes (`kaml`)
Writing JSON on a flip-phone keypad is nobody's idea of a good time. Mochibun uses clean, readable YAML for custom themes—drop your `.yaml` files into `/sdcard/Android/data/one.crinklecod.mochibun/files/themes/`:

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

### 📦 Full Backups (`.json`) 
#### Head to **Settings > Backup & Restore** to save your pinned favorites, folders, hidden apps, key remaps, and cursor tweaks to a simple `.json` file. Restoring shows a preview summary first and reloads everything instantly, no reboot needed.
---

## 📱 Hardware & Specs

My daily driver, and the phone every pixel of Mochibun is fitted to:

| Specification | Target Details |
| :--- | :--- |
| **Target Device** | ALT Mive Style Folder (AT-M120) |
| **Android Version** | Android 12 (API 31) |
| **Processor** | MediaTek MT6761 (32-bit ARMv7) |
| **Display** | 480 × 800 |
| **RAM** | ~1.8 GB |
| **Network** | 100% offline: no internet or camera permissions |

> **Note on Performance**: Heavy GPU runtime blurs (`Modifier.blur()`) are strictly avoided, so animations stay buttery smooth even on modest flip-phone hardware ♡

---

## 📥 Downloads & Setup

| Source | Details | Link |
| :--- | :--- | :--- |
| **GitHub Releases** | Direct APK download | [Download Latest APK](https://github.com/smolstinkyprincess/Mochibun/releases/latest) |
| **Obtainium** | One-click update tracking | [![Add to Obtainium](https://img.shields.io/badge/Obtainium-Add%20App-007ACC?style=for-the-badge&logo=android&logoColor=white)](obtainium://app/https%3A%2F%2Fgithub.com%2Fsmolstinkyprincess%2FMochibun) |

> 💡 **Using an ALT Mive Style Folder (AT-M120)?**
> Mochibun works great right out of the box! (˶ᵔ ᵕ ᵔ˶) But if you want key remapping, a virtual cursor, custom lid LED colors, a recents provider, and SystemUI theming, our **[AT-M120 Root, Magisk & Tuning Guide](docs/m120_root_setup.md)** walks you through getting root ✨

> **Boot animation bonus 🎀**: Got root now? Grab the optional `mochibun-bootanimation.zip` from [Releases](https://github.com/smolstinkyprincess/Mochibun/releases/latest) for a matching pastel boot screen!

### 🧩 LSPosed Module (Hardware Keys & Cursor)

Mochibun is one APK that's both the launcher *and* an LSPosed module, so there's nothing extra to download. The module hooks into the system (`system_server` and SystemUI) so your physical keys, key remaps, and Media Volume Lock work in every app, and your palette can paint SystemUI, all without any accessibility service. The mouse pointer itself floats on top as a little foreground overlay service.

1. **Root with Magisk and install LSPosed (or Vector with Zygisk Next)** (the Zygisk version). The [AT-M120 guide](docs/m120_root_setup.md) covers the Magisk side.
2. **Install Mochibun**, then open LSPosed/Vector and enable the **Mochibun** module.
3. **Tick both scopes** for the module: *System Framework* (`android`) and *System UI* (`com.android.systemui`).
4. **Reboot** so the hook loads.

No LSPosed? No problem! The launcher, drawer, T9 search, widgets, themes, backups, and the Keitai Keyboard all work without it. You'll just miss the system-wide key handling, virtual cursor, Media Volume Lock, and SystemUI theming.

### 🗝️ Quick Permissions Checklist

1. **Default Home**: Set Mochibun as your home app in *Settings > Apps > Default Apps > Home app*.
2. **Now Playing Card**: Enable notification access in *Settings > Apps > Special app access > Notification access* so the media widget can show your currently playing song and album art.
3. **Mouse Pointer**: Allow *Display over other apps* when asked, so the cursor can float on top of everything.
4. **Hardware Keys & Remapping**: Enable the Mochibun module in LSPosed (see [above](#-lsposed-module-hardware-keys--cursor)). There's no Accessibility setting to turn on.
5. **Keitai Keyboard**: Enable **Mochibun Keitai IME** as your keyboard (see [Mochibun Keitai Keyboard](#-mochibun-keitai-keyboard)), or let *Settings > Keyboard* walk you through it.
6. **Superuser Access (Optional extras)**: Granting Mochibun superuser access in Magisk unlocks privileged touch injection (handy when a screen blocks normal taps, like permission dialogs), root Quick Settings toggles, exterior lid LED alerts, one-step keyboard setup, and hooks into custom recents providers. The LSPosed hook doesn't need it.
7. **QuickSwitch (Optional)**: If you use QuickSwitch for hardware multitasking, set Mochibun as your provider:
   ```bash
   su -c "/data/adb/modules/quickswitch/quickswitch --ch=one.crinklecod.mochibun"
   ```

---

## 🛠️ Building from Source

```bash
git clone https://github.com/smolstinkyprincess/Mochibun.git
cd Mochibun
./gradlew assembleRelease
```

The APK will be waiting for you in `app/build/outputs/apk/release/app-release.apk`!

---

## 🌸 Sweet Credits & Thanks ♡

Mochibun stays tiny, breezy, and cute thanks to these awesome projects:

- **[Coil](https://github.com/coil-kt/coil)**: Keeps animated `.gif` and `.png` cursors (and Mochibunny!) dancing smoothly without chewing up precious RAM! (˶ᵔ ᵕ ᵔ˶)
- **[libsu](https://github.com/topjohnwu/libsu)** by [@topjohnwu](https://github.com/topjohnwu): Handles fast background root clicks and clean recents switching. (•̀ᴗ•́)و
- **[kaml](https://github.com/charleskorn/kaml)**: Friendly, human-readable YAML parsing for custom themes. ♡
- **[MaruMinya (マルミーニャ)](https://www.freejapanesefont.com/maruminya-cute-free-font/)**: The darling rounded dot-matrix font powering Mochibun's nostalgic keitai typography! ♡

---

## 📄 License

Distributed under the [Apache 2.0 License](LICENSE). Feel free to fork, customize, and adapt it for your own physical-key Android setups!

*Mochibun was created with love, eepy late-night tinkering, and a little help from AI tools for code scaffolding and debugging.* ♡

---

<div align="center">

made with stardust, ♡ & eepy magic

</div>
