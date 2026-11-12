<div align="center">

<img src="assets/mochibunheader.png" alt="Mochibun Header" width="100%" style="max-width: 680px;" />

A lightweight pastel Android launcher built from scratch for physical keypad and D-pad flip phones ♡<br/>
Lovingly tuned for the **ALT Mive Style Folder (AT-M120)**.

[Download Latest APK](https://github.com/smolstinkyprincess/Mochibun/releases/latest) • [Setup](#-downloads--setup) • [Screenshots](#-gallery--showcase-) • [Keypad Guide](#️-physical-key-map)

</div>

---

> ♡ **Quick heads up:** Mochibun works out of the box as a launcher and keyboard. The hardware-key magic (side-key mouse pointer, key remapping, system-wide key handling), and MtkSystemUI theming runs through a small **LSPosed module** that lives in the same APK, and LSPosed/Vector needs a Magisk-rooted phone. Full steps are in [Setup](#-downloads--setup).

---

## ✨ Features

- **Made for Real Buttons**: Feels like your old 2000s keitai, all clicks and no taps. Glide through cards, folders, and drawers with the physical arrows, pick things with the center key, and follow the gentle pastel glow wherever you go.
- **Snug on Small Screens**: Fitted specifically for 480 × 800 flip-phone displays, so there's no squished text or cut-off buttons.
- **Physical T9 App Search**: Just start typing numbers on the keypad to filter your apps and launch one in a blink. No tiny search bars to poke at!
- **Mochibun Keitai Keyboard**: A system-wide multi-tap keypad keyboard, just like the old flip phones. Tap a number key to cycle its letters in a cute floating pill, hold it to type the digit, and use **`*`** for shift and **`#`** for space. The pill hops out of the way when it would cover the box you're typing in, and fades when you pause. Works in every app, with a handy *Keyboard* card in settings to set it up.
- **Global Kaomoji Wheel**: Long-press **`#`** anywhere on the phone, even inside your messaging apps, to pop up the radial mood wheel and paste cute text faces straight into your chat `( ˶ˆᗜˆ˵ ) ♡`
- **Virtual Mouse Pointer**: Press the side key to toggle a little mouse cursor for those apps that were only ever made for touchscreens. Dress it up with your own `.png` or animated `.gif` sprite, scroll with `2` & `8` (and sideways with `4` & `6`), and set the speed just how you like it. *(Needs the LSPosed module.)*
- **Nostalgic Keitai Click Sounds**: Retro tactile sound effects that are generated right on your phone, so the app stays tiny. Pick a sound profile in settings (*Kawaii Pop*, *Retro Keitai* 2000s clicks, *8-Bit Chiptune*, *Cyber Pager*, *Soft Thock*, *Water Drop*, or mute).
- **Key Remapping (LSPosed Module)**: Remap your extra physical buttons to launch your favorite apps or trigger quick shortcuts. A tiny hook in the system handles the keys, so they behave the same in every app, with no accessibility service running in the background.
- **Widgets & Music Deck**: A cozy widget page with a Now Playing card that pops in while music is playing and tucks itself away when the audio stops, plus room for your own Android widgets.
- **Theme Studio & Simple YAML**: 4 built-in pastel palettes (*Sakura Dream*, *Lavender Mist*, *Minty Matcha*, and *Milky Peach*) that switch live the moment you pick one, or write your own theme files in clean, readable YAML.
- **Setup Sharing & Backups**: Export your pinned apps, folders, and settings to a quick `.json` backup, or pack your colors, custom cursor, and wallpaper into a shareable `.mochibun` theme bundle to swap with friends.
- **Icon Pack Support**: Works with standard icon packs (Nova, Apex, ADW), with gentle memory caching to keep things breezy.
- **Offline & Private**: Mochibun never goes online. No internet permission, no camera access, and no telemetry. Just you and your phone.

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
Writing JSON on a flip-phone keypad is nobody's idea of a good time. Mochibun uses clean, readable YAML for custom themes:

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

### 📦 Full Backups (`.json`)
Head to **Settings > Backup & Restore** to save your pinned favorites, folders, hidden apps, key remaps, and cursor tweaks to a simple `.json` file. Restoring reloads everything instantly, no reboot needed.

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

> **Note on Performance**: Heavy GPU runtime blurs (`Modifier.blur()`) are strictly avoided, so animations stay buttery smooth even on modest flip-phone hardware ♡

---

## 📥 Downloads & Setup

| Source | Details | Link |
| :--- | :--- | :--- |
| **GitHub Releases** | Direct APK download | [Download Latest APK](https://github.com/smolstinkyprincess/Mochibun/releases/latest) |
| **Obtainium** | One-click update tracking | [![Add to Obtainium](https://img.shields.io/badge/Obtainium-Add%20App-007ACC?style=for-the-badge&logo=android&logoColor=white)](obtainium://app/https%3A%2F%2Fgithub.com%2Fsmolstinkyprincess%2FMochibun) |

> 💡 **Using an ALT Mive Style Folder (AT-M120)?**  
> Mochibun works great out of the box, but if you want instant in-place Quick Settings toggles, zero-latency virtual cursor clicking, custom lid LED pulses, and microG, check out our **[AT-M120 Root, Magisk & Tuning Guide](docs/m120_root_setup.md)**.

> **Boot animation bonus 🎀**: Got root now? Grab the optional `mochibun-bootanimation.zip` from [Releases](https://github.com/smolstinkyprincess/Mochibun/releases/latest) for a matching pastel boot screen!

### 🧩 LSPosed Module (Hardware Keys & Cursor)

Mochibun is one APK that's both the launcher *and* an LSPosed module, so there's nothing extra to download. The module hooks into the system so your physical keys, the side-key mouse pointer, and key remaps work in every app, without any accessibility service.

1. **Root with Magisk and install LSPosed(or Vector with Zygisk Next)** (the Zygisk version). The [AT-M120 guide](docs/m120_root_setup.md) covers the Magisk side.
2. **Install Mochibun**, then open LSPosed/Vector and enable the **Mochibun** module.
3. **Tick both scopes** for the module: *System Framework* (`android`) and *System UI* (`com.android.systemui`).
4. **Reboot** so the hook loads.

No LSPosed? No problem! The launcher, drawer, T9 search, widgets, themes, backups, and the Keitai Keyboard all work without it. You'll just miss the system-wide key handling and virtual cursor.

### 🗝️ Quick Permissions Checklist

1. **Default Home**: Set Mochibun as your home app in *Settings > Apps > Default Apps > Home app*.
2. **Now Playing Card**: Enable notification access in *Settings > Apps > Special app access > Notification access* so the media widget can show your currently playing song and album art.
3. **Mouse Pointer**: Allow *Display over other apps* when asked, so the cursor can float on top of everything.
4. **Hardware Keys & Remapping**: Enable the Mochibun module in LSPosed (see [above](#-lsposed-module-hardware-keys--cursor)). There's no Accessibility setting to turn on.
5. **Keitai Keyboard**: Enable **Mochibun Keitai IME** as your keyboard (see [Mochibun Keitai Keyboard](#-mochibun-keitai-keyboard)), or let *Settings > Keyboard* walk you through it.
6. **Superuser Access (Optional extras)**: Granting Mochibun superuser access in Magisk unlocks privileged touch injection (handy when a screen blocks normal taps, like permission dialogs), root Quick Settings toggles, and hooks into custom recents providers. The LSPosed hook doesn't need it.
7. **QuickSwitch (Optional)**: If you use QuickSwitch for hardware multitasking, set Mochibun as your provider:
   ```bash
   su -c "/data/adb/modules/quickswitch/quickswitch --ch=com.crinklecodone.mochibun"
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
