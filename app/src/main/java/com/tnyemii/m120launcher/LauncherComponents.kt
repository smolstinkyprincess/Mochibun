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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

val cardRoundness = 24.dp

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

    val drawerListState = rememberLazyListState()

    // Update Clock
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            kotlinx.coroutines.delay(10000.milliseconds)
        }
    }

    // Update Battery + WiFi
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

                // First try the modern Android 10+ path
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val wifiInfo =
                        capabilities?.transportInfo as? android.net.wifi.WifiInfo

                    rawSsid = wifiInfo?.ssid
                }

                // M120 fallback
                if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") {
                    try {
                        val wifiManager =
                            context.applicationContext.getSystemService(
                                Context.WIFI_SERVICE
                            ) as? android.net.wifi.WifiManager

                        rawSsid = wifiManager?.connectionInfo?.ssid
                    } catch (_: Exception) {
                        rawSsid = null
                    }
                }

                rawSsid = rawSsid?.removeSurrounding("\"")

                wifiSsid = if (
                    !rawSsid.isNullOrBlank() &&
                    rawSsid != "<unknown ssid>"
                ) {
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

    Card(
        modifier = Modifier
            .border(
                width = 4.dp,
                color = outlineColor,
                shape = RoundedCornerShape(cardRoundness)
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        shape = RoundedCornerShape(cardRoundness)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
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

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
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

@Composable
fun GreetingCard(
    context: Context
) {
    // Cute Greeting
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        // Greeting card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .border(
                    width = 4.dp,
                    color = greetingOutlineColor,
                    shape = RoundedCornerShape(cardRoundness + 4.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = greetingBgColor
            ),
            shape = RoundedCornerShape(cardRoundness + 4.dp)
        ) {
            Text(
                text = "♡ welcome home, emii ♡",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = greetingColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 18.dp
                    ),
                textAlign = TextAlign.Center
            )
        }

        // Bow sitting on top of the card
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

@Composable
fun FavoritesCard(
    context: Context,
    favoriteApps: List<LauncherApp>,
    iconCache: Map<String, Bitmap>,
    selectedIndex: Int = 0,
    onAppLongClick: (LauncherApp) -> Unit
) {
    val lazyRowState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (favoriteApps.isNotEmpty()) {
            lazyRowState.animateScrollToItem(selectedIndex.coerceIn(0, favoriteApps.size - 1))
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
            colors = CardDefaults.cardColors(
                containerColor = Color(0xD9CEBAFF)
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
                                        val launchIntent =
                                            context.packageManager.getLaunchIntentForPackage(
                                                app.resolveInfo.activityInfo.packageName
                                            )

                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        }
                                    },
                                    onLongClick = {
                                        onAppLongClick(app)
                                    }
                                )
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val iconKey = app.resolveInfo.activityInfo.packageName
                            val iconBitmap = iconCache[iconKey]

                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap.asImageBitmap(),
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
}

@Composable
fun AppDrawerGridItem(
    context: Context,
    app: LauncherApp,
    iconCache: Map<String, Bitmap>,
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
                    val launchIntent =
                        context.packageManager
                            .getLaunchIntentForPackage(packageName)

                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                },
                onLongClick = {
                    onAppLongClick(app)
                }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(0xFFFFD1F0)
            } else {
                Color(0xD9CEBAFF)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = iconCache[packageName]

            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = app.resolveInfo.loadLabel(
                        context.packageManager
                    ).toString(),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = app.resolveInfo.loadLabel(context.packageManager).toString(),
                fontSize = 14.sp,
                color = cardTextColor,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Text(
                    text = "♡",
                    color = Color(0xFF9B5FA5),
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun AppDrawerHeader(
    onSwipeDown: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .padding(bottom = 8.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 10) {
                        onSwipeDown()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFD8A9D9))
            )

            Text(
                text = "♡",
                fontSize = 16.sp,
                color = Color(0xFFB77BBF)
            )

            Text(
                text = "All Apps",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9C6DF5)
            )
        }
    }
}

