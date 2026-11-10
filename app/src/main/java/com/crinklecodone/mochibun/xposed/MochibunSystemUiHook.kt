package com.crinklecodone.mochibun.xposed

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
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
 * - Stage C: Squircle volume dialog in VolumeDialogImpl (content views styled with #F6F0FC fill,
 *            20dp corner radius, 1.5dp #4A2E56 40% stroke; sliders tinted #FFB7D5 progress,
 *            #EADDF8 track, and #4A2E56 icons).
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

        // Stage C palette colors & pre-allocated tints
        // Dialog background fill #F6F0FC, 1.5dp stroke #4A2E56 at 40% alpha (0x664A2E56)
        private const val COLOR_VOLUME_BG_FILL = 0xFFF6F0FC.toInt()
        private const val COLOR_VOLUME_STROKE = 0x664A2E56.toInt()

        // Volume row tints
        private val VOLUME_PROGRESS_TINT_LIST = ColorStateList.valueOf(0xFFFFB7D5.toInt())
        private val VOLUME_TRACK_TINT_LIST = ColorStateList.valueOf(0xFFEADDF8.toInt())
        private val VOLUME_ICON_TINT_LIST = ColorStateList.valueOf(0xFF4A2E56.toInt())

        /**
         * Creates a squircle GradientDrawable:
         * - Corner radius: 20dp * density
         * - Fill: #F6F0FC
         * - Stroke: 1.5dp * density of #4A2E56 at 40% alpha
         */
        internal fun createSquircleDrawable(density: Float): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(COLOR_VOLUME_BG_FILL)
                val strokeWidth = (1.5f * density).toInt().coerceAtLeast(1)
                setStroke(strokeWidth, COLOR_VOLUME_STROKE)
            }
        }

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
     * Stage C: Squircle volume dialog in VolumeDialogImpl.
     * - Target VolumeDialogImpl.initDialog() (signature: private void initDialog()).
     * - Apply a GradientDrawable (corner radius 20dp * density, fill #F6F0FC, 1.5dp stroke #4A2E56 at 40% alpha)
     *   to dialog content views by id name (do NOT touch window background).
     * - Style volume rows (initRow & updateVolumeRowTintH):
     *   slider progress tint #FFB7D5, slider track tint #EADDF8, icons #4A2E56.
     * - Re-apply on updates without allocating drawables per update.
     */
    @android.annotation.SuppressLint("DiscouragedApi")
    private fun hookVolume(classLoader: ClassLoader) {
        val volumeDialogImplClassName = "com.android.systemui.volume.VolumeDialogImpl"

        // Apply Mochibun pastel styling to a VolumeRow
        fun styleVolumeRow(row: Any) {
            val slider = XposedHelpers.getObjectField(row, "slider") as? SeekBar
            val icon = XposedHelpers.getObjectField(row, "icon") as? ImageButton
            val sliderProgressSolid = XposedHelpers.getObjectField(row, "sliderProgressSolid") as? Drawable
            val sliderBgSolid = XposedHelpers.getObjectField(row, "sliderBgSolid") as? Drawable
            val sliderProgressIcon = XposedHelpers.getObjectField(row, "sliderProgressIcon") as? Drawable
            val sliderBgIcon = XposedHelpers.getObjectField(row, "sliderBgIcon") as? Drawable

            // Slider progress tint: #FFB7D5
            sliderProgressSolid?.setTintList(VOLUME_PROGRESS_TINT_LIST)
            slider?.progressTintList = VOLUME_PROGRESS_TINT_LIST

            // Slider track tint: #EADDF8
            sliderBgSolid?.setTintList(VOLUME_TRACK_TINT_LIST)
            slider?.progressBackgroundTintList = VOLUME_TRACK_TINT_LIST
            val progressDrawable = slider?.progressDrawable as? LayerDrawable
            progressDrawable?.findDrawableByLayerId(android.R.id.background)?.setTintList(VOLUME_TRACK_TINT_LIST)

            // Icons: deep plum #4A2E56
            icon?.imageTintList = VOLUME_ICON_TINT_LIST
            icon?.imageAlpha = 255
            sliderProgressIcon?.setTintList(VOLUME_ICON_TINT_LIST)
            sliderBgIcon?.setTintList(VOLUME_ICON_TINT_LIST)

            val number = XposedHelpers.getObjectField(row, "number") as? TextView
            number?.setTextColor(COLOR_DARK_MODE_DEEP_PLUM)
        }

        // Apply squircle GradientDrawable to content views found by ID name
        fun applySquircleToDialog(instance: Any) {
            val dialog = XposedHelpers.getObjectField(instance, "mDialog") as? Dialog ?: return
            val context = dialog.context
            val res = context.resources
            val density = res.displayMetrics.density

            fun findView(idName: String): View? {
                val id = res.getIdentifier(idName, "id", TARGET_PACKAGE)
                return if (id != 0) dialog.findViewById(id) else null
            }

            // Top container wraps ringer drawer, rows, and settings
            val topContainer = findView("volume_dialog_top_container")
            if (topContainer != null) {
                topContainer.background = createSquircleDrawable(density)
                topContainer.clipToOutline = true
            }

            // Set top/bottom cap backgrounds to transparent so topContainer forms a unified squircle.
            // NOTE: ringerDrawerContainer's background MUST be a LayerDrawable with >= 1 layers,
            // because VolumeDialogImpl.lambda$initDialog$5 explicitly casts
            // (LayerDrawable) this.mRingerAndDrawerContainer.getBackground()!
            val ringerDrawerContainer = findView("volume_ringer_and_drawer_container")
            ringerDrawerContainer?.background = LayerDrawable(arrayOf(ColorDrawable(0)))

            val settingsContainer = findView("settings_container")
            settingsContainer?.background = ColorDrawable(0)

            // Live caption button below top container
            val captionsView = findView("odi_captions")
            if (captionsView != null) {
                captionsView.background = createSquircleDrawable(density)
                captionsView.clipToOutline = true
            }

            // Standalone ringer button (if present)
            val ringerView = findView("ringer")
            if (ringerView != null) {
                ringerView.background = createSquircleDrawable(density)
                ringerView.clipToOutline = true
            }

            // Hide the 3-item ringer drawer container when closed so it does not add ~84dp of empty height
            val isDrawerOpen = try {
                XposedHelpers.getBooleanField(instance, "mIsRingerDrawerOpen")
            } catch (_: Throwable) {
                false
            }
            if (!isDrawerOpen) {
                val drawerContainer = findView("volume_drawer_container")
                drawerContainer?.visibility = View.GONE
            }

            // Tint ringer and settings icons to deep plum #4A2E56
            val settingsIcon = findView("settings") as? ImageButton
            settingsIcon?.imageTintList = VOLUME_ICON_TINT_LIST

            val ringerIcon = findView("ringer_icon") as? ImageButton
            ringerIcon?.imageTintList = VOLUME_ICON_TINT_LIST

            val ringerMuteIcon = findView("volume_drawer_mute_icon") as? ImageView
            ringerMuteIcon?.imageTintList = VOLUME_ICON_TINT_LIST

            val ringerVibrateIcon = findView("volume_drawer_vibrate_icon") as? ImageView
            ringerVibrateIcon?.imageTintList = VOLUME_ICON_TINT_LIST

            val ringerNormalIcon = findView("volume_drawer_normal_icon") as? ImageView
            ringerNormalIcon?.imageTintList = VOLUME_ICON_TINT_LIST

            val captionsIcon = findView("odi_captions_icon") as? ImageView
            captionsIcon?.imageTintList = VOLUME_ICON_TINT_LIST
        }

        val volumeRowClass = try {
            XposedHelpers.findClass(
                "com.android.systemui.volume.VolumeDialogImpl\$VolumeRow",
                classLoader
            )
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to find VolumeRow class", t)
            null
        }

        // 1. Hook initDialog() to style dialog content views with squircle drawables
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "initDialog",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.setBooleanField(param.thisObject, "mShowActiveStreamOnly", true)
                            applySquircleToDialog(param.thisObject)
                            Log.d(TAG, "Applied squircle backgrounds to VolumeDialogImpl content views")
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.initDialog hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.initDialog")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.initDialog", t)
        }

        // 2. Hook setTopContainerBackgroundDrawable() so subsequent layout calls do not overwrite squircle
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "setTopContainerBackgroundDrawable",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val topContainer = XposedHelpers.getObjectField(param.thisObject, "mTopContainer") as? View
                            if (topContainer != null) {
                                val density = topContainer.resources.displayMetrics.density
                                topContainer.background = createSquircleDrawable(density)
                                topContainer.clipToOutline = true
                            }
                            val ringerDrawer = XposedHelpers.getObjectField(param.thisObject, "mRingerAndDrawerContainer") as? View
                            ringerDrawer?.background = LayerDrawable(arrayOf(ColorDrawable(0)))
                            val settingsView = XposedHelpers.getObjectField(param.thisObject, "mSettingsView") as? View
                            settingsView?.background = ColorDrawable(0)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.setTopContainerBackgroundDrawable hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.setTopContainerBackgroundDrawable")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.setTopContainerBackgroundDrawable", t)
        }

        // 3. Hook initRow to style rows and scale slider height for 480x800 display
        if (volumeRowClass != null) {
            try {
                XposedHelpers.findAndHookMethod(
                    volumeDialogImplClassName,
                    classLoader,
                    "initRow",
                    volumeRowClass,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val row = param.args[0] ?: return
                                styleVolumeRow(row)

                                val rowView = XposedHelpers.getObjectField(row, "view") as? View
                                rowView?.background = ColorDrawable(0)

                                val res = rowView?.resources ?: return
                                val density = res.displayMetrics.density
                                val sliderFrameId = res.getIdentifier("volume_row_slider_frame", "id", TARGET_PACKAGE)
                                val sliderFrame = if (sliderFrameId != 0) rowView.findViewById<View>(sliderFrameId) else null
                                val slider = XposedHelpers.getObjectField(row, "slider") as? SeekBar

                                // Scale down from stock 192dp to 128dp for compact 480x800 display
                                val targetHeight = (128f * density).toInt()
                                sliderFrame?.layoutParams?.let { lp ->
                                    lp.height = targetHeight
                                    sliderFrame.layoutParams = lp
                                }
                                slider?.layoutParams?.let { lp ->
                                    lp.width = targetHeight
                                    slider.layoutParams = lp
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in VolumeDialogImpl.initRow hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: VolumeDialogImpl.initRow")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook VolumeDialogImpl.initRow", t)
            }

            // 4. Hook updateVolumeRowTintH to re-apply custom tints whenever SystemUI re-tints sliders
            try {
                XposedHelpers.findAndHookMethod(
                    volumeDialogImplClassName,
                    classLoader,
                    "updateVolumeRowTintH",
                    volumeRowClass,
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val row = param.args[0]
                                if (row != null) {
                                    styleVolumeRow(row)
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in VolumeDialogImpl.updateVolumeRowTintH hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: VolumeDialogImpl.updateVolumeRowTintH")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook VolumeDialogImpl.updateVolumeRowTintH", t)
            }

            // 5. Hook shouldBeVisibleH to enforce strictly single active slider
            try {
                XposedHelpers.findAndHookMethod(
                    volumeDialogImplClassName,
                    classLoader,
                    "shouldBeVisibleH",
                    volumeRowClass,
                    volumeRowClass,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val row = param.args[0]
                                val activeRow = param.args[1]
                                if (row != null && activeRow != null) {
                                    val rowStream = XposedHelpers.getIntField(row, "stream")
                                    val activeStream = XposedHelpers.getIntField(activeRow, "stream")
                                    param.result = (rowStream == activeStream)
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in VolumeDialogImpl.shouldBeVisibleH hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: VolumeDialogImpl.shouldBeVisibleH")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook VolumeDialogImpl.shouldBeVisibleH", t)
            }

            // 6. Hook updateRowsH to ensure all row views keep transparent backgrounds (no dark capsule)
            try {
                XposedHelpers.findAndHookMethod(
                    volumeDialogImplClassName,
                    classLoader,
                    "updateRowsH",
                    volumeRowClass,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val rows = XposedHelpers.getObjectField(param.thisObject, "mRows") as? List<*> ?: return
                                for (row in rows) {
                                    if (row != null) {
                                        val rowView = XposedHelpers.getObjectField(row, "view") as? View
                                        rowView?.background = ColorDrawable(0)
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in VolumeDialogImpl.updateRowsH hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: VolumeDialogImpl.updateRowsH")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook VolumeDialogImpl.updateRowsH", t)
            }
        }

        // 7. Hook showH to ensure drawer void remains GONE when dialog opens
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "showH",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val isDrawerOpen = XposedHelpers.getBooleanField(param.thisObject, "mIsRingerDrawerOpen")
                            if (!isDrawerOpen) {
                                val drawer = XposedHelpers.getObjectField(param.thisObject, "mRingerDrawerContainer") as? View
                                drawer?.visibility = View.GONE
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.showH hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.showH")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.showH", t)
        }

        // 8. Hook hideRingerDrawer to set drawer container back to GONE when closed
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "hideRingerDrawer",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val drawer = XposedHelpers.getObjectField(param.thisObject, "mRingerDrawerContainer") as? View
                            drawer?.visibility = View.GONE
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.hideRingerDrawer hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.hideRingerDrawer")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.hideRingerDrawer", t)
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
