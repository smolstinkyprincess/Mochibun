package com.tnyemii.mochibun

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tnyemii.mochibun.ui.theme.destructiveBgColor
import com.tnyemii.mochibun.ui.theme.destructiveColor
import kotlinx.coroutines.delay

const val APP_WIDGET_HOST_ID = 1024

// widget page hosting actual Android widgets with AppWidgetHost & AppWidgetManager <3
@Composable
fun WidgetPage(
    context: Context,
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
    cardRoundness: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    isActivePage: Boolean = true,
    onNavigateHome: () -> Unit = {}
) {
    val theme = LocalLauncherTheme.current
    val preferences = remember {
        context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
    }

    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val appWidgetHost = remember { AppWidgetHost(context, APP_WIDGET_HOST_ID) }

    DisposableEffect(appWidgetHost) {
        appWidgetHost.startListening()
        onDispose {
            try {
                appWidgetHost.stopListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    // the widget id we are currently creating (survives activity recreation) <3
    var pendingWidgetId by rememberSaveable { mutableStateOf<Int?>(null) }

    // gives up on a widget that could not be created, so no id is leaked
    fun cancelPendingWidget(id: Int) {
        appWidgetHost.deleteAppWidgetId(id)
        pendingWidgetId = null
    }

    // saves a finished widget (only once) so it shows up on the page
    fun finishWidget(id: Int) {
        pendingWidgetId = null
        if (id !in savedWidgetIdsList) {
            saveIds(savedWidgetIdsList + id)
        }
    }

    // builds the intent for a widget's own configuration screen.
    // it needs the explicit component, a plain ACTION_APPWIDGET_CONFIGURE can't be resolved
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
                // new widget whose setup was cancelled, so remove it.
                // (cancelling the ⚙️ re-config of an existing widget keeps the widget)
                Log.d(TAG, "Config cancelled for new widget id=$id")
                appWidgetHost.deleteAppWidgetId(id)
            }
        }
    }

    // called once Android says the widget id is bound to a provider:
    // opens the config screen if the widget needs one, otherwise saves it
    fun continueAfterBind(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (info == null) {
            // binding can lag behind the result callback; retry once before giving up
            Log.w(TAG, "Widget id=$id not visible yet, retrying")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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

        // bind directly — no second bind screen needed
        if (appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)) {
            continueAfterBind(widgetId)
        } else {
            // only when the user hasn't granted bind permission: one bind screen, once
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
            e.printStackTrace()
        }
    }

    // D-pad focus state:
    // focusIndex == 0:Header row (headerCol 0 = "+ Add Widget", headerCol 1 = "Home ▶")
    // focusIndex in 1..savedWidgetIdsList.size: Widget card (pressing OK toggles remove strip or removes)
    var focusIndex by remember { mutableIntStateOf(0) }
    var headerCol by remember { mutableIntStateOf(0) }
    var removingWidgetId by remember { mutableStateOf<Int?>(null) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val pageFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val navigateHomeAndReset: () -> Unit = {
        headerCol = 0
        focusIndex = 0
        removingWidgetId = null
        sawCenterDown = false
        onNavigateHome()
    }

    LaunchedEffect(savedWidgetIdsList.size) {
        focusIndex = focusIndex.coerceIn(0, savedWidgetIdsList.size)
    }

    LaunchedEffect(focusIndex, savedWidgetIdsList.size) {
        val listIdx = focusIndex - 1
        if (listIdx in savedWidgetIdsList.indices) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == listIdx }
            if (!isVisible) {
                listState.animateScrollToItem(listIdx)
            }
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
            headerCol = 0
            focusIndex = 0
            removingWidgetId = null
            sawCenterDown = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(pageFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (!isActivePage) return@onPreviewKeyEvent false

                if (isBackDismissKey(native.keyCode)) {
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
                        if (focusIndex == 0) {
                            if (headerCol == 0) {
                                launchWidgetPicker()
                            } else {
                                navigateHomeAndReset()
                            }
                        } else {
                            val widgetId = savedWidgetIdsList.getOrNull(focusIndex - 1)
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
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        removingWidgetId = null
                        focusIndex = (focusIndex - 1).coerceAtLeast(0)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        removingWidgetId = null
                        focusIndex = (focusIndex + 1).coerceAtMost(savedWidgetIdsList.size)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focusIndex == 0) {
                            headerCol = 0
                        } else {
                            removingWidgetId = null
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focusIndex == 0) {
                            if (headerCol == 0) {
                                headerCol = 1
                            } else {
                                navigateHomeAndReset()
                            }
                        } else {
                            navigateHomeAndReset()
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "♡ Widgets ♡",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.fgTextColor
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KawaiiDialogPillButton(
                    text = "+ Add Widget",
                    isFocused = focusIndex == 0 && headerCol == 0,
                    isPrimary = true,
                    onClick = {
                        focusIndex = 0
                        headerCol = 0
                        launchWidgetPicker()
                    }
                )

                KawaiiDialogPillButton(
                    text = "Home ▶",
                    isFocused = focusIndex == 0 && headerCol == 1,
                    onClick = {
                        navigateHomeAndReset()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (savedWidgetIdsList.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(2.5.dp, theme.greetingOutlineColor, RoundedCornerShape(cardRoundness)),
                colors = CardDefaults.cardColors(containerColor = theme.cardBgColor.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(cardRoundness)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♡ No widgets yet!\nPress OK on '+ Add Widget' to add your favorite Android widgets ♡",
                        color = theme.fgTextColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(savedWidgetIdsList, key = { _, id -> id }) { idx, widgetId ->
                    val info = remember(widgetId) { appWidgetManager.getAppWidgetInfo(widgetId) }
                    if (info == null) {
                        LaunchedEffect(widgetId) {
                            appWidgetHost.deleteAppWidgetId(widgetId)
                            saveIds(savedWidgetIdsList.filter { it != widgetId })
                        }
                    } else {
                        val isWidgetFocused = focusIndex == idx + 1
                        val showRemove = removingWidgetId == widgetId
                        val cardShape = RoundedCornerShape(16.dp)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    with(androidx.compose.ui.platform.LocalDensity.current) {
                                        (info.minHeight.takeIf { it > 0 } ?: 100).dp
                                    }
                                )
                                .clip(cardShape)
                                .border(
                                    width = if (isWidgetFocused) 3.dp else 1.5.dp,
                                    color = if (isWidgetFocused) theme.appActionColor else theme.greetingOutlineColor,
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
                                        android.widget.TextView(ctx).apply {
                                            text = "Error loading widget ♡"
                                            setTextColor(android.graphics.Color.MAGENTA)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // corner handle — tap or press OK when focused to reveal remove button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isWidgetFocused) theme.selectedContainerColor else theme.cardBgColor.copy(alpha = 0.85f)
                                    )
                                    .border(
                                        1.5.dp,
                                        if (isWidgetFocused) theme.appActionColor else theme.greetingOutlineColor,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        focusIndex = idx + 1
                                        removingWidgetId = if (showRemove) null else widgetId
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⋯",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.fgTextColor
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
            }
        }
    }
}
