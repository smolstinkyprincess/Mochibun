/**
 * Outer sub-display ticker feed, shown as a marquee inside the Home greeting banner.
 *
 * Mimics the narrow external OLED/LCD strip on the lid of classic FOMA / au flip phones:
 * the greeting followed by a one-line status feed (pinned Quick Memo, now playing, unread
 * count, next alarm, today's steps, battery and a cozy daily fortune) in discrete 3 Hz steps.
 * No date segment: the clock right below already shows it.
 */
package one.crinklecod.mochibun.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
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
import one.crinklecod.mochibun.data.prefs.DailyStepState
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.MediaSessionTracker
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.time.DayOfWeek
import java.time.LocalDate

/** Deep plum/navy LCD backdrop for the dark "sub-OLED" style. */
internal val SubOledBackdrop = Color(0xFF23182E)
/** Luminous pastel lavender "phosphor" ink for the dark sub-OLED style. */
internal val SubOledInk = Color(0xFFF3D9FF)
private const val FEED_SEPARATOR = "  ✦  "
/** MediaSessionTracker's placeholder artist; treated like a blank artist. */
private const val PLACEHOLDER_ARTIST = "Unknown Artist"


/** Cozy, plain-text fortunes grouped by weekday (no numbers, no kaomoji). */
internal val COZY_FORTUNES_BY_DAY: Map<DayOfWeek, List<String>> = mapOf(
    DayOfWeek.MONDAY to listOf(
        "monday feels soft and slow ♡",
        "snuggle in and stay cozy ✨",
        "a warm blanket monday awaits ♡",
        "sip something sweet and slow 🌸",
        "little steps, big comfort today ♡",
        "rest your paws and breathe ✨",
        "monday hugs are extra soft ♡",
        "a quiet cozy morning for you 🌸",
        "treat yourself gently today ♡",
        "soft skies and warm snacks ✨",
        "mondays are secretly cozy ♡",
        "a warm start blooms into warmth 🌸",
        "be gentle with yourself today ✨",
        "soft steps make sweet mornings ♡",
        "something lovely finds you today 🌸",
        "rest is part of the magic ✨",
        "little joys are still real joys ♡",
        "your cozy counts for something 🌸",
        "take it slow & breathe it in ✨",
        "today holds a tiny surprise ♡"
    ),
    DayOfWeek.TUESDAY to listOf(
        "tuesday smells like warm vanilla ♡",
        "a gentle mood carries you far 🌸",
        "soft things are waiting for you ✨",
        "sip something warm & just breathe ♡",
        "little kindnesses bloom today 🌸",
        "your softness is a superpower ✨",
        "cozy corners call your name ♡",
        "something sweet is on its way 🌸",
        "rest your eyes for a moment ✨",
        "tuesday holds a tiny warm hug ♡",
        "tuesday is softer than it seems ♡",
        "a gentle surprise is on its way 🌸",
        "take a tiny breather & sip warm tea ✨",
        "cozy moments are yours to keep ♡",
        "good things bloom on quiet days 🌸",
        "wrap up snug & let today unfold ✨",
        "kindness comes back around today ♡",
        "a little rest goes a long way 🌸",
        "something sweet is waiting for you ✨",
        "you are doing just fine, really ♡"
    ),
    DayOfWeek.WEDNESDAY to listOf(
        "wednesday holds a hidden sweetness ♡",
        "halfway there & still so lovely 🌸",
        "breathe deep, the week is gentle ✨",
        "soft things find soft hearts today ♡",
        "a warm hug finds you midweek 🌸",
        "little wins still count so much ✨",
        "rest whenever your heart needs it ♡",
        "something blooms quietly today 🌸",
        "you deserve every cozy moment ✨",
        "the best part of today is you ♡",
        "you made it to the middle ♡",
        "midweek magic is real, promise 🌸",
        "breathe out & let the soft in ✨",
        "a cozy nook is calling your name ♡",
        "little wins still count today 🌸",
        "the week bends gently toward you ✨",
        "rest your eyes for just a moment ♡",
        "something warm finds you today 🌸",
        "you are exactly where you should be ✨",
        "wednesday holds a quiet sweetness ♡"
    ),
    DayOfWeek.THURSDAY to listOf(
        "thursday carries quiet good luck ♡",
        "the weekend whispers from nearby 🌸",
        "treat yourself to something soft ✨",
        "a warm hug finds you today ♡",
        "good things are almost here 🌸",
        "let today be light & unhurried ✨",
        "you deserve a little extra sweet ♡",
        "rest now, good things come soon 🌸",
        "a cozy moment blooms today ✨",
        "thursday loves you back, truly ♡",
        "the weekend warmth is nearly here ♡",
        "thursday holds a hidden sweetness 🌸",
        "one soft step at a time today ✨",
        "a little treat is well deserved ♡",
        "good things are quietly growing 🌸",
        "let yourself be carried by today ✨",
        "snuggle something soft & breathe ♡",
        "you are closer than you think 🌸",
        "a warm moment blooms just for you ✨",
        "almost there & you are doing well ♡"
    ),
    DayOfWeek.FRIDAY to listOf(
        "you made it & you deserve this ♡",
        "something lovely is just ahead ♡",
        "rest is your reward today 🌸",
        "the cozy is all yours tonight ✨",
        "wrap up warm & let go gently ♡",
        "a sweet ending blooms for you 🌸",
        "you did so well this week ♡",
        "you made it, sweet one ♡",
        "friday feels like a warm hug 🌸",
        "let the weekend softness begin ✨",
        "you earned every cozy moment ♡",
        "good things bloom at week's end 🌸",
        "wrap up snug & celebrate today ✨",
        "the best part of today is you ♡",
        "a sweet reward is waiting near 🌸",
        "release the week & breathe easy ✨",
        "friday magic is all around you ♡"
    ),
    DayOfWeek.SATURDAY to listOf(
        "slow mornings are a gift today ♡",
        "saturday says sleep in a little 🌸",
        "no rush, just softness & warmth ✨",
        "a lazy day is a lovely day ♡",
        "let yourself do absolutely nothing 🌸",
        "cozy is the whole plan today ✨",
        "snacks & blankets & sweet moments ♡",
        "the day unfolds just for you 🌸",
        "your comfort matters most today ✨",
        "saturday holds only good things ♡",
        "saturdays were made for softness ♡",
        "sleep in just a little bit more 🌸",
        "today belongs entirely to you ✨",
        "no rush, no hurry, just warmth ♡",
        "a slow morning is a sweet gift 🌸",
        "wrap yourself in something cozy ✨",
        "today is yours to fill with joy ♡",
        "snacks & snuggles are valid plans ♡"
    ),
    DayOfWeek.SUNDAY to listOf(
        "rest deeply, you have earned it 🌸",
        "let today be slow & unhurried ✨",
        "a warm drink & a blanket await ♡",
        "gentle sunday magic surrounds you 🌸",
        "breathe in the quiet sweetness ✨",
        "today asks nothing hard of you ♡",
        "snuggle close & let worries go 🌸",
        "sunday holds you soft & warm ✨",
        "tomorrow is gentle, rest for now ♡",
        "sunday is made of quiet magic ♡",
        "sip slowly & watch the light ♡",
        "gentle is the only pace today 🌸",
        "let the world be still a while ✨",
        "you deserve this restful moment ♡",
        "something warm blooms just for you 🌸",
        "carry softness into the week ✨",
        "sunday loves you back, always ♡"
    )
)

