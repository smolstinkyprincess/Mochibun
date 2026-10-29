package com.crinklecodone.mochibun.ui.components

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.TextView
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.data.repository.MediaSessionTracker
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.theme.destructiveBgColor
import com.crinklecodone.mochibun.ui.theme.destructiveColor
import kotlinx.coroutines.delay

const val APP_WIDGET_HOST_ID = 1024

enum class WidgetFocusSection {
    NOW_PLAYING,
    SYSTEM_MONITOR,
    USER_WIDGETS,
    ADD_WIDGET,
    HOME_PILL
}

/**
 * Widget Screen hosting the native "Now Playing" media widget, System Monitor card, and Android AppWidgets.
 * Faithfully matches the visual styling, component hierarchy, and kawaii aesthetic of Mochibun.
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
    val preferences = remember {
        context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
    }

    val isSystemMonitorEnabled by PreferencesManager.isSystemMonitorEnabled.collectAsState()

    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val appWidgetHost = remember { AppWidgetHost(context, APP_WIDGET_HOST_ID) }

    DisposableEffect(appWidgetHost) {
        appWidgetHost.startListening()
        onDispose {
            try {
                appWidgetHost.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping appWidgetHost", e)
            }
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

    var savedWidgetIdsList by remember {
        mutableStateOf<List<Int>>(
            preferences.getString("widget_ids", "")
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?.mapNotNull { it.toIntOrNull() }
                ?: emptyList()
        )
    }

    fun saveIds(ids: List<Int>) {
        if (savedWidgetIdsList != ids) {
            savedWidgetIdsList = ids
            preferences.edit()
                .putString("widget_ids", ids.joinToString(","))
                .apply()
        }
    }

    var pendingWidgetId by rememberSaveable { mutableStateOf<Int?>(null) }

    fun cancelPendingWidget(id: Int) {
        appWidgetHost.deleteAppWidgetId(id)
        pendingWidgetId = null
    }

    fun finishWidget(id: Int) {
        pendingWidgetId = null
        if (id !in savedWidgetIdsList) {
            saveIds(savedWidgetIdsList + id)
        }
    }

    fun makeConfigIntent(id: Int, configure: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    val configLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        if (id == null) {
            Log.w(TAG, "Config result arrived but no widget is pending")
        } else {
            pendingWidgetId = null
            val alreadySaved = id in savedWidgetIdsList
            if (result.resultCode == Activity.RESULT_OK) {
                if (!alreadySaved) saveIds(savedWidgetIdsList + id)
            } else if (!alreadySaved) {
                Log.d(TAG, "Config cancelled for new widget id=$id")
                appWidgetHost.deleteAppWidgetId(id)
            }
        }
    }

    fun continueAfterBind(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (info == null) {
            Log.w(TAG, "Widget id=$id not visible yet, retrying")
            Handler(Looper.getMainLooper()).postDelayed({
                val retry = appWidgetManager.getAppWidgetInfo(id)
                if (retry == null) {
                    Log.e(TAG, "Widget id=$id is not bound to a provider")
                    cancelPendingWidget(id)
                } else {
                    continueAfterBind(id)
                }
            }, 300)
            return
        }
        val configure = info.configure
        if (configure != null) {
            pendingWidgetId = id
            try {
                configLauncher.launch(makeConfigIntent(id, configure))
            } catch (e: Exception) {
                Log.e(TAG, "Could not open config screen $configure for id=$id", e)
                cancelPendingWidget(id)
            }
        } else {
            finishWidget(id)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        if (id == null) {
            Log.w(TAG, "Bind result arrived but no widget is pending")
        } else {
            val ok = result.resultCode == Activity.RESULT_OK
            val bound = ok || appWidgetManager.getAppWidgetInfo(id) != null
            Log.d(TAG, "Bind result=${result.resultCode}, id=$id, bound=$bound")
            if (bound) {
                continueAfterBind(id)
            } else {
                cancelPendingWidget(id)
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            appWidgetHost.deleteAppWidgetId(id)
            pendingWidgetId = null
            return@rememberLauncherForActivityResult
        }

        val data = result.data
        val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) ?: id
        val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
        } ?: appWidgetManager.getAppWidgetInfo(widgetId)?.provider

        if (provider == null) {
            appWidgetHost.deleteAppWidgetId(widgetId)
            pendingWidgetId = null
            return@rememberLauncherForActivityResult
        }

        if (appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)) {
            continueAfterBind(widgetId)
        } else {
            pendingWidgetId = widgetId
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                bindLauncher.launch(bindIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Bind screen failed for id=$widgetId", e)
                appWidgetHost.deleteAppWidgetId(widgetId)
                pendingWidgetId = null
            }
        }
    }

    val launchWidgetPicker: () -> Unit = {
        try {
            val newWidgetId = appWidgetHost.allocateAppWidgetId()
            pendingWidgetId = newWidgetId
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
            }
            pickLauncher.launch(pickIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch widget picker", e)
        }
    }

    // D-Pad Focus State Management
    var focusSection by remember {
        mutableStateOf(
            if (mediaState.isPlaying) WidgetFocusSection.NOW_PLAYING
            else if (isSystemMonitorEnabled) WidgetFocusSection.SYSTEM_MONITOR
            else if (savedWidgetIdsList.isNotEmpty()) WidgetFocusSection.USER_WIDGETS
            else WidgetFocusSection.HOME_PILL
        )
    }
    var nowPlayingCol by remember { mutableIntStateOf(1) } // 0 = Prev, 1 = Play/Pause, 2 = Next
    var focusedWidgetIdx by remember { mutableIntStateOf(0) }
    var removingWidgetId by remember { mutableStateOf<Int?>(null) }
    var sawCenterDown by remember { mutableStateOf(false) }
    var systemMonitorRefreshTrigger by remember { mutableIntStateOf(0) }

    val pageFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val navigateHomeAndReset: () -> Unit = {
        focusSection = if (mediaState.isPlaying) WidgetFocusSection.NOW_PLAYING
            else if (isSystemMonitorEnabled) WidgetFocusSection.SYSTEM_MONITOR
            else if (savedWidgetIdsList.isNotEmpty()) WidgetFocusSection.USER_WIDGETS
            else WidgetFocusSection.HOME_PILL
        nowPlayingCol = 1
        focusedWidgetIdx = 0
        removingWidgetId = null
        sawCenterDown = false
        onNavigateHome()
    }

    // Graceful D-pad focus fallback when active music playback stops or system monitor is toggled
    LaunchedEffect(mediaState.isPlaying, isSystemMonitorEnabled) {
        if (!mediaState.isPlaying && focusSection == WidgetFocusSection.NOW_PLAYING) {
            focusSection = if (isSystemMonitorEnabled) WidgetFocusSection.SYSTEM_MONITOR
                else if (savedWidgetIdsList.isNotEmpty()) WidgetFocusSection.USER_WIDGETS
                else WidgetFocusSection.HOME_PILL
        } else if (!isSystemMonitorEnabled && focusSection == WidgetFocusSection.SYSTEM_MONITOR) {
            focusSection = if (savedWidgetIdsList.isNotEmpty()) WidgetFocusSection.USER_WIDGETS
                else WidgetFocusSection.HOME_PILL
        }
    }

    // Ensure focused item is visible in LazyColumn
    LaunchedEffect(focusSection, focusedWidgetIdx) {
        if (focusSection == WidgetFocusSection.USER_WIDGETS && savedWidgetIdsList.isNotEmpty()) {
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

    LaunchedEffect(isActivePage) {
        if (isActivePage) {
            repeat(6) {
                try {
                    pageFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        } else {
            focusSection = if (mediaState.isPlaying) WidgetFocusSection.NOW_PLAYING
                else if (isSystemMonitorEnabled) WidgetFocusSection.SYSTEM_MONITOR
                else if (savedWidgetIdsList.isNotEmpty()) WidgetFocusSection.USER_WIDGETS
                else WidgetFocusSection.HOME_PILL
            nowPlayingCol = 1
            focusedWidgetIdx = 0
            removingWidgetId = null
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
                        if (removingWidgetId != null) {
                            removingWidgetId = null
                        } else {
                            navigateHomeAndReset()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
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
                                val widgetId = savedWidgetIdsList.getOrNull(focusedWidgetIdx)
                                if (widgetId != null) {
                                    if (removingWidgetId == widgetId) {
                                        appWidgetHost.deleteAppWidgetId(widgetId)
                                        saveIds(savedWidgetIdsList.filter { it != widgetId })
                                        removingWidgetId = null
                                    } else {
                                        removingWidgetId = widgetId
                                    }
                                }
                            }
                            WidgetFocusSection.ADD_WIDGET -> {
                                launchWidgetPicker()
                            }
                            WidgetFocusSection.HOME_PILL -> {
                                navigateHomeAndReset()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        removingWidgetId = null
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
                                if (savedWidgetIdsList.isNotEmpty()) {
                                    focusSection = WidgetFocusSection.USER_WIDGETS
                                    focusedWidgetIdx = savedWidgetIdsList.size - 1
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
                        removingWidgetId = null
                        when (focusSection) {
                            WidgetFocusSection.NOW_PLAYING -> {
                                if (isSystemMonitorEnabled) {
                                    focusSection = WidgetFocusSection.SYSTEM_MONITOR
                                } else if (savedWidgetIdsList.isNotEmpty()) {
                                    focusSection = WidgetFocusSection.USER_WIDGETS
                                    focusedWidgetIdx = 0
                                } else {
                                    focusSection = WidgetFocusSection.ADD_WIDGET
                                }
                            }
                            WidgetFocusSection.SYSTEM_MONITOR -> {
                                if (savedWidgetIdsList.isNotEmpty()) {
                                    focusSection = WidgetFocusSection.USER_WIDGETS
                                    focusedWidgetIdx = 0
                                } else {
                                    focusSection = WidgetFocusSection.ADD_WIDGET
                                }
                            }
                            WidgetFocusSection.USER_WIDGETS -> {
                                if (focusedWidgetIdx < savedWidgetIdsList.size - 1) {
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
                            WidgetFocusSection.USER_WIDGETS -> {
                                removingWidgetId = null
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
            itemsIndexed(savedWidgetIdsList, key = { _, id -> id }) { idx, widgetId ->
                val info = remember(widgetId) { appWidgetManager.getAppWidgetInfo(widgetId) }
                if (info == null) {
                    LaunchedEffect(widgetId) {
                        appWidgetHost.deleteAppWidgetId(widgetId)
                        saveIds(savedWidgetIdsList.filter { it != widgetId })
                    }
                } else {
                    val isWidgetFocused = focusSection == WidgetFocusSection.USER_WIDGETS && focusedWidgetIdx == idx
                    val showRemove = removingWidgetId == widgetId
                    val cardShape = RoundedCornerShape(18.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                with(LocalDensity.current) {
                                    (info.minHeight.takeIf { it > 0 } ?: 100).dp
                                }
                            )
                            .graphicsLayer { clip = false }
                            .glowingDoubleBorder(isSelected = isWidgetFocused, shape = cardShape, cornerRadius = 18.dp)
                            .clip(cardShape)
                            .background(Color(0xFFFFF6F8))
                            .border(
                                width = 1.5.dp,
                                color = if (isWidgetFocused) Color(0xFFE06287) else Color(0xFFE5A4B4),
                                shape = cardShape
                            )
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                try {
                                    appWidgetHost.createView(ctx, widgetId, info).apply {
                                        setAppWidget(widgetId, info)
                                    }
                                } catch (_: Exception) {
                                    TextView(ctx).apply {
                                        text = "Error loading widget ♡"
                                        setTextColor(android.graphics.Color.MAGENTA)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Corner remove handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF6F0FD))
                                .border(1.5.dp, Color(0xFFB5A4CB), RoundedCornerShape(10.dp))
                                .clickable {
                                    focusSection = WidgetFocusSection.USER_WIDGETS
                                    focusedWidgetIdx = idx
                                    removingWidgetId = if (showRemove) null else widgetId
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⋯",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3E2452)
                            )
                        }

                        if (showRemove) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 36.dp, end = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(destructiveBgColor)
                                    .border(2.dp, destructiveColor, RoundedCornerShape(10.dp))
                                    .clickable {
                                        appWidgetHost.deleteAppWidgetId(widgetId)
                                        saveIds(savedWidgetIdsList.filter { it != widgetId })
                                        removingWidgetId = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "✖ Remove (OK)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = destructiveColor
                                )
                            }
                        }
                    }
                }
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
                                launchWidgetPicker()
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (savedWidgetIdsList.isEmpty() && !mediaState.isPlaying) {
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
    }
}
