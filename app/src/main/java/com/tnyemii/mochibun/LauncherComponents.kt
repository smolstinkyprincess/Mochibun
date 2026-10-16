package com.tnyemii.mochibun

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val cardRoundness = 24.dp

// displays current time, date, battery level, and Wi-Fi connection status :3
@Composable
fun StatusCard(
    context: Context,
    cardBgColor: Color,
    cardTextColor: Color,
    outlineColor: Color,
    isFocused: Boolean = false,
    onClick: (() -> Unit)? = null,
    onWifiChanged: (Boolean) -> Unit = {},
    onLowBatteryChanged: (Boolean) -> Unit = {},
) {
    val theme = LocalLauncherTheme.current
    val roundness = theme.cardRoundnessDp.dp
    val shape = RoundedCornerShape(roundness)
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnWifiChanged by rememberUpdatedState(onWifiChanged)
    val currentOnLowBatteryChanged by rememberUpdatedState(onLowBatteryChanged)

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    val initialNow = remember { Date() }
    var formattedTime by remember { mutableStateOf(timeFormat.format(initialNow)) }
    var formattedDate by remember { mutableStateOf(dateFormat.format(initialNow)) }
    var batteryLevel by remember { mutableIntStateOf(0) }
    var wifiConnected by remember { mutableStateOf(false) }
    var wifiSsid by remember { mutableStateOf<String?>(null) }

    // clock ticker coroutine updates on minute boundaries only while launcher is RESUMED <3
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val nowMs = System.currentTimeMillis()
                val nowDate = Date(nowMs)
                val newTime = timeFormat.format(nowDate)
                val newDate = dateFormat.format(nowDate)
                if (formattedTime != newTime) formattedTime = newTime
                if (formattedDate != newDate) formattedDate = newDate

                val delayToNextMinute = (60_000L - (nowMs % 60_000L)).coerceIn(1_000L, 60_000L)
                delay(delayToNextMinute)
            }
        }
    }

    // periodic background status reader for battery and Wi-Fi SSID only while RESUMED :3
    LaunchedEffect(lifecycleOwner, context) {
        val appContext = context.applicationContext ?: context
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val status = withContext(Dispatchers.IO) {
                    val level = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val lowBat = level in 0..20 && !batteryManager.isCharging

                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    val isWifi = capabilities?.hasTransport(
                        android.net.NetworkCapabilities.TRANSPORT_WIFI
                    ) == true

                    val ssid = if (isWifi) {
                        var rawSsid: String? = null

                        // modern Android 10+ transport info path <3
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val wifiInfo = capabilities?.transportInfo as? android.net.wifi.WifiInfo
                            rawSsid = wifiInfo?.ssid
                        }

                        // fallback for older API or MTK hardware :3
                        if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") {
                            try {
                                val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                                rawSsid = wifiManager?.connectionInfo?.ssid
                            } catch (_: Exception) {
                                rawSsid = null
                            }
                        }

                        rawSsid = rawSsid?.removeSurrounding("\"")

                        if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                            rawSsid
                        } else {
                            "Wi-Fi"
                        }
                    } else {
                        null
                    }

                    QuadStatus(level, lowBat, isWifi, ssid)
                }

                if (batteryLevel != status.batteryLevel) batteryLevel = status.batteryLevel
                currentOnLowBatteryChanged(status.isLowBattery)
                if (wifiConnected != status.isWifi) wifiConnected = status.isWifi
                currentOnWifiChanged(status.isWifi)
                if (wifiSsid != status.wifiSsid) wifiSsid = status.wifiSsid

                delay(30_000L)
            }
        }
    }

    Card(
        modifier = Modifier
            .border(
                width = if (isFocused) 3.5.dp else 4.dp,
                color = if (isFocused) theme.appActionColor else outlineColor,
                shape = shape
            )
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) theme.selectedContainerColor else cardBgColor
        ),
        shape = shape
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formattedTime,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) theme.fgTextColor else cardTextColor
            )

            Text(
                text = formattedDate,
                fontSize = 14.sp,
                color = if (isFocused) theme.fgTextColor else cardTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🔋 $batteryLevel%   ",
                    fontSize = 14.sp,
                    color = if (isFocused) theme.fgTextColor else cardTextColor
                )

                if (wifiConnected) {
                    Text(
                        text = wifiSsid ?: "Wi-Fi",
                        fontSize = 14.sp,
                        color = if (isFocused) theme.fgTextColor else cardTextColor
                    )

                    Spacer(modifier = Modifier.width(3.dp))

                    TinyWifiIcon(
                        modifier = Modifier.size(14.dp),
                        color = if (isFocused) theme.fgTextColor else cardTextColor
                    )
                } else {
                    Text(
                        text = "♡ offline",
                        fontSize = 14.sp,
                        color = if (isFocused) theme.fgTextColor else cardTextColor
                    )
                }
            }

            if (isFocused) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "◀ widgets  •  OK options  •  chat ▶",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.appActionColor
                )
            }
        }
    }
}

private data class QuadStatus(
    val batteryLevel: Int,
    val isLowBattery: Boolean,
    val isWifi: Boolean,
    val wifiSsid: String?
)

