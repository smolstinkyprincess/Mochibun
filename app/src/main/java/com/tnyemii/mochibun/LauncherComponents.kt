package com.tnyemii.mochibun

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tnyemii.mochibun.ui.theme.appActionColor
import com.tnyemii.mochibun.ui.theme.cardBgColor
import com.tnyemii.mochibun.ui.theme.cardTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

val cardRoundness = 24.dp

// displays current time, date, battery level, and Wi-Fi connection status :3
@Composable
fun StatusCard(
    context: Context,
    cardBgColor: Color,
    cardTextColor: Color,
    outlineColor: Color,
    onWifiChanged: (Boolean) -> Unit = {},
    onLowBatteryChanged: (Boolean) -> Unit = {},
) {
    var currentTime by remember { mutableStateOf(Date()) }
    var batteryLevel by remember { mutableStateOf(0) }
    var wifiConnected by remember { mutableStateOf(false) }
    var wifiSsid by remember { mutableStateOf<String?>(null) }

    // clock ticker coroutine updates every 10 seconds <3
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            kotlinx.coroutines.delay(10000.milliseconds)
        }
    }

    // periodic background status reader for battery and Wi-Fi SSID :3
    LaunchedEffect(Unit) {
        while (true) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            onLowBatteryChanged(batteryLevel in 0..20 && !batteryManager.isCharging)

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val isWifi = capabilities?.hasTransport(
                android.net.NetworkCapabilities.TRANSPORT_WIFI
            ) == true

            wifiConnected = isWifi
            onWifiChanged(isWifi)

            if (isWifi) {
                var rawSsid: String? = null

                // modern Android 10+ transport info path <3
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val wifiInfo = capabilities?.transportInfo as? android.net.wifi.WifiInfo
                    rawSsid = wifiInfo?.ssid
                }

                // fallback for older API or MTK hardware :3
                if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") {
                    try {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                        rawSsid = wifiManager?.connectionInfo?.ssid
                    } catch (_: Exception) {
                        rawSsid = null
                    }
                }

                rawSsid = rawSsid?.removeSurrounding("\"")

                wifiSsid = if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                    rawSsid
                } else {
                    "Wi-Fi"
                }
            } else {
                wifiSsid = null
            }

            kotlinx.coroutines.delay(30000.milliseconds)
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    val formattedTime = remember(currentTime) { timeFormat.format(currentTime) }
    val formattedDate = remember(currentTime) { dateFormat.format(currentTime) }

    Card(
        modifier = Modifier
            .border(
                width = 4.dp,
                color = outlineColor,
                shape = RoundedCornerShape(cardRoundness)
            ),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(cardRoundness)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formattedTime,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = cardTextColor
            )

            Text(
                text = formattedDate,
                fontSize = 14.sp,
                color = cardTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🔋 $batteryLevel%   ",
                    fontSize = 14.sp,
                    color = cardTextColor
                )

                if (wifiConnected) {
                    Text(
                        text = wifiSsid ?: "Wi-Fi",
                        fontSize = 14.sp,
                        color = cardTextColor
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    TinyWifiIcon(
                        modifier = Modifier.size(14.dp),
                        color = cardTextColor
                    )
                } else {
                    Text(
                        text = "♡ offline",
                        fontSize = 14.sp,
                        color = cardTextColor
                    )
                }
            }
        }
    }
}

// top greeting card header with decorative ribbon bow sticker <3
@Composable
fun GreetingCard(context: Context, isOnline: Boolean = true, isLowBattery: Boolean = false) {
    val theme = LocalLauncherTheme.current
    val cardRoundness = theme.cardRoundnessDp.dp
    val greetingOutlineColor = theme.greetingOutlineColor
    val greetingBgColor = theme.greetingBgColor
    val greetingColor = theme.greetingColor

    // bunny cheers for a moment when tapped :3
    var cheering by remember { mutableStateOf(false) }
    LaunchedEffect(cheering) {
        if (cheering) {
            kotlinx.coroutines.delay(1500)
            cheering = false
        }
    }

    // what hour is it? checked once a minute so the greeting changes by itself <3
    var hour by remember {
        mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        }
    }
    // custom greetings based on time of day
    val greeting = when (hour) {
        in 5..11 -> "♡ good morning, emii ♡"
        in 12..16 -> "♡ good afternoon, emii ♡"
        in 17..20 -> "♡ good evening, emii ♡"
        in 21..23 -> "♡ good night, emii ♡"
        else -> "♡ it's sleepy time, emii ♡"      // 12am to 4am
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {

        // bunny peeks over the top right corner, drawn BEFORE the card so the card hides its lower half <3
        PixelArtMascot(
            expression = when {
                cheering -> MascotExpression.RESPONDING
                isLowBattery -> MascotExpression.LOW_BATTERY
                !isOnline -> MascotExpression.OFFLINE
                else -> MascotExpression.IDLE
            },
            size = 64.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-16).dp, y = (-24).dp)
                .pointerInput(Unit) { detectTapGestures { cheering = true } }
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .border(
                    width = 4.dp,
                    color = greetingOutlineColor,
                    shape = RoundedCornerShape(cardRoundness + 4.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = greetingBgColor),
            shape = RoundedCornerShape(cardRoundness + 4.dp)
        ) {
            Text(
                text = greeting,
                fontSize = if (greeting.length > 22) 22.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                color = greetingColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                textAlign = TextAlign.Center
            )
        }

        BowSticker(
            size = 52.dp,
            color = greetingColor,
            rotation = 0f,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp)
        )
    }
}

