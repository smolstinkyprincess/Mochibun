package one.crinklecod.mochibun.ui.components

import one.crinklecod.mochibun.data.model.CardBorderStyle
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.lerp
import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Immutable metrics data for Mochibun System Monitor.
 */
@Immutable
data class SystemStats(
    val systemCpuPercent: Float = 0f,
    val mochibunCpuPercent: Float = 0f,
    val isSystemCpuRestricted: Boolean = false,
    val activeThreadCount: Int = 0,
    val usedRamMb: Long = 0L,
    val totalRamMb: Long = 0L,
    val mochibunPssMb: Long = 0L,
    val ramPercent: Float = 0f
)

/**
 * Hardware-efficient sampling utility optimized for Chinoe AT-M120 (MT6761 quad-core, Android 12).
 * Operates without GC pressure, allocations in hot loops, or background polling threads.
 */
object SystemStatsSampler {
    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private const val PSS_REFRESH_INTERVAL_MS = 10_000L

    private var lastProcCpuTimeMs: Long = -1L
    private var lastSampleUptimeMs: Long = -1L
    private var lastSysTotalTicks: Long = -1L
    private var lastSysIdleTicks: Long = -1L
    private var lastPssSampleUptimeMs: Long = -1L
    private var lastMochibunPssMb: Long = 0L

    init {
        try {
            lastProcCpuTimeMs = Process.getElapsedCpuTime()
            lastSampleUptimeMs = SystemClock.uptimeMillis()
            val initialStat = readProcStat()
            if (initialStat != null) {
                lastSysTotalTicks = initialStat.first
                lastSysIdleTicks = initialStat.second
            }
        } catch (_: Throwable) {}
    }

