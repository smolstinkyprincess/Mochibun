# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Read `AGENTS.md` first.** It holds the project rules: performance limits, which features must not break, the no-large-refactors rule, and the requirement to build after changes. Everything below assumes you know those rules.

`PROJECT_MEMORY.md` is **stale**. It describes an older "M120Launcher" with an offline llama.cpp chat, an old package (`com.tnyemii.m120launcher`), and Windows paths. None of that (no `cpp/`, `jniLibs/`, NativeLlm, or web search) exists in the current tree. Don't trust its file paths.

## Permanent invariants

- **Target device:** ALT Mive Style Folder AT-M120: Android 12, MediaTek MT6761 quad-core 32-bit ARMv7, ~1.8 GB RAM, 480 × 800 display.
- **Compose:** No runtime blur shaders. `Modifier.blur()` is strictly prohibited. Sample bitmaps with nearest-neighbor filtering (`FilterQuality.None`).
- **D-pad & keypad:** Every interactable element must keep an explicit focusable contract. Its soft glow focus halo must not be clipped (`clipChildren = false`).
- **Verification:** After any Kotlin change, always run `./gradlew compileDebugKotlin` and confirm it finishes with 0 errors.

## Commands

```bash
./gradlew app:assembleDebug            # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease              # minified + resource-shrunk release APK
./gradlew app:testDebugUnitTest        # all JVM unit tests (app/src/test)
./gradlew app:testDebugUnitTest --tests "com.crinklecodone.mochibun.CursorPhysicsTest"   # single test class
./gradlew app:lintDebug                # lint (configured with abortOnError = false)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Both debug and release builds sign with the `release` config from `keystore.properties` / `mochibun-release.jks`. Never print, commit, or change those secrets.
- Unit tests run with `unitTests.isReturnDefaultValues = true`, so Android framework calls return default values instead of throwing. Tests cover pure logic in managers (cursor physics, remaps, theme bundles, backup/restore, and so on). There are no meaningful UI tests.

## Build constraints

- `minSdk = targetSdk = 31` (Android 12 only). `abiFilters` is limited to `armeabi-v7a`. The project uses AGP 9 and Kotlin 2.2 with the Compose compiler plugin and kotlinx.serialization. Versions live in `gradle/libs.versions.toml`.
- Runtime dependencies are deliberately few: Compose/Material3, Coil 2 (+gif), libsu (root shell), kaml (YAML themes), and kotlinx-serialization-json. Don't add more without asking (see AGENTS.md).
- `buildFeatures.aidl = true` exists only for `app/src/main/aidl/com/android/systemui/shared/recents/IOverviewProxy.aidl`.

## Architecture

**Single activity, mostly singletons.** There is no DI framework. Cross-cutting state lives in Kotlin `object` singletons that `MochibunApp.onCreate()` initializes: `PreferencesManager` (one SharedPreferences wrapper behind all settings), `ClamshellManager`, `LidLedManager`, `SoundEffectManager`, `ExternalThemeManager`, `TypographyManager`, and `RootAccessibilityWatchdog`. `MochibunApp` also provides the app-wide Coil `ImageLoader`, which has a bounded memory cache. Use that loader rather than creating another one.

**Event flow from activity to Compose.** `ui/MainActivity` is the HOME activity. It doesn't call into Compose directly. It bumps counters or nullable state that `MainLauncher` observes:
- `homePressCount`: the physical Home button was pressed, so return to the home state.
- `appListRefreshCount`: a package was added, removed, or replaced, which also clears the `IconPackManager` cache.
- `lidOpenCount` / `lidClosedCount`: flip-lid events.
- `unlockCount`: the keyguard was dismissed (`ACTION_USER_PRESENT`), so close the drawer. A Home intent while the keyguard is up or within ~1 s of a wake/unlock never counts as the "second press on Home" that opens the drawer.
- `requestedPage` (from the `OPEN_PAGE` extra) and `pendingThemeBundleUri` (from opening a `.mochibun` file). The consumer callbacks reset these.
- In `onNewIntent`, a HOME intent with `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED` and without `TASK_ON_HOME` is SystemUI asking for Recents. That intent is forwarded to `com.android.quickstep.RecentsActivity`.

**Key handling.** Physical keys drive the whole UI. `MainActivity.dispatchKeyEvent` first offers each event to the static `MainActivity.keyEventInterceptor`. `MainLauncher` installs `LauncherKeyHandler` there (`ui/LauncherKeyHandler.kt`), and dialogs and screens can swap the interceptor in and out. That handler covers D-pad focus, T9 search, long-press timing (~400 ms), `#` (settings / kaomoji wheel), `*`, `0`, speed dial, and F4 (virtual cursor). When you add a screen or dialog that needs keys, follow the existing interceptor pattern. The key map in README.md is the user-facing spec.

Accessibility key filters (`MochibunAccessibilityService`, `VirtualCursorService`) only ever see a press's DOWN (`repeatCount == 0`) and its UP; this was verified on the device. Android synthesizes auto-repeats after the filter and sends them straight to the focused window. So a hold detected in a service, or through `launcherKeyListener`, must use a timer, not repeats. A DOWN the filter lets through will auto-repeat into the app no matter what the filter does later. That is why the global `#` hold holds the DOWN back and replays a tap with root `input keyevent`. Injected events like that one skip the accessibility filters.

**UI composition.** `ui/MainLauncher.kt` is the root composable. It owns a `HorizontalPager` (page 0 = Widgets, 1 = Home, 2 = Notifications; `requestedPage` is clamped to 0..2). It also owns the app-drawer overlay (opened by swiping up) and the flags for which modals are open. `LauncherViewModel` holds app-list, favorites/folders, hidden-app, wallpaper, and KAWAIIICON state as Compose `mutableStateOf`, persisted through `PreferencesManager`. Screens are in `ui/screens` (settings sub-screens are in `ui/screens/settings/`). Dialogs are in `ui/dialogs`, reusable pieces and decorations (stickers, mascot, card motifs, widgets) in `ui/components`, and colors, theme, and typography in `ui/theme`.

**Services (`service/`).**
- `MochibunAccessibilityService`: global button remapping (`flagRequestFilterKeyEvents`). It reads `isLauncherForeground`, which MainActivity sets.
- `VirtualCursorService`: accessibility overlay cursor (`ui/overlay/CursorOverlayView`, physics in `core/util/CursorPhysics`).
- `MochibunNotificationListenerService`: feeds `NotificationTracker` and `MediaSessionTracker` for the Notifications page and the Now Playing card.

**Root features (optional).** Root goes through libsu (`core/system/RootShell`, `RootTouchInjector`, `core/shell/RootOperations`). The su process starts lazily, and the code must work when root isn't available. `com.android.quickstep.*` is a small custom Recents/overview implementation (QuickSwitch provider support) and is separate from the main package on purpose.

**Data formats.** YAML themes are parsed with kaml from `/sdcard/Mochibun/themes/` (`data/model/YamlTheme`, `ExternalThemeManager`). `.mochibun` bundles are zip-style theme packs handled by `ThemeBundleManager`. JSON backups are handled by `BackupRestoreManager`.

## Other directories

- `tools/`: standalone desktop Python utilities (wallpaper overlay generator, boot animation builder). They aren't part of the Gradle build.
- `fastlane/metadata`: store listing and screenshots used by the README. `docs/` is a static project page.
