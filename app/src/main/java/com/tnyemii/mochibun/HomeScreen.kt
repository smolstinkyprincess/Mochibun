/**
 * Main Home Screen surface for Mochibun.
 * Orchestrates the greeting banner, digital clock, favorite apps/folders card,
 * and bottom app drawer handle within the Chinoe AT-M120's 480x800 display envelope.
 */
package com.tnyemii.mochibun

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// dynamic pastel theme colors provided via LocalLauncherTheme and MochibunThemePalette :3

/**
 * Main Home Screen composable recreating the kawaii Mochibun layout.
 * Accurately fits 480x800 displays with tight recomposition scopes.
 */
@Composable
fun HomeScreen(
    context: Context,
    favoriteApps: List<LauncherApp>,
    favoriteFolders: List<FavoriteFolder>,
    modifier: Modifier = Modifier,
    userName: String = "",
    selectedFavoriteIndex: Int = 0,
    folderIconRefreshKey: Int = 0,
    homeFocusArea: Int = 0, // 0 = favorites, 1 = header banner, 2 = drawer handle
    isHomeScreenActive: Boolean = true,
    isPixelSprayEnabled: Boolean = true,
    onHeaderClick: () -> Unit = {},
    onHeaderLongClick: () -> Unit = {},
    onAppClick: (LauncherApp) -> Unit = {},
    onAppLongClick: (LauncherApp) -> Unit = {},
    onFolderClick: (FavoriteFolder) -> Unit = {},
    onFolderLongClick: (FavoriteFolder) -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val theme = LocalLauncherTheme.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // PixelSpray sticker overlay above wallpaper (conditionally rendered)
        if (isPixelSprayEnabled) {
            PixelSpray(
                count = 20,
                modifier = Modifier.fillMaxSize(),
                tintBehavior = PixelSprayTintBehavior.MIXED
            )
        }
        // Bottom cloud & peeking cat illustration layer drawn via lightweight Canvas pass
        /*
        BottomIllustrationLayer(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
        )*/

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
            ClockWidget()

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Favorites Card
            FavoritesCard(
                context = context,
                favoriteApps = favoriteApps,
                favoriteFolders = favoriteFolders,
                selectedIndex = selectedFavoriteIndex,
                isCardFocused = isHomeScreenActive && homeFocusArea == 0,
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

        // 4. Bottom Drawer Handle ("^ Swipe up for apps")
        BottomDrawerHandle(
            isFocused = isHomeScreenActive && homeFocusArea == 2,
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
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
            .padding(top = 12.dp)
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
                .offset(x = (-44).dp, y = 6.dp)
        )
        Text(
            text = "♡",
            fontSize = 13.sp,
            color = palette.primary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 44.dp, y = 6.dp)
        )

        // Decorative ribbon bow badge anchored at top-center overlapping the border
        PixelRibbonBow(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-25).dp)
        )
    }
}

/**
 * Clock & Date Section.
 * Recomposition scope is strictly isolated so minute-ticks only recompose this composable.
 */
