# M120 Launcher Project Rules & Architecture

## Project
This is a lightweight custom Android launcher for the ALT Mive Style Folder (Chinoe AT-M120).

- **Package:** `one.crinklecod.mochibun`
- **Target device:** Android 12 (API 31), MediaTek MT6761 quad-core 32-bit ARMv7 (`armeabi-v7a`), ~1.8–2 GB RAM, 480 × 800 portrait display.
- **Root:** Optional via `libsu` (`core/system/RootShell`, `RootTouchInjector`, `core/shell/RootOperations`). Must function cleanly unrooted.
- **LSPosed / Xposed:** System hooks via LSPosed:
  - `system_server` (`xposed/MochibunKeyHook.kt`): intercepts hardware keys and drives cursor/remapping broadcasts without accessibility service memory overhead.
  - `com.android.systemui` (`xposed/MochibunSystemUiHook.kt`): customizes status bar (cellular signal & carrier text suppression, icon tints), battery (4-heart battery display in `BatteryMeterView`), and volume dialog (squircle pastel container, single active slider, compact 480 × 800 sizing in `VolumeDialogImpl`).

## Development Priorities

1. Keep the launcher lightweight and responsive.
2. Preserve existing behavior unless explicitly asked to change it.
3. Prefer small, targeted changes over large rewrites.
4. Avoid unnecessary dependencies (keep runtime deps limited to Compose/Material3, Coil 2 + gif, libsu, kaml, kotlinx-serialization).
5. Avoid WebViews unless explicitly requested.
6. Avoid unnecessary animations, background work, polling, and allocations.
7. Do not add features unless explicitly requested.
8. Do not replace working Compose architecture just for stylistic reasons.
9. Do not redesign the UI unless explicitly requested.

## UI & Styling

The launcher uses:
- Jetpack Compose with Material 3.
- Pastel pink and lavender styling with kawaii visual elements.
- 480 × 800 portrait layout.
- **Compose styling constraints:**
  - No runtime blur shaders. `Modifier.blur()` is strictly prohibited.
  - Sample bitmaps with nearest-neighbor filtering (`FilterQuality.None`).
  - D-pad & keypad: Every interactable element must keep an explicit focusable contract. Soft glow focus halos must not be clipped (`clipChildren = false`).

- **Keitai lace frame (`ui/components/KeitaiLaceContainer.kt`):** the shared "two-tone organza" frame: a translucent scalloped ruffle (`containerColor` at 54% with a 0.8dp rim at 28%), ivory pearls piped along the seam, and an opaque rounded card (solid `containerColor`, or `containerBrush` for gradient pills) with a 1.35dp outline. Ruffle and pearls follow the rounded card via `RoundRectWalker` (tested in `RoundRectWalkerTest`); all geometry is built in `drawWithCache`, so drawing allocates nothing. Content gets a Surface-equivalent `LocalContentColor`.
  - Used by every dialog, plus the Home Welcome banner, Favorites card, Widgets/Notifications page headers, system stats card and photo frame.
  - Home cards / headers use the dainty sizing `scallopDepth = 3.dp`, `beadDiameter = 4.dp`, `contentPadding = 6.dp` (inner content box carries the click + ripple, clipped to the card shape) and `beadBorderColor = accentDeep @ 45%`. Dialogs use the default (chunkier) sizing; dialogs converted from the old plain surface pass `contentPadding = 14.dp` (just clears the pearl ring).
- **Single-dismiss dialogs:** dialogs whose only bottom button is close / cancel use `KeitaiLaceDialogWithClose` (`ui/dialogs/DialogComponents.kt`): the `KawaiiClosePill` floats over the bottom-centre rim (154dp wide, 2dp overlap) outside any scroll container. Dialogs with two buttons (Cancel + Create/Save/Delete) keep a normal button row.
- **Page "home" pills:** the Widgets and Notifications pages both float `KawaiiReturnHomePill` bottom-centre over the clouds (outside the scrolling list, `padding(bottom = 12.dp)`).
- **Canvas allocation rule:** cache `Stroke` / `Path` objects (`remember`, `drawWithCache`, or the shared single-slot caches in `KawaiiCardMotifs.kt`); never construct them inside per-frame draw lambdas.

