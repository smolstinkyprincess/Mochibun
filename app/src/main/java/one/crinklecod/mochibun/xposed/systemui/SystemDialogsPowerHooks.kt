package one.crinklecod.mochibun.xposed.systemui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.CONTROL_CORNER_DP
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.DIALOG_CORNER_DP
import one.crinklecod.mochibun.xposed.systemui.SystemUiDrawables.createDialogSquircleDrawable
import one.crinklecod.mochibun.xposed.systemui.SystemUiDrawables.createFocusInkList
import one.crinklecod.mochibun.xposed.systemui.SystemUiDrawables.createFocusSquircleDrawable
import one.crinklecod.mochibun.xposed.systemui.SystemUiDrawables.createToastPillDrawable
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.dialogWindowInsets
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.ensureReceiverRegistered
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedPowerMenuDialogs
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedPowerMenuItems
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedPowerOptionsDialogs
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedScreenshotChips
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedScreenshotViews
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedSystemDialogs
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry.trackedToastViews

/** Stage E/H: Toast pills, Power Menu, stock-layout AlertDialog / SystemUIDialog windows, and the screenshot shelf. */
internal object SystemDialogsPowerHooks {

    /** Installs the SystemUIToast pill hooks (installed before the lockscreen hooks, as originally). */
    fun installToasts(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookToast(lpparam.classLoader)
    }

