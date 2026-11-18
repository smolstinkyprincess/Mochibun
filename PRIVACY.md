# Privacy & Permissions

Mochibun is designed to be **100% offline** with zero telemetry, zero analytics, and zero ads.

## Network & Data Collection
- **No `INTERNET` permission:** Mochibun does not declare `android.permission.INTERNET`. Every GitHub Actions CI run verifies the **merged release manifest** (`processReleaseManifest`) to ensure no dependency merges `INTERNET` or `CAMERA` either.
- **No `CAMERA` permission:** Flashlight control uses `FLASHLIGHT` / camera-torch APIs only where permitted without camera capture permissions.
- **No cloud backups (`allowBackup="false"`):** Android's `adb backup` / cloud app-data backup is disabled in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml). Instead, Mochibun provides explicit local export/import (`.json` backups, `.mochibundle` theme packs, and `.mochibunled` animations) that stay strictly under your control.
- **Local storage only:** Preferences, pinned favorites, custom mascots, themes, and Mochibunny pocket-pet state live in app-private storage on your device.

---

## Optional Sensitive Helpers

All elevated or sensitive capabilities are **opt-in** and run strictly on-device:

1. **Mochibun Accessibility Service (`MochibunAccessibilityService`)**
   - **Purpose:** Only used on unrooted phones (if enabled in *Android Settings > Accessibility*) to inject virtual mouse taps/scrolls, toggle the cursor via the side key (`F4`), and trigger global navigation actions (Recents, Notifications, Quick Settings, Back).
   - **Never reads screen content:** [`accessibility_service_config.xml`](app/src/main/res/xml/accessibility_service_config.xml) explicitly sets `android:canRetrieveWindowContent="false"` and requests zero `accessibilityEventTypes`; [`onAccessibilityEvent`](app/src/main/java/one/crinklecod/mochibun/service/MochibunAccessibilityService.kt) is a no-op (`= Unit`).
   - Automatically steps aside when the optional LSPosed hook is active.

2. **Mochibun Keitai IME (`MochibunImeService`)**
   - **Purpose:** Multi-tap T9 hardware-keypad keyboard and Kaomoji Wheel text input.
   - **Privacy:** Keystrokes are processed purely in memory to compose text into the active editor. Nothing typed is ever logged to disk or transmitted.

3. **Notification Access (`MochibunNotificationListenerService`)**
   - **Purpose:** Powers the in-launcher Notifications page and Now Playing media controls.
   - **Privacy:** Active notifications and media metadata are read in memory for display while the launcher runs and are never persisted or shared.

---

## Manifest Permissions Explained

| Permission | Why it is declared |
| :--- | :--- |
| `QUERY_ALL_PACKAGES` | Required by Android home launchers to list installed apps, populate the drawer/favorites/speed dial, and discover icon packs. |
| `SYSTEM_ALERT_WINDOW` | Draws the floating virtual mouse pointer over other apps when toggled on. |
| `FOREGROUND_SERVICE` | Runs `VirtualCursorService` (`specialUse`) only while the virtual cursor is visible on screen. |
| `ACTIVITY_RECOGNITION` | Optional, **never prompted at runtime**. Only reads today's `TYPE_STEP_COUNTER` total if you manually grant it in Android App Info to feed Mochibunny's step treats and sub-display ticker. |
| `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE` | Read-only local status indicators for the Home status card and Quick Settings (no network I/O). |
| `VIBRATE`, `WAKE_LOCK`, `FLASHLIGHT`, `MODIFY_AUDIO_SETTINGS`, `REORDER_TASKS`, `KILL_BACKGROUND_PROCESSES`, `EXPAND_STATUS_BAR` | Keypad haptics, screen/lid handling, flashlight (`0` hold), Media Volume Lock, Recents task switching, and Quick Settings shade. |
| `STATUS_BAR_SERVICE`, `STOP_APP_SWITCHES`, `MEDIA_CONTENT_CONTROL`, `WRITE_SECURE_SETTINGS` | Protected permissions only active if granted by a rooted user (e.g., via QuickSwitch); ignored harmlessly on stock phones. |
| `one.crinklecod.mochibun.permission.CURSOR_MODE_SYNC` (`signature`) | Internal signature-protected permission synchronizing cursor toggle state between the launcher and system hook. |
