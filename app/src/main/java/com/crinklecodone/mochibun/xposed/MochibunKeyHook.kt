package com.crinklecodone.mochibun.xposed

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.crinklecodone.mochibun.core.util.MochibunSystemActions
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
 */
class MochibunKeyHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "MochibunKeyHook"

        const val PACKAGE_MOCHIBUN = "com.crinklecodone.mochibun"
        const val ACTIVITY_MAIN = "com.crinklecodone.mochibun.ui.MainActivity"

        // Shared broadcast actions and extras live in a no-Xposed constants object so the app
        // process can safely register receivers without linking against the Xposed API directly.
        const val ACTION_TOGGLE_CURSOR = MochibunSystemActions.ACTION_TOGGLE_CURSOR
        const val ACTION_CURSOR_KEY = MochibunSystemActions.ACTION_CURSOR_KEY
        const val ACTION_LSK_TRIGGER = MochibunSystemActions.ACTION_LSK_TRIGGER
        const val ACTION_BACK_TRIGGER = MochibunSystemActions.ACTION_BACK_TRIGGER
        const val ACTION_HOME_TRIGGER = MochibunSystemActions.ACTION_HOME_TRIGGER
        const val ACTION_HARDWARE_KEY_TRIGGER = MochibunSystemActions.ACTION_HARDWARE_KEY_TRIGGER

        const val EXTRA_KEY_EVENT = MochibunSystemActions.EXTRA_KEY_EVENT
        const val EXTRA_KEY_CODE = MochibunSystemActions.EXTRA_KEY_CODE
        const val EXTRA_ACTION = MochibunSystemActions.EXTRA_ACTION
        const val EXTRA_REPEAT_COUNT = MochibunSystemActions.EXTRA_REPEAT_COUNT
        const val EXTRA_CURSOR_ACTIVE = MochibunSystemActions.EXTRA_CURSOR_ACTIVE

        // AT-M120 physical scan codes
        private const val SCANCODE_SIDE_F4 = 62
        private const val SCANCODE_APP_SWITCH = 580

        // Volatile internal cursor state tracked across key events in system_server
        @Volatile
        var isCursorActive: Boolean = false

        // Volatile internal Home key press state tracked across key events in system_server
        @Volatile
        var isHomePressedOnMochibun: Boolean = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Gate execution strictly to system_server
        if (lpparam.packageName != "android") return

        try {
            hookPhoneWindowManager(lpparam.classLoader)
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
        if (isCursorActive && isCursorNavKey(keyCode)) {
            forwardCursorKey(context, event)
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

        // 4. Physical function keys: F1 (59), F2 (60), F3 (61), CAMERA (212)
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

    private fun isCursorNavKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_NUMPAD_2,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_NUMPAD_8 -> true
            else -> false
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

    private fun forwardCursorKey(context: Context?, event: KeyEvent) {
        if (context == null) return
        val intent = Intent(ACTION_CURSOR_KEY).apply {
            setPackage(PACKAGE_MOCHIBUN)
            putExtra(EXTRA_KEY_EVENT, event)
            putExtra(EXTRA_KEY_CODE, event.keyCode)
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