    /**
     * Installs the Power Menu, system dialog and screenshot hooks. Kept separate from
     * [installToasts] so the coordinator preserves the original hook installation order.
     */
    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        hookPowerMenu(classLoader)
        hookSystemDialogs()
        hookScreenshot(classLoader)
    }

    /**
     * Mirrors a Power Menu tile's D-pad focus into `activated`. ViewGroup.dispatchSetActivated
     * forwards that to the tile's icon and label, so their [createFocusInkList] inks follow focus
     * even though only the tile itself is focusable. Wraps (never replaces) any existing listener.
     */
    private class FocusActivatesChildren(
        private val wrapped: View.OnFocusChangeListener?
    ) : View.OnFocusChangeListener {
        override fun onFocusChange(v: View, hasFocus: Boolean) {
            v.isActivated = hasFocus
            wrapped?.onFocusChange(v, hasFocus)
        }
    }

    /**
     * Stage E: Hook & restyle system and third-party app toast notifications (ToastUI / SystemUIToast).
     * Applies Mochibun's pastel capsule theme:
     * - Container: Soft frosted pill container using ActiveHookTheme.container
     * - Border: 1.2dp stroke using ActiveHookTheme.accentDeep at 30% alpha (ActiveHookTheme.toastStroke)
     * - Typography: Tint toast text to ActiveHookTheme.accentDeep
     * - Optional Keitai Accent: Wrap plain toast messages in soft hearts (♡ message ♡) if not already present.
     */
    private fun hookToast(classLoader: ClassLoader) {
        try {
            val systemUiToastClass = XposedHelpers.findClass(
                "com.android.systemui.toast.SystemUIToast",
                classLoader
            )

            // Hook SystemUIToast.getView()
            XposedHelpers.findAndHookMethod(
                systemUiToastClass,
                "getView",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.result as? View ?: return
                            ensureReceiverRegistered(view.context.applicationContext ?: view.context)
                            trackedToastViews.add(view)
                            styleToastView(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in SystemUIToast.getView hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: SystemUIToast.getView")

            // Hook SystemUIToast.inflateToastView()
            XposedHelpers.findAndHookMethod(
                systemUiToastClass,
                "inflateToastView",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.result as? View ?: return
                            ensureReceiverRegistered(view.context.applicationContext ?: view.context)
                            trackedToastViews.add(view)
                            styleToastView(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in SystemUIToast.inflateToastView hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: SystemUIToast.inflateToastView")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook SystemUIToast", t)
        }
    }

    /**
     * Styles a toast root view into Mochibun's pill container with border, text color,
     * and optional keitai heart framing.
     */
    internal fun styleToastView(view: View, updateText: Boolean = true) {
        try {
            val density = view.resources.displayMetrics.density
            view.background = createToastPillDrawable(density)

            val textResId = view.resources.getIdentifier("text", "id", TARGET_PACKAGE)
            val textView = (if (textResId != 0) view.findViewById<TextView>(textResId) else null)
                ?: view.findViewById<TextView>(android.R.id.message)
                ?: findTextViewRecursive(view)

            if (textView != null) {
                textView.setTextColor(ActiveHookTheme.accentDeep)
                val pad14dp = (14f * density).toInt()
                textView.setPaddingRelative(pad14dp, textView.paddingTop, pad14dp, textView.paddingBottom)

                if (updateText) {
                    val rawText = textView.text?.toString().orEmpty()
                    textView.text = ToastTextFormatter.format(rawText)
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Error in styleToastView", t)
        }
    }

    internal fun findTextViewRecursive(view: View): TextView? {
        if (view is TextView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTextViewRecursive(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Stage H1: Power / Global Actions menu (GlobalActionsDialogLite on this Android 12 MTK build;
     * GlobalActionsImpl.showGlobalActions only ever creates the Lite dialog).
     * - ActionsDialogLite.initializeLayout(): 22dp squircle panel behind the action grid (@android:id/list).
     *   GlobalActionsLayoutLite.getBackgroundDrawable() returns null, so the panel is never re-tinted on measure.
     * - MyAdapter.getView(): focus-aware squircle tile per action, lilac icon pearl, focus-following label ink.
     * - MyPowerOptionsAdapter.getView() + GlobalActionsPowerDialog.create(): same grammar for the power-options sub-dialog.
     */
    private fun hookPowerMenu(classLoader: ClassLoader) {
        val liteClass = "com.android.systemui.globalactions.GlobalActionsDialogLite"

        try {
            XposedHelpers.findAndHookMethod(
                "$liteClass\$ActionsDialogLite",
                classLoader,
                "initializeLayout",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val dialog = param.thisObject as? Dialog ?: return
                            ensureReceiverRegistered(dialog.context.applicationContext ?: dialog.context)
                            trackedPowerMenuDialogs.add(dialog)
                            stylePowerMenuPanel(dialog)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in ActionsDialogLite.initializeLayout hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: GlobalActionsDialogLite\$ActionsDialogLite.initializeLayout")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook GlobalActionsDialogLite\$ActionsDialogLite.initializeLayout", t)
        }

        val emergencyActionClass = XposedHelpers.findClassIfExists("$liteClass\$EmergencyAction", classLoader)
        try {
            XposedHelpers.findAndHookMethod(
                "$liteClass\$MyAdapter",
                classLoader,
                "getView",
                Int::class.javaPrimitiveType,
                View::class.java,
                ViewGroup::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val item = param.result as? View ?: return
                            val action = try {
                                XposedHelpers.callMethod(param.thisObject, "getItem", param.args[0])
                            } catch (_: Throwable) {
                                null
                            }
                            val isEmergency = emergencyActionClass?.isInstance(action) == true
                            trackedPowerMenuItems[item] = isEmergency
                            stylePowerMenuItem(item, isEmergency)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in GlobalActionsDialogLite\$MyAdapter.getView hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: GlobalActionsDialogLite\$MyAdapter.getView")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook GlobalActionsDialogLite\$MyAdapter.getView", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "$liteClass\$MyPowerOptionsAdapter",
                classLoader,
                "getView",
                Int::class.javaPrimitiveType,
                View::class.java,
                ViewGroup::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val item = param.result as? View ?: return
                            trackedPowerMenuItems[item] = false
                            stylePowerMenuItem(item, isEmergency = false)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in GlobalActionsDialogLite\$MyPowerOptionsAdapter.getView hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: GlobalActionsDialogLite\$MyPowerOptionsAdapter.getView")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook GlobalActionsDialogLite\$MyPowerOptionsAdapter.getView", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.globalactions.GlobalActionsPowerDialog",
                classLoader,
                "create",
                Context::class.java,
                ListAdapter::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val dialog = param.result as? Dialog ?: return
                            val window = dialog.window ?: return
                            trackedPowerOptionsDialogs.add(dialog)
                            styleDialogWindowBackground(window)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in GlobalActionsPowerDialog.create hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: GlobalActionsPowerDialog.create")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook GlobalActionsPowerDialog.create", t)
        }
    }

    /** Replaces the stock global_actions_lite_background behind the Power Menu action grid. */
    internal fun stylePowerMenuPanel(dialog: Dialog) {
        val panel = dialog.findViewById<View>(android.R.id.list)
        if (panel == null) {
            Log.d(TAG, "Power Menu: panel @android:id/list not found")
            return
        }
        panel.background = createDialogSquircleDrawable(panel.resources.displayMetrics.density)
    }

    /**
     * Styles one Power Menu action tile (global_actions_grid_item_lite / global_actions_power_item).
     * Emergency tiles keep the stock red icon circle and label set by EmergencyAction.create().
     */
    internal fun stylePowerMenuItem(item: View, isEmergency: Boolean) {
        val density = item.resources.displayMetrics.density
        item.background = createFocusSquircleDrawable(density, CONTROL_CORNER_DP * density, ActiveHookTheme.container)
        item.isFocusable = true
        val existingListener = item.onFocusChangeListener
        if (existingListener !is FocusActivatesChildren) {
            item.onFocusChangeListener = FocusActivatesChildren(existingListener)
        }
        item.isActivated = item.isFocused
        if (isEmergency) return

        val icon = item.findViewById<ImageView>(android.R.id.icon)
        if (icon != null) {
            if (icon.background != null) {
                // Grid tile: swap the stock button circle for a lilac pearl with contrast-safe ink.
                icon.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ActiveHookTheme.track)
                }
                icon.imageTintList = ActiveHookTheme.onTrackColorStateList
            } else {
                // Power-options row: bare icon follows the tile's focus like the label.
                icon.imageTintList = createFocusInkList(ActiveHookTheme.accentDeep)
            }
        }
        item.findViewById<TextView>(android.R.id.message)
            ?.setTextColor(createFocusInkList(ActiveHookTheme.accentDeep))
    }

    /**
     * Applies the dialog squircle as a window background, keeping the original background's insets
     * (e.g. DeviceDefault's 16dp dialog_background_material inset) so dialog geometry is unchanged.
     */
    internal fun styleDialogWindowBackground(window: Window) {
        val density = window.context.resources.displayMetrics.density
        val insets = synchronized(dialogWindowInsets) {
            dialogWindowInsets.getOrPut(window) {
                Rect().also { window.decorView.background?.getPadding(it) }
            }
        }
        val squircle = createDialogSquircleDrawable(density)
        val hasInsets = (insets.left or insets.top or insets.right or insets.bottom) != 0
        window.setBackgroundDrawable(
            if (hasInsets) InsetDrawable(squircle, insets.left, insets.top, insets.right, insets.bottom) else squircle
        )
    }

    /**
     * Stage H2: SystemUIDialog and AlertDialog popups inside com.android.systemui (low battery,
     * QS security footer, data saver, Bluetooth, volume safety warning, media projection prompts...).
     * SystemUIDialog is a plain AlertDialog (Theme.SystemUI.Dialog -> DeviceDefault.Light.Dialog), so
     * Dialog.onStart is hooked: AlertController content exists by then, and the hook only runs in this process.
     */
    private fun hookSystemDialogs() {
        try {
            XposedHelpers.findAndHookMethod(
                Dialog::class.java,
                "onStart",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dialog = param.thisObject as? AlertDialog ?: return
                        try {
                            if (styleSystemDialog(dialog)) {
                                trackedSystemDialogs.add(dialog)
                                ensureReceiverRegistered(dialog.context.applicationContext ?: dialog.context)
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in Dialog.onStart hook for ${dialog.javaClass.name}", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: Dialog.onStart (SystemUIDialog / AlertDialog)")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook Dialog.onStart", t)
        }
    }

    /**
     * Styles a stock AlertController dialog. Returns false (and leaves the dialog untouched) for
     * subclasses that replace the content with their own layout, e.g. MediaOutputBaseDialog / PrivacyDialog.
     */
    internal fun styleSystemDialog(dialog: AlertDialog): Boolean {
        val window = dialog.window ?: return false
        val res = dialog.context.resources
        val parentPanelId = res.getIdentifier("parentPanel", "id", "android")
        if (parentPanelId == 0 || dialog.findViewById<View>(parentPanelId) == null) return false

        val density = res.displayMetrics.density
        styleDialogWindowBackground(window)

        val titleId = res.getIdentifier("alertTitle", "id", "android")
        if (titleId != 0) dialog.findViewById<TextView>(titleId)?.setTextColor(ActiveHookTheme.accentDeep)
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(ActiveHookTheme.accentDeep)
        // Custom setView() content keeps stock inks otherwise, which vanish on dark-palette containers.
        dialog.findViewById<View>(android.R.id.custom)?.let { tintDialogCustomContent(it) }

        val margin = (4f * density).toInt()
        for (which in intArrayOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL
        )) {
            val button = dialog.getButton(which) ?: continue
            button.background = createFocusSquircleDrawable(density, CONTROL_CORNER_DP * density, ActiveHookTheme.container)
            button.setTextColor(createFocusInkList(ActiveHookTheme.accentDeep))
            (button.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.marginStart = margin
                lp.marginEnd = margin
                button.layoutParams = lp
            }
        }
        return true
    }

    private fun tintDialogCustomContent(view: View) {
        when (view) {
            is CompoundButton -> {
                view.buttonTintList = ActiveHookTheme.accentDeepColorStateList
                view.setTextColor(ActiveHookTheme.accentDeep)
            }
            is Button -> Unit // Custom buttons keep their own styling.
            is TextView -> view.setTextColor(ActiveHookTheme.accentDeep)
            is ViewGroup -> for (i in 0 until view.childCount) tintDialogCustomContent(view.getChildAt(i))
        }
    }

    /**
     * Stage H3: Screenshot corner preview shelf (ScreenshotView / ScreenshotActionChip).
     * - ScreenshotView.onFinishInflate(): squircle actions-shelf background and preview border.
     * - ScreenshotActionChip.onFinishInflate(): primary chips (Share / Edit / Scroll and smart actions)
     *   with a crisp accentDeep ring while pressed or pending.
     */
    private fun hookScreenshot(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.screenshot.ScreenshotView",
                classLoader,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View ?: return
                            ensureReceiverRegistered(view.context.applicationContext ?: view.context)
                            trackedScreenshotViews.add(view)
                            styleScreenshotView(view)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in ScreenshotView.onFinishInflate hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: ScreenshotView.onFinishInflate")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook ScreenshotView.onFinishInflate", t)
        }

        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.screenshot.ScreenshotActionChip",
                classLoader,
                "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val chip = param.thisObject as? View ?: return
                            trackedScreenshotChips.add(chip)
                            styleScreenshotChip(chip)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in ScreenshotActionChip.onFinishInflate hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: ScreenshotActionChip.onFinishInflate")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook ScreenshotActionChip.onFinishInflate", t)
        }
    }

    internal fun styleScreenshotView(root: View) {
        val res = root.resources
        val density = res.displayMetrics.density
        fun find(name: String): View? {
            val id = res.getIdentifier(name, "id", TARGET_PACKAGE)
            return if (id != 0) root.findViewById(id) else null
        }

        val shelf = find("global_screenshot_actions_container_background")
        if (shelf != null) {
            val radiusId = res.getIdentifier("screenshot_action_container_corner_radius", "dimen", TARGET_PACKAGE)
            val radius = if (radiusId != 0) res.getDimension(radiusId) else DIALOG_CORNER_DP * density
            shelf.background = createDialogSquircleDrawable(density, radius)
        } else {
            Log.d(TAG, "ScreenshotView: global_screenshot_actions_container_background not found")
        }

        val border = find("global_screenshot_preview_border")
        if (border != null) {
            // 24dp matches the literal corner radius in the stock screenshot_border.xml.
            border.background = createDialogSquircleDrawable(density, 24f * density)
        } else {
            Log.d(TAG, "ScreenshotView: global_screenshot_preview_border not found")
        }
    }

    internal fun styleScreenshotChip(chip: View) {
        // The chip's first child is the LinearLayout carrying the stock action_chip_background ripple.
        val content = (chip as? ViewGroup)?.getChildAt(0) ?: return
        val res = chip.resources
        val density = res.displayMetrics.density
        val radiusId = res.getIdentifier("screenshot_button_corner_radius", "dimen", TARGET_PACKAGE)
        val radius = if (radiusId != 0) res.getDimension(radiusId) else CONTROL_CORNER_DP * density
        content.background = createFocusSquircleDrawable(density, radius, ActiveHookTheme.primary)

        val iconId = res.getIdentifier("screenshot_action_chip_icon", "id", TARGET_PACKAGE)
        val icon = if (iconId != 0) chip.findViewById<ImageView>(iconId) else null
        // setIcon(icon, tinted = false) clears the tint for full-color app icons; respect that.
        if (icon != null && icon.imageTintList != null) {
            icon.imageTintList = ActiveHookTheme.onPrimaryColorStateList
        }
        val textId = res.getIdentifier("screenshot_action_chip_text", "id", TARGET_PACKAGE)
        if (textId != 0) chip.findViewById<TextView>(textId)?.setTextColor(ActiveHookTheme.onPrimary)
    }
}

/**
 * Pure helper for formatting toast messages with Keitai glyph framing.
 * Kept independent of Xposed classes so it can be verified in standard JVM unit tests.
 */
internal object ToastTextFormatter {
    fun format(rawText: CharSequence?): String {
        val text = rawText?.toString().orEmpty()
        return if (text.isNotEmpty() && !text.contains("♡")) {
            "♡ $text ♡"
        } else {
            text
        }
    }
}
