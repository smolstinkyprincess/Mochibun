package one.crinklecod.mochibun.xposed

import android.content.Context
import android.content.res.ColorStateList
import android.provider.Settings
import android.util.Log
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.xposed.systemui.LockscreenHooks
import one.crinklecod.mochibun.xposed.systemui.QuickSettingsHooks
import one.crinklecod.mochibun.xposed.systemui.StatusBarHooks
import one.crinklecod.mochibun.xposed.systemui.SystemDialogsPowerHooks
import one.crinklecod.mochibun.xposed.systemui.SystemUiHookRegistry
import one.crinklecod.mochibun.xposed.systemui.TAG
import one.crinklecod.mochibun.xposed.systemui.TARGET_PACKAGE
import one.crinklecod.mochibun.xposed.systemui.VolumeDialogHooks
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Source-compatibility aliases: the hook implementation lives in the `xposed.systemui` package,
 * but unit tests and older imports address these helpers from `one.crinklecod.mochibun.xposed`.
 */
typealias ActiveHookTheme = one.crinklecod.mochibun.xposed.systemui.ActiveHookTheme
internal typealias BatteryHeartFormatter = one.crinklecod.mochibun.xposed.systemui.BatteryHeartFormatter
internal typealias ToastTextFormatter = one.crinklecod.mochibun.xposed.systemui.ToastTextFormatter

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
 * - Stage D: Kawaii Quick Settings & shade restyle (scrim & blur suppression, squircle tiles
 *            in 3 pastel states, and pastel brightness slider tinting).
 * - Dynamic Theme Synchronization: Listens for explicit THEME_CHANGED broadcasts from Mochibun,
 *            updating ActiveHookTheme in-memory and re-rendering active hooked views instantly.
 *
 * Each stage lives in its own module under `xposed/systemui/` (StatusBarHooks, VolumeDialogHooks,
 * QuickSettingsHooks, LockscreenHooks, SystemDialogsPowerHooks; shared state in ActiveHookTheme.kt).
 * This class stays the LSPosed entry point registered in assets/xposed_init and only coordinates.
 */
class MochibunSystemUiHook : IXposedHookLoadPackage {

    companion object {
        // Canonical action and extra keys for dynamic theme synchronization
        const val ACTION_THEME_CHANGED = "one.crinklecod.mochibun.action.THEME_CHANGED"
        const val EXTRA_PRIMARY = "EXTRA_PRIMARY"
        const val EXTRA_CONTAINER = "EXTRA_CONTAINER"
        const val EXTRA_ACCENT_DEEP = "EXTRA_ACCENT_DEEP"
        const val EXTRA_TRACK = "EXTRA_TRACK"
        const val EXTRA_SCRIM = "EXTRA_SCRIM"

        // Stage A3 fallback palette colors
        internal const val COLOR_DARK_MODE_DEEP_PLUM = 0xFF4A2E56.toInt()
        internal const val COLOR_LIGHT_MODE_PALE_PINK = 0xFFFFE4F0.toInt()

        // Stage B fallback palette colors
        internal const val COLOR_ROSE_LOW_BATTERY = 0xFFF06292.toInt()

        // Stage C fallback palette colors & pre-allocated tints
        internal const val COLOR_VOLUME_BG_FILL = 0xFFF6F0FC.toInt()
        internal const val COLOR_VOLUME_STROKE = 0x664A2E56.toInt()

        // Stage D fallback palette colors (Quick Settings & Shade)
        internal const val COLOR_QS_BACKDROP_TINT = 0xEBF6F0FC.toInt()
        internal const val COLOR_QS_TILE_STROKE = 0x4D4A2E56.toInt()

        internal const val COLOR_QS_TILE_ACTIVE_BG = 0xFFFFB7D5.toInt()        // Sakura Pink #FFB7D5
        internal const val COLOR_QS_TILE_INACTIVE_BG = 0xFFFFF9FB.toInt()      // Cream / Soft Surface #FFF9FB
        internal const val COLOR_QS_TILE_UNAVAILABLE_BG = 0x66EADDF8.toInt()   // Muted Soft Lilac #EADDF8 at 40%

        internal const val COLOR_QS_TILE_ACTIVE_FG = 0xFF4A2E56.toInt()        // Deep Plum #4A2E56
        internal const val COLOR_QS_TILE_INACTIVE_FG = 0x994A2E56.toInt()      // Deep Plum at 60% alpha
        internal const val COLOR_QS_TILE_UNAVAILABLE_FG = 0x4D4A2E56.toInt()   // Deep Plum at 30% alpha

        internal const val COLOR_BRIGHTNESS_PROGRESS = 0xFFFFB7D5.toInt()      // Sakura Pink #FFB7D5
        internal const val COLOR_BRIGHTNESS_TRACK = 0xFFEADDF8.toInt()         // Soft Lilac #EADDF8
        internal const val COLOR_BRIGHTNESS_ICON = 0xFF4A2E56.toInt()          // Deep Plum #4A2E56

        // Quick Settings footer action button styling (Edit, Power, Settings)
        internal const val COLOR_QS_FOOTER_BTN_BG = 0xFFFFF9FB.toInt()         // Soft Cream #FFF9FB
        internal const val COLOR_QS_FOOTER_BTN_STROKE = 0x4D4A2E56.toInt()     // Deep Plum #4A2E56 at 30% alpha
        internal const val COLOR_QS_FOOTER_BTN_ICON = 0xFF4A2E56.toInt()       // Deep Plum #4A2E56
        internal val QS_FOOTER_BTN_ICON_TINT_LIST = ColorStateList.valueOf(COLOR_QS_FOOTER_BTN_ICON)

        // Toast fallback palette colors (Stage E)
        internal const val COLOR_TOAST_BG = 0xFFFFF9FB.toInt()                 // Soft Cream #FFF9FB
        internal const val COLOR_TOAST_STROKE = 0x4D4A2E56.toInt()             // Deep Plum #4A2E56 at 30% alpha
        internal const val COLOR_TOAST_TEXT = 0xFF4A2E56.toInt()               // Deep Plum #4A2E56


        // Stage F Lockscreen Greeting & Pearl Affordance
        const val CARRIER_GREETING = "— ♡ Welcome home, emii ♡ —"

        // Stage H corner radii (Power Menu, SystemUI dialogs, screenshot shelf)
        internal const val DIALOG_CORNER_DP = 22f
        internal const val CONTROL_CORNER_DP = 16f
    }