Keep the existing visual style intact.

## Important Existing Functionality (Do Not Break)

The launcher currently supports:
- **Core Launcher:** HOME launcher behavior, app drawer (swipe-up or second Home press), physical Home button returning to home state; holding physical Home button (~400 ms) toggles the Quick Settings shade open or closed.
- **Favorites & App Management:** Favorites card (3-slot paginated carousel: `FAVORITES_PAGE_SIZE` = 3, the visible page is derived from `selectedFavoriteIndex` so D-pad Left/Right and reorders flip pages automatically; horizontal swipe on the card and tappable `♥ •` page dots when there are > 3 items; paging math in `favoritePageCount` / `favoritePageForIndex` / `firstFavoriteIndexOfPage`, tested in `FavoritesPagingTest`), folders (`FolderDialog`), favorite reordering, hidden apps.
- **Icon Customization:** Automatic KAWAIIICON mapping, manual KAWAIIICON selection, icon search, custom app icons, icon packs (`IconPackManager`), cached launcher icons.
- **Wallpaper:** Sampled wallpaper loading, wallpaper selection, wallpaper reset, wallpaper overlay generator.
- **Status & Widgets:** Battery/Wi-Fi/time status card, system monitor card, Now Playing card, photo frame card, AppWidget host support, StickerExplosion decorations, Mochibun mascot.
- **Physical Keypad & Navigation:**
  - T9 numeric app drawer search and speed dial.
  - Directional D-pad navigation, center/OK activation, long-press handling (~400 ms).
  - `#` key: space on tap, Kaomoji Wheel (`ui/dialogs/KaomojiWheelDialog.kt`) on hold.
  - `*` key: context actions / shift.
  - `0` key: space / symbols.
  - `F4` key: virtual cursor toggle (`VirtualCursorService` via `MochibunKeyHook` / broadcasts).
- **Built-in Keitai IME (`MochibunImeService`):**
  - System-wide multi-tap keyboard with floating candidate pill (`KeitaiCandidateStrip`), auto-scrolling candidate track, and symbol pools (`1` for conversational, `0` for programming/shell).
  - Uses a transparent, full-height IME input view while reporting full-height content/visible insets so the app underneath is not resized or panned. The IME declares an empty touchable region: screen taps pass through the pill to the foreground app; all text entry is via hardware keys.
  - Requests monitored `CursorAnchorInfo` from the active editor. When a valid visible caret anchor is available, the pill docks to the top if the caret's vertical position enters the bottom pill zone, independent of horizontal position. It returns to the bottom after the caret clears the zone with hysteresis (`DOCK_ENTER_MARGIN_DP` = 12dp, `DOCK_EXIT_MARGIN_DP` = 28dp). If the anchor is absent/invalid, the pill stays at bottom; if the top pill zone would overlap the caret, it also stays at bottom.
  - Top/bottom movement animates the pill view only; there is no polling or per-frame IME window/inset layout work. After 1,500ms without a key-down, pill alpha fades to 0.35 and restores on the next key-down. Alpha uses `graphicsLayer` with `CompositingStrategy.ModulateAlpha` to avoid an offscreen layer. Timing and alpha are named constants in `MochibunImeService`.
  - Manual candidate selection via D-pad Left/Right disables the auto-commit timer until the letter is committed or cleared; this keeps a pending multi-tap letter stable while the user is choosing it, without affecting regular number-key cycling or the existing idle behavior once selection is released.
  - Works with standard editors and TYPE_NULL terminals (Termux). Coordinated via `InputArbiter`.
