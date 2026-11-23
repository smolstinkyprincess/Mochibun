package one.crinklecod.mochibun.ui.dialogs

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import one.crinklecod.mochibun.core.haptic.HapticManager
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.model.ActionType
import one.crinklecod.mochibun.data.model.AppInfo
import one.crinklecod.mochibun.data.model.ButtonActionConfig
import one.crinklecod.mochibun.data.repository.HardwareRemapManager
import one.crinklecod.mochibun.ui.components.MochibunSectionHeader
import one.crinklecod.mochibun.ui.components.softGlowFocus
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.ColorContrast
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.cardBorder
import one.crinklecod.mochibun.ui.theme.destructiveBgColor
import one.crinklecod.mochibun.ui.theme.destructiveColor
import one.crinklecod.mochibun.ui.theme.palette
import one.crinklecod.mochibun.ui.theme.surface
import one.crinklecod.mochibun.ui.theme.textPrimary
import one.crinklecod.mochibun.ui.theme.unfocusedCardBgColor

enum class KeyGuideTab(val label: String) {
    LAUNCHER("♥ Launcher"),
    TYPING("✎ Typing"),
    REMAP("✦ Remap")
}

internal enum class KeyGuideZone {
    TABS,
    KEYPAD,
    REMAP_ACTIONS,
    CLOSE
}

internal enum class RemapSlot {
    SINGLE,
    LONG
}

private enum class RemapPickerType {
    APP,
    SYSTEM
}

internal enum class DpadDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

internal enum class KeyGuideKey(
    val displayName: String,
    val launcherTap: String,
    val launcherHold: String,
    val typingTap: String,
    val typingHold: String
) {
    LSK(
        displayName = "LSK (□)",
        launcherTap = "Jump left to Widgets · App Options in Drawer",
        launcherHold = "Open Recents task switcher",
        typingTap = "Left soft key / App menu",
        typingHold = "Open Recents task switcher"
    ),
    HOME(
        displayName = "HOME (○)",
        launcherTap = "Return Home · Open App Drawer when on Home",
        launcherHold = "Toggle Quick Settings shade",
        typingTap = "Return to Home screen",
        typingHold = "Toggle Quick Settings shade"
    ),
    RSK(
        displayName = "RSK (◁)",
        launcherTap = "Jump right to Notifications · Cycle Sort in Drawer",
        launcherHold = "Return Home from side pages",
        typingTap = "Cancel T9 / pending letter · Back",
        typingHold = "Standard Back navigation"
    ),
    CONTACTS(
        displayName = "Contacts",
        launcherTap = "Launch Contacts (or custom Hardware Remap)",
        launcherHold = "Custom Hardware Remap hold action",
        typingTap = "Launch Contacts (or custom Hardware Remap)",
        typingHold = "Custom Hardware Remap hold action"
    ),
    MESSAGES(
        displayName = "Messages",
        launcherTap = "Launch Messages (or custom Hardware Remap)",
        launcherHold = "Custom Hardware Remap hold action",
        typingTap = "Launch Messages (or custom Hardware Remap)",
        typingHold = "Custom Hardware Remap hold action"
    ),
    CAMERA(
        displayName = "Camera",
        launcherTap = "Launch Camera (or custom Hardware Remap)",
        launcherHold = "Custom Hardware Remap hold action",
        typingTap = "Launch Camera (or custom Hardware Remap)",
        typingHold = "Custom Hardware Remap hold action"
    ),
    STAR_FN(
        displayName = "★ Shortcut",
        launcherTap = "Launch mapped ★ app (or Hardware Remap)",
        launcherHold = "Custom Hardware Remap hold action",
        typingTap = "Launch mapped ★ app (or Hardware Remap)",
        typingHold = "Custom Hardware Remap hold action"
    ),
    DPAD_RING(
        displayName = "D-Pad Ring",
        launcherTap = "Move focus across cards, apps & favorites",
        launcherHold = "Continuous scroll · Move Virtual Cursor",
        typingTap = "◀ / ▶ Pick candidate · ▲ / ▼ Commit & move",
        typingHold = "◀ / ▶ Fast-scroll candidates"
    ),
    OK(
        displayName = "OK (Center)",
        launcherTap = "Open focused app, folder or card",
        launcherHold = "Open App / Folder Options menu",
        typingTap = "Commit candidate · Send / Enter when idle",
        typingHold = "Repeat Enter"
    ),
    CALL(
        displayName = "Call",
        launcherTap = "Open Phone / Dialer (or Hardware Remap)",
        launcherHold = "Custom Hardware Remap hold action",
        typingTap = "Dial / Answer call (or Hardware Remap)",
        typingHold = "Custom Hardware Remap hold action"
    ),
    DEL(
        displayName = "DEL / Clear",
        launcherTap = "Delete / Back in search & dialogs",
        launcherHold = "Clear input",
        typingTap = "Remove T9 digit / cancel or delete character",
        typingHold = "Repeat backspace"
    ),
    END(
        displayName = "End / Power",
        launcherTap = "End call · Short press sleeps screen",
        launcherHold = "Open Power Menu",
        typingTap = "End call · Sleep screen",
        typingHold = "Open Power Menu"
    ),
    SIDE_KEY(
        displayName = "Side Key",
        launcherTap = "Toggle Virtual Mouse Cursor on / off",
        launcherHold = "—",
        typingTap = "Toggle Virtual Mouse Cursor on / off",
        typingHold = "—"
    ),
    KEY_1(
        displayName = "1 · sym",
        launcherTap = "Jump to row 1 in menus & settings",
        launcherHold = "Custom Hardware Remap action",
        typingTap = "Cycle symbols (.,?!-\"':;♡★♥♪)",
        typingHold = "Type digit 1"
    ),
    KEY_2(
        displayName = "2 · ABC",
        launcherTap = "T9 App Search (A B C) in Drawer",
        launcherHold = "Speed Dial #2 · Cursor scroll up",
        typingTap = "T9 predictive or multi-tap (a b c)",
        typingHold = "Type digit 2"
    ),
    KEY_3(
        displayName = "3 · DEF",
        launcherTap = "T9 App Search (D E F) in Drawer",
        launcherHold = "Speed Dial #3",
        typingTap = "T9 predictive or multi-tap (d e f)",
        typingHold = "Type digit 3"
    ),
    KEY_4(
        displayName = "4 · GHI",
        launcherTap = "T9 App Search (G H I) in Drawer",
        launcherHold = "Speed Dial #4",
        typingTap = "T9 predictive or multi-tap (g h i)",
        typingHold = "Type digit 4"
    ),
    KEY_5(
        displayName = "5 · JKL",
        launcherTap = "T9 App Search (J K L) in Drawer",
        launcherHold = "Speed Dial #5",
        typingTap = "T9 predictive or multi-tap (j k l)",
        typingHold = "Type digit 5"
    ),
    KEY_6(
        displayName = "6 · MNO",
        launcherTap = "T9 App Search (M N O) in Drawer",
        launcherHold = "Speed Dial #6",
        typingTap = "T9 predictive or multi-tap (m n o)",
        typingHold = "Type digit 6"
    ),
    KEY_7(
        displayName = "7 · PQRS",
        launcherTap = "T9 App Search (P Q R S) in Drawer",
        launcherHold = "Speed Dial #7",
        typingTap = "T9 predictive or multi-tap (p q r s)",
        typingHold = "Type digit 7"
    ),
    KEY_8(
        displayName = "8 · TUV",
        launcherTap = "T9 App Search (T U V) in Drawer",
        launcherHold = "Speed Dial #8 · Cursor scroll down",
        typingTap = "T9 predictive or multi-tap (t u v)",
        typingHold = "Type digit 8"
    ),
    KEY_9(
        displayName = "9 · WXYZ",
        launcherTap = "T9 App Search (W X Y Z) in Drawer",
        launcherHold = "Speed Dial #9",
        typingTap = "T9 predictive or multi-tap (w x y z)",
        typingHold = "Type digit 9"
    ),
    KEY_STAR(
        displayName = "* · shift",
        launcherTap = "Context actions · Dismiss notification",
        launcherHold = "Toggle Manner Mode (Normal / Vibrate)",
        typingTap = "Cycle mode (abc / T9 / 123)",
        typingHold = "Cycle case (Abc → ABC → abc)"
    ),
    KEY_0(
        displayName = "0 · space",
        launcherTap = "—",
        launcherHold = "Toggle Flashlight On / Off",
        typingTap = "Code symbols (* / \\ _ @ # $ …)",
        typingHold = "Type digit 0"
    ),
    KEY_POUND(
        displayName = "# · wheel",
        launcherTap = "Open Launcher Menu",
        launcherHold = "Open Quick Memo",
        typingTap = "Commit word & insert Space",
        typingHold = "Kaomoji Wheel (D-Pad mood → 1–0 pick, * = Clips)"
    );

    val remapKeyCode: Int?
        get() = when (this) {
            LSK -> KeyEvent.KEYCODE_APP_SWITCH
            CONTACTS -> KeyEvent.KEYCODE_F1
            MESSAGES -> KeyEvent.KEYCODE_F2
            CAMERA -> KeyEvent.KEYCODE_CAMERA
            STAR_FN -> KeyEvent.KEYCODE_F3
            CALL -> KeyEvent.KEYCODE_CALL
            SIDE_KEY -> KeyEvent.KEYCODE_F4
            KEY_1 -> KeyEvent.KEYCODE_1
            KEY_2 -> KeyEvent.KEYCODE_2
            KEY_3 -> KeyEvent.KEYCODE_3
            KEY_4 -> KeyEvent.KEYCODE_4
            KEY_5 -> KeyEvent.KEYCODE_5
            KEY_6 -> KeyEvent.KEYCODE_6
            KEY_7 -> KeyEvent.KEYCODE_7
            KEY_8 -> KeyEvent.KEYCODE_8
            KEY_9 -> KeyEvent.KEYCODE_9
            KEY_0 -> KeyEvent.KEYCODE_0
            KEY_STAR -> KeyEvent.KEYCODE_STAR
            KEY_POUND -> KeyEvent.KEYCODE_POUND
            HOME, RSK, DPAD_RING, OK, DEL, END -> null
        }

    val remapScanCode: Int
        get() = when (this) {
            LSK -> 580
            CONTACTS -> 59
            MESSAGES -> 60
            CAMERA -> 212
            STAR_FN -> 61
            CALL -> 169
            SIDE_KEY -> MochibunSystemActions.SIDE_KEY_SCAN_CODE
            KEY_1 -> 2
            KEY_2 -> 3
            KEY_3 -> 4
            KEY_4 -> 5
            KEY_5 -> 6
            KEY_6 -> 7
            KEY_7 -> 8
            KEY_8 -> 9
            KEY_9 -> 10
            KEY_0 -> 11
            KEY_STAR -> 522
            KEY_POUND -> 523
            HOME, RSK, DPAD_RING, OK, DEL, END -> 0
        }

    val isRemappable: Boolean
        get() = remapKeyCode != null

    val primaryRemapSlot: RemapSlot
        get() = when (this) {
            KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6, KEY_7, KEY_8, KEY_9, KEY_0 -> RemapSlot.LONG
            else -> RemapSlot.SINGLE
        }

    fun tapText(tab: KeyGuideTab): String = when (tab) {
        KeyGuideTab.LAUNCHER -> launcherTap
        KeyGuideTab.TYPING -> typingTap
        KeyGuideTab.REMAP -> if (isRemappable) launcherTap else "System / Protected — core navigation key"
    }

    fun holdText(tab: KeyGuideTab): String = when (tab) {
        KeyGuideTab.LAUNCHER -> launcherHold
        KeyGuideTab.TYPING -> typingHold
        KeyGuideTab.REMAP -> if (isRemappable) launcherHold else "Cannot be remapped"
    }
}

