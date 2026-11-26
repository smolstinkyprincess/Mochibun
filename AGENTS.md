# Mochibun Project Bible: Rules, Hardware, Architecture & History

## 1. Project Identity & Core Philosophy

**Mochibun (もちぶん)** is a lightweight, offline-first, pastel retro-kawaii Android launcher and physical-keypad utility suite engineered specifically for the **ALT Mive Style Folder (Chinoe AT-M120)** Android flip phone.

- **Package / Application ID:** `one.crinklecod.mochibun` (historically `com.tnyemii.mochibun` in v1.0.0, then `com.crinklecodone.mochibun`, finalized to `one.crinklecod.mochibun` for v2.x and F-Droid inclusion).
- **Author & License:** Created by emii (`smolstinkyprincess/crinklecodone`), licensed under **Apache 2.0**.
- **Core Philosophy:** Stock Android flip-phone setups typically require juggling 3–4 separate third-party accessibility daemons (e.g., C9 for a virtual cursor, Key Mapper for side buttons, TT9 for keypad input, plus a standalone launcher), which constantly contend for input focus and trigger Android's Low Memory Killer (LMK) on ~2 GB RAM devices. Mochibun unifies the entire physical-keypad experience—launcher, D-pad spatial engine, T9 app search, system-wide Keitai IME, virtual mouse cursor, Kaomoji wheel, outer lid LEDs, and SystemUI theming—into a single cohesive, low-RAM master process.
- **Privacy & Network Posture:** 100% offline (`allowBackup="false"`). Zero internet permissions, zero telemetry, zero camera capture access. CI verifies on every build that the merged release manifest contains neither `INTERNET` nor `CAMERA` (see `PRIVACY.md`).

---

## 2. Target Hardware & Platform Invariants (ALT Mive Style Folder AT-M120)

Every architectural and UI decision in Mochibun is governed by the physical and silicon constraints of the Chinoe AT-M120:

- **SoC & CPU:** MediaTek MT6761 (Helio A22) — quad-core ARM Cortex-A53 @ 2.0 GHz, **32-bit ARMv7 (`armeabi-v7a`)**.
- **GPU:** PowerVR Rogue GE8300.
- **System Memory (RAM):** ~1.8–2.0 GB physical RAM.
- **Display:** Fixed **480 × 800** portrait viewport (3:5 aspect ratio, ~240 DPI).
- **OS & Runtime:** Android 12 (API 31). Note that OEM/SELinux policies lock down user-space ART ahead-of-time compilation (`cmd package compile` fails with `kDex2oatExec` / exit code 72), meaning classes execute under runtime JIT—hot-path allocations and reflection overhead must be kept minimal. A bundled Baseline Profile (`app/src/main/baseline-prof.txt` + `androidx.profileinstaller`) pre-compiles critical launcher/IME/compose paths on install when permitted.
- **Secondary Community Targets:** `minSdk = 27` (Android 8.1+, `compileSdk = 37`, `targetSdk = 31`, currently `versionCode = 25` / `versionName = "2.8.0"`) allows installation on older Japanese Keitai phones (e.g., Kyocera `902KC` / `KYF42`), though they remain community-untested. Always guard API 28+ calls with `Build.VERSION.SDK_INT` (keep `./gradlew lintDebug` at 0 `NewApi` errors) and keep Android 12-only theme attributes isolated in `res/values-v31/`.
- **Privilege Tiers:**
  - **Unrooted (Base):** Must function cleanly out of the box without root or LSPosed. Optional `MochibunAccessibilityService` (`canRetrieveWindowContent="false"`, zero event types requested) provides non-root virtual cursor gesture taps/scrolls, global actions, and soft-key/cursor key routing when enabled.
  - **Root (`libsu` via Magisk):** Unlocks zero-latency native `evdev` touch/key injection (`evinject`), hardware Hall-switch lid monitoring (`/dev/input/event0`), AW22xxx outer clamshell LED control, in-place Wi-Fi/Bluetooth quick toggles, QuickSwitch recents fallback (`dumpsys` + task snapshots), and root self-healing watchdogs.
  - **LSPosed / Xposed (`:xposed-stub`):** Unlocks `system_server` hardware key interception (`MochibunKeyHook`) and `com.android.systemui` visual theming (`MochibunSystemUiHook`), allowing `MochibunAccessibilityService` to step aside automatically when active (`KeyHookStatus.isActive()`).

---

## 3. Project Evolution & Architectural History

Understanding how Mochibun evolved explains why specific patterns exist today:

1. **v1.0.0 – v1.2.0 (Flat Prototype → Modular Layered Architecture):**
  - Early builds housed all models, preferences, services, and screens flat in the root package alongside an always-on `AccessibilityService` (`MochibunAccessibilityService`) and experimental local LLM chat (`NativeLlm` / `SmolLM2`).
  - In commit `22ca9bddc`, the codebase was refactored into strict domain layers (`core/`, `data/`, `service/`, `ui/`), `ThemeStudio.kt` was decoupled from design tokens (`ui/theme/LauncherTheme.kt`), and duplicate models/preferences were purged.
  - `MainLauncher.kt` was refactored in two passes to consolidate ~12 scattered modal booleans and 13 `BackHandler`s into a single exhaustive `ActiveModal` sealed interface state machine, offloading ZIP I/O to `Dispatchers.IO`.
2. **v1.x → v2.0.0 (LSPosed Hook Migration & Native C++ Daemons):**
  - Running `AccessibilityService` key filters alongside `VirtualCursorService` caused `ACTION_DOWN` / `ACTION_UP` split-event race conditions, ghost D-pad traversal, and accessibility binder latency.
  - Mochibun moved primary hardware key interception to `MochibunKeyHook` inside `system_server` via LSPosed, converted `VirtualCursorService` to an on-demand foreground service using `TYPE_APPLICATION_OVERLAY` (stopping itself whenever the cursor is hidden), and introduced native C++ helpers (`evinject.c` for `<1 ms` evdev input injection vs `~470 ms` `input tap` JVM spawns, and `lidled.c` for flicker-free AW22xxx LED register playback).
  - Built-in system-wide `MochibunImeService` replaced third-party T9 keyboards (TT9), hosting the global `#`-hold Kaomoji Wheel directly inside the IME `InputConnection`.
3. **v2.1.0 – v2.3.x (OEM SystemUI Skinning, Non-Root Fallback, Pocket Pet & Settings Hub Refactor):**
  - `SettingsHubScreen.kt` was decomposed into dedicated destination screens (`AppearanceSettingsScreen`, `KeyboardSettingsScreen`, `CursorHardwareSettingsScreen`, `SoundFeedbackSettingsScreen`, `BehaviorSettingsScreen`, `PermissionsRootSettingsScreen`, `BackupRestoreSettingsScreen`, `AboutMochibunScreen`).
  - `MochibunAccessibilityService` was reintroduced strictly as an optional zero-event-type non-root fallback (bypassed whenever `KeyHookStatus.isActive()` reports a live `MochibunKeyHook` heartbeat), alongside non-root Screen-Off Flip Reset (`MainActivity`) and conditional Settings visibility (`KEY_SHOW_ROOT_OPTIONS`).
  - Added `MochibunSystemUiHook.kt` (modularized under `xposed/systemui/`) to skin status bar icons, 4-heart battery meter, squircle volume dialog, pastel Quick Settings shade, lockscreen pearl icon/scrim, Power Menu, dialogs, toasts, and screenshot shelf inside `com.android.systemui`.
  - Added `Mochibunny` pocket pet (`pet/`), shared sprite atlas (`MascotSprites.kt`), `KeitaiLaceContainer` two-tone organza framing, and `LidLedStudioScreen` with `.mochibunled` YAML/JSON step animations.
4. **v2.4.0 (Custom 9-Slice Card Borders, Overlays, AppWidgets, Sub-Display Ticker & Community Workshop):**
  - Added `CustomCardBorderManager` (`card_border.png` 9-slice frames + 5 procedural `CardBorderStyle`s), 4 daily Docomo-inspired 480×800 pixel `WallpaperOverlay`s, `SubDisplayTickerFeed` + `QuickMemoDialog`, real Android `AppWidget` hosting (`MochibunWidgetHost`, `WidgetPage`, `WidgetRepository`), `QuickSettingsCard`, and `MediaVolumeLockSync`.
  - Standardized shareable theme bundles on `.mochibundle` (retaining legacy `.mochibun` support), added browser-based Community Workshop/Gallery (`docs/community/workshop.html`, `tools/validate_community_packs.py`), F-Droid/IzzyOnDroid build metadata (`fdroid/config.yml`, `dependenciesInfo` disabled), and `MemoryTrimPolicy` (`MemoryTrimTier`).