@Composable
fun ClockWidget(
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val lifecycleOwner = LocalLifecycleOwner.current

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

    Column(
        modifier = modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(2.dp))

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
    favoriteApps: List<LauncherApp>,
    favoriteFolders: List<FavoriteFolder>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    isCardFocused: Boolean = true,
    folderIconRefreshKey: Int = 0,
    onAppClick: (LauncherApp) -> Unit = {},
    onAppLongClick: (LauncherApp) -> Unit = {},
    onFolderClick: (FavoriteFolder) -> Unit = {},
    onFolderLongClick: (FavoriteFolder) -> Unit = {}
) {
    val theme = LocalLauncherTheme.current
    val cardShape = RoundedCornerShape(24.dp)
    val itemCount = favoriteApps.size + favoriteFolders.size
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.5.dp)
                        .background(theme.greetingOutlineColor, RoundedCornerShape(1.dp))
                )
                Text(
                    text = "— ♡ Favorites ♡ —",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.5.dp)
                        .background(theme.greetingOutlineColor, RoundedCornerShape(1.dp))
                )
            }

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
                    favoriteApps.forEachIndexed { index, app ->
                        FavoriteAppSlot(
                            app = app,
                            index = index,
                            isSelected = isCardFocused && index == selectedIndex,
                            onClick = {
                                onAppClick(app)
                                context.packageManager
                                    .getLaunchIntentForPackage(app.packageName)
                                    ?.let(context::startActivity)
                            },
                            onLongClick = {
                                onAppClick(app)
                                onAppLongClick(app)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    favoriteFolders.forEachIndexed { fIndex, folder ->
                        val index = favoriteApps.size + fIndex
                        FavoriteFolderSlot(
                            context = context,
                            folder = folder,
                            index = index,
                            isSelected = isCardFocused && index == selectedIndex,
                            folderIconRefreshKey = folderIconRefreshKey,
                            onClick = { onFolderClick(folder) },
                            onLongClick = { onFolderLongClick(folder) },
                            modifier = Modifier.weight(1f)
                        )
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
                    itemsIndexed(favoriteApps, key = { _, app -> app.favoriteKey }) { index, app ->
                        FavoriteAppSlot(
                            app = app,
                            index = index,
                            isSelected = isCardFocused && index == selectedIndex,
                            onClick = {
                                onAppClick(app)
                                context.packageManager
                                    .getLaunchIntentForPackage(app.packageName)
                                    ?.let(context::startActivity)
                            },
                            onLongClick = {
                                onAppClick(app)
                                onAppLongClick(app)
                            },
                            modifier = Modifier.width(68.dp)
                        )
                    }

                    itemsIndexed(
                        favoriteFolders,
                        key = { _, folder -> "folder:${folder.id}" }
                    ) { fIndex, folder ->
                        val index = favoriteApps.size + fIndex
                        FavoriteFolderSlot(
                            context = context,
                            folder = folder,
                            index = index,
                            isSelected = isCardFocused && index == selectedIndex,
                            folderIconRefreshKey = folderIconRefreshKey,
                            onClick = { onFolderClick(folder) },
                            onLongClick = { onFolderLongClick(folder) },
                            modifier = Modifier.width(68.dp)
                        )
                    }
                }
            }
        }
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
    val squircleShape = RoundedCornerShape(16.dp)
    val focusShape = RoundedCornerShape(20.dp)

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
                .then(
                    if (isSelected) {
                        Modifier.softGlowFocus(isSelected = true, cornerRadius = 20.dp)
                    } else {
                        Modifier
                    }
                )
                .padding(3.dp)
                .background(squircleBg, squircleShape)
                .clip(squircleShape)
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

    val preferences = remember(context) {
        context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
    }

    val folderIconName = remember(preferences, folder.id, folderIconRefreshKey) {
        preferences.getString("custom_folder_icon_${folder.id}", null)
    }

    val folderIconBitmap = remember(context, folderIconName, folderIconRefreshKey) {
        getCachedKawaiiImageBitmap(context, folderIconName, 96)
    }

    val squircleBg = if (index % 2 == 0) palette.squirclePrimary else palette.squircleSecondary
    val squircleShape = RoundedCornerShape(16.dp)
    val focusShape = RoundedCornerShape(20.dp)

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
                .then(
                    if (isSelected) {
                        Modifier.softGlowFocus(isSelected = true, cornerRadius = 20.dp)
                    } else {
                        Modifier
                    }
                )
                .padding(3.dp)
                .background(squircleBg, squircleShape)
                .clip(squircleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (folderIconBitmap != null) {
                // FilterQuality.None preserves crisp 1:1 pixel rendering on 480x800
                Image(
                    bitmap = folderIconBitmap,
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
 * Bottom cloud & kawaii illustration layer drawn using hardware-accelerated Canvas.
 * Renders back, mid, and front cloud silhouettes, peeking cat with paws resting on cloud,
 * and scattered background stars/hearts without runtime blurs or bitmap overhead.
 */
@Composable
fun BottomIllustrationLayer(
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Scattered background sky accents
        drawSparkle(Offset(w * 0.16f, h * 0.18f), 7.dp.toPx(), palette.outline)
        drawSparkle(Offset(w * 0.08f, h * 0.42f), 8.dp.toPx(), palette.primary.copy(alpha = 0.7f))
        drawCircle(palette.outline.copy(alpha = 0.8f), radius = 2.5.dp.toPx(), center = Offset(w * 0.07f, h * 0.30f))
        drawMiniHeart(Offset(w * 0.17f, h * 0.62f), 6.5.dp.toPx(), palette.primary.copy(alpha = 0.5f))

        drawMiniHeart(Offset(w * 0.90f, h * 0.38f), 7.5.dp.toPx(), palette.primary.copy(alpha = 0.6f))
        drawSparkle(Offset(w * 0.85f, h * 0.18f), 7.dp.toPx(), palette.outline)
        drawSparkle(Offset(w * 0.70f, h * 0.50f), 6.5.dp.toPx(), palette.primary.copy(alpha = 0.7f))
        drawMiniHeart(Offset(w * 0.72f, h * 0.66f), 6.dp.toPx(), palette.primary.copy(alpha = 0.6f))

        // 2. Back Cloud layer
        val backCloudPath = Path().apply {
            moveTo(0f, h * 0.46f)
            cubicTo(w * 0.10f, h * 0.38f, w * 0.22f, h * 0.38f, w * 0.32f, h * 0.48f)
            cubicTo(w * 0.42f, h * 0.36f, w * 0.58f, h * 0.36f, w * 0.68f, h * 0.46f)
            cubicTo(w * 0.78f, h * 0.34f, w * 0.92f, h * 0.34f, w, h * 0.44f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(backCloudPath, palette.cloudBack)

        // 3. Middle Cloud layer
        val midCloudPath = Path().apply {
            moveTo(0f, h * 0.60f)
            cubicTo(w * 0.08f, h * 0.52f, w * 0.24f, h * 0.52f, w * 0.36f, h * 0.62f)
            cubicTo(w * 0.48f, h * 0.50f, w * 0.66f, h * 0.50f, w * 0.78f, h * 0.62f)
            cubicTo(w * 0.88f, h * 0.54f, w * 0.96f, h * 0.54f, w, h * 0.60f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(midCloudPath, palette.cloudMid)

        // 4. Kawaii Peeking Cat (tucked behind front cloud layer on bottom right)
        val catW = 96.dp.toPx()
        val catH = 88.dp.toPx()
        val catX = w - catW - 10.dp.toPx()
        val catY = h - catH - 18.dp.toPx()

        val outlineColor = palette.catOutline
        val bodyColor = Color(0xFFFFF7FB)
        val strokeWidth = 2.4.dp.toPx()

        // Cat Left Ear
        val leftEarPath = Path().apply {
            moveTo(catX + catW * 0.16f, catY + catH * 0.32f)
            quadraticTo(catX + catW * 0.18f, catY + catH * 0.04f, catX + catW * 0.28f, catY + catH * 0.05f)
            lineTo(catX + catW * 0.44f, catY + catH * 0.24f)
            close()
        }
        drawPath(leftEarPath, bodyColor)
        drawPath(leftEarPath, outlineColor, style = Stroke(width = strokeWidth))

        val leftInnerEarPath = Path().apply {
            moveTo(catX + catW * 0.20f, catY + catH * 0.28f)
            quadraticTo(catX + catW * 0.22f, catY + catH * 0.10f, catX + catW * 0.28f, catY + catH * 0.11f)
            lineTo(catX + catW * 0.38f, catY + catH * 0.24f)
            close()
        }
        drawPath(leftInnerEarPath, palette.primary)

        // Cat Right Ear
        val rightEarPath = Path().apply {
            moveTo(catX + catW * 0.62f, catY + catH * 0.24f)
            quadraticTo(catX + catW * 0.82f, catY + catH * 0.04f, catX + catW * 0.90f, catY + catH * 0.08f)
            lineTo(catX + catW * 0.94f, catY + catH * 0.34f)
            close()
        }
        drawPath(rightEarPath, bodyColor)
        drawPath(rightEarPath, outlineColor, style = Stroke(width = strokeWidth))

        val rightInnerEarPath = Path().apply {
            moveTo(catX + catW * 0.68f, catY + catH * 0.24f)
            quadraticTo(catX + catW * 0.82f, catY + catH * 0.10f, catX + catW * 0.88f, catY + catH * 0.13f)
            lineTo(catX + catW * 0.90f, catY + catH * 0.30f)
            close()
        }
        drawPath(rightInnerEarPath, palette.primary)

        // Cat Head
        val headPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = catX + catW * 0.06f,
                    top = catY + catH * 0.18f,
                    right = catX + catW * 0.98f,
                    bottom = catY + catH + 16.dp.toPx(),
                    radiusX = 36.dp.toPx(),
                    radiusY = 32.dp.toPx()
                )
            )
        }
        drawPath(headPath, bodyColor)
        drawPath(headPath, outlineColor, style = Stroke(width = strokeWidth))

        // Happy closed eyes: ⌒   ⌒
        val eyeColor = theme.fgTextColor
        val eyeStroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)

        val leftEyePath = Path().apply {
            moveTo(catX + catW * 0.30f, catY + catH * 0.52f)
            quadraticTo(catX + catW * 0.36f, catY + catH * 0.44f, catX + catW * 0.42f, catY + catH * 0.52f)
        }
        drawPath(leftEyePath, eyeColor, style = eyeStroke)

        val rightEyePath = Path().apply {
            moveTo(catX + catW * 0.62f, catY + catH * 0.54f)
            quadraticTo(catX + catW * 0.68f, catY + catH * 0.46f, catX + catW * 0.74f, catY + catH * 0.54f)
        }
        drawPath(rightEyePath, eyeColor, style = eyeStroke)

        // Soft blush cheeks
        val blushColor = palette.catBlush.copy(alpha = 0.85f)
        drawOval(
            color = blushColor,
            topLeft = Offset(catX + catW * 0.20f, catY + catH * 0.56f),
            size = Size(10.dp.toPx(), 6.dp.toPx())
        )
        drawOval(
            color = blushColor,
            topLeft = Offset(catX + catW * 0.74f, catY + catH * 0.58f),
            size = Size(10.dp.toPx(), 6.dp.toPx())
        )

        // Cute kitty mouth: :3 (w-shape)
        val mouthPath = Path().apply {
            val mx = catX + catW * 0.52f
            val my = catY + catH * 0.56f
            moveTo(mx - 7.dp.toPx(), my)
            quadraticTo(mx - 3.5.dp.toPx(), my + 4.dp.toPx(), mx, my + 1.dp.toPx())
            quadraticTo(mx + 3.5.dp.toPx(), my + 4.dp.toPx(), mx + 7.dp.toPx(), my)
        }
        drawPath(mouthPath, eyeColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // 5. Front Cloud layer
        val frontCloudPath = Path().apply {
            moveTo(0f, h * 0.75f)
            cubicTo(w * 0.15f, h * 0.68f, w * 0.30f, h * 0.68f, w * 0.42f, h * 0.76f)
            cubicTo(w * 0.55f, h * 0.70f, w * 0.70f, h * 0.70f, w * 0.80f, h * 0.78f)
            cubicTo(w * 0.90f, h * 0.72f, w * 0.96f, h * 0.72f, w, h * 0.75f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(frontCloudPath, palette.cloudFront)

        // 6. Cat Paw (rests over front cloud layer)
        val pawW = 20.dp.toPx()
        val pawH = 15.dp.toPx()
        val pawX = catX + catW * 0.04f
        val pawY = catY + catH * 0.68f
        val pawPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = pawX,
                    top = pawY,
                    right = pawX + pawW,
                    bottom = pawY + pawH,
                    radiusX = 7.dp.toPx(),
                    radiusY = 7.dp.toPx()
                )
            )
        }
        drawPath(pawPath, bodyColor)
        drawPath(pawPath, outlineColor, style = Stroke(width = strokeWidth))
        drawLine(
            outlineColor,
            Offset(pawX + pawW * 0.35f, pawY + 3.dp.toPx()),
            Offset(pawX + pawW * 0.35f, pawY + pawH * 0.65f),
            strokeWidth = 1.6.dp.toPx()
        )
        drawLine(
            outlineColor,
            Offset(pawX + pawW * 0.65f, pawY + 3.dp.toPx()),
            Offset(pawX + pawW * 0.65f, pawY + pawH * 0.65f),
            strokeWidth = 1.6.dp.toPx()
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

    Column(
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
            .clickable { onClick() }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -18) {
                        onClick()
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⌃",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = theme.appActionColor
        )
        Text(
            text = "♥  Swipe up for apps  ♥",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.appActionColor
        )
    }
}

/**
 * Lightweight 4-pointed sparkle star drawn using vector geometry.
 */
@Composable
fun SparkleAccent(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    color: Color = Color(0xFFDEC3EC)
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
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

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Folder back tab & body
        val folderPath = Path().apply {
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
        val heartPath = Path().apply {
            moveTo(cx, cy + hh * 0.35f)
            cubicTo(cx - hw * 0.5f, cy - hh * 0.15f, cx - hw * 0.5f, cy - hh * 0.5f, cx, cy - hh * 0.15f)
            cubicTo(cx + hw * 0.5f, cy - hh * 0.5f, cx + hw * 0.5f, cy - hh * 0.15f, cx, cy + hh * 0.35f)
            close()
        }
        drawPath(heartPath, palette.primary)
    }
}

private fun DrawScope.drawSparkle(center: Offset, sizePx: Float, color: Color) {
    val half = sizePx * 0.5f
    val path = Path().apply {
        moveTo(center.x, center.y - half)
        quadraticTo(center.x, center.y, center.x + half, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + half)
        quadraticTo(center.x, center.y, center.x - half, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - half)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawMiniHeart(center: Offset, sizePx: Float, color: Color) {
    val w = sizePx
    val h = sizePx
    val path = Path().apply {
        moveTo(center.x, center.y + h * 0.35f)
        cubicTo(center.x - w * 0.5f, center.y - h * 0.15f, center.x - w * 0.5f, center.y - h * 0.5f, center.x, center.y - h * 0.15f)
        cubicTo(center.x + w * 0.5f, center.y - h * 0.5f, center.x + w * 0.5f, center.y - h * 0.15f, center.x, center.y + h * 0.35f)
        close()
    }
    drawPath(path, color)
}
