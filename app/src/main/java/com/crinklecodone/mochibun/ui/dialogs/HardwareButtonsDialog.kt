package com.crinklecodone.mochibun.ui.dialogs

import android.content.Context
import android.view.KeyEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.model.ActionType
import com.crinklecodone.mochibun.data.model.AppInfo
import com.crinklecodone.mochibun.data.model.ButtonActionConfig
import com.crinklecodone.mochibun.data.model.PhysicalKey
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
import com.crinklecodone.mochibun.service.MochibunAccessibilityService
import com.crinklecodone.mochibun.ui.components.MascotMood
import com.crinklecodone.mochibun.ui.components.MochibunMascot
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.destructiveColor

/**
 * Cute retro pixel-art badge representation of a physical hardware button.
 */
data class KeyBadgeData(
    val badgeLabel: String,
    val iconGlyph: String
)

fun PhysicalKey.toBadgeData(): KeyBadgeData = when (this) {
    PhysicalKey.F1 -> KeyBadgeData("F1", "★")
    PhysicalKey.CAMERA -> KeyBadgeData("CAM", "📷")
    PhysicalKey.KEY_ENTER -> KeyBadgeData("OK", "◉")
    PhysicalKey.F2 -> KeyBadgeData("F2", "✦")
    PhysicalKey.F3 -> KeyBadgeData("F3", "✧")
    PhysicalKey.CALL -> KeyBadgeData("CALL", "📞")
    PhysicalKey.APP_SWITCH -> KeyBadgeData("RECENTS", "⧉")
    PhysicalKey.F4 -> KeyBadgeData("SIDE / F4", "⏵")
    PhysicalKey.STAR -> KeyBadgeData("STAR (*)", "✱")
    PhysicalKey.POUND -> KeyBadgeData("POUND (#)", "＃")
}

@Composable
fun RetroKeyBadge(
    key: PhysicalKey,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val data = key.toBadgeData()
    RetroKeyBadge(
        label = data.badgeLabel,
        icon = data.iconGlyph,
        isFocused = isFocused,
        modifier = modifier
    )
}

@Composable
fun RetroKeyBadge(
    label: String,
    icon: String = "⌨",
    isFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(7.dp)
    val bg = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.surfaceTint.copy(alpha = 0.35f)
    val borderCol = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.7f)
    val textCol = if (isFocused) Color.White else ActiveTheme.accentDeep

    Row(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, borderCol, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = icon,
            fontSize = 11.sp,
            color = textCol
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = textCol
        )
    }
}

