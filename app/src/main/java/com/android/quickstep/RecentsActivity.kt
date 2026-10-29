/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.components.*
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.theme.loadSavedTheme
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import com.crinklecodone.mochibun.ui.theme.MochibunTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class RecentsFocusZone {
    TASK_CAROUSEL,
    CLEAR_ALL
}

private data class TaskCardTint(
    val badgeBg: Color,
    val badgeBorder: Color,
    val placeholderBg: Color
)

private val TASK_TINTS = listOf(
    // 0: Soft Lavender (Discord, etc.)
    TaskCardTint(
        badgeBg = Color(0xFFE2DBF6),
        badgeBorder = Color(0xFFC7B6EE),
        placeholderBg = Color(0xFFEDE8F9)
    ),
    // 1: Soft Coral / Rose
    TaskCardTint(
        badgeBg = Color(0xFFFDD4DC),
        badgeBorder = Color(0xFFF6ACB9),
        placeholderBg = Color(0xFFFDE8EC)
    ),
    // 2: Soft Seafoam / Mint (CashApp, etc.)
    TaskCardTint(
        badgeBg = Color(0xFFCEF1E4),
        badgeBorder = Color(0xFFA5E2CD),
        placeholderBg = Color(0xFFE2F7EE)
    ),
    // 3: Soft Periwinkle (Via Browser, etc.)
    TaskCardTint(
        badgeBg = Color(0xFFD4DCF9),
        badgeBorder = Color(0xFFB6C4EE),
        placeholderBg = Color(0xFFE6EBFC)
    )
)

/**
 * Overlay activity displaying recent apps in a kawaii horizontal card carousel.
 * Faithfully matches Mochibun's reference styling and Chinoe AT-M120 D-pad constraints.
 */
class RecentsActivity : ComponentActivity() {

    private lateinit var tasksManager: RecentTasksManager
    private val refreshTrigger = androidx.compose.runtime.mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        tasksManager = RecentTasksManager(applicationContext)

        setContent {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
            }
            val savedTheme = remember { loadSavedTheme(prefs) }