// horizontal row displaying pinned favorite app icons with selection scale animation :3
@Composable
fun FavoritesCard(
    context: Context,
    favoriteApps: List<LauncherApp>,
    favoriteFolders: List<FavoriteFolder>,
    selectedIndex: Int = 0,
    folderIconRefreshKey: Int = 0,
    onAppLongClick: (LauncherApp) -> Unit,
    onFolderClick: (FavoriteFolder) -> Unit,
    onFolderLongClick: (FavoriteFolder) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val lazyRowState = rememberLazyListState()
    val itemCount = favoriteApps.size + favoriteFolders.size

    LaunchedEffect(selectedIndex, itemCount) {
        if (itemCount > 0) {
            val selectedIsVisible =
                lazyRowState.layoutInfo.visibleItemsInfo.any {
                    it.index == selectedIndex
                }

            if (!selectedIsVisible) {
                lazyRowState.animateScrollToItem(
                    selectedIndex.coerceIn(0, itemCount - 1)
                )
            }
        }
    }

    if (itemCount > 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .border(
                    width = 4.dp,
                    color = theme.greetingOutlineColor,
                    shape = RoundedCornerShape(cardRoundness)
                ),
            colors = CardDefaults.cardColors(
                containerColor = cardBgColor
            ),
            shape = RoundedCornerShape(cardRoundness)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "♡ favorites ♡",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardTextColor
                )

                LazyRow(
                    state = lazyRowState,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        favoriteFolders,
                        key = { _, folder -> "folder:${folder.id}" }
                    ) { folderIndex, folder ->
                        val index = favoriteApps.size + folderIndex
                        val isSelected = index == selectedIndex
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 1f,
                            animationSpec = tween(150),
                            label = "favoriteFolderScale"
                        )
                        Column(
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                )
                                .background(
                                    color = if (isSelected) {
                                        theme.selectedContainerColor
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) {
                                        appActionColor
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        onFolderClick(folder)
                                    },
                                    onLongClick = {
                                        onFolderLongClick(folder)
                                    }
                                )
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val folderIconName = remember(
                                context,
                                folder.id,
                                folderIconRefreshKey
                            ) {
                                context.getSharedPreferences(
                                    "launcher_preferences",
                                    Context.MODE_PRIVATE
                                ).getString("custom_folder_icon_${folder.id}", null)
                            }

                            val folderIconBitmap = remember(
                                context,
                                folderIconName,
                                folderIconRefreshKey
                            ) {
                                if (folderIconName == null) {
                                    null
                                } else {
                                    try {
                                        val iconContext = context.createPackageContext(
                                            "kawaii.icon.pack",
                                            Context.CONTEXT_IGNORE_SECURITY
                                        )

                                        val resourceId = iconContext.resources.getIdentifier(
                                            folderIconName,
                                            "drawable",
                                            "kawaii.icon.pack"
                                        )

                                        if (resourceId != 0) {
                                            drawableToBitmap(
                                                iconContext.resources.getDrawable(resourceId, null),
                                                96
                                            ).asImageBitmap()
                                        } else {
                                            null
                                        }
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier.size(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (folderIconBitmap != null) {
                                    Image(
                                        bitmap = folderIconBitmap,
                                        contentDescription = "${folder.name} folder icon",
                                        modifier = Modifier.size(48.dp)
                                    )
                                } else {
                                    Text(
                                        text = "📁",
                                        fontSize = 36.sp
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(
                        favoriteApps,
                        key = { _, app ->
                            "app:${app.resolveInfo.activityInfo.packageName}"
                        }
                    ) { index, app ->
                        val isSelected = index == selectedIndex
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 1f,
                            animationSpec = tween(150),
                            label = "favoriteAppScale"
                        )

                        Box(
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                )
                                .background(
                                    color = if (isSelected) {
                                        theme.selectedContainerColor
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) {
                                        appActionColor
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        context.packageManager
                                            .getLaunchIntentForPackage(
                                                app.resolveInfo.activityInfo.packageName
                                            )?.let(context::startActivity)
                                    },
                                    onLongClick = {
                                        onAppLongClick(app)
                                    }
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = app.imageBitmap,
                                contentDescription = app.name,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
