package com.tnyemii.m120launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
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
import com.tnyemii.m120launcher.ui.theme.appActionColor
import com.tnyemii.m120launcher.ui.theme.cardBgColor
import com.tnyemii.m120launcher.ui.theme.cardTextColor
import com.tnyemii.m120launcher.ui.theme.greetingBgColor
import com.tnyemii.m120launcher.ui.theme.greetingColor
import com.tnyemii.m120launcher.ui.theme.greetingOutlineColor
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
    outlineColor: Color
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

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val isWifi = capabilities?.hasTransport(
                android.net.NetworkCapabilities.TRANSPORT_WIFI
            ) == true

            wifiConnected = isWifi

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

                android.util.Log.d(
                    "M120Wifi",
                    "connected=$wifiConnected, ssid=$wifiSsid, rawSsid=$rawSsid"
                )

            } else {
                wifiSsid = null
            }

            kotlinx.coroutines.delay(30000.milliseconds)
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

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
                text = timeFormat.format(currentTime),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = cardTextColor
            )

            Text(
                text = dateFormat.format(currentTime),
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
fun GreetingCard(context: Context) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
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
                text = "♡ welcome home, emii ♡",
                fontSize = 24.sp,
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
            color = Color(0xFFD8A9D9),
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
     selectedIndex: Int = 0,
    onAppLongClick: (LauncherApp) -> Unit
) {
    val lazyRowState = rememberLazyListState()

    // auto scroll to selected favorite item when navigating with DPAD <3
    LaunchedEffect(selectedIndex, favoriteApps.size) {
        if (favoriteApps.isNotEmpty()) {
            val visibleItems = lazyRowState.layoutInfo.visibleItemsInfo
            val selectedIsVisible = visibleItems.any { it.index == selectedIndex }

            if (!selectedIsVisible) {
                lazyRowState.animateScrollToItem(selectedIndex)
            }
        }
    }

    if (favoriteApps.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .border(
                    width = 4.dp,
                    color = greetingOutlineColor,
                    shape = RoundedCornerShape(cardRoundness)
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xD9CEBAFF)),
            shape = RoundedCornerShape(cardRoundness)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                    itemsIndexed(favoriteApps, key = { _, app -> app.resolveInfo.activityInfo.packageName }) { index, app ->
                        val appName = app.name
                        val isSelected = (index == selectedIndex)
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 1.0f,
                            animationSpec = tween(durationMillis = 150)
                        )

                        Column(
                            modifier = Modifier
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                                .background(
                                    color = if (isSelected) Color(0xFFFFD1F0) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) Color(0xFF9B5FA5) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(
                                            app.resolveInfo.activityInfo.packageName
                                        )
                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        }
                                    },
                                    onLongClick = { onAppLongClick(app) }
                                )
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = app.iconBitmap.asImageBitmap(),
                                contentDescription = appName,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

//alphabet strip in appdrawer
fun firstLetterOf(label: String): Char {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

@Composable
fun AlphabetIndex(
    availableLetters: Set<Char>,
    onLetterTap: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = remember { ('A'..'Z').toList() + '#' }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(letter) {
                        detectTapGestures { onLetterTap(letter) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    fontSize = 11.sp,
                    color = Color(0xFF9B5FA5).copy(
                        alpha = if (letter in availableLetters) 1f else 0.35f
                    )
                )
            }
        }
    }
}


// single app item row in the vertical app drawer list <3
@Composable
fun AppDrawerGridItem(
    context: Context,
    app: LauncherApp,
    isSelected: Boolean = false,
    onAppLongClick: (LauncherApp) -> Unit
) {
    val packageName = app.resolveInfo.activityInfo.packageName
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 150)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(
                onClick = {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                },
                onLongClick = { onAppLongClick(app) }
            ),
        shape = RoundedCornerShape(cardRoundness),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFFFD1F0) else Color(0xFFCEBAFF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = app.iconBitmap.asImageBitmap(),
                contentDescription = app.name,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.width(32.dp))

            Text(
                text = app.name,
                fontSize = 20.sp,
                color = cardTextColor,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
            )

            if (isSelected) {
                Text(
                    text = "♡",
                    color = Color(0xFF9B5FA5),
                    fontSize = 32.sp
                )
            }
        }
    }
}