- **Clamshell & Hardware Management:**
  - `ClamshellManager` and `HallSwitchReader` (detects lid open/close via `/dev/input/event0`).
  - `LidLedManager` (controls external clamshell notification LED).
  - **Lid LED Studio** (root-only row `led_studio` in Settings → Behavior / "Layout & Features"; hub destination `SettingsDestination.LidLedStudio`):
    - Files: `data/model/LidLedAnimation.kt` (`LidLedSwatch` ×7, `LidLedStep`, `LidLedAnimation`, lenient `LidLedAnimationCodec`: JSON if text starts with `{`, else YAML; hand-written YAML encoder), `data/repository/LidLedAnimationRepository.kt` (5 built-ins `builtin_*`, custom files `filesDir/lid_animations/<id>.yaml`, import/export, per-slot cache), `ui/screens/settings/LidLedStudioScreen.kt` (library + 5-window step maker), test `LidLedAnimationTest.kt`.
    - Schema: `id`, `name`, `author`, `step_ms` (JSON also `stepMs`; 100–5000), `loop`, `steps` (1–16) × `windows` (5 hex, W1..W5, `#000000` = off). Sanitized on load.
    - Slots: `LidAnimationSlot.NOTIFICATION` / `CHARGING` → `PreferencesManager.KEY_LID_LED_NOTIFICATION_ANIMATION_ID` (`pref_lid_led_animation_id`, pre-slot key kept) / `KEY_LID_LED_CHARGING_ANIMATION_ID`. Blank id = classic behavior (shimmer / solid charging). Priority ladder unchanged (CALL > NOTIFICATION > CHARGING > OFF).
    - Playback (`LidLedManager.buildStepAnimationCommand`): `lidled off` (chip reset `0x01 0x55` clears on-chip stock programs, e.g. `aw22xxx_cfg_led_breath.bin` loaded at plug-in) then one `lidled frames <step_ms> <loops> <last|off> <mask:hex>...` daemon (`lidled.c`: single process, absolute-deadline step timer, reuses `write_channel_pattern()` / `write_off()` unchanged). One color per step (most common lit color). Notification/charging loop ~60 s then hold the last ("Resting") frame; charging/notification-while-plugged prefix `buildSuppressStockChargeCommand()`. Any later `lidled` call stops the daemon.
    - Never drive animations with per-step `lidled pattern` calls or shell loops, and never alter the AW22xxx register sequence: re-spawning the helper per step drops frames / flickers on the AT-M120 (verified on device).
    - Test Lid: `playStepAnimation()` plays ~5 s immediately (windows work with the flip open); `updateLedState()` is a no-op until it ends; `stopLeds()` still cancels.
    - Only `.mochibunled` opens via ACTION_VIEW (manifest `pathPattern` filter; deliberately not `.yaml`/`.yml`, which would hijack every YAML file and route theme YAMLs into the animation importer). It reaches `MainActivity.pendingThemeBundleUri`; `MainLauncher` routes it by display name to `LidLedAnimationRepository.importFromUri` instead of the `.mochibun` bundle preview. Exports are YAML content named `*.mochibunled`; `.yaml` / `.json` import only via the Studio's Import button.
    - The app prefers `/data/local/tmp/lidled` (dev copy) over the APK-bundled helper; keep it in sync after native changes.
  - `HardwareRemapManager` (pure repository/manager for physical function key remapping and action dispatch).
  - `MochibunKeyHook` (LSPosed framework hook inside `system_server` for low-level hardware key interception and OS event cancellation).
- **Virtual Cursor (`VirtualCursorService`):**
  - Standard Android foreground `Service` using `TYPE_APPLICATION_OVERLAY` with continuous kinetic physics engine (`CursorPhysics`).
  - Receives explicit broadcasts from `MochibunKeyHook` (`ACTION_TOGGLE_CURSOR`, `ACTION_CURSOR_KEY`) to pause/resume `Choreographer.FrameCallback` so idle CPU draw drops to 0%.
  - Fast touch/key/paste via native root evdev daemon (`app/src/main/cpp/evinject/evinject.c` -> `libevinject.so`), streaming 24-byte packets over anonymous pipe stdin (`RootTouchInjector.kt`). Bypasses `app_process` / `input tap` JVM startup latency (< 1 ms vs ~470 ms), automatically dropping back to legacy `input` commands if root is ungranted or helper is unavailable.
- **Themes & Backup:**
  - YAML themes parsed via kaml from `/sdcard/Mochibun/themes/` (`YamlTheme`, `ExternalThemeManager`).
  - `.mochibun` theme bundle import/export (`ThemeBundleManager`).
  - JSON backup/restore (`BackupRestoreManager`).
