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

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.tnyemii.m120launcher.ui.theme.cardTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// overlay activity displaying recent apps in a horizontal scrollable card list :3
class RecentsActivity : ComponentActivity() {

    private lateinit var tasksManager: RecentTasksManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tasksManager = RecentTasksManager(this)

        setContent {
            RecentsScreen(
                tasksManager = tasksManager,
                onDismissActivity = { finish() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
    }
}

// composable UI container for recent app cards, clear-all action, and backdrop tap to close <3
@Composable
fun RecentsScreen(
    tasksManager: RecentTasksManager,
    onDismissActivity: () -> Unit
) {
    var tasks by remember { mutableStateOf<List<RecentTaskInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // asynchronously load recent tasks list on IO thread :3
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val fetchedTasks = tasksManager.getRecentTasks()
            withContext(Dispatchers.Main) {
                tasks = fetchedTasks
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable { onDismissActivity() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // header title banner
            Text(
                text = "✨ Recent Apps ✨",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = cardTextColor,
                modifier = Modifier
                    .background(Color(0xFFFFF0F5), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(
                    color = Color(0xFFFFB6C1),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .background(Color(0xFFFFF0F5), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent apps 🌸",
                        fontSize = 16.sp,
                        color = cardTextColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // horizontal list of recent task cards :3
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(
                        items = tasks,
                        key = { it.taskId }
                    ) { task ->
                        TaskCard(
                            task = task,
                            onLaunch = {
                                tasksManager.launchTask(task)
                                onDismissActivity()
                            },
                            onDismiss = {
                                tasksManager.removeTask(task.taskId)
                                tasks = tasks.filter { it.taskId != task.taskId }
                                if (tasks.isEmpty()) {
                                    onDismissActivity()
                                }
                            }
                        )
                    }
                }
            }

            // bottom action buttons (clear all / close) <3
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tasks.isNotEmpty()) {
                    Button(
                        onClick = {
                            tasks.forEach { tasksManager.removeTask(it.taskId) }
                            tasks = emptyList()
                            onDismissActivity()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB6C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Clear All 🧹", color = Color(0xFF4A0033), fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onDismissActivity,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE6E6FA)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Close 🌸", color = Color(0xFF4B0082), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// individual recent app card supporting vertical drag-to-dismiss gesture <3
@Composable
fun TaskCard(
    task: RecentTaskInfo,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }

    // vertical drag gesture handler to dismiss task when swiped up :3
    val draggableState = rememberDraggableState { delta ->
        offsetY += delta
        if (offsetY < -300f) {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(0, offsetY.roundToInt()) }
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
            .fillMaxHeight(0.8f)
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFFFF0F5))
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
                    task.icon?.let { drawable ->
                        DrawableImage(
                            drawable = drawable,
                            contentDescription = task.title,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A0033),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // close button
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFC0CB))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // task thumbnail preview or fallback app icon <3
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White),
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
                        task.icon?.let { drawable ->
                            DrawableImage(
                                drawable = drawable,
                                contentDescription = task.title,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = task.title,
                            fontSize = 12.sp,
                            color = Color.Gray,
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