    fun sample(context: Context, forcePssRefresh: Boolean = false): SystemStats {
        // --- 1. RAM Sampling ---
        var totalRamMb = 0L
        var usedRamMb = 0L
        var ramPercent = 0f
        var mochibunPssMb = lastMochibunPssMb
        val nowUptime = SystemClock.uptimeMillis()
        val shouldRefreshPss = forcePssRefresh ||
            lastPssSampleUptimeMs < 0L ||
            (nowUptime - lastPssSampleUptimeMs) >= PSS_REFRESH_INTERVAL_MS

        try {
            val am = context.getSystemService(ActivityManager::class.java)
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalBytes = memInfo.totalMem
            val availBytes = memInfo.availMem
            val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)
            totalRamMb = totalBytes / (1024L * 1024L)
            usedRamMb = usedBytes / (1024L * 1024L)
            ramPercent = if (totalRamMb > 0) {
                (usedRamMb.toFloat() / totalRamMb.toFloat()) * 100f
            } else 0f

            // Mochibun process PSS in MB (throttled to avoid heavy AMS Binder/smaps IPC every tick)
            if (shouldRefreshPss) {
                try {
                    val pssArr = am.getProcessMemoryInfo(intArrayOf(Process.myPid()))
                    val pssKb = pssArr.firstOrNull()?.totalPss ?: 0
                    if (pssKb > 0) {
                        mochibunPssMb = pssKb / 1024L
                        lastMochibunPssMb = mochibunPssMb
                        lastPssSampleUptimeMs = nowUptime
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        if (mochibunPssMb <= 0L) {
            try {
                // Debug.getPss() returns kilobytes.
                val pssKb = Debug.getPss()
                if (pssKb > 0) {
                    mochibunPssMb = (pssKb / 1024L).coerceAtLeast(1L)
                }
            } catch (_: Throwable) {}
        }
        if (mochibunPssMb <= 0L) {
            val rt = Runtime.getRuntime()
            mochibunPssMb = ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).coerceAtLeast(1L)
        }

        // --- 2. CPU Sampling ---
        val nowProcCpu = Process.getElapsedCpuTime()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        var mochibunCpuPercent = 0f
        if (lastProcCpuTimeMs > 0 && lastSampleUptimeMs > 0) {
            val deltaProcCpu = (nowProcCpu - lastProcCpuTimeMs).coerceAtLeast(0L)
            val deltaUptime = (nowUptime - lastSampleUptimeMs).coerceAtLeast(1L)
            mochibunCpuPercent = ((deltaProcCpu.toFloat() / (deltaUptime.toFloat() * cores)) * 100f).coerceIn(0f, 100f)
        }
        lastProcCpuTimeMs = nowProcCpu
        lastSampleUptimeMs = nowUptime

        var systemCpuPercent = 0f
        var isRestricted = false
        val activeThreads = Thread.activeCount()

        val stat = readProcStat()
        if (stat != null) {
            val (currTotal, currIdle) = stat
            if (lastSysTotalTicks > 0 && lastSysIdleTicks > 0) {
                val deltaTotal = (currTotal - lastSysTotalTicks).coerceAtLeast(1L)
                val deltaIdle = (currIdle - lastSysIdleTicks).coerceAtLeast(0L)
                systemCpuPercent = (((deltaTotal - deltaIdle).toFloat() / deltaTotal.toFloat()) * 100f).coerceIn(0f, 100f)
            }
            lastSysTotalTicks = currTotal
            lastSysIdleTicks = currIdle
        } else {
            isRestricted = true
            systemCpuPercent = mochibunCpuPercent
        }

        return SystemStats(
            systemCpuPercent = systemCpuPercent,
            mochibunCpuPercent = mochibunCpuPercent,
            isSystemCpuRestricted = isRestricted,
            activeThreadCount = activeThreads,
            usedRamMb = usedRamMb,
            totalRamMb = totalRamMb,
            mochibunPssMb = mochibunPssMb,
            ramPercent = ramPercent
        )
    }

    private fun readProcStat(): Pair<Long, Long>? {
        return try {
            val file = File("/proc/stat")
            if (!file.canRead()) return null
            file.bufferedReader().use { reader ->
                val line = reader.readLine() ?: return null
                if (!line.startsWith("cpu ")) return null
                val parts = line.trim().split(WHITESPACE_REGEX).drop(1).mapNotNull { it.toLongOrNull() }
                if (parts.size >= 4) {
                    val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
                    val total = parts.sum()
                    Pair(total, idle)
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Lightweight, pastel kawaii System Monitor card for Mochibun.
 * Conforms to Mochibun theme tokens, physical D-pad navigation contracts, and 480x800 MT6761 constraints.
 */
@Composable
fun SystemMonitorCard(
    isActivePage: Boolean,
    isFocused: Boolean,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
    triggerRefreshKey: Int = 0,
    // Under washi tape / a corner pin: swap a custom 9-slice frame for a plain stitched border
    plainBorder: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val cardShape = remember { RoundedCornerShape(22.dp) }
    val intervalSec by PreferencesManager.systemMonitorInterval.collectAsState()

    var stats by remember { mutableStateOf(SystemStats()) }
    var isSparkling by remember { mutableStateOf(false) }

    // Instant manual refresh on click or D-pad Center / OK activation
    LaunchedEffect(triggerRefreshKey) {
        if (triggerRefreshKey > 0) {
            isSparkling = true
            withContext(Dispatchers.IO) {
                stats = SystemStatsSampler.sample(context, forcePssRefresh = true)
            }
            delay(1200)
            isSparkling = false
        }
    }

    // Lifecycle-scoped background sampling coroutine, runs only while STARTED on page 0
    LaunchedEffect(isActivePage, intervalSec, lifecycleOwner) {
        if (!isActivePage) return@LaunchedEffect
        val intervalMs = intervalSec.coerceIn(1, 60) * 1000L
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.IO) {
                stats = SystemStatsSampler.sample(context, forcePssRefresh = stats.totalRamMb == 0L)
                while (isActive) {
                    delay(intervalMs)
                    stats = SystemStatsSampler.sample(context)
                }
            }
        }
    }

    // Same dainty lace + pearl rim as the Home cards. The 6dp frill replaces 6dp of the old
    // inner padding (16/13 -> 10/7), so the card keeps its exact size.
    KeitaiLaceContainer(
        modifier = modifier
            .fillMaxWidth()
            .softGlowFocus(isSelected = isFocused, cornerRadius = 22.dp)
            .kawaiiCardMotifs(isFocused = isFocused, cornerRadius = 22.dp),
        cardCornerRadius = 22.dp,
        scallopDepth = 3.dp,
        beadDiameter = 4.dp,
        contentPadding = 6.dp,
        containerColor = palette.container,
        borderColor = palette.outline,
        beadBorderColor = palette.deepAccent.copy(alpha = 0.45f),
        showCornerBows = false,
        allowCustomNineSlice = !plainBorder,
        borderStyleOverride = if (plainBorder) CardBorderStyle.STITCH else null
    ) {
    // Inner card: same shape as the lace card, so tap ripples stay rounded.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable {
                onCardClick()
            }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isHighLoad = (!stats.isSystemCpuRestricted && stats.systemCpuPercent > 80f) || stats.ramPercent > 85f
            val mascotSlot = if (isHighLoad) MascotAnimSlot.SULKY else currentMoodSlot()

            // Header Accent: Mascot loaf perching proudly above the title (52.dp)
            MochibunMascot(
                slot = mascotSlot,
                size = 52.dp,
                themeTintColor = theme.mascotTintColor,
                onClick = { onCardClick() },
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = if (isSparkling) "— ✨ Stats Refreshed ✨ —" else "— ♡ System Stats ♡ —",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.deepAccent,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 1. CPU Metric Row
            val cpuPercent = if (!stats.isSystemCpuRestricted) {
                stats.systemCpuPercent
            } else {
                stats.mochibunCpuPercent
            }
            val cpuProgress = (cpuPercent / 100f).coerceIn(0f, 1f)
            val cpuOverallReadout = "${String.format(Locale.US, "%.1f", cpuPercent)}%"
            val cpuPillText = "${stats.activeThreadCount} threads"

            MetricRow(
                glyph = "✦",
                badgeBg = palette.squircleSecondary.copy(alpha = 0.45f),
                title = "CPU",
                overallReadout = cpuOverallReadout,
                rightPillText = cpuPillText,
                progress = cpuProgress,
                progressColors = listOf(palette.squircleSecondary, palette.primary),
                trackColor = palette.squirclePrimary.copy(alpha = 0.24f),
                deepColor = palette.deepAccent,
                textColor = palette.textColor,
                outlineColor = palette.outline
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. RAM (Memory) Metric Row
            val ramProgress = (stats.ramPercent / 100f).coerceIn(0f, 1f)
            val ramOverallReadout = if (stats.totalRamMb > 0) {
                "${stats.usedRamMb} / ${stats.totalRamMb} MB"
            } else {
                "Sampling..."
            }
            val ramPillText = "App ~${stats.mochibunPssMb} MB"

            MetricRow(
                glyph = "♥",
                badgeBg = palette.squirclePrimary.copy(alpha = 0.45f),
                title = "RAM",
                overallReadout = ramOverallReadout,
                rightPillText = ramPillText,
                progress = ramProgress,
                progressColors = listOf(palette.primary, palette.squirclePrimary),
                trackColor = palette.squirclePrimary.copy(alpha = 0.24f),
                deepColor = palette.deepAccent,
                textColor = palette.textColor,
                outlineColor = palette.outline
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Kawaii Status Footer Line reacting to memory load (its kaomoji is the corner badge)
            val statusFooter = when (ramMood(stats.ramPercent)) {
                RamMood.TIGHT -> "💦 sleepy memory 💦"
                RamMood.BUSY -> "✦ cozy & busy ✦"
                RamMood.COMFY -> "♡ comfy & breezy ♡"
            }
            Text(
                text = statusFooter,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = palette.deepAccent.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Tiny mood badge in the top corner, reacting to RAM pressure
        Text(
            text = ramMood(stats.ramPercent).kaomoji,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = palette.deepAccent,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(50))
                .background(palette.squirclePrimary.copy(alpha = 0.45f))
                .border(1.dp, palette.outline.copy(alpha = 0.6f), RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
    }
}

private enum class RamMood(val kaomoji: String) {
    COMFY("( ˘͈ ᵕ ˘͈ )"),
    BUSY("( •̀ᴗ•́ )و"),
    TIGHT("( ;´・ω・)")
}

/** Same thresholds the footer line has always used. */
private fun ramMood(ramPercent: Float): RamMood = when {
    ramPercent > 90f -> RamMood.TIGHT
    ramPercent >= 80f -> RamMood.BUSY
    else -> RamMood.COMFY
}

@Composable
private fun MetricRow(
    glyph: String,
    badgeBg: Color,
    title: String,
    overallReadout: String,
    rightPillText: String,
    progress: Float,
    progressColors: List<Color>,
    trackColor: Color,
    deepColor: Color,
    textColor: Color,
    outlineColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact squircle pastel badge
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(badgeBg)
                    .border(1.dp, outlineColor.copy(alpha = 0.55f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = glyph,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = deepColor,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.width(7.dp))

            // Title and clean metric readout (no truncation on 480px width)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = overallReadout,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.82f),
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Compact right-aligned pill badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeBg.copy(alpha = 0.6f))
                    .border(1.dp, outlineColor.copy(alpha = 0.65f), RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rightPillText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = deepColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Discrete blocks: a refresh just changes how many are lit, no tween frames needed
        CandyBlockGauge(
            progress = progress,
            fillColors = progressColors,
            emptyColor = trackColor,
            outlineColor = outlineColor.copy(alpha = 0.45f)
        )
    }
}

/** Blocks per gauge: one block = 10%, read at a glance like a keitai signal meter. */
private const val CANDY_BLOCKS = 10

/**
 * Segmented pastel "candy block" gauge (`■■■■□□□□□□`): lit blocks blend from the first to the
 * last [fillColors] entry, unlit blocks are a soft track with a hairline outline. Drawn straight
 * into the Canvas with a remembered stroke, so a redraw allocates nothing.
 */
@Composable
private fun CandyBlockGauge(
    progress: Float,
    fillColors: List<Color>,
    emptyColor: Color,
    outlineColor: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val outlineStroke = remember(density) { Stroke(width = with(density) { 0.75.dp.toPx() }) }
    val startColor = fillColors.first()
    val endColor = fillColors.last()
    // Any non-zero reading lights at least one block, so "barely busy" never looks dead
    val lit = if (progress <= 0f) 0 else (progress.coerceIn(0f, 1f) * CANDY_BLOCKS).roundToInt().coerceIn(1, CANDY_BLOCKS)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
    ) {
        val gap = 3.dp.toPx()
        val blockW = (size.width - gap * (CANDY_BLOCKS - 1)) / CANDY_BLOCKS
        if (blockW <= 0f) return@Canvas
        val blockH = size.height
        val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        for (i in 0 until CANDY_BLOCKS) {
            val topLeft = Offset(i * (blockW + gap), 0f)
            val blockSize = Size(blockW, blockH)
            if (i < lit) {
                drawRoundRect(lerp(startColor, endColor, i / (CANDY_BLOCKS - 1f)), topLeft, blockSize, radius)
            } else {
                drawRoundRect(emptyColor, topLeft, blockSize, radius)
            }
            drawRoundRect(outlineColor, topLeft, blockSize, radius, style = outlineStroke)
        }
    }
}
