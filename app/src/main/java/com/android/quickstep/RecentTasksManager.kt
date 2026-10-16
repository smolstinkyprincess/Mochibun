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

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.lang.reflect.Method

// holds information about a recent app task to display in recents switcher :3
@Immutable
data class RecentTaskInfo(
    val taskId: Int,
    val title: String,
    val packageName: String,
    val icon: Drawable?,
    val iconBitmap: ImageBitmap? = null,
    val thumbnail: ImageBitmap?,
    val baseIntent: Intent?
)

private data class CachedAppTaskMeta(
    val label: String,
    val icon: Drawable,
    val iconBitmap: ImageBitmap
)

// manages querying, launching, and dismissing recent tasks via system services <3
class RecentTasksManager(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = appContext.packageManager

    // fetches up to maxNum recent tasks using ActivityManager <3
    fun getRecentTasks(maxNum: Int = 15): List<RecentTaskInfo> {
        val result = mutableListOf<RecentTaskInfo>()
        try {
            // query active recent tasks from system ActivityManager :3
            val recentTasks = activityManager.getRecentTasks(maxNum, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            val currentPackage = appContext.packageName
            Log.d(TAG, "getRecentTasks queried: received ${recentTasks?.size ?: 0} raw tasks from system (currentPackage=$currentPackage)")

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
                val isHomeCategory = baseIntent.hasCategory(Intent.CATEGORY_HOME)
                val className = component?.className ?: ""

                // exclude our launcher activities (home screen and recents overview) from recents switcher <3
                val isLauncherActivity = (pkgName == currentPackage && (
                    isHomeCategory ||
                    className == RecentsActivity::class.java.name ||
                    className == "com.android.quickstep.RecentsActivity" ||
                    className.endsWith("MainActivity") ||
                    className.endsWith("RecentsActivity")
                ))

                if (isLauncherActivity) {
                    Log.d(TAG, "Filtered out launcher task: id=$taskId, pkg=$pkgName, component=$component, isHome=$isHomeCategory")
                    continue
                }

                var title = rawInfo.description?.toString() ?: ""
                var icon: Drawable? = null
                var iconBitmap: ImageBitmap? = null

                // load cached label and icon bitmap for app package off the UI thread :3
                val cachedMeta = getOrLoadAppMeta(pkgName)
                if (cachedMeta != null) {
                    if (title.isEmpty()) title = cachedMeta.label
                    icon = cachedMeta.icon
                    iconBitmap = cachedMeta.iconBitmap
                } else if (title.isEmpty()) {
                    title = pkgName
                }

                // fetch task snapshot thumbnail using reflection if supported
                val thumbnailBitmap = getTaskThumbnail(taskId)

                Log.d(TAG, "Added task to recents: id=$taskId, title=$title, pkg=$pkgName, component=$component")

                result.add(
                    RecentTaskInfo(
                        taskId = taskId,
                        title = title,
                        packageName = pkgName,
                        icon = icon,
                        iconBitmap = iconBitmap,
                        thumbnail = thumbnailBitmap,
                        baseIntent = baseIntent
                    )
                )
            }
            Log.d(TAG, "getRecentTasks completed: ${result.size} tasks survived filtering")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recent tasks", e)
        }
        return result
    }

    private fun getOrLoadAppMeta(pkgName: String): CachedAppTaskMeta? {
        synchronized(appMetaCache) {
            appMetaCache.get(pkgName)?.let { return it }
        }
        return try {
            val appInfo = packageManager.getApplicationInfo(pkgName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val drawable = packageManager.getApplicationIcon(appInfo)
            val bmp = drawable.toBitmap(84, 84, Bitmap.Config.ARGB_8888)
            bmp.prepareToDraw()
            val meta = CachedAppTaskMeta(
                label = label,
                icon = drawable,
                iconBitmap = bmp.asImageBitmap()
            )
            synchronized(appMetaCache) {
                appMetaCache.put(pkgName, meta)
            }
            meta
        } catch (e: Exception) {
            null
        }
    }

    // attempts to extract hardware buffer task snapshot bitmap via reflection <3
    @SuppressLint("BlockedPrivateApi")
    private fun getTaskThumbnail(taskId: Int): ImageBitmap? {
        return try {
            val service = getAtmService() ?: return null
            val getTaskSnapshotMethod = getTaskSnapshotMethod ?: return null

            // Request low-resolution reduced snapshot (true) first if supported by system
            val snapshot = getTaskSnapshotMethod.invoke(
                service,
                taskId,
                true
            ) ?: getTaskSnapshotMethod.invoke(
                service,
                taskId,
                false
            ) ?: return null

            val getHardwareBufferMethod = getHardwareBufferMethod
                ?: snapshot.javaClass.getMethod("getHardwareBuffer").also { getHardwareBufferMethod = it }

            val hardwareBuffer = getHardwareBufferMethod.invoke(snapshot)
                    as? android.hardware.HardwareBuffer ?: return null

            try {
                val getColorSpaceMethod = getColorSpaceMethod
                    ?: snapshot.javaClass.getMethod("getColorSpace").also { getColorSpaceMethod = it }
                val colorSpace = getColorSpaceMethod.invoke(snapshot)
                        as? android.graphics.ColorSpace
                    ?: return null

                // Convert the hardware buffer into a bitmap
                if (android.os.Build.VERSION.SDK_INT < 29) {
                    return null
                }

                val wrappedBitmap = Bitmap.wrapHardwareBuffer(
                    hardwareBuffer,
                    colorSpace
                ) ?: return null

                try {
                    val rawCopy = wrappedBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
                    // Downscale snapshot to fit the 220dp card width on the 480x800 hdpi screen
                    val finalBitmap = if (rawCopy.width > 300) {
                        val targetWidth = 240
                        val targetHeight = ((rawCopy.height.toFloat() / rawCopy.width.toFloat()) * targetWidth)
                            .toInt()
                            .coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(rawCopy, targetWidth, targetHeight, true)
                        if (scaled !== rawCopy) {
                            rawCopy.recycle()
                        }
                        scaled
                    } else {
                        rawCopy
                    }
                    finalBitmap.prepareToDraw()
                    finalBitmap.asImageBitmap()
                } finally {
                    // Recycle the HardwareBuffer-backed Bitmap immediately to release its native AHardwareBuffer reference
                    wrappedBitmap.recycle()
                }
            } finally {
                // explicitly close the hardware buffer to avoid native graphics buffer memory leaks <3
                hardwareBuffer.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get thumbnail for task $taskId", e)
            null
        }
    }

    // switches back to selected recent task or opens app intent :3
    fun launchTask(task: RecentTaskInfo): Boolean {
        // First try restoring the existing task stack via reflection
        val method = startActivityFromRecentsMethod
        if (method != null) {
            try {
                val opts = ActivityOptions.makeCustomAnimation(appContext, 0, 0)
                method.invoke(activityManager, task.taskId, opts.toBundle())
                return true
            } catch (e: Exception) {
                Log.w(TAG, "startActivityFromRecents reflection failed for task ${task.taskId}, falling back to intent launch", e)
            }
        }

        // Fallback: launch app via standard launch intent or task baseIntent
        return try {
            val intent = task.baseIntent?.cloneFilter()
                ?: packageManager.getLaunchIntentForPackage(task.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                appContext.startActivity(intent)
                true
            } else {
                Log.e(TAG, "No launch intent available for ${task.packageName} (task ${task.taskId})")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch task fallback for ${task.packageName}", e)
            false
        }
    }

    // removes task from recents stack via androids task management service
    @SuppressLint("BlockedPrivateApi")
    fun removeTask(taskId: Int): Boolean {
        return try {
            val service = getAtmService() ?: return false
            val removeTaskMethod = removeTaskMethod
                ?: service.javaClass.methods.firstOrNull {
                    it.name == "removeTask" &&
                            it.parameterTypes.size == 1 &&
                            it.parameterTypes[0] == Int::class.javaPrimitiveType
                }?.also {
                    it.isAccessible = true
                    removeTaskMethod = it
                } ?: run {
                    Log.e(TAG, "No removeTask(int) method found on task service")
                    return false
                }

            val result = removeTaskMethod.invoke(service, taskId)
            Log.i(TAG, "removeTask($taskId) returned: $result")
            result as? Boolean ?: true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove task $taskId", e)
            false
        }
    }

    companion object {
        private const val TAG = "RecentTasksManager"

        private val appMetaCache = LruCache<String, CachedAppTaskMeta>(32)

        @Volatile
        private var atmService: Any? = null
        @Volatile
        private var atmServiceInitialized = false
        @Volatile
        private var getTaskSnapshotMethod: Method? = null
        @Volatile
        private var getHardwareBufferMethod: Method? = null
        @Volatile
        private var getColorSpaceMethod: Method? = null
        @Volatile
        private var removeTaskMethod: Method? = null

        private val startActivityFromRecentsMethod: Method? by lazy {
            try {
                ActivityManager::class.java.getMethod(
                    "startActivityFromRecents",
                    Int::class.javaPrimitiveType,
                    android.os.Bundle::class.java
                )
            } catch (e: Exception) {
                null
            }
        }

        @Synchronized
        private fun getAtmService(): Any? {
            if (atmServiceInitialized) return atmService
            atmServiceInitialized = true
            return try {
                val taskManagerClass = Class.forName("android.app.ActivityTaskManager")
                val getServiceMethod = taskManagerClass.getDeclaredMethod("getService")
                getServiceMethod.isAccessible = true
                val service = getServiceMethod.invoke(null)
                if (service != null) {
                    getTaskSnapshotMethod = service.javaClass.getMethod(
                        "getTaskSnapshot",
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType
                    )
                }
                atmService = service
                service
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ActivityTaskManager service reflection", e)
                null
            }
        }
    }
}
