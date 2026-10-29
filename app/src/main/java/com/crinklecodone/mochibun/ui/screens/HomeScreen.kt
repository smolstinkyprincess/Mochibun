/**
 * Main Home Screen surface for Mochibun.
 * Orchestrates the greeting banner, digital clock, favorite apps/folders card,
 * and bottom app drawer handle within the Chinoe AT-M120's 480x800 display envelope.
 */
package com.crinklecodone.mochibun.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.repository.NotificationTracker
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.data.model.FavoriteSlotItem
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.data.repository.IconPackManager
import com.crinklecodone.mochibun.ui.components.kawaiiCardMotifs
import com.crinklecodone.mochibun.ui.dialogs.PixelRibbonBow
import com.crinklecodone.mochibun.ui.theme.*
import com.crinklecodone.mochibun.ui.components.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// dynamic pastel theme colors provided via LocalLauncherTheme and MochibunThemePalette
private val FavoriteSquircleShape = RoundedCornerShape(16.dp)

/**
 * Main Home Screen composable recreating the kawaii Mochibun layout.
 * Accurately fits 480x800 displays with tight recomposition scopes.
 */
@Composable
fun HomeScreen(
    context: Context,
    favoriteItems: List<FavoriteSlotItem> = emptyList(),
    modifier: Modifier = Modifier,
    userName: String = "",
    selectedFavoriteIndex: Int = 0,
    folderIconRefreshKey: Int = 0,
    homeFocusArea: Int = 0, // 0 = favorites, 1 = header banner, 2 = drawer handle, 3 = clock
    isHomeScreenActive: Boolean = true,
    onHeaderClick: () -> Unit = {},
    onHeaderLongClick: () -> Unit = {},
    onAppClick: (LauncherApp) -> Unit = {},
    onAppLongClick: (LauncherApp) -> Unit = {},
    onFolderClick: (FavoriteFolder) -> Unit = {},
    onFolderLongClick: (FavoriteFolder) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {}
) {
    val theme = LocalLauncherTheme.current
    val notifications by NotificationTracker.notifications.collectAsState()
    val unreadNotifCount = notifications.size

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {

        // Main content column anchored to top within the 800px vertical envelope
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Top "Welcome Home" Banner with customizable user name
            WelcomeHomeBanner(
                userName = userName,
                isFocused = isHomeScreenActive && homeFocusArea == 1,
                onClick = onHeaderClick,
                onLongClick = onHeaderLongClick
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Digital Clock & Date Section
            ClockWidget(
                isFocused = isHomeScreenActive && homeFocusArea == 3,
                onClick = { launchClockApp(context) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Favorites Card
            FavoritesCard(
                context = context,
                favoriteItems = favoriteItems,
                selectedIndex = selectedFavoriteIndex,
                isCardFocused = isHomeScreenActive && homeFocusArea == 0,
                isHomeScreenActive = isHomeScreenActive,
                folderIconRefreshKey = folderIconRefreshKey,
                onAppClick = onAppClick,
                onAppLongClick = onAppLongClick,
                onFolderClick = onFolderClick,
                onFolderLongClick = onFolderLongClick
            )
        }

        // Scalloped lavender clouds matching the App Drawer
        BottomLavenderClouds(
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 4. Bottom Keitai Soft-Key Bar: [ stats ]  ♥⇣♡ ♡⇣♥  [ notifs ]
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SoftKeyPromptPill(
                label = "stats",
                icon = "📊",
                onClick = onNavigateToStats
            )

            BottomDrawerHandle(
                isFocused = isHomeScreenActive && homeFocusArea == 2,
                onClick = onOpenDrawer
            )

            SoftKeyPromptPill(
                label = "notifs",
                icon = "🔔",
                badgeCount = unreadNotifCount,
                onClick = onNavigateToNotifications
            )
        }
    }
}

/**
 * Top "Welcome Home" Banner.
 * Outlined pill container with rounded corners, bow badge anchored at top-center,
 * flanking sparkle/heart accents, and dynamically scaled/truncated user name.
 */
@Composable
fun WelcomeHomeBanner(
    modifier: Modifier = Modifier,
    userName: String = "",
    isFocused: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val pillShape = RoundedCornerShape(32.dp)

    val greetingText = if (userName.isBlank()) {
        "♡ Welcome home ♡"
    } else {
        "♡ Welcome home, ${userName.trim()} ♡"
    }

    val dynamicFontSize = when {
        greetingText.length > 25 -> 14.sp
        greetingText.length > 20 -> 16.sp
        greetingText.length > 16 -> 18.sp
        else -> 20.sp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
    ) {
        // Flanking sparkle accents on the far left and right
        SparkleAccent(
            size = 14.dp,
            color = palette.outline,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 6.dp, y = (-8).dp)
        )
        SparkleAccent(
            size = 12.dp,
            color = palette.primary.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 4.dp, y = 14.dp)
        )
        SparkleAccent(
            size = 14.dp,
            color = palette.outline,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-6).dp, y = (-8).dp)
        )
        SparkleAccent(
            size = 12.dp,
            color = palette.primary.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-4).dp, y = 14.dp)
        )

        // Pill container with enhanced vertical presence
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .graphicsLayer { clip = false }
                .softGlowFocus(isSelected = isFocused, cornerRadius = 32.dp)
                .then(
                    if (!isFocused) {
                        Modifier.border(
                            width = 2.dp,
                            color = theme.greetingOutlineColor,
                            shape = pillShape
                        )
                    } else {
                        Modifier
                    }
                )
                .background(theme.greetingBgColor, pillShape)
                .clip(pillShape)
                .focusable()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 18.dp, vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            // Inner corner sparkles
            SparkleAccent(
                size = 11.dp,
                color = palette.primary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 4.dp, y = (-6).dp)
            )
            SparkleAccent(
                size = 10.dp,
                color = palette.outline,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 2.dp, y = 8.dp)
            )
            SparkleAccent(
                size = 11.dp,
                color = palette.primary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-4).dp, y = (-6).dp)
            )
            SparkleAccent(
                size = 10.dp,
                color = palette.outline,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-2).dp, y = 8.dp)
            )

            // Centered title text with dynamic scaling and clean truncation
            Text(
                text = greetingText,
                fontSize = dynamicFontSize,
                fontWeight = FontWeight.Medium,
                color = theme.greetingColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }

        // Tiny hearts flanking the bow badge
        Text(
            text = "♡",
            fontSize = 13.sp,
            color = palette.primary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = (-36).dp, y = (-12).dp)
        )
        Text(
            text = "♡",
            fontSize = 13.sp,
            color = palette.primary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 36.dp, y = (-12).dp)
        )

        // Decorative ribbon bow badge anchored at top-center overlapping the border
        PixelRibbonBow(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
        )
    }
}

