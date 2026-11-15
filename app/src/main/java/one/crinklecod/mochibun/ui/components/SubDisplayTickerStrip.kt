/**
 * Outer sub-display ticker strip for the Home screen.
 *
 * Mimics the narrow external OLED/LCD strip on the lid of classic FOMA / au flip phones:
 * a short pill that scrolls a one-line status feed (now playing, unread count, battery
 * and a daily fortune) in discrete 3 Hz steps.
 */
package one.crinklecod.mochibun.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.repository.MediaSessionTracker
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherFontFamily
import java.util.Calendar

/** Deep plum/navy LCD backdrop for the dark "sub-OLED" style. */
private val SubOledBackdrop = Color(0xFF23182E)
/** Luminous pastel lavender "phosphor" ink for the dark sub-OLED style. */
private val SubOledInk = Color(0xFFF3D9FF)
private val SubDisplayShape = RoundedCornerShape(12.dp)
private const val SUB_DISPLAY_VISIBLE_CHARS = 36
private const val FEED_SEPARATOR = "  ✦  "
/** MediaSessionTracker's placeholder artist; treated like a blank artist. */
private const val PLACEHOLDER_ARTIST = "Unknown Artist"

/** Pager page indices (see MainLauncher): 0 = Widgets / Now Playing, 2 = Notifications. */
internal const val SUB_DISPLAY_PAGE_WIDGETS = 0
internal const val SUB_DISPLAY_PAGE_NOTIFICATIONS = 2

private val DAILY_FORTUNES = arrayOf(
    "Lucky: Sakura Tea",
    "Lucky: Strawberry Mochi",
    "Lucky: Pink Ribbon",
    "Lucky: Melon Soda",
    "Lucky: Star Sticker",
    "Lucky: Purikura",
    "Lucky: Taiyaki",
    "Lucky: Lavender Pen",
    "Lucky: Gacha Capsule",
    "Lucky: Matcha Latte",
    "Lucky: Phone Charm",
    "Lucky: Pudding",
    "Lucky: Love Letter",
    "Lucky: Bunny Plush"
)

/** Deterministic fortune for a given day: same answer all day, changes at midnight. */
internal fun dailyFortuneLabel(year: Int, dayOfYear: Int): String {
    val seed = year * 400 + dayOfYear
    // Multiply by a prime so consecutive days don't walk the list in order.
    val index = Math.floorMod(seed * 7919, DAILY_FORTUNES.size)
    return DAILY_FORTUNES[index]
}

/**
 * Which pager page tapping / Center on the strip opens: Notifications when alerts are
 * waiting, otherwise the Widgets page (Now Playing, battery and stats live there).
 */
internal fun subDisplayTickerTargetPage(unreadCount: Int): Int =
    if (unreadCount > 0) SUB_DISPLAY_PAGE_NOTIFICATIONS else SUB_DISPLAY_PAGE_WIDGETS

/**
 * Builds the one-line feed. Pure so it can be unit tested; the composable only calls it
 * when one of the inputs changes (never on a ticker step).
 */
internal fun buildSubDisplayFeed(
    isMediaPlaying: Boolean,
    artist: String,
    title: String,
    unreadCount: Int,
    batteryLevel: Int,
    isCharging: Boolean,
    fortune: String
): String {
    val parts = ArrayList<String>(4)
    if (isMediaPlaying && title.isNotBlank()) {
        val cleanArtist = artist.trim().takeUnless { it.isEmpty() || it == PLACEHOLDER_ARTIST }
        parts += if (cleanArtist == null) "♫ ${title.trim()}" else "♫ $cleanArtist - ${title.trim()}"
    }
    if (unreadCount > 0) parts += "✉ $unreadCount new"
    if (batteryLevel in 0..100) parts += "${if (isCharging) "⚡" else "♥"} $batteryLevel%"
    if (fortune.isNotBlank()) parts += fortune
    return parts.joinToString(FEED_SEPARATOR)
}

@Immutable
private data class BatterySnapshot(val level: Int, val isCharging: Boolean)

/**
 * Battery level + charging state, listened to only while the activity is RESUMED.
 * ACTION_BATTERY_CHANGED is sticky, so registering returns the current value immediately.
 */