/** Deterministic fortune for a given day: same answer all day, changes at midnight. */
internal fun dailyFortuneLabel(year: Int, dayOfYear: Int): String {
    val dayOfWeek = LocalDate.ofYearDay(year, dayOfYear).dayOfWeek
    val pool = COZY_FORTUNES_BY_DAY[dayOfWeek].orEmpty()
    if (pool.isEmpty()) return "good vibes ahead ♡"

    val seed = year * 400 + dayOfYear
    // Multiply by a prime so consecutive days don't walk the list in order.
    val index = Math.floorMod(seed * 7919, pool.size)
    return pool[index]
}


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
    fortune: String,
    pinnedMemo: String? = null,
    nextAlarm: String? = null,
    stepsToday: Long? = null,
    greeting: String = ""
): String {
    val parts = ArrayList<String>(8)
    // The Home greeting ("♡ Welcome home, name ♡") always leads the marquee.
    if (greeting.isNotBlank()) parts += greeting.trim()
    // The Quick Memo leads: it's the one thing the user asked to see.
    val memo = pinnedMemo?.replace('\n', ' ')?.trim()
    if (!memo.isNullOrEmpty()) parts += "✎ $memo"
    if (isMediaPlaying && title.isNotBlank()) {
        val cleanArtist = artist.trim().takeUnless { it.isEmpty() || it == PLACEHOLDER_ARTIST }
        parts += if (cleanArtist == null) "♫ ${title.trim()}" else "♫ $cleanArtist - ${title.trim()}"
    }
    if (unreadCount > 0) parts += "✉ $unreadCount new"
    if (!nextAlarm.isNullOrBlank()) parts += "⏰ $nextAlarm"
    if (stepsToday != null && stepsToday >= 0) parts += "🐾 $stepsToday steps"
    if (batteryLevel in 0..100) parts += "${if (isCharging) "⚡" else "♥"} $batteryLevel%"
    if (fortune.isNotBlank()) parts += fortune
    return parts.joinToString(FEED_SEPARATOR)
}