- **SystemUI Customizations (`MochibunSystemUiHook` via LSPosed in `com.android.systemui`):**
  - **Status bar cellular suppression:** Cellular signal strength, data type indicators, and no-SIM badges hidden from status bar while preserving Wi-Fi (`StatusBarIconControllerImpl`, `StatusBarSignalPolicy`, `StatusBarMobileView`).
  - **Carrier text suppression:** Suppressed across lockscreen (`CarrierText`, `CarrierTextManager`) and status bar (`OperatorNameView`).
  - **Status bar icon tinting:** `DarkIconDispatcherImpl` single-tone colors overridden to deep plum (`#4A2E56`) in dark-icon mode and pale pink (`#FFE4F0`) in light-icon mode.
  - **4-heart battery display:** In `BatteryMeterView`, forces percent text on, hides stock battery meter icon, renders 4 hearts (`♥♥♥♥` down to `♡♡♡♡`) with ` ✦` charging star, and rose (`#F06292`) tint at <=15% when discharging. Tested via `BatteryHeartsTest`.
  - **Squircle volume dialog:** In `VolumeDialogImpl`, applies pastel squircle (`#F6F0FC` fill, 20dp corner radius, 1.5dp `#4A2E56` 40% stroke) to content views; tints sliders (`#FFB7D5` progress, `#EADDF8` track, `#4A2E56` icons); enforces single active slider via `shouldBeVisibleH` and `mShowActiveStreamOnly`; suppresses stock charcoal row capsule; collapses ringer drawer options when closed (`View.GONE`) to remove empty void and scales slider height to 128dp for compact 480 × 800 layout.
  - **Kawaii Quick Settings shade:** Scrim & blur suppression (`BlurUtils`, `NotificationShadeDepthController`, `ScrimController`) enforcing soft pastel backdrop (`#F6F0FC` at ~92% alpha, 0px GPU runtime blur); 14dp squircle tiles (`QSTileViewImpl`, `QSIconViewImpl`) with 1.2dp border in Active (`#FFB7D5` fill, `#4A2E56` label), Inactive (`#FAF4FE` fill, `#4A2E56` label), and Unavailable (`#F0E8F8` fill, `#4A2E56` 38% label) states; and brightness slider pastel tinting (`BrightnessSliderView`, `BrightnessSlider` with `#FFB7D5` progress, `#EADDF8` track, `#4A2E56` icon). Tested via `QuickSettingsPaletteTest`.
  - **Power Menu, SystemUI dialogs & screenshot shelf (Stage H):** 22dp `container` squircles with 1.5dp `accentDeep` 30% stroke behind the Power Menu grid (`GlobalActionsDialogLite$ActionsDialogLite.initializeLayout`, `@android:id/list`), its power-options sub-dialog (`GlobalActionsPowerDialog.create`), every stock-layout `AlertDialog` / `SystemUIDialog` window (`Dialog.onStart`, original window insets preserved; custom-layout dialogs such as `MediaOutputBaseDialog` are skipped), and the screenshot actions shelf / preview border (`ScreenshotView.onFinishInflate`). Power Menu tiles (`MyAdapter` / `MyPowerOptionsAdapter.getView`), dialog buttons and screenshot chips (`ScreenshotActionChip.onFinishInflate`) use 16dp focus-aware squircles: `primary` fill + crisp 2dp `accentDeep` ring when D-pad focused or pressed. Emergency tiles keep their stock red inks. Tested via `SystemDialogPaletteTest`.

## Performance & Memory

The target device is strictly low-RAM (~2 GB total).
Current launcher memory usage is approximately:
- ~162 MB PSS
- ~99 MB RSS

Graphics/icon memory is more significant than the Kotlin/Java heap.

Do not add:
- `System.gc()`
- RAM cleaners or aggressive cache clearing
- Unnecessary bitmap duplication
- Unnecessary WebViews
- Unnecessary background services or polling daemons

Do not optimize based solely on theoretical concerns. Prefer measurable improvements.

## Architecture

