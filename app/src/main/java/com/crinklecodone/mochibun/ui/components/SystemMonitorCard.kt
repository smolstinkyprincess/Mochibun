package com.crinklecodone.mochibun.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.focusedContainerColor
import com.crinklecodone.mochibun.ui.theme.palette
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
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
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
            }
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .softGlowFocus(isSelected = isFocused, cornerRadius = 22.dp)
            .kawaiiCardMotifs(isFocused = isFocused, cornerRadius = 22.dp)
            .clip(cardShape)
            .background(palette.container)
            .border(
                width = 1.5.dp,
                color = if (isFocused) Color.Transparent else palette.outline,
                shape = cardShape
            )
            .clickable {
                onCardClick()
            }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isHighLoad = (!stats.isSystemCpuRestricted && stats.systemCpuPercent > 80f) || stats.ramPercent > 85f
            val mascotMood = when {
                isHighLoad -> MascotMood.WORRIED
                else -> getDefaultTimeOfDayMood()
            }

            // Header Accent: Mascot loaf perching proudly above the title (52.dp)
            MochibunMascot(
                mood = mascotMood,
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
            val cpuProgress = if (!stats.isSystemCpuRestricted) {
                stats.systemCpuPercent / 100f
            } else {
                (stats.mochibunCpuPercent / 100f).coerceAtLeast(0.02f)
            }
            val cpuOverallReadout = if (!stats.isSystemCpuRestricted) {
                "${stats.systemCpuPercent.toInt()}%"
            } else {
                "App: ${String.format(Locale.US, "%.1f", stats.mochibunCpuPercent)}%"
            }
            val cpuPillText = if (!stats.isSystemCpuRestricted) {
                "Launcher: ${String.format(Locale.US, "%.1f", stats.mochibunCpuPercent)}%"
            } else {
                "Threads: ${stats.activeThreadCount}"
            }

            MetricRow(
                glyph = "✦",
                badgeBg = palette.squircleSecondary.copy(alpha = 0.45f),
                title = "CPU",
                overallReadout = cpuOverallReadout,
                mochibunBadgeText = cpuPillText,
                progress = cpuProgress,
                progressColors = listOf(palette.squircleSecondary, palette.primary),
                trackColor = palette.squirclePrimary.copy(alpha = 0.25f),
                deepColor = palette.deepAccent,
                textColor = palette.textColor,
                outlineColor = palette.outline
            )

            Spacer(modifier = Modifier.height(11.dp))

            // 2. RAM (Memory) Metric Row
            val ramProgress = (stats.ramPercent / 100f).coerceIn(0f, 1f)
            val ramOverallReadout = if (stats.totalRamMb > 0) {
                "${stats.usedRamMb}/${stats.totalRamMb} MB (${stats.ramPercent.toInt()}%)"
            } else {
                "Sampling..."
            }
            val ramPillText = "Launcher: ~${stats.mochibunPssMb} MB"

            MetricRow(
                glyph = "♡",
                badgeBg = palette.squirclePrimary.copy(alpha = 0.45f),
                title = "RAM",
                overallReadout = ramOverallReadout,
                mochibunBadgeText = ramPillText,
                progress = ramProgress,
                progressColors = listOf(palette.primary, palette.squirclePrimary),
                trackColor = palette.squirclePrimary.copy(alpha = 0.25f),
                deepColor = palette.deepAccent,
                textColor = palette.textColor,
                outlineColor = palette.outline
            )
        }
    }
}

@Composable
private fun MetricRow(
    glyph: String,
    badgeBg: Color,
    title: String,
    overallReadout: String,
    mochibunBadgeText: String,
    progress: Float,
    progressColors: List<Color>,
    trackColor: Color,
    deepColor: Color,
    textColor: Color,
    outlineColor: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0.04f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "MetricProgress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle pastel badge (RoundedCornerShape(12.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg)
                    .border(1.dp, outlineColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = glyph,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = deepColor
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            // Title and readout
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = overallReadout,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Dedicated pill badge showing Mochibun's own consumption
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeBg.copy(alpha = 0.65f))
                    .border(1.dp, outlineColor.copy(alpha = 0.65f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mochibunBadgeText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = deepColor
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        // Thick, pill-shaped progress bar (height 9.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                .background(trackColor)
                .border(0.75.dp, outlineColor.copy(alpha = 0.35f), RoundedCornerShape(50))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(progressColors))
            )
        }
    }
}
