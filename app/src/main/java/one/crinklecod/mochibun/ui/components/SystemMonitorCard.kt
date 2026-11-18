package one.crinklecod.mochibun.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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

    private var lastProcCpuTimeMs: Long = -1L
    private var lastSampleUptimeMs: Long = -1L
    private var lastSysTotalTicks: Long = -1L
    private var lastSysIdleTicks: Long = -1L

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

    fun sample(context: Context): SystemStats {
        // --- 1. RAM Sampling ---
        var totalRamMb = 0L
        var usedRamMb = 0L
        var ramPercent = 0f
        var mochibunPssMb = 0L

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

            // Mochibun process PSS in MB
            try {
                val pssArr = am.getProcessMemoryInfo(intArrayOf(Process.myPid()))
                val pssKb = pssArr.firstOrNull()?.totalPss ?: 0
                if (pssKb > 0) {
                    mochibunPssMb = pssKb / 1024L
                }
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}

        if (mochibunPssMb <= 0L) {
            try {
                val pssBytes = Debug.getPss()
                if (pssBytes > 0) {
                    mochibunPssMb = pssBytes / (1024L * 1024L)
                }
            } catch (_: Throwable) {}
        }
        if (mochibunPssMb <= 0L) {
            val rt = Runtime.getRuntime()
            mochibunPssMb = ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).coerceAtLeast(1L)
        }

        // --- 2. CPU Sampling ---
        val nowProcCpu = Process.getElapsedCpuTime()
        val nowUptime = SystemClock.uptimeMillis()
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
    triggerRefreshKey: Int = 0
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val cardShape = remember { RoundedCornerShape(22.dp) }

    var stats by remember { mutableStateOf(SystemStats()) }
    var isSparkling by remember { mutableStateOf(false) }

    // Instant manual refresh on click or D-pad Center / OK activation
    LaunchedEffect(triggerRefreshKey) {
        if (triggerRefreshKey > 0) {
            isSparkling = true
            withContext(Dispatchers.IO) {
                stats = SystemStatsSampler.sample(context)
            }
            delay(1200)
            isSparkling = false
        }
    }

    // Low-overhead background sampling coroutine, runs strictly when active on page 0
    LaunchedEffect(isActivePage) {
        if (!isActivePage) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            stats = SystemStatsSampler.sample(context)
            while (isActive) {
                delay(2800)
                stats = SystemStatsSampler.sample(context)
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
        showCornerBows = false
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
                markerKind = MetricMarkerKind.SPARKLE,
                badgeBg = palette.squircleSecondary.copy(alpha = 0.45f),
                title = "CPU",
                overallReadout = cpuOverallReadout,
                rightPillText = cpuPillText,
                progress = cpuProgress,
                progressColors = listOf(palette.squircleSecondary, palette.primary),
                trackColor = palette.squirclePrimary.copy(alpha = 0.24f),
                markerFillColor = palette.squircleSecondary,
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
                markerKind = MetricMarkerKind.HEART,
                badgeBg = palette.squirclePrimary.copy(alpha = 0.45f),
                title = "RAM",
                overallReadout = ramOverallReadout,
                rightPillText = ramPillText,
                progress = ramProgress,
                progressColors = listOf(palette.primary, palette.squirclePrimary),
                trackColor = palette.squirclePrimary.copy(alpha = 0.24f),
                markerFillColor = palette.primary,
                deepColor = palette.deepAccent,
                textColor = palette.textColor,
                outlineColor = palette.outline
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Kawaii Status Footer Line reacting to memory load
            val statusFooter = when {
                stats.ramPercent > 90f -> "💦 sleepy memory ( ; ω ; )"
                stats.ramPercent >= 80f -> "✦ cozy & busy ( •̀ᴗ•́ )و ✦"
                else -> "♡ comfy & breezy ( ˘͈ ᵕ ˘͈ ) ♡"
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
    }
    }
}

private enum class MetricMarkerKind {
    SPARKLE,
    HEART
}

@Composable
private fun MetricRow(
    glyph: String,
    markerKind: MetricMarkerKind,
    badgeBg: Color,
    title: String,
    overallReadout: String,
    rightPillText: String,
    progress: Float,
    progressColors: List<Color>,
    trackColor: Color,
    markerFillColor: Color,
    deepColor: Color,
    textColor: Color,
    outlineColor: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0.02f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "MetricProgress"
    )

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

        KeitaiMetricGauge(
            progress = animatedProgress,
            markerKind = markerKind,
            progressColors = progressColors,
            trackColor = trackColor,
            trackBorderColor = outlineColor.copy(alpha = 0.35f),
            tickColor = outlineColor.copy(alpha = 0.35f),
            markerFillColor = markerFillColor,
            markerOutlineColor = deepColor
        )
    }
}

