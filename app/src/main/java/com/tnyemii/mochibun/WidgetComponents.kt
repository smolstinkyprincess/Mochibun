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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

const val APP_WIDGET_HOST_ID = 1024

// widget page hosting actual Android widgets with AppWidgetHost & AppWidgetManager <3
@Composable
fun WidgetPage(
    context: Context,
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
    cardRoundness: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
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
            // Android may return RESULT_OK with a null Intent, and getAppWidgetInfo()
            // can still be null right after the bind screen closes. So: accept RESULT_OK
            // outright, and only fall back to querying the manager when it is not OK.
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

    Column(
        modifier = modifier
            .fillMaxSize()
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
                color = cardTextColor
            )

            Button(
                onClick = {
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
                },
                colors = ButtonDefaults.buttonColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(cardRoundness)
            ) {
                Text(
                    text = "+ Add Widget",
                    color = appActionColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (savedWidgetIdsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♡ No widgets yet! Tap '+ Add Widget' to add your favorite Android widgets ♡",
                    color = cardTextColor.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedWidgetIdsList, key = { it }) { widgetId ->
                    val info = remember(widgetId) { appWidgetManager.getAppWidgetInfo(widgetId) }
                    if (info == null) {
                        LaunchedEffect(widgetId) {
                            appWidgetHost.deleteAppWidgetId(widgetId)
                            saveIds(savedWidgetIdsList.filter { it != widgetId })
                        }
                    } else {
                        val widgetLabel = remember(widgetId, info) {
                            info.loadLabel(context.packageManager) ?: "Widget"
                        }

                        var showRemove by remember(widgetId) { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    with(androidx.compose.ui.platform.LocalDensity.current) {
                                        (info.minHeight.takeIf { it > 0 } ?: 100).dp
                                    }
                                )
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    try {
                                        appWidgetHost.createView(ctx, widgetId, info).apply {
                                            setAppWidget(widgetId, info)
                                        }
                                    } catch (ex: Exception) {
                                        android.widget.TextView(ctx).apply {
                                            text = "Error loading widget ♡"
                                            setTextColor(android.graphics.Color.MAGENTA)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // tiny always-visible handle in the corner — tap to reveal remove strip
                            TextButton(
                                onClick = { showRemove = !showRemove },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                                contentPadding = PaddingValues(4.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = cardTextColor.copy(alpha = 0.6f)
                                )
                            ) {
                                Text(text = "⋯", fontSize = 18.sp)
                            }

                            // remove strip, revealed on tap
                            if (showRemove) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 32.dp, end = 4.dp)
                                ) {
                                    TextButton(
                                        onClick = {
                                            appWidgetHost.deleteAppWidgetId(widgetId)
                                            saveIds(savedWidgetIdsList.filter { it != widgetId })
                                        },
                                        contentPadding = PaddingValues(4.dp),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = cardTextColor
                                        )
                                    ) {
                                        Text(text = "✖ remove", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