@Composable
private fun rememberBatterySnapshot(): BatterySnapshot {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember { mutableStateOf(BatterySnapshot(-1, false)) }

    DisposableEffect(context, lifecycleOwner) {
        fun read(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            // Data-class equality: identical readings don't trigger recomposition.
            snapshot = BatterySnapshot(pct, charging)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = read(intent)
        }
        var registered = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!registered) {
                    read(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
                    registered = true
                }
                Lifecycle.Event.ON_PAUSE -> if (registered) {
                    context.unregisterReceiver(receiver)
                    registered = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (registered) context.unregisterReceiver(receiver)
        }
    }
    return snapshot
}

@Immutable
private data class DayStamp(val year: Int, val dayOfYear: Int)

private fun currentDayStamp(): DayStamp {
    val cal = Calendar.getInstance()
    return DayStamp(
        year = cal.get(Calendar.YEAR),
        dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    )
}

/** Today's date (drives the daily fortune); refreshed once at midnight while RESUMED (and on each resume). */
@Composable
private fun rememberDayStamp(): DayStamp {
    val lifecycleOwner = LocalLifecycleOwner.current
    var stamp by remember { mutableStateOf(currentDayStamp()) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                stamp = currentDayStamp()
                val now = Calendar.getInstance()
                val midnight = (now.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 1)
                    set(Calendar.MILLISECOND, 0)
                }
                delay((midnight.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L))
            }
        }
    }
    return stamp
}

/**
 * Compact keitai sub-display marquee.
 *
 * @param unreadCount unread notification count (passed in: Home already collects it).
 * @param isActivePage the ticker only steps while Home is the visible page.
 * @param isDarkStyle true = dark sub-OLED, false = pastel theme-matched surface.
 * @param isFocused D-pad focus from the Home focus model (draws the soft glow halo).
 * @param onClick tap / Center action; null makes the strip non-interactive.
 */
@Composable
fun SubDisplayTickerStrip(
    unreadCount: Int,
    modifier: Modifier = Modifier,
    isActivePage: Boolean = true,
    isDarkStyle: Boolean = true,
    isFocused: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val media by MediaSessionTracker.mediaState.collectAsState()
    val battery = rememberBatterySnapshot()
    val day = rememberDayStamp()

    val fortune = remember(day.year, day.dayOfYear) { dailyFortuneLabel(day.year, day.dayOfYear) }
    val feedText = remember(
        media.isPlaying, media.artist, media.title, unreadCount, battery, fortune
    ) {
        buildSubDisplayFeed(
            isMediaPlaying = media.isPlaying,
            artist = media.artist,
            title = media.title,
            unreadCount = unreadCount,
            batteryLevel = battery.level,
            isCharging = battery.isCharging,
            fortune = fortune
        )
    }

    val shownText = rememberSteppedTickerText(
        text = feedText,
        visibleCharCount = SUB_DISPLAY_VISIBLE_CHARS,
        enabled = isActivePage && isResumed
    )

    // Pastel style: a frosted mini greeting pill. Light themes get a slightly see-through
    // surface; dark themes (e.g. Midnight Mochi) keep the solid surface for contrast.
    val surface = ActiveTheme.surface
    val isLightTheme = ActiveTheme.background.luminance() > 0.5f
    val backdrop = when {
        isDarkStyle -> SubOledBackdrop
        isLightTheme -> surface.copy(alpha = 0.90f)
        else -> surface
    }
    val inkColor = if (isDarkStyle) SubOledInk else ActiveTheme.accentDeep
    val capColor = inkColor.copy(alpha = 0.75f)

    var hasComposeFocus by remember { mutableStateOf(false) }
    val activate: () -> Unit = {
        if (onClick != null) {
            SoundEffectManager.play(context, SoundEffect.SELECT)
            onClick()
        }
    }

    var interactive: Modifier = Modifier
    if (onClick != null) {
        interactive = Modifier
            .onFocusChanged { hasComposeFocus = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    activate()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = activate
            )
    }

    // Outer box: unclipped layer so the soft glow halo can spill past the pill.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(
                isSelected = isFocused || hasComposeFocus,
                cornerRadius = 12.dp,
                containerTint = Color.Transparent
            )
            .then(interactive)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(SubDisplayShape)
                .background(backdrop)
                .border(1.4.dp, ActiveTheme.cardBorder, SubDisplayShape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✦",
                color = capColor,
                fontSize = 10.sp,
                fontFamily = LocalLauncherFontFamily.current,
                maxLines = 1,
                softWrap = false
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = shownText,
                    color = inkColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = LocalLauncherFontFamily.current,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
            Text(
                text = "♡",
                color = capColor,
                fontSize = 10.sp,
                fontFamily = LocalLauncherFontFamily.current,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