    private val hooksInstalled = AtomicBoolean(false)

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        Log.d(TAG, "Initializing MochibunSystemUiHook for package: ${lpparam.packageName}")

        // SystemUIApplication provides a resolver that can read the root-controlled kill switch.
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.SystemUIApplication",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val app = param.thisObject as? Context
                            if (app != null) {
                                installHooksIfEnabled(app, lpparam)
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in SystemUIApplication.onCreate hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: SystemUIApplication.onCreate")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook SystemUIApplication.onCreate", t)
        }

        // Fallback startup point for ROMs that bypass SystemUIApplication.onCreate.
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                lpparam.classLoader,
                "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val view = param.thisObject as? View
                            val context = view?.context
                            if (context != null) {
                                installHooksIfEnabled(
                                    context.applicationContext ?: context,
                                    lpparam
                                )
                            }
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in PhoneStatusBarView.onAttachedToWindow hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed fallback hook: PhoneStatusBarView.onAttachedToWindow")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook PhoneStatusBarView.onAttachedToWindow", t)
        }
    }

    private fun installHooksIfEnabled(context: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!isSystemUiHookEnabled(context)) {
            Log.i(TAG, "SystemUI styling hooks disabled by ${PreferencesManager.GLOBAL_HOOK_SYSUI_ENABLED}")
            return
        }
        if (!hooksInstalled.compareAndSet(false, true)) return

        SystemUiHookRegistry.ensureReceiverRegistered(context)
        // Each module wraps every individual hook in its own try/catch, so one missing OEM
        // class never prevents the remaining modules from installing.
        StatusBarHooks.install(lpparam)
        VolumeDialogHooks.install(lpparam)
        QuickSettingsHooks.install(lpparam)
        SystemDialogsPowerHooks.installToasts(lpparam)
        LockscreenHooks.install(lpparam)
        SystemDialogsPowerHooks.install(lpparam)
    }

    private fun isSystemUiHookEnabled(context: Context): Boolean =
        try {
            Settings.Global.getInt(
                context.contentResolver,
                PreferencesManager.GLOBAL_HOOK_SYSUI_ENABLED,
                1
            ) == 1
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read SystemUI hook switch; keeping hooks enabled", t)
            true
        }
}