// top greeting card header with decorative ribbon bow sticker <3
@Composable
fun GreetingCard(
    context: Context,
    isOnline: Boolean = true,
    isLowBattery: Boolean = false,
    mascotEnabled: Boolean = true
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val theme = LocalLauncherTheme.current
    val cardRoundness = theme.cardRoundnessDp.dp
    val greetingOutlineColor = theme.greetingOutlineColor
    val greetingBgColor = theme.greetingBgColor
    val greetingColor = theme.greetingColor

    // bunny cheers for a moment when tapped :3
    var cheering by remember { mutableStateOf(false) }
    LaunchedEffect(cheering) {
        if (cheering) {
            delay(1500)
            cheering = false
        }
    }

    // what hour is it? checked once a minute while RESUMED so the greeting changes by itself <3
    var hour by remember {
        mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                if (hour != currentHour) hour = currentHour
                delay(60_000L)
            }
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
            enabled = mascotEnabled,
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

// horizontal row displaying pinned favorite app icons and folders with selection scale animation :3
@Composable
fun FavoritesCard(
    context: Context,
    favoriteApps: List<LauncherApp>,
    favoriteFolders: List<FavoriteFolder>,
    selectedIndex: Int = 0,
    isCardFocused: Boolean = true,
    folderIconRefreshKey: Int = 0,
    onAppClick: ((LauncherApp) -> Unit)? = null,
    onAppLongClick: (LauncherApp) -> Unit,
    onFolderClick: (FavoriteFolder) -> Unit,
    onFolderLongClick: (FavoriteFolder) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val roundness = theme.cardRoundnessDp.dp
    val lazyRowState = rememberLazyListState()
    val itemCount = favoriteApps.size + favoriteFolders.size
    val preferences = remember(context) {
        context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
    }

    LaunchedEffect(selectedIndex, itemCount) {
        if (itemCount > 0) {
            val targetIndex = selectedIndex.coerceIn(0, itemCount - 1)
            val selectedItemInfo = lazyRowState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.index == targetIndex
            }
            val viewportEnd = lazyRowState.layoutInfo.viewportEndOffset
            val isFullyVisible = selectedItemInfo != null &&
                selectedItemInfo.offset >= 0 &&
                (selectedItemInfo.offset + selectedItemInfo.size) <= viewportEnd

            if (!isFullyVisible) {
                lazyRowState.animateScrollToItem(targetIndex)
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
                    shape = RoundedCornerShape(roundness)
                ),
            colors = CardDefaults.cardColors(
                containerColor = theme.cardBgColor
            ),
            shape = RoundedCornerShape(roundness)
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
                    color = theme.cardTextColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    state = lazyRowState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(
                        favoriteApps,
                        key = { _, app -> app.favoriteKey }
                    ) { index, app ->
                        val isSelected = isCardFocused && index == selectedIndex
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 1f,
                            animationSpec = tween(150),
                            label = "favoriteAppScale"
                        )
                        val itemShape = RoundedCornerShape(12.dp)

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .background(
                                    color = if (isSelected) {
                                        theme.selectedContainerColor
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = itemShape
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) {
                                        theme.appActionColor
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = itemShape
                                )
                                .combinedClickable(
                                    onClick = {
                                        onAppClick?.invoke(app)
                                        context.packageManager
                                            .getLaunchIntentForPackage(app.packageName)
                                            ?.let(context::startActivity)
                                    },
                                    onLongClick = {
                                        onAppClick?.invoke(app)
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

                    itemsIndexed(
                        favoriteFolders,
                        key = { _, folder -> "folder:${folder.id}" }
                    ) { folderIndex, folder ->
                        val index = favoriteApps.size + folderIndex
                        val isSelected = isCardFocused && index == selectedIndex
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 1f,
                            animationSpec = tween(150),
                            label = "favoriteFolderScale"
                        )
                        val itemShape = RoundedCornerShape(12.dp)

                        Column(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .background(
                                    color = if (isSelected) {
                                        theme.selectedContainerColor
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = itemShape
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) {
                                        theme.appActionColor
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = itemShape
                                )
                                .combinedClickable(
                                    onClick = {
                                        onFolderClick(folder)
                                    },
                                    onLongClick = {
                                        onFolderLongClick(folder)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val folderIconName = remember(
                                preferences,
                                folder.id,
                                folderIconRefreshKey
                            ) {
                                preferences.getString("custom_folder_icon_${folder.id}", null)
                            }

                            val folderIconBitmap = remember(
                                context,
                                folderIconName,
                                folderIconRefreshKey
                            ) {
                                getCachedKawaiiImageBitmap(context, folderIconName, 96)
                            }

                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (folderIconBitmap != null) {
                                    Image(
                                        bitmap = folderIconBitmap,
                                        contentDescription = "${folder.name} folder icon",
                                        modifier = Modifier.size(42.dp)
                                    )
                                } else {
                                    Text(
                                        text = "📁",
                                        fontSize = 32.sp
                                    )
                                }
                            }

                            Text(
                                text = folder.name,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) theme.fgTextColor else theme.cardTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 64.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
