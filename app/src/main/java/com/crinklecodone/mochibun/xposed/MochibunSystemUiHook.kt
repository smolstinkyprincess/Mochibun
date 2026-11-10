package com.crinklecodone.mochibun.xposed

import android.util.Log
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed / Xposed hook inside SystemUI (target package: "com.android.systemui") for
 * the ALT Mive Style Folder AT-M120 (Android 12, API 31).
 *
 * Implements:
 * - Stage 0: Initial module setup, gating to com.android.systemui.
 * - Stage A: Status bar cellular suppression (A1), carrier text suppression (A2), and icon tint (A3).
 * - Stage B: 4-heart battery display in BatteryMeterView (percent forced on, stock icon hidden,
 *            hearts ♥/♡ with optional charging ✦, low-battery rose tint #F06292).
 *
 * Stub is in place for Stage C (squircle volume dialog).
 */
class MochibunSystemUiHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "MochibunSysUI"
        private const val TARGET_PACKAGE = "com.android.systemui"

        // Stage A3 palette colors
        // Icons drawn dark (over light backgrounds): deep plum #4A2E56
        private const val COLOR_DARK_MODE_DEEP_PLUM = 0xFF4A2E56.toInt()
        // Icons drawn light (over dark backgrounds): pale pink #FFE4F0
        private const val COLOR_LIGHT_MODE_PALE_PINK = 0xFFFFE4F0.toInt()

        // Stage B palette colors
        // Rose for low battery (level <= 15 and not charging): #F06292
        private const val COLOR_ROSE_LOW_BATTERY = 0xFFF06292.toInt()

        /**
         * Computes the 4-heart battery string:
         * filled = ceil(level / 25.0) clamped to 0..4
         * 76-100 = ♥♥♥♥
         * 51-75 = ♥♥♥♡
         * 26-50 = ♥♥♡♡
         * 1-25 = ♥♡♡♡
         * 0 = ♡♡♡♡
         * Charging: appends " ✦"
         */
        internal fun getBatteryHearts(level: Int, charging: Boolean): String =
            BatteryHeartFormatter.format(level, charging)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        Log.d(TAG, "Initializing MochibunSystemUiHook for package: ${lpparam.packageName}")

        hookStatusBar(lpparam.classLoader)
        hookBattery(lpparam.classLoader)
        hookVolume(lpparam.classLoader)
    }

    /**
     * Stage A: Status bar hooks (cellular suppression, carrier text suppression, icon tint).
     */
    private fun hookStatusBar(classLoader: ClassLoader) {
        // A1: Cellular signal suppression
        hookCellularSignal(classLoader)

        // A2: Carrier / Operator text suppression
        hookCarrierText(classLoader)

        // A3: DarkIconDispatcher icon tint colors
        hookIconTint(classLoader)
    }

    /**
     * A1: Suppress mobile signal strength icon, data-type indicators, and no-SIM badges.
     * Leaves Wi-Fi slot untouched.
     */
    private fun hookCellularSignal(classLoader: ClassLoader) {
        // 1. Suppress StatusBarIconControllerImpl.setMobileIcons
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
                classLoader,
                "setMobileIcons",
                String::class.java,
                List::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            // Suppress adding or updating mobile icons in the status bar
                            param.result = null
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in StatusBarIconControllerImpl.setMobileIcons hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: StatusBarIconControllerImpl.setMobileIcons")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook StatusBarIconControllerImpl.setMobileIcons", t)
        }

        // 2. Hook StatusBarSignalPolicy to keep mHideMobile=true and clear no-SIM indicators
        try {
            val signalPolicyClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.StatusBarSignalPolicy",
                classLoader
            )

            for (constructor in signalPolicyClass.declaredConstructors) {
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                XposedHelpers.setBooleanField(param.thisObject, "mHideMobile", true)
                                val slotMobile = XposedHelpers.getObjectField(param.thisObject, "mSlotMobile") as? String
                                val iconController = XposedHelpers.getObjectField(param.thisObject, "mIconController")
                                if (slotMobile != null && iconController != null) {
                                    XposedHelpers.callMethod(iconController, "removeAllIconsForSlot", slotMobile)
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in StatusBarSignalPolicy constructor hook", t)
                            }
                        }
                    }
                )
            }
            Log.d(TAG, "Installed hook: StatusBarSignalPolicy constructors")

            // Keep mHideMobile=true if onTuningChanged is invoked
            XposedHelpers.findAndHookMethod(
                signalPolicyClass,
                "onTuningChanged",
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.setBooleanField(param.thisObject, "mHideMobile", true)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in StatusBarSignalPolicy.onTuningChanged hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: StatusBarSignalPolicy.onTuningChanged")

            // Suppress no-SIM indicator updates
            XposedHelpers.findAndHookMethod(
                signalPolicyClass,
                "setNoSims",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            param.args[0] = false
                            param.args[1] = false
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in StatusBarSignalPolicy.setNoSims hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: StatusBarSignalPolicy.setNoSims")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook StatusBarSignalPolicy", t)
        }

        // 3. Hook StatusBarMobileView to force GONE if any instance is created
        try {
            val mobileViewClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.StatusBarMobileView",
                classLoader
            )

            for (constructor in mobileViewClass.declaredConstructors) {
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val view = param.thisObject as? View
                                view?.visibility = View.GONE
                                XposedHelpers.setBooleanField(param.thisObject, "mForceHidden", true)
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in StatusBarMobileView constructor hook", t)
                            }
                        }
                    }
                )
            }
            Log.d(TAG, "Installed hook: StatusBarMobileView constructors")

            XposedHelpers.findAndHookMethod(
                mobileViewClass,
                "initViewState",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View
                            view?.visibility = View.GONE
                            XposedHelpers.setBooleanField(param.thisObject, "mForceHidden", true)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in StatusBarMobileView.initViewState hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: StatusBarMobileView.initViewState")

            val mobileIconStateClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.StatusBarSignalPolicy\$MobileIconState",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                mobileViewClass,
                "applyMobileState",
                mobileIconStateClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            param.args[0] = null
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in StatusBarMobileView.applyMobileState before hook", t)
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View
                            view?.visibility = View.GONE
                            XposedHelpers.setBooleanField(param.thisObject, "mForceHidden", true)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in StatusBarMobileView.applyMobileState after hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: StatusBarMobileView.applyMobileState")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook StatusBarMobileView", t)
        }
    }

    /**
     * A2: Suppress carrier and operator name text in both the status bar and lockscreen.
     */
    private fun hookCarrierText(classLoader: ClassLoader) {
        // 1. Hook CarrierText in com.android.keyguard (lockscreen / keyguard status bar)
        try {
            val carrierTextClass = XposedHelpers.findClass(
                "com.android.keyguard.CarrierText",
                classLoader
            )

            for (constructor in carrierTextClass.declaredConstructors) {
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val view = param.thisObject as? View
                                view?.visibility = View.GONE
                                (view as? TextView)?.text = ""
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in CarrierText constructor hook", t)
                            }
                        }
                    }
                )
            }
            Log.d(TAG, "Installed hook: CarrierText constructors")

            XposedHelpers.findAndHookMethod(
                carrierTextClass,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View
                            view?.visibility = View.GONE
                            (view as? TextView)?.text = ""
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in CarrierText.onFinishInflate hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: CarrierText.onFinishInflate")

            XposedHelpers.findAndHookMethod(
                carrierTextClass,
                "onVisibilityChanged",
                View::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            param.args[1] = View.GONE
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in CarrierText.onVisibilityChanged hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: CarrierText.onVisibilityChanged")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook CarrierText", t)
        }

        // 2. Hook CarrierTextManager to clear carrier text updates
        try {
            val callbackInfoClass = XposedHelpers.findClass(
                "com.android.keyguard.CarrierTextManager\$CarrierTextCallbackInfo",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                "com.android.keyguard.CarrierTextManager",
                classLoader,
                "postToCallback",
                callbackInfoClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val info = param.args[0]
                            if (info != null) {
                                XposedHelpers.setObjectField(info, "carrierText", "")
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in CarrierTextManager.postToCallback hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: CarrierTextManager.postToCallback")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook CarrierTextManager", t)
        }

        // 3. Hook OperatorNameView in com.android.systemui.statusbar (status bar operator name)
        try {
            val operatorNameViewClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.OperatorNameView",
                classLoader
            )

            for (constructor in operatorNameViewClass.declaredConstructors) {
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val view = param.thisObject as? View
                                view?.visibility = View.GONE
                                (view as? TextView)?.text = ""
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in OperatorNameView constructor hook", t)
                            }
                        }
                    }
                )
            }
            Log.d(TAG, "Installed hook: OperatorNameView constructors")

            XposedHelpers.findAndHookMethod(
                operatorNameViewClass,
                "update",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View
                            view?.visibility = View.GONE
                            (view as? TextView)?.text = ""
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in OperatorNameView.update hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: OperatorNameView.update")

            XposedHelpers.findAndHookMethod(
                operatorNameViewClass,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View
                            view?.visibility = View.GONE
                            (view as? TextView)?.text = ""
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in OperatorNameView.onAttachedToWindow hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: OperatorNameView.onAttachedToWindow")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook OperatorNameView", t)
        }
    }

    /**
     * A3: Override DarkIconDispatcherImpl single-tone colors to:
     * - Deep plum #4A2E56 for dark-mode icons (drawn dark over light backgrounds)
     * - Pale pink #FFE4F0 for light-mode icons (drawn light over dark backgrounds)
     */
    private fun hookIconTint(classLoader: ClassLoader) {
        try {
            val darkIconClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl",
                classLoader
            )

            for (constructor in darkIconClass.declaredConstructors) {
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                XposedHelpers.setIntField(
                                    param.thisObject,
                                    "mDarkModeIconColorSingleTone",
                                    COLOR_DARK_MODE_DEEP_PLUM
                                )
                                XposedHelpers.setIntField(
                                    param.thisObject,
                                    "mLightModeIconColorSingleTone",
                                    COLOR_LIGHT_MODE_PALE_PINK
                                )
                                Log.d(TAG, "Applied Mochibun icon tint colors to DarkIconDispatcherImpl")
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error setting icon colors on DarkIconDispatcherImpl", t)
                            }
                        }
                    }
                )
            }
            Log.d(TAG, "Installed hook: DarkIconDispatcherImpl constructors")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook DarkIconDispatcherImpl", t)
        }
    }

    /**
     * Stage B: 4-heart battery display in BatteryMeterView.
     * - Force percent text on (mShowPercentMode = 1).
     * - Hide stock battery icon view (mBatteryIconView.visibility = View.GONE).
     * - Replace percent text with 4 hearts (♥ / ♡) and " ✦" when charging.
     * - Tint rose (#F06292) when level <= 15 and not charging.
     */
    private fun hookBattery(classLoader: ClassLoader) {
        val batteryMeterViewClassName = "com.android.systemui.BatteryMeterView"

        fun applyHeartBattery(instance: Any) {
            val textView = XposedHelpers.getObjectField(instance, "mBatteryPercentView") as? TextView ?: return
            val level = XposedHelpers.getIntField(instance, "mLevel")
            val charging = XposedHelpers.getBooleanField(instance, "mCharging")

            textView.text = getBatteryHearts(level, charging)

            if (level <= 15 && !charging) {
                textView.setTextColor(COLOR_ROSE_LOW_BATTERY)
            } else {
                val textColor = XposedHelpers.getIntField(instance, "mTextColor")
                if (textColor != 0) {
                    textView.setTextColor(textColor)
                }
            }
        }

        fun hideBatteryIcon(instance: Any) {
            val iconView = XposedHelpers.getObjectField(instance, "mBatteryIconView") as? View
            iconView?.visibility = View.GONE
        }

        // 1. Hook BatteryMeterView constructors to force percent mode and hide icon
        try {
            val clazz = XposedHelpers.findClass(batteryMeterViewClassName, classLoader)
            for (constructor in clazz.declaredConstructors) {
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                XposedHelpers.setIntField(param.thisObject, "mShowPercentMode", 1)
                                hideBatteryIcon(param.thisObject)
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in BatteryMeterView constructor hook", t)
                            }
                        }
                    }
                )
            }
            Log.d(TAG, "Installed hook: BatteryMeterView constructors")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BatteryMeterView constructors", t)
        }

        // 2. Hook onAttachedToWindow to ensure percent mode is forced and icon is hidden
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClassName,
                classLoader,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.setIntField(param.thisObject, "mShowPercentMode", 1)
                            hideBatteryIcon(param.thisObject)
                            applyHeartBattery(param.thisObject)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in BatteryMeterView.onAttachedToWindow hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: BatteryMeterView.onAttachedToWindow")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BatteryMeterView.onAttachedToWindow", t)
        }

        // 3. Hook updateShowPercent to ensure percent view is created and stock icon is hidden
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClassName,
                classLoader,
                "updateShowPercent",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.setIntField(param.thisObject, "mShowPercentMode", 1)
                            XposedHelpers.setBooleanField(param.thisObject, "mShowPercentAvailable", true)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in BatteryMeterView.updateShowPercent before hook", t)
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            hideBatteryIcon(param.thisObject)
                            applyHeartBattery(param.thisObject)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in BatteryMeterView.updateShowPercent after hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: BatteryMeterView.updateShowPercent")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BatteryMeterView.updateShowPercent", t)
        }

        // 4. Hook setPercentTextAtCurrentLevel to replace the percentage text with hearts and apply rose tint
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClassName,
                classLoader,
                "setPercentTextAtCurrentLevel",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            applyHeartBattery(param.thisObject)
                            param.result = null
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in BatteryMeterView.setPercentTextAtCurrentLevel hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: BatteryMeterView.setPercentTextAtCurrentLevel")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BatteryMeterView.setPercentTextAtCurrentLevel", t)
        }

        // 5. Hook updateColors to preserve low battery rose tint (#F06292) across dark-mode transitions
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClassName,
                classLoader,
                "updateColors",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val level = XposedHelpers.getIntField(param.thisObject, "mLevel")
                            val charging = XposedHelpers.getBooleanField(param.thisObject, "mCharging")
                            if (level <= 15 && !charging) {
                                val textView = XposedHelpers.getObjectField(param.thisObject, "mBatteryPercentView") as? TextView
                                textView?.setTextColor(COLOR_ROSE_LOW_BATTERY)
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in BatteryMeterView.updateColors hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: BatteryMeterView.updateColors")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BatteryMeterView.updateColors", t)
        }

        // 6. Hook scaleBatteryMeterViews to keep mBatteryIconView hidden when configuration/density changes
        try {
            XposedHelpers.findAndHookMethod(
                batteryMeterViewClassName,
                classLoader,
                "scaleBatteryMeterViews",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            hideBatteryIcon(param.thisObject)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in BatteryMeterView.scaleBatteryMeterViews hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: BatteryMeterView.scaleBatteryMeterViews")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BatteryMeterView.scaleBatteryMeterViews", t)
        }
    }

    /**
     * Stage C stub: squircle volume dialog in VolumeDialogImpl (do not activate yet).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun hookVolume(classLoader: ClassLoader) {
        try {
            Log.d(TAG, "hookVolume: Stage C stub registered (inactive until Stage C)")
        } catch (t: Throwable) {
            Log.d(TAG, "hookVolume failed", t)
        }
    }
}

/**
 * Pure helper for computing the 4-heart battery indicator string.
 * Kept independent of Xposed classes so it can be verified in standard JVM unit tests.
 */
internal object BatteryHeartFormatter {
    fun format(level: Int, charging: Boolean): String {
        val filled = if (level <= 0) 0 else Math.ceil(level / 25.0).toInt().coerceIn(0, 4)
        val empty = 4 - filled
        val hearts = StringBuilder(6)
        repeat(filled) { hearts.append('♥') }
        repeat(empty) { hearts.append('♡') }
        if (charging) {
            hearts.append(" ✦")
        }
        return hearts.toString()
    }
}
