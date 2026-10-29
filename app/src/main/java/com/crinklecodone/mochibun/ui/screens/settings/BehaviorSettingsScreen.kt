package com.crinklecodone.mochibun.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.core.system.LidLedManager
import com.crinklecodone.mochibun.data.model.PhysicalKey
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
import com.crinklecodone.mochibun.service.MochibunAccessibilityService
import com.crinklecodone.mochibun.ui.components.MascotMood
import com.crinklecodone.mochibun.ui.components.MochibunMascot
import com.crinklecodone.mochibun.ui.components.mascotTintColor
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.dialogs.LidLedColorPickerDialog
import com.crinklecodone.mochibun.ui.dialogs.PixelRibbonBow
import com.crinklecodone.mochibun.ui.dialogs.RetroKeyBadge
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.destructiveColor

/**
 * Tier 3: Layout & Behavior Sub-Screen
 */
@Composable
internal fun BehaviorSettingsSubScreen(
    onBack: () -> Unit,
    onOpenHiddenApps: (() -> Unit)?,
    onCreateFolderClick: (() -> Unit)?,
    onOpenHardwareButtons: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val isFolderGridMode by PreferencesManager.isFolderGridMode.collectAsState()
    val isT9SearchEnabled by PreferencesManager.isT9SearchEnabled.collectAsState()
    val kaomojiKeyCode by PreferencesManager.kaomojiTriggerKeyCode.collectAsState()
    val kaomojiScanCode by PreferencesManager.kaomojiTriggerScanCode.collectAsState()
    val kaomojiLongPress by PreferencesManager.kaomojiTriggerLongPress.collectAsState()
    var isCapturingKaomojiKey by remember { mutableStateOf(false) }

    val kaomojiKeyName = remember(kaomojiKeyCode, kaomojiScanCode) {
        PreferencesManager.formatKaomojiKeyName(kaomojiKeyCode, kaomojiScanCode)
    }
    val kaomojiKeyBadge = remember(kaomojiKeyCode, kaomojiScanCode) {
        PreferencesManager.formatKaomojiKeyBadge(kaomojiKeyCode, kaomojiScanCode)
    }
    var configuredRemapBadges by remember {
        mutableStateOf(HardwareRemapManager.getConfiguredKeyBadges(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val remapPrefs = HardwareRemapManager.getPrefs(context)
        val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            configuredRemapBadges = HardwareRemapManager.getConfiguredKeyBadges(context)
        }
        remapPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                configuredRemapBadges = HardwareRemapManager.getConfiguredKeyBadges(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            remapPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isDpadWrapAround by PreferencesManager.isDpadWrapAround.collectAsState()
    val isDpadBoundaryHaptics by PreferencesManager.isDpadBoundaryHaptics.collectAsState()
    val keypadSoundMode by PreferencesManager.keypadSoundMode.collectAsState()
    val launcherAudioStream by PreferencesManager.launcherAudioStream.collectAsState()
    val launcherSoundVolume by PreferencesManager.launcherSoundVolume.collectAsState()
    val isLidLedEnabled by PreferencesManager.isLidLedEnabled.collectAsState()
    val isLidLedChargingEnabled by PreferencesManager.isLidLedChargingEnabled.collectAsState()
    val lidLedColor by PreferencesManager.lidLedColor.collectAsState()
    var showLidLedColorPicker by remember { mutableStateOf(false) }
    val isMediaVolumeLockEnabled by PreferencesManager.isMediaVolumeLockEnabled.collectAsState()
    val isSystemMonitorEnabled by PreferencesManager.isSystemMonitorEnabled.collectAsState()
    val systemMonitorInterval by PreferencesManager.systemMonitorInterval.collectAsState()

    // Dynamic item list matching LazyColumn visual card order
    val items = remember(onCreateFolderClick, isSystemMonitorEnabled, isLidLedEnabled) {
        buildList {
            if (onCreateFolderClick != null) {
                add("create_folder")
            }
            add("folder_grid")
            add("t9_search")
            add("hidden_apps")
            add("keypad_remapper")
            add("kaomoji_trigger")
            add("kaomoji_press_mode")
            add("dpad_wrap")
            add("boundary_haptics")
            add("keypad_sound")
            add("audio_stream")
            add("sound_volume")
            add("media_volume_lock")
            add("lid_led_notification")
            if (isLidLedEnabled) {
                add("lid_led_color")
                add("lid_led_charging")
            }
            add("system_monitor")
            if (isSystemMonitorEnabled) {
                add("monitor_interval")
            }
        }
    }

    val totalItems = items.size

    val targetCardIndex = remember(focusedIndex, items) {
        when (items.getOrNull(focusedIndex)) {
            "create_folder", "folder_grid" -> 0
            "t9_search", "hidden_apps" -> 1
            "keypad_remapper", "kaomoji_trigger", "kaomoji_press_mode", "dpad_wrap", "boundary_haptics", "keypad_sound", "audio_stream", "sound_volume", "media_volume_lock" -> 2
            "lid_led_notification", "lid_led_color", "lid_led_charging" -> 3
            "system_monitor", "monitor_interval" -> 4
            else -> 0
        }
    }

    LaunchedEffect(targetCardIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || targetCardIndex in visible) return@LaunchedEffect
        listState.animateScrollToItem(targetCardIndex)
    }

    LaunchedEffect(isCapturingKaomojiKey) {
        if (!isCapturingKaomojiKey) {
            focusRequester.requestFocus()
        }
    }

    requestFocusSafely(focusRequester)

    if (isCapturingKaomojiKey) {
        KaomojiKeyCaptureDialog(
            currentKeyCode = kaomojiKeyCode,
            currentScanCode = kaomojiScanCode,
            isLongPress = kaomojiLongPress,
            onDismiss = { isCapturingKaomojiKey = false },
            onAssignButton = { newKeyCode, newScanCode ->
                PreferencesManager.setKaomojiTriggerButton(context, newKeyCode, newScanCode)
            },
            onToggleLongPress = { newLongPress ->
                PreferencesManager.setKaomojiTriggerLongPress(context, newLongPress)
            },
            onResetDefault = {
                PreferencesManager.resetKaomojiTrigger(context)
            }
        )
    }

    fun activateBehaviorItem(index: Int) {
        when (items.getOrNull(index)) {
            "create_folder" -> onCreateFolderClick?.invoke()
            "folder_grid" -> PreferencesManager.setFolderGridMode(context, !isFolderGridMode)
            "t9_search" -> PreferencesManager.setT9SearchEnabled(context, !isT9SearchEnabled)
            "keypad_remapper" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                onOpenHardwareButtons?.invoke()
            }
            "kaomoji_trigger" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                isCapturingKaomojiKey = true
            }
            "kaomoji_press_mode" -> {
                PreferencesManager.setKaomojiTriggerLongPress(context, !kaomojiLongPress)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            "dpad_wrap" -> PreferencesManager.setDpadWrapAround(context, !isDpadWrapAround)
            "boundary_haptics" -> PreferencesManager.setDpadBoundaryHaptics(context, !isDpadBoundaryHaptics)
            "keypad_sound" -> {
                val nextMode = when (keypadSoundMode) {
                    "kawaii" -> "retro"
                    "retro" -> "off"
                    else -> "kawaii"
                }
                PreferencesManager.setKeypadSoundMode(context, nextMode)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            "audio_stream" -> {
                val next = if (launcherAudioStream == PreferencesManager.AUDIO_STREAM_MEDIA) {
                    PreferencesManager.AUDIO_STREAM_NOTIFICATION
                } else {
                    PreferencesManager.AUDIO_STREAM_MEDIA
                }
                PreferencesManager.setLauncherAudioStream(context, next)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            "sound_volume" -> {
                val next = if (launcherSoundVolume >= 100) 0 else launcherSoundVolume + 10
                PreferencesManager.setLauncherSoundVolume(context, next)
                SoundEffectManager.play(context, SoundEffect.CLICK)
            }
            "media_volume_lock" -> {
                val next = !isMediaVolumeLockEnabled
                if (next && !HardwareRemapManager.isAccessibilityServiceEnabled(context)) {
                    Toast.makeText(
                        context,
                        "Please enable Mochibun in Accessibility Services ♡",
                        Toast.LENGTH_LONG
                    ).show()
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                } else {
                    PreferencesManager.setMediaVolumeLockEnabled(context, next)
                    if (next) {
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    } else {
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                }
            }
            "lid_led_notification" -> {
                val next = !isLidLedEnabled
                PreferencesManager.setLidLedEnabled(context, next)
                if (next) LidLedManager.updateLedState() else LidLedManager.clearAllLeds()
            }
            "lid_led_color" -> {
                showLidLedColorPicker = true
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            "lid_led_charging" -> {
                val next = !isLidLedChargingEnabled
                PreferencesManager.setLidLedChargingEnabled(context, next)
                LidLedManager.updateLedState()
            }
            "hidden_apps" -> onOpenHiddenApps?.invoke()
            "system_monitor" -> PreferencesManager.setSystemMonitorEnabled(context, !isSystemMonitorEnabled)
            "monitor_interval" -> {
                val nextInterval = when (systemMonitorInterval) {
                    1 -> 2
                    2 -> 3
                    3 -> 5
                    else -> 1
                }
                PreferencesManager.setSystemMonitorInterval(context, nextInterval)
            }
        }
    }

    val jumpNum: (String) -> Int? = { id ->
        val idx = items.indexOf(id)
        if (idx in 0..8) idx + 1 else null
    }

    SettingsScaffold(
        title = "Behavior",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (isCapturingKaomojiKey) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onBack()
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        activateBehaviorItem(focusedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..items.size.coerceAtMost(9)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        activateBehaviorItem(targetIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        focusedIndex = if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                        } else {
                            if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                        }
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                when (items.getOrNull(focusedIndex)) {
                                    "kaomoji_press_mode" -> {
                                        PreferencesManager.setKaomojiTriggerLongPress(context, !kaomojiLongPress)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "keypad_sound" -> {
                                        val nextMode = when (keypadSoundMode) {
                                            "kawaii" -> "off"
                                            "retro" -> "kawaii"
                                            else -> "retro"
                                        }
                                        PreferencesManager.setKeypadSoundMode(context, nextMode)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "audio_stream" -> {
                                        val next = if (launcherAudioStream == PreferencesManager.AUDIO_STREAM_MEDIA) {
                                            PreferencesManager.AUDIO_STREAM_NOTIFICATION
                                        } else {
                                            PreferencesManager.AUDIO_STREAM_MEDIA
                                        }
                                        PreferencesManager.setLauncherAudioStream(context, next)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "sound_volume" -> {
                                        val next = (launcherSoundVolume - 10).coerceIn(0, 100)
                                        PreferencesManager.setLauncherSoundVolume(context, next)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "lid_led_color" -> {
                                        val presets = LidLedManager.PRESET_COLORS
                                        val curIdx = presets.indexOfFirst { it.id.equals(lidLedColor, ignoreCase = true) }
                                        val nextIdx = if (curIdx <= 0) presets.size - 1 else curIdx - 1
                                        PreferencesManager.setLidLedColor(context, presets[nextIdx].id)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "monitor_interval" -> {
                                        val nextInterval = when (systemMonitorInterval) {
                                            5 -> 3
                                            3 -> 2
                                            2 -> 1
                                            else -> 5
                                        }
                                        PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                when (items.getOrNull(focusedIndex)) {
                                    "kaomoji_press_mode" -> {
                                        PreferencesManager.setKaomojiTriggerLongPress(context, !kaomojiLongPress)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "keypad_sound" -> {
                                        val nextMode = when (keypadSoundMode) {
                                            "kawaii" -> "retro"
                                            "retro" -> "off"
                                            else -> "kawaii"
                                        }
                                        PreferencesManager.setKeypadSoundMode(context, nextMode)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "audio_stream" -> {
                                        val next = if (launcherAudioStream == PreferencesManager.AUDIO_STREAM_MEDIA) {
                                            PreferencesManager.AUDIO_STREAM_NOTIFICATION
                                        } else {
                                            PreferencesManager.AUDIO_STREAM_MEDIA
                                        }
                                        PreferencesManager.setLauncherAudioStream(context, next)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "sound_volume" -> {
                                        val next = (launcherSoundVolume + 10).coerceIn(0, 100)
                                        PreferencesManager.setLauncherSoundVolume(context, next)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "lid_led_color" -> {
                                        val presets = LidLedManager.PRESET_COLORS
                                        val curIdx = presets.indexOfFirst { it.id.equals(lidLedColor, ignoreCase = true) }
                                        val nextIdx = if (curIdx == -1 || curIdx >= presets.size - 1) 0 else curIdx + 1
                                        PreferencesManager.setLidLedColor(context, presets[nextIdx].id)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "monitor_interval" -> {
                                        val nextInterval = when (systemMonitorInterval) {
                                            1 -> 2
                                            2 -> 3
                                            3 -> 5
                                            else -> 1
                                        }
                                        PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                }
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 64.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsCard(categoryTitle = "Folder Options") {
                    if (onCreateFolderClick != null) {
                        SettingsNavigationRow(
                            title = "Create New Folder",
                            subtitle = "Group favorite apps into a kawaii folder",
                            icon = "📁",
                            isFocused = items.getOrNull(focusedIndex) == "create_folder",
                            jumpNumber = jumpNum("create_folder"),
                            onClick = { onCreateFolderClick() }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    SettingsToggleRow(
                        title = "2×2 Folder Grid",
                        subtitle = if (isFolderGridMode) "2×2 Grid layout active" else "Compact single-column list active",
                        checked = isFolderGridMode,
                        isFocused = items.getOrNull(focusedIndex) == "folder_grid",
                        jumpNumber = jumpNum("folder_grid"),
                        onCheckedChange = { PreferencesManager.setFolderGridMode(context, it) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "App Drawer") {
                    SettingsToggleRow(
                        title = "T9 Keyboard Search",
                        subtitle = "Filter applications using physical numeric keypad",
                        checked = isT9SearchEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "t9_search",
                        jumpNumber = jumpNum("t9_search"),
                        onCheckedChange = { PreferencesManager.setT9SearchEnabled(context, it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Hidden Apps",
                        subtitle = "Manage hidden launcher packages",
                        icon = "🙈",
                        isFocused = items.getOrNull(focusedIndex) == "hidden_apps",
                        jumpNumber = jumpNum("hidden_apps"),
                        onClick = { onOpenHiddenApps?.invoke() }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Keypad & D-Pad") {
                    val remapTitle = if (configuredRemapBadges.isEmpty()) {
                        "Keypad Remapper"
                    } else {
                        "Keypad Remapper [${configuredRemapBadges.size} Mapped]"
                    }
                    val remapSubtitle = if (configuredRemapBadges.isEmpty()) {
                        "Press any physical button to assign actions or app shortcuts"
                    } else {
                        "Active: ${configuredRemapBadges.joinToString(", ")} • Tap to press & remap any key"
                    }
                    SettingsNavigationRow(
                        title = remapTitle,
                        subtitle = remapSubtitle,
                        icon = "🎮",
                        isFocused = items.getOrNull(focusedIndex) == "keypad_remapper",
                        jumpNumber = jumpNum("keypad_remapper"),
                        onClick = { onOpenHardwareButtons?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Kaomoji Trigger Key [$kaomojiKeyBadge]",
                        subtitle = "Assigned: $kaomojiKeyName • Tap to press & assign any button",
                        icon = "✨",
                        isFocused = items.getOrNull(focusedIndex) == "kaomoji_trigger",
                        jumpNumber = jumpNum("kaomoji_trigger"),
                        onClick = { isCapturingKaomojiKey = true }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Kaomoji Trigger Mode",
                        subtitle = if (kaomojiLongPress) "Hold $kaomojiKeyName to open Kaomoji Wheel" else "Single press $kaomojiKeyName opens Kaomoji Wheel",
                        valueDisplay = if (kaomojiLongPress) "Hold" else "Single Tap",
                        isFocused = items.getOrNull(focusedIndex) == "kaomoji_press_mode",
                        jumpNumber = jumpNum("kaomoji_press_mode"),
                        onDecrement = {
                            PreferencesManager.setKaomojiTriggerLongPress(context, !kaomojiLongPress)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            PreferencesManager.setKaomojiTriggerLongPress(context, !kaomojiLongPress)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "D-Pad Wrap-Around",
                        subtitle = if (isDpadWrapAround) "Navigation loops top to bottom & left to right" else "Navigation stops at list boundaries",
                        checked = isDpadWrapAround,
                        isFocused = items.getOrNull(focusedIndex) == "dpad_wrap",
                        jumpNumber = jumpNum("dpad_wrap"),
                        onCheckedChange = { PreferencesManager.setDpadWrapAround(context, it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Boundary Haptics",
                        subtitle = if (isDpadBoundaryHaptics) "Subtle buzz when hitting edge limits" else "Silent boundaries",
                        checked = isDpadBoundaryHaptics,
                        isFocused = items.getOrNull(focusedIndex) == "boundary_haptics",
                        jumpNumber = jumpNum("boundary_haptics"),
                        onCheckedChange = { PreferencesManager.setDpadBoundaryHaptics(context, it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val soundModeLabel = when (keypadSoundMode) {
                        "kawaii" -> "Kawaii Pop"
                        "retro" -> "Retro Keitai"
                        else -> "Muted / Off"
                    }
                    val soundModeSubtitle = when (keypadSoundMode) {
                        "kawaii" -> "Sweet 8-bit resonant chimes & bubbles"
                        "retro" -> "Crisp tactile mechanical switch clicks"
                        else -> "Silent keypad navigation"
                    }
                    SettingsStepperRow(
                        title = "Keypad Audio FX",
                        subtitle = soundModeSubtitle,
                        valueDisplay = soundModeLabel,
                        isFocused = items.getOrNull(focusedIndex) == "keypad_sound",
                        jumpNumber = jumpNum("keypad_sound"),
                        onDecrement = {
                            val nextMode = when (keypadSoundMode) {
                                "kawaii" -> "off"
                                "retro" -> "kawaii"
                                else -> "retro"
                            }
                            PreferencesManager.setKeypadSoundMode(context, nextMode)
                            if (nextMode != "off") {
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                        },
                        onIncrement = {
                            val nextMode = when (keypadSoundMode) {
                                "kawaii" -> "retro"
                                "retro" -> "off"
                                else -> "kawaii"
                            }
                            PreferencesManager.setKeypadSoundMode(context, nextMode)
                            if (nextMode != "off") {
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val audioStreamLabel = if (launcherAudioStream == PreferencesManager.AUDIO_STREAM_MEDIA) {
                        "Media"
                    } else {
                        "Notification"
                    }
                    val audioStreamSubtitle = if (launcherAudioStream == PreferencesManager.AUDIO_STREAM_MEDIA) {
                        "Media channel (plays in silent / vibrate mode)"
                    } else {
                        "Notification channel (muted by silent mode)"
                    }
                    SettingsStepperRow(
                        title = "Audio Routing Stream",
                        subtitle = audioStreamSubtitle,
                        valueDisplay = audioStreamLabel,
                        isFocused = items.getOrNull(focusedIndex) == "audio_stream",
                        jumpNumber = jumpNum("audio_stream"),
                        onDecrement = {
                            val next = if (launcherAudioStream == PreferencesManager.AUDIO_STREAM_MEDIA) {
                                PreferencesManager.AUDIO_STREAM_NOTIFICATION
                            } else {
                                PreferencesManager.AUDIO_STREAM_MEDIA
                            }
                            PreferencesManager.setLauncherAudioStream(context, next)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            val next = if (launcherAudioStream == PreferencesManager.AUDIO_STREAM_MEDIA) {
                                PreferencesManager.AUDIO_STREAM_NOTIFICATION
                            } else {
                                PreferencesManager.AUDIO_STREAM_MEDIA
                            }
                            PreferencesManager.setLauncherAudioStream(context, next)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Keypad Audio Volume",
                        subtitle = "Master volume for keypad & mascot sounds",
                        valueDisplay = "$launcherSoundVolume%",
                        isFocused = items.getOrNull(focusedIndex) == "sound_volume",
                        jumpNumber = jumpNum("sound_volume"),
                        onDecrement = {
                            val next = (launcherSoundVolume - 10).coerceIn(0, 100)
                            PreferencesManager.setLauncherSoundVolume(context, next)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            val next = if (launcherSoundVolume >= 100) 0 else launcherSoundVolume + 10
                            PreferencesManager.setLauncherSoundVolume(context, next)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Lock Volume Keys to Media",
                        subtitle = if (isMediaVolumeLockEnabled) "Physical volume buttons adjust media only & keep call vol max" else "System default volume behavior",
                        checked = isMediaVolumeLockEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "media_volume_lock",
                        jumpNumber = jumpNum("media_volume_lock"),
                        onCheckedChange = { enabled ->
                            if (enabled && !HardwareRemapManager.isAccessibilityServiceEnabled(context)) {
                                Toast.makeText(
                                    context,
                                    "Please enable Mochibun in Accessibility Services ♡",
                                    Toast.LENGTH_LONG
                                ).show()
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            } else {
                                PreferencesManager.setMediaVolumeLockEnabled(context, enabled)
                                if (enabled) {
                                    SoundEffectManager.play(context, SoundEffect.SELECT)
                                } else {
                                    SoundEffectManager.play(context, SoundEffect.NAV)
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Clamshell & Exterior LED") {
                    SettingsToggleRow(
                        title = "Lid Notification LED",
                        subtitle = if (isLidLedEnabled) "Pulse exterior breathing LED when lid is closed" else "Exterior notification LED disabled",
                        checked = isLidLedEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "lid_led_notification",
                        jumpNumber = jumpNum("lid_led_notification"),
                        onCheckedChange = {
                            PreferencesManager.setLidLedEnabled(context, it)
                            if (it) LidLedManager.updateLedState() else LidLedManager.clearAllLeds()
                        }
                    )
                    if (isLidLedEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val colorLabel = LidLedManager.getColorLabel(lidLedColor)
                        SettingsStepperRow(
                            title = "Lid LED Color",
                            subtitle = "Exterior light accent (Press Center for palette)",
                            valueDisplay = colorLabel,
                            isFocused = items.getOrNull(focusedIndex) == "lid_led_color",
                            jumpNumber = jumpNum("lid_led_color"),
                            onClick = {
                                showLidLedColorPicker = true
                                SoundEffectManager.play(context, SoundEffect.SELECT)
                            },
                            onDecrement = {
                                val presets = LidLedManager.PRESET_COLORS
                                val curIdx = presets.indexOfFirst { it.id.equals(lidLedColor, ignoreCase = true) }
                                val nextIdx = if (curIdx <= 0) presets.size - 1 else curIdx - 1
                                PreferencesManager.setLidLedColor(context, presets[nextIdx].id)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            },
                            onIncrement = {
                                val presets = LidLedManager.PRESET_COLORS
                                val curIdx = presets.indexOfFirst { it.id.equals(lidLedColor, ignoreCase = true) }
                                val nextIdx = if (curIdx == -1 || curIdx >= presets.size - 1) 0 else curIdx + 1
                                PreferencesManager.setLidLedColor(context, presets[nextIdx].id)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsToggleRow(
                            title = "Lid Charging Indicator",
                            subtitle = if (isLidLedChargingEnabled) "Light exterior LED while charging with lid closed" else "Charging light disabled",
                            checked = isLidLedChargingEnabled,
                            isFocused = items.getOrNull(focusedIndex) == "lid_led_charging",
                            jumpNumber = jumpNum("lid_led_charging"),
                            onCheckedChange = {
                                PreferencesManager.setLidLedChargingEnabled(context, it)
                                LidLedManager.updateLedState()
                            }
                        )
                    }
                }
            }

            item {
                SettingsCard(categoryTitle = "System Monitor Card") {
                    SettingsToggleRow(
                        title = "System Monitor Deck",
                        subtitle = "Display live CPU, RAM, & storage gauges",
                        checked = isSystemMonitorEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "system_monitor",
                        jumpNumber = jumpNum("system_monitor"),
                        onCheckedChange = { PreferencesManager.setSystemMonitorEnabled(context, it) }
                    )
                    if (isSystemMonitorEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsStepperRow(
                            title = "Refresh Interval",
                            subtitle = "Polling frequency (lower = smoother, higher = battery friendly)",
                            valueDisplay = "$systemMonitorInterval sec",
                            isFocused = items.getOrNull(focusedIndex) == "monitor_interval",
                            jumpNumber = jumpNum("monitor_interval"),
                            onDecrement = {
                                val nextInterval = when (systemMonitorInterval) {
                                    5 -> 3
                                    3 -> 2
                                    2 -> 1
                                    else -> 5
                                }
                                PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                            },
                            onIncrement = {
                                val nextInterval = when (systemMonitorInterval) {
                                    1 -> 2
                                    2 -> 3
                                    3 -> 5
                                    else -> 1
                                }
                                PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showLidLedColorPicker) {
        LidLedColorPickerDialog(
            currentColor = lidLedColor,
            onColorSelected = { selected ->
                PreferencesManager.setLidLedColor(context, selected)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            },
            onDismiss = {
                showLidLedColorPicker = false
            }
        )
    }
}

/**
 * Interactive Hardware Key Capture Dialog for assigning any physical button
 * to open the Kaomoji Wheel (modeled after the Hardware Button Remapper's Key Capture dialog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KaomojiKeyCaptureDialog(
    currentKeyCode: Int,
    currentScanCode: Int,
    isLongPress: Boolean,
    onDismiss: () -> Unit,
    onAssignButton: (Int, Int) -> Unit,
    onToggleLongPress: (Boolean) -> Unit,
    onResetDefault: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }

    var isListening by remember { mutableStateOf(true) }
    var capturedKeyCode by remember { mutableIntStateOf(currentKeyCode) }
    var capturedScanCode by remember { mutableIntStateOf(currentScanCode) }
    var statusHint by remember { mutableStateOf<String?>(null) }
    var selectedActionIndex by remember { mutableIntStateOf(0) } // 0: Done, 1: Mode, 2: Listen Again, 3: Reset
    var sawCenterDown by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "kaomoji_key_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kaomoji_pulse_scale"
    )

    requestFocusSafely(focusRequester)

    val displayKeyCode = if (isListening) currentKeyCode else capturedKeyCode
    val displayScanCode = if (isListening) currentScanCode else capturedScanCode
    val physicalKey = remember(displayKeyCode, displayScanCode) {
        PhysicalKey.fromKeyCode(displayKeyCode)
            ?: if (displayScanCode > 0) PhysicalKey.fromScancode(displayScanCode) else null
    }
    val keyName = remember(displayKeyCode, displayScanCode) {
        PreferencesManager.formatKaomojiKeyName(displayKeyCode, displayScanCode)
    }
    val keyBadge = remember(displayKeyCode, displayScanCode) {
        PreferencesManager.formatKaomojiKeyBadge(displayKeyCode, displayScanCode)
    }

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
                        statusHint = null
                        isListening = false
                        selectedActionIndex = 0
                        onAssignButton(resolvedKeyCode, native.scanCode)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    true
                }
            }
        } else {
            MochibunAccessibilityService.keyCaptureListener = null
        }
        onDispose {
            MochibunAccessibilityService.keyCaptureListener = null
        }
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

                if (native.keyCode == KeyEvent.KEYCODE_BACK || native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                if (isListening) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val isNavigationDpad = native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                            isCenterActivateKey(native.keyCode)

                        if (isNavigationDpad) {
                            statusHint = "D-Pad & OK are reserved for launcher navigation ♡"
                            SoundEffectManager.play(context, SoundEffect.BOUNDARY)
                            return@onPreviewKeyEvent true
                        }

                        val resolvedKeyCode = if (native.keyCode == KeyEvent.KEYCODE_UNKNOWN && native.scanCode == 62) {
                            KeyEvent.KEYCODE_F4
                        } else {
                            native.keyCode
                        }
                        capturedKeyCode = resolvedKeyCode
                        capturedScanCode = native.scanCode
                        statusHint = null
                        isListening = false
                        selectedActionIndex = 0
                        onAssignButton(resolvedKeyCode, native.scanCode)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }

                // Post-capture navigation inside the confirmation view
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        when (selectedActionIndex) {
                            0 -> onDismiss()
                            1 -> onToggleLongPress(!isLongPress)
                            2 -> {
                                statusHint = null
                                isListening = true
                            }
                            3 -> {
                                onResetDefault()
                                capturedKeyCode = PreferencesManager.DEFAULT_KAOMOJI_TRIGGER_KEYCODE
                                capturedScanCode = PreferencesManager.DEFAULT_KAOMOJI_TRIGGER_SCANCODE
                                onDismiss()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type == KeyEventType.KeyDown) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            selectedActionIndex = if (selectedActionIndex > 0) selectedActionIndex - 1 else 3
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            selectedActionIndex = if (selectedActionIndex < 3) selectedActionIndex + 1 else 0
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onToggleLongPress(!isLongPress)
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
                        text = "— ♡ Kaomoji Key Capture ♡ —",
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
                        themeTintColor = theme.mascotTintColor,
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
                        text = "Press any button on your keypad to assign it\n(Side Key, Call, Camera, F1–F4, *, #, 0–9, etc.)",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ActiveTheme.surfaceTint.copy(alpha = 0.3f))
                            .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Current:",
                            color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (physicalKey != null) {
                            RetroKeyBadge(key = physicalKey, isFocused = true)
                        } else {
                            RetroKeyBadge(label = keyBadge, isFocused = true)
                        }
                        Text(
                            text = "${if (isLongPress) "Hold" else "Tap"} $keyName",
                            color = ActiveTheme.accentDeep,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    statusHint?.let { hint ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = hint,
                            color = destructiveColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ActiveTheme.container)
                                .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    onResetDefault()
                                    SoundEffectManager.play(context, SoundEffect.SELECT)
                                    onDismiss()
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↺ Default (#)",
                                color = ActiveTheme.accentDeep,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ActiveTheme.surfaceTint.copy(alpha = 0.25f))
                                .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .clickable { onDismiss() }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel (Back)",
                                color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Button Assigned! ♡",
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ActiveTheme.surfaceTint.copy(alpha = 0.4f))
                            .border(1.dp, ActiveTheme.selectionGlow, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (physicalKey != null) {
                            RetroKeyBadge(key = physicalKey, isFocused = true)
                        } else {
                            RetroKeyBadge(label = keyBadge, isFocused = true)
                        }
                        Column {
                            Text(
                                text = keyName,
                                color = ActiveTheme.accentDeep,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val scanHex = "0x" + displayScanCode.toString(16).uppercase().padStart(4, '0')
                            Text(
                                text = "Scan $scanHex ($displayScanCode) • KeyCode #$displayKeyCode",
                                color = ActiveTheme.textPrimary.copy(alpha = 0.6f),
                                fontSize = 9.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action 0: Done / Confirm
                    val isDoneFocused = selectedActionIndex == 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDoneFocused) ActiveTheme.accentDeep else ActiveTheme.container)
                            .border(
                                width = if (isDoneFocused) 1.5.dp else 1.dp,
                                color = if (isDoneFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onDismiss() }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ Done (OK)",
                            color = if (isDoneFocused) Color.White else ActiveTheme.accentDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Action 1: Trigger Mode toggle (Hold vs Single Press)
                    val isModeFocused = selectedActionIndex == 1
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isModeFocused) ActiveTheme.surfaceTint.copy(alpha = 0.45f) else ActiveTheme.container)
                            .border(
                                width = if (isModeFocused) 1.5.dp else 1.dp,
                                color = if (isModeFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onToggleLongPress(!isLongPress) }
                            .padding(vertical = 6.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "◄ Mode: ${if (isLongPress) "Hold (Long-Press)" else "Single Tap"} ►",
                            color = ActiveTheme.accentDeep,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isListenAgainFocused = selectedActionIndex == 2
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isListenAgainFocused) ActiveTheme.surfaceTint.copy(alpha = 0.45f) else ActiveTheme.container)
                                .border(
                                    width = if (isListenAgainFocused) 1.5.dp else 1.dp,
                                    color = if (isListenAgainFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    statusHint = null
                                    isListening = true
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔄 Re-Capture",
                                color = ActiveTheme.accentDeep,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val isResetFocused = selectedActionIndex == 3
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isResetFocused) ActiveTheme.surfaceTint.copy(alpha = 0.45f) else ActiveTheme.container)
                                .border(
                                    width = if (isResetFocused) 1.5.dp else 1.dp,
                                    color = if (isResetFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onResetDefault()
                                    onDismiss()
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↺ Default (#)",
                                color = ActiveTheme.accentDeep,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
