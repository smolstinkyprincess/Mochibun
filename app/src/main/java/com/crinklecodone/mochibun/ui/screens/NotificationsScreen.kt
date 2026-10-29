package com.crinklecodone.mochibun.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.core.util.drawableToBitmap
import com.crinklecodone.mochibun.data.model.MochibunNotificationItem
import com.crinklecodone.mochibun.data.repository.NotificationTracker
import com.crinklecodone.mochibun.ui.components.MochibunMascot
import com.crinklecodone.mochibun.ui.components.TopLavenderClouds
import com.crinklecodone.mochibun.ui.components.getDefaultTimeOfDayMood
import com.crinklecodone.mochibun.ui.components.softGlowFocus
import com.crinklecodone.mochibun.ui.dialogs.awaitFocusSafely
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.performBoundaryHaptic
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.palette

private const val TAG = "NotificationsScreen"

enum class NotifFocusTarget {
    TOP_ACTION,
    NOTIFICATION_CARD,
    HOME_PILL
}

/**
 * Dedicated Notifications Screen (Page Index 2) adhering faithfully to Mochibun's
 * kawaii pastel aesthetic, physical D-pad navigation contracts, and low-RAM hardware constraints.
 */
@Composable
fun NotificationsScreen(
    context: Context,
    cardRoundness: Dp,
    modifier: Modifier = Modifier,
    isActivePage: Boolean = true,
    onNavigateHome: () -> Unit = {}
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette

    val notifications by NotificationTracker.notifications.collectAsState()
    val isServiceConnected by NotificationTracker.isServiceConnected.collectAsState()
    val hasAccess = remember(isServiceConnected, isActivePage) {
        NotificationTracker.hasNotificationAccess(context)
    }

    val pageFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    var focusTarget by remember { mutableStateOf(NotifFocusTarget.NOTIFICATION_CARD) }
    var focusedCardIndex by remember { mutableIntStateOf(0) }
    var focusedCardAction by remember { mutableIntStateOf(0) } // 0 = Card Body, 1 = Dismiss Button
    var sawCenterDown by remember { mutableStateOf(false) }

    // Synchronize focus targets when notification counts or access permissions change
    LaunchedEffect(notifications.size, hasAccess) {
        if (!hasAccess) {
            focusTarget = NotifFocusTarget.TOP_ACTION
            focusedCardAction = 0
        } else if (notifications.isEmpty()) {
            focusTarget = NotifFocusTarget.HOME_PILL
            focusedCardAction = 0
            focusedCardIndex = 0
        } else {
            if (focusTarget == NotifFocusTarget.NOTIFICATION_CARD) {
                focusedCardIndex = focusedCardIndex.coerceIn(0, notifications.size - 1)
            }
        }
    }

    // Scroll LazyColumn to keep focused element visible
    LaunchedEffect(focusTarget, focusedCardIndex) {
        if (!isActivePage) return@LaunchedEffect
        when (focusTarget) {
            NotifFocusTarget.TOP_ACTION -> {
                listState.animateScrollToItem(0)
            }
            NotifFocusTarget.NOTIFICATION_CARD -> {
                if (notifications.isNotEmpty()) {
                    val targetIdx = 1 + focusedCardIndex // 0 is header item
                    val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIdx }
                    if (!isVisible) {
                        listState.animateScrollToItem(targetIdx)
                    }
                }
            }
            NotifFocusTarget.HOME_PILL -> {
                val totalCount = listState.layoutInfo.totalItemsCount
                if (totalCount > 0) {
                    listState.animateScrollToItem(totalCount - 1)
                }
            }
        }
    }

    // Acquire focus when page becomes active
    LaunchedEffect(isActivePage) {
        if (isActivePage) {
            NotificationTracker.refresh(context)
            if (!NotificationTracker.hasNotificationAccess(context)) {
                focusTarget = NotifFocusTarget.TOP_ACTION
            } else if (notifications.isNotEmpty()) {
                focusTarget = NotifFocusTarget.NOTIFICATION_CARD
                focusedCardIndex = 0
                focusedCardAction = 0
            } else {
                focusTarget = NotifFocusTarget.HOME_PILL
            }

            pageFocusRequester.awaitFocusSafely(attempts = 6)
        } else {
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

                // BACK or KEYCODE_F4 safely returns to Page 1 (Home)
                if (isBackDismissKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_F4) {
                    if (event.type == KeyEventType.KeyUp) {
                        onNavigateHome()
                    }
                    return@onPreviewKeyEvent true
                }

                // DPAD_CENTER / ENTER activation
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusTarget) {
                            NotifFocusTarget.TOP_ACTION -> {
                                if (!hasAccess) {
                                    launchNotificationListenerSettings(context)
                                } else if (notifications.isNotEmpty()) {
                                    NotificationTracker.cancelAllNotifications()
                                }
                            }
                            NotifFocusTarget.NOTIFICATION_CARD -> {
                                val item = notifications.getOrNull(focusedCardIndex)
                                if (item != null) {
                                    if (focusedCardAction == 1) {
                                        // Dismiss button
                                        NotificationTracker.cancelNotification(item.key)
                                    } else {
                                        // Card body triggers notification target intent
                                        launchNotification(context, item)
                                    }
                                }
                            }
                            NotifFocusTarget.HOME_PILL -> {
                                onNavigateHome()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                // Secondary quick dismiss action (MENU, STAR, DEL) cancels focused notification
                if (native.keyCode == KeyEvent.KEYCODE_MENU ||
                    native.keyCode == KeyEvent.KEYCODE_STAR ||
                    native.keyCode == KeyEvent.KEYCODE_DEL
                ) {
                    if (native.repeatCount == 0 && focusTarget == NotifFocusTarget.NOTIFICATION_CARD) {
                        notifications.getOrNull(focusedCardIndex)?.let { item ->
                            NotificationTracker.cancelNotification(item.key)
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusTarget) {
                            NotifFocusTarget.HOME_PILL -> {
                                if (notifications.isNotEmpty()) {
                                    focusTarget = NotifFocusTarget.NOTIFICATION_CARD
                                    focusedCardIndex = notifications.size - 1
                                    focusedCardAction = 0
                                } else if (!hasAccess) {
                                    focusTarget = NotifFocusTarget.TOP_ACTION
                                } else {
                                    performBoundaryHaptic(context)
                                }
                            }
                            NotifFocusTarget.NOTIFICATION_CARD -> {
                                if (focusedCardIndex > 0) {
                                    focusedCardIndex--
                                    focusedCardAction = 0
                                } else {
                                    // Move up to Top Action (Clear All or Permission)
                                    focusTarget = NotifFocusTarget.TOP_ACTION
                                    focusedCardAction = 0
                                }
                            }
                            NotifFocusTarget.TOP_ACTION -> {
                                performBoundaryHaptic(context)
                            }
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusTarget) {
                            NotifFocusTarget.TOP_ACTION -> {
                                if (notifications.isNotEmpty()) {
                                    focusTarget = NotifFocusTarget.NOTIFICATION_CARD
                                    focusedCardIndex = 0
                                    focusedCardAction = 0
                                } else {
                                    focusTarget = NotifFocusTarget.HOME_PILL
                                }
                            }
                            NotifFocusTarget.NOTIFICATION_CARD -> {
                                if (focusedCardIndex < notifications.size - 1) {
                                    focusedCardIndex++
                                    focusedCardAction = 0
                                } else {
                                    focusTarget = NotifFocusTarget.HOME_PILL
                                    focusedCardAction = 0
                                }
                            }
                            NotifFocusTarget.HOME_PILL -> {
                                performBoundaryHaptic(context)
                            }
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focusTarget == NotifFocusTarget.NOTIFICATION_CARD && focusedCardAction == 1) {
                            // Step back from dismiss button to card body
                            focusedCardAction = 0
                        } else {
                            // DPAD_LEFT on the edge returns focus/navigates back to Home (page = 1)
                            onNavigateHome()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focusTarget == NotifFocusTarget.NOTIFICATION_CARD && focusedCardAction == 0) {
                            // Step focus into the card's dismiss action button
                            focusedCardAction = 1
                        } else {
                            performBoundaryHaptic(context)
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        // 1. Top Scalloped Lavender Clouds motif matching Mochibun design system
        TopLavenderClouds(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // 2. Main Scrollable Content
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 44.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Gradient pill card
            item(key = "header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(palette.squircleSecondary, palette.squirclePrimary)
                                )
                            )
                            .border(1.5.dp, palette.outline, RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "— ♡ Notifications ♡ —",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ActiveTheme.accentDeep
                        )
                    }

                    // Overlapping Sub-Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.cloudFront)
                            .border(1.dp, palette.outline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (!hasAccess) {
                                "✦ permission required ✦"
                            } else if (notifications.isNotEmpty()) {
                                "✦ ${notifications.size} active ✦"
                            } else {
                                "✦ all caught up! ✨ ✦"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.deepAccent
                        )
                    }
                }
            }

            // Top Action Item: Permission Request or Clear All Button
            if (!hasAccess) {
                item(key = "permission_banner") {
                    val isFocused = focusTarget == NotifFocusTarget.TOP_ACTION
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .graphicsLayer { clip = false }
                            .softGlowFocus(isSelected = isFocused, cornerRadius = cardRoundness)
                            .clip(RoundedCornerShape(cardRoundness))
                            .background(
                                if (isFocused) {
                                    Brush.horizontalGradient(
                                        listOf(palette.focusedCardContainer, palette.focusedCardContainerEnd)
                                    )
                                } else {
                                    SolidColor(palette.unfocusedCardBg)
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isFocused) palette.focusedCardBorder else palette.unfocusedCardBorder,
                                shape = RoundedCornerShape(cardRoundness)
                            )
                            .clickable { launchNotificationListenerSettings(context) }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "♡ Notification Access Needed ♡",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = palette.deepAccent
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Enable Mochibun's listener service to view and manage notifications here.",
                                fontSize = 11.sp,
                                color = palette.textColor.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isFocused) palette.deepAccent else palette.container)
                                    .border(1.dp, palette.outline, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Grant Access (✿◠‿◠)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocused) Color.White else palette.textColor
                                )
                            }
                        }
                    }
                }
            } else if (notifications.isNotEmpty()) {
                item(key = "clear_all_bar") {
                    val isFocused = focusTarget == NotifFocusTarget.TOP_ACTION
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer { clip = false }
                                .softGlowFocus(isSelected = isFocused, cornerRadius = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isFocused) palette.focusedCardContainer else palette.container
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isFocused) palette.focusedCardBorder else palette.outline,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { NotificationTracker.cancelAllNotifications() }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕ Clear All",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFocused) palette.deepAccent else palette.textColor
                            )
                        }
                    }
                }
            }

            // Notification Cards
            if (hasAccess && notifications.isNotEmpty()) {
                itemsIndexed(
                    items = notifications,
                    key = { _, item -> item.key }
                ) { index, item ->
                    val isCardFocused = focusTarget == NotifFocusTarget.NOTIFICATION_CARD && focusedCardIndex == index
                    val isDismissFocused = isCardFocused && focusedCardAction == 1
                    val isBodyFocused = isCardFocused && focusedCardAction == 0

                    NotificationCard(
                        item = item,
                        palette = palette,
                        cardRoundness = cardRoundness,
                        isCardFocused = isCardFocused,
                        isBodyFocused = isBodyFocused,
                        isDismissFocused = isDismissFocused,
                        onCardClick = { launchNotification(context, item) },
                        onDismissClick = { NotificationTracker.cancelNotification(item.key) }
                    )
                }
            } else if (hasAccess && notifications.isEmpty()) {
                // Empty state card
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(cardRoundness))
                            .background(palette.unfocusedCardBg)
                            .border(1.dp, palette.unfocusedCardBorder, RoundedCornerShape(cardRoundness))
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            MochibunMascot(
                                mood = getDefaultTimeOfDayMood(),
                                size = 72.dp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "All Caught Up! ૮₍ ˶•⤙•˶ ₎ა",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = palette.deepAccent
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "No active notifications right now ~",
                                fontSize = 11.sp,
                                color = palette.textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Bottom Navigation Pill: Return to Home
            item(key = "home_pill") {
                val isPillFocused = focusTarget == NotifFocusTarget.HOME_PILL
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = 8.dp)
                        .height(38.dp)
                        .graphicsLayer { clip = false }
                        .softGlowFocus(isSelected = isPillFocused, cornerRadius = 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isPillFocused) {
                                Brush.horizontalGradient(
                                    listOf(palette.focusedCardContainer, palette.focusedCardContainerEnd)
                                )
                            } else {
                                SolidColor(palette.container)
                            }
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isPillFocused) palette.focusedCardBorder else palette.outline,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onNavigateHome() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "— ♡ Return to Home ♡ —",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPillFocused) palette.deepAccent else palette.textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: MochibunNotificationItem,
    palette: com.crinklecodone.mochibun.ui.theme.MochibunThemePalette,
    cardRoundness: Dp,
    isCardFocused: Boolean,
    isBodyFocused: Boolean,
    isDismissFocused: Boolean,
    onCardClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    val iconBitmap = remember(item.appIcon) {
        item.appIcon?.let { drawableToBitmap(it, 48).asImageBitmap() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isBodyFocused, cornerRadius = cardRoundness)
            .clip(RoundedCornerShape(cardRoundness))
            .background(
                if (isCardFocused) {
                    Brush.horizontalGradient(
                        colors = listOf(palette.focusedCardContainer, palette.focusedCardContainerEnd)
                    )
                } else {
                    SolidColor(palette.unfocusedCardBg)
                }
            )
            .border(
                width = 1.5.dp,
                color = if (isBodyFocused) palette.focusedCardBorder else if (isCardFocused) palette.outline else palette.unfocusedCardBorder,
                shape = RoundedCornerShape(cardRoundness)
            )
            .clickable { onCardClick() }
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // App identification and dismiss header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = item.appName,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                }

                Text(
                    text = item.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = palette.deepAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (item.postTime > 0L) {
                    Text(
                        text = " • ${formatRelativeTime(item.postTime)}",
                        fontSize = 10.sp,
                        color = palette.textColor.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                }

                Spacer(Modifier.weight(1f))

                // Dismiss button: focused indicator or tap target
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDismissFocused) palette.deepAccent else palette.container
                        )
                        .border(
                            width = if (isDismissFocused) 2.dp else 1.dp,
                            color = if (isDismissFocused) palette.focusedCardBorder else palette.outline,
                            shape = CircleShape
                        )
                        .clickable { onDismissClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDismissFocused) Color.White else palette.textColor
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Notification Title
            if (item.title.isNotEmpty()) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Notification Body Text
            if (item.text.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.text,
                    fontSize = 11.sp,
                    color = palette.textColor.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun launchNotification(context: Context, item: MochibunNotificationItem) {
    if (item.contentIntent != null) {
        try {
            item.contentIntent.send()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch content intent for ${item.packageName}", e)
        }
    }
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch package: ${item.packageName}", e)
    }
}

private fun launchNotificationListenerSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch notification listener settings", e)
    }
}

private fun formatRelativeTime(postTime: Long): String {
    if (postTime <= 0L) return ""
    val diff = (System.currentTimeMillis() - postTime).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    if (minutes < 1L) return "Just now"
    if (minutes < 60L) return "${minutes}m ago"
    val hours = minutes / 60L
    if (hours < 24L) return "${hours}h ago"
    val days = hours / 24L
    return "${days}d ago"
}
