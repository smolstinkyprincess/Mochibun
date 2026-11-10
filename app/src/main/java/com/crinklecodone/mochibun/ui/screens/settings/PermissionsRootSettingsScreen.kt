package com.crinklecodone.mochibun.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
import com.crinklecodone.mochibun.service.VirtualCursorService
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import kotlinx.coroutines.launch

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
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityEnabled by remember { mutableStateOf(HardwareRemapManager.isAccessibilityServiceEnabled(context)) }
    var cursorServiceRunning by remember { mutableStateOf(VirtualCursorService.isServiceRunning) }
    var rootStatus by remember {
        mutableStateOf(if (RootShell.isSuBinaryPresent()) RootStatus.Unknown else RootStatus.NotAvailable)
    }
    var rootRecentsEnabled by remember { mutableStateOf(HardwareRemapManager.isRootRecentsFallbackEnabled(context)) }
    var rootQuickSettingsEnabled by remember { mutableStateOf(PreferencesManager.isRootQuickSettingsEnabled(context)) }
    var watchdogEnabled by remember { mutableStateOf(PreferencesManager.isRootA11yWatchdogEnabled(context)) }
    var healing by remember { mutableStateOf(false) }

    val rowIds = listOf("overlay", "accessibility", "cursor", "root", "recents", "quick_settings", "watchdog", "self_heal")
    val totalItems = rowIds.size

    fun refreshState() {
        overlayGranted = Settings.canDrawOverlays(context)
        accessibilityEnabled = HardwareRemapManager.isAccessibilityServiceEnabled(context)
        cursorServiceRunning = VirtualCursorService.isServiceRunning
        rootRecentsEnabled = HardwareRemapManager.isRootRecentsFallbackEnabled(context)
        rootQuickSettingsEnabled = PreferencesManager.isRootQuickSettingsEnabled(context)
        watchdogEnabled = PreferencesManager.isRootA11yWatchdogEnabled(context)
        if (!RootShell.isSuBinaryPresent()) rootStatus = RootStatus.NotAvailable
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toggleRecents() {
        val enabled = !rootRecentsEnabled
        if (!enabled) {
            HardwareRemapManager.setRootRecentsFallbackEnabled(context, false)
            rootRecentsEnabled = false
        } else if (!RootShell.isSuBinaryPresent()) {
            Toast.makeText(context, "su binary not found. Device is not rooted.", Toast.LENGTH_SHORT).show()
        } else if (rootStatus !is RootStatus.Granted) {
            scope.launch {
                rootStatus = RootStatus.Requesting
                rootStatus = RootShell.requestRoot()
                if (rootStatus is RootStatus.Granted) {
                    HardwareRemapManager.setRootRecentsFallbackEnabled(context, true)
                    rootRecentsEnabled = true
                } else {
                    Toast.makeText(context, "Root access denied in Magisk.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            HardwareRemapManager.setRootRecentsFallbackEnabled(context, true)
            rootRecentsEnabled = true
        }
    }

    fun toggleQuickSettings() {
        rootQuickSettingsEnabled = !rootQuickSettingsEnabled
        PreferencesManager.setRootQuickSettingsEnabled(context, rootQuickSettingsEnabled)
    }

    fun toggleWatchdog() {
        watchdogEnabled = !watchdogEnabled
        PreferencesManager.setRootA11yWatchdogEnabled(context, watchdogEnabled)
        if (watchdogEnabled) {
            RootAccessibilityWatchdog.checkAsync(context, force = true) { refreshState() }
        }
    }

    fun requestRoot() {
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

    fun selfHeal() {
        if (!RootShell.isSuBinaryPresent()) {
            Toast.makeText(context, "No su binary detected on device", Toast.LENGTH_SHORT).show()
            return
        }
        healing = true
        RootAccessibilityWatchdog.checkAsync(context, force = true) { result ->
            healing = false
            refreshState()
            val message = when (result) {
                is WatchdogResult.Healthy -> "Services are healthy and running ♡"
                is WatchdogResult.Healed -> "Services successfully repaired! ♡"
                is WatchdogResult.Failed -> "Repair failed: ${result.reason}"
                is WatchdogResult.NoRoot -> "Root access required to self-heal"
                is WatchdogResult.Disabled -> "Watchdog is disabled"
            }
            Toast.makeText(context, message, if (result is WatchdogResult.Failed) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    fun activate(index: Int) {
        when (rowIds.getOrNull(index)) {
            "overlay" -> context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "accessibility" -> context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "cursor" -> {
                if (!VirtualCursorService.isServiceRunning) VirtualCursorService.start(context)
                else VirtualCursorService.toggleCursor()
                refreshState()
            }
            "root" -> requestRoot()
            "recents" -> toggleRecents()
            "quick_settings" -> toggleQuickSettings()
            "watchdog" -> toggleWatchdog()
            "self_heal" -> selfHeal()
        }
    }

    fun adjust(index: Int): Boolean {
        when (rowIds.getOrNull(index)) {
            "recents" -> toggleRecents()
            "quick_settings" -> toggleQuickSettings()
            "watchdog" -> toggleWatchdog()
            else -> return false
        }
        return true
    }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + totalItems) % totalItems
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    LaunchedEffect(focusedIndex) {
        val cardIndex = if (focusedIndex <= 2) 0 else 1
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || cardIndex in visible) return@LaunchedEffect
        listState.animateScrollToItem(cardIndex)
    }

    requestFocusSafely(focusRequester)
    SettingsScaffold(
        title = "Permissions & Superuser",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
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
                        activate(focusedIndex)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        focusedIndex = digit - 1
                        activate(focusedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (!adjust(focusedIndex)) {
                            stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 64.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsCard(categoryTitle = "System Overlay & Accessibility") {
                    SettingsNavigationRow(
                        title = "Overlay Window Permission",
                        subtitle = if (overlayGranted) "Granted • Display over other apps" else "Missing • Open Android permission settings",
                        icon = if (overlayGranted) "✓" else "!",
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onClick = { activate(0) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Accessibility Service Status",
                        subtitle = if (accessibilityEnabled) "Enabled" else "Disabled • Open Android Accessibility settings",
                        icon = if (accessibilityEnabled) "✓" else "!",
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onClick = { activate(1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsInfoRow(
                        label = "Virtual Cursor Foreground",
                        value = if (cursorServiceRunning) "Running" else "Stopped"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsInfoRow(
                        label = "LSPosed Hooks",
                        value = "Enable system_server and SystemUI hooks in LSPosed"
                    )
                }
            }
            item {
                SettingsCard(categoryTitle = "Magisk Root & Superuser") {
                    val rootLabel = when (rootStatus) {
                        is RootStatus.Granted -> "Granted • libsu root shell authorized"
                        is RootStatus.Denied -> "Denied • Authorize in Magisk"
                        is RootStatus.Requesting -> "Requesting Magisk root permission…"
                        is RootStatus.NotAvailable -> "Unavailable • su binary not found"
                        else -> "Press OK to request or verify Magisk root access"
                    }
                    SettingsNavigationRow(
                        title = "Magisk Root Access",
                        subtitle = rootLabel,
                        icon = if (rootStatus is RootStatus.Granted) "✓" else "⚡",
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onClick = { activate(3) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Root-Shell Recents Fallback (QuickSwitch)",
                        subtitle = "Use root shell if standard Recents activation fails",
                        checked = rootRecentsEnabled,
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
                        onCheckedChange = { toggleRecents() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Root Quick Settings Toggles",
                        subtitle = "Toggle Wi-Fi and Bluetooth through root shell",
                        checked = rootQuickSettingsEnabled,
                        isFocused = focusedIndex == 5,
                        jumpNumber = 6,
                        onCheckedChange = { toggleQuickSettings() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "A11y Root Watchdog",
                        subtitle = "Repair accessibility services after sleep or wake",
                        checked = watchdogEnabled,
                        isFocused = focusedIndex == 6,
                        jumpNumber = 7,
                        onCheckedChange = { toggleWatchdog() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Self-heal Services Now",
                        subtitle = if (healing) "Checking and restoring services…" else "Verify and rebind services using root",
                        icon = if (healing) "⏳" else "♡",
                        isFocused = focusedIndex == 7,
                        jumpNumber = 8,
                        onClick = { selfHeal() }
                    )
                }
            }
        }
    }
}
