package one.crinklecod.mochibun.xposed.systemui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.ref.WeakReference
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.ACTION_THEME_CHANGED
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.CARRIER_GREETING
import one.crinklecod.mochibun.xposed.systemui.SystemDialogsPowerHooks.findTextViewRecursive
import one.crinklecod.mochibun.xposed.systemui.SystemUiDrawables.createPearlLockDrawable
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedCarrierTexts
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedClockViews
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedEmergencyButtons
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedLockIconViews
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedMessageAreas
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedNotificationPanelView
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedNumPadButtons
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedNumPadKeys
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedPasswordViews
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedScrimControllers

/** Stage F/G: keyguard clock/greeting, pearl lock icon, scrim safety, PIN bouncer skinning, and bouncer fade. */
internal object LockscreenHooks {

    /** Installs this module's hooks; called once by [MochibunSystemUiHook] after the kill-switch check. */
    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        hookLockscreen(classLoader)
        hookPinBouncer(classLoader)
    }

    // Pre-allocated static heart path and paints for zero-allocation PIN dot rendering
    internal val sNormalizedHeartPath: Path by lazy {
        Path().apply {
            moveTo(0f, 0.95f)
            cubicTo(-1.1f, 0.25f, -1.25f, -0.55f, -0.65f, -0.95f)
            cubicTo(-0.25f, -1.2f, 0f, -0.65f, 0f, -0.35f)
            cubicTo(0f, -0.65f, 0.25f, -1.2f, 0.65f, -0.95f)
            cubicTo(1.25f, -0.55f, 1.1f, 0.25f, 0f, 0.95f)
            close()
        }
    }
    private val sHeartFillPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }
    private val sHeartStrokePaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    /**
     * Stage F: Restyle Dreamy Keitai Lockscreen.
     * Transforms the stock Android 12 lockscreen into Mochibun's pastel keitai aesthetic:
     * - F1: Compact single-line clock layout in KeyguardClockSwitch & KeyguardClockSwitchController
     * - F2: Clock typography tinting using ActiveHookTheme.accentDeep in AnimatableClockView
     * - F3: Custom carrier greeting ("— ♡ Welcome home, emii ♡ —") in CarrierText
     * - F4: Pearl lock icon affordance with GradientDrawable.OVAL in LockIconView & LockIconViewController
     * - F5: MediaTek MT6761 GPU safety: ScrimController transparent pastel & 0px runtime blur
     */
    private fun hookLockscreen(classLoader: ClassLoader) {
        hookLockscreenClock(classLoader)
        hookLockscreenCarrierGreeting(classLoader)
        hookLockscreenLockIcon(classLoader)
        hookLockscreenScrimSafety(classLoader)
        hookLockscreenEmergencyButton(classLoader)
    }

    /**
     * Keeps the keyguard emergency call button hidden despite controller visibility updates.
     */
    private fun hookLockscreenEmergencyButton(classLoader: ClassLoader) {
        try {
            val emergencyButtonClass = XposedHelpers.findClass(
                "com.android.keyguard.EmergencyButton",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                View::class.java,
                "setVisibility",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            if (emergencyButtonClass.isInstance(param.thisObject)) {
                                param.args[0] = View.GONE
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error suppressing EmergencyButton visibility", t)
                        }
                    }
                }
            )

            try {
                val controllerClass = XposedHelpers.findClass(
                    "com.android.keyguard.EmergencyButtonController",
                    classLoader
                )
                XposedHelpers.findAndHookMethod(
                    controllerClass,
                    "updateEmergencyCallButton",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val button = XposedHelpers.getObjectField(param.thisObject, "mView") as? View
                                button?.visibility = View.GONE
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error hiding EmergencyButton after controller update", t)
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.d(TAG, "EmergencyButtonController hook unavailable", t)
            }

            try {
                val securityContainerClass = XposedHelpers.findClass(
                    "com.android.keyguard.KeyguardSecurityContainer",
                    classLoader
                )
                XposedHelpers.findAndHookMethod(
                    securityContainerClass,
                    "onFinishInflate",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                hideEmergencyButtonInContainer(param.thisObject as? View)
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error searching KeyguardSecurityContainer", t)
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.d(TAG, "KeyguardSecurityContainer fallback unavailable", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.android.systemui.statusbar.phone.KeyguardBouncer",
                    classLoader,
                    "ensureView",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val root = XposedHelpers.getObjectField(param.thisObject, "mRoot") as? View
                                hideEmergencyButtonInContainer(root)
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error searching KeyguardBouncer root", t)
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.d(TAG, "KeyguardBouncer fallback unavailable", t)
            }

            Log.d(TAG, "Installed hook: Suppress EmergencyButton")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to install EmergencyButton suppression hook", t)
        }
    }

    private fun hideEmergencyButtonInContainer(container: View?) {
        if (container == null) return
        val buttonId = container.resources.getIdentifier(
            "emergency_call_button",
            "id",
            "com.android.systemui"
        )
        if (buttonId != 0) {
            container.findViewById<View>(buttonId)?.visibility = View.GONE
        }
    }

    /**
     * F1 & F2: Enforces compact single-line lockscreen clock and tints typography to ActiveHookTheme.accentDeep.
     */
    private fun hookLockscreenClock(classLoader: ClassLoader) {
        // 1. Enforce compact single-line clock layout in KeyguardClockSwitch
        try {
            val clockSwitchClass = XposedHelpers.findClass(
                "com.android.keyguard.KeyguardClockSwitch",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                clockSwitchClass,
                "willSwitchToLargeClock",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            param.args[0] = true
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardClockSwitch.willSwitchToLargeClock hook", t)
                        }
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            param.result = false
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardClockSwitch.willSwitchToLargeClock after hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: KeyguardClockSwitch.willSwitchToLargeClock")

            XposedHelpers.findAndHookMethod(
                clockSwitchClass,
                "animateClockChange",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            param.args[0] = false
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardClockSwitch.animateClockChange hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: KeyguardClockSwitch.animateClockChange")

            XposedHelpers.findAndHookMethod(
                clockSwitchClass,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val clockSwitch = param.thisObject as? View ?: return
                            val largeClockFrame = try { XposedHelpers.getObjectField(clockSwitch, "mLargeClockFrame") as? View } catch (_: Throwable) { null }
                            largeClockFrame?.visibility = View.GONE
                            val clockFrame = try { XposedHelpers.getObjectField(clockSwitch, "mClockFrame") as? View } catch (_: Throwable) { null }
                            clockFrame?.visibility = View.VISIBLE
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardClockSwitch.onFinishInflate hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: KeyguardClockSwitch.onFinishInflate")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook KeyguardClockSwitch", t)
        }

        // 2. Prevent large clock switch in KeyguardClockSwitchController
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.keyguard.KeyguardClockSwitchController",
                classLoader,
                "setHasVisibleNotifications",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            param.args[0] = true
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardClockSwitchController.setHasVisibleNotifications hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: KeyguardClockSwitchController.setHasVisibleNotifications")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook KeyguardClockSwitchController", t)
        }

        // 3. Prevent large clock switch in KeyguardStatusViewController
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.keyguard.KeyguardStatusViewController",
                classLoader,
                "setHasVisibleNotifications",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            param.args[0] = true
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardStatusViewController.setHasVisibleNotifications hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: KeyguardStatusViewController.setHasVisibleNotifications")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook KeyguardStatusViewController", t)
        }

        // 4. Hook AnimatableClockView for single-line formatting and pastel typography tint
        try {
            val animatableClockClass = XposedHelpers.findClass(
                "com.android.keyguard.AnimatableClockView",
                classLoader
            )

            for (constructor in animatableClockClass.declaredConstructors) {
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val clockView = param.thisObject as? TextView ?: return
                                XposedHelpers.setBooleanField(clockView, "mIsSingleLine", true)
                                clockView.setTextColor(ActiveHookTheme.accentDeep)
                                trackedClockViews.add(clockView)
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in AnimatableClockView constructor hook", t)
                            }
                        }
                    }
                )
            }
            Log.d(TAG, "Installed hook: AnimatableClockView constructors")

            XposedHelpers.findAndHookMethod(
                animatableClockClass,
                "refreshFormat",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.setBooleanField(param.thisObject, "mIsSingleLine", true)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in AnimatableClockView.refreshFormat hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: AnimatableClockView.refreshFormat")

            XposedHelpers.findAndHookMethod(
                animatableClockClass,
                "setColors",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            param.args[1] = ActiveHookTheme.accentDeep
                            val clockView = param.thisObject as? TextView
                            clockView?.setTextColor(ActiveHookTheme.accentDeep)
                            if (clockView != null) trackedClockViews.add(clockView)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in AnimatableClockView.setColors hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: AnimatableClockView.setColors")

            try {
                XposedHelpers.findAndHookMethod(
                    animatableClockClass,
                    "setTextStyle",
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Int::class.javaObjectType,
                    Boolean::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Runnable::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                if (param.args[2] != null) {
                                    param.args[2] = ActiveHookTheme.accentDeep
                                }
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in AnimatableClockView.setTextStyle hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: AnimatableClockView.setTextStyle")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook AnimatableClockView.setTextStyle", t)
            }

            XposedHelpers.findAndHookMethod(
                animatableClockClass,
                "onDraw",
                android.graphics.Canvas::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val clockView = param.thisObject as? TextView ?: return
                            trackedClockViews.add(clockView)
                            val textAnimator = try { XposedHelpers.getObjectField(clockView, "mTextAnimator") } catch (_: Throwable) { null }
                            if (textAnimator != null) {
                                val interpolator = try { XposedHelpers.getObjectField(textAnimator, "textInterpolator") } catch (_: Throwable) { null }
                                if (interpolator != null) {
                                    val targetPaint = try { XposedHelpers.getObjectField(interpolator, "targetPaint") as? android.graphics.Paint } catch (_: Throwable) { null }
                                    val basePaint = try { XposedHelpers.getObjectField(interpolator, "basePaint") as? android.graphics.Paint } catch (_: Throwable) { null }
                                    val dozingColor = try { XposedHelpers.getIntField(clockView, "mDozingColor") } catch (_: Throwable) { -1 }
                                    if (targetPaint != null && targetPaint.color != dozingColor) {
                                        targetPaint.color = ActiveHookTheme.accentDeep
                                    }
                                    if (basePaint != null && basePaint.color != dozingColor) {
                                        basePaint.color = ActiveHookTheme.accentDeep
                                    }
                                }
                            }
                            clockView.setTextColor(ActiveHookTheme.accentDeep)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in AnimatableClockView.onDraw hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: AnimatableClockView.onDraw")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook AnimatableClockView", t)
        }

        // 5. Hook AnimatableClockController.initColors
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.keyguard.AnimatableClockController",
                classLoader,
                "initColors",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val clockView = try { XposedHelpers.getObjectField(param.thisObject, "mView") } catch (_: Throwable) { null }
                            if (clockView != null) {
                                try {
                                    XposedHelpers.callMethod(clockView, "setColors", -1, ActiveHookTheme.accentDeep)
                                } catch (_: Throwable) {}
                                (clockView as? TextView)?.setTextColor(ActiveHookTheme.accentDeep)
                                if (clockView is TextView) trackedClockViews.add(clockView)
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in AnimatableClockController.initColors hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: AnimatableClockController.initColors")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook AnimatableClockController", t)
        }
    }

    /**
     * F3: Ensures CarrierTextController displays Mochibun's signature greeting.
     */
    private fun hookLockscreenCarrierGreeting(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.keyguard.CarrierTextController",
                classLoader,
                "onViewAttached",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val carrierText = try { XposedHelpers.getObjectField(param.thisObject, "mView") as? View } catch (_: Throwable) { null } ?: return
                            carrierText.visibility = View.VISIBLE
                            val tv = carrierText as? TextView ?: findTextViewRecursive(carrierText)
                            if (tv != null) {
                                tv.text = CARRIER_GREETING
                                tv.setTextColor(ActiveHookTheme.accentDeep)
                                trackedCarrierTexts.add(tv)
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in CarrierTextController.onViewAttached hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: CarrierTextController.onViewAttached")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook CarrierTextController.onViewAttached", t)
        }
    }

    /**
     * F4: Styles Keyguard LockIconView into a dreamy pearl oval container with sakura pink icon tint.
     */
    private fun hookLockscreenLockIcon(classLoader: ClassLoader) {
        // 1. Hook LockIconView methods
        try {
            val lockIconViewClass = XposedHelpers.findClass(
                "com.android.keyguard.LockIconView",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                lockIconViewClass,
                "updateColorAndBackgroundVisibility",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View ?: return
                            trackedLockIconViews.add(view)
                            styleLockIconView(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in LockIconView.updateColorAndBackgroundVisibility hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: LockIconView.updateColorAndBackgroundVisibility")

            XposedHelpers.findAndHookMethod(
                lockIconViewClass,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View ?: return
                            trackedLockIconViews.add(view)
                            styleLockIconView(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in LockIconView.onFinishInflate hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: LockIconView.onFinishInflate")

            XposedHelpers.findAndHookMethod(
                lockIconViewClass,
                "setImageDrawable",
                Drawable::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View ?: return
                            val lockIcon = try { XposedHelpers.getObjectField(view, "mLockIcon") as? ImageView } catch (_: Throwable) { null }
                            val primaryTint = ActiveHookTheme.primaryColorStateList
                                ?: ColorStateList.valueOf(ActiveHookTheme.primary)
                            lockIcon?.imageTintList = primaryTint
                            lockIcon?.setColorFilter(ActiveHookTheme.primary, PorterDuff.Mode.SRC_IN)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in LockIconView.setImageDrawable hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: LockIconView.setImageDrawable")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook LockIconView", t)
        }

        // 2. Hook LockIconViewController
        try {
            val controllerClass = XposedHelpers.findClass(
                "com.android.keyguard.LockIconViewController",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                controllerClass,
                "updateColors",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = try { XposedHelpers.getObjectField(param.thisObject, "mView") as? View } catch (_: Throwable) { null } ?: return
                            trackedLockIconViews.add(view)
                            styleLockIconView(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in LockIconViewController.updateColors hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: LockIconViewController.updateColors")

            XposedHelpers.findAndHookMethod(
                controllerClass,
                "updateVisibility",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = try { XposedHelpers.getObjectField(param.thisObject, "mView") as? View } catch (_: Throwable) { null } ?: return
                            trackedLockIconViews.add(view)
                            styleLockIconView(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in LockIconViewController.updateVisibility hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: LockIconViewController.updateVisibility")

            XposedHelpers.findAndHookMethod(
                controllerClass,
                "onViewAttached",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = try { XposedHelpers.getObjectField(param.thisObject, "mView") as? View } catch (_: Throwable) { null } ?: return
                            trackedLockIconViews.add(view)
                            styleLockIconView(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in LockIconViewController.onViewAttached hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: LockIconViewController.onViewAttached")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook LockIconViewController", t)
        }
    }

    /**
     * Styles the Keyguard Lock Icon into Mochibun's pearl affordance:
     * - Background: GradientDrawable.OVAL with container fill and 1.2dp accentDeep 30% border stroke
     * - Foreground lock icon: tinted to ActiveHookTheme.primary (Sakura pink)
     */
    internal fun styleLockIconView(view: View) {
        try {
            val density = view.resources.displayMetrics.density
            val pearlDrawable = createPearlLockDrawable(density)

            val lockIcon = try { XposedHelpers.getObjectField(view, "mLockIcon") as? ImageView } catch (_: Throwable) { null }
                ?: (try {
                    val lockIconId = view.resources.getIdentifier("lock_icon", "id", TARGET_PACKAGE)
                    if (lockIconId != 0) view.findViewById(lockIconId) else null
                } catch (_: Throwable) { null })

            val bgView = try { XposedHelpers.getObjectField(view, "mUnlockBgView") as? ImageView } catch (_: Throwable) { null }
                ?: (try {
                    val bgId = view.resources.getIdentifier("lock_icon_bg", "id", TARGET_PACKAGE)
                    if (bgId != 0) view.findViewById(bgId) else null
                } catch (_: Throwable) { null })

            if (bgView != null) {
                bgView.background = pearlDrawable
                bgView.visibility = View.VISIBLE
            } else {
                view.background = pearlDrawable
            }

            if (lockIcon != null) {
                val primaryTint = ActiveHookTheme.primaryColorStateList
                    ?: ColorStateList.valueOf(ActiveHookTheme.primary)
                lockIcon.imageTintList = primaryTint
                lockIcon.setColorFilter(ActiveHookTheme.primary, PorterDuff.Mode.SRC_IN)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Error in styleLockIconView", t)
        }
    }

    /**
     * F5: Scrim safety for lockscreen on MT6761 GPU.
     * Ensures ScrimController instances are registered and tinted in pastel ActiveHookTheme.scrim without GPU blurs.
     */
    private fun hookLockscreenScrimSafety(classLoader: ClassLoader) {
        try {
            val scrimControllerClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.ScrimController",
                classLoader
            )
            val scrimStateClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.ScrimState",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                scrimControllerClass,
                "transitionTo",
                scrimStateClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            trackedScrimControllers.add(param.thisObject)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in ScrimController.transitionTo hook", t)
                        }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val state = param.args[0]
                            val stateName = state?.toString().orEmpty()
                            if (stateName.contains("BOUNCER")) {
                                XposedHelpers.setFloatField(param.thisObject, "mBehindAlpha", 0.0f)
                                XposedHelpers.setFloatField(param.thisObject, "mInFrontAlpha", 0.0f)
                                XposedHelpers.setFloatField(param.thisObject, "mNotificationsAlpha", 0.0f)
                                XposedHelpers.setIntField(param.thisObject, "mBehindTint", 0)
                                XposedHelpers.setIntField(param.thisObject, "mInFrontTint", 0)
                                XposedHelpers.setIntField(param.thisObject, "mNotificationsTint", 0)
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in ScrimController.transitionTo after hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: ScrimController.transitionTo")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook ScrimController for Lockscreen", t)
        }
    }

    /**
     * Stage G: PIN Bouncer hooks.
     * Restyles KeyguardPINView with:
     * - Circular pearl keys and action buttons (0-9, delete, enter) with Soft Cream container fill,
     *   1.2dp border in lockPearlStroke, and Sakura Pink highlight ripple.
     * - Kawaii heart dots in PasswordTextView instead of default circles.
     * - Pill-shaped EmergencyButton matching the pearl theme.
     * - KeyguardMessageArea tinted with ActiveHookTheme.accentDeep.
     * - Live dynamic theme synchronization via ACTION_THEME_CHANGED.
     */
    private fun hookPinBouncer(classLoader: ClassLoader) {
        try {
            // G1: Heart dots in PasswordTextView
            hookPasswordTextView(classLoader)

            // G2: Circular pearl keys in NumPadKey
            hookNumPadKey(classLoader)

            // G3: Circular pearl action buttons in NumPadButton
            hookNumPadButton(classLoader)

            // G4: NumPadAnimator reloadColors override
            hookNumPadAnimator(classLoader)

            // G5: KeyguardPinBasedInputView tracking & styling
            hookKeyguardPinView(classLoader)

            // G6: Emergency call button styling
            hookEmergencyButton(classLoader)

            // G7: Keyguard message area styling
            hookKeyguardMessageArea(classLoader)

            // G8: Allow screencap on PIN bouncer by making NotificationShadeWindowControllerImpl.isDebuggable() return true
            hookBouncerSecureFlag(classLoader)

            // G9: Fade lockscreen clock and notifications during PIN bouncer showing
            hookLockscreenBouncerFade(classLoader)

            Log.d(TAG, "Successfully installed PIN bouncer hooks")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook PIN bouncer", t)
        }
    }

    /**
     * G1: Kawaii heart dots in PasswordTextView.
     * Replaces CharState.draw() to draw a chubby kawaii heart marker instead of canvas.drawCircle().
     * Preserves dot pop-in/blossom and removal scaling animations via currentDotSizeFactor.
     */
    private fun hookPasswordTextView(classLoader: ClassLoader) {
        try {
            val charStateClass = XposedHelpers.findClass(
                "com.android.keyguard.PasswordTextView\$CharState",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                charStateClass,
                "draw",
                Canvas::class.java,
                java.lang.Float.TYPE,
                java.lang.Integer.TYPE,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE,
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        try {
                            val charState = param.thisObject
                            val ptv = try {
                                XposedHelpers.getSurroundingThis(charState) as? View
                            } catch (_: Throwable) {
                                try {
                                    XposedHelpers.getObjectField(charState, "this$0") as? View
                                } catch (_: Throwable) {
                                    null
                                }
                            }
                            if (ptv != null) {
                                trackedPasswordViews.add(ptv)
                            }

                            val canvas = param.args[0] as Canvas
                            val f = (param.args[1] as Number).toFloat()
                            val i = (param.args[2] as Number).toInt()
                            val f2 = (param.args[3] as Number).toFloat()
                            val f3 = (param.args[4] as Number).toFloat()

                            val currentTextSizeFactor = try { XposedHelpers.getFloatField(charState, "currentTextSizeFactor") } catch (_: Throwable) { 0f }
                            val currentDotSizeFactor = try { XposedHelpers.getFloatField(charState, "currentDotSizeFactor") } catch (_: Throwable) { 0f }
                            val currentWidthFactor = try { XposedHelpers.getFloatField(charState, "currentWidthFactor") } catch (_: Throwable) { 1f }
                            val currentTextTranslationY = try { XposedHelpers.getFloatField(charState, "currentTextTranslationY") } catch (_: Throwable) { 0f }
                            val whichChar = try { XposedHelpers.getCharField(charState, "whichChar") } catch (_: Throwable) { '•' }

                            val drawPaint = if (ptv != null) {
                                try { XposedHelpers.getObjectField(ptv, "mDrawPaint") as? Paint } catch (_: Throwable) { null }
                            } else null

                            val dotSize = if (ptv != null) {
                                try { XposedHelpers.getIntField(ptv, "mDotSize") } catch (_: Throwable) { 24 }
                            } else 24

                            val charPadding = if (ptv != null) {
                                try { XposedHelpers.getIntField(ptv, "mCharPadding") } catch (_: Throwable) { 16 }
                            } else 16

                            val hasText = currentTextSizeFactor > 0f
                            val hasDot = currentDotSizeFactor > 0f
                            val charWidth = f3 * currentWidthFactor

                            if (hasText && drawPaint != null) {
                                val textY = ((i / 2f) * currentTextSizeFactor) + f2 + (i * currentTextTranslationY * 0.8f)
                                canvas.save()
                                canvas.translate((charWidth / 2f) + f, textY)
                                canvas.scale(currentTextSizeFactor, currentTextSizeFactor)
                                drawPaint.color = ActiveHookTheme.accentDeep
                                canvas.drawText(whichChar.toString(), 0f, 0f, drawPaint)
                                canvas.restore()
                            }

                            if (hasDot) {
                                val r = (dotSize / 2f) * currentDotSizeFactor * 1.15f
                                if (r > 0.05f) {
                                    canvas.save()
                                    canvas.translate(f + (charWidth / 2f), f2)
                                    canvas.scale(r, r)

                                    // Draw filled kawaii heart in Sakura Pink
                                    sHeartFillPaint.color = ActiveHookTheme.primary
                                    canvas.drawPath(sNormalizedHeartPath, sHeartFillPaint)

                                    // Draw delicate pearl outline in Deep Plum / lockPearlStroke
                                    sHeartStrokePaint.color = ActiveHookTheme.lockPearlStroke
                                    canvas.drawPath(sNormalizedHeartPath, sHeartStrokePaint)

                                    canvas.restore()
                                }
                            }

                            return charWidth + (charPadding * currentWidthFactor)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in CharState.draw replacement", t)
                            return try { (param.args[4] as Number).toFloat() } catch (_: Throwable) { 0f }
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: PasswordTextView\$CharState.draw")

            val ptvClass = XposedHelpers.findClass(
                "com.android.keyguard.PasswordTextView",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                ptvClass,
                "reloadColors",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val ptv = param.thisObject as? View ?: return
                            trackedPasswordViews.add(ptv)
                            val paint = try { XposedHelpers.getObjectField(ptv, "mDrawPaint") as? Paint } catch (_: Throwable) { null }
                            paint?.color = ActiveHookTheme.accentDeep
                            ptv.invalidate()
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in PasswordTextView.reloadColors hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: PasswordTextView.reloadColors")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook PasswordTextView", t)
        }
    }

    /**
     * G2: Circular pearl keys in NumPadKey.
     * Hooks reloadColors() to apply ActiveHookTheme styling.
     */
    private fun hookNumPadKey(classLoader: ClassLoader) {
        try {
            val numPadKeyClass = XposedHelpers.findClass(
                "com.android.keyguard.NumPadKey",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                numPadKeyClass,
                "reloadColors",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val key = param.thisObject as? View ?: return
                            trackedNumPadKeys.add(key)
                            styleNumPadKey(key)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in NumPadKey.reloadColors hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: NumPadKey.reloadColors")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NumPadKey", t)
        }
    }

    /**
     * G3: Circular pearl action buttons in NumPadButton (delete, enter).
     */
    private fun hookNumPadButton(classLoader: ClassLoader) {
        try {
            val numPadButtonClass = XposedHelpers.findClass(
                "com.android.keyguard.NumPadButton",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                numPadButtonClass,
                "reloadColors",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val button = param.thisObject as? ImageView ?: return
                            trackedNumPadButtons.add(button)
                            styleNumPadButton(button)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in NumPadButton.reloadColors hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: NumPadButton.reloadColors")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NumPadButton", t)
        }
    }

    /**
     * G4: NumPadAnimator reloadColors override.
     * Hooks NumPadAnimator.reloadColors(Context) to ensure the background GradientDrawable
     * and RippleDrawable are initialized with ActiveHookTheme container fill, pearl stroke, and primary ripple.
     */
    private fun hookNumPadAnimator(classLoader: ClassLoader) {
        try {
            val numPadAnimatorClass = XposedHelpers.findClass(
                "com.android.keyguard.NumPadAnimator",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                numPadAnimatorClass,
                "reloadColors",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val animator = param.thisObject
                            val context = param.args[0] as? Context ?: return
                            val bg = try { XposedHelpers.getObjectField(animator, "mBackground") as? GradientDrawable } catch (_: Throwable) { null } ?: return
                            val ripple = try { XposedHelpers.getObjectField(animator, "mRipple") as? RippleDrawable } catch (_: Throwable) { null }

                            val density = context.resources.displayMetrics.density
                            val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)

                            bg.setColor(ActiveHookTheme.container)
                            bg.setStroke(strokeWidth, ActiveHookTheme.lockPearlStroke)
                            ripple?.setColor(ColorStateList.valueOf(ActiveHookTheme.primary))
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in NumPadAnimator.reloadColors hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: NumPadAnimator.reloadColors")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NumPadAnimator", t)
        }
    }

    /**
     * G5: KeyguardPinBasedInputView tracking & styling.
     * Hooks onFinishInflate() and reloadColors() on the root PIN container to discover
     * all keys, action buttons, and PasswordTextView, ensuring immediate and consistent styling.
     */
    private fun hookKeyguardPinView(classLoader: ClassLoader) {
        try {
            val pinViewClass = XposedHelpers.findClass(
                "com.android.keyguard.KeyguardPinBasedInputView",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                pinViewClass,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View ?: return
                            val ptv = try { XposedHelpers.getObjectField(view, "mPasswordEntry") as? View } catch (_: Throwable) { null }
                            if (ptv != null) trackedPasswordViews.add(ptv)

                            val okButton = try { XposedHelpers.getObjectField(view, "mOkButton") as? ImageView } catch (_: Throwable) { null }
                            if (okButton != null) {
                                trackedNumPadButtons.add(okButton)
                                styleNumPadButton(okButton)
                            }

                            val deleteButton = try { XposedHelpers.getObjectField(view, "mDeleteButton") as? ImageView } catch (_: Throwable) { null }
                            if (deleteButton != null) {
                                trackedNumPadButtons.add(deleteButton)
                                styleNumPadButton(deleteButton)
                            }

                            val buttons = try { XposedHelpers.getObjectField(view, "mButtons") as? Array<*> } catch (_: Throwable) { null }
                            if (buttons != null) {
                                for (btn in buttons) {
                                    val keyView = btn as? View ?: continue
                                    trackedNumPadKeys.add(keyView)
                                    styleNumPadKey(keyView)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardPinBasedInputView.onFinishInflate hook", t)
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                pinViewClass,
                "reloadColors",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View ?: return
                            val okButton = try { XposedHelpers.getObjectField(view, "mOkButton") as? ImageView } catch (_: Throwable) { null }
                            if (okButton != null) styleNumPadButton(okButton)

                            val deleteButton = try { XposedHelpers.getObjectField(view, "mDeleteButton") as? ImageView } catch (_: Throwable) { null }
                            if (deleteButton != null) styleNumPadButton(deleteButton)

                            val buttons = try { XposedHelpers.getObjectField(view, "mButtons") as? Array<*> } catch (_: Throwable) { null }
                            if (buttons != null) {
                                for (btn in buttons) {
                                    val keyView = btn as? View ?: continue
                                    styleNumPadKey(keyView)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardPinBasedInputView.reloadColors hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hooks: KeyguardPinBasedInputView.onFinishInflate and reloadColors")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook KeyguardPinBasedInputView", t)
        }
    }

    /**
     * G6: Emergency call button styling.
     */
    private fun hookEmergencyButton(classLoader: ClassLoader) {
        try {
            val emergencyButtonClass = XposedHelpers.findClass(
                "com.android.keyguard.EmergencyButton",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                emergencyButtonClass,
                "reloadColors",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val button = param.thisObject as? TextView ?: return
                            trackedEmergencyButtons.add(button)
                            styleEmergencyButton(button)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in EmergencyButton.reloadColors hook", t)
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                emergencyButtonClass,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val button = param.thisObject as? TextView ?: return
                            trackedEmergencyButtons.add(button)
                            styleEmergencyButton(button)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in EmergencyButton.onFinishInflate hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hooks: EmergencyButton.reloadColors and onFinishInflate")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook EmergencyButton", t)
        }
    }

    /**
     * G7: Keyguard message area styling.
     */
    private fun hookKeyguardMessageArea(classLoader: ClassLoader) {
        try {
            val messageAreaClass = XposedHelpers.findClass(
                "com.android.keyguard.KeyguardMessageArea",
                classLoader
            )

            XposedHelpers.findAndHookMethod(
                messageAreaClass,
                "reloadColor",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val messageArea = param.thisObject as? TextView ?: return
                            trackedMessageAreas.add(messageArea)
                            styleMessageArea(messageArea)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardMessageArea.reloadColor hook", t)
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                messageAreaClass,
                "onThemeChanged",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val messageArea = param.thisObject as? TextView ?: return
                            trackedMessageAreas.add(messageArea)
                            styleMessageArea(messageArea)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardMessageArea.onThemeChanged hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hooks: KeyguardMessageArea.reloadColor and onThemeChanged")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook KeyguardMessageArea", t)
        }
    }

    /**
     * G8: Allows screencap on PIN bouncer by making NotificationShadeWindowControllerImpl.isDebuggable() return true.
     */
    private fun hookBouncerSecureFlag(classLoader: ClassLoader) {
        try {
            val controllerClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.NotificationShadeWindowControllerImpl",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                controllerClass,
                "isDebuggable",
                XC_MethodReplacement.returnConstant(true)
            )
            Log.d(TAG, "Installed hook: NotificationShadeWindowControllerImpl.isDebuggable")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationShadeWindowControllerImpl.isDebuggable", t)
        }
    }

    /**
     * Resolves the NotificationPanelView instance (the lockscreen clock, notifications,
     * lock icon, and shade container) either from the cached weak reference or by walking
     * the container hierarchy of the KeyguardBouncer.
     */
    private fun getNotificationPanelView(bouncerObj: Any? = null): View? {
        trackedNotificationPanelView?.get()?.let { return it }
        if (bouncerObj != null) {
            try {
                val container = XposedHelpers.getObjectField(bouncerObj, "mContainer") as? ViewGroup
                if (container != null) {
                    val res = container.resources
                    val panelId = res.getIdentifier("notification_panel", "id", TARGET_PACKAGE)
                    if (panelId != 0) {
                        val panel = container.findViewById<View>(panelId)
                        if (panel != null) {
                            trackedNotificationPanelView = WeakReference(panel)
                            return panel
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Error finding NotificationPanelView from bouncer container", t)
            }
        }
        return null
    }

    private fun updateLockscreenBouncerFade(expansion: Float, bouncerObj: Any? = null) {
        try {
            val panel = getNotificationPanelView(bouncerObj) ?: return
            val targetAlpha = ActiveHookTheme.calculateBouncerFadeAlpha(expansion)
            panel.alpha = targetAlpha
        } catch (t: Throwable) {
            Log.d(TAG, "Error updating lockscreen bouncer fade", t)
        }
    }

    /**
     * G9: Fades lockscreen clock, notifications, and lock affordance (NotificationPanelView)
     * during PIN bouncer showing so only the wallpaper and floating PIN pad remain visible.
     */
    private fun hookLockscreenBouncerFade(classLoader: ClassLoader) {
        // 1. Capture NotificationPanelView when constructed
        try {
            val panelViewClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.NotificationPanelView",
                classLoader
            )
            XposedBridge.hookAllConstructors(
                panelViewClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View ?: return
                            trackedNotificationPanelView = WeakReference(view)
                            Log.d(TAG, "Tracked NotificationPanelView from constructor")
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in NotificationPanelView constructor hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: NotificationPanelView constructor")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelView constructors", t)
        }

        // 2. Capture NotificationPanelView from NotificationShadeWindowView.onFinishInflate
        try {
            val windowViewClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.NotificationShadeWindowView",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                windowViewClass,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val windowView = param.thisObject as? ViewGroup ?: return
                            val res = windowView.resources
                            val panelId = res.getIdentifier("notification_panel", "id", TARGET_PACKAGE)
                            if (panelId != 0) {
                                val panel = windowView.findViewById<View>(panelId)
                                if (panel != null) {
                                    trackedNotificationPanelView = WeakReference(panel)
                                    Log.d(TAG, "Tracked NotificationPanelView from NotificationShadeWindowView.onFinishInflate")
                                }
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in NotificationShadeWindowView.onFinishInflate hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: NotificationShadeWindowView.onFinishInflate")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationShadeWindowView.onFinishInflate", t)
        }

        // 3. Smoothly fade NotificationPanelView during bouncer expansion / swipe gesture
        try {
            val bouncerClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.KeyguardBouncer",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                bouncerClass,
                "setExpansion",
                Float::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val expansion = param.args[0] as? Float ?: return
                            updateLockscreenBouncerFade(expansion, param.thisObject)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardBouncer.setExpansion hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: KeyguardBouncer.setExpansion")

            // Ensure alpha is fully restored when bouncer is hidden
            XposedHelpers.findAndHookMethod(
                bouncerClass,
                "hide",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            updateLockscreenBouncerFade(1.0f, param.thisObject)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in KeyguardBouncer.hide hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: KeyguardBouncer.hide")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook KeyguardBouncer expansion/hide", t)
        }

        // 4. Hook StatusBar.setBouncerShowing to ensure fade state is enforced on all show/hide transitions
        try {
            val statusBarClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.StatusBar",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                statusBarClass,
                "setBouncerShowing",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val isShowing = param.args[0] as? Boolean ?: return
                            val targetAlpha = if (isShowing) 0.0f else 1.0f
                            updateLockscreenBouncerFade(targetAlpha)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in StatusBar.setBouncerShowing hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: StatusBar.setBouncerShowing")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook StatusBar.setBouncerShowing", t)
        }
    }

    /**
     * Styles a NumPadKey with ActiveHookTheme colors:
     * - Digit text: ActiveHookTheme.accentDeep
     * - Klondike (letter) text: ActiveHookTheme.qsTileInactiveFg (accentDeep 60% alpha)
     * - Pearl background: ActiveHookTheme.container fill + 1.2dp ActiveHookTheme.lockPearlStroke border
     * - Ripple: ActiveHookTheme.primary
     */
    internal fun styleNumPadKey(key: View) {
        try {
            val digitText = try { XposedHelpers.getObjectField(key, "mDigitText") as? TextView } catch (_: Throwable) { null }
                ?: (try {
                    val digitId = key.resources.getIdentifier("digit_text", "id", TARGET_PACKAGE)
                    if (digitId != 0) key.findViewById(digitId) else null
                } catch (_: Throwable) { null })

            val klondikeText = try { XposedHelpers.getObjectField(key, "mKlondikeText") as? TextView } catch (_: Throwable) { null }
                ?: (try {
                    val klondikeId = key.resources.getIdentifier("klondike_text", "id", TARGET_PACKAGE)
                    if (klondikeId != 0) key.findViewById(klondikeId) else null
                } catch (_: Throwable) { null })

            digitText?.setTextColor(ActiveHookTheme.accentDeep)
            klondikeText?.setTextColor(ActiveHookTheme.qsTileInactiveFg)

            val animator = try { XposedHelpers.getObjectField(key, "mAnimator") } catch (_: Throwable) { null }
            if (animator != null) {
                val bg = try { XposedHelpers.getObjectField(animator, "mBackground") as? GradientDrawable } catch (_: Throwable) { null }
                val ripple = try { XposedHelpers.getObjectField(animator, "mRipple") as? RippleDrawable } catch (_: Throwable) { null }
                val density = key.resources.displayMetrics.density
                val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)

                bg?.setColor(ActiveHookTheme.container)
                bg?.setStroke(strokeWidth, ActiveHookTheme.lockPearlStroke)
                ripple?.setColor(ColorStateList.valueOf(ActiveHookTheme.primary))
            } else {
                val bg = key.background
                if (bg is RippleDrawable) {
                    bg.setColor(ColorStateList.valueOf(ActiveHookTheme.primary))
                    val innerBg = bg.findDrawableByLayerId(android.R.id.background) as? GradientDrawable
                    if (innerBg != null) {
                        val density = key.resources.displayMetrics.density
                        val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
                        innerBg.setColor(ActiveHookTheme.container)
                        innerBg.setStroke(strokeWidth, ActiveHookTheme.lockPearlStroke)
                    }
                }
            }
            key.invalidate()
        } catch (t: Throwable) {
            Log.d(TAG, "Error styling NumPadKey", t)
        }
    }

    /**
     * Styles a NumPadButton (delete, enter) with pearl circle container and themed action icon.
     */
    internal fun styleNumPadButton(button: ImageView) {
        try {
            val animator = try { XposedHelpers.getObjectField(button, "mAnimator") } catch (_: Throwable) { null }
            if (animator != null) {
                val bg = try { XposedHelpers.getObjectField(animator, "mBackground") as? GradientDrawable } catch (_: Throwable) { null }
                val ripple = try { XposedHelpers.getObjectField(animator, "mRipple") as? RippleDrawable } catch (_: Throwable) { null }
                val density = button.resources.displayMetrics.density
                val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)

                bg?.setColor(ActiveHookTheme.container)
                bg?.setStroke(strokeWidth, ActiveHookTheme.lockPearlStroke)
                ripple?.setColor(ColorStateList.valueOf(ActiveHookTheme.primary))
            } else {
                val bg = button.background
                if (bg is RippleDrawable) {
                    bg.setColor(ColorStateList.valueOf(ActiveHookTheme.primary))
                    val innerBg = bg.findDrawableByLayerId(android.R.id.background) as? GradientDrawable
                    if (innerBg != null) {
                        val density = button.resources.displayMetrics.density
                        val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
                        innerBg.setColor(ActiveHookTheme.container)
                        innerBg.setStroke(strokeWidth, ActiveHookTheme.lockPearlStroke)
                    }
                }
            }

            val buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf()
                ),
                intArrayOf(
                    ActiveHookTheme.qsTileUnavailableFg,
                    ActiveHookTheme.accentDeep
                )
            )
            button.imageTintList = buttonTintList
            try {
                button.drawable?.setTintList(buttonTintList)
            } catch (_: Throwable) {}
            button.invalidate()
        } catch (t: Throwable) {
            Log.d(TAG, "Error styling NumPadButton", t)
        }
    }

    /**
     * Styles EmergencyButton with a pearl pill background and Deep Plum text.
     */
    internal fun styleEmergencyButton(button: TextView) {
        try {
            val density = button.resources.displayMetrics.density
            val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
            val pillBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor(ActiveHookTheme.container)
                setStroke(strokeWidth, ActiveHookTheme.lockPearlStroke)
            }
            val ripple = RippleDrawable(
                ColorStateList.valueOf(ActiveHookTheme.primary),
                pillBg,
                null
            )
            button.background = ripple
            button.setTextColor(ActiveHookTheme.accentDeep)
            button.invalidate()
        } catch (t: Throwable) {
            Log.d(TAG, "Error styling EmergencyButton", t)
        }
    }

    /**
     * Styles KeyguardMessageArea with Deep Plum text.
     */
    internal fun styleMessageArea(messageArea: TextView) {
        try {
            try {
                XposedHelpers.setObjectField(messageArea, "mDefaultColorState", ColorStateList.valueOf(ActiveHookTheme.accentDeep))
            } catch (_: Throwable) {}
            messageArea.setTextColor(ActiveHookTheme.accentDeep)
            messageArea.invalidate()
        } catch (t: Throwable) {
            Log.d(TAG, "Error styling KeyguardMessageArea", t)
        }
    }
}
