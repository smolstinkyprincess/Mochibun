package one.crinklecod.mochibun.ui.screens.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.WorkerThread
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.crinklecod.mochibun.core.shell.RootStatus
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.KeyHookStatus
import one.crinklecod.mochibun.core.system.RootShell
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.pet.PetBleCompanionServer
import one.crinklecod.mochibun.data.repository.HardwareRemapManager
import one.crinklecod.mochibun.service.MochibunAccessibilityService
import one.crinklecod.mochibun.service.MochibunImeStatus
import one.crinklecod.mochibun.service.VirtualCursorService
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import one.crinklecod.mochibun.ui.theme.ActiveTheme

internal enum class PermissionsRootRowId(val id: String) {
    HOME("home"),
    IME("ime"),
    OVERLAY("overlay"),
    A11Y("a11y"),
    NOTIFICATIONS("notifications"),
    CURSOR("cursor"),
    BLE_COMPANION("ble_companion"),
    SHOW_ROOT("show_root"),
    MAGISK_ROOT_LSPOSED("magisk_root_lsposed")
}

internal enum class MagiskRootLsposedRowId(val id: String) {
    ROOT("root"),
    KEY_HOOK("key_hook"),
    KEY_HOOK_ENABLED("key_hook_enabled"),
    SYSUI_HOOK_ENABLED("sysui_hook_enabled"),
    RECENTS("recents"),
    QUICK_SETTINGS("quick_settings"),
    SELF_HEAL("self_heal")
}

/** Rows of the Magisk Root & LSPosed sub-screen, reachable only when root options are enabled. */
internal val MAGISK_ROOT_LSPOSED_ROW_IDS: List<String> =
    MagiskRootLsposedRowId.entries.map { it.id }

/** Alias preserved for tests checking root row IDs. */
internal val ROOT_PERMISSION_ROWS: List<String> = MAGISK_ROOT_LSPOSED_ROW_IDS

internal fun permissionsRows(showRootOptions: Boolean): List<PermissionsRootRowId> = buildList {
    add(PermissionsRootRowId.HOME)
    add(PermissionsRootRowId.IME)
    add(PermissionsRootRowId.OVERLAY)
    add(PermissionsRootRowId.A11Y)
    add(PermissionsRootRowId.NOTIFICATIONS)
    add(PermissionsRootRowId.CURSOR)
    add(PermissionsRootRowId.BLE_COMPANION)
    add(PermissionsRootRowId.SHOW_ROOT)
    if (showRootOptions) add(PermissionsRootRowId.MAGISK_ROOT_LSPOSED)
}

/**
 * Visible rows of the Permissions & Root parent screen in on-screen order. Focus index = list
 * index and jump number = index + 1 (`1..8` non-root, `1..9` when root options are revealed).
 */
internal fun permissionsRowIds(showRootOptions: Boolean): List<String> =
    permissionsRows(showRootOptions).map { it.id }

/**
 * Status rows of the Setup & Diagnostics screen that need binder / settings-provider IPC:
 * PackageManager.resolveActivity, Settings.Secure / Settings.Global reads, AppOps
 * (canDrawOverlays) and InputMethodManager. Together that is ~8 synchronous IPCs, which used to
 * run on the UI thread during the screen's first frame (the ON_RESUME observer fires
 * synchronously while the effect registers it) and stalled the open on the MT6761.
 */
internal data class PermissionsSystemStatus(
    val isDefaultHome: Boolean,
    val imeState: MochibunImeStatus.State,
    val overlayGranted: Boolean,
    val notificationAccessGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val keyHookGlobalEnabled: Boolean,
    val systemUiHookGlobalEnabled: Boolean,
    /** Hook globally enabled and its heartbeat is from this boot ([KeyHookStatus.readActive]). */
    val keyHookActive: Boolean
)

/** Last status read (process lifetime): seeds the next open's first frame so rows don't flicker. */
@Volatile
private var lastPermissionsSystemStatus: PermissionsSystemStatus? = null

private fun readGlobalHookEnabled(context: Context, setting: String): Boolean = try {
    Settings.Global.getInt(context.contentResolver, setting, 1) == 1
} catch (_: Exception) {
    true
}