@Composable
fun AppActionDialog(
    app: LauncherApp,
    favoritePackages: List<String>,
    iconCache: Map<String, Bitmap>,
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
    val packageName = app.resolveInfo.activityInfo.packageName
    val appName = app.name
    val iconBitmap = iconCache[packageName]
    val isFavorite = packageName in favoritePackages
    val favoriteIndex = favoritePackages.indexOf(packageName)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBgColor,
        shape = RoundedCornerShape(cardRoundness),
        title = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = appName,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = appName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = cardTextColor,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isFavorite) {
                    if (favoriteIndex > 0) {
                        TextButton(
                            onClick = onMoveFavoriteLeft
                        ) {
                            Text(
                                text = "← Move Left",
                                color = appActionColor,
                                fontSize = 15.sp
                            )
                        }
                    }

                    if (favoriteIndex < favoritePackages.lastIndex) {
                        TextButton(
                            onClick = onMoveFavoriteRight
                        ) {
                            Text(
                                text = "→ Move Right",
                                color = appActionColor,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onToggleFavorite
                ) {
                    Text(
                        text = if (isFavorite) {
                            "☆ Remove from Favorites"
                        } else {
                            "☆ Add to Favorites"
                        },
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }

                TextButton(
                    onClick = onAppInfo
                ) {
                    Text(
                        text = "⚙ App Info",
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }

                TextButton(
                    onClick = onChangeIcon
                ) {
                    Text(
                        text = "🎀 Change icon",
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }

                TextButton(
                    onClick = onResetIcon
                ) {
                    Text(
                        text = "↩ Reset icon",
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }

                TextButton(
                    onClick = onHideApp
                ) {
                    Text(
                        text = "♡ Hide app",
                        color = appActionColor,
                        fontSize = 15.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
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

@Composable
fun KawaiiIconPicker(
    context: Context,
    iconNames: List<String>,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }

    val filteredIcons = remember(searchText, iconNames) {
        if (searchText.isBlank()) {
            iconNames
        } else {
            iconNames.filter {
                it.contains(searchText.trim(), ignoreCase = true)
            }
        }
    }

    val iconPackContext = remember {
        try {
            context.createPackageContext(
                "kawaii.icon.pack",
                Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBgColor,
        shape = RoundedCornerShape(cardRoundness),
        title = {
            Text(
                text = "♡ Choose an icon ♡",
                fontWeight = FontWeight.Bold,
                color = appActionColor
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text("Search icons ♡")
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        filteredIcons,
                        key = { it }
                    ) { iconName ->

                        val imageBitmap = remember(iconName, iconPackContext) {
                            if (iconPackContext == null) {
                                null
                            } else {
                                val resourceId =
                                    iconPackContext.resources.getIdentifier(
                                        iconName,
                                        "drawable",
                                        "kawaii.icon.pack"
                                    )

                                if (resourceId != 0) {
                                    val drawable = iconPackContext.resources.getDrawable(
                                        resourceId,
                                        null
                                    )
                                    drawableToBitmap(drawable).asImageBitmap()
                                } else {
                                    null
                                }
                            }
                        }

                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = iconName,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onIconSelected(iconName)
                                    }
                            )
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

    //Wallpaper Setup
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

            // remember wallpaper uri in prefs
            preferences.edit()
                .putString("wallpaper_uri", uri.toString())
                .apply()
        }
    }

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

    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }

    LaunchedEffect(appListRefreshCount) {
        apps = withContext(Dispatchers.Default) {
            val kawaiiIconContext = try {
                context.createPackageContext("kawaii.icon.pack", Context.CONTEXT_IGNORE_SECURITY)
            } catch (e: Exception) { null }
            val kawaiiIconMap = loadKawaiiIconMap(context)

            context.packageManager
                .queryIntentActivities(intent, 0)
                .map { app ->
                    LauncherApp(
                        resolveInfo = app,
                        name = app.loadLabel(context.packageManager).toString(),
                        icon = getKawaiiIcon(
                            iconPackContext = kawaiiIconContext,
                            iconMap = kawaiiIconMap,
                            packageName = app.activityInfo.packageName,
                            activityName = app.activityInfo.name
                        ) ?: app.loadIcon(context.packageManager)
                    )
                }
                .sortedBy { it.name }
        }
    }

    LaunchedEffect(iconEditingApp) {
        if (iconEditingApp != null) {
            kawaiiIconNames = withContext(Dispatchers.Default) {
                loadAllKawaiiIconNames(context)
            }
        }
    }

    val visibleApps = remember(apps, hiddenPackages) {
        apps.filter { app ->
            app.resolveInfo.activityInfo.packageName !in hiddenPackages
        }
    }

    val favoriteApps = remember(favoritePackages, apps, hiddenPackages) {
        favoritePackages.mapNotNull { packageName ->
            if (packageName in hiddenPackages) {
                null
            } else {
                apps.find {
                    it.resolveInfo.activityInfo.packageName == packageName
                }
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

    LaunchedEffect(selectedDrawerIndex, drawerOpen) {
        if (drawerOpen && visibleApps.isNotEmpty()) {
            listState.animateScrollToItem(selectedDrawerIndex)
        }
    }

    LaunchedEffect(drawerOpen, chatPageOpen, selectedApp, wallpaperMenuOpen, iconPickerOpen) {
        if (!drawerOpen && !chatPageOpen && selectedApp == null && !wallpaperMenuOpen && !iconPickerOpen) {
            focusRequester.requestFocus()
        } else if (drawerOpen) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(homePressCount) {
        if (homePressCount > 0) {
            drawerOpen = false
            selectedApp = null
        }
    }

    val iconCache = remember {
        mutableStateMapOf<String, Bitmap>()
    }

    LaunchedEffect(apps, iconRefreshKey) {
        val iconPackContext = try {
            context.createPackageContext(
                "kawaii.icon.pack",
                Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (e: Exception) {
            null
        }

        val loadedIcons = withContext(Dispatchers.Default) {
            apps.associate { app ->
                val packageName =
                    app.resolveInfo.activityInfo.packageName

                val customIconName =
                    preferences.getString(
                        "custom_icon_$packageName",
                        null
                    )

                val drawable = if (
                    customIconName != null &&
                    iconPackContext != null
                ) {
                    val resourceId =
                        iconPackContext.resources.getIdentifier(
                            customIconName,
                            "drawable",
                            "kawaii.icon.pack"
                        )

                    if (resourceId != 0) {
                        iconPackContext.resources.getDrawable(
                            resourceId,
                            null
                        )
                    } else {
                        app.icon
                    }
                } else {
                    app.icon
                }

                packageName to drawableToBitmap(drawable)
            }
        }

        iconCache.clear()
        iconCache.putAll(loadedIcons)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                if (nativeEvent.action == android.view.KeyEvent.ACTION_DOWN && nativeEvent.repeatCount == 0) {
                    when (nativeEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (drawerOpen) {
                                selectedDrawerIndex =
                                    (selectedDrawerIndex - 1).coerceAtLeast(0)
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
                    wallpaperMenuOpen = true
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

        //cute stickies 4 bg
        StickerExplosion()

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
            // what to draw when app drawer closed/at home
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
                    iconCache = iconCache,
                    selectedIndex = selectedFavoriteIndex,
                    onAppLongClick = { app ->
                        selectedApp = app
                    }
                )
            }

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

            if (chatPageOpen && !drawerOpen) {
                ChatPlaceholderPage(
                    onBack = {
                        chatPageOpen = false
                    }
                )
            }

            //Selected app
            if (selectedApp != null) {
                AppActionDialog(
                    app = selectedApp!!,
                    favoritePackages = favoritePackages,
                    iconCache = iconCache,
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
                    },
                    onDismiss = {
                        iconPickerOpen = false
                        iconEditingApp = null
                    }
                )
            }


            if (drawerOpen) {
                AppDrawerHeader(
                    onSwipeDown = { drawerOpen = false }
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8F0FF))
                        .border(
                            width = 2.dp,
                            color = Color(0xFFCE93D8),
                            shape = RoundedCornerShape(16.dp)
                        ),
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
                            iconCache = iconCache,
                            isSelected = index == selectedDrawerIndex,
                            onAppLongClick = { selectedApp = it }
                        )
                    }
                }
            }
        }
    }
}