/**
 * Unified Kawaii Keypad Remapper dialog matching the Kaomoji Key Capture aesthetic.
 * Listens for any physical button press, intercepts raw events before window dispatch to
 * suppress stock actions, and allows configuring Single Tap, Long Press, and Double Tap actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareButtonsDialog(
    installedApps: List<AppInfo>,
    initialCapturedKeyCode: Int? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var isListening by remember { mutableStateOf(initialCapturedKeyCode == null) }
    var capturedKeyCode by remember { mutableIntStateOf(initialCapturedKeyCode ?: 0) }
    var capturedScanCode by remember { mutableIntStateOf(0) }
    var statusHint by remember { mutableStateOf<String?>(null) }
    var configuredBadges by remember { mutableStateOf(HardwareRemapManager.getConfiguredKeyBadges(context)) }

    // Action config state for currently captured key
    var singleAction by remember { mutableStateOf<ActionType>(ActionType.None) }
    var doubleAction by remember { mutableStateOf<ActionType>(ActionType.None) }
    var longAction by remember { mutableStateOf<ActionType>(ActionType.None) }

    // Sub-dialogs
    var appPickerTargetAction by remember { mutableStateOf<Int?>(null) } // 0: single, 1: long, 2: double

    // 0: Single Tap, 1: Long Press, 2: Double Tap, 3: Save & Apply, 4: Remap Another, 5: Clear Actions
    var selectedRowIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "keypad_remap_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "remap_pulse_scale"
    )

    fun loadKeyConfig(keyCode: Int, scanCode: Int) {
        val cfg = HardwareRemapManager.getButtonActionConfigForKey(context, keyCode, scanCode)
        singleAction = cfg.singlePressAction
        doubleAction = cfg.doublePressAction
        longAction = cfg.longPressAction
    }

    LaunchedEffect(initialCapturedKeyCode) {
        if (initialCapturedKeyCode != null) {
            loadKeyConfig(initialCapturedKeyCode, 0)
        }
    }

    requestFocusSafely(focusRequester)

    // Direct hardware key interceptor: consumes raw physical key presses so stock ROM
    // never fires stock apps/services (and prevents F3 hold crash loops)
    DisposableEffect(isListening) {
        if (isListening) {
            MochibunAccessibilityService.keyCaptureListener = { event ->
                val native = event
                val isNav = native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    isCenterActivateKey(native.keyCode)

                if (isNav) {
                    if (native.action == KeyEvent.ACTION_DOWN) {
                        statusHint = "D-Pad & OK are reserved for launcher navigation ♡"
                        SoundEffectManager.play(context, SoundEffect.BOUNDARY)
                    }
                    false
                } else if (isBackDismissKey(native.keyCode)) {
                    if (native.action == KeyEvent.ACTION_UP) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onDismiss()
                    }
                    false
                } else {
                    if (native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                        val resolvedKeyCode = if (native.keyCode == KeyEvent.KEYCODE_UNKNOWN && native.scanCode == 62) {
                            KeyEvent.KEYCODE_F4
                        } else {
                            native.keyCode
                        }
                        capturedKeyCode = resolvedKeyCode
                        capturedScanCode = native.scanCode
                        loadKeyConfig(resolvedKeyCode, native.scanCode)
                        statusHint = null
                        isListening = false
                        selectedRowIndex = 0
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    true // Consume so stock ROM actions never fire!
                }
            }
        } else {
            MochibunAccessibilityService.keyCaptureListener = null
        }
        onDispose {
            MochibunAccessibilityService.keyCaptureListener = null
        }
    }

    val cycleList = listOf(
        ActionType.None,
        ActionType.OpenWidgetsPage,
        ActionType.OpenNotificationsPage,
        ActionType.GlobalBack,
        ActionType.GlobalRecents,
        ActionType.OpenRecentsSystem,
        ActionType.GlobalNotifications,
        ActionType.ToggleFlashlight
    )

    fun cycleAction(current: ActionType, forward: Boolean): ActionType {
        val currentIndex = cycleList.indexOfFirst { it::class == current::class }
        return if (currentIndex == -1) {
            if (forward) cycleList.first() else cycleList.last()
        } else {
            val nextIndex = if (forward) {
                (currentIndex + 1) % cycleList.size
            } else {
                if (currentIndex > 0) currentIndex - 1 else cycleList.size - 1
            }
            cycleList[nextIndex]
        }
    }

    val displayKeyCode = capturedKeyCode
    val displayScanCode = capturedScanCode
    val physicalKey = remember(displayKeyCode, displayScanCode) {
        PhysicalKey.fromKeyCode(displayKeyCode)
            ?: if (displayScanCode > 0) PhysicalKey.fromScancode(displayScanCode) else null
    }
    val keyName = remember(displayKeyCode, displayScanCode) {
        if (displayKeyCode != 0 || displayScanCode != 0) {
            PreferencesManager.formatKaomojiKeyName(displayKeyCode, displayScanCode)
        } else {
            "Select Button"
        }
    }
    val keyBadge = remember(displayKeyCode, displayScanCode) {
        if (displayKeyCode != 0 || displayScanCode != 0) {
            PreferencesManager.formatKaomojiKeyBadge(displayKeyCode, displayScanCode)
        } else {
            "?"
        }
    }

    if (appPickerTargetAction != null) {
        AppPickerDialog(
            installedApps = installedApps,
            onAppSelected = { app ->
                val newAction = ActionType.LaunchApp(app.packageName, app.label)
                when (appPickerTargetAction) {
                    0 -> singleAction = newAction
                    1 -> longAction = newAction
                    2 -> doubleAction = newAction
                }
                appPickerTargetAction = null
                SoundEffectManager.play(context, SoundEffect.SELECT)
            },
            onDismiss = {
                appPickerTargetAction = null
                SoundEffectManager.play(context, SoundEffect.BACK)
            }
        )
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent

                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        if (!isListening) {
                            isListening = true
                        } else {
                            onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isListening) {
                    return@onPreviewKeyEvent false
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (selectedRowIndex) {
                            0 -> appPickerTargetAction = 0
                            1 -> appPickerTargetAction = 1
                            2 -> appPickerTargetAction = 2
                            3 -> {
                                val newConfig = ButtonActionConfig(
                                    singlePressAction = singleAction,
                                    doublePressAction = doubleAction,
                                    longPressAction = longAction
                                )
                                HardwareRemapManager.saveButtonActionConfigForKey(context, capturedKeyCode, capturedScanCode, newConfig)
                                MochibunAccessibilityService.reloadActiveConfig(context)
                                configuredBadges = HardwareRemapManager.getConfiguredKeyBadges(context)
                                SoundEffectManager.play(context, SoundEffect.SELECT)
                                onDismiss()
                            }
                            4 -> {
                                isListening = true
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                            5 -> {
                                singleAction = ActionType.None
                                doubleAction = ActionType.None
                                longAction = ActionType.None
                                HardwareRemapManager.resetButtonActionConfigForKey(context, capturedKeyCode, capturedScanCode)
                                MochibunAccessibilityService.reloadActiveConfig(context)
                                configuredBadges = HardwareRemapManager.getConfiguredKeyBadges(context)
                                SoundEffectManager.play(context, SoundEffect.SELECT)
                                isListening = true
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type == KeyEventType.KeyDown) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            selectedRowIndex = if (selectedRowIndex > 0) selectedRowIndex - 1 else 5
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            selectedRowIndex = if (selectedRowIndex < 5) selectedRowIndex + 1 else 0
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            when (selectedRowIndex) {
                                0 -> singleAction = cycleAction(singleAction, false)
                                1 -> longAction = cycleAction(longAction, false)
                                2 -> doubleAction = cycleAction(doubleAction, false)
                            }
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            when (selectedRowIndex) {
                                0 -> singleAction = cycleAction(singleAction, true)
                                1 -> longAction = cycleAction(longAction, true)
                                2 -> doubleAction = cycleAction(doubleAction, true)
                            }
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                true
            }
    ) {
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PixelRibbonBow(width = 22.dp, height = 18.dp)
                    Text(
                        text = "— ♡ Keypad Remapper ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    PixelRibbonBow(width = 22.dp, height = 18.dp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer {
                            scaleX = if (isListening) pulseScale else 1f
                            scaleY = if (isListening) pulseScale else 1f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MochibunMascot(
                        mood = if (!isListening) MascotMood.CHEER else MascotMood.WORRIED,
                        size = 48.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isListening) {
                    Text(
                        text = "Listening for physical key...",
                        color = ActiveTheme.accentDeep,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Press any button on your keypad to remap it\n(Side Key, Call, Camera, F1–F4, *, #, 0–9, etc.)",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )

                    if (configuredBadges.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Currently Active Remappings:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ActiveTheme.accentDeep
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = configuredBadges.joinToString(" • "),
                            fontSize = 9.5.sp,
                            color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (statusHint != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusHint ?: "",
                            color = ActiveTheme.accentDeep,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (physicalKey != null) {
                            RetroKeyBadge(key = physicalKey, isFocused = true)
                        } else {
                            RetroKeyBadge(label = keyBadge, isFocused = true)
                        }
                        Text(
                            text = keyName,
                            color = ActiveTheme.accentDeep,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = ActiveTheme.cardBorder.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        RemapActionRow(
                            label = "Single Tap",
                            action = singleAction,
                            isFocused = selectedRowIndex == 0,
                            onClick = { appPickerTargetAction = 0 }
                        )

                        RemapActionRow(
                            label = "Hold / Long Press",
                            action = longAction,
                            isFocused = selectedRowIndex == 1,
                            onClick = { appPickerTargetAction = 1 }
                        )

                        RemapActionRow(
                            label = "Double Tap",
                            action = doubleAction,
                            isFocused = selectedRowIndex == 2,
                            onClick = { appPickerTargetAction = 2 }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        RemapButtonRow(
                            title = "Save & Apply ♡",
                            isFocused = selectedRowIndex == 3,
                            isPrimary = true
                        )

                        RemapButtonRow(
                            title = "Remap Another Key",
                            isFocused = selectedRowIndex == 4
                        )

                        RemapButtonRow(
                            title = "Clear Actions For This Key",
                            isFocused = selectedRowIndex == 5,
                            isDestructive = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemapActionRow(
    label: String,
    action: ActionType,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val rowShape = RoundedCornerShape(10.dp)
    val bg = if (isFocused) ActiveTheme.surfaceTint.copy(alpha = 0.55f) else ActiveTheme.surfaceTint.copy(alpha = 0.18f)
    val border = if (isFocused) BorderStroke(1.5.dp, ActiveTheme.selectionGlow) else BorderStroke(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.4f))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .border(border, rowShape)
            .background(bg, rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary
        )
        Text(
            text = "◀ ${action.displayName()} ▶",
            fontSize = 10.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RemapButtonRow(
    title: String,
    isFocused: Boolean,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false
) {
    val rowShape = RoundedCornerShape(10.dp)
    val bg = when {
        isFocused && isPrimary -> ActiveTheme.accentDeep
        isFocused -> ActiveTheme.surfaceTint.copy(alpha = 0.55f)
        isPrimary -> ActiveTheme.accentDeep.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val border = when {
        isFocused -> BorderStroke(1.5.dp, ActiveTheme.selectionGlow)
        else -> BorderStroke(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.4f))
    }
    val textCol = when {
        isFocused && isPrimary -> Color.White
        isDestructive -> destructiveColor
        isFocused -> ActiveTheme.accentDeep
        else -> ActiveTheme.textPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .border(border, rowShape)
            .background(bg, rowShape)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.Normal,
            color = textCol
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    installedApps: List<AppInfo>,
    onAppSelected: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val sortedApps = remember(installedApps) { installedApps.sortedBy { it.label.lowercase() } }
    val listState = rememberLazyListState()
    var selectedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.80f)
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
                        sortedApps.getOrNull(selectedIndex)?.let { onAppSelected(it) }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else sortedApps.size - 1
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            selectedIndex = if (selectedIndex < sortedApps.size - 1) selectedIndex + 1 else 0
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
    ) {
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .border(1.5.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PixelRibbonBow(width = 22.dp, height = 18.dp)
                    Text(
                        text = "— ♡ Choose Application ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    PixelRibbonBow(width = 22.dp, height = 18.dp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(sortedApps) { index, app ->
                        val isFocused = index == selectedIndex
                        val itemShape = RoundedCornerShape(12.dp)
                        val bg = if (isFocused) ActiveTheme.surfaceTint.copy(alpha = 0.5f) else Color.Transparent
                        val border = if (isFocused) BorderStroke(1.5.dp, ActiveTheme.selectionGlow) else null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .then(if (border != null) Modifier.border(border, itemShape) else Modifier)
                                .background(bg, itemShape)
                                .clickable { onAppSelected(app) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🌸",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = app.label,
                                fontSize = 12.sp,
                                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
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