@WorkerThread
private fun readPermissionsSystemStatus(context: Context): PermissionsSystemStatus {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val isDefaultHome = try {
        context.packageManager.resolveActivity(
            homeIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName == context.packageName
    } catch (_: Exception) {
        false
    }
    return PermissionsSystemStatus(
        isDefaultHome = isDefaultHome,
        imeState = MochibunImeStatus.state(context),
        overlayGranted = Settings.canDrawOverlays(context),
        notificationAccessGranted =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
        accessibilityEnabled = MochibunAccessibilityService.isEnabled(context),
        keyHookGlobalEnabled = readGlobalHookEnabled(context, PreferencesManager.GLOBAL_HOOK_KEY_ENABLED),
        systemUiHookGlobalEnabled = readGlobalHookEnabled(context, PreferencesManager.GLOBAL_HOOK_SYSUI_ENABLED),
        keyHookActive = KeyHookStatus.readActive(context)
    ).also { lastPermissionsSystemStatus = it }
}

/** Keypad jump for a row: keys 1–9 ([MAX_JUMP_ROWS]). */
internal fun settingsJumpNumber(rows: List<String>, id: String): Int? =
    (rows.indexOf(id) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

internal fun magiskRootLsposedSummarySubtitle(
    rootStatus: RootStatus,
    keyHookActive: Boolean
): String {
    val rootPart = when (rootStatus) {
        is RootStatus.Granted -> "Magisk granted"
        is RootStatus.Denied -> "Root denied"
        is RootStatus.Requesting -> "Checking Magisk…"
        is RootStatus.NotAvailable -> "Unrooted"
        else -> "Root available"
    }
    val hookPart = if (keyHookActive) "LSPosed hook active" else "LSPosed hook inactive"
    return "$rootPart · $hookPart"
}

private fun initialRootStatus(suPresent: Boolean): RootStatus = when {
    !suPresent -> RootStatus.NotAvailable
    RootShell.isGranted() == true -> RootStatus.Granted("Root access already granted")
    else -> RootStatus.Unknown
}

private fun refreshedRootStatus(current: RootStatus): RootStatus = when {
    !RootShell.isRootAvailable() -> RootStatus.NotAvailable
    RootShell.isGranted() == true -> RootStatus.Granted("Root access already granted")
    current is RootStatus.Granted -> current
    else -> RootStatus.Unknown
}

@Composable
internal fun PermissionsRootSettingsScreen(
    onBack: () -> Unit,
    onOpenMagiskRootLsposed: () -> Unit = {},
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val showRootOptions by PreferencesManager.showRootOptions.collectAsState()
    val bleCompanionEnabled by PreferencesManager.bleCompanionEnabled.collectAsState()
    val bleStatus by PetBleCompanionServer.status.collectAsState()
    val rows = remember(showRootOptions) { permissionsRows(showRootOptions) }
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

    // IPC-backed rows start from the last-known status and refresh on Dispatchers.IO (refreshState)
    val seed = remember { lastPermissionsSystemStatus }
    var isDefaultHome by remember { mutableStateOf(seed?.isDefaultHome ?: false) }
    var imeState by remember {
        mutableStateOf(seed?.imeState ?: MochibunImeStatus.lastKnownState ?: MochibunImeStatus.State.NOT_ENABLED)
    }
    var overlayGranted by remember { mutableStateOf(seed?.overlayGranted ?: false) }
    var notificationAccessGranted by remember { mutableStateOf(seed?.notificationAccessGranted ?: false) }
    var accessibilityEnabled by remember { mutableStateOf(seed?.accessibilityEnabled ?: false) }
    // A few stat() calls, no IPC; it decides whether the banner item exists, so it stays synchronous
    // (an async value would shift the LazyColumn items after the first frame). Probed once.
    val suPresent = remember { RootShell.isSuBinaryPresent() }
    var cursorVisible by remember { mutableStateOf(VirtualCursorService.isCursorVisible) }
    var rootStatus by remember { mutableStateOf(initialRootStatus(suPresent)) }
    var keyHookActive by remember { mutableStateOf(seed?.keyHookActive ?: false) }

    fun isRowFocused(row: PermissionsRootRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: PermissionsRootRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    // Keep focus on a real row when the Magisk Root & LSPosed navigation row is hidden / revealed
    LaunchedEffect(rows.size) {
        focusedIndex = focusedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    }

    /**
     * Re-reads every status row. In-process reads (static flags, su stat) stay synchronous;
     * the IPC-backed ones land a frame or two later from [readPermissionsSystemStatus] on IO.
     * [applyHeartbeat] = false keeps keyHookActive untouched: on resume it is reset and re-requested
     * from the hook, whose reply must not be overwritten by a late heartbeat read.
     */
    fun refreshState(applyHeartbeat: Boolean = true) {
        cursorVisible = VirtualCursorService.isCursorVisible
        rootStatus = refreshedRootStatus(rootStatus)
        scope.launch {
            val status = withContext(Dispatchers.IO) { readPermissionsSystemStatus(context) }
            isDefaultHome = status.isDefaultHome
            imeState = status.imeState
            overlayGranted = status.overlayGranted
            notificationAccessGranted = status.notificationAccessGranted
            accessibilityEnabled = status.accessibilityEnabled
            if (applyHeartbeat) keyHookActive = status.keyHookActive
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val hookStatusReceiver = remember(context) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == MochibunSystemActions.ACTION_KEY_HOOK_STATUS) {
                    val appContext = context ?: return
                    val reportedActive =
                        intent.getBooleanExtra(MochibunSystemActions.EXTRA_KEY_HOOK_ACTIVE, false)
                    // Heartbeat + global switch are Settings.Global reads: keep them off the UI thread
                    scope.launch {
                        val alive = withContext(Dispatchers.IO) { KeyHookStatus.readActive(appContext) }
                        keyHookActive = reportedActive && alive
                    }
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
                refreshState(applyHeartbeat = false)
                // Picks up a Nearby devices grant made in App info while we were away
                if (PreferencesManager.bleCompanionEnabled.value) PetBleCompanionServer.requestSync()
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

    fun toggleShowRootOptions() {
        PreferencesManager.setShowRootOptions(context, !showRootOptions)
    }

    // Android 12+ "Nearby devices" prompt; below 12 the Bluetooth permissions are install-time.
    val blePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.isNotEmpty() && results.values.all { it }) {
            PreferencesManager.setBleCompanionEnabled(context, true)
        } else {
            Toast.makeText(
                context,
                "Allow Nearby devices for Mochibun (App info › Permissions) to link MicroMochi.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun toggleBleCompanion() {
        if (bleCompanionEnabled) {
            PreferencesManager.setBleCompanionEnabled(context, false)
            return
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(context, "This phone has no Bluetooth LE.", Toast.LENGTH_SHORT).show()
            return
        }
        val missing = PetBleCompanionServer.missingRuntimePermissions(context)
        if (missing.isEmpty()) {
            PreferencesManager.setBleCompanionEnabled(context, true)
        } else {
            blePermissionLauncher.launch(missing)
        }
    }

    fun activateRow(row: PermissionsRootRowId) {
        when (row) {
            PermissionsRootRowId.HOME -> launchSettings(Intent(Settings.ACTION_HOME_SETTINGS))
            PermissionsRootRowId.IME -> openImeSettings()
            PermissionsRootRowId.OVERLAY -> openOverlaySettings()
            PermissionsRootRowId.A11Y -> launchSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            PermissionsRootRowId.NOTIFICATIONS ->
                launchSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            PermissionsRootRowId.CURSOR -> {
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
            PermissionsRootRowId.BLE_COMPANION -> toggleBleCompanion()
            PermissionsRootRowId.SHOW_ROOT -> toggleShowRootOptions()
            PermissionsRootRowId.MAGISK_ROOT_LSPOSED -> onOpenMagiskRootLsposed()
        }
    }

    fun adjustRow(row: PermissionsRootRowId?): Boolean {
        when (row) {
            PermissionsRootRowId.SHOW_ROOT -> toggleShowRootOptions()
            PermissionsRootRowId.BLE_COMPANION -> toggleBleCompanion()
            else -> return false
        }
        return true
    }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + rows.size) % rows.size
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    // LazyColumn items: [non-root banner], essential card, advanced card
    val showBanner = !showRootOptions && !suPresent
    // Re-aligns the focused row's card on every focus / row-set change.
    ScrollToFocusedItem(
        listState,
        onlyIfHidden = true,
        retriggerKey = {
            (focusedIndex shl 2) or (if (showRootOptions) 2 else 0) or (if (showBanner) 1 else 0)
        }
    ) {
        val bannerOffset = if (showBanner) 1 else 0
        when (rows.getOrNull(focusedIndex)) {
            null -> null
            PermissionsRootRowId.HOME,
            PermissionsRootRowId.IME,
            PermissionsRootRowId.OVERLAY,
            PermissionsRootRowId.A11Y -> 0
            PermissionsRootRowId.NOTIFICATIONS,
            PermissionsRootRowId.CURSOR -> bannerOffset
            PermissionsRootRowId.BLE_COMPANION,
            PermissionsRootRowId.SHOW_ROOT,
            PermissionsRootRowId.MAGISK_ROOT_LSPOSED -> bannerOffset + 1
        }
    }

    requestFocusSafely(focusRequester)
    SettingsScaffold(
        title = "Permissions & Root",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 146,
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
                        rows.getOrNull(focusedIndex)?.let(::activateRow)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        activateRow(rows[targetIndex])
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
                        if (!adjustRow(rows.getOrNull(focusedIndex))) {
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
            contentPadding = PaddingValues(top = 44.dp, bottom = 72.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (showBanner) {
                item(contentType = "permissions_non_root_banner") {
                    NonRootModeBanner()
                }
            }
            item(contentType = "permissions_essential_card") {
                SettingsCard(categoryTitle = "Essential Setup") {
                    SettingsNavigationRow(
                        title = "Default Launcher",
                        subtitle = if (isDefaultHome) "✓ Active" else "! Tap to Enable • choose Mochibun Home",
                        icon = if (isDefaultHome) "✓" else "!",
                        isFocused = isRowFocused(PermissionsRootRowId.HOME),
                        jumpNumber = jumpNumber(PermissionsRootRowId.HOME),
                        onClick = { activateRow(PermissionsRootRowId.HOME) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Mochibun IME",
                        subtitle = when (imeState) {
                            MochibunImeStatus.State.DEFAULT -> "✓ Active • default keyboard"
                            MochibunImeStatus.State.ENABLED -> "✓ Enabled • tap to select"
                            MochibunImeStatus.State.NOT_ENABLED -> "! Tap to Enable"
                        },
                        icon = if (imeState == MochibunImeStatus.State.NOT_ENABLED) "!" else "✓",
                        isFocused = isRowFocused(PermissionsRootRowId.IME),
                        jumpNumber = jumpNumber(PermissionsRootRowId.IME),
                        onClick = { activateRow(PermissionsRootRowId.IME) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Overlay Permission",
                        subtitle = if (overlayGranted) "✓ Active" else "! Tap to Enable",
                        icon = if (overlayGranted) "✓" else "!",
                        isFocused = isRowFocused(PermissionsRootRowId.OVERLAY),
                        jumpNumber = jumpNumber(PermissionsRootRowId.OVERLAY),
                        onClick = { activateRow(PermissionsRootRowId.OVERLAY) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Non-Root Cursor",
                        subtitle = when {
                            accessibilityEnabled -> "✓ Active • cursor taps, scroll & F4 toggle"
                            keyHookActive || rootStatus is RootStatus.Granted ->
                                "○ Not needed • root / LSPosed handles the cursor"
                            else -> "Tap to Enable • Accessibility › Mochibun Cursor & Keys"
                        },
                        icon = if (accessibilityEnabled) "✓" else "○",
                        isFocused = isRowFocused(PermissionsRootRowId.A11Y),
                        jumpNumber = jumpNumber(PermissionsRootRowId.A11Y),
                        onClick = { activateRow(PermissionsRootRowId.A11Y) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Notification Access",
                        subtitle = if (notificationAccessGranted) "✓ Active" else "! Tap to Enable",
                        icon = if (notificationAccessGranted) "✓" else "!",
                        isFocused = isRowFocused(PermissionsRootRowId.NOTIFICATIONS),
                        jumpNumber = jumpNumber(PermissionsRootRowId.NOTIFICATIONS),
                        onClick = { activateRow(PermissionsRootRowId.NOTIFICATIONS) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Cursor Service",
                        subtitle = when {
                            !overlayGranted -> "! Overlay permission needed • tap to grant"
                            cursorVisible -> "✓ Cursor on • tap or F4 to hide"
                            else -> "✓ Ready • tap or F4 to show cursor"
                        },
                        icon = if (overlayGranted) "✓" else "○",
                        isFocused = isRowFocused(PermissionsRootRowId.CURSOR),
                        jumpNumber = jumpNumber(PermissionsRootRowId.CURSOR),
                        onClick = { activateRow(PermissionsRootRowId.CURSOR) }
                    )
                }
            }
            item(contentType = "permissions_advanced_card") {
                SettingsCard(categoryTitle = "Advanced") {
                    SettingsToggleRow(
                        title = "MicroMochi Keychain (BLE)",
                        subtitle = if (!bleCompanionEnabled) {
                            "Share Mochibunny with an e-paper keychain • offline Bluetooth LE"
                        } else {
                            when (bleStatus) {
                                PetBleCompanionServer.Status.CONNECTED -> "✓ Keychain linked"
                                PetBleCompanionServer.Status.ADVERTISING -> "✓ Waiting for keychain to pair"
                                PetBleCompanionServer.Status.BLUETOOTH_OFF -> "○ Turn on Bluetooth to link"
                                PetBleCompanionServer.Status.NEEDS_PERMISSION -> "! Nearby devices permission needed"
                                PetBleCompanionServer.Status.UNSUPPORTED -> "! BLE advertising unsupported"
                                PetBleCompanionServer.Status.OFF -> "Starting…"
                            }
                        },
                        checked = bleCompanionEnabled,
                        isFocused = isRowFocused(PermissionsRootRowId.BLE_COMPANION),
                        jumpNumber = jumpNumber(PermissionsRootRowId.BLE_COMPANION),
                        onCheckedChange = { toggleBleCompanion() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Show Root Options",
                        subtitle = if (showRootOptions) {
                            "Magisk, LSPosed hooks and outer LED settings are shown"
                        } else {
                            "Reveal Magisk, LSPosed and outer LED settings"
                        },
                        checked = showRootOptions,
                        isFocused = isRowFocused(PermissionsRootRowId.SHOW_ROOT),
                        jumpNumber = jumpNumber(PermissionsRootRowId.SHOW_ROOT),
                        onCheckedChange = { toggleShowRootOptions() }
                    )
                    if (showRootOptions) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsNavigationRow(
                            title = "Magisk Root & LSPosed",
                            subtitle = magiskRootLsposedSummarySubtitle(rootStatus, keyHookActive),
                            docomoIndex = 146,
                            isFocused = isRowFocused(PermissionsRootRowId.MAGISK_ROOT_LSPOSED),
                            jumpNumber = jumpNumber(PermissionsRootRowId.MAGISK_ROOT_LSPOSED),
                            onClick = { activateRow(PermissionsRootRowId.MAGISK_ROOT_LSPOSED) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Permissions & Root → Magisk Root & LSPosed sub-screen: root access, LSPosed hook switches,
 * root shell utilities, and service self-heal.
 */
@Composable
internal fun MagiskRootLsposedSettingsScreen(
    onBack: () -> Unit,
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val rows = MagiskRootLsposedRowId.entries
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

    val seed = remember { lastPermissionsSystemStatus }
    val suPresent = remember { RootShell.isSuBinaryPresent() }
    var rootStatus by remember { mutableStateOf(initialRootStatus(suPresent)) }
    var keyHookActive by remember { mutableStateOf(false) }
    var keyHookEnabled by remember {
        mutableStateOf(PreferencesManager.isKeyHookEnabled(context))
    }
    var systemUiHookEnabled by remember {
        mutableStateOf(PreferencesManager.isSystemUiHookEnabled(context))
    }
    var keyHookGlobalEnabled by remember { mutableStateOf(seed?.keyHookGlobalEnabled ?: true) }
    var systemUiHookGlobalEnabled by remember { mutableStateOf(seed?.systemUiHookGlobalEnabled ?: true) }
    var rootRecentsEnabled by remember {
        mutableStateOf(HardwareRemapManager.isRootRecentsFallbackEnabled(context))
    }
    var rootQuickSettingsEnabled by remember {
        mutableStateOf(PreferencesManager.isRootQuickSettingsEnabled(context))
    }
    var healing by remember { mutableStateOf(false) }

    fun isRowFocused(row: MagiskRootLsposedRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: MagiskRootLsposedRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    fun refreshState(applyHeartbeat: Boolean = true) {
        rootRecentsEnabled = HardwareRemapManager.isRootRecentsFallbackEnabled(context)
        rootQuickSettingsEnabled = PreferencesManager.isRootQuickSettingsEnabled(context)
        keyHookEnabled = PreferencesManager.isKeyHookEnabled(context)
        systemUiHookEnabled = PreferencesManager.isSystemUiHookEnabled(context)
        rootStatus = refreshedRootStatus(rootStatus)
        scope.launch {
            val status = withContext(Dispatchers.IO) { readPermissionsSystemStatus(context) }
            keyHookGlobalEnabled = status.keyHookGlobalEnabled
            systemUiHookGlobalEnabled = status.systemUiHookGlobalEnabled
            if (applyHeartbeat) keyHookActive = status.keyHookActive
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val hookStatusReceiver = remember(context) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == MochibunSystemActions.ACTION_KEY_HOOK_STATUS) {
                    val appContext = context ?: return
                    val reportedActive =
                        intent.getBooleanExtra(MochibunSystemActions.EXTRA_KEY_HOOK_ACTIVE, false)
                    scope.launch {
                        val alive = withContext(Dispatchers.IO) { KeyHookStatus.readActive(appContext) }
                        keyHookActive = reportedActive && alive
                    }
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
                refreshState(applyHeartbeat = false)
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

    fun activateRow(row: MagiskRootLsposedRowId) {
        when (row) {
            MagiskRootLsposedRowId.ROOT -> requestRoot()
            MagiskRootLsposedRowId.KEY_HOOK -> {
                keyHookActive = false
                context.sendBroadcast(
                    Intent(MochibunSystemActions.ACTION_REQUEST_KEY_HOOK_STATUS)
                )
            }
            MagiskRootLsposedRowId.KEY_HOOK_ENABLED -> toggleKeyHook()
            MagiskRootLsposedRowId.SYSUI_HOOK_ENABLED -> toggleSystemUiHook()
            MagiskRootLsposedRowId.RECENTS -> toggleRecents()
            MagiskRootLsposedRowId.QUICK_SETTINGS -> toggleQuickSettings()
            MagiskRootLsposedRowId.SELF_HEAL -> selfHeal()
        }
    }

    fun adjustRow(row: MagiskRootLsposedRowId?): Boolean {
        when (row) {
            MagiskRootLsposedRowId.KEY_HOOK_ENABLED -> toggleKeyHook()
            MagiskRootLsposedRowId.SYSUI_HOOK_ENABLED -> toggleSystemUiHook()
            MagiskRootLsposedRowId.RECENTS -> toggleRecents()
            MagiskRootLsposedRowId.QUICK_SETTINGS -> toggleQuickSettings()
            else -> return false
        }
        return true
    }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + rows.size) % rows.size
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    ScrollToFocusedItem(listState) {
        when (rows.getOrNull(focusedIndex)) {
            MagiskRootLsposedRowId.ROOT,
            MagiskRootLsposedRowId.KEY_HOOK,
            MagiskRootLsposedRowId.KEY_HOOK_ENABLED,
            MagiskRootLsposedRowId.SYSUI_HOOK_ENABLED -> 0
            MagiskRootLsposedRowId.RECENTS,
            MagiskRootLsposedRowId.QUICK_SETTINGS,
            MagiskRootLsposedRowId.SELF_HEAL -> 1
            null -> 0
        }
    }

    requestFocusSafely(focusRequester)
    SettingsScaffold(
        title = "Magisk Root & LSPosed",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 146,
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
                        rows.getOrNull(focusedIndex)?.let(::activateRow)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        activateRow(rows[targetIndex])
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
                        if (!adjustRow(rows.getOrNull(focusedIndex))) {
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
            contentPadding = PaddingValues(top = 44.dp, bottom = 72.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(contentType = "permissions_root_hooks_card") {
                SettingsCard(categoryTitle = "Magisk Root & Superuser") {
                    val rootLabel = when (rootStatus) {
                        is RootStatus.Granted -> "Granted • root shell authorized"
                        is RootStatus.Denied -> "Unrooted — Fallback Mode • access denied"
                        is RootStatus.Requesting -> "Requesting Magisk root permission…"
                        is RootStatus.NotAvailable -> "Unrooted — Fallback Mode"
                        else -> "Root available • tap to verify access"
                    }
                    SettingsNavigationRow(
                        title = "Magisk Root",
                        subtitle = rootLabel,
                        icon = "✓",
                        docomoIndex = if (rootStatus is RootStatus.Granted) null else 146,
                        isFocused = isRowFocused(MagiskRootLsposedRowId.ROOT),
                        jumpNumber = jumpNumber(MagiskRootLsposedRowId.ROOT),
                        onClick = { activateRow(MagiskRootLsposedRowId.ROOT) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "LSPosed Key Hook",
                        subtitle = if (keyHookActive) {
                            "✓ Loaded this boot • system_server hook responded"
                        } else {
                            "! Not active • in-app LSK/RSK and T9 fallback remains available"
                        },
                        icon = if (keyHookActive) "✓" else "!",
                        isFocused = isRowFocused(MagiskRootLsposedRowId.KEY_HOOK),
                        jumpNumber = jumpNumber(MagiskRootLsposedRowId.KEY_HOOK),
                        onClick = { activateRow(MagiskRootLsposedRowId.KEY_HOOK) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "System Key Hook",
                        subtitle = when {
                            !keyHookEnabled && keyHookGlobalEnabled ->
                                "Root sync pending • hook may still intercept hardware keys"
                            !keyHookEnabled -> "Disabled • in-app LSK/RSK and T9 fallback active"
                            !keyHookGlobalEnabled -> "Awaiting root sync • switch may still be active"
                            else -> "Enabled • checked at a 2-second interval"
                        },
                        checked = keyHookEnabled,
                        isFocused = isRowFocused(MagiskRootLsposedRowId.KEY_HOOK_ENABLED),
                        jumpNumber = jumpNumber(MagiskRootLsposedRowId.KEY_HOOK_ENABLED),
                        onCheckedChange = { toggleKeyHook() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "SystemUI Hook",
                        subtitle = when {
                            !systemUiHookEnabled && systemUiHookGlobalEnabled ->
                                "Root sync pending • current hooks remain until SystemUI restarts"
                            !systemUiHookEnabled -> "Disabled • applies after SystemUI restart"
                            !systemUiHookGlobalEnabled -> "Awaiting root sync • SystemUI restart required"
                            else -> "Enabled • installed when SystemUI starts"
                        },
                        checked = systemUiHookEnabled,
                        isFocused = isRowFocused(MagiskRootLsposedRowId.SYSUI_HOOK_ENABLED),
                        jumpNumber = jumpNumber(MagiskRootLsposedRowId.SYSUI_HOOK_ENABLED),
                        onCheckedChange = { toggleSystemUiHook() }
                    )
                }
            }
            item(contentType = "permissions_root_utilities_card") {
                SettingsCard(categoryTitle = "Root Utilities & Recovery") {
                    SettingsToggleRow(
                        title = "Root Recents",
                        subtitle = "Use root shell fallback if standard Recents fails",
                        checked = rootRecentsEnabled,
                        isFocused = isRowFocused(MagiskRootLsposedRowId.RECENTS),
                        jumpNumber = jumpNumber(MagiskRootLsposedRowId.RECENTS),
                        onCheckedChange = { toggleRecents() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Root Quick Toggles",
                        subtitle = "Toggle Wi-Fi & Bluetooth via root shell",
                        checked = rootQuickSettingsEnabled,
                        isFocused = isRowFocused(MagiskRootLsposedRowId.QUICK_SETTINGS),
                        jumpNumber = jumpNumber(MagiskRootLsposedRowId.QUICK_SETTINGS),
                        onCheckedChange = { toggleQuickSettings() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Self-Heal Services",
                        subtitle = if (healing) {
                            "Granting overlay access and restarting cursor service…"
                        } else {
                            "Grant overlay access and restart cursor service with root"
                        },
                        icon = "♡",
                        docomoIndex = if (healing) 40 else null,
                        isFocused = isRowFocused(MagiskRootLsposedRowId.SELF_HEAL),
                        jumpNumber = jumpNumber(MagiskRootLsposedRowId.SELF_HEAL),
                        onClick = { selfHeal() }
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
