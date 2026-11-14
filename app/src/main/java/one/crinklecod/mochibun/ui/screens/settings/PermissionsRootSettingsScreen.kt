package one.crinklecod.mochibun.ui.screens.settings

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import one.crinklecod.mochibun.core.shell.RootStatus
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.RootShell
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.HardwareRemapManager
import one.crinklecod.mochibun.service.MochibunAccessibilityService
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.service.MochibunImeStatus
import one.crinklecod.mochibun.service.VirtualCursorService
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import kotlinx.coroutines.launch

/** Rows of the essential (non-root) setup checklist, always visible. */
internal val ESSENTIAL_PERMISSION_ROWS = listOf("home", "ime", "overlay", "a11y", "notifications", "cursor")

/** Rows of the Magisk Root & Superuser card, shown only with root options revealed. */
internal val ROOT_PERMISSION_ROWS = listOf(
    "root", "key_hook", "key_hook_enabled", "sysui_hook_enabled", "recents", "quick_settings", "self_heal"
)

/**
 * Visible rows of the Setup & Diagnostics screen in on-screen order. Focus index = list index and
 * jump number = index + 1, so hiding the root card never leaves a gap.
 */
internal fun permissionsRowIds(showRootOptions: Boolean): List<String> = buildList {
    addAll(ESSENTIAL_PERMISSION_ROWS)
    if (showRootOptions) addAll(ROOT_PERMISSION_ROWS)
    add("show_root")
}