/** Next alarm time as "7:30 AM" (12h) or "07:30" (24h), following the system time format. */
internal fun formatNextAlarmTime(
    triggerAtMillis: Long,
    is24Hour: Boolean,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    val format = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", locale)
    format.timeZone = timeZone
    return format.format(Date(triggerAtMillis))
}

/**
 * Next alarm (AlarmManager.nextAlarmClock), formatted. Re-read on resume and when the
 * alarm changes (ACTION_NEXT_ALARM_CLOCK_CHANGED); the receiver lives only while RESUMED.
 */
@Composable
private fun rememberNextAlarmLabel(): String? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var label by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context, lifecycleOwner) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        fun read() {
            val trigger = alarmManager?.nextAlarmClock?.triggerTime
            label = trigger?.let { formatNextAlarmTime(it, DateFormat.is24HourFormat(context)) }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = read()
        }
        var registered = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!registered) {
                    context.registerReceiver(receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
                    registered = true
                    read()
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
    return label
}

/**
 * Today's steps from TYPE_STEP_COUNTER, or null when the phone has no step sensor (the AT-M120
 * doesn't) or activity recognition isn't granted (API 29+). Reads once per `ON_RESUME`.
 */
@Composable
private fun rememberStepsToday(): Long? {
    var steps by remember { mutableStateOf<Long?>(null) }
    StepCounterResumeSync { steps = it }
    return steps
}

/**
 * Registers a one-shot [Sensor.TYPE_STEP_COUNTER] read on each `ON_RESUME` and unregisters
 * immediately after the first reading arrives (or on `ON_PAUSE`). Updates [DailyStepState] and
 * forwards the cumulative count to [PetManager.onStepCounterSensor].
 * No-ops cleanly on phones without a step sensor (AT-M120) or `ACTIVITY_RECOGNITION` permission.
 */
@Composable
internal fun StepCounterResumeSync(onStepsToday: ((Long) -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, lifecycleOwner) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        if (sensorManager == null || sensor == null || !permitted) {
            return@DisposableEffect onDispose {}
        }

        var registered = false
        lateinit var listener: SensorEventListener
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (registered) {
                    sensorManager.unregisterListener(listener)
                    registered = false
                }
                val cal = Calendar.getInstance()
                val today = DailyStepState.dayStamp(cal.get(Calendar.YEAR), cal.get(Calendar.DAY_OF_YEAR))
                val state = DailyStepState.update(
                    PreferencesManager.getDailyStepState(context),
                    today,
                    event.values[0].toLong()
                )
                PreferencesManager.setDailyStepState(context, state)
                onStepsToday?.invoke(state.stepsToday)
                // Mochibunny earns a treat per 1,000 steps (capped daily)
                PetManager.onStepCounterSensor(event.values[0])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!registered) {
                    registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                }
                Lifecycle.Event.ON_PAUSE -> if (registered) {
                    sensorManager.unregisterListener(listener)
                    registered = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (registered) sensorManager.unregisterListener(listener)
        }
    }
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
 * Live sub-display feed text for the Home greeting marquee: [greeting] first, then the live
 * segments. Recomputed only when an input changes (never on a ticker step).
 */
@Composable
fun rememberSubDisplayFeed(unreadCount: Int, greeting: String): String {
    val media by MediaSessionTracker.mediaState.collectAsState()
    val memoSlots by PreferencesManager.quickMemoSlots.collectAsState()
    val pinnedSlot by PreferencesManager.pinnedQuickMemoSlot.collectAsState()
    val battery = rememberBatterySnapshot()
    val nextAlarm = rememberNextAlarmLabel()
    val stepsToday = rememberStepsToday()
    val day = rememberDayStamp()

    val fortune = remember(day.year, day.dayOfYear) { dailyFortuneLabel(day.year, day.dayOfYear) }
    // Pinned slot first, else the first non-blank slot; all blank = no memo segment
    val memo = remember(memoSlots, pinnedSlot) { PreferencesManager.tickerQuickMemo(memoSlots, pinnedSlot) }
    return remember(
        greeting, media.isPlaying, media.artist, media.title, unreadCount, battery, fortune,
        memo, nextAlarm, stepsToday
    ) {
        buildSubDisplayFeed(
            isMediaPlaying = media.isPlaying,
            artist = media.artist,
            title = media.title,
            unreadCount = unreadCount,
            batteryLevel = battery.level,
            isCharging = battery.isCharging,
            fortune = fortune,
            pinnedMemo = memo,
            nextAlarm = nextAlarm,
            stepsToday = stepsToday,
            greeting = greeting
        )
    }
}
