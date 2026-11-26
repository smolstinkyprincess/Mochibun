package one.crinklecod.mochibun.xposed.systemui

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.crinklecod.mochibun.ui.theme.withAlpha
import one.crinklecod.mochibun.xposed.systemui.SystemUiDrawables.createFooterButtonDrawable
import one.crinklecod.mochibun.xposed.systemui.SystemUiDrawables.createTileSquircleDrawable
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.ensureReceiverRegistered
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedBrightnessSliders
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedFooters
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedNotificationFooters
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedScrimControllers
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedTileViews
import kotlin.math.roundToInt

/** Stage D: shade blur/scrim suppression, 14dp QS tile squircles, footer & brightness slider tinting, and notification row skinning. */
internal object QuickSettingsHooks {

    /** Installs this module's hooks; called once by [MochibunSystemUiHook] after the kill-switch check. */
    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        hookQuickSettings(classLoader)
        hookNotifications(classLoader)
    }

    /** Notification.COLOR_INVALID: CachingIconView reports this for legacy, non-colorable small icons. */
    private const val NOTIFICATION_COLOR_INVALID = 1

    internal fun refreshTileView(tileView: View) {
        val stateInt = try {
            val s = XposedHelpers.getIntField(tileView, "lastState")
            if (s == -1) {
                val tileState = try { XposedHelpers.getBooleanField(tileView, "tileState") } catch (_: Throwable) { false }
                if (tileState) ActiveHookTheme.QS_STATE_ACTIVE else ActiveHookTheme.QS_STATE_INACTIVE
            } else {
                s
            }
        } catch (_: Throwable) {
            1
        }
        val density = tileView.context.resources.displayMetrics.density

        var bgDrawable = XposedHelpers.getObjectField(tileView, "colorBackgroundDrawable") as? GradientDrawable
        if (bgDrawable == null) {
            bgDrawable = createTileSquircleDrawable(density)
            XposedHelpers.setObjectField(tileView, "colorBackgroundDrawable", bgDrawable)
            val ripple = XposedHelpers.getObjectField(tileView, "ripple") as? RippleDrawable
            val bgId = tileView.context.resources.getIdentifier("background", "id", TARGET_PACKAGE)
            if (bgId != 0 && ripple != null) {
                try {
                    ripple.setDrawableByLayerId(bgId, bgDrawable)
                } catch (_: Throwable) {}
            }
        }

        val fillColor = ActiveHookTheme.qsTileFill(stateInt)
        val fgColor = ActiveHookTheme.qsTileFg(stateInt)

        // Cancel any active animator to prevent it from animating back to old colors
        try {
            (XposedHelpers.getObjectField(tileView, "singleAnimator") as? ValueAnimator)?.cancel()
        } catch (_: Throwable) {}

        // Call tileView.setColor(fillColor) to keep QSTileViewImpl internal paintColor in sync
        try {
            XposedHelpers.callMethod(tileView, "setColor", fillColor)
        } catch (_: Throwable) {
            try {
                XposedHelpers.setIntField(tileView, "paintColor", fillColor)
            } catch (_: Throwable) {}
        }

        // Update both the fill color, tint, and stroke on the squircle GradientDrawable
        bgDrawable.setColor(fillColor)
        bgDrawable.setTint(fillColor)
        val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
        bgDrawable.setStroke(strokeWidth, ActiveHookTheme.qsTileStroke)

        val label = XposedHelpers.getObjectField(tileView, "label") as? TextView
        label?.setTextColor(fgColor)

        val secondaryLabel = try {
            XposedHelpers.callMethod(tileView, "getSecondaryLabel") as? TextView
        } catch (_: Throwable) {
            null
        }
        secondaryLabel?.setTextColor(fgColor)

        val chevron = XposedHelpers.getObjectField(tileView, "chevronView") as? ImageView
        chevron?.imageTintList = ColorStateList.valueOf(fgColor)

        val iconView = try {
            XposedHelpers.callMethod(tileView, "getIcon")
        } catch (_: Throwable) {
            null
        }
        if (iconView != null) {
            val iconIv = try {
                XposedHelpers.callMethod(iconView, "getIconView") as? ImageView
            } catch (_: Throwable) {
                null
            }
            iconIv?.imageTintList = ColorStateList.valueOf(fgColor)
        }

        tileView.invalidate()
    }

    internal fun styleBrightnessSlider(sliderView: View) {
        val context = sliderView.context
        val res = context.resources
        val sliderId = res.getIdentifier("slider", "id", TARGET_PACKAGE)
        val slider = (if (sliderId != 0) sliderView.findViewById<SeekBar>(sliderId) else null)
            ?: (try { XposedHelpers.getObjectField(sliderView, "mSlider") as? SeekBar } catch (_: Throwable) { null })
            ?: return

        slider.progressTintList = ActiveHookTheme.primaryColorStateList
        slider.progressBackgroundTintList = ActiveHookTheme.trackColorStateList

        val progressDrawable = slider.progressDrawable as? LayerDrawable
        if (progressDrawable != null) {
            val bgDrawable = progressDrawable.findDrawableByLayerId(android.R.id.background)
            bgDrawable?.setTint(ActiveHookTheme.track)

            val progressLayer = progressDrawable.findDrawableByLayerId(android.R.id.progress)
            val fullLayer = when (progressLayer) {
                is LayerDrawable -> progressLayer
                is DrawableWrapper -> progressLayer.drawable as? LayerDrawable
                else -> null
            }
            if (fullLayer != null) {
                val fgId = res.getIdentifier("slider_foreground", "id", TARGET_PACKAGE)
                if (fgId != 0) {
                    val fgDrawable = fullLayer.findDrawableByLayerId(fgId)
                    fgDrawable?.setTint(ActiveHookTheme.primary)
                }
                val iconId = res.getIdentifier("slider_icon", "id", TARGET_PACKAGE)
                if (iconId != 0) {
                    val iconDrawable = fullLayer.findDrawableByLayerId(iconId)
                    iconDrawable?.setTint(ActiveHookTheme.onPrimary)
                    iconDrawable?.setTintList(ActiveHookTheme.onPrimaryColorStateList)
                }
            }
        }
    }

    internal fun styleFooterView(footerView: View) {
        val context = footerView.context
        val res = context.resources
        val density = res.displayMetrics.density

        fun styleButton(view: View) {
            view.background = createFooterButtonDrawable(density)
            view.clipToOutline = true
            if (view is ImageView) {
                view.imageTintList = ActiveHookTheme.accentDeepColorStateList
                view.setColorFilter(ActiveHookTheme.accentDeep, PorterDuff.Mode.SRC_IN)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    if (child is ImageView) {
                        child.imageTintList = ActiveHookTheme.accentDeepColorStateList
                        child.setColorFilter(ActiveHookTheme.accentDeep, PorterDuff.Mode.SRC_IN)
                    }
                }
            }
        }

        // 1. Edit Quick Settings button (pencil)
        var editView: View? = footerView.findViewById(android.R.id.edit)
        if (editView == null) {
            val editId = res.getIdentifier("edit", "id", TARGET_PACKAGE).takeIf { it != 0 }
                ?: res.getIdentifier("qs_edit", "id", TARGET_PACKAGE).takeIf { it != 0 }
                ?: 0
            if (editId != 0) {
                editView = footerView.findViewById(editId)
            }
        }
        if (editView == null) {
            editView = try {
                XposedHelpers.getObjectField(footerView, "mEdit") as? View
            } catch (_: Throwable) { null }
        }
        if (editView != null) {
            styleButton(editView)
        } else {
            Log.d(TAG, "QSFooterView: edit button view ID ('edit' / 'qs_edit') not found")
        }

        // 2. Power Menu button
        val powerId = res.getIdentifier("pm_lite", "id", TARGET_PACKAGE).takeIf { it != 0 }
            ?: res.getIdentifier("power_button", "id", TARGET_PACKAGE).takeIf { it != 0 }
            ?: res.getIdentifier("power_group", "id", TARGET_PACKAGE).takeIf { it != 0 }
            ?: 0
        var powerView: View? = if (powerId != 0) footerView.findViewById(powerId) else null
        if (powerView == null) {
            powerView = try {
                XposedHelpers.getObjectField(footerView, "mPowerMenuLite") as? View
            } catch (_: Throwable) { null }
        }
        if (powerView != null) {
            styleButton(powerView)
        } else {
            Log.d(TAG, "QSFooterView: power button view ID ('pm_lite' / 'power_button' / 'power_group') not found")
        }

        // 3. Settings button (gear)
        val settingsId = res.getIdentifier("settings_button", "id", TARGET_PACKAGE).takeIf { it != 0 }
            ?: res.getIdentifier("settings", "id", TARGET_PACKAGE).takeIf { it != 0 }
            ?: 0
        var settingsView: View? = if (settingsId != 0) footerView.findViewById(settingsId) else null
        if (settingsView == null) {
            settingsView = try {
                XposedHelpers.getObjectField(footerView, "mSettingsButton") as? View
            } catch (_: Throwable) { null }
        }
        if (settingsView != null) {
            styleButton(settingsView)
        } else {
            Log.d(TAG, "QSFooterView: settings button view ID ('settings_button') not found")
        }

        val containerId = res.getIdentifier("settings_button_container", "id", TARGET_PACKAGE).takeIf { it != 0 }
            ?: res.getIdentifier("settings_container", "id", TARGET_PACKAGE).takeIf { it != 0 }
            ?: 0
        val settingsContainer = (if (containerId != 0) footerView.findViewById<View>(containerId) else null)
            ?: (try { XposedHelpers.getObjectField(footerView, "mSettingsContainer") as? View } catch (_: Throwable) { null })
        settingsContainer?.background = ColorDrawable(0)

        val tunerId = res.getIdentifier("tuner_icon", "id", TARGET_PACKAGE)
        val tunerIcon = (if (tunerId != 0) footerView.findViewById<View>(tunerId) else null)
            ?: (try { XposedHelpers.getObjectField(footerView, "mTunerIcon") as? View } catch (_: Throwable) { null })
        if (tunerIcon is ImageView) {
            tunerIcon.imageTintList = ActiveHookTheme.accentDeepColorStateList
            tunerIcon.setColorFilter(ActiveHookTheme.accentDeep, PorterDuff.Mode.SRC_IN)
        }

        val multiUserId = res.getIdentifier("multi_user_switch", "id", TARGET_PACKAGE)
        val multiUserSwitch = (if (multiUserId != 0) footerView.findViewById<View>(multiUserId) else null)
            ?: (try { XposedHelpers.getObjectField(footerView, "mMultiUserSwitch") as? View } catch (_: Throwable) { null })
        if (multiUserSwitch != null) {
            styleButton(multiUserSwitch)
        }
    }

    /**
     * Stage D: Kawaii SystemUI Quick Settings Hook.
     * Restyles Android 12's stock Quick Settings pull-down shade and its tiles into Mochibun's pastel keitai design language:
     * - Scrim & Blur Suppression (BlurUtils, NotificationShadeDepthController, ScrimController)
     * - Kawaii Squircle Tiles (QSTileViewImpl, QSIconViewImpl)
     * - Brightness Slider Tinting (BrightnessSliderView, BrightnessSlider)
     * - Footer Action Buttons (QSFooterView / QSFooterViewController)
     */
    private fun hookQuickSettings(classLoader: ClassLoader) {
        hookQuickSettingsBlurAndScrim(classLoader)
        hookQuickSettingsTiles(classLoader)
        hookQuickSettingsBrightness(classLoader)
        hookQuickSettingsFooter(classLoader)
    }

    /**
     * Applies the active pastel palette to notification rows, content text, action buttons,
     * and the notification shade's Clear All button.
     */
    private fun hookNotifications(classLoader: ClassLoader) {
        val rowClassName = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"
        val rowHooks = listOf("onFinishInflate", "onNotificationUpdated")
        for (methodName in rowHooks) {
            try {
                XposedHelpers.findAndHookMethod(
                    rowClassName,
                    classLoader,
                    methodName,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                (param.thisObject as? View)?.let(::styleNotificationRow)
                            } catch (t: Throwable) {
                                Log.d(TAG, "Failed styling notification row after $methodName", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed notification row hook: $methodName")
            } catch (t: Throwable) {
                Log.d(TAG, "Unable to hook notification row method $methodName; skipping", t)
            }
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.notification.row.NotificationBackgroundView",
                classLoader,
                "setTint",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            param.args[0] = 0
                        } catch (t: Throwable) {
                            Log.d(TAG, "Failed suppressing stock notification background tint", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed notification background tint suppression")
        } catch (t: Throwable) {
            Log.d(TAG, "Unable to hook NotificationBackgroundView.setTint; skipping", t)
        }

        for (methodName in listOf("setContractedChild", "setExpandedChild", "setHeadsUpChild")) {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.android.systemui.statusbar.notification.row.NotificationContentView",
                    classLoader,
                    methodName,
                    View::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                (param.thisObject as? View)?.let(::styleNotificationExpandButton)
                                (param.args[0] as? View)?.let(::styleNotificationSmallIcons)
                            } catch (t: Throwable) {
                                Log.d(TAG, "Failed styling notification expand button after $methodName", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed notification content hook: $methodName")
            } catch (t: Throwable) {
                Log.d(TAG, "Unable to hook NotificationContentView.$methodName; skipping", t)
            }
        }

        val footerClassName = "com.android.systemui.statusbar.notification.row.FooterView"
        for (methodName in listOf("onFinishInflate", "setTextColor", "onConfigurationChanged")) {
            try {
                val parameterTypes = when (methodName) {
                    "setTextColor" -> arrayOf(Int::class.javaPrimitiveType!!)
                    "onConfigurationChanged" -> arrayOf(android.content.res.Configuration::class.java)
                    else -> emptyArray()
                }
                val footerHook = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            (param.thisObject as? View)?.let { footer ->
                                trackedNotificationFooters.add(footer)
                                styleNotificationFooter(footer)
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Failed styling notification footer after $methodName", t)
                        }
                    }
                }
                val hookArguments = ArrayList<Any>(parameterTypes.size + 1).apply {
                    parameterTypes.forEach(::add)
                    add(footerHook)
                }
                XposedHelpers.findAndHookMethod(
                    footerClassName,
                    classLoader,
                    methodName,
                    *hookArguments.toTypedArray()
                )
                Log.d(TAG, "Installed notification footer hook: $methodName")
            } catch (t: Throwable) {
                Log.d(TAG, "Unable to hook FooterView.$methodName; skipping", t)
            }
        }
    }

    internal fun styleNotificationRow(row: View) {
        try {
            ActiveHookTheme.notificationRows.add(row)
            val density = row.resources.displayMetrics.density
            val background = try {
                XposedHelpers.getObjectField(row, "mBackgroundNormal") as? View
            } catch (_: Throwable) {
                null
            } ?: run {
                val backgroundId = row.resources.getIdentifier("backgroundNormal", "id", TARGET_PACKAGE)
                if (backgroundId != 0) row.findViewById(backgroundId) else null
            }

            if (background != null) {
                val border = ActiveHookTheme.accentDeep.withAlpha(0x4D)
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 19f * density
                    setColor(ActiveHookTheme.container)
                    setStroke((1.2f * density).toInt().coerceAtLeast(1), border)
                }
                XposedHelpers.callMethod(background, "setCustomBackground", drawable)
            }

            styleNotificationExpandButton(row)

            styleNotificationContentTree(row, inActionContainer = false)
            row.invalidate()
        } catch (t: Throwable) {
            Log.d(TAG, "Failed applying pastel notification row styling", t)
        }
    }

    private fun styleNotificationExpandButton(root: View) {
        try {
            val expandButton = findNotificationViewByName(root, "expand_button") ?: return
            val pill = findNotificationViewByName(expandButton, "expand_button_pill")
            val visibleButton = pill ?: expandButton
            val density = root.resources.displayMetrics.density
            val border = ActiveHookTheme.accentDeep.withAlpha(0x4D)
            visibleButton.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ActiveHookTheme.container)
                setStroke((1.2f * density).toInt().coerceAtLeast(1), border)
            }
            visibleButton.backgroundTintList = null

            (findNotificationViewByName(expandButton, "expand_button_icon") as? ImageView)?.let { icon ->
                icon.imageTintList = ActiveHookTheme.accentDeepColorStateList
                icon.setColorFilter(ActiveHookTheme.accentDeep, PorterDuff.Mode.SRC_IN)
            }
            visibleButton.invalidate()
        } catch (t: Throwable) {
            Log.d(TAG, "Failed applying pastel notification expand-button styling", t)
        }
    }

    private fun findNotificationViewByName(root: View, targetName: String): View? {
        if (root.entryNameOrNull() == targetName) return root

        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findNotificationViewByName(root.getChildAt(index), targetName)?.let { return it }
            }
        }
        return null
    }

    private fun styleNotificationContentTree(view: View, inActionContainer: Boolean) {
        val idName = view.entryNameOrNull()?.lowercase().orEmpty()
        val inActions = inActionContainer ||
            idName.contains("action") ||
            idName.contains("smart_reply")

        if (view is ImageView) styleNotificationSmallIconView(view, idName)

        if (view is TextView) {
            when {
                view.id == android.R.id.title || idName in setOf("title", "notification_title") ->
                    view.setTextColor(ActiveHookTheme.accentDeep)
                idName in setOf(
                    "text", "text_line_1", "big_text", "text2", "notification_text"
                ) ->
                    view.setTextColor(ActiveHookTheme.accentDeep.withAlpha(0xD9))
                idName.contains("app_name") || idName.contains("header") ||
                    idName.contains("time") || idName.contains("chronometer") ->
                    view.setTextColor(ActiveHookTheme.accentDeep.withAlpha(0xA6))
            }

            if (inActions && (view.isClickable || idName.contains("action"))) {
                val density = view.resources.displayMetrics.density
                val actionBackground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f * density
                    setColor(ActiveHookTheme.primary)
                }
                view.background = actionBackground
                view.setTextColor(ActiveHookTheme.onPrimary)
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                styleNotificationContentTree(view.getChildAt(index), inActions)
            }
        }
    }

    /** Walks [root] and themes every notification small app icon badge inside it. */
    private fun styleNotificationSmallIcons(root: View) {
        if (root is ImageView) {
            styleNotificationSmallIconView(root, root.entryNameOrNull()?.lowercase().orEmpty())
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                styleNotificationSmallIcons(root.getChildAt(index))
            }
        }
    }

    /**
     * Themes the small circular app icon badge in the top-left of a notification.
     * - `android:id/icon` (a framework CachingIconView) in contracted, expanded, heads-up and
     *   conversation templates: pastel circle background + accentDeep glyph tint. The framework
     *   applies the per-app accent through RemoteViews when the notification is inflated, so this
     *   runs again from the row / content-view hooks after every (re)inflation.
     * - `conversation_icon_badge_bg`: the separate circle drawn behind the badge in conversation
     *   layouts gets the same fill.
     * Legacy full-color small icons (getOriginalIconColor() == COLOR_INVALID) keep their pixels;
     * only the circle behind them is themed so they don't become a solid blob.
     */
    private fun styleNotificationSmallIconView(view: ImageView, idName: String) {
        try {
            if (idName == "conversation_icon_badge_bg") {
                view.imageTintList = ActiveHookTheme.primaryColorStateList
                view.setColorFilter(ActiveHookTheme.primary, PorterDuff.Mode.SRC_IN)
                return
            }
            // Only the framework header/badge icon; app-provided custom layouts may reuse android:id/icon.
            if (view.id != android.R.id.icon || !view.javaClass.name.endsWith("CachingIconView")) return

            val density = view.resources.displayMetrics.density
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ActiveHookTheme.primary)
                setStroke((1f * density).toInt().coerceAtLeast(1), ActiveHookTheme.onPrimary)
            }
            view.backgroundTintList = null

            val originalIconColor = try {
                XposedHelpers.callMethod(view, "getOriginalIconColor") as? Int
            } catch (_: Throwable) {
                null
            }
            if (originalIconColor != NOTIFICATION_COLOR_INVALID) {
                view.imageTintList = ActiveHookTheme.onPrimaryColorStateList
                view.setColorFilter(ActiveHookTheme.onPrimary, PorterDuff.Mode.SRC_IN)
            }
            view.invalidate()
        } catch (t: Throwable) {
            Log.d(TAG, "Failed applying pastel notification small-icon styling", t)
        }
    }

    internal fun styleNotificationFooterPill(button: TextView, customText: String? = null) {
        val density = button.resources.displayMetrics.density
        val border = ActiveHookTheme.accentDeep.withAlpha(0x66)
        val padH = (18f * density).roundToInt()
        val padV = (6f * density).roundToInt()
        val strokePx = (1.5f * density).roundToInt().coerceAtLeast(1)

        button.backgroundTintList = null
        button.clipToOutline = false
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f * density
            setColor(ActiveHookTheme.container)
            setStroke(strokePx, border)
        }
        button.setPadding(padH, padV, padH, padV)
        if (customText != null) {
            button.text = customText
        }
        button.setTextColor(ActiveHookTheme.accentDeep)
        button.invalidate()
    }

    internal fun styleNotificationFooter(footer: View) {
        try {
            val resources = footer.resources
            val dismissId = resources.getIdentifier("dismiss_text", "id", TARGET_PACKAGE)
            val clearAllId = resources.getIdentifier("clear_all", "id", TARGET_PACKAGE)
            val dismissButton = (if (dismissId != 0) footer.findViewById(dismissId) else null)
                ?: (if (clearAllId != 0) footer.findViewById(clearAllId) else null)
                ?: try {
                    XposedHelpers.getObjectField(footer, "mDismissButton") as? View
                } catch (_: Throwable) {
                    null
                }
            (dismissButton as? TextView)?.let { styleNotificationFooterPill(it, "♡ Clear All ♡") }

            val manageId = resources.getIdentifier("manage_text", "id", TARGET_PACKAGE)
            val manageButton = (if (manageId != 0) footer.findViewById(manageId) else null)
                ?: try {
                    XposedHelpers.getObjectField(footer, "mManageButton") as? View
                } catch (_: Throwable) {
                    null
                }
            (manageButton as? TextView)?.let { styleNotificationFooterPill(it) }
        } catch (t: Throwable) {
            Log.d(TAG, "Failed applying pastel notification footer styling", t)
        }
    }

    /**
     * Stage D1: Scrim & Blur Suppression.
     * Disables GPU surface blur on MT6761 hardware and replaces stock black shade backdrop with
     * soft translucent lavender / frosted ivory #F6F0FC at ~92% opacity (0xEBF6F0FC).
     */
    private fun hookQuickSettingsBlurAndScrim(classLoader: ClassLoader) {
        // 1. Disable window/surface blur via BlurUtils
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.BlurUtils",
                classLoader,
                "supportsBlursOnWindows",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )
            Log.d(TAG, "Installed hook: BlurUtils.supportsBlursOnWindows")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BlurUtils.supportsBlursOnWindows", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.BlurUtils",
                classLoader,
                "blurRadiusOfRatio",
                Float::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = 0
                    }
                }
            )
            Log.d(TAG, "Installed hook: BlurUtils.blurRadiusOfRatio")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BlurUtils.blurRadiusOfRatio", t)
        }

        // 2. Disable shade blur in NotificationShadeDepthController
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.NotificationShadeDepthController",
                classLoader,
                "shouldApplyShadeBlur",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )
            Log.d(TAG, "Installed hook: NotificationShadeDepthController.shouldApplyShadeBlur")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook NotificationShadeDepthController.shouldApplyShadeBlur", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.NotificationShadeDepthController",
                classLoader,
                "setWakeAndUnlockBlurRadius",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = 0
                    }
                }
            )
            Log.d(TAG, "Installed hook: NotificationShadeDepthController.setWakeAndUnlockBlurRadius")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook NotificationShadeDepthController.setWakeAndUnlockBlurRadius", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.NotificationShadeDepthController",
                classLoader,
                "setBlursDisabledForAppLaunch",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = true
                    }
                }
            )
            Log.d(TAG, "Installed hook: NotificationShadeDepthController.setBlursDisabledForAppLaunch")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook NotificationShadeDepthController.setBlursDisabledForAppLaunch", t)
        }

        // 3. Override ScrimController backdrop tint to translucent lavender #F6F0FC at ~92%
        // and eliminate dark veil during PIN bouncer so the wallpaper remains fully visible
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.phone.ScrimController",
                classLoader,
                "updateScrimColor",
                View::class.java,
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            trackedScrimControllers.add(param.thisObject)
                            val state = try { XposedHelpers.getObjectField(param.thisObject, "mState") } catch (_: Throwable) { null }
                            val stateName = state?.toString().orEmpty()
                            if (stateName.contains("BOUNCER")) {
                                // Transparent bouncer backdrop: eliminate dark veil so wallpaper is fully visible
                                param.args[1] = 0.0f
                                param.args[2] = 0
                                return
                            }

                            val view = param.args[0] as? View ?: return
                            val scrimBehind = XposedHelpers.getObjectField(param.thisObject, "mScrimBehind") as? View
                            val notifScrim = XposedHelpers.getObjectField(param.thisObject, "mNotificationsScrim") as? View
                            if (view === scrimBehind || view === notifScrim) {
                                val currentTint = param.args[2] as? Int ?: 0
                                if (currentTint != 0) {
                                    param.args[2] = ActiveHookTheme.scrim
                                }
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in ScrimController.updateScrimColor hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: ScrimController.updateScrimColor")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook ScrimController.updateScrimColor", t)
        }
    }

    /**
     * Stage D2: Kawaii Squircle Tiles.
     * Restyles QSTileViewImpl container background to a 14dp squircle with 1.2dp border,
     * and maps active/inactive/unavailable states to Mochibun's pastel palette.
     */
    @android.annotation.SuppressLint("DiscouragedApi")
    private fun hookQuickSettingsTiles(classLoader: ClassLoader) {
        val tileViewClassName = "com.android.systemui.qs.tileimpl.QSTileViewImpl"

        // 1. Hook getBackgroundColorForState to return Mochibun fills for all 3 states
        try {
            XposedHelpers.findAndHookMethod(
                tileViewClassName,
                classLoader,
                "getBackgroundColorForState",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val state = param.args[0] as? Int ?: return
                        param.result = ActiveHookTheme.qsTileFill(state)
                    }
                }
            )
            Log.d(TAG, "Installed hook: QSTileViewImpl.getBackgroundColorForState")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook QSTileViewImpl.getBackgroundColorForState", t)
        }

        // 2. Hook getLabelColorForState
        try {
            XposedHelpers.findAndHookMethod(
                tileViewClassName,
                classLoader,
                "getLabelColorForState",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val state = param.args[0] as? Int ?: return
                        param.result = ActiveHookTheme.qsTileFg(state)
                    }
                }
            )
            Log.d(TAG, "Installed hook: QSTileViewImpl.getLabelColorForState")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook QSTileViewImpl.getLabelColorForState", t)
        }

        // 3. Hook getSecondaryLabelColorForState
        try {
            XposedHelpers.findAndHookMethod(
                tileViewClassName,
                classLoader,
                "getSecondaryLabelColorForState",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val state = param.args[0] as? Int ?: return
                        param.result = ActiveHookTheme.qsTileFg(state)
                    }
                }
            )
            Log.d(TAG, "Installed hook: QSTileViewImpl.getSecondaryLabelColorForState")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook QSTileViewImpl.getSecondaryLabelColorForState", t)
        }

        // 4. Hook getChevronColorForState
        try {
            XposedHelpers.findAndHookMethod(
                tileViewClassName,
                classLoader,
                "getChevronColorForState",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val state = param.args[0] as? Int ?: return
                        param.result = ActiveHookTheme.qsTileFg(state)
                    }
                }
            )
            Log.d(TAG, "Installed hook: QSTileViewImpl.getChevronColorForState")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook QSTileViewImpl.getChevronColorForState", t)
        }

        // 5. Hook QSIconViewImpl.getIconColorForState
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.qs.tileimpl.QSIconViewImpl",
                classLoader,
                "getIconColorForState",
                android.content.Context::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val state = param.args[1] as? Int ?: return
                        param.result = ActiveHookTheme.qsTileFg(state)
                    }
                }
            )
            Log.d(TAG, "Installed hook: QSIconViewImpl.getIconColorForState")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook QSIconViewImpl.getIconColorForState", t)
        }

        // 6. Hook createTileBackground to inject programmatic squircle GradientDrawable
        try {
            XposedHelpers.findAndHookMethod(
                tileViewClassName,
                classLoader,
                "createTileBackground",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val tileView = param.thisObject as? View ?: return
                            trackedTileViews.add(tileView)
                            ensureReceiverRegistered(tileView.context.applicationContext ?: tileView.context)
                            val density = tileView.context.resources.displayMetrics.density
                            val squircle = createTileSquircleDrawable(density)
                            XposedHelpers.setObjectField(param.thisObject, "colorBackgroundDrawable", squircle)
                            val ripple = XposedHelpers.getObjectField(param.thisObject, "ripple") as? RippleDrawable
                            if (ripple != null) {
                                val bgId = tileView.context.resources.getIdentifier("background", "id", TARGET_PACKAGE)
                                if (bgId != 0) {
                                    try {
                                        ripple.setDrawableByLayerId(bgId, squircle)
                                    } catch (_: Throwable) {}
                                }
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in QSTileViewImpl.createTileBackground hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: QSTileViewImpl.createTileBackground")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook QSTileViewImpl.createTileBackground", t)
        }

        // 7. Hook handleStateChanged to enforce squircle stroke, fill, and icon tints across state updates
        try {
            val stateClass = XposedHelpers.findClass("com.android.systemui.plugins.qs.QSTile\$State", classLoader)
            XposedHelpers.findAndHookMethod(
                tileViewClassName,
                classLoader,
                "handleStateChanged",
                stateClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val tileView = param.thisObject as? View ?: return
                            trackedTileViews.add(tileView)
                            val state = param.args[0] ?: return
                            val stateInt = XposedHelpers.getIntField(state, "state")
                            val density = tileView.context.resources.displayMetrics.density

                            var bgDrawable = XposedHelpers.getObjectField(param.thisObject, "colorBackgroundDrawable") as? GradientDrawable
                            if (bgDrawable == null) {
                                bgDrawable = createTileSquircleDrawable(density)
                                XposedHelpers.setObjectField(param.thisObject, "colorBackgroundDrawable", bgDrawable)
                                val ripple = XposedHelpers.getObjectField(param.thisObject, "ripple") as? RippleDrawable
                                val bgId = tileView.context.resources.getIdentifier("background", "id", TARGET_PACKAGE)
                                if (bgId != 0 && ripple != null) {
                                    try {
                                        ripple.setDrawableByLayerId(bgId, bgDrawable)
                                    } catch (_: Throwable) {}
                                }
                            }

                            val fillColor = ActiveHookTheme.qsTileFill(stateInt)
                            val fgColor = ActiveHookTheme.qsTileFg(stateInt)

                            bgDrawable.setColor(fillColor)
                            bgDrawable.setTint(fillColor)
                            val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
                            bgDrawable.setStroke(strokeWidth, ActiveHookTheme.qsTileStroke)

                            val label = XposedHelpers.getObjectField(param.thisObject, "label") as? TextView
                            label?.setTextColor(fgColor)

                            val secondaryLabel = try {
                                XposedHelpers.callMethod(param.thisObject, "getSecondaryLabel") as? TextView
                            } catch (_: Throwable) { null }
                            secondaryLabel?.setTextColor(fgColor)

                            val chevron = XposedHelpers.getObjectField(param.thisObject, "chevronView") as? ImageView
                            chevron?.imageTintList = ColorStateList.valueOf(fgColor)

                            val iconView = try {
                                XposedHelpers.callMethod(param.thisObject, "getIcon")
                            } catch (_: Throwable) { null }
                            if (iconView != null) {
                                val iconIv = try {
                                    XposedHelpers.callMethod(iconView, "getIconView") as? ImageView
                                } catch (_: Throwable) { null }
                                iconIv?.imageTintList = ColorStateList.valueOf(fgColor)
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in QSTileViewImpl.handleStateChanged hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: QSTileViewImpl.handleStateChanged")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook QSTileViewImpl.handleStateChanged", t)
        }
    }

    @android.annotation.SuppressLint("DiscouragedApi")
    private fun hookQuickSettingsBrightness(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.settings.brightness.BrightnessSliderView",
                classLoader,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View ?: return
                            trackedBrightnessSliders.add(view)
                            ensureReceiverRegistered(view.context.applicationContext ?: view.context)
                            styleBrightnessSlider(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in BrightnessSliderView.onFinishInflate hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: BrightnessSliderView.onFinishInflate")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BrightnessSliderView.onFinishInflate", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.settings.brightness.BrightnessSlider",
                classLoader,
                "onViewAttached",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = XposedHelpers.getObjectField(param.thisObject, "mView") as? View ?: return
                            trackedBrightnessSliders.add(view)
                            ensureReceiverRegistered(view.context.applicationContext ?: view.context)
                            styleBrightnessSlider(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in BrightnessSlider.onViewAttached hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: BrightnessSlider.onViewAttached")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook BrightnessSlider.onViewAttached", t)
        }
    }

    /**
     * Stage D4: Quick Settings Footer Action Buttons (Edit, Power, Settings).
     * Restyles the bottom action buttons in the expanded Quick Settings shade to match
     * Mochibun's pastel kawaii squircle design language:
     * - Background: Soft Cream (12dp squircle)
     * - Border: 1.2dp stroke in Deep Plum at 30% alpha
     * - Icon Tint: Deep Plum
     */
    @android.annotation.SuppressLint("DiscouragedApi")
    private fun hookQuickSettingsFooter(classLoader: ClassLoader) {
        val footerClassNames = listOf(
            "com.android.systemui.qs.QSFooterView",
            "com.android.systemui.qs.FooterActionsView"
        )

        for (className in footerClassNames) {
            val footerClass = try {
                XposedHelpers.findClass(className, classLoader)
            } catch (_: Throwable) {
                null
            } ?: continue

            try {
                XposedHelpers.findAndHookMethod(
                    footerClass,
                    "onFinishInflate",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val view = param.thisObject as? View ?: return
                                trackedFooters.add(view)
                                ensureReceiverRegistered(view.context.applicationContext ?: view.context)
                                styleFooterView(view)
                                Log.d(TAG, "Applied Mochibun squircle styling to $className")
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in $className.onFinishInflate hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: $className.onFinishInflate")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook $className.onFinishInflate", t)
            }

            try {
                XposedHelpers.findAndHookMethod(
                    footerClass,
                    "onConfigurationChanged",
                    android.content.res.Configuration::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val view = param.thisObject as? View ?: return
                                trackedFooters.add(view)
                                styleFooterView(view)
                            } catch (t: Throwable) {
                                Log.d(TAG, "Error in $className.onConfigurationChanged hook", t)
                            }
                        }
                    }
                )
                Log.d(TAG, "Installed hook: $className.onConfigurationChanged")
            } catch (_: Throwable) {}
        }

        // Also hook QSFooterViewController.onViewAttached as safeguard
        try {
            val controllerClass = XposedHelpers.findClass(
                "com.android.systemui.qs.QSFooterViewController",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                controllerClass,
                "onViewAttached",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = XposedHelpers.getObjectField(param.thisObject, "mView") as? View ?: return
                            trackedFooters.add(view)
                            ensureReceiverRegistered(view.context.applicationContext ?: view.context)
                            styleFooterView(view)
                            Log.d(TAG, "Applied Mochibun squircle styling in QSFooterViewController.onViewAttached")
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in QSFooterViewController.onViewAttached hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: QSFooterViewController.onViewAttached")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook QSFooterViewController.onViewAttached", t)
        }
    }
}