internal data class KeyRemapState(
    val config: ButtonActionConfig,
    val defaultConfig: ButtonActionConfig,
    val hasCustomRemap: Boolean
)

internal fun isCustomRemapped(
    config: ButtonActionConfig,
    defaultConfig: ButtonActionConfig
): Boolean = config != defaultConfig && config.hasAnyAction()

internal fun readKeyRemapSnapshot(context: Context): Map<KeyGuideKey, KeyRemapState> {
    val result = mutableMapOf<KeyGuideKey, KeyRemapState>()
    for (key in KeyGuideKey.entries) {
        val kc = key.remapKeyCode ?: continue
        val sc = key.remapScanCode
        val config = HardwareRemapManager.getButtonActionConfigForKey(context, kc, sc)
        val defaultConfig = HardwareRemapManager.getDefaultActionConfigForKey(kc, sc)
        result[key] = KeyRemapState(
            config = config,
            defaultConfig = defaultConfig,
            hasCustomRemap = isCustomRemapped(config, defaultConfig)
        )
    }
    return result
}

internal fun formatRemapCalloutLines(
    key: KeyGuideKey,
    state: KeyRemapState?
): Pair<String, String> {
    if (!key.isRemappable) {
        return "System / Protected — reserved for navigation & power" to key.launcherHold
    }
    if (state == null) {
        return key.launcherTap to key.launcherHold
    }
    val cfg = state.config
    val tapDesc = when {
        cfg.singlePressAction !is ActionType.None -> cfg.singlePressAction.displayName()
        key.primaryRemapSlot == RemapSlot.SINGLE -> "Default · ${key.launcherTap}"
        else -> key.launcherTap
    }
    val holdPart = when {
        cfg.longPressAction !is ActionType.None -> cfg.longPressAction.displayName()
        key.primaryRemapSlot == RemapSlot.LONG -> "Not set (Hold / Speed Dial)"
        else -> "None"
    }
    val holdDesc = if (cfg.doublePressAction !is ActionType.None) {
        "$holdPart · 2× ${cfg.doublePressAction.displayName()}"
    } else {
        holdPart
    }
    return tapDesc to holdDesc
}

internal fun applyPrimaryRemapAction(
    context: Context,
    key: KeyGuideKey,
    action: ActionType
) {
    val kc = key.remapKeyCode ?: return
    val sc = key.remapScanCode
    val existing = HardwareRemapManager.getButtonActionConfigForKey(context, kc, sc)
    val updated = when (key.primaryRemapSlot) {
        RemapSlot.SINGLE -> existing.copy(singlePressAction = action)
        RemapSlot.LONG -> existing.copy(longPressAction = action)
    }
    HardwareRemapManager.saveButtonActionConfigForKey(context, kc, sc, updated)
}

internal fun clearRemapForKey(
    context: Context,
    key: KeyGuideKey
) {
    val kc = key.remapKeyCode ?: return
    HardwareRemapManager.resetButtonActionConfigForKey(context, kc, key.remapScanCode)
}

internal val SYSTEM_REMAP_ACTIONS: List<ActionType> = listOf(
    ActionType.OpenWidgetsPage,
    ActionType.OpenNotificationsPage,
    ActionType.GlobalBack,
    ActionType.GlobalRecents,
    ActionType.OpenRecentsSystem,
    ActionType.GlobalNotifications,
    ActionType.ToggleFlashlight,
    ActionType.OpenQuickMemo
)

/** Result of a D-pad step inside [MochibunKeyGuideDialog]. */
internal data class KeyGuideNavState(
    val zone: KeyGuideZone,
    val key: KeyGuideKey
)

/**
 * Deterministic 2D keypad navigation across the mode tabs, AT-M120 4-zone keypad + side key,
 * remap callout action pills, and floating close pill.
 */
