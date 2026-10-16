package com.tnyemii.mochibun

import android.view.KeyEvent

data class AppInfo(
    val packageName: String,
    val label: String
)

/**
 * Verified physical keys on Chinoe AT-M120 (mtk-kpd and gpio-keys).
 * Only low-risk keys are enabled for remapping to prevent bricking or locking out the device.
 * Critical navigation/system keys (POWER, HOME, BACK, APP_SWITCH, ENTER, DPAD, VOLUME)
 * are deliberately omitted from remapping targets.
 */
enum class PhysicalKey(
    val scancode: Int,
    val defaultKeyCode: Int,
    val keyName: String,
    val description: String,
    val isStaticRemapSafe: Boolean = true
) {
    CALL(169, KeyEvent.KEYCODE_CALL, "Call Key", "Dedicated physical green call button", true),
    F1(59, KeyEvent.KEYCODE_F1, "Left Softkey (F1)", "Top-left physical menu softkey", true),
    F2(60, KeyEvent.KEYCODE_F2, "Right Softkey (F2)", "Top-right physical softkey", true),
    F3(61, KeyEvent.KEYCODE_F3, "Center Softkey (F3)", "Center physical function key", true),
    F4(62, KeyEvent.KEYCODE_F4, "Side Key (F4)", "Physical side button (gpio-keys)", false),
    CAMERA(212, KeyEvent.KEYCODE_CAMERA, "Camera Key", "Dedicated camera shutter key", true),
    STAR(522, KeyEvent.KEYCODE_STAR, "Star Key (*)", "Numeric keypad star key", true),
    POUND(523, KeyEvent.KEYCODE_POUND, "Pound Key (#)", "Numeric keypad pound key", true);

    companion object {
        fun fromKeyCode(keyCode: Int): PhysicalKey? = entries.firstOrNull { it.defaultKeyCode == keyCode }
        fun fromScancode(scancode: Int): PhysicalKey? = entries.firstOrNull { it.scancode == scancode }
    }
}

/**
 * High-level system-wide actions handled dynamically via MochibunAccessibilityService.
 * Requires NO reboot and takes effect in real-time across all apps.
 */
sealed class ActionType {
    object None : ActionType()
    data class LaunchApp(val packageName: String, val appLabel: String) : ActionType()
    object OpenAiChat : ActionType()
    object GlobalBack : ActionType()
    object GlobalRecents : ActionType()
    object GlobalNotifications : ActionType()
    object ToggleFlashlight : ActionType()

    fun displayName(): String = when (this) {
        is None -> "None (Default)"
        is LaunchApp -> "Launch $appLabel"
        is OpenAiChat -> "Open Mochibun AI Chat"
        is GlobalBack -> "Global Back"
        is GlobalRecents -> "Overview / Recents"
        is GlobalNotifications -> "Open Notifications"
        is ToggleFlashlight -> "Toggle Flashlight"
    }

    fun serialize(): String = when (this) {
        is None -> "NONE"
        is LaunchApp -> "LAUNCH_APP:$packageName:$appLabel"
        is OpenAiChat -> "OPEN_AI_CHAT"
        is GlobalBack -> "GLOBAL_BACK"
        is GlobalRecents -> "GLOBAL_RECENTS"
        is GlobalNotifications -> "GLOBAL_NOTIFICATIONS"
        is ToggleFlashlight -> "TOGGLE_FLASHLIGHT"
    }

    companion object {
        fun deserialize(raw: String?): ActionType {
            if (raw.isNullOrBlank() || raw == "NONE") return None
            if (raw.startsWith("LAUNCH_APP:")) {
                val parts = raw.split(":", limit = 3)
                if (parts.size >= 2) {
                    val pkg = parts[1]
                    val label = if (parts.size >= 3) parts[2] else pkg
                    return LaunchApp(pkg, label)
                }
            }
            return when (raw) {
                "OPEN_AI_CHAT" -> OpenAiChat
                "GLOBAL_BACK" -> GlobalBack
                "GLOBAL_RECENTS" -> GlobalRecents
                "GLOBAL_NOTIFICATIONS" -> GlobalNotifications
                "TOGGLE_FLASHLIGHT" -> ToggleFlashlight
                else -> None
            }
        }
    }
}

data class ButtonActionConfig(
    val singlePressAction: ActionType = ActionType.None,
    val doublePressAction: ActionType = ActionType.None,
    val longPressAction: ActionType = ActionType.None
) {
    fun hasAnyAction(): Boolean =
        singlePressAction !is ActionType.None ||
        doublePressAction !is ActionType.None ||
        longPressAction !is ActionType.None
}

/**
 * State reconciliation for the device-specific Magisk overlay module.
 */
sealed class StaticModuleStatus {
    object NotInstalled : StaticModuleStatus()
    object Disabled : StaticModuleStatus()
    data class StagedPendingReboot(val activeTargetKeyCodes: Map<Int, String>) : StaticModuleStatus()
    data class Active(val activeTargetKeyCodes: Map<Int, String>) : StaticModuleStatus()
    data class Error(val message: String) : StaticModuleStatus()

    fun description(): String = when (this) {
        is NotInstalled -> "Not installed (Default Generic.kl active)"
        is Disabled -> "Disabled via Magisk /disable flag (Defaults active)"
        is StagedPendingReboot -> "Staged in Magisk module (Reboot required to mount)"
        is Active -> "Active and mounted system-wide"
        is Error -> "Error: $message"
    }
}

/**
 * Controlled list of valid Android KeyCode names safe for static remapping.
 */
val CONTROLLED_STATIC_KEYCODES = listOf(
    "CALL",
    "VOLUME_MUTE",
    "CAMERA",
    "ASSISTANT",
    "VOICE_ASSIST",
    "TAB",
    "ESCAPE",
    "DEL",
    "FORWARD_DEL",
    "SEARCH",
    "MEDIA_PLAY_PAUSE",
    "MEDIA_NEXT",
    "MEDIA_PREVIOUS",
    "NOTIFICATION",
    "EXPLORER",
    "CALCULATOR",
    "CALENDAR",
    "MUSIC",
    "BOOKMARK",
    "F1",
    "F2",
    "F3",
    "F4"
)

/**
 * Baseline original scancode mappings for mtk-kpd (Vendor 2454 Product 6500).
 * All 28 physical keys are preserved to ensure no keys lose functionality.
 */
val BASELINE_MTK_KPD_MAPPINGS = mapOf(
    2 to "1",
    3 to "2",
    4 to "3",
    5 to "4",
    6 to "5",
    7 to "6",
    8 to "7",
    9 to "8",
    10 to "9",
    11 to "0",
    14 to "DEL",
    28 to "ENTER",
    59 to "F1",
    60 to "F2",
    61 to "F3",
    103 to "DPAD_UP",
    105 to "DPAD_LEFT",
    106 to "DPAD_RIGHT",
    108 to "DPAD_DOWN",
    142 to "SLEEP",
    143 to "WAKEUP",
    158 to "BACK",
    169 to "CALL",
    172 to "HOME",
    212 to "CAMERA",
    522 to "STAR",
    523 to "POUND",
    580 to "APP_SWITCH"
)