// app drawer header bar showing title and swipe down button :3
@Composable
fun AppDrawerHeader(onSwipeDown: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSwipeDown() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "♡ apps ♡",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = cardTextColor
            )
            Text(
                text = "▾",
                fontSize = 18.sp,
                color = cardTextColor
            )
        }
    }
}

// long-press dialog for app actions (favorite reorder, custom icon, app info, hide) <3
@Composable
fun AppActionDialog(
    app: LauncherApp,
    favoritePackages: List<String>,
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
    cardRoundness: Dp,
    onDismiss: () -> Unit,
    onMoveFavoriteLeft: () -> Unit,
    onMoveFavoriteRight: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAppInfo: () -> Unit,
    onChangeIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onHideApp: () -> Unit
) {
    val pkgName = app.resolveInfo.activityInfo.packageName
    val isFav = pkgName in favoritePackages

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBgColor,
        shape = RoundedCornerShape(cardRoundness),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = app.iconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = app.name,
                    color = cardTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // reorder buttons if app is currently in favorites :3
                if (isFav) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onMoveFavoriteLeft) {
                            Text("◀ Move Left", color = appActionColor)
                        }
                        TextButton(onClick = onMoveFavoriteRight) {
                            Text("Move Right ▶", color = appActionColor)
                        }
                    }
                }

                // toggle favorite status <3
                TextButton(onClick = onToggleFavorite) {
                    Text(
                        text = if (isFav) "💔 Remove from favorites" else "💖 Add to favorites",
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }

                // custom icon pack picker :3
                TextButton(onClick = onChangeIcon) {
                    Text(
                        text = "🎨 Change app icon",
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }

                // reset custom icon back to default
                TextButton(onClick = onResetIcon) {
                    Text(
                        text = "↺ Reset icon to default",
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }

                // system app details info <3
                TextButton(onClick = onAppInfo) {
                    Text(
                        text = "ℹ️ App info",
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }

                // hide app from app drawer :3
                TextButton(onClick = onHideApp) {
                    Text(
                        text = "🙈 Hide app",
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = appActionColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// dialog search picker for manual Kawaii Icon Pack icon assignment <3
@Composable
fun KawaiiIconPicker(
    context: Context,
    iconNames: List<String>,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredIcons = remember(searchQuery, iconNames) {
        if (searchQuery.isBlank()) {
            iconNames.take(60)
        } else {
            iconNames.filter { it.contains(searchQuery.trim(), ignoreCase = true) }.take(60)
        }
    }

    val iconPackContext = remember {
        try {
            context.createPackageContext("kawaii.icon.pack", Context.CONTEXT_IGNORE_SECURITY)
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBgColor,
        shape = RoundedCornerShape(cardRoundness),
        title = {
            Text("Select Kawaii Icon ♡", fontWeight = FontWeight.Bold, color = appActionColor)
        },
        text = {
            Column(modifier = Modifier.height(320.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search icon name...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredIcons, key = { it }) { iconName ->
                        val resId = remember(iconName) {
                            iconPackContext?.resources?.getIdentifier(iconName, "drawable", "kawaii.icon.pack") ?: 0
                        }

                        if (resId != 0 && iconPackContext != null) {
                            val drawable = remember(resId) {
                                iconPackContext.resources.getDrawable(resId, null)
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onIconSelected(iconName) },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = drawableToBitmap(drawable).asImageBitmap(),
                                    contentDescription = iconName,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// custom vector drawn Wi-Fi status icon for StatusCard :3
@Composable
fun TinyWifiIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    Canvas(
        modifier = modifier.size(16.dp)
    ) {
        val strokeWidth = 2.dp.toPx()

        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )

        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width * 0.2f, size.height * 0.2f),
            size = Size(size.width * 0.6f, size.height * 0.6f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )

        drawCircle(
            color = color,
            radius = strokeWidth * 0.8f,
            center = Offset(size.width / 2f, size.height * 0.78f)
        )
    }
}

// main launcher composable managing focus, gestures, state, and page navigation <3
@Composable
fun MainLauncher(name: String, modifier: Modifier = Modifier, homePressCount: Int = 0, appListRefreshCount: Int = 0) {
    val context = LocalContext.current

    val preferences = remember {
        context.getSharedPreferences(
            "launcher_preferences",
            Context.MODE_PRIVATE
        )
    }
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    // wallpaper selection and persistence state :3
    var wallpaperUri by remember {
        mutableStateOf(
            preferences.getString("wallpaper_uri", null)?.let {
                Uri.parse(it)
            }
        )
    }
    var wallpaperBitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }
    val wallpaperPicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        wallpaperUri = uri

        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            preferences.edit()
                .putString("wallpaper_uri", uri.toString())
                .apply()
        }
    }

    // memory-efficient sampled wallpaper loader <3
    LaunchedEffect(wallpaperUri) {
        val uri = wallpaperUri ?: run {
            wallpaperBitmap = null
            return@LaunchedEffect
        }

        wallpaperBitmap = withContext(Dispatchers.IO) {
            loadWallpaperBitmap(context, uri)
        }
    }

    var hiddenPackages by remember {
        mutableStateOf(
            preferences.getStringSet("hidden_apps", emptySet())?.toSet()
                ?: emptySet()
        )
    }

    var favoritePackages by remember {
        mutableStateOf(
            preferences
                .getString("favorite_packages_ordered", "")
                ?.split("|")
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        )
    }

    var drawerOpen by remember { mutableStateOf(false) }
    var chatPageOpen by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<LauncherApp?>(null) }
    var iconEditingApp by remember { mutableStateOf<LauncherApp?>(null) }
    var iconPickerOpen by remember { mutableStateOf(false) }
    var kawaiiIconNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var iconRefreshKey by remember { mutableStateOf(0) }

    var wallpaperMenuOpen by remember {
        mutableStateOf(false)
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }

    // queries installed apps, applies KawaiiIconPack automatic mappings & custom icon overrides, and loads bitmaps in background :3
    LaunchedEffect(appListRefreshCount, iconRefreshKey) {
        apps = withContext(Dispatchers.Default) {
            val kawaiiIconContext = try {
                context.createPackageContext("kawaii.icon.pack", Context.CONTEXT_IGNORE_SECURITY)
            } catch (e: Exception) { null }
            val kawaiiIconMap = loadKawaiiIconMap(context)

            context.packageManager
                .queryIntentActivities(intent, 0)
                .map { app ->
                    val pkgName = app.activityInfo.packageName
                    val customIconName = preferences.getString("custom_icon_$pkgName", null)

                    val drawable = if (customIconName != null && kawaiiIconContext != null) {
                        val resourceId = kawaiiIconContext.resources.getIdentifier(
                            customIconName,
                            "drawable",
                            "kawaii.icon.pack"
                        )
                        if (resourceId != 0) {
                            kawaiiIconContext.resources.getDrawable(resourceId, null)
                        } else {
                            getKawaiiIcon(kawaiiIconContext, kawaiiIconMap, pkgName, app.activityInfo.name)
                                ?: app.loadIcon(context.packageManager)
                        }
                    } else {
                        getKawaiiIcon(kawaiiIconContext, kawaiiIconMap, pkgName, app.activityInfo.name)
                            ?: app.loadIcon(context.packageManager)
                    }

                    LauncherApp(
                        resolveInfo = app,
                        name = app.loadLabel(context.packageManager).toString(),
                        iconBitmap = drawableToBitmap(drawable, 96)
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    // async load icon names list for icon picker <3
    LaunchedEffect(iconEditingApp) {
        if (iconEditingApp != null) {
            kawaiiIconNames = withContext(Dispatchers.Default) {
                loadAllKawaiiIconNames(context)
            }
        } else {
            kawaiiIconNames = emptyList()
        }
    }

    val visibleApps = remember(apps, hiddenPackages) {
        apps.filter { app ->
            app.resolveInfo.activityInfo.packageName !in hiddenPackages
        }
    }

    // remembers the list position where each starting letter first appears :3
    val letterStarts = remember(visibleApps) {
        val map = linkedMapOf<Char, Int>()
        visibleApps.forEachIndexed { index, app ->
            map.getOrPut(firstLetterOf(app.name)) { index }
        }
        map
    }

    var favoriteApps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }

    // fast async loader for favorite apps (~20ms) so favorites card displays instantly <3
    LaunchedEffect(favoritePackages, hiddenPackages, appListRefreshCount, iconRefreshKey) {
        favoriteApps = withContext(Dispatchers.Default) {
            val kawaiiIconContext = try {
                context.createPackageContext("kawaii.icon.pack", Context.CONTEXT_IGNORE_SECURITY)
            } catch (e: Exception) { null }
            val kawaiiIconMap = loadKawaiiIconMap(context)

            favoritePackages
                .filter { it !in hiddenPackages }
                .mapNotNull { pkgName ->
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName) ?: return@mapNotNull null
                    val resolveInfo = context.packageManager.resolveActivity(launchIntent, 0) ?: return@mapNotNull null
                    val activityName = resolveInfo.activityInfo.name
                    val customIconName = preferences.getString("custom_icon_$pkgName", null)

                    val drawable = if (customIconName != null && kawaiiIconContext != null) {
                        val resourceId = kawaiiIconContext.resources.getIdentifier(
                            customIconName,
                            "drawable",
                            "kawaii.icon.pack"
                        )
                        if (resourceId != 0) {
                            kawaiiIconContext.resources.getDrawable(resourceId, null)
                        } else {
                            getKawaiiIcon(kawaiiIconContext, kawaiiIconMap, pkgName, activityName)
                                ?: resolveInfo.loadIcon(context.packageManager)
                        }
                    } else {
                        getKawaiiIcon(kawaiiIconContext, kawaiiIconMap, pkgName, activityName)
                            ?: resolveInfo.loadIcon(context.packageManager)
                    }

                    LauncherApp(
                        resolveInfo = resolveInfo,
                        name = resolveInfo.loadLabel(context.packageManager).toString(),
                        iconBitmap = drawableToBitmap(drawable, 96)
                    )
                }
        }
    }

    var selectedFavoriteIndex by remember { mutableIntStateOf(0) }
    var selectedDrawerIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(favoriteApps) {
        selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(0, (favoriteApps.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(visibleApps) {
        selectedDrawerIndex = selectedDrawerIndex.coerceIn(0, (visibleApps.size - 1).coerceAtLeast(0))
    }

    // auto scroll to highlighted item in app drawer :3
    LaunchedEffect(selectedDrawerIndex, drawerOpen, visibleApps.size) {
        if (drawerOpen && visibleApps.isNotEmpty()) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val selectedItem = visibleItems.firstOrNull {
                it.index == selectedDrawerIndex
            }

            if (selectedItem == null) {
                listState.animateScrollToItem(selectedDrawerIndex)
            }
        }
    }

    // focus requester handling for physical hardware key navigation <3
    LaunchedEffect(drawerOpen, chatPageOpen, selectedApp, wallpaperMenuOpen, iconPickerOpen) {
        if (!drawerOpen && !chatPageOpen && selectedApp == null && !wallpaperMenuOpen && !iconPickerOpen) {
            focusRequester.requestFocus()
        } else if (drawerOpen) {
            focusRequester.requestFocus()
        }
    }

    // physical home button press brings user back to home screen :3
    LaunchedEffect(homePressCount) {
        if (homePressCount > 0) {
            drawerOpen = false
            selectedApp = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                if (nativeEvent.action == android.view.KeyEvent.ACTION_DOWN && nativeEvent.repeatCount == 0) {
                    when (nativeEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (drawerOpen) {
                                if (selectedDrawerIndex == 0) { drawerOpen = false }
                                else {
                                    selectedDrawerIndex =
                                        (selectedDrawerIndex - 1).coerceAtLeast(0)
                                }
                            } else if (!chatPageOpen) {
                                drawerOpen = true
                            }
                            true
                        }

                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (drawerOpen) {
                                selectedDrawerIndex =
                                    (selectedDrawerIndex + 1)
                                        .coerceIn(0, (visibleApps.size - 1).coerceAtLeast(0))
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (drawerOpen) {
                                if (visibleApps.isNotEmpty()) {
                                    selectedDrawerIndex = (selectedDrawerIndex - 1).coerceAtLeast(0)
                                }
                            } else if (!chatPageOpen) {
                                if (favoriteApps.isNotEmpty()) {
                                    selectedFavoriteIndex = (selectedFavoriteIndex - 1).coerceAtLeast(0)
                                }
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (drawerOpen) {
                                if (visibleApps.isNotEmpty()) {
                                    selectedDrawerIndex = (selectedDrawerIndex + 1).coerceIn(0, visibleApps.size - 1)
                                }
                            } else if (!chatPageOpen) {
                                if (favoriteApps.isNotEmpty()) {
                                    selectedFavoriteIndex = (selectedFavoriteIndex + 1).coerceIn(0, (favoriteApps.size - 1).coerceAtLeast(0))
                                }
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (drawerOpen) {
                                val app = visibleApps.getOrNull(selectedDrawerIndex)
                                if (app != null) {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(
                                        app.resolveInfo.activityInfo.packageName
                                    )
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
                                }
                            } else if (!chatPageOpen) {
                                val app = favoriteApps.getOrNull(selectedFavoriteIndex)
                                if (app != null) {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(
                                        app.resolveInfo.activityInfo.packageName
                                    )
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    if (!chatPageOpen && !drawerOpen) {
                        wallpaperMenuOpen = true
                    }
                }
            )
    ) {
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // cute sticker decorations :3
        if (!drawerOpen && !chatPageOpen) {
            StickerExplosion()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(16.dp)
                .pointerInput(drawerOpen, chatPageOpen) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (!drawerOpen && !chatPageOpen && dragAmount < -32) {
                            drawerOpen = true
                        }
                    }
                }
                .pointerInput(drawerOpen, chatPageOpen) {
                    var totalDrag = 0f

                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            totalDrag += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            if (!drawerOpen) {
                                if (totalDrag < -80f) {
                                    chatPageOpen = true
                                } else if (totalDrag > 80f) {
                                    chatPageOpen = false
                                }
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = {
                            totalDrag = 0f
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // main launcher cards on home screen <3
            if (!drawerOpen && !chatPageOpen) {
                GreetingCard(context = context)
                Spacer(modifier = Modifier.height(8.dp))
                StatusCard(
                    context = context,
                    cardBgColor = cardBgColor,
                    cardTextColor = cardTextColor,
                    outlineColor = greetingOutlineColor
                )
                FavoritesCard(
                    context = context,
                    favoriteApps = favoriteApps,
                    selectedIndex = selectedFavoriteIndex,
                    onAppLongClick = { app ->
                        selectedApp = app
                    }
                )
            }

            // long-press wallpaper menu dialog :3
            if (wallpaperMenuOpen) {
                AlertDialog(
                    onDismissRequest = {
                        wallpaperMenuOpen = false
                    },
                    containerColor = cardBgColor,
                    shape = RoundedCornerShape(cardRoundness),
                    title = {
                        Text(
                            text = "♡ Wallpaper options ♡",
                            fontWeight = FontWeight.Bold,
                            color = appActionColor
                        )
                    },
                    text = {
                        Column {
                            TextButton(
                                onClick = {
                                    wallpaperMenuOpen = false
                                    try {
                                        wallpaperPicker.launch(arrayOf("image/*"))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            ) {
                                Text(
                                    text = "🖼 Pick wallpaper from gallery",
                                    color = appActionColor,
                                    fontSize = 15.sp
                                )
                            }

                            if (wallpaperUri != null) {
                                TextButton(
                                    onClick = {
                                        wallpaperMenuOpen = false
                                        wallpaperUri = null
                                        preferences.edit()
                                            .remove("wallpaper_uri")
                                            .apply()
                                    }
                                ) {
                                    Text(
                                        text = "↩ Reset to default wallpaper",
                                        color = appActionColor,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                wallpaperMenuOpen = false
                            }
                        ) {
                            Text(
                                text = "Cancel",
                                color = appActionColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }

            // AI assistant chat page overlay <3
            if (chatPageOpen && !drawerOpen) {
                ChatPlaceholderPage(
                    onBack = {
                        chatPageOpen = false
                    }
                )
            }

            // app options long-press dialog :3
            if (selectedApp != null) {
                AppActionDialog(
                    app = selectedApp!!,
                    favoritePackages = favoritePackages,
                    cardBgColor = cardBgColor,
                    cardTextColor = cardTextColor,
                    appActionColor = appActionColor,
                    cardRoundness = cardRoundness,
                    onDismiss = {
                        selectedApp = null
                    },
                    onMoveFavoriteLeft = {
                        val pkgName = selectedApp!!.resolveInfo.activityInfo.packageName
                        val list = favoritePackages.toMutableList()
                        val index = list.indexOf(pkgName)
                        if (index > 0) {
                            list.removeAt(index)
                            list.add(index - 1, pkgName)
                            favoritePackages = list
                            preferences.edit()
                                .putString("favorite_packages_ordered", list.joinToString("|"))
                                .apply()
                        }
                        selectedApp = null
                    },
                    onMoveFavoriteRight = {
                        val pkgName = selectedApp!!.resolveInfo.activityInfo.packageName
                        val list = favoritePackages.toMutableList()
                        val index = list.indexOf(pkgName)
                        if (index >= 0 && index < list.lastIndex) {
                            list.removeAt(index)
                            list.add(index + 1, pkgName)
                            favoritePackages = list
                            preferences.edit()
                                .putString("favorite_packages_ordered", list.joinToString("|"))
                                .apply()
                        }
                        selectedApp = null
                    },
                    onToggleFavorite = {
                        val pkgName = selectedApp!!.resolveInfo.activityInfo.packageName
                        val list = favoritePackages.toMutableList()
                        if (pkgName in list) {
                            list.remove(pkgName)
                        } else {
                            list.add(pkgName)
                        }
                        favoritePackages = list
                        preferences.edit()
                            .putString("favorite_packages_ordered", list.joinToString("|"))
                            .apply()
                        selectedApp = null
                    },
                    onAppInfo = {
                        val pkgName = selectedApp!!.resolveInfo.activityInfo.packageName
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$pkgName")
                        )
                        context.startActivity(intent)
                        selectedApp = null
                    },
                    onChangeIcon = {
                        iconEditingApp = selectedApp
                        iconPickerOpen = true
                        selectedApp = null
                    },
                    onResetIcon = {
                        val pkgName = selectedApp!!.resolveInfo.activityInfo.packageName
                        preferences.edit()
                            .remove("custom_icon_$pkgName")
                            .apply()
                        iconRefreshKey++
                        selectedApp = null
                    },
                    onHideApp = {
                        val pkgName = selectedApp!!.resolveInfo.activityInfo.packageName
                        val newHidden = hiddenPackages.toMutableSet().apply {
                            add(pkgName)
                        }
                        hiddenPackages = newHidden
                        preferences.edit()
                            .putStringSet("hidden_apps", newHidden)
                            .apply()
                        selectedApp = null
                    }
                )
            }

            // custom KawaiiIconPack icon picker dialog <3
            if (iconPickerOpen && iconEditingApp != null) {
                KawaiiIconPicker(
                    context = context,
                    iconNames = kawaiiIconNames,
                    onIconSelected = { selectedIconName ->
                        val pkgName = iconEditingApp!!.resolveInfo.activityInfo.packageName
                        preferences.edit()
                            .putString("custom_icon_$pkgName", selectedIconName)
                            .apply()

                        iconRefreshKey++
                        iconPickerOpen = false
                        iconEditingApp = null
                        kawaiiIconNames = emptyList()
                    },
                    onDismiss = {
                        iconPickerOpen = false
                        iconEditingApp = null
                        kawaiiIconNames = emptyList()
                    }
                )
            }

            // app drawer view overlay :3
            if (drawerOpen) {
                AppDrawerHeader(
                    onSwipeDown = { drawerOpen = false }
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(cardRoundness))
                            .background(Color.Transparent)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            visibleApps,
                            key = { _, app ->
                                "${app.resolveInfo.activityInfo.packageName}/${app.resolveInfo.activityInfo.name}"
                            }
                        ) { index, app ->
                            AppDrawerGridItem(
                                context = context,
                                app = app,
                                isSelected = index == selectedDrawerIndex,
                                onAppLongClick = { selectedApp = it }
                            )
                        }
                    }

                    AlphabetIndex(
                        availableLetters = letterStarts.keys,
                        onLetterTap = { letter ->
                            val target = letterStarts[letter]
                                ?: letterStarts.entries.firstOrNull { it.key > letter }?.value
                                ?: (visibleApps.size - 1)
                            if (target >= 0) {
                                selectedDrawerIndex = target
                                scope.launch { listState.scrollToItem(target) }
                            }
                        }
                    )
                }
            }
        }
    }
}