5. **v2.4.1+ (Widget Polish, Baseline Profiles & Privacy/Cache Hardening):**
  - Redesigned `NowPlayingCard` (`HeartScrubberProgressBar`, `PastelMiniDiscIcon`, corner mascot pin) and `SystemMonitorCard`.
  - Added `baseline-prof.txt` + `androidx.profileinstaller` + `tools/capture_baseline_profile.sh`, `PRIVACY.md`, CI release-manifest permission checks, incremental `Channel<Update>` notification tracking, and bounded artwork/mascot caching.
6. **v2.5.0 – v2.6.0 (Settings Hub Polish, All-in-One Theme Bundles, Sticker-Tray Folders & Interactive Key Guide):**
  - `v2.5.0`: Polished Settings Hub cards/back pill, scoped recompositions & IME binder calls, and added 5-slot Quick Memo, per-app Lid LED rules, and the `#` wheel's Clips & Snippets mode.
  - `v2.6.0`: Extended `.mochibundle` packs with optional bundled fonts (`font.ttf`/`font.otf`, `<= 4 MB`) and keypad sound packs (`sounds/<cue>.ogg`, `<= 256 KB` per cue), added optional Magisk system-wide font sync (`SystemFontSyncManager`), redesigned `FavoriteFolderDialog` as a cloud-perched sticker tray (`FolderTrayNavigation`, `FolderTiltNudge`), introduced the interactive AT-M120 `MochibunKeyGuideDialog` (`Launcher`, `Typing`, and `Remap` tabs), and unified Settings Hub and action-dialog glyphs on 1-bit `DocomoPictogram` tiles (`R.drawable.docomo_atlas`).
7. **v2.7.0 (Offline Predictive T9, Custom Dictionaries, 1–9 Settings Jump Keys & Cloud Strip Polish):**
  - Added offline predictive T9 (`T9DictionaryEngine`, `tools/gen_t9_dict.py`, `en.tsv` 30k wordlist) with `*` mode cycling, custom `.tsv`/`.txt` dictionary imports, offline word-frequency & new-word learning (`UserWordStats` / `UserWordStore`), 1–9 keypad jump-key navigation inside Settings Hub, a fixed 5-puff asymmetrical cloud strip silhouette with a soft back-cloud layer in `KeitaiCandidateStrip`, and complete backup coverage across cursor scroll step, appearance, drawer sort, and lid LED preferences.
8. **v2.7.1 (8-Slice Compass Kaomoji Wheel, IME `*` Split & Quick Memo Polish):**
  - Split IME `*` tap (`abc` → `T9` → `123` → `abc`) from `*` hold (live `Abc` → `ABC` → `abc` casing), moved `*` to the front of `0`'s symbol cycle, and ensured single-digit T9 queries (`length == 1`) always surface all mapped letters before dictionary completions.
  - Upgraded the `#`-hold Kaomoji Wheel (`KaomojiRepository`, `KaomojiWheel`) to an 8-slice compass (`4` cardinals + 2-tap perpendicular diagonal tilt + `1`–`8` direct slice jump) with Stage 2 `1`–`9`/`0` (`#10`) instant quick-pick and 3 Hz `rememberSteppedTickerText` marquee for wide kaomoji (`> 13` chars).
  - Polished `QuickMemoDialog` with faint `1.dp` ruled notebook lines, `1.dp` unselected slot pill borders with `•` saved-content dots, and contextual helper hints (`↑ slots` vs `Center: Pin ★ • Back: Close`).
9. **v2.7.x – v2.8.0 (J2ME .jar Games, AbyssME Template Sync, Settings Lace & Perf Polish):**
  - Added `J2meGameRepository` to index SAF `.jar` folders for classic Java ME games (`META-INF/MANIFEST.MF` MIDlet names and crisp pixel-art icons cached in a 2 MB `LruCache`), surface them alongside Android apps in App Drawer, Favorites, and Folders, and launch them via `ACTION_VIEW` (`application/java-archive`) into AbyssME or J2ME Loader.
  - Added `J2meTemplateSyncDialog` and `TEMPLATE_SYNC_KEYS` selective `org.json` template merging (via SAF `configs` grant or `RootShell` fallback) to propagate AT-M120 keypad mappings, hidden touch overlays, and aspect-fit scaling across game `config.json` files.
  - Added scalloped `CardBorderStyle.LACE` fallback borders to Settings Hub root cards and trimmed Kaomoji Wheel frame times on the AT-M120.

---

## 4. Development Priorities & Coding Rules

1. **Keep the launcher lightweight and responsive.** Measure before optimizing; never optimize on purely theoretical concerns.
2. **Preserve existing behavior unless explicitly asked to change it.**
3. **Prefer small, targeted, reviewable changes over large rewrites.**
4. **Avoid unnecessary dependencies.** Runtime dependencies are intentionally restricted to Jetpack Compose / Material 3, `androidx.profileinstaller`, Coil 2 (`coil-compose` + `coil-gif`), `libsu`, `kaml`, and `kotlinx-serialization` (plus `robolectric` in `testImplementation`).
5. **Never add WebViews** unless explicitly requested.
6. **Avoid unnecessary animations, background work, polling, and allocations.**
7. **Do not add unrequested features, redesign the UI, or replace working Compose architecture for stylistic reasons.**
8. **Beginner-friendly clarity:** Keep code readable and document *why* hardware/framework workarounds exist (e.g., MT6761 GPU limits, SystemUI reflection targets, R8 rules), rather than writing obvious comments stating *what* Kotlin syntax does.
9. **Git hygiene:** Do not silently rewrite unrelated code, delete files, or remove features without explicit permission. Never commit temporary compiler session files (`*.salive`) or signing credentials.

---

## 5. Performance & Memory Envelope

Measured footprint on the AT-M120 (`v2.4.2` signed R8 release APK, `3.17 MiB` / `3,328,228 B`, Android 12 API 31, `MemTotal: 1,906,344 kB`, `MochibunImeService` active as default IME — down from the legacy `~162 MB PSS / ~99 MB RSS` baseline):
- **Home Idle Memory (`dumpsys meminfo`):** **107–109 MiB Total PSS** (`109,865–112,010 KB`, `~60.8 MiB` Private Dirty; `9.6 MiB` Java Heap, `19.4 MiB` Native Heap, `18.4 MiB` Graphics; `VmRSS: 151.6–155.5 MB`, settling to **`~82.9 MiB` PSS / `126.3 MiB` `VmRSS`** once clean `.apk` pagecache evicts).
- **Post-Stress Peak Memory:** **93–106 MiB Total PSS** (`95,486–108,864 KB`, `67.5–76.9 MiB` Private Dirty, `17.29 MB` Skia GPU caches with `13.21 MB` purgeable) across App Drawer fast-scrolling, sort cycling, T9 search, pager swipes, Settings Hub, and IME Kaomoji Wheel.
- **Trim Recovery (`TRIM_MEMORY_RUNNING_CRITICAL` while backgrounded → `MemoryTrimTier.RELEASE_DRAWER`):** Reclaims **~6.8 MiB** PSS immediately (`95,486 KB → 88,556 KB`), releasing `2.5 MiB` Dalvik Heap Alloc, `1.9 MiB` Native Heap, and idle dialog/drawer views. (Note: while `MochibunImeService` is the bound keyboard, Android treats the process as foreground-service class and sends `RUNNING_CRITICAL` rather than `COMPLETE`.)
- **Activity Launch (`am start -W` after `am force-stop`, `LaunchState: WARM` with IME rebound):** **899 ms** median over 3 trials (`856–939 ms`; first post-install JIT pass `1,194 ms`). Startup disk/PNG restores (`CustomMascotManager.restore`, `CustomCardBorderManager.restore`) and Binder IPCs (`MediaVolumeLockSync`, `TorchManager`, Global settings syncs) run on `Dispatchers.IO` in `MochibunApp`.
- **Frame & GPU Timing (`dumpsys gfxinfo`, Skia OpenGL on PowerVR GE8300):**
  - *Home Idle (5 s / 47 frames):* **0.00% jank** (`0` missed vsyncs, `0` slow UI/draw frames), CPU **p50 10 ms / p90 14 ms / p95 14 ms / p99 21 ms**, GPU **p50 3 ms / p95 4 ms / p99 4 ms** (down from `1.85%` jank / `16 ms` p50 / `24 ms` p95 after coalescing the Home 4 Hz clock, coarsening cloud drift to 4 Hz, and translating `GreetingMarquee` in `graphicsLayer`).
  - *Interactive Stress (Drawer + Pager + Settings + IME):* `17.17%` jank on mixed navigation/IME (`233` frames; CPU p50 `19 ms` / p95 `65 ms`, GPU p95 `6 ms`); rapid App Drawer flings CPU **p95 93 ms / p99 117 ms** (down from `150 ms` p95 after gating `softGlowFocus` / `mochibunCardBorder` on `AppDrawerCard`), GPU **p50 5 ms / p95 5 ms / p99 7 ms**.
