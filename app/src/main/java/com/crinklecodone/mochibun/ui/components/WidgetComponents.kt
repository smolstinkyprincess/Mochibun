package com.crinklecodone.mochibun.ui.components

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.core.haptic.HapticManager
import com.crinklecodone.mochibun.core.widget.MochibunWidgetHost
import com.crinklecodone.mochibun.core.widget.NavDirection
import com.crinklecodone.mochibun.core.widget.WidgetFrame
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.WidgetRepository
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.data.repository.MediaSessionTracker
import com.crinklecodone.mochibun.ui.dialogs.WidgetOptionsDialog
import com.crinklecodone.mochibun.ui.dialogs.WidgetPickerDialog
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "WidgetPage"

/** Holding OK this long on a widget opens its options instead of using it. */
private const val WIDGET_LONG_PRESS_MS = 400L

enum class WidgetFocusSection {
    NOW_PLAYING,
    SYSTEM_MONITOR,
    USER_WIDGETS,
    ADD_WIDGET,
    HOME_PILL
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Widget Screen hosting the native "Now Playing" media widget, System Monitor card, and Android AppWidgets.
 * Faithfully matches the visual styling, component hierarchy, and kawaii aesthetic of Mochibun.
 *
 * Hosted app widgets: OK uses the widget (walks its buttons with the D-pad when it has several),
 * holding OK opens Move / Configure / Remove. Listening for widget updates is tied to
 * MainActivity's onStart/onStop through [MochibunWidgetHost], not to this composable.
 */
@Composable
fun WidgetPage(
    context: Context,
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
    cardRoundness: Dp,
    modifier: Modifier = Modifier,
    isActivePage: Boolean = true,
    onNavigateHome: () -> Unit = {}
) {
    val theme = LocalLauncherTheme.current
    val scope = rememberCoroutineScope()

    val isSystemMonitorEnabled by PreferencesManager.isSystemMonitorEnabled.collectAsState()

    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val widgetHost = remember { MochibunWidgetHost.get(context) }
    val widgetSlots by WidgetRepository.slots.collectAsState()
    val providersVersion by widgetHost.providersVersion.collectAsState()

    // Provider info per widget; null means the provider is unavailable (uninstalled, updating, disabled)
    val widgetInfos = remember(widgetSlots, providersVersion) {
        widgetSlots.associate { slot ->
            slot.appWidgetId to try {
                appWidgetManager.getAppWidgetInfo(slot.appWidgetId)
            } catch (_: Exception) {
                null
            }
        }
    }

    // Live WidgetFrames by widget ID, registered/unregistered by each card's AndroidView
    val widgetFrames = remember { HashMap<Int, WidgetFrame>() }

    // When providers change, drop widgets whose app was uninstalled and delete orphaned IDs
    LaunchedEffect(providersVersion) {
        if (providersVersion == 0) return@LaunchedEffect // MainActivity already reconciled at startup
        widgetHost.hostIdsOrNull()?.let { hostIds ->
            WidgetRepository.reconcile(context, hostIds, keepPending = true)
                .forEach { widgetHost.deleteAppWidgetIdSafely(it) }
        }
    }

    // MediaSession Tracking on Android 12 (API 31)
    val mediaState by MediaSessionTracker.mediaState.collectAsState()

    DisposableEffect(isActivePage) {
        if (isActivePage) {
            MediaSessionTracker.startTracking(context)
        }
        onDispose {
            MediaSessionTracker.stopTracking()
        }
    }

    // D-Pad Focus State Management
    fun defaultFocusSection(): WidgetFocusSection =
        if (mediaState.isPlaying) WidgetFocusSection.NOW_PLAYING
        else if (isSystemMonitorEnabled) WidgetFocusSection.SYSTEM_MONITOR
        else if (widgetSlots.isNotEmpty()) WidgetFocusSection.USER_WIDGETS
        else WidgetFocusSection.HOME_PILL

    var focusSection by remember { mutableStateOf(defaultFocusSection()) }
    var nowPlayingCol by remember { mutableIntStateOf(1) } // 0 = Prev, 1 = Play/Pause, 2 = Next
    var focusedWidgetIdx by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }
    var centerDownAt by remember { mutableLongStateOf(0L) }
    var centerLongPressFired by remember { mutableStateOf(false) }
    var systemMonitorRefreshTrigger by remember { mutableIntStateOf(0) }

