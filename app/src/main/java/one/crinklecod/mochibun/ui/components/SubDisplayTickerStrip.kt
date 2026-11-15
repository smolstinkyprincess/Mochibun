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

/** Deep plum/navy LCD backdrop for the dark "sub-OLED" style. */
internal val SubOledBackdrop = Color(0xFF23182E)
/** Luminous pastel lavender "phosphor" ink for the dark sub-OLED style. */
internal val SubOledInk = Color(0xFFF3D9FF)
private const val FEED_SEPARATOR = "  ✦  "
/** MediaSessionTracker's placeholder artist; treated like a blank artist. */
private const val PLACEHOLDER_ARTIST = "Unknown Artist"


/** Cozy, plain-text fortunes (no numbers, no kaomoji). One per day; see [dailyFortuneLabel]. */
internal val COZY_FORTUNES = arrayOf(
    "good vibes ahead ♡",
    "a peaceful little day awaits",
    "treat yourself to something sweet",
    "something cute will happen soon",
    "take a cozy breath & stretch",
    "small steps still count ♡",
    "a warm drink would be lovely",
    "someone is thinking of you",
    "be gentle with yourself today",
    "a happy surprise is on its way",
    "today is a good day to rest",
    "you are doing better than you think"
)

/** Deterministic fortune for a given day: same answer all day, changes at midnight. */
internal fun dailyFortuneLabel(year: Int, dayOfYear: Int): String {
    val seed = year * 400 + dayOfYear
    // Multiply by a prime so consecutive days don't walk the list in order.
    val index = Math.floorMod(seed * 7919, COZY_FORTUNES.size)
    return COZY_FORTUNES[index]
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
 * doesn't) or activity recognition isn't granted (API 29+). The sensor is cumulative since boot;
 * [DailyStepState] turns it into a since-midnight count persisted in PreferencesManager. The
 * listener is registered only while RESUMED.
 */
@Composable
private fun rememberStepsToday(): Long? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var steps by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(context, lifecycleOwner) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        if (sensorManager == null || sensor == null || !permitted) {
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val cal = Calendar.getInstance()
                val today = DailyStepState.dayStamp(cal.get(Calendar.YEAR), cal.get(Calendar.DAY_OF_YEAR))
                val state = DailyStepState.update(
                    PreferencesManager.getDailyStepState(context),
                    today,
                    event.values[0].toLong()
                )
                PreferencesManager.setDailyStepState(context, state)
                steps = state.stepsToday
                // Mochibunny earns a treat per 1,000 steps (capped daily)
                PetManager.onStepCounterSensor(event.values[0])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        var registered = false
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
    return steps
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
    val memoText by PreferencesManager.quickMemoText.collectAsState()
    val battery = rememberBatterySnapshot()
    val nextAlarm = rememberNextAlarmLabel()
    val stepsToday = rememberStepsToday()
    val day = rememberDayStamp()

    val fortune = remember(day.year, day.dayOfYear) { dailyFortuneLabel(day.year, day.dayOfYear) }
    val memo = memoText.takeIf { it.isNotBlank() }
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