- Graphics, bitmap caches, and Compose layer allocations dominate memory far more than the Kotlin/Java heap.

**Strict Prohibitions:**
- **NEVER call `System.gc()`** or build "RAM cleaner" / aggressive cache-purging loops.
- **NEVER use `Modifier.blur()`** or runtime GPU blur shaders (causes severe frame drops on the PowerVR GE8300).
- **NEVER duplicate bitmaps** (e.g., mascot atlases, custom borders, or wallpapers) or instantiate new Coil `ImageLoader` instances. Always use the singleton bounded `ImageLoader` configured in `MochibunApp`.
- **1 GB Device Tier Guard (`isOneGbClassDevice`):** Detected via total physical RAM `< 1.5 GB` (`ONE_GB_CLASS_RAM_BYTES = 1_500_000_000L`, deliberately *not* `ActivityManager.isLowRamDevice`, which 2 GB Go builds also report). On ~1 GB devices, Coil memory cache drops from 5% to 2.5%, GIF decoders are omitted (rendering frame 0), and Mochibunny sprite animations lock to frame 0.
- **Memory Trim Policy (`MemoryTrimPolicy.kt`):**
  - `MemoryTrimTier.PICKER` (`MochibunApp.onTrimMemory`) evicts `IconPackManager.trimPickerCache()` (`4 MB` picker thumbnail LRU).
  - `MemoryTrimTier.BACKGROUND` (`MochibunApp.onTrimMemory`) clears `NotificationTracker.clearCache()` and `RecentTasksManager.clearCache()`.
  - `MemoryTrimTier.RELEASE_DRAWER` (`MainActivity.onTrimMemory` & `MochibunApp.onTrimMemory`) invokes `LauncherViewModel.releaseDrawerForMemory()` (dropping the full drawer list while keeping `earlyFavoriteApps` and calling `IconPackManager.retainOnly(favoriteComponentKeys)`), plus clears `MascotSpriteStore.clearCache()` and `clearDialogBitmapCaches()`; automatically reloaded on return via `restoreAfterMemoryRelease()` and on-demand decode.
- **Bounded Asset & Bitmap Caches:**
  - `IconPackManager`: Byte-weighted `LruCache`s (`8 MB` `appEntryCache` with $O(1)$ component-key replacement tracking via `activeAppCacheKeys`, `4 MB` `imageBitmapCache`); opaque 96px icons compacted to `RGB_565` via `BitmapUtils.compactIfOpaque()`.
  - `MediaSessionTracker`: Album art scaled to `ARTWORK_TARGET_PX = 160` (`RGB_565` for URI covers).
  - `RecentTasksManager` & `RecentsActivity`: Task snapshots downscaled crisp (nearest-neighbor) to `240 px` wide (`RGB_565`) + 32-entry `appMetaCache`; fallback icons clamped to `84 px` `RGB_565`.
  - `CustomMascotManager` / `CustomCardBorderManager` / `ThemeBundleManager`: Custom mascot atlas capped at `1.5 MB` (`<= 2048×2048`), custom 9-slice border capped at `512 KB` (`16–256 px`, single shared `ImageBitmap`), total `.mochibundle` ZIP decompression capped at `8 MB`.
- **Zero-Allocation & Lifecycle-Gated Hot Paths:**
  - `Choreographer.FrameCallback` in `VirtualCursorService` / `CursorPhysics` runs at 60 Hz when moving and unregisters immediately at rest for **0% idle CPU**. Allocate zero `PointF` objects, lambdas, or collections inside frame ticks.
  - `TopLavenderClouds` / `BottomLavenderClouds` share a single hoisted `LocalCloudDriftPx` ticker provided by `MochibunTheme` and gated to `Lifecycle.State.RESUMED`.
  - `MochibunImeService` gates its `ServiceLifecycleOwner` to `RESUMED` only while `onStartInputView`/`onFinishInputView` is active (`CREATED` otherwise) and evaluates `rememberLiveCardBorderState()` lazily only when the Kaomoji Wheel is open.
  - `Canvas` draw lambdas must never allocate `Path`, `Paint`, or `Stroke` objects per frame; use `remember`, `drawWithCache`, or the shared single-slot caches in `KawaiiCardMotifs.kt`.

---

## 6. UI, Visual Identity & Design System (`ui/`)

Mochibun uses a cohesive **pastel retro-kawaii keitai** aesthetic built on Jetpack Compose and Material 3, designed to feel like factory OEM firmware for a flip phone.

### Theme Engine (`ui/theme/LauncherTheme.kt`, `Theme.kt`, `ColorContrast.kt`, `SystemUiThemeBridge.kt`)
- **Built-in Palettes (`MochibunThemePalette`):**
  1. *Sakura Dream* (soft rose pink `#FFB7D5` / `#F8BBD0`, blush containers, deep plum/mauve accents `#4A2E56` / `#880E4F`)
  2. *Lavender Mist* (gentle lilac, twilight mist containers, deep grape accents)
  3. *Minty Matcha* (sage mint, seafoam containers, deep matcha accents)
  4. *Milky Peach* (apricot peach, warm cream containers, warm cinnamon accents)
  5. *Midnight Mochi* (dark pastel plum theme with contrast-checked tokens)
- **Token Binding:** All composables must read reactive `ActiveTheme` / `MochibunThemePalette` tokens rather than hardcoded hex colors so live theme switching updates every screen (and pushes colors to SystemUI via `SystemUiThemeBridge`) immediately.
- **Typography:** Custom pixel/bitmap aesthetic fonts (`MaruMinyaM` built-in, plus user `.ttf`/`.otf`/`.ttc` fonts) managed via `TypographyManager`.

### Shared Framing, Borders & Motifs
- **Card Borders (`KawaiiCardMotifs.kt`, `CardBorderStyle.kt`, `CustomCardBorderManager.kt`):**
  - `Modifier.mochibunCardBorder` renders either an active user-installed 9-slice `card_border.png` (`CustomCardBorderFrame` via `NineSliceDestGeometry` using `FilterQuality.None`) or one of 5 procedural `CardBorderStyle`s: `SOLID`, `DOUBLE`, `LACE`, `STITCH`, and `PIXEL`.
- **Keitai Lace Frame (`ui/components/KeitaiLaceContainer.kt`):**
  - The signature "two-tone organza" card container: a translucent scalloped ruffle (`containerColor` at 54% alpha with a 0.8dp rim at 28%), ivory pearls piped along the seam (`beadDiameter`), and an opaque rounded card (`containerColor` or `containerBrush` gradient) with a 1.35dp outline.
  - Geometry is computed along rounded corners by `RoundRectWalker` (unit-tested in `RoundRectWalkerTest`) inside `drawWithCache` so drawing allocates nothing. Provides Surface-equivalent `LocalContentColor`.
  - **Sizing Variants:** Home cards/headers and `SystemMonitorCard` use dainty sizing (`scallopDepth = 3.dp`, `beadDiameter = 4.dp`, `contentPadding = 6.dp`, inner content box clipped to card shape with click + ripple, `beadBorderColor = accentDeep @ 45%`). Dialogs use chunkier default sizing (`contentPadding = 14.dp` when converted from plain surfaces so content clears the pearl ring).
- **Single-Dismiss Dialogs (`KeitaiLaceDialogWithClose` in `ui/dialogs/DialogComponents.kt`):**
  - Dialogs with only a close/cancel action float `KawaiiClosePill` (154dp wide, 2dp overlap) over the bottom-center rim outside any scroll container. Two-button dialogs (Cancel + Create/Save/Delete) retain a standard bottom button row.
- **Page Return & Navigation Pills (`KeitaiNavigationPills.kt`, `SoftKeyPromptPill.kt`):**
  - Widgets (Page 0) and Notifications (Page 2) float `KawaiiReturnHomePill` bottom-center over the background clouds (`padding(bottom = 12.dp)`), outside scrolling lists.
- **Wallpaper Pixel Overlays (`WallpaperOverlay.kt`):**
  - Cycles through 4 pre-rendered 480×800 `drawable-nodpi` pixel overlays (bows, sparkles, gingham, hearts) selected deterministically by day of year.