internal fun stepKeyGuideFocus(
    zone: KeyGuideZone,
    key: KeyGuideKey,
    direction: DpadDirection,
    isRemapActionsVisible: Boolean = false
): KeyGuideNavState {
    if (zone == KeyGuideZone.TABS) {
        return when (direction) {
            DpadDirection.DOWN -> KeyGuideNavState(KeyGuideZone.KEYPAD, key)
            DpadDirection.UP -> KeyGuideNavState(KeyGuideZone.CLOSE, key)
            DpadDirection.LEFT, DpadDirection.RIGHT -> KeyGuideNavState(KeyGuideZone.TABS, key)
        }
    }
    if (zone == KeyGuideZone.REMAP_ACTIONS) {
        return when (direction) {
            DpadDirection.UP -> KeyGuideNavState(KeyGuideZone.KEYPAD, key)
            DpadDirection.DOWN -> KeyGuideNavState(KeyGuideZone.CLOSE, key)
            DpadDirection.LEFT, DpadDirection.RIGHT -> KeyGuideNavState(KeyGuideZone.REMAP_ACTIONS, key)
        }
    }
    if (zone == KeyGuideZone.CLOSE) {
        return when (direction) {
            DpadDirection.UP -> if (isRemapActionsVisible) {
                KeyGuideNavState(KeyGuideZone.REMAP_ACTIONS, key)
            } else {
                KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_0)
            }
            DpadDirection.DOWN -> KeyGuideNavState(KeyGuideZone.TABS, key)
            DpadDirection.LEFT -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_STAR)
            DpadDirection.RIGHT -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_POUND)
        }
    }

    return when (direction) {
        DpadDirection.UP -> when (key) {
            KeyGuideKey.LSK, KeyGuideKey.HOME, KeyGuideKey.RSK ->
                KeyGuideNavState(KeyGuideZone.TABS, key)
            KeyGuideKey.CONTACTS -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.LSK)
            KeyGuideKey.DPAD_RING -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.HOME)
            KeyGuideKey.CAMERA -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.RSK)
            KeyGuideKey.SIDE_KEY -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.RSK)
            KeyGuideKey.MESSAGES -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CONTACTS)
            KeyGuideKey.OK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.DPAD_RING)
            KeyGuideKey.STAR_FN -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CAMERA)
            KeyGuideKey.CALL -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.MESSAGES)
            KeyGuideKey.DEL -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.OK)
            KeyGuideKey.END -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.STAR_FN)
            KeyGuideKey.KEY_1 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CALL)
            KeyGuideKey.KEY_2 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.DEL)
            KeyGuideKey.KEY_3 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.END)
            KeyGuideKey.KEY_4 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_1)
            KeyGuideKey.KEY_5 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_2)
            KeyGuideKey.KEY_6 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_3)
            KeyGuideKey.KEY_7 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_4)
            KeyGuideKey.KEY_8 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_5)
            KeyGuideKey.KEY_9 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_6)
            KeyGuideKey.KEY_STAR -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_7)
            KeyGuideKey.KEY_0 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_8)
            KeyGuideKey.KEY_POUND -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_9)
        }

        DpadDirection.DOWN -> when (key) {
            KeyGuideKey.LSK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CONTACTS)
            KeyGuideKey.HOME -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.DPAD_RING)
            KeyGuideKey.RSK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CAMERA)
            KeyGuideKey.CONTACTS -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.MESSAGES)
            KeyGuideKey.DPAD_RING -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.OK)
            KeyGuideKey.CAMERA -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.STAR_FN)
            KeyGuideKey.SIDE_KEY -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.END)
            KeyGuideKey.MESSAGES -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CALL)
            KeyGuideKey.OK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.DEL)
            KeyGuideKey.STAR_FN -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.END)
            KeyGuideKey.CALL -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_1)
            KeyGuideKey.DEL -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_2)
            KeyGuideKey.END -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_3)
            KeyGuideKey.KEY_1 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_4)
            KeyGuideKey.KEY_2 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_5)
            KeyGuideKey.KEY_3 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_6)
            KeyGuideKey.KEY_4 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_7)
            KeyGuideKey.KEY_5 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_8)
            KeyGuideKey.KEY_6 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_9)
            KeyGuideKey.KEY_7 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_STAR)
            KeyGuideKey.KEY_8 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_0)
            KeyGuideKey.KEY_9 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_POUND)
            KeyGuideKey.KEY_STAR, KeyGuideKey.KEY_0, KeyGuideKey.KEY_POUND ->
                if (isRemapActionsVisible) {
                    KeyGuideNavState(KeyGuideZone.REMAP_ACTIONS, key)
                } else {
                    KeyGuideNavState(KeyGuideZone.CLOSE, key)
                }
        }

        DpadDirection.LEFT -> when (key) {
            KeyGuideKey.LSK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.RSK)
            KeyGuideKey.HOME -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.LSK)
            KeyGuideKey.RSK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.HOME)
            KeyGuideKey.CONTACTS -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CAMERA)
            KeyGuideKey.DPAD_RING -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CONTACTS)
            KeyGuideKey.CAMERA -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.DPAD_RING)
            KeyGuideKey.SIDE_KEY -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CAMERA)
            KeyGuideKey.MESSAGES -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.STAR_FN)
            KeyGuideKey.OK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.MESSAGES)
            KeyGuideKey.STAR_FN -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.OK)
            KeyGuideKey.CALL -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.END)
            KeyGuideKey.DEL -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CALL)
            KeyGuideKey.END -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.DEL)
            KeyGuideKey.KEY_1 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_3)
            KeyGuideKey.KEY_2 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_1)
            KeyGuideKey.KEY_3 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_2)
            KeyGuideKey.KEY_4 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_6)
            KeyGuideKey.KEY_5 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_4)
            KeyGuideKey.KEY_6 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_5)
            KeyGuideKey.KEY_7 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_9)
            KeyGuideKey.KEY_8 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_7)
            KeyGuideKey.KEY_9 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_8)
            KeyGuideKey.KEY_STAR -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_POUND)
            KeyGuideKey.KEY_0 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_STAR)
            KeyGuideKey.KEY_POUND -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_0)
        }

        DpadDirection.RIGHT -> when (key) {
            KeyGuideKey.LSK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.HOME)
            KeyGuideKey.HOME -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.RSK)
            KeyGuideKey.RSK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.SIDE_KEY)
            KeyGuideKey.CONTACTS -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.DPAD_RING)
            KeyGuideKey.DPAD_RING -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CAMERA)
            KeyGuideKey.CAMERA -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.SIDE_KEY)
            KeyGuideKey.SIDE_KEY -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.CONTACTS)
            KeyGuideKey.MESSAGES -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.OK)
            KeyGuideKey.OK -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.STAR_FN)
            KeyGuideKey.STAR_FN -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.SIDE_KEY)
            KeyGuideKey.CALL -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.DEL)
            KeyGuideKey.DEL -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.END)
            KeyGuideKey.END -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.SIDE_KEY)
            KeyGuideKey.KEY_1 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_2)
            KeyGuideKey.KEY_2 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_3)
            KeyGuideKey.KEY_3 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_1)
            KeyGuideKey.KEY_4 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_5)
            KeyGuideKey.KEY_5 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_6)
            KeyGuideKey.KEY_6 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_4)
            KeyGuideKey.KEY_7 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_8)
            KeyGuideKey.KEY_8 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_9)
            KeyGuideKey.KEY_9 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_7)
            KeyGuideKey.KEY_STAR -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_0)
            KeyGuideKey.KEY_0 -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_POUND)
            KeyGuideKey.KEY_POUND -> KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.KEY_STAR)
        }
    }
}

/**
 * Maps real AT-M120 physical keycodes / scancodes to the corresponding [KeyGuideKey] so pressing
 * any physical button lights it up on screen. (`KEYCODE_BACK` is handled separately to dismiss.)
 */
internal fun mapHardwareKeyToGuideKey(keyCode: Int, scanCode: Int = 0): KeyGuideKey? {
    if (keyCode == KeyEvent.KEYCODE_F4 || scanCode == MochibunSystemActions.SIDE_KEY_SCAN_CODE) {
        return KeyGuideKey.SIDE_KEY
    }
    return when (keyCode) {
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> KeyGuideKey.KEY_0
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> KeyGuideKey.KEY_1
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> KeyGuideKey.KEY_2
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> KeyGuideKey.KEY_3
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> KeyGuideKey.KEY_4
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> KeyGuideKey.KEY_5
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> KeyGuideKey.KEY_6
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> KeyGuideKey.KEY_7
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> KeyGuideKey.KEY_8
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> KeyGuideKey.KEY_9
        KeyEvent.KEYCODE_STAR -> KeyGuideKey.KEY_STAR
        KeyEvent.KEYCODE_POUND -> KeyGuideKey.KEY_POUND
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_CLEAR -> KeyGuideKey.DEL
        KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_MENU -> KeyGuideKey.LSK
        KeyEvent.KEYCODE_HOME -> KeyGuideKey.HOME
        KeyEvent.KEYCODE_CALL -> KeyGuideKey.CALL
        KeyEvent.KEYCODE_ENDCALL -> KeyGuideKey.END
        KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_CONTACTS -> KeyGuideKey.CONTACTS
        KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_ENVELOPE -> KeyGuideKey.MESSAGES
        KeyEvent.KEYCODE_CAMERA -> KeyGuideKey.CAMERA
        KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_BOOKMARK -> KeyGuideKey.STAR_FN
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A -> KeyGuideKey.OK
        else -> when (scanCode) {
            580 -> KeyGuideKey.LSK
            172, 102 -> KeyGuideKey.HOME
            59 -> KeyGuideKey.CONTACTS
            60 -> KeyGuideKey.MESSAGES
            212 -> KeyGuideKey.CAMERA
            61 -> KeyGuideKey.STAR_FN
            169 -> KeyGuideKey.CALL
            14 -> KeyGuideKey.DEL
            else -> null
        }
    }
}

// Crisp zero-allocation pixel-art icon bitmasks for the AT-M120 keypad.
private val PIXEL_LSK = intArrayOf(
    0b1111111,
    0b1000001,
    0b1000001,
    0b1000001,
    0b1000001,
    0b1000001,
    0b1111111
)

private val PIXEL_HOME = intArrayOf(
    0b0011100,
    0b0100010,
    0b1000001,
    0b1000001,
    0b1000001,
    0b0100010,
    0b0011100
)

private val PIXEL_RSK = intArrayOf(
    0b0000010,
    0b0001110,
    0b0110010,
    0b1000010,
    0b0110010,
    0b0001110,
    0b0000010
)

private val PIXEL_CONTACTS = intArrayOf(
    0b0011100,
    0b0100010,
    0b0100010,
    0b0011100,
    0b0111110,
    0b1000001,
    0b1111111
)

private val PIXEL_MESSAGES = intArrayOf(
    0b0111110,
    0b1000001,
    0b1010101,
    0b1000001,
    0b0111110,
    0b0110000,
    0b1000000
)

private val PIXEL_CAMERA = intArrayOf(
    0b000111000,
    0b111111111,
    0b100111001,
    0b101000101,
    0b101000101,
    0b100111001,
    0b111111111
)

private val PIXEL_STAR = intArrayOf(
    0b0001000,
    0b0011100,
    0b1111111,
    0b0111110,
    0b0011100,
    0b0110110,
    0b1100011
)