- **Single activity, mostly singletons:** No DI framework. Cross-cutting state lives in Kotlin `object` singletons initialized in `MochibunApp.onCreate()` (`PreferencesManager`, `ClamshellManager`, `LidLedManager`, `SoundEffectManager`, `ExternalThemeManager`, `TypographyManager`, `CursorServiceWatchdog`).
- **Coil Image Loader:** Use the app-wide bounded memory loader from `MochibunApp` rather than creating new image loaders.
- **Event flow:** `ui/MainActivity.kt` is the HOME activity. It communicates state changes to Compose (`MainLauncher.kt`) via observed counters (`homePressCount`, `appListRefreshCount`, `lidOpenCount`, `unlockCount`, `requestedPage`).
- **Key Interceptors:** Physical key events flow from `MainActivity.dispatchKeyEvent` to `MainActivity.keyEventInterceptor`. `MainLauncher` installs `LauncherKeyHandler` (`ui/LauncherKeyHandler.kt`). Dialogs and sub-screens swap interceptors in and out as needed. Low-level global hardware keys are captured via `MochibunKeyHook` in `system_server`.
- **UI Tree:** `ui/MainLauncher.kt` owns the 3-page `HorizontalPager` (0 = Widgets, 1 = Home, 2 = Notifications) and modal states. Sub-screens live in `ui/screens/` and dialogs live in `ui/dialogs/`.
- **Services (`service/`):**
  - `MochibunImeService`: System-wide Keitai multi-tap keyboard.
  - `InputArbiter`: Coordinates keypad routing between active IME text views and launcher search.
  - Global Kaomoji Wheel: hosted inside `MochibunImeService` (`#` hold while typing in any app); the chosen kaomoji is committed through the IME's `InputConnection`.
  - `VirtualCursorService`: Standard foreground service running `CursorOverlayView` (`TYPE_APPLICATION_OVERLAY`) driven by `CursorPhysics` and root touch dispatch.
  - `MochibunNotificationListenerService`: Feeds `NotificationTracker` and `MediaSessionTracker`.
- **System Hook (`xposed/`):**
  - `MochibunKeyHook`: Hooks `PhoneWindowManager.interceptKeyBeforeQueueing` in `system_server` via LSPosed. Intercepts physical side keys (`F4` / scan code 62) for virtual cursor toggle, directional D-pad navigation during cursor mode, physical function keys (`F1`..`F3`, `CAMERA`) forwarding to `HardwareRemapManager` (`ACTION_HARDWARE_KEY_TRIGGER`), suppresses OS Recents/Back on Home/Drawer (`ACTION_LSK_TRIGGER` and `ACTION_BACK_TRIGGER`; gated by the Soft-Key Navigation pref, mirrored to `Settings.Global` `mochibun_soft_key_nav_enabled` and read with a 2 s cache), and intercepts `KEYCODE_HOME` (scan codes 172 & 102) when Mochibun or the notification shade is active (`ACTION_HOME_TRIGGER`), tracking press state (`isHomePressedOnMochibun`) and swallowing `ACTION_UP` so `PhoneWindowManager` never collapses system panels or triggers `closeSystemDialogs()` on Home button release.
  - `MochibunSystemUiHook`: Hooks `com.android.systemui` via LSPosed. Targets status bar cellular icons, carrier text suppression, dark icon dispatcher tints, 4-heart battery meter formatting, squircle single-slider volume dialog, and kawaii Quick Settings shade styling (zero GPU blurs, 14dp squircle tiles with 3-state pastel styling, and brightness slider tints).

## Build & Verification

- **Build constraints:** `minSdk = targetSdk = 31` (Android 12 only). `abiFilters` is locked to `armeabi-v7a`. AGP 9 + Kotlin 2.2 with Compose compiler plugin + CMake 3.22 for `evinject`.
- **Verification rule:** After code or resource changes, always compile and run tests to verify:
  ```bash
  ./gradlew app:compileDebugKotlin       # Fast Kotlin compile verification
  ./gradlew app:testDebugUnitTest        # JVM unit tests (app/src/test)
  ./gradlew app:assembleDebug            # Debug APK build
  ```
- Both debug and release builds sign with the `release` config from `keystore.properties` / `mochibun-release.jks`. Never print, commit, or alter signing credentials.

## Coding Style & Git Changes

- Keep code understandable to a beginner Android developer.
- Keep changes small, targeted, and reviewable.
- Explain what changed and why it helps; show the relevant diff or edit.
- Do not silently rewrite unrelated code.
- Do not delete files, remove features, or perform broad refactors without explicit permission.
