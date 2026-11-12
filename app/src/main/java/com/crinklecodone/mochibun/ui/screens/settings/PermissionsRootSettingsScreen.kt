package com.crinklecodone.mochibun.ui.screens.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.crinklecodone.mochibun.core.shell.RootStatus
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.core.system.RootShell
import com.crinklecodone.mochibun.core.util.MochibunSystemActions
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
import com.crinklecodone.mochibun.service.MochibunImeStatus
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
    var isDefaultHome by remember { mutableStateOf(false) }
    var imeState by remember { mutableStateOf(MochibunImeStatus.State.NOT_ENABLED) }
    var overlayGranted by remember { mutableStateOf(false) }
    var notificationAccessGranted by remember { mutableStateOf(false) }
    var cursorServiceRunning by remember { mutableStateOf(VirtualCursorService.isServiceRunning) }
    var rootStatus by remember {
        mutableStateOf(
            when {
                !RootShell.isRootAvailable() -> RootStatus.NotAvailable
                RootShell.isGranted() == true -> RootStatus.Granted("Root access already granted")
                else -> RootStatus.Unknown
            }
        )
    }
    var keyHookActive by remember { mutableStateOf(false) }
    var keyHookEnabled by remember {
        mutableStateOf(PreferencesManager.isKeyHookEnabled(context))
    }
    var systemUiHookEnabled by remember {
        mutableStateOf(PreferencesManager.isSystemUiHookEnabled(context))
    }
    var keyHookGlobalEnabled by remember { mutableStateOf(true) }
    var systemUiHookGlobalEnabled by remember { mutableStateOf(true) }
    var rootRecentsEnabled by remember {
        mutableStateOf(HardwareRemapManager.isRootRecentsFallbackEnabled(context))
    }
    var rootQuickSettingsEnabled by remember {
        mutableStateOf(PreferencesManager.isRootQuickSettingsEnabled(context))
    }
    var healing by remember { mutableStateOf(false) }

    val rowIds = listOf(
        "home",
        "ime",
        "overlay",
        "notifications",
        "root",
        "key_hook",
        "key_hook_enabled",
        "sysui_hook_enabled",
        "cursor",
        "recents",
        "quick_settings",
        "self_heal"
    )
    val totalItems = rowIds.size

    val readGlobalHookEnabled: (String) -> Boolean = { setting ->
        try {
            Settings.Global.getInt(context.contentResolver, setting, 1) == 1
        } catch (_: Exception) {
            true
        }
    }

    fun refreshState() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        isDefaultHome = context.packageManager.resolveActivity(
            homeIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName == context.packageName
        imeState = MochibunImeStatus.state(context)
        overlayGranted = Settings.canDrawOverlays(context)
        notificationAccessGranted =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        cursorServiceRunning = VirtualCursorService.isServiceRunning
        rootRecentsEnabled = HardwareRemapManager.isRootRecentsFallbackEnabled(context)
        rootQuickSettingsEnabled = PreferencesManager.isRootQuickSettingsEnabled(context)
        keyHookEnabled = PreferencesManager.isKeyHookEnabled(context)
        systemUiHookEnabled = PreferencesManager.isSystemUiHookEnabled(context)
        keyHookGlobalEnabled = readGlobalHookEnabled(PreferencesManager.GLOBAL_HOOK_KEY_ENABLED)
        systemUiHookGlobalEnabled =
            readGlobalHookEnabled(PreferencesManager.GLOBAL_HOOK_SYSUI_ENABLED)
        val heartbeat = try {
            Settings.Global.getLong(
                context.contentResolver,
                PreferencesManager.GLOBAL_KEY_HOOK_ALIVE_ELAPSED,
                0L
            )
        } catch (_: Exception) {
            0L
        }
        val heartbeatBootCount = try {
            Settings.Global.getInt(
                context.contentResolver,
                PreferencesManager.GLOBAL_KEY_HOOK_ALIVE_BOOT_COUNT,
                -1
            )
        } catch (_: Exception) {
            -1
        }
        val currentBootCount = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }
        keyHookActive = keyHookGlobalEnabled &&
            heartbeat > 0L &&
            heartbeat <= SystemClock.elapsedRealtime() &&
            currentBootCount >= 0 &&
            heartbeatBootCount == currentBootCount
        rootStatus = when {
            !RootShell.isRootAvailable() -> RootStatus.NotAvailable
            RootShell.isGranted() == true -> RootStatus.Granted("Root access already granted")
            rootStatus is RootStatus.Granted -> rootStatus
            else -> RootStatus.Unknown
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val hookStatusReceiver = remember(context) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == MochibunSystemActions.ACTION_KEY_HOOK_STATUS) {
                    val appContext = context ?: return
                    val alive = try {
                        Settings.Global.getLong(
                            appContext.contentResolver,
                            PreferencesManager.GLOBAL_KEY_HOOK_ALIVE_ELAPSED,
                            0L
                        )
                    } catch (_: Exception) {
                        0L
                    }
                    val aliveBootCount = try {
                        Settings.Global.getInt(
                            appContext.contentResolver,
                            PreferencesManager.GLOBAL_KEY_HOOK_ALIVE_BOOT_COUNT,
                            -1
                        )
                    } catch (_: Exception) {
                        -1
                    }
                    val currentBootCount = try {
                        Settings.Global.getInt(
                            appContext.contentResolver,
                            Settings.Global.BOOT_COUNT,
                            -1
                        )
                    } catch (_: Exception) {
                        -1
                    }
                    keyHookActive =
                        intent.getBooleanExtra(MochibunSystemActions.EXTRA_KEY_HOOK_ACTIVE, false) &&
                            keyHookGlobalEnabled &&
                            alive > 0L &&
                            alive <= SystemClock.elapsedRealtime() &&
                            currentBootCount >= 0 &&
                            aliveBootCount == currentBootCount
                }
            }
        }
    }
    DisposableEffect(context, lifecycleOwner, hookStatusReceiver) {
        ContextCompat.registerReceiver(
            context,
            hookStatusReceiver,
            IntentFilter(MochibunSystemActions.ACTION_KEY_HOOK_STATUS),
            ContextCompat.RECEIVER_EXPORTED
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
                keyHookActive = false
                context.sendBroadcast(Intent(MochibunSystemActions.ACTION_REQUEST_KEY_HOOK_STATUS))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(hookStatusReceiver)
        }
    }

    fun toggleRecents() {
        val enabled = !rootRecentsEnabled
        if (!enabled) {
            HardwareRemapManager.setRootRecentsFallbackEnabled(context, false)
            rootRecentsEnabled = false
        } else if (!RootShell.isRootAvailable()) {
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

    fun syncHookSwitch(setting: String, enabled: Boolean, hookName: String) {
        if (!RootShell.isRootAvailable()) {
            Toast.makeText(
                context,
                "$hookName saved locally; root access is required to apply it system-wide.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        RootShell.submit("settings put global $setting ${if (enabled) 1 else 0}") { result ->
            if (!result.isSuccess) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "$hookName setting could not be applied; in-app keypad handling remains active.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    if (setting == PreferencesManager.GLOBAL_HOOK_KEY_ENABLED) {
                        keyHookGlobalEnabled = enabled
                    } else {
                        systemUiHookGlobalEnabled = enabled
                        Toast.makeText(
                            context,
                            "SystemUI hook setting saved; restart SystemUI or reboot to apply.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    fun toggleKeyHook() {
        keyHookEnabled = !keyHookEnabled
        PreferencesManager.setKeyHookEnabled(context, keyHookEnabled)
        syncHookSwitch(
            PreferencesManager.GLOBAL_HOOK_KEY_ENABLED,
            keyHookEnabled,
            "Key hook"
        )
    }

    fun toggleSystemUiHook() {
        systemUiHookEnabled = !systemUiHookEnabled
        PreferencesManager.setSystemUiHookEnabled(context, systemUiHookEnabled)
        syncHookSwitch(
            PreferencesManager.GLOBAL_HOOK_SYSUI_ENABLED,
            systemUiHookEnabled,
            "SystemUI hook"
        )
    }

    fun launchSettings(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "Android settings page is unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openOverlaySettings() {
        launchSettings(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    fun openImeSettings() {
        if (imeState == MochibunImeStatus.State.NOT_ENABLED) {
            launchSettings(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } else {
            MochibunImeStatus.showPicker(context)
        }
    }

    fun requestRoot() {
        if (!RootShell.isRootAvailable()) {
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
        if (healing) return
        if (!RootShell.isRootAvailable()) {
            Toast.makeText(context, "No su binary detected on device", Toast.LENGTH_SHORT).show()
            return
        }
        healing = true
        scope.launch {
            try {
                rootStatus = RootStatus.Requesting
                rootStatus = RootShell.requestRoot()
                if (rootStatus !is RootStatus.Granted) {
                    Toast.makeText(context, "Root access denied in Magisk.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val overlayResult = RootShell.execute(
                    "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow"
                )
                if (!overlayResult.isSuccess) {
                    val detail = overlayResult.errorString.ifBlank {
                        overlayResult.outputString.ifBlank { "exit code ${overlayResult.exitCode}" }
                    }
                    Toast.makeText(
                        context,
                        "Overlay permission grant failed: $detail",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val serviceComponent =
                    "${context.packageName}/${VirtualCursorService::class.java.name}"
                if (VirtualCursorService.isServiceRunning) {
                    val stopResult = RootShell.execute(
                        "am stopservice --user current -n $serviceComponent"
                    )
                    if (!stopResult.isSuccess) {
                        val detail = stopResult.errorString.ifBlank {
                            stopResult.outputString.ifBlank { "exit code ${stopResult.exitCode}" }
                        }
                        Toast.makeText(
                            context,
                            "Cursor service stop failed: $detail",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                }

                val startResult = RootShell.execute(
                    "am start-foreground-service --user current -n $serviceComponent"
                )
                if (!startResult.isSuccess) {
                    val detail = startResult.errorString.ifBlank {
                        startResult.outputString.ifBlank { "exit code ${startResult.exitCode}" }
                    }
                    Toast.makeText(
                        context,
                        "Cursor service restart failed: $detail",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                Toast.makeText(
                    context,
                    "Overlay permission granted and cursor service restarted ♡",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                healing = false
                refreshState()
            }
        }
    }

    fun activatePermissionItem(index: Int) {
        when (rowIds.getOrNull(index)) {
            "home" -> launchSettings(Intent(Settings.ACTION_HOME_SETTINGS))
            "ime" -> openImeSettings()
            "overlay" -> openOverlaySettings()
            "notifications" -> launchSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            "root" -> requestRoot()
            "key_hook" -> {
                keyHookActive = false
                context.sendBroadcast(
                    Intent(MochibunSystemActions.ACTION_REQUEST_KEY_HOOK_STATUS)
                )
            }
            "key_hook_enabled" -> toggleKeyHook()
            "sysui_hook_enabled" -> toggleSystemUiHook()
            "cursor" -> {
                if (!Settings.canDrawOverlays(context)) {
                    Toast.makeText(
                        context,
                        "Grant overlay permission to start the cursor service.",
                        Toast.LENGTH_SHORT
                    ).show()
                    openOverlaySettings()
                } else if (!VirtualCursorService.isServiceRunning) {
                    VirtualCursorService.start(context, showCursor = true)
                    cursorServiceRunning = true
                } else {
                    VirtualCursorService.toggleCursor()
                    cursorServiceRunning = VirtualCursorService.isServiceRunning
                }
            }
            "recents" -> toggleRecents()
            "quick_settings" -> toggleQuickSettings()
            "self_heal" -> selfHeal()
        }
    }

    fun adjust(index: Int): Boolean {
        when (rowIds.getOrNull(index)) {
            "key_hook_enabled" -> toggleKeyHook()
            "sysui_hook_enabled" -> toggleSystemUiHook()
            "recents" -> toggleRecents()
            "quick_settings" -> toggleQuickSettings()
            else -> return false
        }
        return true
    }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + totalItems) % totalItems
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    LaunchedEffect(focusedIndex) {
        val targetCardIndex = if (focusedIndex < 4) 0 else 1
        listState.animateScrollToItem(targetCardIndex)
    }

    requestFocusSafely(focusRequester)
    SettingsScaffold(
        title = "Setup & Diagnostics",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .graphicsLayer { clip = false }
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
                        activatePermissionItem(focusedIndex)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                val jumpIndex = when {
                    digit != null && digit in 1..9 -> digit - 1
                    native.keyCode == KeyEvent.KEYCODE_0 && totalItems >= 10 -> 9
                    else -> null
                }
                if (jumpIndex != null && jumpIndex < totalItems) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        focusedIndex = jumpIndex
                        activatePermissionItem(focusedIndex)
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
                SettingsCard(categoryTitle = "Essential Setup • Core Launcher & Keypad") {
                    SettingsNavigationRow(
                        title = "Default Home Launcher",
                        subtitle = if (isDefaultHome) "✓ Active" else "! Tap to Enable • choose Mochibun Home",
                        icon = if (isDefaultHome) "✓" else "!",
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onClick = { activatePermissionItem(0) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Mochibun T9 Keyboard (IME)",
                        subtitle = when (imeState) {
                            MochibunImeStatus.State.DEFAULT -> "✓ Active • default keyboard"
                            MochibunImeStatus.State.ENABLED -> "✓ Enabled • tap to select"
                            MochibunImeStatus.State.NOT_ENABLED -> "! Tap to Enable"
                        },
                        icon = if (imeState == MochibunImeStatus.State.NOT_ENABLED) "!" else "✓",
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onClick = { activatePermissionItem(1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Display Over Other Apps",
                        subtitle = if (overlayGranted) "✓ Active" else "! Tap to Enable",
                        icon = if (overlayGranted) "✓" else "!",
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onClick = { activatePermissionItem(2) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Notification Access",
                        subtitle = if (notificationAccessGranted) "✓ Active" else "! Tap to Enable",
                        icon = if (notificationAccessGranted) "✓" else "!",
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onClick = { activatePermissionItem(3) }
                    )
                }
            }
            item {
                SettingsCard(categoryTitle = "Hardware Hooks & Root Diagnostics") {
                    val rootLabel = when (rootStatus) {
                        is RootStatus.Granted -> "Granted • root shell authorized"
                        is RootStatus.Denied -> "Unrooted — Fallback Mode • access denied"
                        is RootStatus.Requesting -> "Requesting Magisk root permission…"
                        is RootStatus.NotAvailable -> "Unrooted — Fallback Mode"
                        else -> "Root available • tap to verify access"
                    }
                    SettingsNavigationRow(
                        title = "Magisk Root Access",
                        subtitle = rootLabel,
                        icon = if (rootStatus is RootStatus.Granted) "✓" else "⚡",
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
                        onClick = { activatePermissionItem(4) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "LSPosed / Vector Key Hook",
                        subtitle = if (keyHookActive) {
                            "✓ Loaded this boot • system_server hook responded"
                        } else {
                            "! Not active • in-app LSK/RSK and T9 fallback remains available"
                        },
                        icon = if (keyHookActive) "✓" else "!",
                        isFocused = focusedIndex == 5,
                        jumpNumber = 6,
                        onClick = { activatePermissionItem(5) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Enable system_server Key Hook",
                        subtitle = when {
                            !keyHookEnabled && keyHookGlobalEnabled ->
                                "Root sync pending • hook may still intercept hardware keys"
                            !keyHookEnabled -> "Disabled • in-app LSK/RSK and T9 fallback active"
                            !keyHookGlobalEnabled -> "Awaiting root sync • switch may still be active"
                            else -> "Enabled • checked at a 2-second interval"
                        },
                        checked = keyHookEnabled,
                        isFocused = focusedIndex == 6,
                        jumpNumber = 7,
                        onCheckedChange = { toggleKeyHook() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Enable SystemUI Hook",
                        subtitle = when {
                            !systemUiHookEnabled && systemUiHookGlobalEnabled ->
                                "Root sync pending • current hooks remain until SystemUI restarts"
                            !systemUiHookEnabled -> "Disabled • applies after SystemUI restart"
                            !systemUiHookGlobalEnabled -> "Awaiting root sync • SystemUI restart required"
                            else -> "Enabled • installed when SystemUI starts"
                        },
                        checked = systemUiHookEnabled,
                        isFocused = focusedIndex == 7,
                        jumpNumber = 8,
                        onCheckedChange = { toggleSystemUiHook() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Virtual Cursor Service",
                        subtitle = if (cursorServiceRunning) {
                            "✓ Running • tap to toggle cursor"
                        } else {
                            "! Stopped • tap to start"
                        },
                        icon = if (cursorServiceRunning) "✓" else "○",
                        isFocused = focusedIndex == 8,
                        jumpNumber = 9,
                        onClick = { activatePermissionItem(8) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Root-Shell Recents Fallback",
                        subtitle = "Use root shell if standard Recents activation fails",
                        checked = rootRecentsEnabled,
                        isFocused = focusedIndex == 9,
                        jumpNumber = 10,
                        onCheckedChange = { toggleRecents() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Root Quick Settings Toggles",
                        subtitle = "Toggle Wi-Fi and Bluetooth through root shell",
                        checked = rootQuickSettingsEnabled,
                        isFocused = focusedIndex == 10,
                        onCheckedChange = { toggleQuickSettings() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Self-Heal & Restart Services",
                        subtitle = if (healing) {
                            "Granting overlay access and restarting cursor service…"
                        } else {
                            "Grant overlay access and restart cursor service with root"
                        },
                        icon = if (healing) "⏳" else "♡",
                        isFocused = focusedIndex == 11,
                        onClick = { selfHeal() }
                    )
                }
            }
        }
    }
}
