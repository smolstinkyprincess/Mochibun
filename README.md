# Mochibun (`com.tnyemii.mochibun`)

A lightweight, pastel kawaii Android launcher built with Jetpack Compose and Material 3, designed specifically for physical D-pad dumbphones and flip devices[cite: 1, 2, 3]. 

Mochibun was built from the ground up for the **Chinoe AT-M120**[cite: 1, 3]. It bridges retro flip-phone ergonomics with modern Android utilities, featuring native D-pad spatial focus, a built-in virtual mouse cursor, dynamic media controls, and local AI chat—all tuned to run smoothly within a constrained hardware envelope[cite: 1, 2, 3, 4].

---

## Features

### D-Pad & Keypad-First Navigation
* **Spatial Focus Traversal**: Full navigation across cards, dialogs, and drawer grids using `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, and `DPAD_RIGHT`[cite: 2, 3].
* **Focus Isolation**: Prevents background UI elements (such as home screen favorites) from intercepting key events or shifting state while browsing dialogs, folders, or adjacent screens[cite: 2, 3, 7].
* **Unclipped Focus Rings**: Signature glowing double-line rose focus outlines configured with `clipChildren = false` to prevent clipping along container and screen edges[cite: 1, 2, 4, 7].
* **T9 Search**: Fast physical numeric keypad app filtering directly from the home screen and app drawer[cite: 1, 2, 3].

### Integrated Virtual Mouse Cursor
Replaces standalone mouse daemons (like C9) with a native, configurable system service:
* **System-Wide Accessibility Overlay**: Runs as a lightweight `AccessibilityService` overlay that floats above third-party apps, browsers, and settings[cite: 2].
* **Hardware Key Toggle**: Show or hide the pointer on demand with the side button (`KEYCODE_F4` / OEM button)[cite: 2].
* **Custom Cursor Sprites**: Support for custom user-selected `.gif` animations and `.png` sprites via document picker, rendered with nearest-neighbor scaling (`FilterQuality.None`)[cite: 1, 2].
* **Dual Click Engine**: Primary tap gestures dispatched via `AccessibilityService.dispatchGesture()`, with an optional asynchronous root touch injection fallback (`su -c input tap x y`)[cite: 2].
* **Motion Tuning**: Configurable base velocity, acceleration multipliers, and custom hotspot alignment (top-left tip vs. center)[cite: 2].

### Dynamic "Now Playing" Media Widget
* **Contextual Visibility**: Built-in media card that automatically appears during active playback (`PlaybackState.STATE_PLAYING`) and collapses cleanly when playback stops[cite: 1, 4].
* **MediaSession Integration**: Extracts live track titles, artist metadata, and album art directly via Android's `MediaSessionManager` (with a pixel-art fallback sprite)[cite: 1, 3, 4].
* **Keypad Controls**: Dedicated D-pad selectable playback controls (`[ ◄◄ ]`, `[ ▶ ]` / `[ ❚❚ ]`, `[ ►► ]`) and a heart-scrubber track progress bar[cite: 1, 3].

### System Integration & Hardware Key Remapping
* **Recents Activity Bridge**: Integrated bridge to summon the Android Recent Apps carousel without external key mappers[cite: 2, 3, 7].
* **Hardware Interception**: Consumes and redirects OEM keycodes before system dispatch to prevent focus bugs[cite: 2].
* **Custom Icon Pack Engine**: Dynamically detects and parses standard third-party icon packs (`appfilter.xml` via ADW, Nova, Go, etc.) with a bounded in-memory LRU cache[cite: 2].

### Kawaii UI & Theme Studio
* **4 Curated Pastel Presets**: Switch across **Sakura Dream**, **Lavender Mist**, **Minty Matcha**, and **Milky Peach** with real-time updates across all launcher surfaces[cite: 1, 2, 3].
* **Customizable Greeting Card**: Home card displaying an editable user name (configured in launcher settings) flanked by pixel-art accents[cite: 1, 2].
* **App Drawer & Folders**: Vertical list drawer with A–Z quick-scrub rail and 2×2 folder modal popups with rounded cards and ribbon badges[cite: 1, 4, 7].
* **Cloud & Sticker Engines**: Layered cloud borders and procedural sticker effects (`PixelSpray` and `PixelSplat`) drawn on a single `Canvas` pass with zero per-frame garbage collection[cite: 1, 2, 4, 8].

### On-Device & Remote AI Assistant
* Dedicated chat interface (`AiChatScreen.kt`) supporting local inference via SmolLM2 (`native-llama.cpp`) or remote PC-hosted Ollama endpoints[cite: 2, 3].
* CPU thread clamping (`n_threads <= 2`) to ensure local token generation does not starve the UI thread or drop input events[cite: 2].

---

## Hardware Target & Specifications

Mochibun is tuned specifically for the **Chinoe AT-M120**:

| Component | Target Specification |
| :--- | :--- |
| **Operating System** | Android 12 (API level 31)[cite: 1, 3] |
| **Display Resolution** | 480 × 800 (Portrait)[cite: 1, 3] |
| **Processor (SoC)** | MediaTek MT6761 (Quad-Core Cortex-A53)[cite: 1, 3] |
| **ABI Architecture** | `armeabi-v7a` (32-bit ARM)[cite: 1, 3] |
| **Memory (RAM)** | ~1.8 GB[cite: 1, 3] |
| **Root Solution** | Magisk (Optional, for shell fallbacks)[cite: 2, 3] |

### Resource Rules
* **Strict 480 × 800 Containment**: All menus, dialogs, and cards are contained within scrollable structures (`LazyColumn` or `Modifier.verticalScroll`) with adequate bottom padding to avoid clipping against system insets[cite: 1, 2, 3].
* **No GPU Blur Overhead**: Avoids runtime blur shaders (`Modifier.blur()`) or deep nested alpha compositions to maintain 60 FPS on the MT6761 GPU[cite: 1, 2, 3].
* **Nearest-Neighbor Scaling**: Pixel art, sticker badges, and custom cursors enforce `FilterQuality.None` to keep silhouettes crisp without bilinear blur[cite: 1, 2, 3].

---

## Setup & Permissions

After installing Mochibun, grant the following permissions to enable its hardware utilities:

1. **Default Launcher**: Set Mochibun as default in *Settings > Apps > Default Apps > Home app*.
2. **Virtual Mouse Service**: Enable **Mochibun Virtual Cursor** in *Settings > Accessibility*[cite: 2].
3. **Overlay Permission**: Grant `SYSTEM_ALERT_WINDOW` ("Display over other apps") so the mouse cursor can render across third-party applications[cite: 2].
4. **Media Access**: Enable notification / media listener access when prompted to allow the Now Playing widget to read active playback metadata[cite: 4].
5. **Root Access (Optional)**: Grant root permissions via Magisk for high-privilege touch injection and system shell fallbacks[cite: 2, 3].

---

## Recommended Boot Script (Magisk `service.d`)

To eliminate UI latency, manage memory, and prevent low-memory killer (LMK) issues on the MT6761, create `/data/adb/service.d/01_m120_tune.sh`:

```bash
#!/system/bin/sh

# Wait for system services to finish booting
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

# Disable window animations for instant D-pad navigation
settings put global window_animation_scale 0.0
settings put global transition_animation_scale 0.0
settings put global animator_duration_scale 0.0

# Virtual memory optimization
if [ -w /proc/sys/vm/swappiness ]; then
    echo 60 > /proc/sys/vm/swappiness
fi
if [ -w /proc/sys/vm/dirty_background_ratio ]; then
    echo 5 > /proc/sys/vm/dirty_background_ratio
fi
if [ -w /proc/sys/vm/dirty_ratio ]; then
    echo 15 > /proc/sys/vm/dirty_ratio
fi

# Expand ZRAM swap to ~1.75GB
if [ -e /dev/block/zram0 ]; then
    swapoff /dev/block/zram0 2>/dev/null
    echo 1 > /sys/block/zram0/reset
    echo 1792M > /sys/block/zram0/disksize
    mkswap /dev/block/zram0 >/dev/null 2>&1
    swapon /dev/block/zram0
fi

# Protect Mochibun from LMK termination
PID=$(pidof com.tnyemii.mochibun)
if [ -n "$PID" ]; then
    echo -1000 > /proc/$PID/oom_score_adj
fi
