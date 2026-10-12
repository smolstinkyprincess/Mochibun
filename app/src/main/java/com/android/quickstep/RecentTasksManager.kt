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

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.lang.reflect.Method

// holds information about a recent app task to display in recents switcher :3
data class RecentTaskInfo(
    val taskId: Int,
    val title: String,
    val packageName: String,
    val icon: Drawable?,
    val thumbnail: ImageBitmap?,
    val baseIntent: Intent?
)

// manages querying, launching, and dismissing recent tasks via system services <3
class RecentTasksManager(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    // fetches up to maxNum recent tasks using ActivityManager <3
    fun getRecentTasks(maxNum: Int = 15): List<RecentTaskInfo> {
        val result = mutableListOf<RecentTaskInfo>()
        try {
            // query active recent tasks from system ActivityManager :3
            val recentTasks = activityManager.getRecentTasks(maxNum, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            val currentPackage = context.packageName

            for (rawInfo in recentTasks) {
                // get persistent task ID depending on SDK version
                val taskId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    rawInfo.taskId
                } else {
                    rawInfo.persistentId
                }
                if (taskId < 0) continue

                val baseIntent = rawInfo.baseIntent ?: continue
                val component = baseIntent.component ?: rawInfo.origActivity
                val pkgName = component?.packageName ?: baseIntent.`package` ?: continue

                // exclude our launcher home activity from recents overview <3
                if (pkgName == currentPackage && baseIntent.hasCategory(Intent.CATEGORY_HOME)) {
                    continue
                }

                var title = rawInfo.description?.toString() ?: ""
                var icon: Drawable? = null

                // load label and icon for app package :3
                try {
                    val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                    if (title.isEmpty()) {
                        title = packageManager.getApplicationLabel(appInfo).toString()
                    }
                    icon = packageManager.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    if (title.isEmpty()) title = pkgName
                }

                // fetch task snapshot thumbnail using reflection if supported
                val thumbnailBitmap = getTaskThumbnail(taskId)

                result.add(
                    RecentTaskInfo(
                        taskId = taskId,
                        title = title,
                        packageName = pkgName,
                        icon = icon,
                        thumbnail = thumbnailBitmap,
                        baseIntent = baseIntent
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recent tasks", e)
        }
        return result
    }

    // attempts to extract hardware buffer task snapshot bitmap via reflection <3
    private fun getTaskThumbnail(taskId: Int): ImageBitmap? {
        try {
            // invoke getTaskSnapshot on ActivityManager
            val getTaskSnapshotMethod: Method? = try {
                ActivityManager::class.java.getMethod("getTaskSnapshot", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            } catch (e: Exception) {
                null
            }

            if (getTaskSnapshotMethod != null) {
                val snapshot = getTaskSnapshotMethod.invoke(activityManager, taskId, false)
                if (snapshot != null) {
                    val getSnapshotMethod = snapshot.javaClass.getMethod("getSnapshot")
                    val hardwareBuffer = getSnapshotMethod.invoke(snapshot)
                    if (hardwareBuffer != null) {
                        val colorSpaceMethod = snapshot.javaClass.getMethod("getColorSpace")
                        val colorSpace = colorSpaceMethod.invoke(snapshot) as? android.graphics.ColorSpace
                        val wrapHardwareBufferMethod = Bitmap::class.java.getMethod(
                            "wrapHardwareBuffer",
                            android.hardware.HardwareBuffer::class.java,
                            android.graphics.ColorSpace::class.java
                        )
                        val hwBitmap = wrapHardwareBufferMethod.invoke(null, hardwareBuffer, colorSpace) as? Bitmap
                        if (hwBitmap != null) {
                            val copyBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            return copyBitmap?.asImageBitmap()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // fail gracefully if snapshot reflection is unavailable
        }
        return null
    }

    // switches back to selected recent task or opens app intent :3
    fun launchTask(task: RecentTaskInfo): Boolean {
        try {
            // attempt reflection method startActivityFromRecents <3
            val startActivityFromRecentsMethod: Method? = try {
                ActivityManager::class.java.getMethod(
                    "startActivityFromRecents",
                    Int::class.javaPrimitiveType,
                    android.os.Bundle::class.java
                )
            } catch (e: Exception) {
                null
            }

            if (startActivityFromRecentsMethod != null) {
                val opts = ActivityOptions.makeCustomAnimation(context, 0, 0)
                startActivityFromRecentsMethod.invoke(activityManager, task.taskId, opts.toBundle())
                return true
            }

            // fallback: launch app via standard launch intent
            val intent = task.baseIntent ?: packageManager.getLaunchIntentForPackage(task.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch task ${task.taskId}", e)
        }
        return false
    }

    // removes task from recents stack via system ActivityManager :3
    fun removeTask(taskId: Int) {
        try {
            val removeTaskMethod: Method? = try {
                ActivityManager::class.java.getMethod("removeTask", Int::class.javaPrimitiveType)
            } catch (e: Exception) {
                null
            }

            if (removeTaskMethod != null) {
                removeTaskMethod.invoke(activityManager, taskId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove task $taskId", e)
        }
    }

    companion object {
        private const val TAG = "RecentTasksManager"
    }
}