    // Hosted widget state
    var interactingWidgetId by remember { mutableStateOf<Int?>(null) }
    var optionsWidgetId by remember { mutableStateOf<Int?>(null) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    val anyWidgetDialogOpen = optionsWidgetId != null || showWidgetPicker

    fun endWidgetInteraction() {
        interactingWidgetId?.let { widgetFrames[it]?.endInteraction() }
        interactingWidgetId = null
    }

    fun focusWidget(appWidgetId: Int) {
        val idx = WidgetRepository.ids().indexOf(appWidgetId)
        if (idx >= 0) {
            focusSection = WidgetFocusSection.USER_WIDGETS
            focusedWidgetIdx = idx
        }
    }

    // ── Add-widget flow: allocate -> bind (permission) -> configure -> save ──
    // The in-flight ID is persisted so startup cleanup can tell it apart from orphans.

    fun cancelPendingWidget(id: Int) {
        widgetHost.deleteAppWidgetIdSafely(id)
        WidgetRepository.setPendingWidgetId(context, null)
    }

    fun finishWidget(id: Int) {
        WidgetRepository.setPendingWidgetId(context, null)
        val provider = try {
            appWidgetManager.getAppWidgetInfo(id)?.provider?.flattenToString()
        } catch (_: Exception) {
            null
        }
        WidgetRepository.add(context, id, provider)
        focusWidget(id)
    }

    fun configureIntent(id: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            // No FLAG_ACTIVITY_NEW_TASK: with it, startActivityForResult returns RESULT_CANCELED at once
            component = info.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }

    /** Fallback for config activities that aren't exported: the host starts them via an IntentSender. */
    fun startConfigureViaHost(id: Int): Boolean {
        val activity = context.findActivity() ?: return false
        return try {
            widgetHost.startAppWidgetConfigureActivityForResult(activity, id, 0, 0, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Host could not open config for id=$id", e)
            false
        }
    }

    val configLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = WidgetRepository.pendingWidgetId(context) ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            finishWidget(id)
        } else {
            Log.d(TAG, "Config cancelled for new widget id=$id")
            cancelPendingWidget(id)
        }
    }

