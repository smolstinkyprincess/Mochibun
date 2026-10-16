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
import android.graphics.drawable.Drawable
import android.os.Bundle
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tnyemii.mochibun.KawaiiDialogPillButton
import com.tnyemii.mochibun.LocalLauncherTheme
import com.tnyemii.mochibun.isBackDismissKey
import com.tnyemii.mochibun.isCenterActivateKey
import com.tnyemii.mochibun.loadSavedTheme
import com.tnyemii.mochibun.requestFocusSafely
import com.tnyemii.mochibun.selectedContainerColor
import com.tnyemii.mochibun.ui.theme.M120LauncherTheme
import com.tnyemii.mochibun.ui.theme.destructiveBgColor
import com.tnyemii.mochibun.ui.theme.destructiveColor
import com.tnyemii.mochibun.ui.theme.softSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// overlay activity displaying recent apps in a horizontal scrollable card list :3
class RecentsActivity : ComponentActivity() {

    private lateinit var tasksManager: RecentTasksManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tasksManager = RecentTasksManager(applicationContext)

        setContent {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
            }
            val savedTheme = remember { loadSavedTheme(prefs) }

            M120LauncherTheme {
                CompositionLocalProvider(LocalLauncherTheme provides savedTheme) {
                    RecentsScreen(
                        tasksManager = tasksManager,
                        onDismissActivity = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
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

// composable UI container for recent app cards, clear-all action, and backdrop tap to close <3
@Composable
fun RecentsScreen(
    tasksManager: RecentTasksManager,
    onDismissActivity: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    var tasks by remember { mutableStateOf<List<RecentTaskInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Focus zones:
    // 0 = Task Card dismiss "✕" button on the currently selected card
    // 1 = Task Card body (launch task on OK, LEFT/RIGHT moves between cards)
    // 2 = Bottom action buttons (bottomCol: 0 = Clear All, 1 = Close)
    var focusZone by remember { mutableIntStateOf(1) }
    var selectedTaskIndex by remember { mutableIntStateOf(0) }
    var bottomCol by remember { mutableIntStateOf(1) }
    var sawCenterDown by remember { mutableStateOf(false) }

    // asynchronously load recent tasks list on IO thread :3
    LaunchedEffect(Unit) {
        val fetchedTasks = withContext(Dispatchers.IO) {
            tasksManager.getRecentTasks()
        }
        tasks = fetchedTasks
        isLoading = false
        if (fetchedTasks.isEmpty()) {
            focusZone = 2
            bottomCol = 1
        } else {
            focusZone = 1
            selectedTaskIndex = 0
        }
    }

    LaunchedEffect(tasks.size) {
        selectedTaskIndex = selectedTaskIndex.coerceIn(0, (tasks.size - 1).coerceAtLeast(0))
        if (!isLoading && tasks.isEmpty()) {
            focusZone = 2
            bottomCol = 1
        }
    }

    LaunchedEffect(selectedTaskIndex, tasks.size) {
        if (tasks.isNotEmpty() && selectedTaskIndex in tasks.indices) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any {
                it.index == selectedTaskIndex
            }
            if (!isVisible) {
                listState.animateScrollToItem(selectedTaskIndex)
            }
        }
    }

    requestFocusSafely(focusRequester, isLoading)

    val dismissTaskAt: (RecentTaskInfo) -> Unit = { task ->
        val dismissedId = task.taskId
        tasks = tasks.filter { it.taskId != dismissedId }
        scope.launch(Dispatchers.IO) {
            tasksManager.removeTask(dismissedId)
        }
        if (tasks.isEmpty()) {
            onDismissActivity()
        }
    }

    val clearAllTasks: () -> Unit = {
        val idsToRemove = tasks.map { it.taskId }
        tasks = emptyList()
        scope.launch(Dispatchers.IO) {
            idsToRemove.forEach { tasksManager.removeTask(it) }
        }
        onDismissActivity()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x991A0D22))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode) || native.keyCode == android.view.KeyEvent.KEYCODE_APP_SWITCH) {
                    if (event.type == KeyEventType.KeyUp) {
                        onDismissActivity()
                    }
                    return@onPreviewKeyEvent true
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusZone) {
                            0 -> {
                                tasks.getOrNull(selectedTaskIndex)?.let(dismissTaskAt)
                                focusZone = 1
                            }
                            1 -> {
                                tasks.getOrNull(selectedTaskIndex)?.let { task ->
                                    val launched = tasksManager.launchTask(task)
                                    if (launched) {
                                        onDismissActivity()
                                    }
                                }
                            }
                            2 -> {
                                if (bottomCol == 0 && tasks.isNotEmpty()) {
                                    clearAllTasks()
                                } else {
                                    onDismissActivity()
                                }
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DEL,
                    android.view.KeyEvent.KEYCODE_FORWARD_DEL -> {
                        if ((focusZone == 0 || focusZone == 1) && tasks.isNotEmpty()) {
                            tasks.getOrNull(selectedTaskIndex)?.let(dismissTaskAt)
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusZone) {
                            0, 1 -> {
                                if (selectedTaskIndex > 0) {
                                    selectedTaskIndex--
                                }
                            }
                            2 -> {
                                if (tasks.isNotEmpty()) bottomCol = 0
                            }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (focusZone) {
                            0, 1 -> {
                                if (selectedTaskIndex < tasks.lastIndex) {
                                    selectedTaskIndex++
                                }
                            }
                            2 -> {
                                bottomCol = 1
                            }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusZone) {
                            1 -> {
                                if (tasks.isNotEmpty()) focusZone = 0
                            }
                            2 -> {
                                if (tasks.isNotEmpty()) focusZone = 1
                            }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusZone) {
                            0 -> focusZone = 1
                            1 -> {
                                focusZone = 2
                                bottomCol = if (tasks.isNotEmpty()) 0 else 1
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            .clickable { onDismissActivity() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // header title banner
            val headerShape = RoundedCornerShape(18.dp)
            Text(
                text = "✨ Recent Apps ✨",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = theme.fgTextColor,
                modifier = Modifier
                    .background(theme.greetingBgColor, headerShape)
                    .border(2.dp, theme.greetingOutlineColor, headerShape)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(
                    color = theme.appActionColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else if (tasks.isEmpty()) {
                val emptyShape = RoundedCornerShape(theme.cardRoundnessDp.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .background(theme.cardBgColor, emptyShape)
                        .border(2.5.dp, theme.greetingOutlineColor, emptyShape)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent apps 🌸",
                        fontSize = 16.sp,
                        color = theme.fgTextColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // horizontal list of recent task cards :3
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(
                        items = tasks,
                        key = { _, it -> it.taskId }
                    ) { index, task ->
                        val isCardSelected = index == selectedTaskIndex && (focusZone == 0 || focusZone == 1)
                        val isDismissFocused = index == selectedTaskIndex && focusZone == 0

                        TaskCard(
                            task = task,
                            isCardFocused = isCardSelected,
                            isDismissFocused = isDismissFocused,
                            onLaunch = {
                                selectedTaskIndex = index
                                focusZone = 1
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

            // bottom action buttons (clear all / close) <3
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tasks.isNotEmpty()) {
                    KawaiiDialogPillButton(
                        text = "Clear All 🧹",
                        isFocused = focusZone == 2 && bottomCol == 0,
                        isDestructive = true,
                        onClick = clearAllTasks
                    )
                }

                KawaiiDialogPillButton(
                    text = "Close 🌸",
                    isFocused = focusZone == 2 && bottomCol == 1,
                    isPrimary = true,
                    onClick = onDismissActivity
                )
            }
        }
    }
}

// individual recent app card supporting vertical drag-to-dismiss gesture and D-pad focus <3
@Composable
fun TaskCard(
    task: RecentTaskInfo,
    isCardFocused: Boolean = false,
    isDismissFocused: Boolean = false,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    var offsetY by remember { mutableFloatStateOf(0f) }

    val scale by animateFloatAsState(
        targetValue = if (isCardFocused) 1.03f else 1f,
        animationSpec = tween(130),
        label = "recentsCardScale"
    )

    // vertical drag gesture handler to dismiss task when swiped up :3
    val draggableState = rememberDraggableState { delta ->
        offsetY += delta
        if (offsetY < -300f) {
            onDismiss()
        }
    }

    val cardShape = RoundedCornerShape(theme.cardRoundnessDp.dp)

    Box(
        modifier = Modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = {
                    if (offsetY > -200f) {
                        offsetY = 0f
                    }
                }
            )
            .width(220.dp)
            .fillMaxHeight(0.82f)
            .shadow(8.dp, cardShape)
            .clip(cardShape)
            .background(
                if (isCardFocused) theme.selectedContainerColor else theme.cardBgColor
            )
            .border(
                width = if (isCardFocused) 3.dp else 2.dp,
                color = if (isCardFocused) theme.appActionColor else theme.greetingOutlineColor,
                shape = cardShape
            )
            .clickable { onLaunch() }
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // task app header (icon, title, close button) :3
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (task.iconBitmap != null) {
                        Image(
                            bitmap = task.iconBitmap,
                            contentDescription = task.title,
                            modifier = Modifier.size(24.dp)
                        )
                    } else if (task.icon != null) {
                        DrawableImage(
                            drawable = task.icon,
                            contentDescription = task.title,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.fgTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // close button (focusable via DPAD_UP on the selected card)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDismissFocused) destructiveColor else destructiveBgColor
                        )
                        .border(
                            width = if (isDismissFocused) 2.dp else 1.dp,
                            color = destructiveColor,
                            shape = CircleShape
                        )
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✕",
                        fontSize = 11.sp,
                        color = if (isDismissFocused) Color.White else destructiveColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // task thumbnail preview or fallback app icon <3
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(softSurfaceColor),
                contentAlignment = Alignment.Center
            ) {
                if (task.thumbnail != null) {
                    Image(
                        bitmap = task.thumbnail,
                        contentDescription = task.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (task.iconBitmap != null) {
                            Image(
                                bitmap = task.iconBitmap,
                                contentDescription = task.title,
                                modifier = Modifier.size(56.dp)
                            )
                        } else if (task.icon != null) {
                            DrawableImage(
                                drawable = task.icon,
                                contentDescription = task.title,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = task.title,
                            fontSize = 12.sp,
                            color = theme.fgTextColor,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

// renders Android drawable object into Compose Image bitmap :3
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
            modifier = modifier
        )
    }
}