/** Keypad jump for a row: keys 1–9, then 0 for the tenth row (as before). */
internal fun settingsJumpNumber(rows: List<String>, id: String): Int? =
    (rows.indexOf(id) + 1).takeIf { it in 1..10 }

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
    var accessibilityEnabled by remember { mutableStateOf(false) }
    val showRootOptions by PreferencesManager.showRootOptions.collectAsState()
    val suPresent = remember { RootShell.isSuBinaryPresent() }
    var cursorVisible by remember { mutableStateOf(VirtualCursorService.isCursorVisible) }
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

    val rowIds = remember(showRootOptions) { permissionsRowIds(showRootOptions) }
    val totalItems = rowIds.size

    fun isFocused(id: String): Boolean = rowIds.getOrNull(focusedIndex) == id
    fun jump(id: String): Int? = settingsJumpNumber(rowIds, id)

    // Keep focus on a real row when the root card is hidden / revealed
    LaunchedEffect(totalItems) {
        focusedIndex = focusedIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
    }

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
        accessibilityEnabled = MochibunAccessibilityService.isEnabled(context)
        cursorVisible = VirtualCursorService.isCursorVisible
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
                    "am start-foreground-service --user current -n $serviceComponent -a ${VirtualCursorService.ACTION_TOGGLE_CURSOR} --ez ${VirtualCursorService.EXTRA_CURSOR_ACTIVE} true"
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

    fun toggleShowRootOptions() {
        PreferencesManager.setShowRootOptions(context, !showRootOptions)
    }

    fun activatePermissionItem(index: Int) {
        when (rowIds.getOrNull(index)) {
            "home" -> launchSettings(Intent(Settings.ACTION_HOME_SETTINGS))
            "ime" -> openImeSettings()
            "overlay" -> openOverlaySettings()
            "a11y" -> launchSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "show_root" -> toggleShowRootOptions()
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
                } else {
                    val wasVisible = VirtualCursorService.isCursorVisible
                    VirtualCursorService.toggleCursor(context)
                    cursorVisible = !wasVisible
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
            "show_root" -> toggleShowRootOptions()
            else -> return false
        }
        return true
    }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + totalItems) % totalItems
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    // LazyColumn items: [non-root banner], essential card, [root card], advanced card
    val showBanner = !showRootOptions && !suPresent
    LaunchedEffect(focusedIndex, rowIds, showBanner) {
        val id = rowIds.getOrNull(focusedIndex) ?: return@LaunchedEffect
        val bannerOffset = if (showBanner) 1 else 0
        val targetItem = when (id) {
            in ESSENTIAL_PERMISSION_ROWS ->
                if (ESSENTIAL_PERMISSION_ROWS.indexOf(id) < 4) 0 else bannerOffset
            in ROOT_PERMISSION_ROWS -> bannerOffset + 1
            else -> bannerOffset + if (showRootOptions) 2 else 1
        }
        listState.animateScrollToItem(targetItem)
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
            if (showBanner) {
                item {
                    NonRootModeBanner()
                }
            }
            item {
                SettingsCard(categoryTitle = "Essential Setup • Core Launcher & Keypad") {
                    SettingsNavigationRow(
                        title = "Default Home Launcher",
                        subtitle = if (isDefaultHome) "✓ Active" else "! Tap to Enable • choose Mochibun Home",
                        icon = if (isDefaultHome) "✓" else "!",
                        isFocused = isFocused("home"),
                        jumpNumber = jump("home"),
                        onClick = { activatePermissionItem(rowIds.indexOf("home")) }
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
                        isFocused = isFocused("ime"),
                        jumpNumber = jump("ime"),
                        onClick = { activatePermissionItem(rowIds.indexOf("ime")) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Display Over Other Apps",
                        subtitle = if (overlayGranted) "✓ Active" else "! Tap to Enable",
                        icon = if (overlayGranted) "✓" else "!",
                        isFocused = isFocused("overlay"),
                        jumpNumber = jump("overlay"),
                        onClick = { activatePermissionItem(rowIds.indexOf("overlay")) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Non-Root Cursor & Key Service (Optional)",
                        subtitle = when {
                            accessibilityEnabled -> "✓ Active • cursor taps, scroll & F4 toggle"
                            keyHookActive || rootStatus is RootStatus.Granted ->
                                "○ Not needed • root / LSPosed handles the cursor"
                            else -> "Tap to Enable • Accessibility › Mochibun Cursor & Keys"
                        },
                        icon = if (accessibilityEnabled) "✓" else "○",
                        isFocused = isFocused("a11y"),
                        jumpNumber = jump("a11y"),
                        onClick = { activatePermissionItem(rowIds.indexOf("a11y")) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Notification Access (Now Playing Widget)",
                        subtitle = if (notificationAccessGranted) "✓ Active" else "! Tap to Enable",
                        icon = if (notificationAccessGranted) "✓" else "!",
                        isFocused = isFocused("notifications"),
                        jumpNumber = jump("notifications"),
                        onClick = { activatePermissionItem(rowIds.indexOf("notifications")) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Virtual Cursor Service",
                        subtitle = when {
                            !overlayGranted -> "! Overlay permission needed • tap to grant"
                            cursorVisible -> "✓ Cursor on • tap or F4 to hide"
                            else -> "✓ Ready • tap or F4 to show cursor"
                        },
                        icon = if (overlayGranted) "✓" else "○",
                        isFocused = isFocused("cursor"),
                        jumpNumber = jump("cursor"),
                        onClick = { activatePermissionItem(rowIds.indexOf("cursor")) }
                    )
                }
            }
            if (showRootOptions) {
                item {
                    SettingsCard(categoryTitle = "Magisk Root & Superuser") {
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
                            isFocused = isFocused("root"),
                            jumpNumber = jump("root"),
                            onClick = { activatePermissionItem(rowIds.indexOf("root")) }
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
                            isFocused = isFocused("key_hook"),
                            jumpNumber = jump("key_hook"),
                            onClick = { activatePermissionItem(rowIds.indexOf("key_hook")) }
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
                            isFocused = isFocused("key_hook_enabled"),
                            jumpNumber = jump("key_hook_enabled"),
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
                            isFocused = isFocused("sysui_hook_enabled"),
                            jumpNumber = jump("sysui_hook_enabled"),
                            onCheckedChange = { toggleSystemUiHook() }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsToggleRow(
                            title = "Root-Shell Recents Fallback",
                            subtitle = "Use root shell if standard Recents activation fails",
                            checked = rootRecentsEnabled,
                            isFocused = isFocused("recents"),
                            jumpNumber = jump("recents"),
                            onCheckedChange = { toggleRecents() }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsToggleRow(
                            title = "Root Quick Settings Toggles",
                            subtitle = "Toggle Wi-Fi and Bluetooth through root shell",
                            checked = rootQuickSettingsEnabled,
                            isFocused = isFocused("quick_settings"),
                            jumpNumber = jump("quick_settings"),
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
                            isFocused = isFocused("self_heal"),
                            jumpNumber = jump("self_heal"),
                            onClick = { selfHeal() }
                        )
                    }
                }
            }
            item {
                SettingsCard(categoryTitle = "Advanced") {
                    SettingsToggleRow(
                        title = "Show Root & LSPosed Options",
                        subtitle = if (showRootOptions) {
                            "Magisk, LSPosed hooks and outer LED settings are shown"
                        } else {
                            "Reveal Magisk, LSPosed and outer LED settings"
                        },
                        checked = showRootOptions,
                        isFocused = isFocused("show_root"),
                        jumpNumber = jump("show_root"),
                        onCheckedChange = { toggleShowRootOptions() }
                    )
                }
            }
        }
    }
}

/** Friendly pastel note shown to unrooted users while root options are hidden. Not focusable. */
@Composable
private fun NonRootModeBanner() {
    SettingsCard(categoryTitle = "Welcome") {
        Text(
            text = "♡ Standard (Non-Root) Mode ♡",
            color = ActiveTheme.accentDeep,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "No root needed: the launcher, T9 search, themes, Kaomoji wheel and the " +
                "Accessibility virtual cursor all work out of the box. Finish the checklist below ♡",
            color = ActiveTheme.textPrimary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