/**
 * Pastel pocket-monitor gauge with a 6dp rounded track, subtle 25/50/75% segment ticks,
 * and a `✦` or `♥` marker at the current fill position (zero per-frame allocations).
 */
@Composable
private fun KeitaiMetricGauge(
    progress: Float,
    markerKind: MetricMarkerKind,
    progressColors: List<Color>,
    trackColor: Color,
    trackBorderColor: Color,
    tickColor: Color,
    markerFillColor: Color,
    markerOutlineColor: Color,
    modifier: Modifier = Modifier
) {
    val markerPath = remember { Path() }
    val fillBrush = remember(progressColors) { Brush.horizontalGradient(progressColors) }
    val density = LocalDensity.current
    val trackBorderStroke = remember(density) { Stroke(width = with(density) { 0.75.dp.toPx() }) }
    val markerStroke = remember(density) { Stroke(width = with(density) { 1.1.dp.toPx() }) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val trackHeight = 6.dp.toPx()
        val trackRadius = trackHeight / 2f
        val trackY = (h - trackHeight) / 2f
        val clamped = progress.coerceIn(0f, 1f)

        // 1. Soft pastel pill track + hairline outline
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackY),
            size = Size(w, trackHeight),
            cornerRadius = CornerRadius(trackRadius, trackRadius)
        )
        drawRoundRect(
            color = trackBorderColor,
            topLeft = Offset(0f, trackY),
            size = Size(w, trackHeight),
            cornerRadius = CornerRadius(trackRadius, trackRadius),
            style = trackBorderStroke
        )

        // 2. Active pastel gradient fill
        if (clamped > 0f) {
            val fillWidth = (w * clamped).coerceAtLeast(trackHeight).coerceAtMost(w)
            drawRoundRect(
                brush = fillBrush,
                topLeft = Offset(0f, trackY),
                size = Size(fillWidth, trackHeight),
                cornerRadius = CornerRadius(trackRadius, trackRadius)
            )
        }

        // 3. Subtle quarter segment ticks (25%, 50%, 75%)
        val tickInset = 1.dp.toPx()
        val tickStrokeW = 1.dp.toPx()
        val tickTop = trackY + tickInset
        val tickBottom = trackY + trackHeight - tickInset
        for (i in 1..3) {
            val tx = w * (i * 0.25f)
            drawLine(
                color = tickColor,
                start = Offset(tx, tickTop),
                end = Offset(tx, tickBottom),
                strokeWidth = tickStrokeW
            )
        }

        // 4. Tiny ✦ (CPU) or ♥ (RAM) marker at current fill position
        val markerSize = if (markerKind == MetricMarkerKind.SPARKLE) 5.2.dp.toPx() else 4.8.dp.toPx()
        val mx = (w * clamped).coerceIn(markerSize, (w - markerSize).coerceAtLeast(markerSize))
        val my = h / 2f

        markerPath.reset()
        when (markerKind) {
            MetricMarkerKind.SPARKLE -> {
                markerPath.moveTo(mx, my - markerSize)
                markerPath.lineTo(mx + markerSize * 0.30f, my - markerSize * 0.30f)
                markerPath.lineTo(mx + markerSize, my)
                markerPath.lineTo(mx + markerSize * 0.30f, my + markerSize * 0.30f)
                markerPath.lineTo(mx, my + markerSize)
                markerPath.lineTo(mx - markerSize * 0.30f, my + markerSize * 0.30f)
                markerPath.lineTo(mx - markerSize, my)
                markerPath.lineTo(mx - markerSize * 0.30f, my - markerSize * 0.30f)
                markerPath.close()
            }
            MetricMarkerKind.HEART -> {
                markerPath.moveTo(mx, my + markerSize * 0.75f)
                markerPath.cubicTo(
                    mx - markerSize * 1.15f, my + markerSize * 0.1f,
                    mx - markerSize * 1.15f, my - markerSize * 0.85f,
                    mx, my - markerSize * 0.35f
                )
                markerPath.cubicTo(
                    mx + markerSize * 1.15f, my - markerSize * 0.85f,
                    mx + markerSize * 1.15f, my + markerSize * 0.1f,
                    mx, my + markerSize * 0.75f
                )
                markerPath.close()
            }
        }
        drawPath(markerPath, markerFillColor)
        drawPath(markerPath, markerOutlineColor, style = markerStroke)
    }
}
