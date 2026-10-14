package com.tnyemii.m120launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    var pendingWidgetId by remember { mutableStateOf<Int?>(null) }

    val configLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        pendingWidgetId = null
        if (id != null) {
            if (result.resultCode == Activity.RESULT_OK) {
                if (id !in savedWidgetIdsList) {
                    saveIds(savedWidgetIdsList + id)
                }
            } else {
                appWidgetHost.deleteAppWidgetId(id)
            }
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId

        android.util.Log.d(
            "MochibunWidgets",
            "BIND CALLBACK: result=${result.resultCode}, id=$id, data=${result.data}"
        )

        if (result.resultCode == Activity.RESULT_OK && id != null) {
            val info = appWidgetManager.getAppWidgetInfo(id)

            android.util.Log.d(
                "MochibunWidgets",
                "BIND INFO: id=$id, info=$info"
            )

            if (info != null) {
                saveIds(savedWidgetIdsList + id)
                android.util.Log.d(
                    "MochibunWidgets",
                    "SAVED IDS: ${savedWidgetIdsList}"
                )
            } else {
                android.util.Log.e(
                    "MochibunWidgets",
                    "Widget info is null for id=$id"
                )
            }
        } else {
            android.util.Log.e(
                "MochibunWidgets",
                "Binding failed or pending ID is null"
            )
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        if (id != null) {
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) ?: id
                val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
                } ?: appWidgetManager.getAppWidgetInfo(widgetId)?.provider

                if (provider != null) {
                    val allowed = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider)
                    if (!allowed) {
                        pendingWidgetId = widgetId
                        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                        }
                        try {
                            bindLauncher.launch(bindIntent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            appWidgetHost.deleteAppWidgetId(widgetId)
                            pendingWidgetId = null
                        }
                    } else {
                        val info = appWidgetManager.getAppWidgetInfo(widgetId)
                        if (info?.configure != null) {
                            pendingWidgetId = widgetId
                            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            }
                            try {
                                configLauncher.launch(configIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                appWidgetHost.deleteAppWidgetId(widgetId)
                                pendingWidgetId = null
                            }
                        } else {
                            pendingWidgetId = null
                            if (widgetId !in savedWidgetIdsList) {
                                saveIds(savedWidgetIdsList + widgetId)
                            }
                        }
                    }
                } else {
                    appWidgetHost.deleteAppWidgetId(widgetId)
                    pendingWidgetId = null
                }
            } else {
                appWidgetHost.deleteAppWidgetId(id)
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
                        // cache resolved widget label to prevent repeated Binder IPC to package manager on recomposition <3
                        val widgetLabel = remember(widgetId, info) {
                            info.loadLabel(context.packageManager) ?: "Widget"
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            shape = RoundedCornerShape(cardRoundness),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = widgetLabel,
                                        color = cardTextColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Row {
                                        if (info.configure != null) {
                                            TextButton(
                                                onClick = {
                                                    pendingWidgetId = widgetId
                                                    val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                                    }
                                                    try {
                                                        configLauncher.launch(configIntent)
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                },
                                                contentPadding = PaddingValues(4.dp)
                                            ) {
                                                Text(text = "⚙️", fontSize = 14.sp)
                                            }
                                        }
                                        TextButton(
                                            onClick = {
                                                appWidgetHost.deleteAppWidgetId(widgetId)
                                                saveIds(savedWidgetIdsList.filter { it != widgetId })
                                            },
                                            contentPadding = PaddingValues(4.dp)
                                        ) {
                                            Text(text = "❌", fontSize = 14.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
