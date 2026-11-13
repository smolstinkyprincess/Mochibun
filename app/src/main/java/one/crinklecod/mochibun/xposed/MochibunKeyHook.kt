package one.crinklecod.mochibun.xposed

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed / Xposed hook inside system_server (target package: "android").
 * Intercepts low-level hardware keys before they reach the window manager dispatcher:
 * - F4 / Side key: Toggles virtual cursor state and notifies VirtualCursorService.
 * - Directional D-pad & numeric cursor keys: Redirected to Mochibun when the cursor is active.
 * - KEYCODE_APP_SWITCH: Swallows system Recents invocation when Mochibun is in foreground.
 * - KEYCODE_BACK: Prevents OS minimization when on Mochibun's Home screen.
 * - KEYCODE_VOLUME_UP/DOWN: With Media Volume Lock on, always steps STREAM_MUSIC (outside calls).
 */
class MochibunKeyHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "MochibunKeyHook"

        const val PACKAGE_MOCHIBUN = "one.crinklecod.mochibun"
        const val ACTIVITY_MAIN = "one.crinklecod.mochibun.ui.MainActivity"

        // Shared broadcast actions and extras live in a no-Xposed constants object so the app
        // process can safely register receivers without linking against the Xposed API directly.
        const val ACTION_TOGGLE_CURSOR = MochibunSystemActions.ACTION_TOGGLE_CURSOR
        const val ACTION_CURSOR_KEY = MochibunSystemActions.ACTION_CURSOR_KEY
        const val ACTION_LSK_TRIGGER = MochibunSystemActions.ACTION_LSK_TRIGGER
        const val ACTION_BACK_TRIGGER = MochibunSystemActions.ACTION_BACK_TRIGGER
        const val ACTION_HOME_TRIGGER = MochibunSystemActions.ACTION_HOME_TRIGGER
        const val ACTION_HARDWARE_KEY_TRIGGER = MochibunSystemActions.ACTION_HARDWARE_KEY_TRIGGER
        private const val ACTION_CURSOR_MODE_CHANGED = MochibunSystemActions.ACTION_CURSOR_MODE_CHANGED
        private const val ACTION_REQUEST_CURSOR_MODE = MochibunSystemActions.ACTION_REQUEST_CURSOR_MODE
        private const val ACTION_REQUEST_KEY_HOOK_STATUS =
            MochibunSystemActions.ACTION_REQUEST_KEY_HOOK_STATUS
        private const val ACTION_KEY_HOOK_STATUS = MochibunSystemActions.ACTION_KEY_HOOK_STATUS
        private const val ACTION_MEDIA_VOLUME_LOCK_CHANGED =
            MochibunSystemActions.ACTION_MEDIA_VOLUME_LOCK_CHANGED
        private const val ACTION_REQUEST_MEDIA_VOLUME_LOCK =
            MochibunSystemActions.ACTION_REQUEST_MEDIA_VOLUME_LOCK
        private const val HOOK_SETTING_CACHE_TTL_MS = 2_000L

        const val EXTRA_KEY_EVENT = MochibunSystemActions.EXTRA_KEY_EVENT
        const val EXTRA_KEY_CODE = MochibunSystemActions.EXTRA_KEY_CODE
        const val EXTRA_ACTION = MochibunSystemActions.EXTRA_ACTION
        const val EXTRA_REPEAT_COUNT = MochibunSystemActions.EXTRA_REPEAT_COUNT
        const val EXTRA_CURSOR_ACTIVE = MochibunSystemActions.EXTRA_CURSOR_ACTIVE
        private const val EXTRA_MEDIA_VOLUME_LOCK_ENABLED =
            MochibunSystemActions.EXTRA_MEDIA_VOLUME_LOCK_ENABLED

        // AT-M120 physical scan codes
        private const val SCANCODE_SIDE_F4 = 62
        private const val SCANCODE_APP_SWITCH = 580

        // Volatile internal cursor state tracked across key events in system_server
        @Volatile
        var isCursorActive: Boolean = false

        // Volatile internal Home key press state tracked across key events in system_server
        @Volatile
        var isHomePressedOnMochibun: Boolean = false

        // Mirrors PreferencesManager.isMediaVolumeLockEnabled; pushed by the app via
        // ACTION_MEDIA_VOLUME_LOCK_CHANGED (see MediaVolumeLockSync). Off until the app reports in.
        @Volatile
        var isMediaVolumeLockEnabled: Boolean = false

        // True while we own the current volume key press, so its ACTION_UP is swallowed too
        @Volatile
        private var isVolumePressRoutedToMedia: Boolean = false

        @Volatile
        private var appStateReceiverRegistered = false

        private var appStateReceiver: BroadcastReceiver? = null

        @Volatile
        private var keyHookStatusReceiverRegistered = false

        @Volatile
        private var cachedHookEnabled = true

        @Volatile
        private var lastHookSettingCheckElapsed = 0L

        @Volatile
        private var hasHookSettingCheck = false

        @Volatile
        private var heartbeatRecorded = false

        @Volatile
        private var hookStatusInitialized = false

        @Volatile
        private var systemServerResolver: android.content.ContentResolver? = null
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Gate execution strictly to system_server
        if (lpparam.packageName != "android") return

        try {
            hookPhoneWindowManager(lpparam.classLoader)
            val systemContext = try {
                val activityThread = XposedHelpers.findClass(
                    "android.app.ActivityThread",
                    lpparam.classLoader
                )
                XposedHelpers.callStaticMethod(activityThread, "currentApplication") as? Context
            } catch (_: Throwable) {
                null
            }
            if (systemContext != null) initializeHookStatus(systemContext)
            XposedBridge.log("$TAG: Successfully installed PhoneWindowManager key hook")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Failed to hook PhoneWindowManager: ${t.message}")
        }
    }

    private fun hookPhoneWindowManager(classLoader: ClassLoader) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.policy.PhoneWindowManager",
            classLoader,
            "interceptKeyBeforeQueueing",
            KeyEvent::class.java,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        handleInterceptKey(param)
                    } catch (t: Throwable) {
                        XposedBridge.log("$TAG: Error in interceptKeyBeforeQueueing: ${t.message}")
                    }
                }
            }
        )
    }

    private fun handleInterceptKey(param: XC_MethodHook.MethodHookParam) {
        val event = param.args[0] as? KeyEvent ?: return
        val keyCode = event.keyCode
        val action = event.action
        val isDown = action == KeyEvent.ACTION_DOWN

        val phoneWindowManager = param.thisObject
        val context = XposedHelpers.getObjectField(phoneWindowManager, "mContext") as? Context
        if (context != null && !hookStatusInitialized) initializeHookStatus(context)
        if (!isHookEnabled()) return
        if (context != null) ensureAppStateReceiver(context)

        // 1. F4 Key (KEYCODE_F4 / AT-M120 side key scanCode 62)
        val isF4 = keyCode == KeyEvent.KEYCODE_F4 || event.scanCode == SCANCODE_SIDE_F4
        if (isF4) {
            if (isDown && event.repeatCount == 0) {
                isCursorActive = !isCursorActive
                XposedBridge.log("$TAG: F4 pressed, toggling cursor -> isCursorActive=$isCursorActive")
                broadcastCursorToggle(context, isCursorActive)
            }

            // Swallow key from the OS on both ACTION_DOWN and ACTION_UP
            param.result = 0
            return
        }

        // 2. Cursor active redirection
        val cursorKeyCode = MochibunSystemActions.cursorKeyCodeForEvent(keyCode, event.scanCode)
        if (isCursorActive && cursorKeyCode != null) {
            forwardCursorKey(context, event, cursorKeyCode)
            // Swallow key so foreground application and OS never receive directional or numeric inputs
            param.result = 0
            return
        }

        // 3. Launcher HOME handling with press-state tracking and Quick Settings toggle
        val isHome = keyCode == KeyEvent.KEYCODE_HOME || event.scanCode == 172 || event.scanCode == 102
        if (isHome) {
            if (isDown) {
                val topActivity = getTopActivity(phoneWindowManager, context)
                val isMochibunForeground = topActivity?.packageName == PACKAGE_MOCHIBUN
                val isMochibunHome = isMochibunForeground && (
                    topActivity?.className?.isEmpty() == true ||
                    topActivity?.className == ACTIVITY_MAIN ||
                    topActivity?.className?.endsWith("MainActivity") == true
                )
                val isShadeOverMochibun = if (!isMochibunHome) {
                    isNotificationShadeOverMochibun(phoneWindowManager, context)
                } else false

                if (isMochibunHome || isShadeOverMochibun) {
                    isHomePressedOnMochibun = true
                    broadcastHomeTrigger(context, event)
                    // Swallow KEYCODE_HOME so OS default Home intent / assistant launch is suppressed
                    param.result = 0
                    return
                }
            } else {
                // ACTION_UP or non-down action: swallow release if the press began on Mochibun or shade
                if (isHomePressedOnMochibun) {
                    isHomePressedOnMochibun = false
                    broadcastHomeTrigger(context, event)
                    // CRITICAL: Swallow ACTION_UP from the OS so PhoneWindowManager never invokes
                    // closeSystemDialogs() / collapsePanels() on Home key release!
                    param.result = 0
                    return
                }
            }
        }

        // Launcher LSK & RSK (Recents & Back) handling when Mochibun is in foreground
        val isAppSwitch = keyCode == KeyEvent.KEYCODE_APP_SWITCH || event.scanCode == SCANCODE_APP_SWITCH
        val isBack = keyCode == KeyEvent.KEYCODE_BACK

        if (isAppSwitch || isBack) {
            val topActivity = getTopActivity(phoneWindowManager, context)
            val isMochibunForeground = topActivity?.packageName == PACKAGE_MOCHIBUN

            if (isMochibunForeground) {
                if (isAppSwitch) {
                    broadcastLskTrigger(context, event)
                    // Swallow KEYCODE_APP_SWITCH so OS Recents overview is never triggered
                    param.result = 0
                    return
                } else {
                    val isMochibunHome = topActivity.className.isEmpty() ||
                            topActivity.className == ACTIVITY_MAIN ||
                            topActivity.className.endsWith("MainActivity")

                    if (isMochibunHome) {
                        broadcastBackTrigger(context, event)
                        // Swallow KEYCODE_BACK on Home to prevent OS minimization
                        param.result = 0
                        return
                    }
                }
            }
        }

        // 4. Media Volume Lock: route the volume rocker to STREAM_MUSIC instead of the ringer
        if (MochibunSystemActions.mediaVolumeDirection(keyCode) != null) {
            if (handleMediaVolumeKey(context, event)) {
                param.result = 0
                return
            }
        }

        // 5. Physical function keys: F1 (59), F2 (60), F3 (61), CAMERA (212)
        val isFunctionKey = keyCode == KeyEvent.KEYCODE_F1 ||
                keyCode == KeyEvent.KEYCODE_F2 ||
                keyCode == KeyEvent.KEYCODE_F3 ||
                keyCode == KeyEvent.KEYCODE_CAMERA ||
                when (event.scanCode) { 59, 60, 61, 212 -> true else -> false }

        if (isFunctionKey) {
            broadcastHardwareKey(context, event)
            // Swallow key so stock ROM apps never launch and F3 long press never crashes
            param.result = 0
            return
        }
    }

    private fun initializeHookStatus(context: Context) {
        if (hookStatusInitialized) return
        synchronized(MochibunKeyHook::class.java) {
            if (hookStatusInitialized) return
            systemServerResolver = context.contentResolver
            if (!heartbeatRecorded) {
                try {
                    Settings.Global.putLong(
                        context.contentResolver,
                        PreferencesManager.GLOBAL_KEY_HOOK_ALIVE_ELAPSED,
                        SystemClock.elapsedRealtime()
                    )
                    Settings.Global.putInt(
                        context.contentResolver,
                        PreferencesManager.GLOBAL_KEY_HOOK_ALIVE_BOOT_COUNT,
                        Settings.Global.getInt(
                            context.contentResolver,
                            Settings.Global.BOOT_COUNT,
                            -1
                        )
                    )
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG: Failed to record key hook heartbeat: ${t.message}")
                }
                heartbeatRecorded = true
            }
            ensureKeyHookStatusReceiver(context)
            // Register early so the Media Volume Lock state is known before the first volume press
            ensureAppStateReceiver(context)
            hasHookSettingCheck = false
            hookStatusInitialized = true
        }
    }

    private fun isHookEnabled(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!hasHookSettingCheck ||
            now - lastHookSettingCheckElapsed >= HOOK_SETTING_CACHE_TTL_MS
        ) {
            synchronized(MochibunKeyHook::class.java) {
                if (!hasHookSettingCheck ||
                    now - lastHookSettingCheckElapsed >= HOOK_SETTING_CACHE_TTL_MS
                ) {
                    val resolver = systemServerResolver
                    if (resolver != null) {
                        cachedHookEnabled = try {
                            Settings.Global.getInt(
                                resolver,
                                PreferencesManager.GLOBAL_HOOK_KEY_ENABLED,
                                1
                            ) == 1
                        } catch (_: Throwable) {
                            true
                        }
                    }
                    lastHookSettingCheckElapsed = now
                    hasHookSettingCheck = true
                }
            }
        }
        return cachedHookEnabled
    }

    private fun ensureKeyHookStatusReceiver(context: Context) {
        synchronized(MochibunKeyHook::class.java) {
            if (keyHookStatusReceiverRegistered) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_REQUEST_KEY_HOOK_STATUS) return
                    val response = Intent(ACTION_KEY_HOOK_STATUS).apply {
                        setPackage(PACKAGE_MOCHIBUN)
                        putExtra(
                            MochibunSystemActions.EXTRA_KEY_HOOK_ACTIVE,
                            isHookEnabled()
                        )
                    }
                    context?.sendBroadcast(response)
                }
            }
            val filter = IntentFilter(ACTION_REQUEST_KEY_HOOK_STATUS)
            try {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    context.registerReceiver(receiver, filter)
                }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: Failed to register key hook status receiver: ${t.message}")
            }
            keyHookStatusReceiverRegistered = true
        }
    }

    private fun broadcastCursorToggle(context: Context?, active: Boolean) {
        if (context == null) return
        val intent = Intent(ACTION_TOGGLE_CURSOR).apply {
            setPackage(PACKAGE_MOCHIBUN)
            putExtra(EXTRA_CURSOR_ACTIVE, active)
        }
        sendBroadcastSafe(context, intent)
    }

    /**
     * Receives state pushed by the Mochibun app process (cursor mode, Media Volume Lock).
     * Only senders holding Mochibun's signature permission are accepted.
     */
    private fun ensureAppStateReceiver(context: Context) {
        if (appStateReceiverRegistered) return
        synchronized(MochibunKeyHook::class.java) {
            if (appStateReceiverRegistered) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        ACTION_CURSOR_MODE_CHANGED ->
                            isCursorActive = intent.getBooleanExtra(EXTRA_CURSOR_ACTIVE, false)
                        ACTION_MEDIA_VOLUME_LOCK_CHANGED -> {
                            isMediaVolumeLockEnabled =
                                intent.getBooleanExtra(EXTRA_MEDIA_VOLUME_LOCK_ENABLED, false)
                            XposedBridge.log("$TAG: Media Volume Lock -> $isMediaVolumeLockEnabled")
                        }
                    }
                }
            }
            try {
                val filter = IntentFilter().apply {
                    addAction(ACTION_CURSOR_MODE_CHANGED)
                    addAction(ACTION_MEDIA_VOLUME_LOCK_CHANGED)
                }
                context.registerReceiver(
                    receiver,
                    filter,
                    MochibunSystemActions.CURSOR_MODE_SYNC_PERMISSION,
                    null
                )
                appStateReceiver = receiver
                appStateReceiverRegistered = true
                // Ask the app for its current state; it also pushes on its own start-up and on changes
                sendBroadcastSafe(context, Intent(ACTION_REQUEST_CURSOR_MODE))
                sendBroadcastSafe(context, Intent(ACTION_REQUEST_MEDIA_VOLUME_LOCK))
            } catch (t: Throwable) {
                XposedBridge.log("$TAG: Failed to register app state receiver: ${t.message}")
            }
        }
    }

    /**
     * Media Volume Lock: steps STREAM_MUSIC on volume key down/repeat and reports whether the key
     * should be swallowed. Returns false (stock handling) when the lock is off or a call is active.
     */
    private fun handleMediaVolumeKey(context: Context?, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            // Swallow the release only if we handled the matching press
            val owned = isVolumePressRoutedToMedia
            isVolumePressRoutedToMedia = false
            return owned
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false

        if (event.repeatCount == 0) isVolumePressRoutedToMedia = false
        if (!isMediaVolumeLockEnabled || context == null) return false
        // A repeat of a press we didn't take (e.g. lock toggled mid-hold) stays with stock handling
        if (event.repeatCount > 0 && !isVolumePressRoutedToMedia) return false

        val direction = MochibunSystemActions.mediaVolumeDirection(event.keyCode) ?: return false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        if (!MochibunSystemActions.shouldRouteVolumeToMedia(audioManager.mode)) return false

        val token = Binder.clearCallingIdentity()
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: Media Volume Lock adjust failed: ${t.message}")
            return false
        } finally {
            Binder.restoreCallingIdentity(token)
        }
        isVolumePressRoutedToMedia = true
        return true
    }

    private fun forwardCursorKey(context: Context?, event: KeyEvent, cursorKeyCode: Int) {
        if (context == null) return
        val intent = Intent(ACTION_CURSOR_KEY).apply {
            setPackage(PACKAGE_MOCHIBUN)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra(EXTRA_KEY_EVENT, event)
            putExtra(EXTRA_KEY_CODE, cursorKeyCode)
            putExtra(EXTRA_ACTION, event.action)
            putExtra(EXTRA_REPEAT_COUNT, event.repeatCount)
        }
        sendBroadcastSafe(context, intent)
    }

    private fun broadcastLskTrigger(context: Context?, event: KeyEvent) {
        if (context == null) return
        val keyCode = if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN) event.keyCode else KeyEvent.KEYCODE_APP_SWITCH
        val intent = Intent(ACTION_LSK_TRIGGER).apply {
            setPackage(PACKAGE_MOCHIBUN)
            putExtra(EXTRA_KEY_EVENT, event)
            putExtra(EXTRA_KEY_CODE, keyCode)
            putExtra(EXTRA_ACTION, event.action)
            putExtra(EXTRA_REPEAT_COUNT, event.repeatCount)
        }
        sendBroadcastSafe(context, intent)
    }

    private fun broadcastBackTrigger(context: Context?, event: KeyEvent) {
        if (context == null) return
        val keyCode = if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN) event.keyCode else KeyEvent.KEYCODE_BACK
        val intent = Intent(ACTION_BACK_TRIGGER).apply {
            setPackage(PACKAGE_MOCHIBUN)
            putExtra(EXTRA_KEY_EVENT, event)
            putExtra(EXTRA_KEY_CODE, keyCode)
            putExtra(EXTRA_ACTION, event.action)
            putExtra(EXTRA_REPEAT_COUNT, event.repeatCount)
        }
        sendBroadcastSafe(context, intent)
    }

    private fun broadcastHomeTrigger(context: Context?, event: KeyEvent) {
        if (context == null) return
        val keyCode = if (event.keyCode != KeyEvent.KEYCODE_UNKNOWN) event.keyCode else KeyEvent.KEYCODE_HOME
        val intent = Intent(ACTION_HOME_TRIGGER).apply {
            setPackage(PACKAGE_MOCHIBUN)
            putExtra(EXTRA_KEY_EVENT, event)
            putExtra(EXTRA_KEY_CODE, keyCode)
            putExtra(EXTRA_ACTION, event.action)
            putExtra(EXTRA_REPEAT_COUNT, event.repeatCount)
        }
        sendBroadcastSafe(context, intent)
    }

    private fun broadcastHardwareKey(context: Context?, event: KeyEvent) {
        if (context == null) return
        val intent = Intent(ACTION_HARDWARE_KEY_TRIGGER).apply {
            setPackage(PACKAGE_MOCHIBUN)
            putExtra(EXTRA_KEY_EVENT, event)
            putExtra(EXTRA_KEY_CODE, event.keyCode)
            putExtra(EXTRA_ACTION, event.action)
            putExtra(EXTRA_REPEAT_COUNT, event.repeatCount)
        }
        sendBroadcastSafe(context, intent)
    }

    private fun sendBroadcastSafe(context: Context, intent: Intent) {
        intent.setPackage(PACKAGE_MOCHIBUN)
        try {
            val userAll = XposedHelpers.getStaticObjectField(android.os.UserHandle::class.java, "ALL")
            XposedHelpers.callMethod(context, "sendBroadcastAsUser", intent, userAll)
        } catch (_: Throwable) {
            context.sendBroadcast(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun getTopActivity(phoneWindowManager: Any, context: Context?): ComponentName? {
        // Method 1: Check focused window from DisplayPolicy / DisplayContent
        try {
            val displayPolicy = XposedHelpers.getObjectField(phoneWindowManager, "mDefaultDisplayPolicy")
            if (displayPolicy != null) {
                var focusedWindow = try {
                    XposedHelpers.getObjectField(displayPolicy, "mFocusedWindow")
                } catch (_: Throwable) { null }
                if (focusedWindow == null) {
                    val displayContent = try {
                        XposedHelpers.getObjectField(displayPolicy, "mDisplayContent")
                    } catch (_: Throwable) { null }
                    if (displayContent != null) {
                        focusedWindow = try {
                            XposedHelpers.getObjectField(displayContent, "mCurrentFocus")
                        } catch (_: Throwable) { null }
                    }
                }
                if (focusedWindow != null) {
                    val owningPackage = try {
                        XposedHelpers.callMethod(focusedWindow, "getOwningPackage") as? String
                    } catch (_: Throwable) {
                        try {
                            XposedHelpers.getObjectField(focusedWindow, "mOwningPackage") as? String
                        } catch (_: Throwable) { null }
                    }
                    if (!owningPackage.isNullOrEmpty()) {
                        val activityRecord = try {
                            XposedHelpers.getObjectField(focusedWindow, "mActivityRecord")
                        } catch (_: Throwable) { null }
                        val componentName = try {
                            activityRecord?.let {
                                XposedHelpers.getObjectField(it, "mActivityComponent") as? ComponentName
                            }
                        } catch (_: Throwable) { null }
                        if (componentName != null) return componentName

                        val shortComponentName = try {
                            activityRecord?.let {
                                XposedHelpers.getObjectField(it, "shortComponentName") as? String
                            }
                        } catch (_: Throwable) { null }
                        val className = if (!shortComponentName.isNullOrEmpty() && shortComponentName.contains("/")) {
                            val part = shortComponentName.substringAfter("/")
                            if (part.startsWith(".")) "$owningPackage$part" else part
                        } else {
                            ""
                        }
                        return ComponentName(owningPackage, className)
                    }
                }
            }
        } catch (_: Throwable) {}

        // Method 2: Check RunningTaskInfo via ActivityManager
        try {
            if (context != null) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val top = am?.getRunningTasks(1)?.firstOrNull()?.topActivity
                if (top != null) return top
            }
        } catch (_: Throwable) {}

        // Method 3: Query ActivityTaskManagerService directly via reflection
        try {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val getService = atmClass.getMethod("getService")
            val atm = getService.invoke(null)
            val getTasks = atm.javaClass.getMethod("getTasks", Int::class.javaPrimitiveType)
            val tasks = getTasks.invoke(atm, 1) as? List<*>
            val task = tasks?.firstOrNull()
            if (task != null) {
                val top = XposedHelpers.getObjectField(task, "topActivity") as? ComponentName
                if (top != null) return top
            }
        } catch (_: Throwable) {}

        return null
    }

    private fun isNotificationShadeOverMochibun(phoneWindowManager: Any, context: Context?): Boolean {
        try {
            val displayPolicy = XposedHelpers.getObjectField(phoneWindowManager, "mDefaultDisplayPolicy") ?: return false
            var focusedWindow = try {
                XposedHelpers.getObjectField(displayPolicy, "mFocusedWindow")
            } catch (_: Throwable) { null }
            if (focusedWindow == null) {
                val displayContent = try {
                    XposedHelpers.getObjectField(displayPolicy, "mDisplayContent")
                } catch (_: Throwable) { null }
                if (displayContent != null) {
                    focusedWindow = try {
                        XposedHelpers.getObjectField(displayContent, "mCurrentFocus")
                    } catch (_: Throwable) { null }
                }
            }
            if (focusedWindow != null) {
                val owningPackage = try {
                    XposedHelpers.callMethod(focusedWindow, "getOwningPackage") as? String
                } catch (_: Throwable) {
                    try {
                        XposedHelpers.getObjectField(focusedWindow, "mOwningPackage") as? String
                    } catch (_: Throwable) { null }
                }
                if (owningPackage == "com.android.systemui") {
                    val displayContent = try {
                        XposedHelpers.getObjectField(displayPolicy, "mDisplayContent")
                    } catch (_: Throwable) { null }
                    if (displayContent != null) {
                        val focusedApp = try {
                            XposedHelpers.getObjectField(displayContent, "mFocusedApp")
                        } catch (_: Throwable) { null }
                        if (focusedApp != null) {
                            val comp = try {
                                XposedHelpers.getObjectField(focusedApp, "mActivityComponent") as? ComponentName
                            } catch (_: Throwable) { null }
                            if (comp?.packageName == PACKAGE_MOCHIBUN) return true
                        }
                    }
                    if (context != null) {
                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        val top = am?.getRunningTasks(1)?.firstOrNull()?.topActivity
                        if (top?.packageName == PACKAGE_MOCHIBUN) return true
                    }
                }
            }
        } catch (_: Throwable) {}
        return false
    }
}