- **Pixel-Art Rendering:** All pixel stickers (`Stickers.kt`, `PixelSpray.kt`), 1-bit Docomo pictograms (`DocomoPictogram.kt`, `12×12` tiles sliced from `R.drawable.docomo_atlas` with `clearDocomoAtlasCache()` on trim), ribbon bow badges, custom icons, custom 9-slice card borders, and mascot sprites must be drawn with **`FilterQuality.None`** (nearest-neighbor filtering) to preserve crisp 1:1 pixel clusters without bilinear blur.

### 480 × 800 Viewport & D-Pad Focus Contracts (Non-Negotiable)
- **Scroll Containment:** Every dialog, settings screen, and list must fit within `480 × 800` portrait bounds using `LazyColumn` or `Modifier.verticalScroll` with sufficient bottom padding so physical keys/IME pills never obscure controls.
- **Explicit Focusability:** Every interactive element must maintain a contiguous, deterministic focus contract (`Modifier.focusable()`, `Modifier.focusRequester()`, `onPreviewKeyEvent`). Never trap focus.
- **Unclipped Soft Glow Halos:** Focus indicators use Mochibun's soft pastel glow (`Modifier.softGlowFocus`). Parent cards, rows, and containers must enforce `clipChildren = false` and adequate padding so focus rings are never sliced off at container edges.
- **Focus Isolation:** Inactive pager pages and background cards must never consume D-pad events or mutate selection while a modal (`activeModal != null`) or overlay is open.

---

## 7. Package & Architecture Map

```text
.
├── .github/workflows/ci.yml       # CI: unit tests, lintDebug (0 NewApi), release manifest check, pack validation
├── PRIVACY.md                     # Offline posture & permission audit documentation
├── fdroid/config.yml              # F-Droid / IzzyOnDroid reproducible repo metadata
├── fastlane/metadata/android/     # Store descriptions, changelogs, screenshots
├── docs/
│   ├── community/workshop.html    # Browser-based .mochibundle / theme / mascot / border creator & gallery
│   ├── m120_root_setup.md         # AT-M120 root + LSPosed + QuickSwitch setup guide
│   └── theming_formats.md         # YAML theme, .mochibundle, mascot atlas, 9-slice border & .mochibunled specs
├── tools/
│   ├── capture_baseline_profile.sh # Captures ART profile from device -> app/src/main/baseline-prof.txt
│   ├── benchmark_m120.sh          # APK/dex sizes + adb launch, gfxinfo (Home idle, drawer fling) & meminfo report;
│   │                              # --stress-settings: fail-safe Settings Hub nav/D-pad/toggle stress (gfxinfo + meminfo)
│   ├── gen_t9_dict.py             # Generates assets/t9/en.tsv offline T9 wordlist
│   ├── validate_community_packs.py # Validates docs/community/catalog.json & .mochibundle assets
│   ├── export_src_bundle.py       # Generates source archive
│   ├── docomo_overlay/            # Generator for 480x800 pixel wallpaper overlays
│   └── mochibun_bootanim/         # Custom bootanimation.zip generator
└── app/src/main/
    ├── assets/
    │   ├── t9/en.tsv              # Bundled 30k+ English offline T9 frequency wordlist (SCOWL-filtered)
    │   └── xposed_init            # LSPosed entrypoints (MochibunKeyHook, MochibunSystemUiHook)
    ├── baseline-prof.txt          # Bundled ART Baseline Profile
    ├── cpp/
    │   ├── evinject/evinject.c    # Native root evdev daemon (libevinject.so) for <1ms touch/key/paste
    │   └── lidled/lidled.c        # Native AW22xxx LED register & deadline-timed frame loop daemon
    └── java/
        ├── com/android/quickstep/
        │   ├── RecentsActivity.kt       # QuickSwitch / Recents task switcher UI
        │   ├── RecentTasksManager.kt    # ActivityManager / dumpsys task & crisp RGB_565 snapshot loader
        │   └── TouchInteractionService.kt # SystemUI IOverviewProxy binder service
        └── one/crinklecod/mochibun/
            ├── MochibunApp.kt     # Process entry point; initializes singletons, trim hooks & bounded Coil loader
            ├── core/
            │   ├── haptic/        # HapticManager (boundary & interaction buzz)
            │   ├── keypad/        # LauncherKeypadContracts (Home focus traversal, T9 state, PetPill/Pound math)
            │   ├── shell/         # RootOperations (QuickSwitch, system commands)
            │   ├── sound/         # SoundEffectManager (procedural 16-bit PCM SoundPool themes)
            │   ├── system/        # RootShell, RootTouchInjector, ClamshellManager, HallSwitchReader,
            │   │                  # LidLedManager, TorchManager, KeyHookStatus, MemoryTrimPolicy,
            │   │                  # MediaVolumeLockSync, CursorServiceWatchdog
            │   ├── util/          # BitmapUtils, ContextUtils, CursorPhysics, MochibunSystemActions
            │   └── widget/        # MochibunWidgetHost, WidgetFrame (D-pad widget traversal), WidgetLayoutMath,
│                      # WidgetCanvasLayout (Page 0 freeform 16dp-grid placements, layers, presets)
            ├── data/
            │   ├── ime/           # T9Keymap, T9DictionaryEngine, UserWordStats, UserWordStore, LearnedWordExtractor
            │   ├── model/         # LauncherModels, FavoriteFolder, FavoriteSlotItem, CardBorderStyle,
            │   │                  # HardwareRemapModels, LidLedAnimation, MascotAtlasValidator,
            │   │                  # MochibunBundleManifest, MochibunNotificationItem, YamlTheme
            │   ├── prefs/         # PreferencesManager (public API: keys, flows, delegating getters/setters),
            │   │                  # internal stores (AppearancePrefs, HardwareInputPrefs, SoundPrefs, LidLedPrefs,
            │   │                  # QuickMemoPrefs, HomeContentPrefs, SystemAccessPrefs), CursorPreferences
            │   └── repository/    # ExternalThemeManager, ThemeBundleManager, BackupRestoreManager,
            │                      # IconPackManager, CustomMascotManager, CustomCardBorderManager,
            │                      # HardwareRemapManager, LidLedAnimationRepository, KaomojiRepository,
            │                      # J2meGameRepository, NotificationTracker, MediaSessionTracker,
            │                      # TypographyManager, SystemFontSyncManager, WidgetRepository
            ├── pet/
            │   ├── PetLogic.kt         # Pure Kotlin rules, mood decay math, daily treat caps
            │   ├── PetManager.kt       # Atomic StateFlow<PetState> & serial IO persistence worker
            │   ├── PetPersistence.kt   # SharedPreferences (mochibunny_pet) & JSON backup codec
            │   └── ChargerPlugTracker.kt # Ignores initial sticky power broadcast for treat granting
            ├── service/
            │   ├── InputArbiter.kt        # Coordinates typing vs raw-key layers & IME input-active state
            │   ├── ImeInputMode.kt        # Pure Kotlin ShiftMode, '*' mode cycling & predictive field gate
            │   ├── PredictiveInput.kt     # Pure Kotlin T9 digit buffer, one-shot auto-cap & case application
            │   ├── MochibunImeStatus.kt   # Tracks enabled/default IME state
            │   ├── MochibunImeService.kt  # System-wide Keitai multi-tap + T9 IME + floating pill + # Kaomoji Wheel
            │   ├── VirtualCursorService.kt # TYPE_APPLICATION_OVERLAY cursor + Choreographer loop
            │   ├── MochibunAccessibilityService.kt # Optional zero-event non-root gesture/key fallback
            │   └── MochibunNotificationListenerService.kt # Feeds NotificationTracker & MediaSessionTracker
            ├── ui/
            │   ├── MainActivity.kt        # HOME activity; dispatches keys, screen-off reset, intents & trim
            │   ├── LauncherViewModel.kt   # App loading, favorites/folders, wallpaper & memory release/restore
            │   ├── MainLauncher.kt        # 3-page HorizontalPager (0=Widgets, 1=Home, 2=Notifs) & top-level composition
            │   ├── LauncherModalHost.kt   # ActiveModal sealed interface + modal routing/rendering
            │   ├── LauncherNavigationState.kt # Nav/focus state holder, keypad actions, key interceptor & Back arbiter
            │   ├── LauncherKeyHandler.kt  # Contextual D-pad, T9, LSK/RSK, PetPill, Home & shortcut key routing
            │   ├── components/            # KeitaiLaceContainer, KawaiiCardMotifs, KeitaiCandidateStrip,
            │   │                          # KeitaiNavigationPills, SoftKeyPromptPill, KeitaiEffects,
            │   │                          # DocomoPictogram, MochibunMascot, MascotSprites, MascotReaction,
            │   │                          # PetActionPill, NowPlayingCard, SystemMonitorCard, QuickSettingsCard,
            │   │                          # PhotoFrameCard, AppWidgetHostCard, WidgetPage, SubDisplayTickerFeed,
            │   │                          # WallpaperOverlay, JumpNumberBadge, PixelSpray, Stickers
            │   ├── dialogs/               # DialogComponents (KeitaiLaceDialogWithClose), QuickMemoDialog,
            │   │                          # AppActionDialog, FolderActionDialog, FavoriteFolderDialog,
            │   │                          # FolderTrayNavigation, FolderTiltNudge, CreateFavoriteFolderDialog,
            │   │                          # KaomojiWheel (IME-only), MochibunKeyGuideDialog, HardwareButtonsDialog,
            │   │                          # ThemeBundleDialogs, WidgetDialogs, PhotoFrameActionDialog,
            │   │                          # IconPickerDialog, IconPackSelectionDialog, HiddenAppsDialog,
            │   │                          # LidLedColorPickerDialog, QuickSnippetsDialog, ResizeWithKeyboard
            │   ├── overlay/               # CursorOverlayView (hardware-translated cursor sprite view)
            │   ├── screens/               # HomeScreen, AppDrawerScreen, NotificationsScreen, ThemeStudioDialog,
            │   │                          # SettingsHubScreen & settings/* (11 files incl. LidLedStudioScreen,
            │   │                          # J2meTemplateSyncDialog)
            │   └── theme/                 # LauncherTheme, Theme, Color, Type, ColorContrast, SystemUiThemeBridge
            └── xposed/
                ├── MochibunKeyHook.kt       # system_server hook (PhoneWindowManager.interceptKeyBeforeQueueing)
                ├── MochibunSystemUiHook.kt  # com.android.systemui entry point (xposed_init); thin coordinator
                └── systemui/                # Hook modules, each with install(lpparam):
                                             # ActiveHookTheme.kt (theme state, broadcast sync, weak view
                                             #   registries, refreshAllHookedViews, squircle builders),
                                             # StatusBarHooks, VolumeDialogHooks, QuickSettingsHooks,
                                             # LockscreenHooks, SystemDialogsPowerHooks
```