private val PIXEL_CALL = intArrayOf(
    0b001111100,
    0b011000110,
    0b110000011,
    0b110000011,
    0b000000000,
    0b001111100
)

private val PIXEL_END = intArrayOf(
    0b001111100,
    0b011000110,
    0b110010011,
    0b110101011,
    0b000010000,
    0b000000000
)

private val PIXEL_ARROW_UP = intArrayOf(
    0b00100,
    0b01110,
    0b11111
)

private val PIXEL_ARROW_DOWN = intArrayOf(
    0b11111,
    0b01110,
    0b00100
)

private val PIXEL_ARROW_LEFT = intArrayOf(
    0b001,
    0b011,
    0b111,
    0b011,
    0b001
)

private val PIXEL_ARROW_RIGHT = intArrayOf(
    0b100,
    0b110,
    0b111,
    0b110,
    0b100
)

/**
 * Interactive pastel pixel-art ALT Mive Style Folder (AT-M120) keypad reference & remap dialog.
 * Supports D-pad exploration, tap inspection, 3-tab switching (`♥ Launcher` / `✎ Typing` / `✦ Remap`),
 * live hardware keypress mirroring, and visual hardware key remapping via [HardwareRemapManager].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MochibunKeyGuideDialog(
    initialTab: KeyGuideTab = KeyGuideTab.LAUNCHER,
    installedApps: List<AppInfo> = emptyList(),
    onNestedBackHandlerChanged: ((() -> Unit)?) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val focusRequester = remember { FocusRequester() }

    var mode by remember(initialTab) { mutableStateOf(initialTab) }
    var focusZone by remember { mutableStateOf(KeyGuideZone.KEYPAD) }
    var selectedKey by remember(initialTab) {
        mutableStateOf(if (initialTab == KeyGuideTab.REMAP) KeyGuideKey.CONTACTS else KeyGuideKey.HOME)
    }
    var pressedKey by remember { mutableStateOf<KeyGuideKey?>(null) }
    var pressedDirection by remember { mutableStateOf<DpadDirection?>(null) }
    var sawCenterDown by remember { mutableStateOf(false) }

    var remapStates by remember { mutableStateOf(readKeyRemapSnapshot(context)) }
    var remapActionIndex by remember { mutableIntStateOf(0) }
    var activePicker by remember { mutableStateOf<RemapPickerType?>(null) }

    DisposableEffect(activePicker, onDismiss) {
        onNestedBackHandlerChanged(
            if (activePicker != null) {
                { activePicker = null }
            } else {
                onDismiss
            }
        )
        onDispose {
            onNestedBackHandlerChanged(null)
        }
    }

    val selectedRemapState = remapStates[selectedKey]
    val hasCustomRemapForSelected = selectedRemapState?.hasCustomRemap == true
    val isRemapActionsVisible = mode == KeyGuideTab.REMAP && selectedKey.isRemappable
    val remapActionCount = if (hasCustomRemapForSelected) 3 else 2

    LaunchedEffect(selectedKey, mode, hasCustomRemapForSelected) {
        remapActionIndex = remapActionIndex.coerceIn(0, remapActionCount - 1)
        if (!isRemapActionsVisible && focusZone == KeyGuideZone.REMAP_ACTIONS) {
            focusZone = KeyGuideZone.KEYPAD
        }
    }

    val primaryInk = remember(palette) {
        Color(
            ColorContrast.legibleInk(
                background = palette.primary.toArgb(),
                preferred = palette.textColor.toArgb(),
                alternate = palette.container.toArgb()
            )
        )
    }

    requestFocusSafely(focusRequester)

    // Intercept raw physical key broadcasts while the guide/remapper is open so pressing mapped
    // buttons (Camera, Contacts, Side Key, etc.) highlights them instead of launching apps.
    DisposableEffect(Unit) {
        val listener: (KeyEvent) -> Boolean = { event ->
            val isNav = event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                isCenterActivateKey(event.keyCode) ||
                isBackDismissKey(event.keyCode)
            if (isNav) {
                false
            } else {
                val mappedKey = mapHardwareKeyToGuideKey(event.keyCode, event.scanCode)
                if (activePicker == null && mappedKey != null) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        focusZone = KeyGuideZone.KEYPAD
                        selectedKey = mappedKey
                        pressedKey = mappedKey
                        remapActionIndex = 0
                        if (event.repeatCount == 0) {
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        pressedKey = null
                    }
                }
                true
            }
        }
        HardwareRemapManager.keyCaptureListener = listener
        onDispose {
            if (HardwareRemapManager.keyCaptureListener === listener) {
                HardwareRemapManager.keyCaptureListener = null
            }
        }
    }

    fun selectKeyFromTap(key: KeyGuideKey) {
        focusZone = KeyGuideZone.KEYPAD
        selectedKey = key
        pressedKey = null
        remapActionIndex = 0
        SoundEffectManager.play(context, SoundEffect.NAV)
        HapticManager.vibrateTick(context)
    }

    fun openAppPickerForSelected() {
        if (!selectedKey.isRemappable) {
            SoundEffectManager.play(context, SoundEffect.BOUNDARY)
            HapticManager.vibrateTick(context)
            return
        }
        pressedKey = null
        SoundEffectManager.play(context, SoundEffect.SELECT)
        activePicker = RemapPickerType.APP
    }

    fun openSystemActionPickerForSelected() {
        if (!selectedKey.isRemappable) {
            SoundEffectManager.play(context, SoundEffect.BOUNDARY)
            HapticManager.vibrateTick(context)
            return
        }
        pressedKey = null
        SoundEffectManager.play(context, SoundEffect.SELECT)
        activePicker = RemapPickerType.SYSTEM
    }

    fun clearSelectedRemap() {
        if (!selectedKey.isRemappable) return
        clearRemapForKey(context, selectedKey)
        remapStates = readKeyRemapSnapshot(context)
        remapActionIndex = 0
        SoundEffectManager.play(context, SoundEffect.SELECT)
        HapticManager.vibrateTick(context)
    }

    fun stepTab(direction: Int) {
        val entries = KeyGuideTab.entries
        val nextIdx = (mode.ordinal + direction + entries.size) % entries.size
        mode = entries[nextIdx]
        if (mode != KeyGuideTab.REMAP && focusZone == KeyGuideZone.REMAP_ACTIONS) {
            focusZone = KeyGuideZone.KEYPAD
        }
    }

    if (activePicker == RemapPickerType.APP) {
        AppPickerDialog(
            installedApps = installedApps,
            onAppSelected = { app ->
                applyPrimaryRemapAction(
                    context = context,
                    key = selectedKey,
                    action = ActionType.LaunchApp(app.packageName, app.label)
                )
                remapStates = readKeyRemapSnapshot(context)
                activePicker = null
                SoundEffectManager.play(context, SoundEffect.SELECT)
            },
            onDismiss = {
                activePicker = null
                SoundEffectManager.play(context, SoundEffect.BACK)
            }
        )
    } else if (activePicker == RemapPickerType.SYSTEM) {
        SystemActionPickerDialog(
            keyDisplayName = selectedKey.displayName,
            onActionSelected = { action ->
                applyPrimaryRemapAction(
                    context = context,
                    key = selectedKey,
                    action = action
                )
                remapStates = readKeyRemapSnapshot(context)
                activePicker = null
                SoundEffectManager.play(context, SoundEffect.SELECT)
            },
            onDismiss = {
                activePicker = null
                SoundEffectManager.play(context, SoundEffect.BACK)
            }
        )
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val isDown = event.type == KeyEventType.KeyDown
                val isUp = event.type == KeyEventType.KeyUp

                if (isBackDismissKey(native.keyCode)) {
                    if (isUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (isDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        when (focusZone) {
                            KeyGuideZone.TABS, KeyGuideZone.REMAP_ACTIONS, KeyGuideZone.CLOSE -> {
                                SoundEffectManager.play(context, SoundEffect.CLICK)
                            }
                            KeyGuideZone.KEYPAD -> {
                                if (mode == KeyGuideTab.REMAP) {
                                    if (selectedKey.isRemappable) {
                                        SoundEffectManager.play(context, SoundEffect.CLICK)
                                    } else {
                                        if (selectedKey == KeyGuideKey.OK) {
                                            pressedKey = KeyGuideKey.OK
                                        }
                                        SoundEffectManager.play(context, SoundEffect.BOUNDARY)
                                    }
                                } else {
                                    selectedKey = KeyGuideKey.OK
                                    pressedKey = KeyGuideKey.OK
                                    SoundEffectManager.play(context, SoundEffect.NAV)
                                }
                            }
                        }
                    } else if (isUp) {
                        pressedKey = null
                        if (sawCenterDown) {
                            sawCenterDown = false
                            when (focusZone) {
                                KeyGuideZone.TABS -> {
                                    stepTab(1)
                                    SoundEffectManager.play(context, SoundEffect.SELECT)
                                }
                                KeyGuideZone.REMAP_ACTIONS -> {
                                    when (remapActionIndex) {
                                        0 -> openAppPickerForSelected()
                                        1 -> openSystemActionPickerForSelected()
                                        else -> if (hasCustomRemapForSelected) clearSelectedRemap()
                                    }
                                }
                                KeyGuideZone.CLOSE -> {
                                    SoundEffectManager.play(context, SoundEffect.BACK)
                                    onDismiss()
                                }
                                KeyGuideZone.KEYPAD -> {
                                    if (mode == KeyGuideTab.REMAP) {
                                        openAppPickerForSelected()
                                    }
                                }
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                val dpadDir = when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> DpadDirection.UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> DpadDirection.DOWN
                    KeyEvent.KEYCODE_DPAD_LEFT -> DpadDirection.LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> DpadDirection.RIGHT
                    else -> null
                }
                if (dpadDir != null) {
                    if (isDown) {
                        pressedDirection = dpadDir
                        when {
                            focusZone == KeyGuideZone.TABS &&
                                (dpadDir == DpadDirection.LEFT || dpadDir == DpadDirection.RIGHT) -> {
                                stepTab(if (dpadDir == DpadDirection.LEFT) -1 else 1)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                            focusZone == KeyGuideZone.REMAP_ACTIONS &&
                                (dpadDir == DpadDirection.LEFT || dpadDir == DpadDirection.RIGHT) -> {
                                val delta = if (dpadDir == DpadDirection.LEFT) -1 else 1
                                remapActionIndex = (remapActionIndex + delta + remapActionCount) % remapActionCount
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                            else -> {
                                val next = stepKeyGuideFocus(
                                    zone = focusZone,
                                    key = selectedKey,
                                    direction = dpadDir,
                                    isRemapActionsVisible = isRemapActionsVisible
                                )
                                if (next.key != selectedKey) {
                                    remapActionIndex = 0
                                }
                                focusZone = next.zone
                                selectedKey = next.key
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                        }
                    } else if (isUp) {
                        pressedDirection = null
                    }
                    return@onPreviewKeyEvent true
                }

                // While the Mode Switcher tabs are focused, * / # cycle previous / next tab;
                // everywhere else they mirror the physical * and # keys on the keypad diagram.
                if (focusZone == KeyGuideZone.TABS &&
                    (native.keyCode == KeyEvent.KEYCODE_STAR || native.keyCode == KeyEvent.KEYCODE_POUND)
                ) {
                    if (isDown && native.repeatCount == 0) {
                        stepTab(if (native.keyCode == KeyEvent.KEYCODE_STAR) -1 else 1)
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    return@onPreviewKeyEvent true
                }

                val mappedKey = mapHardwareKeyToGuideKey(native.keyCode, native.scanCode)
                if (mappedKey != null) {
                    if (isDown) {
                        focusZone = KeyGuideZone.KEYPAD
                        selectedKey = mappedKey
                        pressedKey = mappedKey
                        remapActionIndex = 0
                        if (native.repeatCount == 0) {
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    } else if (isUp) {
                        pressedKey = null
                    }
                    return@onPreviewKeyEvent true
                }

                false
            }
    ) {
        KeitaiLaceDialogWithClose(
            isCloseFocused = focusZone == KeyGuideZone.CLOSE,
            onClose = {
                SoundEffectManager.play(context, SoundEffect.BACK)
                onDismiss()
            },
            closeLabel = "close",
            cardCornerRadius = 22.dp,
            contentPadding = 14.dp,
            modifier = Modifier
                .width(292.dp)
                .heightIn(max = 486.dp)
        ) {
            MochibunKeyGuideContent(
                mode = mode,
                focusZone = focusZone,
                selectedKey = selectedKey,
                pressedKey = pressedKey,
                pressedDirection = pressedDirection,
                primaryInk = primaryInk,
                remapStates = remapStates,
                remapActionIndex = remapActionIndex,
                onSelectMode = { tabMode ->
                    focusZone = KeyGuideZone.TABS
                    mode = tabMode
                    SoundEffectManager.play(context, SoundEffect.SELECT)
                },
                onSelectKey = ::selectKeyFromTap,
                onAssignApp = ::openAppPickerForSelected,
                onAssignSystemAction = ::openSystemActionPickerForSelected,
                onClearRemap = ::clearSelectedRemap
            )
        }
    }
}

@Composable
internal fun MochibunKeyGuideContent(
    mode: KeyGuideTab,
    focusZone: KeyGuideZone,
    selectedKey: KeyGuideKey,
    pressedKey: KeyGuideKey?,
    pressedDirection: DpadDirection?,
    primaryInk: Color,
    remapStates: Map<KeyGuideKey, KeyRemapState> = emptyMap(),
    remapActionIndex: Int = 0,
    onSelectMode: (KeyGuideTab) -> Unit,
    onSelectKey: (KeyGuideKey) -> Unit,
    onAssignApp: () -> Unit = {},
    onAssignSystemAction: () -> Unit = {},
    onClearRemap: () -> Unit = {}
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val isRemapTab = mode == KeyGuideTab.REMAP

    fun keyHasRemapDot(key: KeyGuideKey): Boolean =
        isRemapTab && key.isRemappable && (remapStates[key]?.hasCustomRemap == true)

    fun keyIsMuted(key: KeyGuideKey): Boolean =
        isRemapTab && !key.isRemappable

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MochibunSectionHeader(
            title = if (isRemapTab) "Keypad Remap" else "Key Guide",
            color = ActiveTheme.accentDeep,
            modifier = Modifier.padding(top = 1.dp, bottom = 2.dp)
        )

        // 3-Tab Mode Switcher Pill Bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false }
                .padding(vertical = 2.dp)
        ) {
            KeyGuideTab.entries.forEach { tabMode ->
                val isActiveTab = mode == tabMode
                val isTabFocused = focusZone == KeyGuideZone.TABS && isActiveTab
                val tabShape = RoundedCornerShape(14.dp)
                val tabBg = if (isActiveTab) palette.primary else theme.unfocusedCardBgColor
                val tabBorder = when {
                    isTabFocused -> palette.deepAccent
                    isActiveTab -> theme.cardBorder
                    else -> theme.cardBorder.copy(alpha = 0.6f)
                }
                val tabTextColor = if (isActiveTab) primaryInk else theme.textPrimary.copy(alpha = 0.78f)
                Box(
                    modifier = Modifier
                        .graphicsLayer { clip = false }
                        .softGlowFocus(
                            isSelected = isTabFocused,
                            cornerRadius = 14.dp,
                            glowColor = palette.selectionGlow,
                            borderColor = palette.deepAccent
                        )
                        .height(23.dp)
                        .clip(tabShape)
                        .background(tabBg, tabShape)
                        .border(if (isTabFocused) 1.4.dp else 1.dp, tabBorder, tabShape)
                        .clickable { onSelectMode(tabMode) }
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabMode.label,
                        fontSize = 9.5.sp,
                        fontWeight = if (isActiveTab) FontWeight.Bold else FontWeight.Medium,
                        color = tabTextColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        // AT-M120 Pixel Keypad Deck + Side-Mounted Side Key Pill
        val deckShape = RoundedCornerShape(
            topStart = 12.dp,
            topEnd = 12.dp,
            bottomStart = 20.dp,
            bottomEnd = 20.dp
        )
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false }
        ) {
            Column(
                modifier = Modifier
                    .width(216.dp)
                    .graphicsLayer { clip = false }
                    .background(theme.unfocusedCardBgColor.copy(alpha = 0.94f), deckShape)
                    .border(1.4.dp, theme.cardBorder, deckShape)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Subtle top clamshell hinge line
                HingeLineAccent(color = theme.cardBorder)

                // Row 1 — Android Soft-Key Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false },
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PixelKeycap(
                        isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.LSK,
                        isPressed = pressedKey == KeyGuideKey.LSK,
                        primaryInk = primaryInk,
                        hasCustomRemap = keyHasRemapDot(KeyGuideKey.LSK),
                        isMuted = keyIsMuted(KeyGuideKey.LSK),
                        height = 22.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectKey(KeyGuideKey.LSK) }
                    ) { ink ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            PixelMaskIcon(PIXEL_LSK, cols = 7, color = ink, pixelSize = 1.dp)
                            Text("LSK", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                        }
                    }
                    PixelKeycap(
                        isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.HOME,
                        isPressed = pressedKey == KeyGuideKey.HOME,
                        primaryInk = primaryInk,
                        hasCustomRemap = keyHasRemapDot(KeyGuideKey.HOME),
                        isMuted = keyIsMuted(KeyGuideKey.HOME),
                        height = 22.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectKey(KeyGuideKey.HOME) }
                    ) { ink ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            PixelMaskIcon(PIXEL_HOME, cols = 7, color = ink, pixelSize = 1.dp)
                            Text("HOME", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                        }
                    }
                    PixelKeycap(
                        isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.RSK,
                        isPressed = pressedKey == KeyGuideKey.RSK,
                        primaryInk = primaryInk,
                        hasCustomRemap = keyHasRemapDot(KeyGuideKey.RSK),
                        isMuted = keyIsMuted(KeyGuideKey.RSK),
                        height = 22.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectKey(KeyGuideKey.RSK) }
                    ) { ink ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            PixelMaskIcon(PIXEL_RSK, cols = 7, color = ink, pixelSize = 1.dp)
                            Text("RSK", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                        }
                    }
                }

                // Row 2 — Function Keys Flanking the Circular D-Pad Ring
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Left column: Contacts (top) & Messages (bottom)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { clip = false },
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PixelKeycap(
                            isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.CONTACTS,
                            isPressed = pressedKey == KeyGuideKey.CONTACTS,
                            primaryInk = primaryInk,
                            hasCustomRemap = keyHasRemapDot(KeyGuideKey.CONTACTS),
                            isMuted = keyIsMuted(KeyGuideKey.CONTACTS),
                            height = 24.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectKey(KeyGuideKey.CONTACTS) }
                        ) { ink ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                PixelMaskIcon(PIXEL_CONTACTS, cols = 7, color = ink, pixelSize = 1.1.dp)
                                Text("Cntcs", fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(theme.cardBorder.copy(alpha = 0.4f))
                        )
                        PixelKeycap(
                            isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.MESSAGES,
                            isPressed = pressedKey == KeyGuideKey.MESSAGES,
                            primaryInk = primaryInk,
                            hasCustomRemap = keyHasRemapDot(KeyGuideKey.MESSAGES),
                            isMuted = keyIsMuted(KeyGuideKey.MESSAGES),
                            height = 24.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectKey(KeyGuideKey.MESSAGES) }
                        ) { ink ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                PixelMaskIcon(PIXEL_MESSAGES, cols = 7, color = ink, pixelSize = 1.1.dp)
                                Text("Msgs", fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                            }
                        }
                    }

                    // Center: Circular D-Pad Ring + OK Center Button
                    CircularDpadPad(
                        isRingSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.DPAD_RING,
                        isOkSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.OK,
                        isOkPressed = pressedKey == KeyGuideKey.OK,
                        isMuted = isRemapTab,
                        pressedDirection = pressedDirection,
                        primaryInk = primaryInk,
                        onSelectRing = { onSelectKey(KeyGuideKey.DPAD_RING) },
                        onSelectOk = { onSelectKey(KeyGuideKey.OK) }
                    )

                    // Right column: Camera (top) & ★ Star (bottom)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { clip = false },
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PixelKeycap(
                            isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.CAMERA,
                            isPressed = pressedKey == KeyGuideKey.CAMERA,
                            primaryInk = primaryInk,
                            hasCustomRemap = keyHasRemapDot(KeyGuideKey.CAMERA),
                            isMuted = keyIsMuted(KeyGuideKey.CAMERA),
                            height = 24.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectKey(KeyGuideKey.CAMERA) }
                        ) { ink ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                PixelMaskIcon(PIXEL_CAMERA, cols = 9, color = ink, pixelSize = 1.dp)
                                Text("Cam", fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(theme.cardBorder.copy(alpha = 0.4f))
                        )
                        PixelKeycap(
                            isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.STAR_FN,
                            isPressed = pressedKey == KeyGuideKey.STAR_FN,
                            primaryInk = primaryInk,
                            hasCustomRemap = keyHasRemapDot(KeyGuideKey.STAR_FN),
                            isMuted = keyIsMuted(KeyGuideKey.STAR_FN),
                            height = 24.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectKey(KeyGuideKey.STAR_FN) }
                        ) { ink ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                PixelMaskIcon(PIXEL_STAR, cols = 7, color = ink, pixelSize = 1.1.dp)
                                Text("Star", fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                            }
                        }
                    }
                }

                // Row 3 — Call / DEL / End Row
                val isDarkPalette = palette.container.luminance() < 0.3f
                val callTint = if (isDarkPalette) Color(0xFF284632) else Color(0xFFDDF5E3)
                val callGlyph = if (isDarkPalette) Color(0xFF81E69D) else Color(0xFF2E7D32)
                val endTint = if (isDarkPalette) Color(0xFF4A2535) else Color(0xFFFFE1EA)
                val endGlyph = if (isDarkPalette) Color(0xFFFF8DB0) else Color(0xFFC2185B)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false },
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PixelKeycap(
                        isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.CALL,
                        isPressed = pressedKey == KeyGuideKey.CALL,
                        primaryInk = primaryInk,
                        hasCustomRemap = keyHasRemapDot(KeyGuideKey.CALL),
                        isMuted = keyIsMuted(KeyGuideKey.CALL),
                        customFill = callTint,
                        height = 22.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectKey(KeyGuideKey.CALL) }
                    ) { ink ->
                        val active = (focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.CALL) ||
                            pressedKey == KeyGuideKey.CALL
                        PixelMaskIcon(
                            mask = PIXEL_CALL,
                            cols = 9,
                            color = if (active) ink else callGlyph,
                            pixelSize = 1.2.dp
                        )
                    }
                    PixelKeycap(
                        isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.DEL,
                        isPressed = pressedKey == KeyGuideKey.DEL,
                        primaryInk = primaryInk,
                        hasCustomRemap = keyHasRemapDot(KeyGuideKey.DEL),
                        isMuted = keyIsMuted(KeyGuideKey.DEL),
                        height = 22.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectKey(KeyGuideKey.DEL) }
                    ) { ink ->
                        Text(
                            text = "DEL",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ink,
                            maxLines = 1
                        )
                    }
                    PixelKeycap(
                        isSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.END,
                        isPressed = pressedKey == KeyGuideKey.END,
                        primaryInk = primaryInk,
                        hasCustomRemap = keyHasRemapDot(KeyGuideKey.END),
                        isMuted = keyIsMuted(KeyGuideKey.END),
                        customFill = if (keyIsMuted(KeyGuideKey.END)) null else endTint,
                        height = 22.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectKey(KeyGuideKey.END) }
                    ) { ink ->
                        val active = (focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.END) ||
                            pressedKey == KeyGuideKey.END
                        val iconColor = when {
                            active -> ink
                            keyIsMuted(KeyGuideKey.END) -> endGlyph.copy(alpha = 0.42f)
                            else -> endGlyph
                        }
                        PixelMaskIcon(
                            mask = PIXEL_END,
                            cols = 9,
                            color = iconColor,
                            pixelSize = 1.2.dp
                        )
                    }
                }

                // Rows 4–7 — Classic 3x4 T9 Numeric Pad
                T9KeypadRow(
                    left = KeyGuideKey.KEY_1 to ("1" to "sym"),
                    center = KeyGuideKey.KEY_2 to ("2" to "ABC"),
                    right = KeyGuideKey.KEY_3 to ("3" to "DEF"),
                    selectedKey = selectedKey,
                    pressedKey = pressedKey,
                    keypadFocused = focusZone == KeyGuideZone.KEYPAD,
                    primaryInk = primaryInk,
                    remapStates = remapStates,
                    isRemapTab = isRemapTab,
                    onSelect = onSelectKey
                )
                T9KeypadRow(
                    left = KeyGuideKey.KEY_4 to ("4" to "GHI"),
                    center = KeyGuideKey.KEY_5 to ("5" to "JKL"),
                    right = KeyGuideKey.KEY_6 to ("6" to "MNO"),
                    selectedKey = selectedKey,
                    pressedKey = pressedKey,
                    keypadFocused = focusZone == KeyGuideZone.KEYPAD,
                    primaryInk = primaryInk,
                    remapStates = remapStates,
                    isRemapTab = isRemapTab,
                    onSelect = onSelectKey
                )
                T9KeypadRow(
                    left = KeyGuideKey.KEY_7 to ("7" to "PQRS"),
                    center = KeyGuideKey.KEY_8 to ("8" to "TUV"),
                    right = KeyGuideKey.KEY_9 to ("9" to "WXYZ"),
                    selectedKey = selectedKey,
                    pressedKey = pressedKey,
                    keypadFocused = focusZone == KeyGuideZone.KEYPAD,
                    primaryInk = primaryInk,
                    remapStates = remapStates,
                    isRemapTab = isRemapTab,
                    onSelect = onSelectKey
                )
                T9KeypadRow(
                    left = KeyGuideKey.KEY_STAR to ("*" to "shift"),
                    center = KeyGuideKey.KEY_0 to ("0" to "␣"),
                    right = KeyGuideKey.KEY_POUND to ("#" to "wheel"),
                    selectedKey = selectedKey,
                    pressedKey = pressedKey,
                    keypadFocused = focusZone == KeyGuideZone.KEYPAD,
                    primaryInk = primaryInk,
                    remapStates = remapStates,
                    isRemapTab = isRemapTab,
                    onSelect = onSelectKey
                )
            }

            // Outer Edge Side Key Pill mounted along the right bezel
            val sideSelected = focusZone == KeyGuideZone.KEYPAD && selectedKey == KeyGuideKey.SIDE_KEY
            val sidePressed = pressedKey == KeyGuideKey.SIDE_KEY
            val sideLit = sideSelected || sidePressed
            val sideHasRemapDot = keyHasRemapDot(KeyGuideKey.SIDE_KEY)
            val sideShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp, topStart = 3.dp, bottomStart = 3.dp)
            Box(
                modifier = Modifier
                    .padding(top = 34.dp)
                    .offset(x = (-1).dp)
                    .graphicsLayer { clip = false }
                    .softGlowFocus(
                        isSelected = sideSelected,
                        cornerRadius = 8.dp,
                        glowColor = palette.selectionGlow,
                        borderColor = palette.deepAccent
                    )
                    .width(25.dp)
                    .height(48.dp)
                    .clip(sideShape)
                    .background(if (sideLit) palette.primary else theme.surface, sideShape)
                    .border(
                        width = if (sideLit) 1.4.dp else 1.2.dp,
                        color = if (sideLit) palette.deepAccent else theme.cardBorder,
                        shape = sideShape
                    )
                    .clickable { onSelectKey(KeyGuideKey.SIDE_KEY) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SIDE\nKEY",
                    fontSize = 7.sp,
                    lineHeight = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = if (sideLit) primaryInk else theme.textPrimary
                )
                if (sideHasRemapDot) {
                    RemapIndicatorDot(
                        dotColor = if (sideLit) primaryInk else palette.deepAccent,
                        borderColor = if (sideLit) palette.primary else theme.surface,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 3.dp, end = 3.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Live Keymap Callout Card
        val calloutShape = RoundedCornerShape(14.dp)
        val selectedRemapState = remapStates[selectedKey]
        val hasCustomRemap = selectedRemapState?.hasCustomRemap == true
        val (remapTapDesc, remapHoldDesc) = remember(selectedKey, selectedRemapState) {
            formatRemapCalloutLines(selectedKey, selectedRemapState)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false }
                .background(theme.surface, calloutShape)
                .border(1.2.dp, theme.cardBorder, calloutShape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedKey.displayName,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActiveTheme.accentDeep,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val badgeBg = when {
                    isRemapTab && !selectedKey.isRemappable -> theme.unfocusedCardBgColor
                    isRemapTab && hasCustomRemap -> palette.primary
                    else -> palette.primary.copy(alpha = 0.55f)
                }
                val badgeText = when {
                    isRemapTab && !selectedKey.isRemappable -> "System / Protected"
                    isRemapTab && hasCustomRemap -> "✦ Custom"
                    isRemapTab -> "Default"
                    else -> mode.label
                }
                val badgeInkColor = if (isRemapTab && !selectedKey.isRemappable) {
                    theme.textPrimary.copy(alpha = 0.72f)
                } else {
                    primaryInk
                }
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(8.dp))
                        .then(
                            if (isRemapTab && !selectedKey.isRemappable) {
                                Modifier.border(0.8.dp, theme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeInkColor,
                        maxLines = 1
                    )
                }
            }

            if (isRemapTab) {
                if (!selectedKey.isRemappable) {
                    CalloutActionRow(
                        badge = "Lock",
                        description = remapTapDesc,
                        badgeColor = theme.unfocusedCardBgColor,
                        badgeInk = theme.textPrimary.copy(alpha = 0.72f),
                        textColor = theme.cardTextColor,
                        borderColor = theme.cardBorder.copy(alpha = 0.6f)
                    )
                    CalloutActionRow(
                        badge = "Role",
                        description = remapHoldDesc,
                        badgeColor = theme.unfocusedCardBgColor,
                        badgeInk = theme.textPrimary.copy(alpha = 0.7f),
                        textColor = theme.cardTextColor.copy(alpha = 0.85f),
                        borderColor = theme.cardBorder.copy(alpha = 0.5f)
                    )
                } else {
                    CalloutActionRow(
                        badge = "Tap",
                        description = remapTapDesc,
                        badgeColor = palette.primary,
                        badgeInk = primaryInk,
                        textColor = theme.textPrimary
                    )
                    CalloutActionRow(
                        badge = "Hold",
                        description = remapHoldDesc,
                        badgeColor = theme.unfocusedCardBgColor,
                        badgeInk = theme.textPrimary.copy(alpha = 0.85f),
                        textColor = theme.cardTextColor,
                        borderColor = theme.cardBorder.copy(alpha = 0.7f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { clip = false }
                            .padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RemapCalloutButton(
                            label = "Assign App",
                            isFocused = focusZone == KeyGuideZone.REMAP_ACTIONS && remapActionIndex == 0,
                            isPrimary = true,
                            primaryInk = primaryInk,
                            modifier = Modifier.weight(1.05f),
                            onClick = onAssignApp
                        )
                        RemapCalloutButton(
                            label = "System Action",
                            isFocused = focusZone == KeyGuideZone.REMAP_ACTIONS && remapActionIndex == 1,
                            isPrimary = false,
                            primaryInk = primaryInk,
                            modifier = Modifier.weight(1.25f),
                            onClick = onAssignSystemAction
                        )
                        if (hasCustomRemap) {
                            RemapCalloutButton(
                                label = "Clear",
                                isFocused = focusZone == KeyGuideZone.REMAP_ACTIONS && remapActionIndex == 2,
                                isPrimary = false,
                                isDestructive = true,
                                primaryInk = primaryInk,
                                modifier = Modifier.weight(0.75f),
                                onClick = onClearRemap
                            )
                        }
                    }
                }
            } else {
                CalloutActionRow(
                    badge = "Tap",
                    description = selectedKey.tapText(mode),
                    badgeColor = palette.primary,
                    badgeInk = primaryInk,
                    textColor = theme.textPrimary
                )
                CalloutActionRow(
                    badge = "Hold",
                    description = selectedKey.holdText(mode),
                    badgeColor = theme.unfocusedCardBgColor,
                    badgeInk = theme.textPrimary.copy(alpha = 0.85f),
                    textColor = theme.cardTextColor,
                    borderColor = theme.cardBorder.copy(alpha = 0.7f)
                )
            }
        }

        val footerHint = when {
            isRemapTab && selectedKey.isRemappable -> "OK on key to assign app · D-pad or press real key"
            isRemapTab -> "Protected key · pick Contacts, Cam, Star, Side or 0–9"
            else -> "D-pad to browse · Press any real key to highlight"
        }
        Text(
            text = footerHint,
            fontSize = 9.sp,
            color = theme.cardTextColor.copy(alpha = 0.68f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun RemapIndicatorDot(
    dotColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(4.5.dp)
            .background(dotColor, CircleShape)
            .border(0.6.dp, borderColor, CircleShape)
    )
}

@Composable
private fun RemapCalloutButton(
    label: String,
    isFocused: Boolean,
    isPrimary: Boolean,
    primaryInk: Color,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val shape = RoundedCornerShape(9.dp)
    val bg = when {
        isDestructive && isFocused -> destructiveBgColor
        isDestructive -> theme.unfocusedCardBgColor
        isFocused || isPrimary -> palette.primary.copy(alpha = if (isFocused) 1f else 0.65f)
        else -> theme.unfocusedCardBgColor
    }
    val borderColor = when {
        isDestructive && isFocused -> destructiveColor
        isDestructive -> destructiveColor.copy(alpha = 0.55f)
        isFocused -> palette.deepAccent
        else -> theme.cardBorder
    }
    val textColor = when {
        isDestructive -> destructiveColor
        isFocused || isPrimary -> primaryInk
        else -> theme.textPrimary
    }

    Box(
        modifier = modifier
            .height(22.dp)
            .graphicsLayer { clip = false }
            .softGlowFocus(
                isSelected = isFocused,
                cornerRadius = 9.dp,
                glowColor = palette.selectionGlow,
                borderColor = if (isDestructive) destructiveColor else palette.deepAccent
            )
            .clip(shape)
            .background(bg, shape)
            .border(if (isFocused) 1.4.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemActionPickerDialog(
    keyDisplayName: String,
    onActionSelected: (ActionType) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isCloseFocused by remember { mutableStateOf(false) }
    var sawCenterDown by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex) {
        if (!isCloseFocused) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        if (isCloseFocused) {
                            onDismiss()
                        } else {
                            SYSTEM_REMAP_ACTIONS.getOrNull(selectedIndex)?.let(onActionSelected)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (isCloseFocused) {
                                isCloseFocused = false
                                selectedIndex = SYSTEM_REMAP_ACTIONS.lastIndex
                            } else if (selectedIndex > 0) {
                                selectedIndex--
                            } else {
                                isCloseFocused = true
                            }
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (isCloseFocused) {
                                isCloseFocused = false
                                selectedIndex = 0
                            } else if (selectedIndex < SYSTEM_REMAP_ACTIONS.lastIndex) {
                                selectedIndex++
                            } else {
                                isCloseFocused = true
                            }
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
    ) {
        KeitaiLaceDialogWithClose(
            isCloseFocused = isCloseFocused,
            onClose = onDismiss,
            closeLabel = "cancel",
            cardCornerRadius = 22.dp,
            contentPadding = 14.dp,
            modifier = Modifier
                .width(282.dp)
                .heightIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MochibunSectionHeader(
                    title = "System Action · $keyDisplayName",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 310.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = SYSTEM_REMAP_ACTIONS,
                        key = { _, action -> action.serialize() },
                        contentType = { _, _ -> "system_remap_action" }
                    ) { index, action ->
                        val isFocused = !isCloseFocused && index == selectedIndex
                        val itemShape = RoundedCornerShape(12.dp)
                        val bg = if (isFocused) ActiveTheme.surfaceTint.copy(alpha = 0.5f) else Color.Transparent
                        val border = if (isFocused) BorderStroke(1.4.dp, ActiveTheme.selectionGlow) else null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .then(if (border != null) Modifier.border(border, itemShape) else Modifier)
                                .background(bg, itemShape)
                                .clickable { onActionSelected(action) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✦",
                                fontSize = 11.sp,
                                color = ActiveTheme.accentDeep,
                                modifier = Modifier.padding(end = 7.dp)
                            )
                            Text(
                                text = action.displayName(),
                                fontSize = 11.5.sp,
                                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HingeLineAccent(color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(3.dp)
                .background(color.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(color.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(3.dp)
                .background(color.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun T9KeypadRow(
    left: Pair<KeyGuideKey, Pair<String, String>>,
    center: Pair<KeyGuideKey, Pair<String, String>>,
    right: Pair<KeyGuideKey, Pair<String, String>>,
    selectedKey: KeyGuideKey,
    pressedKey: KeyGuideKey?,
    keypadFocused: Boolean,
    primaryInk: Color,
    remapStates: Map<KeyGuideKey, KeyRemapState>,
    isRemapTab: Boolean,
    onSelect: (KeyGuideKey) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(left, center, right).forEach { (key, labels) ->
            val (main, sub) = labels
            PixelKeycap(
                isSelected = keypadFocused && selectedKey == key,
                isPressed = pressedKey == key,
                primaryInk = primaryInk,
                hasCustomRemap = isRemapTab && key.isRemappable && (remapStates[key]?.hasCustomRemap == true),
                isMuted = isRemapTab && !key.isRemappable,
                height = 23.dp,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(key) }
            ) { ink ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = main,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ink,
                        maxLines = 1
                    )
                    Text(
                        text = sub,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink.copy(alpha = 0.78f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PixelKeycap(
    isSelected: Boolean,
    isPressed: Boolean,
    primaryInk: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    hasCustomRemap: Boolean = false,
    isMuted: Boolean = false,
    customFill: Color? = null,
    onClick: () -> Unit,
    content: @Composable (ink: Color) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val lit = isSelected || isPressed
    val shape = RoundedCornerShape(6.dp)
    val fill = when {
        lit -> palette.primary
        isMuted -> theme.unfocusedCardBgColor.copy(alpha = 0.52f)
        else -> customFill ?: theme.surface
    }
    val borderColor = when {
        lit -> palette.deepAccent
        isMuted -> theme.cardBorder.copy(alpha = 0.36f)
        else -> theme.cardBorder
    }
    val ink = when {
        lit -> primaryInk
        isMuted -> theme.textPrimary.copy(alpha = 0.42f)
        else -> theme.textPrimary
    }

    // Two-layer keycap gives a crisp 1.5dp bottom bevel edge that depresses when held.
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { clip = false }
            .softGlowFocus(
                isSelected = isSelected,
                cornerRadius = 6.dp,
                glowColor = palette.selectionGlow,
                borderColor = palette.deepAccent
            )
            .background(theme.cardBorder.copy(alpha = if (isMuted && !lit) 0.22f else 0.48f), shape)
            .padding(bottom = if (isPressed) 0.dp else 1.5.dp, top = if (isPressed) 1.5.dp else 0.dp)
            .clip(shape)
            .background(fill, shape)
            .border(1.2.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content(ink)
        if (hasCustomRemap) {
            RemapIndicatorDot(
                dotColor = if (lit) primaryInk else palette.deepAccent,
                borderColor = if (lit) palette.primary else theme.surface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
            )
        }
    }
}

@Composable
private fun CircularDpadPad(
    isRingSelected: Boolean,
    isOkSelected: Boolean,
    isOkPressed: Boolean,
    isMuted: Boolean = false,
    pressedDirection: DpadDirection?,
    primaryInk: Color,
    onSelectRing: () -> Unit,
    onSelectOk: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val ringLit = isRingSelected || pressedDirection != null
    val ringFill = when {
        isRingSelected -> palette.primary
        isMuted -> theme.unfocusedCardBgColor.copy(alpha = 0.52f)
        else -> theme.surface
    }
    val ringBorder = when {
        ringLit -> palette.deepAccent
        isMuted -> theme.cardBorder.copy(alpha = 0.36f)
        else -> theme.cardBorder
    }
    val ringArrowInk = when {
        isRingSelected -> primaryInk.copy(alpha = 0.72f)
        isMuted -> theme.textPrimary.copy(alpha = 0.36f)
        else -> theme.textPrimary.copy(alpha = 0.65f)
    }
    val activeDirInk = palette.deepAccent

    val okLit = isOkSelected || isOkPressed
    val okFill = when {
        okLit -> palette.primary
        isMuted -> theme.unfocusedCardBgColor.copy(alpha = 0.52f)
        else -> theme.unfocusedCardBgColor
    }
    val okBorder = when {
        okLit -> palette.deepAccent
        isMuted -> theme.cardBorder.copy(alpha = 0.36f)
        else -> theme.cardBorder
    }
    val okInk = when {
        okLit -> primaryInk
        isMuted -> theme.textPrimary.copy(alpha = 0.42f)
        else -> theme.textPrimary
    }

    Box(
        modifier = Modifier
            .size(58.dp)
            .graphicsLayer { clip = false },
        contentAlignment = Alignment.Center
    ) {
        // Outer D-pad ring
        Box(
            modifier = Modifier
                .size(58.dp)
                .graphicsLayer { clip = false }
                .softGlowFocus(
                    isSelected = isRingSelected,
                    cornerRadius = 29.dp,
                    glowColor = palette.selectionGlow,
                    borderColor = palette.deepAccent
                )
                .clip(CircleShape)
                .background(ringFill, CircleShape)
                .border(1.3.dp, ringBorder, CircleShape)
                .clickable(onClick = onSelectRing)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 5.dp)
            ) {
                PixelMaskIcon(
                    mask = PIXEL_ARROW_UP,
                    cols = 5,
                    color = if (pressedDirection == DpadDirection.UP) activeDirInk else ringArrowInk,
                    pixelSize = 1.2.dp
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
            ) {
                PixelMaskIcon(
                    mask = PIXEL_ARROW_DOWN,
                    cols = 5,
                    color = if (pressedDirection == DpadDirection.DOWN) activeDirInk else ringArrowInk,
                    pixelSize = 1.2.dp
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 5.dp)
            ) {
                PixelMaskIcon(
                    mask = PIXEL_ARROW_LEFT,
                    cols = 3,
                    color = if (pressedDirection == DpadDirection.LEFT) activeDirInk else ringArrowInk,
                    pixelSize = 1.2.dp
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 5.dp)
            ) {
                PixelMaskIcon(
                    mask = PIXEL_ARROW_RIGHT,
                    cols = 3,
                    color = if (pressedDirection == DpadDirection.RIGHT) activeDirInk else ringArrowInk,
                    pixelSize = 1.2.dp
                )
            }
        }

        // Center OK button
        Box(
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer { clip = false }
                .softGlowFocus(
                    isSelected = isOkSelected,
                    cornerRadius = 13.dp,
                    glowColor = palette.selectionGlow,
                    borderColor = palette.deepAccent
                )
                .clip(CircleShape)
                .background(okFill, CircleShape)
                .border(1.2.dp, okBorder, CircleShape)
                .clickable(onClick = onSelectOk),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OK",
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                color = okInk,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PixelMaskIcon(
    mask: IntArray,
    cols: Int,
    color: Color,
    pixelSize: Dp
) {
    val rows = mask.size
    Canvas(
        modifier = Modifier.size(width = pixelSize * cols, height = pixelSize * rows)
    ) {
        val px = pixelSize.toPx()
        for (r in 0 until rows) {
            val bits = mask[r]
            for (c in 0 until cols) {
                if ((bits and (1 shl (cols - 1 - c))) != 0) {
                    drawRect(
                        color = color,
                        topLeft = Offset(c * px, r * px),
                        size = Size(px, px)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalloutActionRow(
    badge: String,
    description: String,
    badgeColor: Color,
    badgeInk: Color,
    textColor: Color,
    borderColor: Color? = null
) {
    val badgeShape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .background(badgeColor, badgeShape)
                .then(
                    if (borderColor != null) Modifier.border(0.8.dp, borderColor, badgeShape) else Modifier
                )
                .padding(vertical = 1.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = badgeInk,
                maxLines = 1
            )
        }
        Text(
            text = description,
            fontSize = 10.sp,
            lineHeight = 12.5.sp,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
