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
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.crinklecodone.mochibun.core.system.RootShell
import com.topjohnwu.superuser.Shell
import java.io.File
import java.lang.reflect.Method

/**
 * Holds information about a recent app task to display in Mochibun's Recents overview.
 */
@Immutable
data class RecentTaskInfo(
    val taskId: Int,
    val title: String,
    val packageName: String,
    val icon: Drawable?,
    val iconBitmap: ImageBitmap? = null,
    val thumbnail: ImageBitmap?,
    val baseIntent: Intent?,
    val tintIndex: Int = 0
)

private data class CachedAppTaskMeta(
    val label: String,
    val icon: Drawable,
    val iconBitmap: ImageBitmap
)

/**
 * Manages querying, launching, and dismissing recent tasks via system services on Android 12 (API 31).
 * Optimized for MediaTek MT6761 memory constraints (~1.8 GB RAM).
 */
class RecentTasksManager(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = appContext.packageManager

    /**
     * Fetches up to maxNum recent tasks using ActivityManager, with root fallback for Android 12
     * third-party application process isolation.
     */
    fun getRecentTasks(maxNum: Int = 15): List<RecentTaskInfo> {
        val result = mutableListOf<RecentTaskInfo>()
        val currentPackage = appContext.packageName

        // 1. Try standard framework ActivityManager first
        try {
            val recentTasks = activityManager.getRecentTasks(maxNum, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            Log.d(TAG, "getRecentTasks queried: received ${recentTasks?.size ?: 0} raw tasks from system (currentPackage=$currentPackage)")

            if (recentTasks != null) {
                for (rawInfo in recentTasks) {
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

                    // Exclude Mochibun's own launcher and recents activities from the switcher
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

                    val cachedMeta = getOrLoadAppMeta(pkgName)
                    if (cachedMeta != null) {
                        if (title.isEmpty()) title = cachedMeta.label
                        icon = cachedMeta.icon
                        iconBitmap = cachedMeta.iconBitmap
                    } else if (title.isEmpty()) {
                        title = pkgName
                    }

                    // Downscaled snapshot thumbnail to protect 1.8GB RAM budget
                    val thumbnailBitmap = getTaskThumbnail(taskId)

                    // Assign coordinated pastel tint index based on package hash
                    val tintIndex = (Math.abs(pkgName.hashCode())) % 4

                    Log.d(TAG, "Added task to recents: id=$taskId, title=$title, pkg=$pkgName, component=$component")

                    result.add(
                        RecentTaskInfo(
                            taskId = taskId,
                            title = title,
                            packageName = pkgName,
                            icon = icon,
                            iconBitmap = iconBitmap,
                            thumbnail = thumbnailBitmap,
                            baseIntent = baseIntent,
                            tintIndex = tintIndex
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Standard getRecentTasks failed, checking root fallback: ${e.message}")
        }

        // 2. If standard API returned 0 tasks (due to Android 12 third-party caller isolation)
        // and root is present, query recent tasks via dumpsys activity recents!
        if (result.isEmpty() && RootShell.isSuBinaryPresent()) {
            val rootTasks = getRecentTasksFromRoot(maxNum)
            if (rootTasks.isNotEmpty()) {
                Log.d(TAG, "Retrieved ${rootTasks.size} tasks via root dumpsys activity recents")
                return rootTasks
            }
        }

        Log.d(TAG, "getRecentTasks completed: ${result.size} tasks survived filtering")
        return result
    }

    /**
     * Fallback parser for Android 12 when standard getRecentTasks() is restricted to caller tasks.
     * Parses dumpsys activity recents using libsu's persistent daemon.
     */
    private fun getRecentTasksFromRoot(maxNum: Int): List<RecentTaskInfo> {
        val result = mutableListOf<RecentTaskInfo>()
        try {
            val shellResult = Shell.cmd("dumpsys activity recents").exec()
            if (!shellResult.isSuccess || shellResult.out.isEmpty()) {
                return emptyList()
            }

            val lines = shellResult.out
            var currentTaskId = -1
            var currentType: String
            var currentPkg = ""
            var currentCmp = ""
            var isCurrentHome = false
            val currentPackage = appContext.packageName

            fun commitCurrentTask() {
                if (currentTaskId > 0 && currentPkg.isNotEmpty() && !isCurrentHome) {
                    val isLauncher = currentPkg == currentPackage ||
                            currentCmp.contains("RecentsActivity") ||
                            currentCmp.contains("MainActivity")
                    if (!isLauncher && result.none { it.taskId == currentTaskId }) {
                        val cachedMeta = getOrLoadAppMeta(currentPkg)
                        val title = cachedMeta?.label ?: currentPkg
                        val icon = cachedMeta?.icon
                        val iconBitmap = cachedMeta?.iconBitmap
                        val thumbnailBitmap = getTaskThumbnail(currentTaskId)
                        val tintIndex = (Math.abs(currentPkg.hashCode())) % 4

                        val baseIntent = if (currentCmp.isNotEmpty() && currentCmp.contains("/")) {
                            val parts = currentCmp.split("/")
                            val cls = if (parts[1].startsWith(".")) parts[0] + parts[1] else parts[1]
                            Intent(Intent.ACTION_MAIN).apply {
                                setClassName(parts[0], cls)
                                addCategory(Intent.CATEGORY_LAUNCHER)
                            }
                        } else {
                            packageManager.getLaunchIntentForPackage(currentPkg)
                        }

                        result.add(
                            RecentTaskInfo(
                                taskId = currentTaskId,
                                title = title,
                                packageName = currentPkg,
                                icon = icon,
                                iconBitmap = iconBitmap,
                                thumbnail = thumbnailBitmap,
                                baseIntent = baseIntent,
                                tintIndex = tintIndex
                            )
                        )
                    }
                }
                currentTaskId = -1
                currentType = ""
                currentPkg = ""
                currentCmp = ""
                isCurrentHome = false
            }

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("* Recent #")) {
                    commitCurrentTask()
                    if (result.size >= maxNum) break

                    // e.g. * Recent #1: Task{b3b3168 #3466 type=standard A=10151:com.sevenshifts.android ...}
                    if (trimmed.contains("Task{")) {
                        val afterTask = trimmed.substringAfter("Task{")
                        if (afterTask.contains("#")) {
                            val idStr = afterTask.substringAfter("#").substringBefore(" ").substringBefore("}")
                            currentTaskId = idStr.toIntOrNull() ?: -1
                        }
                    }
                    val typeMatch = Regex("type=(\\w+)").find(trimmed)
                    if (typeMatch != null) {
                        currentType = typeMatch.groupValues[1]
                        if (currentType == "home") {
                            isCurrentHome = true
                        }
                    }
                } else {
                    if (trimmed.startsWith("taskId=")) {
                        val tid = trimmed.substringAfter("taskId=").substringBefore(" ").toIntOrNull()
                        if (tid != null && tid > 0) {
                            currentTaskId = tid
                        }
                    } else if (trimmed.startsWith("mActivityComponent=")) {
                        val comp = trimmed.substringAfter("mActivityComponent=").trim()
                        if (comp.contains("/")) {
                            currentCmp = comp
                            currentPkg = comp.substringBefore("/")
                        }
                    } else if (trimmed.startsWith("intent=")) {
                        if (trimmed.contains("cat=[android.intent.category.HOME]")) {
                            isCurrentHome = true
                        }
                        if (currentPkg.isEmpty()) {
                            val cmpMatch = Regex("cmp=([a-zA-Z0-9._]+)/([a-zA-Z0-9._$]+)").find(trimmed)
                            if (cmpMatch != null) {
                                currentPkg = cmpMatch.groupValues[1]
                                currentCmp = "${cmpMatch.groupValues[1]}/${cmpMatch.groupValues[2]}"
                            } else {
                                val pkgMatch = Regex("pkg=([a-zA-Z0-9._]+)").find(trimmed)
                                if (pkgMatch != null) {
                                    currentPkg = pkgMatch.groupValues[1]
                                }
                            }
                        }
                    } else if (trimmed.startsWith("affinity=") && currentPkg.isEmpty()) {
                        val aff = trimmed.substringAfter("affinity=").trim()
                        val pkg = if (aff.contains(":")) aff.substringAfter(":") else aff
                        if (pkg.isNotEmpty() && !pkg.contains(" ")) {
                            currentPkg = pkg
                        }
                    }
                }
            }
            commitCurrentTask()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent tasks from root", e)
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

    /**
     * Extracts hardware buffer task snapshot bitmap via reflection or cached snapshot files.
     */
    private fun getTaskThumbnail(taskId: Int): ImageBitmap? {
        // 1. Try reflection via ActivityTaskManager
        val reflectionThumb = getTaskThumbnailViaReflection(taskId)
        if (reflectionThumb != null) return reflectionThumb

        // 2. Try reading cached snapshot file from system storage if accessible
        val snapshotFiles = listOf(
            File("/data/system_ce/0/snapshots/${taskId}_reduced.jpg"),
            File("/data/system_ce/0/snapshots/${taskId}.jpg"),
            File("/data/system/users/0/snapshots/${taskId}_reduced.jpg"),
            File("/data/system/users/0/snapshots/${taskId}.jpg")
        )
        for (file in snapshotFiles) {
            if (file.exists() && file.canRead()) {
                try {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        bmp.prepareToDraw()
                        return bmp.asImageBitmap()
                    }
                } catch (_: Exception) {}
            }
        }

        return null
    }

    @SuppressLint("BlockedPrivateApi")
    private fun getTaskThumbnailViaReflection(taskId: Int): ImageBitmap? {
        return try {
            val service = getAtmService() ?: return null
            val getTaskSnapshotMethod = getTaskSnapshotMethod ?: return null

            // Request low-resolution reduced snapshot first if supported
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

                if (android.os.Build.VERSION.SDK_INT < 29) {
                    return null
                }

                val wrappedBitmap = Bitmap.wrapHardwareBuffer(
                    hardwareBuffer,
                    colorSpace
                ) ?: return null

                try {
                    val rawCopy = wrappedBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
                    // Downscale snapshot directly to 240px width for 480x800 display
                    val finalBitmap = if (rawCopy.width > 260) {
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
                    wrappedBitmap.recycle()
                }
            } finally {
                hardwareBuffer.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Switches to selected recent task or opens app intent with fallbacks.
     */
    fun launchTask(task: RecentTaskInfo): Boolean {
        // 1. Try restoring the existing task stack via reflection
        val method = startActivityFromRecentsMethod
        if (method != null) {
            try {
                val opts = ActivityOptions.makeCustomAnimation(appContext, 0, 0)
                method.invoke(activityManager, task.taskId, opts.toBundle())
                return true
            } catch (e: Exception) {
                Log.w(TAG, "startActivityFromRecents reflection failed for task ${task.taskId}", e)
            }
        }

        // 2. Try moveTaskToFront via ActivityManager
        try {
            activityManager.moveTaskToFront(task.taskId, ActivityManager.MOVE_TASK_WITH_HOME)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "moveTaskToFront failed for task ${task.taskId}", e)
        }

        // 3. Root shell: bring task to front via am start
        if (RootShell.isSuBinaryPresent()) {
            try {
                val result = Shell.cmd("am start --activity-brought-to-front -p ${task.packageName}").exec()
                if (result.isSuccess) return true
            } catch (e: Exception) {
                Log.w(TAG, "Root am start failed for ${task.packageName}", e)
            }
        }

        // 4. Fallback: launch app via standard launch intent or task baseIntent
        try {
            val intent = task.baseIntent?.cloneFilter()
                ?: packageManager.getLaunchIntentForPackage(task.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                appContext.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch intent fallback for ${task.packageName}", e)
        }

        return false
    }

    /**
     * Removes a single task from the recents stack and terminates its process.
     */
    @SuppressLint("BlockedPrivateApi")
    fun removeTask(taskId: Int, packageName: String? = null): Boolean {
        var removed = false

        // 1. Try ActivityTaskManager reflection (framework removeTask)
        try {
            val service = getAtmService()
            if (service != null) {
                val method = removeTaskMethod
                    ?: service.javaClass.methods.firstOrNull {
                        it.name == "removeTask" &&
                                it.parameterTypes.size == 1 &&
                                it.parameterTypes[0] == Int::class.javaPrimitiveType
                    }?.also {
                        it.isAccessible = true
                        removeTaskMethod = it
                    }

                if (method != null) {
                    val result = method.invoke(service, taskId)
                    Log.i(TAG, "removeTask($taskId) returned: $result")
                    removed = (result as? Boolean) ?: true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reflection removeTask failed for $taskId", e)
        }

        // 2. Shell fallback for Android 11/12 (am stack remove <taskId>)
        if (RootShell.isSuBinaryPresent()) {
            try {
                val result = Shell.cmd("am stack remove $taskId").exec()
                Log.d(TAG, "Root am stack remove $taskId result: isSuccess=${result.isSuccess}, code=${result.code}")
                removed = result.isSuccess || removed
            } catch (e: Exception) {
                Log.e(TAG, "Root am stack remove failed for $taskId", e)
            }
        } else if (!removed) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("am", "stack", "remove", taskId.toString()))
                process.waitFor()
                removed = process.exitValue() == 0
            } catch (e: Exception) {
                Log.e(TAG, "Shell fallback removeTask failed for $taskId", e)
            }
        }

        // 3. Kill the associated application process so memory is truly freed
        if (!packageName.isNullOrEmpty() && packageName != appContext.packageName) {
            try {
                activityManager.killBackgroundProcesses(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to killBackgroundProcesses for $packageName", e)
            }

            // If root is available, issue am force-stop to guarantee complete termination
            if (RootShell.isSuBinaryPresent()) {
                try {
                    Shell.cmd("am force-stop $packageName").submit()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to force-stop $packageName", e)
                }
            }
        }

        return removed
    }

    /**
     * Sequentially kills and removes all background tasks from the stack.
     */
    fun clearAllTasks(tasks: List<RecentTaskInfo>): Boolean {
        var allSuccess = true
        for (task in tasks) {
            val ok = removeTask(task.taskId, task.packageName)
            if (!ok) {
                allSuccess = false
            }
        }
        return allSuccess
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