/**
 * Resilient helper that resolves and opens the user's installed clock or alarm application
 * across common device configurations with robust fallback mechanisms.
 *
 * Fallback cascade:
 * 1. Standard Alarm intent (AlarmClock.ACTION_SHOW_ALARMS)
 * 2. Category fallback (Intent.ACTION_MAIN with CATEGORY_APP_CALENDAR)
 * 3. Common DeskClock vendor package fallbacks (Google, AOSP, Samsung, CSK)
 * 4. Safety fallback with gentle Mochibun feedback toast ("No clock app found ♡")
 */
fun launchClockApp(context: Context) {
    val pm = context.packageManager

    // 1. Standard Alarm Intent
    try {
        val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (alarmIntent.resolveActivity(pm) != null) {
            context.startActivity(alarmIntent)
            return
        }
    } catch (_: Exception) {
        // Fall through to next option
    }

    // 2. Category Fallback
    try {
        val categoryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CALENDAR)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (categoryIntent.resolveActivity(pm) != null) {
            context.startActivity(categoryIntent)
            return
        }
    } catch (_: Exception) {
        // Fall through to next option
    }

    // 3. Common DeskClock Package Fallbacks
    val vendorClockPackages = listOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.csk.deskclock"
    )
    for (pkg in vendorClockPackages) {
        try {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                return
            }
        } catch (_: Exception) {
            // Try next package
        }
    }

    // 4. Graceful Safety Fallback
    try {
        Toast.makeText(context, "No clock app found ♡", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}

/**
 * Clock & Date Section.
 * Recomposition scope is strictly isolated so minute-ticks only recompose this composable.
 * Supports D-pad navigation and touch activation to launch the device clock/alarm app.
 *
 * Selection Halo Aesthetic:
 * When focused, renders only Mochibun's unclipped, theme-aware soft pastel focus halo
 * with containerTint = Color.Transparent (no opaque background card, tinted box, or solid fill).
 * When unfocused, remains 100% transparent.
 */
@Composable
fun ClockWidget(
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasComposeFocus by remember { mutableStateOf(false) }
    val effectiveFocused = isFocused || hasComposeFocus

    val timeFormat = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val amPmFormat = remember { SimpleDateFormat("a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    val initialNow = remember { Date() }
    var formattedTime by remember { mutableStateOf(timeFormat.format(initialNow)) }
    var formattedAmPm by remember { mutableStateOf(amPmFormat.format(initialNow)) }
    var formattedDate by remember { mutableStateOf(dateFormat.format(initialNow)) }

    // Coroutine updates strictly on minute boundaries while RESUMED
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val nowMs = System.currentTimeMillis()
                val nowDate = Date(nowMs)
                val newTime = timeFormat.format(nowDate)
                val newAmPm = amPmFormat.format(nowDate)
                val newDate = dateFormat.format(nowDate)

                if (formattedTime != newTime) formattedTime = newTime
                if (formattedAmPm != newAmPm) formattedAmPm = newAmPm
                if (formattedDate != newDate) formattedDate = newDate

                val delayToNextMinute = (60_000L - (nowMs % 60_000L)).coerceIn(1_000L, 60_000L)
                delay(delayToNextMinute)
            }
        }
    }

    val handleActivate = {
        SoundEffectManager.play(context, SoundEffect.SELECT)
        if (onClick != null) {
            onClick()
        } else {
            launchClockApp(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .graphicsLayer { clip = false }
            .softGlowFocus(
                isSelected = effectiveFocused,
                cornerRadius = 18.dp,
                containerTint = Color.Transparent // Strictly transparent: no card fill
            )
            .onFocusChanged { focusState ->
                hasComposeFocus = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    handleActivate()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                handleActivate()
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Left floating accents: heart and sparkle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 14.dp)
            ) {
                Text(
                    text = "♥",
                    fontSize = 16.sp,
                    color = palette.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                SparkleAccent(size = 14.dp, color = palette.outline)
            }

            // Big digital time with trailing AM/PM aligned to midline/baseline
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = formattedTime,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.greetingColor,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formattedAmPm,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.greetingColor,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }

            // Right floating accents: sparkle and heart
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 14.dp)
            ) {
                SparkleAccent(size = 14.dp, color = palette.outline)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "♥",
                    fontSize = 16.sp,
                    color = palette.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle formatted date
        Text(
            text = formattedDate,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = theme.greetingColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Favorites Card.
 * Displays pinned apps and folders in a horizontal row/carousel.
 *
 * Physical Focus Contract:
 * Outer and inner containers specify 'graphicsLayer { clip = false }' so the ~3.5 dp soft focus
 * halo around the active slot isn't clipped by container edges or viewport bounds.
 * When > 4 favorites exist, the LazyRow automatically scrolls focused slots into view so D-pad
 * navigation remains smooth and predictable.
 */
@Composable
fun FavoritesCard(
    context: Context,
    favoriteItems: List<FavoriteSlotItem>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    isCardFocused: Boolean = true,
    isHomeScreenActive: Boolean = true,
    folderIconRefreshKey: Int = 0,
    onAppClick: (LauncherApp) -> Unit = {},
    onAppLongClick: (LauncherApp) -> Unit = {},
    onFolderClick: (FavoriteFolder) -> Unit = {},
    onFolderLongClick: (FavoriteFolder) -> Unit = {}
) {
    val theme = LocalLauncherTheme.current
    val cardShape = RoundedCornerShape(24.dp)
    val itemCount = favoriteItems.size
    val lazyRowState = rememberLazyListState()

    // Smooth scroll if index moves outside viewport when more than 4 favorites exist
    LaunchedEffect(selectedIndex, itemCount) {
        if (itemCount > 4) {
            val targetIndex = selectedIndex.coerceIn(0, itemCount - 1)
            val selectedItemInfo = lazyRowState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.index == targetIndex
            }
            val viewportStart = lazyRowState.layoutInfo.viewportStartOffset
            val viewportEnd = lazyRowState.layoutInfo.viewportEndOffset
            val isFullyVisible = selectedItemInfo != null &&
                selectedItemInfo.offset >= viewportStart &&
                (selectedItemInfo.offset + selectedItemInfo.size) <= viewportEnd

            if (!isFullyVisible) {
                lazyRowState.animateScrollToItem(targetIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .padding(top = 26.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .kawaiiCardMotifs(
                    isFocused = isCardFocused,
                    cornerRadius = 24.dp
                )
                .border(
                    width = 1.5.dp,
                    color = theme.greetingOutlineColor,
                    shape = cardShape
                )
                .background(theme.cardBgColor, cardShape)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with decorative side divider lines: — ♡ Favorites ♡ —
            MochibunSectionHeader(
                title = "Favorites",
                color = ActiveTheme.accentDeep,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Slots row: evenly distributed across 480px width
            if (itemCount <= 4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    favoriteItems.forEachIndexed { index, item ->
                        when (item) {
                            is FavoriteSlotItem.App -> {
                                FavoriteAppSlot(
                                    app = item.app,
                                    index = index,
                                    isSelected = isCardFocused && index == selectedIndex,
                                    onClick = {
                                        onAppClick(item.app)
                                        context.packageManager
                                            .getLaunchIntentForPackage(item.app.packageName)
                                            ?.let(context::startActivity)
                                    },
                                    onLongClick = {
                                        onAppClick(item.app)
                                        onAppLongClick(item.app)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            is FavoriteSlotItem.Folder -> {
                                FavoriteFolderSlot(
                                    context = context,
                                    folder = item.folder,
                                    index = index,
                                    isSelected = isCardFocused && index == selectedIndex,
                                    folderIconRefreshKey = folderIconRefreshKey,
                                    onClick = { onFolderClick(item.folder) },
                                    onLongClick = { onFolderLongClick(item.folder) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    state = lazyRowState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    itemsIndexed(favoriteItems, key = { _, item -> item.key }) { index, item ->
                        when (item) {
                            is FavoriteSlotItem.App -> {
                                FavoriteAppSlot(
                                    app = item.app,
                                    index = index,
                                    isSelected = isCardFocused && index == selectedIndex,
                                    onClick = {
                                        onAppClick(item.app)
                                        context.packageManager
                                            .getLaunchIntentForPackage(item.app.packageName)
                                            ?.let(context::startActivity)
                                    },
                                    onLongClick = {
                                        onAppClick(item.app)
                                        onAppLongClick(item.app)
                                    },
                                    modifier = Modifier.width(68.dp)
                                )
                            }
                            is FavoriteSlotItem.Folder -> {
                                FavoriteFolderSlot(
                                    context = context,
                                    folder = item.folder,
                                    index = index,
                                    isSelected = isCardFocused && index == selectedIndex,
                                    folderIconRefreshKey = folderIconRefreshKey,
                                    onClick = { onFolderClick(item.folder) },
                                    onLongClick = { onFolderLongClick(item.folder) },
                                    modifier = Modifier.width(68.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Companion mascot loaf sitting on the top-right corner of the favorites card with dynamic speech bubble
    MochibunnyWithSpeechBubble(
        mood = getDefaultTimeOfDayMood(),
        size = 56.dp,
        themeTintColor = theme.mascotTintColor,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = (-14).dp, y = (-26).dp)
            .keitaiMascotBob(enabled = isHomeScreenActive)
    )
}
}

/**
 * Individual favorite app slot with squircle background and unclipped focus ring.
 */
@Composable
private fun FavoriteAppSlot(
    app: LauncherApp,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = tween(120),
        label = "favAppScale"
    )

    val squircleBg = if (index % 2 == 0) palette.squirclePrimary else palette.squircleSecondary

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .focusPop(isSelected = isSelected, cornerRadius = 20.dp)
                .padding(3.dp)
                .background(squircleBg, FavoriteSquircleShape)
                .clip(FavoriteSquircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // FilterQuality.None prevents bilinear blur on small/pixel-art icons at 480x800
            Image(
                bitmap = app.imageBitmap,
                contentDescription = app.name,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = app.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.fgTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 66.dp)
        )
    }
}

/**
 * Individual favorite folder slot with squircle background and unclipped focus ring.
 */
@Composable
private fun FavoriteFolderSlot(
    context: Context,
    folder: FavoriteFolder,
    index: Int,
    isSelected: Boolean,
    folderIconRefreshKey: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = tween(120),
        label = "favFolderScale"
    )

    val folderIconBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        folder.id,
        folderIconRefreshKey
    ) {
        withContext(Dispatchers.IO) {
            val preferences = context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
            val folderIconName = preferences.getString("custom_folder_icon_${folder.id}", null)
            value = IconPackManager.getCachedImageBitmap(context, folderIconName, 96)
        }
    }

    val squircleBg = if (index % 2 == 0) palette.squirclePrimary else palette.squircleSecondary

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .focusPop(isSelected = isSelected, cornerRadius = 20.dp)
                .padding(3.dp)
                .background(squircleBg, FavoriteSquircleShape)
                .clip(FavoriteSquircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            val currentIcon = folderIconBitmap
            if (currentIcon != null) {
                // FilterQuality.None preserves crisp 1:1 pixel rendering on 480x800
                Image(
                    bitmap = currentIcon,
                    contentDescription = folder.name,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                FolderWithHeartIcon(
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = folder.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.fgTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 66.dp)
        )
    }
}

/**
 * Bottom Drawer Handle.
 * Displays upward chevron and "♥ Swipe up for apps ♥".
 * Supports tap, swipe up gesture, and physical D-pad focus.
 */
@Composable
fun BottomDrawerHandle(
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    onClick: () -> Unit = {}
) {
    val theme = LocalLauncherTheme.current
    val handleShape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .graphicsLayer { clip = false }
            .then(
                if (isFocused) {
                    Modifier
                        .softGlowFocus(isSelected = true, cornerRadius = 16.dp)
                        .background(theme.focusedContainerColor, handleShape)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                } else {
                    Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                }
            )
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "♥⇣♡ ♡⇣♥",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.appActionColor
        )
    }
}

// SoftKeyPromptPill moved to com.crinklecodone.mochibun.ui.components.SoftKeyPromptPill

/**
 * Lightweight 4-pointed sparkle star drawn using vector geometry.
 */
@Composable
fun SparkleAccent(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    color: Color = Color(0xFFDEC3EC)
) {
    val path = remember { Path() }
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        path.reset()
        path.apply {
            moveTo(w * 0.5f, 0f)
            quadraticTo(w * 0.5f, h * 0.5f, w, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.5f, w * 0.5f, h)
            quadraticTo(w * 0.5f, h * 0.5f, 0f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.5f, w * 0.5f, 0f)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * Cute vector folder icon with a heart centered inside.
 */
@Composable
fun FolderWithHeartIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val folderPath = remember { Path() }
    val heartPath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Folder back tab & body
        folderPath.reset()
        folderPath.apply {
            moveTo(w * 0.12f, h * 0.28f)
            lineTo(w * 0.42f, h * 0.28f)
            lineTo(w * 0.52f, h * 0.38f)
            lineTo(w * 0.88f, h * 0.38f)
            quadraticTo(w * 0.94f, h * 0.38f, w * 0.94f, h * 0.44f)
            lineTo(w * 0.94f, h * 0.80f)
            quadraticTo(w * 0.94f, h * 0.86f, w * 0.88f, h * 0.86f)
            lineTo(w * 0.12f, h * 0.86f)
            quadraticTo(w * 0.06f, h * 0.86f, w * 0.06f, h * 0.80f)
            lineTo(w * 0.06f, h * 0.34f)
            quadraticTo(w * 0.06f, h * 0.28f, w * 0.12f, h * 0.28f)
            close()
        }
        drawPath(folderPath, tint)

        // Pink heart in center of folder
        val hw = w * 0.24f
        val hh = h * 0.22f
        val cx = w * 0.50f
        val cy = h * 0.62f
        heartPath.reset()
        heartPath.apply {
            moveTo(cx, cy + hh * 0.35f)
            cubicTo(cx - hw * 0.5f, cy - hh * 0.15f, cx - hw * 0.5f, cy - hh * 0.5f, cx, cy - hh * 0.15f)
            cubicTo(cx + hw * 0.5f, cy - hh * 0.5f, cx + hw * 0.5f, cy - hh * 0.15f, cx, cy + hh * 0.35f)
            close()
        }
        drawPath(heartPath, palette.primary)
    }
}
