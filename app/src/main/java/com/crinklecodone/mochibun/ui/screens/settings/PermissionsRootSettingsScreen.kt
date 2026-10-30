package com.crinklecodone.mochibun.ui.screens.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.crinklecodone.mochibun.core.shell.RootStatus
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.core.system.RootAccessibilityWatchdog
import com.crinklecodone.mochibun.core.system.RootShell
import com.crinklecodone.mochibun.core.system.WatchdogResult
import com.crinklecodone.mochibun.data.prefs.ClickMethod
import com.crinklecodone.mochibun.data.prefs.CursorPreferences
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
import com.crinklecodone.mochibun.service.VirtualCursorService
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import kotlinx.coroutines.launch

/**
 * Tier 3.5: Permissions & Root Sub-Screen
 * Centralizes Accessibility Services, Magisk Root Status, Root-Shell Recents Fallback,
 * and Root Cursor Click Dispatch.
 */
@Composable
internal fun PermissionsRootSettingsSubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    var isRemapA11yActive by remember {
        mutableStateOf(HardwareRemapManager.isAccessibilityServiceEnabled(context))
    }
    var isCursorA11yActive by remember { mutableStateOf(false) }
    var rootStatus by remember {
        mutableStateOf<RootStatus>(
            if (!RootShell.isSuBinaryPresent()) RootStatus.NotAvailable else RootStatus.Unknown
        )
    }
    var useRootRecentsFallback by remember {
        mutableStateOf(HardwareRemapManager.isRootRecentsFallbackEnabled(context))
    }
    var clickMethod by remember { mutableStateOf(CursorPreferences.getClickMethod(context)) }
    var isWatchdogEnabled by remember {
        mutableStateOf(PreferencesManager.isRootA11yWatchdogEnabled(context))
    }
    var isHealingServices by remember { mutableStateOf(false) }
    var isRootQuickSettingsEnabled by remember {
        mutableStateOf(PreferencesManager.isRootQuickSettingsEnabled(context))
    }

    fun refreshPermissionsState() {
        isRemapA11yActive = HardwareRemapManager.isAccessibilityServiceEnabled(context)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val cursorEnabled = enabledServices?.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name.contains("VirtualCursorService")
        } == true
        isCursorA11yActive = cursorEnabled || VirtualCursorService.isServiceRunning
        useRootRecentsFallback = HardwareRemapManager.isRootRecentsFallbackEnabled(context)
        clickMethod = CursorPreferences.getClickMethod(context)
        isWatchdogEnabled = PreferencesManager.isRootA11yWatchdogEnabled(context)
        isRootQuickSettingsEnabled = PreferencesManager.isRootQuickSettingsEnabled(context)
        if (!RootShell.isSuBinaryPresent()) {
            rootStatus = RootStatus.NotAvailable
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionsState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { refreshPermissionsState() }

    val totalItems = 8 // 0: Remap A11y, 1: Cursor A11y, 2: Magisk Root, 3: Root Recents, 4: Root QS Toggles, 5: Root Click Dispatch, 6: Watchdog, 7: Self-Heal

    val targetCardIndex = remember(focusedIndex) {
        when (focusedIndex) {
            0, 1 -> 0
            2, 3, 4, 5, 6, 7 -> 1
            else -> 0
        }
    }

    LaunchedEffect(targetCardIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || targetCardIndex in visible) return@LaunchedEffect
        listState.animateScrollToItem(targetCardIndex)
    }

    fun toggleRootRecents() {
        val newTarget = !useRootRecentsFallback
        if (newTarget) {
            if (!RootShell.isSuBinaryPresent()) {
                Toast.makeText(context, "su binary not found. Device is not rooted.", Toast.LENGTH_SHORT).show()
            } else if (rootStatus !is RootStatus.Granted) {
                scope.launch {
                    rootStatus = RootStatus.Requesting
                    val res = RootShell.requestRoot()
                    rootStatus = res
                    if (res is RootStatus.Granted) {
                        useRootRecentsFallback = true
                        HardwareRemapManager.setRootRecentsFallbackEnabled(context, true)
                    } else {
                        Toast.makeText(context, "Root access denied in Magisk.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                useRootRecentsFallback = true
                HardwareRemapManager.setRootRecentsFallbackEnabled(context, true)
            }
        } else {
            useRootRecentsFallback = false
            HardwareRemapManager.setRootRecentsFallbackEnabled(context, false)
        }
    }

    fun toggleRootQuickSettings() {
        val next = !isRootQuickSettingsEnabled
        PreferencesManager.setRootQuickSettingsEnabled(context, next)
        isRootQuickSettingsEnabled = next
    }

    fun toggleClickDispatchMethod() {
        val nextMethod = if (clickMethod == ClickMethod.ACCESSIBILITY_GESTURE) {
            ClickMethod.ROOT_INPUT_TAP
        } else {
            ClickMethod.ACCESSIBILITY_GESTURE
        }
        CursorPreferences.setClickMethod(context, nextMethod)
        clickMethod = nextMethod
    }

    fun activatePermissionItem(index: Int) {
        when (index) {
            0, 1 -> {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
            2 -> {
                if (!RootShell.isSuBinaryPresent()) {
                    rootStatus = RootStatus.NotAvailable
                    Toast.makeText(context, "No su binary detected on device", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        rootStatus = RootStatus.Requesting
                        rootStatus = RootShell.requestRoot()
                    }
                }
            }
            3 -> toggleRootRecents()
            4 -> toggleRootQuickSettings()
            5 -> toggleClickDispatchMethod()
            6 -> {
                val next = !isWatchdogEnabled
                PreferencesManager.setRootA11yWatchdogEnabled(context, next)
                isWatchdogEnabled = next
                if (next) {
                    RootAccessibilityWatchdog.checkAsync(context, force = true) {
                        refreshPermissionsState()
                    }
                }
            }
            7 -> {
                if (!RootShell.isSuBinaryPresent()) {
                    Toast.makeText(context, "No su binary detected on device", Toast.LENGTH_SHORT).show()
                } else {
                    isHealingServices = true
                    RootAccessibilityWatchdog.checkAsync(context, force = true) { res ->
                        isHealingServices = false
                        refreshPermissionsState()
                        when (res) {
                            is WatchdogResult.Healthy -> Toast.makeText(context, "Services are healthy and running ♡", Toast.LENGTH_SHORT).show()
                            is WatchdogResult.Healed -> Toast.makeText(context, "Services successfully repaired! ♡", Toast.LENGTH_SHORT).show()
                            is WatchdogResult.Failed -> Toast.makeText(context, "Repair failed: ${res.reason}", Toast.LENGTH_LONG).show()
                            is WatchdogResult.NoRoot -> Toast.makeText(context, "Root access required to self-heal", Toast.LENGTH_SHORT).show()
                            is WatchdogResult.Disabled -> Toast.makeText(context, "Watchdog is disabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Permissions & Root",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
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
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        activatePermissionItem(focusedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        activatePermissionItem(targetIndex)
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
                        if (focusedIndex == 5) {
                            toggleClickDispatchMethod()
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        } else if (focusedIndex == 6) {
                            val next = !isWatchdogEnabled
                            PreferencesManager.setRootA11yWatchdogEnabled(context, next)
                            isWatchdogEnabled = next
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            if (next) {
                                RootAccessibilityWatchdog.checkAsync(context, force = true) {
                                    refreshPermissionsState()
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
                SettingsCard(categoryTitle = "Accessibility Services") {
                    SettingsNavigationRow(
                        title = "Key Remapper & Actions",
                        subtitle = if (isRemapA11yActive) {
                            "✔ Active • Listening for remapped hardware keys"
                        } else {
                            "✖ Inactive • Press OK to enable in Android Settings"
                        },
                        icon = if (isRemapA11yActive) "✓" else "!",
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {}
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Virtual Cursor Service",
                        subtitle = if (isCursorA11yActive) {
                            "✔ Active • Press Side Key/F4 to toggle pointer"
                        } else {
                            "✖ Inactive • Press OK to enable in Android Settings"
                        },
                        icon = if (isCursorA11yActive) "✓" else "!",
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {}
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Magisk Root & Superuser") {
                    val rootStatusLabel = when (rootStatus) {
                        is RootStatus.Granted -> "✔ Granted • libsu root shell authorized"
                        is RootStatus.Denied -> "✖ Denied • Authorize Mochibun in Magisk"
                        is RootStatus.Requesting -> "⏳ Requesting Magisk root permission..."
                        is RootStatus.NotAvailable -> "✖ Not Available • su binary not found"
                        else -> "Press OK to request or verify Magisk root access"
                    }
                    SettingsNavigationRow(
                        title = "Magisk Root Access",
                        subtitle = rootStatusLabel,
                        icon = if (rootStatus is RootStatus.Granted) "✓" else "⚡",
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onClick = {
                            if (!RootShell.isSuBinaryPresent()) {
                                rootStatus = RootStatus.NotAvailable
                            } else {
                                scope.launch {
                                    rootStatus = RootStatus.Requesting
                                    rootStatus = RootShell.requestRoot()
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Root-Shell Recents Fallback",
                        subtitle = if (useRootRecentsFallback) {
                            "Launches Recents directly via root shell if stock fails"
                        } else {
                            "Using standard Android Recents intent"
                        },
                        checked = useRootRecentsFallback,
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onCheckedChange = { toggleRootRecents() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Root Quick Settings Toggles",
                        subtitle = if (isRootQuickSettingsEnabled) {
                            "Toggle Wi-Fi and Bluetooth instantly in-place via root shell"
                        } else {
                            "Opening system dialog panels (standard non-root)"
                        },
                        checked = isRootQuickSettingsEnabled,
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
                        onCheckedChange = { toggleRootQuickSettings() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Cursor Click Dispatch",
                        subtitle = if (clickMethod == ClickMethod.ACCESSIBILITY_GESTURE) {
                            "Accessibility Gesture (Non-root)"
                        } else {
                            "Root Shell Injection (libsu daemon)"
                        },
                        valueDisplay = if (clickMethod == ClickMethod.ROOT_INPUT_TAP) "Root Tap" else "Gesture",
                        isFocused = focusedIndex == 5,
                        jumpNumber = 6,
                        onDecrement = {
                            toggleClickDispatchMethod()
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            toggleClickDispatchMethod()
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "A11y Root Watchdog",
                        subtitle = if (isWatchdogEnabled) {
                            "Auto-rebinds services on sleep & wake if dropped"
                        } else {
                            "Watchdog disabled • manual service restart"
                        },
                        checked = isWatchdogEnabled,
                        isFocused = focusedIndex == 6,
                        jumpNumber = 7,
                        onCheckedChange = { enabled ->
                            PreferencesManager.setRootA11yWatchdogEnabled(context, enabled)
                            isWatchdogEnabled = enabled
                            if (enabled) {
                                RootAccessibilityWatchdog.checkAsync(context, force = true) {
                                    refreshPermissionsState()
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Self-Heal Services Now",
                        subtitle = if (isHealingServices) {
                            "⏳ Verifying & restoring services via Root..."
                        } else {
                            "Force-restores A11y permissions & re-binds daemons"
                        },
                        icon = if (isHealingServices) "⏳" else "♡",
                        isFocused = focusedIndex == 7,
                        jumpNumber = 8,
                        onClick = {
                            if (!RootShell.isSuBinaryPresent()) {
                                Toast.makeText(context, "No su binary detected on device", Toast.LENGTH_SHORT).show()
                            } else {
                                isHealingServices = true
                                RootAccessibilityWatchdog.checkAsync(context, force = true) { res ->
                                    isHealingServices = false
                                    refreshPermissionsState()
                                    when (res) {
                                        is WatchdogResult.Healthy -> Toast.makeText(context, "Services are healthy and running ♡", Toast.LENGTH_SHORT).show()
                                        is WatchdogResult.Healed -> Toast.makeText(context, "Services successfully repaired! ♡", Toast.LENGTH_SHORT).show()
                                        is WatchdogResult.Failed -> Toast.makeText(context, "Repair failed: ${res.reason}", Toast.LENGTH_LONG).show()
                                        is WatchdogResult.NoRoot -> Toast.makeText(context, "Root access required to self-heal", Toast.LENGTH_SHORT).show()
                                        is WatchdogResult.Disabled -> Toast.makeText(context, "Watchdog is disabled", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