---

## 8. Detailed Subsystem Specifications (Do Not Break)

### A. Core Launcher, Widgets & Navigation (`MainActivity`, `MainLauncher`, `LauncherKeyHandler`, `WidgetPage`)
- **3-Page Pager:** `0 = Widgets` (`QuickSettingsCard` 1×4 pill bar, `NowPlayingCard`, `SystemMonitorCard`, `PhotoFrameCard`, and hosted Android `AppWidget`s via `MochibunWidgetHost` / `WidgetFrame`), `1 = Home` (Welcome banner + optional `SubDisplayTickerFeed`, Clock/Date, Favorites card, Mochibunny mascot, bottom clouds, and `WallpaperOverlay`), `2 = Notifications`.
- **Sub-Display Ticker & Quick Memo (`SubDisplayTickerFeed.kt`, `QuickMemoDialog.kt`):**
  - **Quick Memo slots:** 5 slots (`QUICK_MEMO_SLOT_COUNT`, 140 chars each). Slot 1 keeps the legacy `pref_quick_memo_text` key (so pre-slot memos read back as slot 1); slots 2–5 use `pref_quick_memo_text_<n>`, plus `pref_quick_memo_pinned_slot` / `pref_quick_memo_last_opened_slot`. In `QuickMemoDialog`, D-pad Up from the text opens the slot bar (`[1★] [2•] …`, rendered with `1.dp` unselected pill borders and a `•` dot on non-blank unpinned slots): Left/Right or digits `1`–`5` switch (saving the draft), Center or `*` pins. Digits only switch slots while the bar is focused, never while typing. The editor draws faint `1.dp` horizontal notebook lines behind the text (`drawBehind`) and swaps the helper prompt between `↑ slots` (editor focused) and `Center: Pin ★ • Back: Close` (slot bar focused). The ticker shows the pinned slot, else the first non-blank slot. Slots and the pinned slot are included in backups (`quick_memo`).
  - `GreetingMarquee` renders a 3 Hz stepped marquee inside `WelcomeHomeBanner` (showing pinned Quick Memo up to 140 chars, now playing, unread notification count, next alarm, daily steps via one-shot `TYPE_STEP_COUNTER` on resume, battery, and weekday-seeded cozy fortunes), with optional dark sub-OLED styling (`pref_sub_display_ticker_dark`).
- **App Drawer:** Opened via swipe-up on Home or pressing physical `HOME` while already on Home. Supports T9 numeric search (`2`–`9`), sort modes (`ALPHABETICAL`, `NEWEST`, `MOST_USED`), custom icons, and hidden apps.
- **Favorites Carousel (`HomeScreen` / `FavoritesPagingTest`):**
  - Paginated 3-slot card (`FAVORITES_PAGE_SIZE = 3`). Visible page is derived from `selectedFavoriteIndex` so D-pad Left/Right and reordering flip pages automatically.
  - Supports horizontal touch swipe and tappable `♥ •` page dots when > 3 favorites exist. Paging math lives in `favoritePageCount`, `favoritePageForIndex`, and `firstFavoriteIndexOfPage`.
- **Sticker-Tray Folders (`FavoriteFolderDialog.kt`, `FolderTrayNavigation.kt`, `FolderTiltNudge.kt`):**
  - Renders open folders as a recessed sticker tray of up to 6 pastel squircle charms per page sitting atop `BottomLavenderClouds` with a `PixelRibbonBow` overhang, supporting scattered sticker placement or a tidy 3×2 grid (`FolderTrayNav`), optional accelerometer tilt nudge while resumed (skipped on ~1 GB devices), live name pill, and `KawaiiClosePill` resting on the clouds.
- **Interactive Key Guide & Keypad Remapper (`MochibunKeyGuideDialog.kt`, `HardwareButtonsDialog.kt`):**
  - Renders a visual AT-M120 flip-phone keypad diagram with live physical key highlighting across `Launcher`, `Typing`, and `Remap` tabs (`Settings > Keyboard` opens on `Launcher`/`Typing`; `Settings > Virtual Cursor` launches directly on the refreshed `Remap` tab for per-key app/system action binding).
- **Settings Hub Icons (`SettingsHubScreen.kt`, `DocomoPictogram.kt`):**
  - Category navigation rows and action dialogs render crisp 1-bit `12×12` keitai Docomo pixel pictograms sliced from `R.drawable.docomo_atlas` (`FilterQuality.None`, `clearDocomoAtlasCache()` on trim).