    // Reconfiguring an existing widget: the result doesn't matter, the widget stays either way
    val reconfigureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun continueAfterBind(id: Int, retriesLeft: Int = 1) {
        val info = try {
            appWidgetManager.getAppWidgetInfo(id)
        } catch (_: Exception) {
            null
        }
        if (info == null) {
            if (retriesLeft > 0) {
                scope.launch {
                    delay(300)
                    continueAfterBind(id, retriesLeft - 1)
                }
            } else {
                Log.e(TAG, "Widget id=$id is not bound to a provider")
                cancelPendingWidget(id)
            }
            return
        }
        val features = info.widgetFeatures
        val configOptional =
            (features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0 &&
                (features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
        if (info.configure == null || configOptional) {
            finishWidget(id)
            return
        }
        try {
            configLauncher.launch(configureIntent(id, info))
        } catch (e: Exception) {
            Log.w(TAG, "Direct config launch failed for id=$id, trying host", e)
            // We can't observe the result this way, so keep the widget
            if (startConfigureViaHost(id)) finishWidget(id) else cancelPendingWidget(id)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = WidgetRepository.pendingWidgetId(context) ?: return@rememberLauncherForActivityResult
        val bound = result.resultCode == Activity.RESULT_OK ||
            (try { appWidgetManager.getAppWidgetInfo(id) } catch (_: Exception) { null }) != null
        Log.d(TAG, "Bind result=${result.resultCode}, id=$id, bound=$bound")
        if (bound) continueAfterBind(id) else cancelPendingWidget(id)
    }

    fun addWidget(info: AppWidgetProviderInfo) {
        showWidgetPicker = false
        val id = try {
            widgetHost.allocateAppWidgetId()
        } catch (e: Exception) {
            Log.e(TAG, "allocateAppWidgetId failed", e)
            Toast.makeText(context, "Couldn't add that widget ♡", Toast.LENGTH_SHORT).show()
            return
        }
        WidgetRepository.setPendingWidgetId(context, id)
        val bound = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        } catch (e: Exception) {
            Log.w(TAG, "bindAppWidgetIdIfAllowed failed for id=$id", e)
            false
        }
        if (bound) {
            continueAfterBind(id)
            return
        }
        // Not allowed yet: ask the user once (the system dialog offers "always allow")
        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        }
        try {
            bindLauncher.launch(bindIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Bind screen failed for id=$id", e)
            cancelPendingWidget(id)
        }
    }

    fun reconfigureWidget(id: Int) {
        val info = widgetInfos[id] ?: return
        if (info.configure == null) return
        try {
            reconfigureLauncher.launch(configureIntent(id, info))
        } catch (e: Exception) {
            Log.w(TAG, "Direct reconfigure failed for id=$id, trying host", e)
            if (!startConfigureViaHost(id)) {
                Toast.makeText(context, "This widget can't be configured ♡", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeWidget(id: Int) {
        if (interactingWidgetId == id) endWidgetInteraction()
        widgetFrames.remove(id)
        widgetHost.deleteAppWidgetIdSafely(id)
        WidgetRepository.remove(context, id)
        val remaining = WidgetRepository.ids().size
        if (remaining == 0) {
            focusSection = WidgetFocusSection.ADD_WIDGET
            focusedWidgetIdx = 0
        } else {
            focusedWidgetIdx = focusedWidgetIdx.coerceIn(0, remaining - 1)
        }
    }

    /**
     * OK on a widget card. One control: tap it. Several: walk them with the D-pad.
     * None: hint at the options menu.
     */
    fun activateWidget(id: Int) {
        if (widgetInfos[id] == null) {
            optionsWidgetId = id
            return
        }
        val frame = widgetFrames[id] ?: return
        when (frame.beginInteraction()) {
            0 -> {
                frame.endInteraction()
                Toast.makeText(context, "This widget has no buttons ♡ Hold OK for options", Toast.LENGTH_SHORT).show()
            }
            1 -> {
                frame.clickSelection()
                frame.endInteraction()
            }
            else -> interactingWidgetId = id
        }
    }

    val pageFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val navigateHomeAndReset: () -> Unit = {
        endWidgetInteraction()
        focusSection = defaultFocusSection()
        nowPlayingCol = 1
        focusedWidgetIdx = 0
        sawCenterDown = false
        onNavigateHome()
    }

    // Graceful D-pad focus fallback when active music playback stops or system monitor is toggled
    LaunchedEffect(mediaState.isPlaying, isSystemMonitorEnabled) {
        if (!mediaState.isPlaying && focusSection == WidgetFocusSection.NOW_PLAYING) {
            focusSection = if (isSystemMonitorEnabled) WidgetFocusSection.SYSTEM_MONITOR
                else if (widgetSlots.isNotEmpty()) WidgetFocusSection.USER_WIDGETS
                else WidgetFocusSection.HOME_PILL
        } else if (!isSystemMonitorEnabled && focusSection == WidgetFocusSection.SYSTEM_MONITOR) {
            focusSection = if (widgetSlots.isNotEmpty()) WidgetFocusSection.USER_WIDGETS
                else WidgetFocusSection.HOME_PILL
        }
    }

    // Keep the focused widget index valid when widgets disappear (removed or uninstalled)
    LaunchedEffect(widgetSlots.size) {
        if (widgetSlots.isEmpty() && focusSection == WidgetFocusSection.USER_WIDGETS) {
            focusSection = WidgetFocusSection.ADD_WIDGET
        }
        focusedWidgetIdx = focusedWidgetIdx.coerceIn(0, (widgetSlots.size - 1).coerceAtLeast(0))
        if (interactingWidgetId != null && widgetSlots.none { it.appWidgetId == interactingWidgetId }) {
            interactingWidgetId = null
        }
    }

    // Ensure focused item is visible in LazyColumn
    LaunchedEffect(focusSection, focusedWidgetIdx) {
        if (focusSection == WidgetFocusSection.USER_WIDGETS && widgetSlots.isNotEmpty()) {
            val baseOffset = 1 + (if (mediaState.isPlaying) 1 else 0) + (if (isSystemMonitorEnabled) 1 else 0)
            val targetIdx = baseOffset + focusedWidgetIdx
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIdx }
            if (!isVisible) {
                listState.animateScrollToItem(targetIdx)
            }
        } else if (focusSection == WidgetFocusSection.NOW_PLAYING) {
            listState.animateScrollToItem(0)
        } else if (focusSection == WidgetFocusSection.SYSTEM_MONITOR) {
            val targetIdx = if (mediaState.isPlaying) 1 else 0
            listState.animateScrollToItem(targetIdx)
        }
    }

    // Re-take key focus when the page becomes active and after a widget dialog closes
    LaunchedEffect(isActivePage, anyWidgetDialogOpen) {
        if (isActivePage) {
            if (anyWidgetDialogOpen) return@LaunchedEffect
            repeat(6) {
                try {
                    pageFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        } else {
            endWidgetInteraction()
            optionsWidgetId = null
            showWidgetPicker = false
            focusSection = defaultFocusSection()
            nowPlayingCol = 1
            focusedWidgetIdx = 0
            sawCenterDown = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent) // Let wallpaper + sticker overlay show through
            .focusRequester(pageFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (!isActivePage) return@onPreviewKeyEvent false

                if (isBackDismissKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_F4) {
                    if (event.type == KeyEventType.KeyUp) {
                        if (interactingWidgetId != null) {
                            endWidgetInteraction()
                        } else {
                            navigateHomeAndReset()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        centerDownAt = native.eventTime
                        centerLongPressFired = false
                    } else if (event.type == KeyEventType.KeyDown) {
                        // Held OK on a widget card: open its options
                        val heldMs = native.eventTime - centerDownAt
                        if (sawCenterDown && !centerLongPressFired &&
                            focusSection == WidgetFocusSection.USER_WIDGETS &&
                            heldMs >= WIDGET_LONG_PRESS_MS
                        ) {
                            centerLongPressFired = true
                            sawCenterDown = false
                            val widgetId = widgetSlots.getOrNull(focusedWidgetIdx)?.appWidgetId
                            if (widgetId != null) {
                                endWidgetInteraction()
                                optionsWidgetId = widgetId
                            }
                        }
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusSection) {
                            WidgetFocusSection.NOW_PLAYING -> {
                                when (nowPlayingCol) {
                                    0 -> MediaSessionTracker.skipToPrevious()
                                    1 -> MediaSessionTracker.togglePlayPause()
                                    2 -> MediaSessionTracker.skipToNext()
                                }
                            }
                            WidgetFocusSection.SYSTEM_MONITOR -> {
                                systemMonitorRefreshTrigger++
                            }
                            WidgetFocusSection.USER_WIDGETS -> {
                                val widgetId = widgetSlots.getOrNull(focusedWidgetIdx)?.appWidgetId
                                val interacting = interactingWidgetId
                                if (interacting != null && interacting == widgetId) {
                                    widgetFrames[interacting]?.clickSelection()
                                } else if (widgetId != null) {
                                    activateWidget(widgetId)
                                }
                            }
                            WidgetFocusSection.ADD_WIDGET -> {
                                showWidgetPicker = true
                            }
                            WidgetFocusSection.HOME_PILL -> {
                                navigateHomeAndReset()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // Inside a widget: the D-pad walks its buttons until it runs off the top/bottom
                val interacting = interactingWidgetId
                if (interacting != null) {
                    val direction = when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> NavDirection.UP
                        KeyEvent.KEYCODE_DPAD_DOWN -> NavDirection.DOWN
                        KeyEvent.KEYCODE_DPAD_LEFT -> NavDirection.LEFT
                        KeyEvent.KEYCODE_DPAD_RIGHT -> NavDirection.RIGHT
                        else -> null
                    }
                    if (direction != null) {
                        val frame = widgetFrames[interacting]
                        if (frame != null && frame.moveSelection(direction)) {
                            return@onPreviewKeyEvent true
                        }
                        if (direction == NavDirection.LEFT || direction == NavDirection.RIGHT) {
                            HapticManager.performBoundaryHaptic(context)
                            return@onPreviewKeyEvent true
                        }
                        // UP/DOWN past the edge: leave the widget and move to the next card
                        endWidgetInteraction()
                    }
                }

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusSection) {
                            WidgetFocusSection.NOW_PLAYING -> {
                                // Already at top
                            }
                            WidgetFocusSection.SYSTEM_MONITOR -> {
                                if (mediaState.isPlaying) {
                                    focusSection = WidgetFocusSection.NOW_PLAYING
                                    nowPlayingCol = 1
                                }
                            }
                            WidgetFocusSection.USER_WIDGETS -> {
                                if (focusedWidgetIdx > 0) {
                                    focusedWidgetIdx--
                                } else if (isSystemMonitorEnabled) {
                                    focusSection = WidgetFocusSection.SYSTEM_MONITOR
                                } else if (mediaState.isPlaying) {
                                    focusSection = WidgetFocusSection.NOW_PLAYING
                                    nowPlayingCol = 1
                                }
                            }
                            WidgetFocusSection.ADD_WIDGET -> {
                                if (widgetSlots.isNotEmpty()) {
                                    focusSection = WidgetFocusSection.USER_WIDGETS
                                    focusedWidgetIdx = widgetSlots.size - 1
                                } else if (isSystemMonitorEnabled) {
                                    focusSection = WidgetFocusSection.SYSTEM_MONITOR
                                } else if (mediaState.isPlaying) {
                                    focusSection = WidgetFocusSection.NOW_PLAYING
                                    nowPlayingCol = 1
                                }
                            }
                            WidgetFocusSection.HOME_PILL -> {
                                focusSection = WidgetFocusSection.ADD_WIDGET
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusSection) {
                            WidgetFocusSection.NOW_PLAYING -> {
                                if (isSystemMonitorEnabled) {
                                    focusSection = WidgetFocusSection.SYSTEM_MONITOR
                                } else if (widgetSlots.isNotEmpty()) {
                                    focusSection = WidgetFocusSection.USER_WIDGETS
                                    focusedWidgetIdx = 0
                                } else {
                                    focusSection = WidgetFocusSection.ADD_WIDGET
                                }
                            }
                            WidgetFocusSection.SYSTEM_MONITOR -> {
                                if (widgetSlots.isNotEmpty()) {
                                    focusSection = WidgetFocusSection.USER_WIDGETS
                                    focusedWidgetIdx = 0
                                } else {
                                    focusSection = WidgetFocusSection.ADD_WIDGET
                                }
                            }
                            WidgetFocusSection.USER_WIDGETS -> {
                                if (focusedWidgetIdx < widgetSlots.size - 1) {
                                    focusedWidgetIdx++
                                } else {
                                    focusSection = WidgetFocusSection.ADD_WIDGET
                                }
                            }
                            WidgetFocusSection.ADD_WIDGET -> {
                                focusSection = WidgetFocusSection.HOME_PILL
                            }
                            WidgetFocusSection.HOME_PILL -> {
                                // Already at bottom
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusSection) {
                            WidgetFocusSection.NOW_PLAYING -> {
                                nowPlayingCol = (nowPlayingCol - 1).coerceAtLeast(0)
                            }
                            else -> {}
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (focusSection) {
                            WidgetFocusSection.NOW_PLAYING -> {
                                if (nowPlayingCol < 2) {
                                    nowPlayingCol++
                                } else {
                                    navigateHomeAndReset()
                                }
                            }
                            else -> {
                                navigateHomeAndReset()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        // 1. Top Scalloped Lavender Clouds
        TopLavenderClouds(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // 2. Main Scrollable Content (Header, Now Playing, System Monitor, User Widgets, Add Widget)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 44.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header & Overlapping Sub-Badge
            item(key = "header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    // Gradient pill card: — ♡ Widgets ♡ —
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFD5D4F5), Color(0xFFF7CAD8))
                                )
                            )
                            .border(1.5.dp, Color(0xFFB5A4CB), RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "— ♡ Widgets ♡ —",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ActiveTheme.accentDeep
                            )
                        }
                    }

                    // Overlapping Sub-Badge: ✦ Now Playing ✦
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 13.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFEAE8FB))
                            .border(1.2.dp, Color(0xFFB5A4CB), RoundedCornerShape(50))
                            .clickable {
                                if (!MediaSessionTracker.hasNotificationAccess(context)) {
                                    Toast.makeText(
                                        context,
                                        "Please allow Notification Access for Mochibun to track media ♡",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    } catch (_: Exception) {}
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Now Playing media service active ♡",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✦ Now Playing ✦",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3E2452)
                        )
                    }
                }
            }

            // Dedicated, Dynamic "Now Playing" Native Card (Active Playback Only)
            if (mediaState.isPlaying) {
                item(key = "now_playing_card") {
                    NowPlayingCard(
                        mediaState = mediaState,
                        focusedControl = if (focusSection == WidgetFocusSection.NOW_PLAYING) nowPlayingCol else -1,
                        onPrevious = { MediaSessionTracker.skipToPrevious() },
                        onPlayPause = { MediaSessionTracker.togglePlayPause() },
                        onNext = { MediaSessionTracker.skipToNext() },
                        onOpenMediaApp = { MediaSessionTracker.openCurrentMediaApp(context) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Lightweight, Cute Pastel System Monitor Card (when enabled)
            if (isSystemMonitorEnabled) {
                item(key = "system_monitor_card") {
                    SystemMonitorCard(
                        isActivePage = isActivePage,
                        isFocused = focusSection == WidgetFocusSection.SYSTEM_MONITOR,
                        onCardClick = {
                            focusSection = WidgetFocusSection.SYSTEM_MONITOR
                            systemMonitorRefreshTrigger++
                        },
                        triggerRefreshKey = systemMonitorRefreshTrigger,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // User-added Android App Widgets
            itemsIndexed(widgetSlots, key = { _, slot -> slot.appWidgetId }) { idx, slot ->
                AppWidgetHostCard(
                    slot = slot,
                    info = widgetInfos[slot.appWidgetId],
                    host = widgetHost,
                    isFocused = focusSection == WidgetFocusSection.USER_WIDGETS && focusedWidgetIdx == idx,
                    isInteracting = interactingWidgetId == slot.appWidgetId,
                    onFrameAttached = { id, frame -> widgetFrames[id] = frame },
                    onFrameDetached = { id, frame ->
                        if (widgetFrames[id] === frame) widgetFrames.remove(id)
                        if (interactingWidgetId == id) interactingWidgetId = null
                    },
                    onOptionsClick = {
                        endWidgetInteraction()
                        focusSection = WidgetFocusSection.USER_WIDGETS
                        focusedWidgetIdx = idx
                        optionsWidgetId = slot.appWidgetId
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // "+ Add Widget" kawaii pill action
            item(key = "add_widget_button") {
                val isAddFocused = focusSection == WidgetFocusSection.ADD_WIDGET
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer { clip = false }
                            .glowingDoubleBorder(isSelected = isAddFocused, shape = RoundedCornerShape(50), cornerRadius = 50.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isAddFocused) Color(0xFFFFF0F4) else Color(0xFFF6F0FD))
                            .border(1.5.dp, if (isAddFocused) Color(0xFFE06287) else Color(0xFFB5A4CB), RoundedCornerShape(50))
                            .clickable {
                                focusSection = WidgetFocusSection.ADD_WIDGET
                                showWidgetPicker = true
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (widgetSlots.isEmpty() && !mediaState.isPlaying) {
                                "+ Add Favorite Widgets ♡"
                            } else {
                                "+ Add Widget ♡"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3E2452)
                        )
                    }
                }
            }
        }

        // 3. Bottom Layered Lavender Clouds
        BottomLavenderClouds(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )

        // 4. Upward Chevron and "Back to Home" Navigation Pill
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "^",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E2452)
            )
            Spacer(modifier = Modifier.height(2.dp))
            val isHomeFocused = focusSection == WidgetFocusSection.HOME_PILL
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFFF6F8))
                    .glowingDoubleBorder(isSelected = isHomeFocused, shape = RoundedCornerShape(50), cornerRadius = 50.dp)
                    .border(1.2.dp, if (isHomeFocused) Color(0xFFE06287) else Color(0xFFB5A4CB), RoundedCornerShape(50))
                    .clickable { navigateHomeAndReset() }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♥ Back to Home ▶",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E2452)
                )
            }
        }

        // 5. Widget dialogs (own windows, so they take keys away from this page while open)
        if (showWidgetPicker) {
            val density = LocalDensity.current
            val cardWidthPx = with(density) { (LocalConfiguration.current.screenWidthDp.dp - 28.dp).roundToPx() }
            WidgetPickerDialog(
                availableWidthPx = cardWidthPx,
                onPick = { info -> addWidget(info) },
                onDismiss = { showWidgetPicker = false }
            )
        }

        optionsWidgetId?.let { id ->
            val idx = widgetSlots.indexOfFirst { it.appWidgetId == id }
            if (idx >= 0) {
                val info = widgetInfos[id]
                val label = remember(id, info) {
                    info?.loadLabel(context.packageManager)?.takeIf { it.isNotBlank() } ?: "Widget"
                }
                WidgetOptionsDialog(
                    widgetLabel = label,
                    canMoveUp = idx > 0,
                    canMoveDown = idx < widgetSlots.lastIndex,
                    canConfigure = info?.configure != null,
                    canUseControls = info != null && widgetFrames.containsKey(id),
                    onMoveUp = {
                        if (WidgetRepository.move(context, id, -1)) focusWidget(id)
                    },
                    onMoveDown = {
                        if (WidgetRepository.move(context, id, 1)) focusWidget(id)
                    },
                    onConfigure = {
                        optionsWidgetId = null
                        reconfigureWidget(id)
                    },
                    onUseControls = {
                        optionsWidgetId = null
                        activateWidget(id)
                    },
                    onRemove = {
                        optionsWidgetId = null
                        removeWidget(id)
                    },
                    onDismiss = { optionsWidgetId = null }
                )
            }
        }
    }
}