            MochibunTheme {
                CompositionLocalProvider(LocalLauncherTheme provides savedTheme) {
                    RecentsScreen(
                        tasksManager = tasksManager,
                        refreshTrigger = refreshTrigger.intValue,
                        onDismissActivity = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshTrigger.intValue++
    }

    companion object {
        private const val DEBOUNCE_MS = 600L

        @Volatile
        private var lastLaunchTime: Long = 0L

        @Synchronized
        fun shouldThrottleLaunch(now: Long = android.os.SystemClock.uptimeMillis()): Boolean {
            if (now - lastLaunchTime < DEBOUNCE_MS) {
                return true
            }
            lastLaunchTime = now
            return false
        }
    }
}

/**
 * Composable UI container for recent task carousel, clear-all button, and kawaii framing.
 */
@Composable
fun RecentsScreen(
    tasksManager: RecentTasksManager,
    refreshTrigger: Int = 0,
    onDismissActivity: () -> Unit
) {
    var tasks by remember { mutableStateOf<List<RecentTaskInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    var focusZone by remember { mutableStateOf(RecentsFocusZone.TASK_CAROUSEL) }
    var selectedTaskIndex by remember { mutableIntStateOf(0) }
    var isDismissFocused by remember { mutableStateOf(false) }
    var sawCenterDown by remember { mutableStateOf(false) }

    // Load recent tasks asynchronously off the main thread
    LaunchedEffect(refreshTrigger) {
        val fetchedTasks = withContext(Dispatchers.IO) {
            tasksManager.getRecentTasks()
        }
        tasks = fetchedTasks
        isLoading = false
        focusZone = RecentsFocusZone.TASK_CAROUSEL
        selectedTaskIndex = 0
        isDismissFocused = false
    }

    LaunchedEffect(tasks.size) {
        selectedTaskIndex = selectedTaskIndex.coerceIn(0, (tasks.size - 1).coerceAtLeast(0))
        if (!isLoading && tasks.isEmpty()) {
            focusZone = RecentsFocusZone.TASK_CAROUSEL
        }
    }

    // Scroll carousel smoothly to center focused task card
    LaunchedEffect(selectedTaskIndex, tasks.size) {
        if (tasks.isNotEmpty() && selectedTaskIndex in tasks.indices) {
            listState.animateScrollToItem(selectedTaskIndex)
        }
    }

    requestFocusSafely(focusRequester, isLoading)

    val dismissTaskAt: (RecentTaskInfo) -> Unit = { task ->
        val dismissedId = task.taskId
        val pkg = task.packageName
        tasks = tasks.filter { it.taskId != dismissedId }
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            tasksManager.removeTask(dismissedId, pkg)
        }
        if (tasks.isEmpty()) {
            onDismissActivity()
        }
    }

    val clearAllTasks: () -> Unit = {
        val currentTasks = tasks
        tasks = emptyList()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            tasksManager.clearAllTasks(currentTasks)
        }
        onDismissActivity()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent) // Recents background color
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode) ||
                    native.keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                    native.keyCode == KeyEvent.KEYCODE_F4
                ) {
                    if (event.type == KeyEventType.KeyUp) {
                        onDismissActivity()
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type == KeyEventType.KeyDown &&
                    (native.keyCode == KeyEvent.KEYCODE_DEL || native.keyCode == KeyEvent.KEYCODE_FORWARD_DEL)
                ) {
                    if (focusZone == RecentsFocusZone.TASK_CAROUSEL && tasks.isNotEmpty()) {
                        tasks.getOrNull(selectedTaskIndex)?.let(dismissTaskAt)
                    }
                    return@onPreviewKeyEvent true
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusZone) {
                            RecentsFocusZone.TASK_CAROUSEL -> {
                                if (isDismissFocused) {
                                    tasks.getOrNull(selectedTaskIndex)?.let(dismissTaskAt)
                                    isDismissFocused = false
                                } else {
                                    tasks.getOrNull(selectedTaskIndex)?.let { task ->
                                        val launched = tasksManager.launchTask(task)
                                        if (launched) {
                                            onDismissActivity()
                                        }
                                    }
                                }
                            }
                            RecentsFocusZone.CLEAR_ALL -> {
                                clearAllTasks()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focusZone == RecentsFocusZone.TASK_CAROUSEL) {
                            isDismissFocused = false
                            if (selectedTaskIndex > 0) {
                                selectedTaskIndex--
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focusZone == RecentsFocusZone.TASK_CAROUSEL) {
                            isDismissFocused = false
                            if (selectedTaskIndex < tasks.lastIndex) {
                                selectedTaskIndex++
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusZone) {
                            RecentsFocusZone.TASK_CAROUSEL -> {
                                if (!isDismissFocused) {
                                    isDismissFocused = true
                                } else {
                                    // Second UP dismisses task with upward slide
                                    tasks.getOrNull(selectedTaskIndex)?.let(dismissTaskAt)
                                    isDismissFocused = false
                                }
                            }
                            RecentsFocusZone.CLEAR_ALL -> {
                                if (tasks.isNotEmpty()) {
                                    focusZone = RecentsFocusZone.TASK_CAROUSEL
                                    isDismissFocused = false
                                }
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusZone) {
                            RecentsFocusZone.TASK_CAROUSEL -> {
                                if (isDismissFocused) {
                                    isDismissFocused = false
                                } else if (tasks.isNotEmpty()) {
                                    focusZone = RecentsFocusZone.CLEAR_ALL
                                }
                            }
                            RecentsFocusZone.CLEAR_ALL -> {
                                // Already at bottom button
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        // 1. Top Scalloped Lavender Clouds
        TopLavenderClouds(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // 2. Main Layout (Header, Carousel, Clear All Button)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: — ♡ Recents ♡ —
            MochibunSectionHeader(
                title = "Recents",
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = ActiveTheme.accentDeep
            )

            // Center Content: Loading, Empty state, or Horizontal Task Carousel
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ActiveTheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(ActiveTheme.surface)
                            .border(1.5.dp, ActiveTheme.cardBorder, RoundedCornerShape(22.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "— ♡ No Recent Apps ♡ —\n\nAll tasks closed! Press Back or Home to return ♡",
                            fontSize = 14.sp,
                            color = ActiveTheme.textPrimary,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Sizing and Carousel padding to center card at 480px width
                val configuration = LocalConfiguration.current
                val screenWidthDp = configuration.screenWidthDp.dp
                val cardWidth = 210.dp
                val horizontalContentPadding = ((screenWidthDp - cardWidth) / 2).coerceAtLeast(16.dp)

                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = horizontalContentPadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(
                        items = tasks,
                        key = { _, it -> it.taskId }
                    ) { index, task ->
                        val isCardSelected = index == selectedTaskIndex && focusZone == RecentsFocusZone.TASK_CAROUSEL
                        val isDismissActive = isCardSelected && isDismissFocused

                        TaskCard(
                            task = task,
                            isCardFocused = isCardSelected,
                            isDismissFocused = isDismissActive,
                            onLaunch = {
                                selectedTaskIndex = index
                                focusZone = RecentsFocusZone.TASK_CAROUSEL
                                isDismissFocused = false
                                val launched = tasksManager.launchTask(task)
                                if (launched) {
                                    onDismissActivity()
                                }
                            },
                            onDismiss = {
                                selectedTaskIndex = index
                                dismissTaskAt(task)
                            }
                        )
                    }
                }
            }

            // "♡ Clear All ♡" Pill Button
            if (tasks.isNotEmpty()) {
                val isClearAllFocused = focusZone == RecentsFocusZone.CLEAR_ALL
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ActiveTheme.accentDeep)
                        .glowingDoubleBorder(isSelected = isClearAllFocused, shape = RoundedCornerShape(50), cornerRadius = 50.dp)
                        .clickable { clearAllTasks() }
                        .padding(horizontal = 24.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♡ Clear All ♡",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        // 3. Bottom Layered Lavender Clouds
        BottomLavenderClouds(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
    }
}

/**
 * Individual recent app task card matching the reference image.
 * Features:
 * - Overlapping squircle app icon badge centered on the top edge
 * - Bold deep violet app title directly below the icon badge
 * - Top-right '✕' dismiss button in soft lilac
 * - High-contrast frosted lavender-cream card container with 22dp corner radius
 * - Glowing double-line rose neon focus ring when selected via D-pad
 * - Receding / scaled-down side cards (scale = 0.88f)
 */
@Composable
fun TaskCard(
    task: RecentTaskInfo,
    isCardFocused: Boolean = false,
    isDismissFocused: Boolean = false,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isCardFocused) 1.0f else 0.88f,
        animationSpec = tween(140),
        label = "recentsCardScale"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isDismissing) 0f else if (isCardFocused) 1.0f else 0.82f,
        animationSpec = tween(180),
        label = "recentsCardAlpha"
    )

    val dismissOffsetY by animateFloatAsState(
        targetValue = if (isDismissing) -500f else offsetY,
        animationSpec = tween(220),
        finishedListener = {
            if (isDismissing) {
                onDismiss()
            }
        },
        label = "recentsDismissOffsetY"
    )

    // Vertical drag gesture handler to dismiss task when swiped up
    val draggableState = rememberDraggableState { delta ->
        offsetY += delta
        if (offsetY < -240f && !isDismissing) {
            isDismissing = true
        }
    }

    val cardShape = RoundedCornerShape(22.dp)
    val tint = remember(task.tintIndex) {
        TASK_TINTS[task.tintIndex % TASK_TINTS.size]
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(0, dismissOffsetY.roundToInt()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = cardAlpha
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = {
                    if (offsetY > -180f) {
                        offsetY = 0f
                    }
                }
            )
            .width(210.dp)
            .height(350.dp)
            .padding(top = 18.dp) // Clearance for overlapping top icon badge
    ) {
        // Main Card Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
                .background(ActiveTheme.surface) // Frosted theme surface
                .glowingDoubleBorder(isSelected = isCardFocused && !isDismissFocused, shape = cardShape, cornerRadius = 22.dp)
                .border(
                    width = if (isCardFocused && !isDismissFocused) 2.dp else 1.5.dp,
                    color = if (isCardFocused && !isDismissFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                    shape = cardShape
                )
                .clickable { onLaunch() }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top spacing to account for overlapping badge and App Title
                Spacer(modifier = Modifier.height(18.dp))

                // App Title centered directly underneath the icon badge
                Text(
                    text = task.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActiveTheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Task Thumbnail Preview Snapshot or Tinted Pastel Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tint.placeholderBg)
                        .border(1.dp, tint.badgeBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.thumbnail != null) {
                        Image(
                            bitmap = task.thumbnail,
                            contentDescription = task.title,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Fallback coordinated pastel preview canvas
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (task.iconBitmap != null) {
                                Image(
                                    bitmap = task.iconBitmap,
                                    contentDescription = task.title,
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier.size(48.dp)
                                )
                            } else if (task.icon != null) {
                                DrawableImage(
                                    drawable = task.icon,
                                    contentDescription = task.title,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = task.title,
                                fontSize = 12.sp,
                                color = ActiveTheme.textPrimary,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Top-right Close Button ('✕') in soft theme tint
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isDismissFocused) ActiveTheme.surfaceTint else ActiveTheme.surface)
                    .border(
                        width = if (isDismissFocused) 2.dp else 1.dp,
                        color = if (isDismissFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                        shape = RoundedCornerShape(9.dp)
                    )
                    .glowingDoubleBorder(isSelected = isDismissFocused, shape = RoundedCornerShape(9.dp), cornerRadius = 9.dp)
                    .clickable {
                        isDismissing = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    fontSize = 11.5.sp,
                    color = if (isDismissFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Overlapping Squircle App Icon Badge Centered on Top Border
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.badgeBg)
                .border(1.5.dp, tint.badgeBorder, RoundedCornerShape(14.dp))
                .clickable { onLaunch() },
            contentAlignment = Alignment.Center
        ) {
            if (task.iconBitmap != null) {
                Image(
                    bitmap = task.iconBitmap,
                    contentDescription = task.title,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(26.dp)
                )
            } else if (task.icon != null) {
                DrawableImage(
                    drawable = task.icon,
                    contentDescription = task.title,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * Renders Android drawable object into Compose Image bitmap.
 */
@Composable
fun DrawableImage(
    drawable: Drawable,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(drawable) {
        try {
            drawable.toBitmap().asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            filterQuality = FilterQuality.None,
            modifier = modifier
        )
    }
}