- **Physical Keypad & Contextual Soft-Keys:**
  - **LSK (`KEYCODE_APP_SWITCH` / scan 580 / `KEYCODE_MENU`) & RSK (`KEYCODE_BACK`):** Gated by `pref_soft_key_nav_enabled` (mirrored to `Settings.Global` `mochibun_soft_key_nav_enabled` with a 2 s cache in `MochibunKeyHook`). On Home (Page 1), LSK tap jumps left to Widgets (Page 0), LSK (`KEYCODE_APP_SWITCH`/scan 580) hold (~400 ms) opens `RecentsActivity`, and RSK jumps right to Notifications (Page 2). On side pages, Back returns to Home. In App Drawer, LSK opens `AppActionDialog` for the focused app and RSK cycles sort mode without closing the drawer (when Soft-Key Nav is off, Back closes the drawer normally). In third-party apps, both pass through untouched to the OS.
  - **Physical `HOME` key (scan codes 172 & 102):** Short press returns to Home (or opens App Drawer if already on Home). Holding `HOME` (~400 ms) toggles the Quick Settings shade open/closed. `MochibunKeyHook` tracks `isHomePressedOnMochibun` and swallows `ACTION_UP` so `PhoneWindowManager` never collapses system panels or broadcasts `closeSystemDialogs()` on release.
  - **`0` key:** Holding on Home toggles the hardware flashlight via `TorchManager` (synced with Android's `TorchCallback` so Quick Settings toggles never desync state).
  - **`*` key:** Context actions for current focus on press; repeat toggles ringer mode (Normal / Vibrate).
  - **`#` key:** Fixed Kaomoji trigger constant (`matchesKaomojiTrigger`). Holding (~400 ms) on Home opens `QuickMemoDialog` (`ActiveModal.QuickMemo`); short tap opens Launcher Menu (`openLauncherMenu()`). The global radial Kaomoji Wheel opens on `#` hold while typing inside `MochibunImeService`.
  - **`F4` / Cursor Toggle Key:** `F4` (scan code 62) always toggles `VirtualCursorService`. An optional secondary **Cursor Toggle Key** (`F4` / `Menu` / `Search` / `Camera` / `*` in `CursorPreferences.TOGGLE_KEY_OPTIONS`) is mirrored to `Settings.Global` `mochibun_cursor_toggle_keycode` (2 s cache in hook) and matched via `MochibunSystemActions.isCursorToggleKey`. `*` never toggles the cursor while the IME is actively typing (`InputArbiter.updateInputActive` pushes `ACTION_INPUT_ACTIVE_CHANGED` to the hook only when `*` is selected as the toggle key).

### B. Built-in Keitai IME (`MochibunImeService` & `KeitaiCandidateStrip`)
- **System-Wide Multi-Tap & Offline T9 Predictive Keyboard:** Replaces third-party T9 IMEs across all apps (standard editors and `TYPE_NULL` terminals like Termux, coordinated via `InputArbiter`). Includes `ImeSettingsActivity` (`activity-alias` to `MainActivity`) to jump directly into keyboard settings.
- **Offline T9 Engine (`T9DictionaryEngine`, `T9Keymap`, `PredictiveInput`, `ImeInputMode`, `LearnedWordExtractor`, `UserWordStore`):** Short `*` taps act on key release (`resolvePendingTap()`) to cycle `abc -> T9 -> 123 -> abc` (skipping `T9` in password/PIN/URI/email/number fields or when `pref_ime_predictive_t9_enabled` is off) while preserving shift; holding `*` cycles letter casing live (`Abc -> ABC -> abc`, or returns from `123` to the previous text mode with `Abc`). Single-digit T9 queries (`length == 1`) always include all mapped letters for that digit before dictionary completions. `T9DictionaryEngine` loads `assets/t9/en.tsv` (generated via `tools/gen_t9_dict.py`) or user `.tsv`/`.txt` dictionaries from `getExternalFilesDir(null)/dictionaries` (and `/sdcard/Mochibun/dictionaries` only if `listFiles()` succeeds) asynchronously on `Dispatchers.IO`, keeping `ready = false` (falling back cleanly to multi-tap) until loaded and falling back to `"en"` if a custom file is missing. `UserWordStats` promotes dictionary words committed 2+ times per dictionary (`pref_ime_t9_learned_words`), while `UserWordStore` learns non-dictionary words typed before `#` in eligible fields without `IME_FLAG_NO_PERSONALIZED_LEARNING` (`pref_ime_t9_user_words`, shown once count `>= 2` and promoted above dictionary words at `>= 3`), both capped at 500 entries/dict and guarded by `pref_ime_t9_learned_epoch` across **Clear Learned Words** and `.json` backup restore.
- **Zero-Resize Full-Height Window:** Uses a transparent, full-height IME input view while reporting full-height content/visible insets so underlying apps never pan or resize. Declares an empty touchable region so screen taps pass straight through to the foreground app; all typing happens via physical keys.
- **Smart Caret Docking:** Monitors `CursorAnchorInfo`. When a valid caret enters the bottom pill zone, the floating `KeitaiCandidateStrip` docks to the top (regardless of horizontal X) and returns to the bottom with hysteresis (`DOCK_ENTER_MARGIN_DP = 12dp`, `DOCK_EXIT_MARGIN_DP = 28dp`). If the anchor is missing/invalid or top docking would overlap the caret, it stays at the bottom.
- **Idle Fade & Manual Selection:** Top/bottom docking animates only the pill view (zero per-frame window/inset relayout). After `1,500 ms` without key-down, pill alpha fades to `0.35f` using `graphicsLayer(compositingStrategy = CompositingStrategy.ModulateAlpha)` (avoiding an offscreen layer) and restores on next key-down. Pressing D-pad Left/Right to manually pick a candidate disables the auto-commit timer until committed or cleared; active T9 words wait for Space / Center / navigation rather than auto-committing on idle.
- **Symbol Pools & Global 8-Slice Kaomoji Wheel:** `1` cycles conversational punctuation, `0` cycles programming/shell symbols (starting with `*`), and holding `#` opens the 2-stage radial Kaomoji Wheel (`KaomojiWheelCard` + `KaomojiRepository`, 8 mood slices × up to 10 kaomoji) to commit directly via `currentInputConnection`. Stage 1 (`WheelStage.CATEGORY`) supports 4-way D-pad cardinal selection, quick perpendicular 2-tap diagonal tilt (`COMPASS_TILT_WINDOW_MS = 900 ms`), or `1`–`8` direct slice jump; Stage 2 (`WheelStage.ITEM`) supports `1`–`9` / `0` (`#10`) instant quick-pick or `◀▶` browsing, wrapping wide faces (`> 13` chars) in `rememberSteppedTickerText` (3 Hz).
- **Clips & Snippets Wheel Mode (`WheelMode`, `ClipboardRingRepository`, `QuickSnippetPrefs`):** `*` (in any wheel stage) or `0` (in Stage 1 / Clips mode, or tapping the header) flips the `#` wheel to `CLIPBOARD_SNIPPETS`: up to 8 petals = pinned Quick Snippets (`★`, max 6 × 240 chars, `pref_quick_snippets` JSON array, backed up as `quick_snippets`) then recent clips. D-pad steps, Center or `1`–`8` commits the full text (IME) or copies it (launcher dialog, digits act on release). Recent clips are in-memory only (8 × 240 chars, deduped, never backed up), captured by the IME's `OnPrimaryClipChangedListener` (the default IME may read the clipboard in the background) plus `MainActivity` resume/focus sync gated on `ClipDescription.timestamp`; blank and `IS_SENSITIVE` clips are skipped before the content is read. Snippets are edited in *Settings → Keyboard → Quick Snippets* (`QuickSnippetsDialog`).

### C. Virtual Mouse Cursor (`VirtualCursorService`, `CursorPhysics`, `evinject`)
- **Overlay Window:** Foreground service running `CursorOverlayView` in `TYPE_APPLICATION_OVERLAY`, supporting static `.png` and animated `.gif` custom sprites (`FilterQuality.None`). Only runs while the cursor is visible; calling `start(context, showCursor = false)` immediately `stopSelf()`s so there is no idle notification.
- **Kinetic Physics & Vsync Loop:** `CursorPhysics` implements dual-cadence motion—a discrete micro-step nudge on short taps (`< 120 ms`) for tiny UI targets, and smooth exponential velocity ramping (`baseSpeed` → `maxSpeed`) on sustained D-pad holds, with snappy friction decay on key release. Driven by `Choreographer.FrameCallback` at 60 Hz only while moving (0% idle CPU) and positioned via hardware-accelerated `translationX` / `translationY` (never `updateViewLayout`). Keys `2` and `8` perform smooth vertical scroll swipes.
- **Touch & Gesture Routing (`RootTouchInjector`):**
  - **Root (`evinject.c` → `libevinject.so`):** Streams 24-byte binary packets over an anonymous pipe stdin to the persistent root `evdev` daemon for `< 1 ms` taps, swipes, key events, and clipboard pastes (compared to `~470 ms` JVM startup per `input tap` shell call), falling back to legacy `input` commands if the helper fails or the display is rotated.
  - **Non-Root (`MochibunAccessibilityService`):** `RootTouchInjector.chooseRoute()` automatically routes taps and scroll swipes to `MochibunAccessibilityService.dispatchGesture` when `su` is absent or denied and the user enabled the service.

### D. Clamshell & Outer Lid LED Studio (`ClamshellManager`, `LidLedManager`, `LidLedStudioScreen`)
- **Hall Sensor & Non-Root Fallbacks:** `HallSwitchReader` monitors `/dev/input/event0` (with root) and `ClamshellManager` supports `Sensor.TYPE_HINGE_ANGLE` (API 30+) to trigger lid open/close sounds, dismiss modals/cursor, and run `LidOpenAction` (`HOME`, `RESUME_LAST_APP`, `NOTIFICATIONS`, `QUICK_MEMO`). On non-root devices without a hardware lid source, `ClamshellManager` tracks `ACTION_SCREEN_OFF` → `ACTION_SCREEN_ON` for `pendingOpenActionSequence`, and `MainActivity.screenOffReceiver` handles **Screen-Off Flip Reset** (`KEY_NON_ROOT_SCREEN_OFF_RESET`).
- **Hardware Detection Gate:** `LidLedManager.hasLedHardware` checks `/sys/class/leds/aw22xxx_led` (with a root `[ -e ]` probe when SELinux hides the sysfs node). Only a definitive root "missing" result hides LED UI rows; unknown behaves as present.
- **AW22xxx Native Playback (`lidled.c`):**
  - `LidLedManager.buildStepAnimationCommand` first runs `lidled off` (chip reset `0x01 0x55` to clear stock firmware programs like `aw22xxx_cfg_led_breath.bin` loaded when plugging in a charger), then spawns a single `lidled frames <step_ms> <loops> <last|off> <mask:hex>...` daemon using an absolute-deadline timer and existing `write_channel_pattern()` / `write_off()` register writes.
  - **CRITICAL:** Never drive animations via per-step `lidled pattern` calls or shell loops, and never alter the AW22xxx register sequence—re-spawning per step causes frame drops and flicker on the AT-M120.
  - Notification/charging animations loop for ~60 s and hold their final "Resting" frame; charging/notification-while-plugged commands prepend `buildSuppressStockChargeCommand()`. Below `CRITICAL_BATTERY_PCT` (5%), the ladder forces LEDs off. Any subsequent `lidled` invocation stops the running daemon.
  - Dev override: The app prefers `/data/local/tmp/lidled` over the APK-bundled binary when present—keep it synced when modifying `lidled.c`.
- **Lid LED Studio & `.mochibunled` Sharing:**
  - Located under *Settings → Behavior → Clamshell & Lid → Lid LED Studio* (`SettingsDestination.LidLedStudio`, root-only).
  - Supports 5 built-in animations (`builtin_*`), custom YAML/JSON files in `filesDir/lid_animations/<id>.yaml` (`100–5000 ms` step time, `1–16` steps × 5 LED windows `W1..W5` using 7 `LidLedSwatch` colors or `#000000` off), and separate `NOTIFICATION` / `CHARGING` slots (priority ladder: `CALL > NOTIFICATION > CHARGING > OFF`).
  - **Per-App LED Rules** (Studio row → `LidAppLedRulesScreen`): `LidAppLedRule` (`DEFAULT` / `CUSTOM_COLOR` / `CUSTOM_ANIMATION` / `MUTED`) stored as JSON in `pref_lid_led_app_rules` and backed up as `lid_led_app_rules`. `LidLedManager.updateLedState` drops muted apps' notifications (ringing calls are never muted), then resolves the newest eligible notification's rule: `CUSTOM_COLOR` → `startShimmer`, `CUSTOM_ANIMATION` → `startNotificationStepAnimation` (a deleted id falls back to the global slot). Applied state is keyed on `NotificationLook.signature` (mode + color + animation id), so consecutive posts from apps with different looks re-trigger.
  - Only `*.mochibunled` files register for `ACTION_VIEW` in `AndroidManifest.xml` (deliberately not `.yaml`/`.yml` so theme YAMLs are not hijacked) and route through `MainActivity.pendingThemeBundleUri` → `LidLedAnimationRepository.importFromUri`.

### E. Mochibunny Pocket Pet & Sprite Atlas (`pet/`, `MascotSprites.kt`, `MochibunMascot.kt`)
- **Zero-Timer Lazy State (`PetLogic.kt`):** Pure Kotlin logic (tested in `PetLogicTest`). Hunger/happiness/energy decay is computed lazily from `lastUpdatedMs` (clamped `0..48 h`, floor `0.15`) and local `epochDay`. `PetManager` manages an atomic `StateFlow<PetState>` (`feed()` uses CAS loops) and writes snapshots to `mochibunny_pet` prefs on a serial IO worker only when non-decay fields change (`refresh()` on Home resume never writes).
- **Treat Economy:** Daily caps of `5 / 3 / 2` (max `9` stored treats) earned from:
  1. Steps via `TYPE_STEP_COUNTER` (when ticker is enabled and `ACTIVITY_RECOGNITION` is granted; note the AT-M120 hardware lacks a step sensor).
  2. Clearing notifications via `NotificationTracker.cancelAllNotifications()` (`>= 1` cleared).
  3. Plugging in power via `ChargerPlugTracker` (initial sticky broadcast is baseline only).
- **In-Home Pet Pill (`PetActionPill.kt`):** Rendered inline on Home via `ActiveModal.PetPill` (not a dialog). `LauncherKeyHandler.handlePetPillKey` captures Left/Right, Center (on release), `1`–`3`, and Back while swallowing other Home keys; auto-dismisses on tap outside or 4 s idle. Uses `softGlowFocus(bringIntoView = false)` so focus never scrolls the Home `HorizontalPager`, while `isHomeVisible` keeps mascot bobbing/glow alive.
- **Shared Sprite Atlas (`MascotSpriteStore`):**
  - Holds **exactly one** decoded `ImageBitmap` app-wide: 8 animation rows (`idle`, `talk`, `happy`, `hungry`, `sulky`, `sleepy`, `eat`, `pet`) × 1–16 frames. Built-in `R.drawable.mochibunny_atlas` (`1024 × 2048`) decodes at `inSampleSize = 2` (~2 MB).
  - Driven on Home by a single 4 Hz `rememberKeitaiStep` (`TICK_WRAP = 720720`), read only during layout/draw phases. Reactions (`eat`, `pet`, `talk`) play twice (2 s) before returning to the mood's resting row.
  - Custom user atlases (`filesDir/custom_mascot_atlas.png`) are validated before decoding via `MascotAtlasValidator` (PNG header + `inJustDecodeBounds`, `16–256 px` frames, `<= 2048 × 2048`, `<= 1.5 MB`) and can be bundled inside `.mochibundle` / `.mochibun` archives as `mascot_atlas.png`.

### F. Themes, Borders, Fonts, Icons & Backups (`data/repository/`)
- **Icon Customization:** Automatic `kawaii.icon.pack` detection when installed, manual icon picker with search, custom per-app images, and third-party icon packs (`IconPackManager`).
- **External Themes, Fonts & System Font Sync:** Custom YAML themes parsed via `kaml` from `/sdcard/Android/data/one.crinklecod.mochibun/files/themes/` (`ExternalThemeManager`). Custom `.ttf`/`.otf`/`.ttc` fonts loaded from `/sdcard/Android/data/one.crinklecod.mochibun/files/fonts/` (and `/sdcard/Mochibun/fonts/` if readable) via `TypographyManager`. Rooted users can optionally push an imported `.mochibundle` font system-wide via `SystemFontSyncManager` (Magisk overlay at `/data/adb/modules/mochibun_system_font/`, safely prepending `mochibun_custom.ttf` to `<family name="sans-serif">` in `/system/etc/fonts.xml` while leaving `.ttc` collections and `AndroidClock.ttf` untouched).
- **Shareable `.mochibundle` / `.mochibun` Archives (`ThemeBundleManager`):** ZIP setup bundles (`ACTION_VIEW` for both `.mochibundle` and legacy `.mochibun`) containing `manifest.json` (palette, `borderStyle`, `cardBorderSlicePx`, `fontFamilyId`, `hasFont`/`fontDisplayName`, `hasSoundPack`/`soundPackName`, cursor physics, greeting, mascot layout/dialogue), `wallpaper.png`, `cursor.gif`/`cursor.png`, `mascot_atlas.png`, `card_border.png` (`<= 512 KB`, `16–256 px`, slice `4–64 px`), optional `font.ttf`/`font.otf` (`<= 4 MB`, installed as `TypographyManager.BUNDLE_FONT_ID = "bundle"`), and optional `sounds/<cue>.ogg` (`<= 256 KB` per cue, `OggS` header, installed as `SoundEffectManager.BUNDLE_THEME_ID = "bundle"` with fallback to `kawaii` for unbundled cues), bounded by an `8 MB` total decompression cap.
- **Backup & Restore (`BackupRestoreManager`):** Full JSON export/restore of launcher preferences, favorites, folders, and optional `"pet"` state (deliberately excluding device-specific `AppWidget` IDs managed by `WidgetRepository`).

### G. LSPosed System Hooks (`xposed/`) & Non-Root Accessibility Fallback
1. **`MochibunKeyHook.kt` (`system_server`):**
  - Hooks `PhoneWindowManager.interceptKeyBeforeQueueing` to intercept `F4` / custom cursor toggle key, directional D-pad keys while cursor mode is active, remapped hardware keys (`F1`–`F3`, `CAMERA` → `ACTION_HARDWARE_KEY_TRIGGER`), Media Volume Lock (`KEYCODE_VOLUME_UP`/`DOWN` → `STREAM_MUSIC` outside calls), LSK/RSK soft-keys on Home/Drawer (`ACTION_LSK_TRIGGER`, `ACTION_BACK_TRIGGER`), and `HOME` press/hold (`ACTION_HOME_TRIGGER`) without triggering `closeSystemDialogs()`.
  - Broadcast `EXTRA_KEY_EVENT`s must be copies (`KeyEvent(event)`): `system_server` recycles the live event right after `interceptKeyBeforeQueueing`, while the broadcast is parceled later, on delivery. Sharing it made one BACK arrive as two `ACTION_UP`s under load (closing the whole Settings Hub from a sub-screen). App receivers read events through `MochibunSystemActions.hookKeyEvent`, which trusts the send-time `EXTRA_ACTION` / `EXTRA_REPEAT_COUNT` ints, so an APK still paired with an older, unrebooted hook stays correct.
  - Publishes its boot-scoped heartbeat to `Settings.Global` (`KeyHookStatus`), uses `Binder.clearCallingIdentity()` when reading `Settings.Global` mirrors (`2 s` TTL cache), and preserves `runCatching` reflection guards for AOSP/MediaTek ROM stability.
2. **`MochibunSystemUiHook.kt` (`com.android.systemui`; coordinator for the `xposed/systemui/` hook modules):**
  - **Status Bar:** Hides cellular signal bars, data indicators, and no-SIM badges while keeping Wi-Fi (`StatusBarIconControllerImpl`, `StatusBarSignalPolicy`, `StatusBarMobileView`); suppresses carrier text on lockscreen & status bar (`CarrierText`, `CarrierTextManager`, `OperatorNameView`); overrides `DarkIconDispatcherImpl` single-tone tints using `ActiveHookTheme` tokens.
  - **4-Heart Battery (`BatteryMeterView`):** Forces percentage text visible, hides stock battery graphic, renders 4 hearts (`♥♥♥♥` → `♡♡♡♡`) with a ` ✦` charging star, and tints rose (`#F06292`) at `<= 15%` when discharging (tested in `BatteryHeartsTest`).
  - **Squircle Volume Dialog (`VolumeDialogImpl`):** Styles container as a 20dp pastel squircle, tints sliders, enforces single active stream (`shouldBeVisibleH` / `mShowActiveStreamOnly`), hides stock charcoal capsule, collapses ringer drawer (`View.GONE`), and scales slider height to 128dp for `480 × 800` (tested in `VolumeDialogPaletteTest`).
  - **Kawaii Quick Settings (`BlurUtils`, `NotificationShadeDepthController`, `ScrimController`, `QSTileViewImpl`, `QSIconViewImpl`, `BrightnessSliderView`):** Eliminates runtime blur (`0px`), applies pastel scrim from `ActiveHookTheme`, styles tiles as 14dp squircles with 1.2dp borders across Active/Inactive/Unavailable states, tints footer buttons & brightness slider, and skins notification rows (`ActiveHookTheme.notificationRows`) (tested in `QuickSettingsPaletteTest`).
  - **Lockscreen, Power Menu, Dialogs, Toasts & Screenshot Shelf:** Applies pearl lock styling (`createPearlLockDrawable`) and bouncer fade (`calculateBouncerFadeAlpha`); applies 22dp `container` squircles (`1.5dp` `accentDeep` 30% stroke) to `GlobalActionsDialogLite$ActionsDialogLite`, `GlobalActionsPowerDialog`, stock-layout `AlertDialog` / `SystemUIDialog` windows (skipping custom dialogs like `MediaOutputBaseDialog`), Toast pills, and `ScreenshotView`; styles tiles, buttons, and `ScreenshotActionChip` with 16dp focus-aware squircles (`primary` fill + 2dp `accentDeep` focus ring, preserving stock red on emergency actions; tested in `SystemDialogPaletteTest`).
3. **`MochibunAccessibilityService.kt` (Optional Non-Root Fallback):**
  - Declares `canRetrieveWindowContent="false"` and zero `accessibilityEventTypes` (`onAccessibilityEvent` is a no-op).
  - Steps aside automatically for key filtering whenever `KeyHookStatus.isActive(context)` is true; otherwise provides cursor toggle/routing, LSK/RSK forwarding on Mochibun Home/Drawer, global actions, and `dispatchGesture` taps/scrolls for `RootTouchInjector`.

### H. J2ME `.jar` Games & Template Sync (`J2meGameRepository`, `J2meTemplateSyncDialog`)
- **SAF `.jar` Indexing (`J2meGameRepository`):** Scans the persisted SAF folder (`PreferencesManager.getJ2meFolderUri`) on `Dispatchers.IO`, parses `META-INF/MANIFEST.MF` (`MIDlet-Name` / `MIDlet-1`) and PNG/image icons (using a bounded single-pass candidate map `<= 512 KB` with second-pass fallback, or a cached 24×24 pixel-art cartridge fallback bitmap), compacts decoded `<= 96 px` icons via `compactIfOpaque()`, and stores them in a `2 MB` byte-weighted `LruCache` (`JAR_CACHE_MAX_BYTES`). Games surface in App Drawer, Favorites, and Folders and launch via `ACTION_VIEW` (`application/java-archive`) with `FLAG_GRANT_READ_URI_PERMISSION` for AbyssME or J2ME Loader.
- **Template Config Sync (`TEMPLATE_SYNC_KEYS`, `J2meTemplateSyncDialog`):** Accessed under *Settings → Behavior* (`BehaviorSettingsScreen.kt`). Captures a reference `config.json` from any detected game and selectively copies only `TEMPLATE_SYNC_KEYS` (controls, key mappings, touch overlay visibility, display/aspect-fit scaling) onto target `config.json` files via `org.json.JSONObject` (`mergeConfigTemplate`), preserving per-game resolution and compatibility keys (`detectedScreenWidth`, `detectedScreenHeight`, `CompatibilityProfile`, `compatibilityReasons`, `SystemProperties`). Operates over a persisted SAF `configs` tree grant (`ABYSSME_CONFIGS_DOCUMENT_ID`) or `RootShell` fallback (`/storage/emulated/0/Android/data/io.github.eriark.abyssme/files/configs/*/config.json`), and supports optional auto-sync before `.jar` launch (`launchGame`) and after folder scans.
- **`J2meTemplateSyncDialog` UI:** Hosted in `KeitaiLaceDialogWithClose` with `[1]`–`[5]` jump badges, a pastel `SettingsTogglePill` on row 3 (`Auto-Sync on Launch`), permanently attached `basicMarquee` on focused row titles, and unclipped `softGlowFocus` halos for `480 × 800`.

---

## 9. Build, Testing & Release Verification

- **Toolchain:** AGP 9 + Kotlin 2.2 (Compose compiler plugin) + CMake 3.22 (`evinject`, `lidled`), `compileSdk = 37`, `minSdk = 27`, `targetSdk = 31`, `abiFilters = ["armeabi-v7a"]`.
- **Mandatory Verification Commands:** After any code, resource, or native change, run:
  ```bash
  ./gradlew app:compileDebugKotlin       # Fast Kotlin compilation check
  ./gradlew app:testDebugUnitTest        # Full JVM unit test suite (app/src/test)
  ./gradlew app:assembleDebug            # Full debug APK + native CMake build
  ```
- **Lint & CI Policy Checks:** Keep `./gradlew app:lintDebug` free of `NewApi` errors (all API 28+ calls must have `Build.VERSION.SDK_INT` guards) and ensure the merged release manifest never introduces `INTERNET` or `CAMERA` permissions (enforced in `.github/workflows/ci.yml`).
- **Community & Baseline Tooling:** Run `python3 tools/validate_community_packs.py` when editing `docs/community/catalog.json` or `.mochibundle` packs; regenerate `app/src/main/baseline-prof.txt` via `tools/capture_baseline_profile.sh` when updating ART startup profiles. Use `tools/benchmark_m120.sh` (adb-connected AT-M120) to capture comparable size/launch/frame/memory numbers before and after performance changes; `--stress-settings` (`STRESS_ROUNDS=N`) drives the Settings Hub with injected keys, checking the focused window and on-screen title (`uiautomator dump`) before every batch and aborting on the first mismatch, so keys never leak to Home/Drawer.
- **Settings screens (`ui/screens/settings/`):** Never key a `LaunchedEffect` / `remember` on the focus index in a screen body (it recomposes the whole screen per D-pad step); use `ScrollToFocusedItem` (snapshotFlow) and read focus only inside card/row scopes. Keep binder / settings-provider probes (PackageManager, `Settings.*`, AppOps, IMM, ActivityManager) off the first frame: seed from last-known values and refresh on `Dispatchers.IO` (see `PermissionsRootSettingsScreen`, `MochibunImeStatus.lastKnownState`).
- **Signing & F-Droid:** Both `debug` and `release` builds sign with the `release` config from `keystore.properties` / `mochibun-release.jks` (`dependenciesInfo` disabled in APK/AAB for F-Droid / IzzyOnDroid). Never print, log, commit, or modify signing credentials. Fastlane store metadata lives in `fastlane/metadata/android/en-US/`.
