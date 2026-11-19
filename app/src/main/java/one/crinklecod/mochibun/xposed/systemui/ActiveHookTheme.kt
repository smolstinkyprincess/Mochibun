package one.crinklecod.mochibun.xposed.systemui

import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import one.crinklecod.mochibun.ui.theme.withAlpha
import one.crinklecod.mochibun.ui.theme.ColorContrast
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.ACTION_THEME_CHANGED
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.CARRIER_GREETING
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.DIALOG_CORNER_DP
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.EXTRA_ACCENT_DEEP
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.EXTRA_CONTAINER
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.EXTRA_PRIMARY
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.EXTRA_SCRIM
import one.crinklecod.mochibun.xposed.MochibunSystemUiHook.Companion.EXTRA_TRACK
import one.crinklecod.mochibun.xposed.systemui.LockscreenHooks.styleEmergencyButton
import one.crinklecod.mochibun.xposed.systemui.LockscreenHooks.styleLockIconView
import one.crinklecod.mochibun.xposed.systemui.LockscreenHooks.styleMessageArea
import one.crinklecod.mochibun.xposed.systemui.LockscreenHooks.styleNumPadButton
import one.crinklecod.mochibun.xposed.systemui.LockscreenHooks.styleNumPadKey
import one.crinklecod.mochibun.xposed.systemui.QuickSettingsHooks.refreshTileView
import one.crinklecod.mochibun.xposed.systemui.QuickSettingsHooks.styleBrightnessSlider
import one.crinklecod.mochibun.xposed.systemui.QuickSettingsHooks.styleFooterView
import one.crinklecod.mochibun.xposed.systemui.QuickSettingsHooks.styleNotificationFooter
import one.crinklecod.mochibun.xposed.systemui.QuickSettingsHooks.styleNotificationRow
import one.crinklecod.mochibun.xposed.systemui.StatusBarHooks.applyHeartBattery
import one.crinklecod.mochibun.xposed.systemui.SystemDialogsPowerHooks.styleDialogWindowBackground
import one.crinklecod.mochibun.xposed.systemui.SystemDialogsPowerHooks.stylePowerMenuItem
import one.crinklecod.mochibun.xposed.systemui.SystemDialogsPowerHooks.stylePowerMenuPanel
import one.crinklecod.mochibun.xposed.systemui.SystemDialogsPowerHooks.styleScreenshotChip
import one.crinklecod.mochibun.xposed.systemui.SystemDialogsPowerHooks.styleScreenshotView
import one.crinklecod.mochibun.xposed.systemui.SystemDialogsPowerHooks.styleSystemDialog
import one.crinklecod.mochibun.xposed.systemui.SystemDialogsPowerHooks.styleToastView
import one.crinklecod.mochibun.xposed.systemui.VolumeDialogHooks.applySquircleToDialog
import one.crinklecod.mochibun.xposed.systemui.VolumeDialogHooks.styleVolumeRow

internal const val TAG = "MochibunSysUI"
internal const val TARGET_PACKAGE = "com.android.systemui"

internal fun cslOrNull(color: Int): ColorStateList? = try {
    ColorStateList.valueOf(color)
} catch (_: Throwable) {
    null
}

/** Resource entry name of this view's id, or null for NO_ID / ids missing from its resources. */
internal fun View.entryNameOrNull(): String? = try {
    if (id != View.NO_ID) resources.getResourceEntryName(id) else null
} catch (_: Throwable) {
    null
}

/**
 * In-memory active theme cache for Mochibun's SystemUI hooks running inside com.android.systemui.
 * Stores volatile primitive ARGB integers and pre-cached ColorStateLists.
 * Ensures zero object allocations during frame/draw callbacks.
 */
object ActiveHookTheme {
    const val DEFAULT_PRIMARY: Int = 0xFFFFB7D5.toInt()        // Sakura Pink #FFB7D5
    const val DEFAULT_CONTAINER: Int = 0xFFFFF9FB.toInt()      // Cream / Soft Surface #FFF9FB
    const val DEFAULT_ACCENT_DEEP: Int = 0xFF4A2E56.toInt()    // Deep Plum #4A2E56
    const val DEFAULT_TRACK: Int = 0xFFEADDF8.toInt()          // Soft Lilac #EADDF8
    const val DEFAULT_SCRIM: Int = 0xEBF6F0FC.toInt()          // Frosted Ivory / Lavender ~92%
    const val DEFAULT_LOW_BATTERY: Int = 0xFFF06292.toInt()    // Rose #F06292

    @Volatile var primary: Int = DEFAULT_PRIMARY
        set(value) {
            field = value
            primaryColorStateList = cslOrNull(value)
            recomputeDerivedInks()
        }
    @Volatile var container: Int = DEFAULT_CONTAINER
        set(value) {
            field = value
            recomputeDerivedInks()
        }
    @Volatile var accentDeep: Int = DEFAULT_ACCENT_DEEP
        set(value) {
            field = value
            accentDeepColorStateList = cslOrNull(value)
            recomputeDerivedInks()
        }
    @Volatile var track: Int = DEFAULT_TRACK
        set(value) {
            field = value
            trackColorStateList = cslOrNull(value)
            recomputeDerivedInks()
        }

    /*
     * Contrast-safe inks derived from the broadcast colors. Light palettes ink everything with
     * accentDeep, but dark palettes (e.g. Midnight Mochi) broadcast a *light* accentDeep together
     * with a light primary fill, so glyphs/labels drawn on primary (active QS tiles, ringer pill,
     * slider icons, notification actions) switch to the dark container color instead.
     * Recomputed only when a color changes, never during draw.
     */

    /** Ink for content drawn on a [primary] fill. Equals accentDeep for the built-in light palettes. */
    @Volatile var onPrimary: Int = DEFAULT_ACCENT_DEEP
        private set

    /** Ink for content drawn on the [track] fill (e.g. volume slider icon at low levels). */
    @Volatile var onTrack: Int = DEFAULT_ACCENT_DEEP
        private set

    /** Status bar icon tone on light backgrounds (always the darker of accentDeep / container). */
    @Volatile var statusIconDarkTone: Int = DEFAULT_ACCENT_DEEP
        private set

    /** Status bar icon tone on dark backgrounds (always the lighter of accentDeep / container). */
    @Volatile var statusIconLightTone: Int = DEFAULT_CONTAINER
        private set
    @Volatile var scrim: Int = DEFAULT_SCRIM
        set(value) {
            field = if ((value ushr 24) == 0xFF) {
                value.withAlpha(0xEB)
            } else {
                value
            }
        }
    @Volatile var lowBattery: Int = DEFAULT_LOW_BATTERY

    val notificationRows: MutableSet<View> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))

    // Pre-allocated / pre-cached ColorStateLists to avoid allocations on draw
    @Volatile var primaryColorStateList: ColorStateList? = cslOrNull(DEFAULT_PRIMARY)
        internal set
    @Volatile var trackColorStateList: ColorStateList? = cslOrNull(DEFAULT_TRACK)
        internal set
    @Volatile var accentDeepColorStateList: ColorStateList? = cslOrNull(DEFAULT_ACCENT_DEEP)
        internal set
    @Volatile var onPrimaryColorStateList: ColorStateList? = cslOrNull(DEFAULT_ACCENT_DEEP)
        internal set
    @Volatile var onTrackColorStateList: ColorStateList? = cslOrNull(DEFAULT_ACCENT_DEEP)
        internal set

    private fun recomputeDerivedInks() {
        val newOnPrimary = ColorContrast.legibleInk(primary, accentDeep, container)
        if (newOnPrimary != onPrimary) {
            onPrimary = newOnPrimary
            onPrimaryColorStateList = cslOrNull(newOnPrimary)
        }
        val newOnTrack = ColorContrast.legibleInk(track, accentDeep, container)
        if (newOnTrack != onTrack) {
            onTrack = newOnTrack
            onTrackColorStateList = cslOrNull(newOnTrack)
        }
        statusIconDarkTone = ColorContrast.darkerOf(accentDeep, container)
        statusIconLightTone = ColorContrast.lighterOf(accentDeep, container)
    }

    // Alpha-derived dynamic colors for Volume dialog and Quick Settings
    val volumeStroke: Int
        get() = accentDeep.withAlpha(0x66)

    val qsTileStroke: Int
        get() = accentDeep.withAlpha(0x4D)

    val qsTileUnavailableBg: Int
        get() = track.withAlpha(0x66)

    val qsTileInactiveFg: Int
        get() = accentDeep.withAlpha(0x99)

    val qsTileUnavailableFg: Int
        get() = accentDeep.withAlpha(0x4D)

    val qsFooterBtnStroke: Int
        get() = accentDeep.withAlpha(0x4D)

    val toastStroke: Int
        get() = accentDeep.withAlpha(0x4D)

    val lockPearlStroke: Int
        get() = accentDeep.withAlpha(0x4D)

    /** Soft border for Power Menu, SystemUI dialog and screenshot shelf containers. */
    val dialogStroke: Int
        get() = accentDeep.withAlpha(0x4D)

    // QSTile.State.state values (Tile.STATE_UNAVAILABLE / INACTIVE / ACTIVE)
    const val QS_STATE_UNAVAILABLE = 0
    const val QS_STATE_INACTIVE = 1
    const val QS_STATE_ACTIVE = 2

    fun qsTileFill(state: Int): Int = when (state) {
        QS_STATE_ACTIVE -> primary
        QS_STATE_UNAVAILABLE -> qsTileUnavailableBg
        else -> container
    }

    fun qsTileFg(state: Int): Int = when (state) {
        QS_STATE_ACTIVE -> onPrimary
        QS_STATE_UNAVAILABLE -> qsTileUnavailableFg
        else -> qsTileInactiveFg
    }

    /**
     * Calculates the target alpha for the lockscreen NotificationPanelView based on bouncer expansion:
     * - expansion = 1.0f: bouncer hidden at bottom -> panelView.alpha = 1.0f (clock & notifs 100% visible)
     * - expansion = 0.0f: bouncer fully open -> panelView.alpha = 0.0f (only wallpaper & floating PIN keys visible)
     */
    fun calculateBouncerFadeAlpha(expansion: Float): Float {
        return expansion.coerceIn(0f, 1f)
    }

    fun updateColors(
        newPrimary: Int,
        newContainer: Int,
        newAccentDeep: Int,
        newTrack: Int,
        newScrim: Int,
        newLowBattery: Int = DEFAULT_LOW_BATTERY
    ) {
        primary = newPrimary
        container = newContainer
        accentDeep = newAccentDeep
        track = newTrack
        scrim = newScrim
        lowBattery = newLowBattery
    }
}

/**
 * Process-wide state shared by every SystemUI hook module: the theme broadcast receiver and the
 * weak registries of hooked views that [refreshAllHookedViews] re-styles on a palette change.
 * Weak sets so SystemUI can freely discard views; never holds strong references.
 */
internal object SystemUiHookRegistry {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val isReceiverRegistered = AtomicBoolean(false)

    // Weak sets for tracking active hooked view instances across SystemUI
    internal val trackedDarkDispatchers = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
    internal val trackedBatteryViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
    internal val trackedVolumeDialogs = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
    internal val trackedTileViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    internal val trackedBrightnessSliders = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    internal val trackedFooters = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    internal val trackedScrimControllers = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
    internal val trackedToastViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    internal val trackedClockViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    internal val trackedCarrierTexts = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    internal val trackedLockIconViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    internal val trackedPasswordViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    internal val trackedNumPadKeys = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    internal val trackedNumPadButtons = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ImageView, Boolean>()))
    internal val trackedEmergencyButtons = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    internal val trackedMessageAreas = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    internal val trackedNotificationFooters = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    internal val trackedPowerMenuDialogs = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Dialog, Boolean>()))
    internal val trackedPowerOptionsDialogs = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Dialog, Boolean>()))
    /** Power Menu tiles -> true when the tile is an emergency action (keeps its stock red inks). */
    internal val trackedPowerMenuItems = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    internal val trackedSystemDialogs = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<AlertDialog, Boolean>()))
    internal val trackedScreenshotViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    internal val trackedScreenshotChips = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    /** Original window-background insets per dialog window, so restyling never compounds the inset. */
    internal val dialogWindowInsets = Collections.synchronizedMap(WeakHashMap<Window, Rect>())

    @Volatile
    internal var trackedNotificationPanelView: WeakReference<View>? = null

    private val themeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent == null || intent.action != ACTION_THEME_CHANGED) return

            Log.d(TAG, "Theme broadcast received! Updating colors.")
            val primary = intent.getIntExtra(EXTRA_PRIMARY, ActiveHookTheme.DEFAULT_PRIMARY)
            val container = intent.getIntExtra(EXTRA_CONTAINER, ActiveHookTheme.DEFAULT_CONTAINER)
            val accentDeep = intent.getIntExtra(EXTRA_ACCENT_DEEP, ActiveHookTheme.DEFAULT_ACCENT_DEEP)
            val track = intent.getIntExtra(EXTRA_TRACK, ActiveHookTheme.DEFAULT_TRACK)
            val scrim = intent.getIntExtra(EXTRA_SCRIM, ActiveHookTheme.DEFAULT_SCRIM)

            Log.d(
                TAG,
                "Received THEME_CHANGED broadcast: primary=#%08X container=#%08X accentDeep=#%08X track=#%08X scrim=#%08X".format(
                    primary, container, accentDeep, track, scrim
                )
            )

            ActiveHookTheme.primary = primary
            ActiveHookTheme.container = container
            ActiveHookTheme.accentDeep = accentDeep
            ActiveHookTheme.track = track
            ActiveHookTheme.scrim = scrim

            // Trigger invalidate and state re-application on all cached views
            refreshAllHookedViews()
        }
    }

    /**
     * Registers a dynamic BroadcastReceiver in com.android.systemui for ACTION_THEME_CHANGED.
     * Guaranteed to run at most once.
     */
    @android.annotation.SuppressLint("WrongConstant", "UnprotectedReceiver", "NewApi")
    internal fun ensureReceiverRegistered(context: Context) {
        if (isReceiverRegistered.compareAndSet(false, true)) {
            val filter = IntentFilter(ACTION_THEME_CHANGED)
            try {
                // Use Context.RECEIVER_EXPORTED (value 2) for cross-process broadcasts on Android 12+
                context.registerReceiver(themeReceiver, filter, Context.RECEIVER_EXPORTED)
                Log.d(TAG, "Theme broadcast receiver successfully registered (EXPORTED)")
            } catch (_: NoSuchMethodError) {
                try {
                    context.registerReceiver(themeReceiver, filter)
                    Log.d(TAG, "Theme broadcast receiver successfully registered (legacy fallback)")
                } catch (t: Throwable) {
                    isReceiverRegistered.set(false)
                    Log.e(TAG, "Failed to register theme broadcast receiver (legacy fallback)", t)
                }
            } catch (t: Throwable) {
                try {
                    context.registerReceiver(themeReceiver, filter)
                    Log.d(TAG, "Theme broadcast receiver successfully registered (fallback)")
                } catch (inner: Throwable) {
                    isReceiverRegistered.set(false)
                    Log.e(TAG, "Failed to register theme broadcast receiver", inner)
                }
            }
        }
    }

    /**
     * Re-renders all cached active hooked views across SystemUI with the new ActiveHookTheme colors.
     */
    private fun refreshAllHookedViews() {
        mainHandler.post {
            try {
                // 1. Status Bar Icons & Clock (DarkIconDispatcherImpl)
                for (dispatcher in trackedDarkDispatchers) {
                    try {
                        XposedHelpers.setIntField(dispatcher, "mDarkModeIconColorSingleTone", ActiveHookTheme.statusIconDarkTone)
                        XposedHelpers.setIntField(dispatcher, "mLightModeIconColorSingleTone", ActiveHookTheme.statusIconLightTone)
                        val intensity = try {
                            XposedHelpers.getFloatField(dispatcher, "mDarkIntensity")
                        } catch (_: Throwable) {
                            0f
                        }
                        XposedHelpers.callMethod(dispatcher, "applyDarkIntensity", intensity)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing DarkIconDispatcherImpl", t)
                    }
                }

                // 2. Battery Meter (BatteryMeterView)
                for (battery in trackedBatteryViews) {
                    try {
                        applyHeartBattery(battery)
                        (battery as? View)?.invalidate()
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing BatteryMeterView", t)
                    }
                }

                // 3. Volume Dialog (VolumeDialogImpl)
                for (dialogImpl in trackedVolumeDialogs) {
                    try {
                        applySquircleToDialog(dialogImpl)
                        val rows = XposedHelpers.getObjectField(dialogImpl, "mRows") as? List<*>
                        if (rows != null) {
                            for (row in rows) {
                                if (row != null) {
                                    styleVolumeRow(row)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing VolumeDialogImpl", t)
                    }
                }

                // 4. Quick Settings Tiles (QSTileViewImpl)
                for (tileView in trackedTileViews) {
                    try {
                        refreshTileView(tileView)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing QSTileViewImpl", t)
                    }
                }

                // 5. Brightness Slider (BrightnessSliderView)
                for (sliderView in trackedBrightnessSliders) {
                    try {
                        styleBrightnessSlider(sliderView)
                        sliderView.invalidate()
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing BrightnessSliderView", t)
                    }
                }

                // 6. QS Footer Action Buttons (Edit, Power, Settings)
                for (footerView in trackedFooters) {
                    try {
                        styleFooterView(footerView)
                        footerView.invalidate()
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing QSFooterView", t)
                    }
                }

                // 7. Scrim backdrop
                for (scrimController in trackedScrimControllers) {
                    try {
                        val scrimBehind = XposedHelpers.getObjectField(scrimController, "mScrimBehind") as? View
                        val notifScrim = XposedHelpers.getObjectField(scrimController, "mNotificationsScrim") as? View
                        scrimBehind?.invalidate()
                        notifScrim?.invalidate()
                        try {
                            XposedHelpers.callMethod(scrimController, "applyAndDispatchState")
                        } catch (_: Throwable) {}
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing ScrimController", t)
                    }
                }

                // 8. Toasts (SystemUIToast view)
                for (toastView in trackedToastViews) {
                    try {
                        styleToastView(toastView, updateText = false)
                        toastView.invalidate()
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing ToastView", t)
                    }
                }

                // 9. Lockscreen Clock (AnimatableClockView / single-line TextView)
                for (clock in trackedClockViews) {
                    try {
                        try {
                            XposedHelpers.callMethod(clock, "setColors", -1, ActiveHookTheme.accentDeep)
                        } catch (_: Throwable) {}
                        clock.setTextColor(ActiveHookTheme.accentDeep)
                        val textAnimator = try { XposedHelpers.getObjectField(clock, "mTextAnimator") } catch (_: Throwable) { null }
                        if (textAnimator != null) {
                            val interpolator = try { XposedHelpers.getObjectField(textAnimator, "textInterpolator") } catch (_: Throwable) { null }
                            if (interpolator != null) {
                                val targetPaint = try { XposedHelpers.getObjectField(interpolator, "targetPaint") as? android.graphics.Paint } catch (_: Throwable) { null }
                                val basePaint = try { XposedHelpers.getObjectField(interpolator, "basePaint") as? android.graphics.Paint } catch (_: Throwable) { null }
                                val dozingColor = try { XposedHelpers.getIntField(clock, "mDozingColor") } catch (_: Throwable) { -1 }
                                if (targetPaint != null && targetPaint.color != dozingColor) {
                                    targetPaint.color = ActiveHookTheme.accentDeep
                                }
                                if (basePaint != null && basePaint.color != dozingColor) {
                                    basePaint.color = ActiveHookTheme.accentDeep
                                }
                            }
                        }
                        clock.invalidate()
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing lockscreen clock", t)
                    }
                }

                // 10. Lockscreen Carrier Greeting (CarrierText)
                for (carrierText in trackedCarrierTexts) {
                    try {
                        carrierText.text = CARRIER_GREETING
                        carrierText.setTextColor(ActiveHookTheme.accentDeep)
                        carrierText.visibility = View.VISIBLE
                        carrierText.invalidate()
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing CarrierText", t)
                    }
                }

                // 11. Lock Icon Affordance (LockIconView)
                for (lockIconView in trackedLockIconViews) {
                    try {
                        styleLockIconView(lockIconView)
                        lockIconView.invalidate()
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing LockIconView", t)
                    }
                }

                // 12. PIN Bouncer - PasswordTextView (Kawaii Heart Dots)
                for (ptv in trackedPasswordViews) {
                    try {
                        val paint = try { XposedHelpers.getObjectField(ptv, "mDrawPaint") as? Paint } catch (_: Throwable) { null }
                        paint?.color = ActiveHookTheme.accentDeep
                        ptv.invalidate()
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing PasswordTextView", t)
                    }
                }

                // 13. PIN Bouncer - NumPadKey (Pearl Circle Keys)
                for (key in trackedNumPadKeys) {
                    try {
                        styleNumPadKey(key)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing NumPadKey", t)
                    }
                }

                // 14. PIN Bouncer - NumPadButton (Pearl Circle Action Buttons)
                for (button in trackedNumPadButtons) {
                    try {
                        styleNumPadButton(button)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing NumPadButton", t)
                    }
                }

                // 15. PIN Bouncer - EmergencyButton (Pill Button)
                for (button in trackedEmergencyButtons) {
                    try {
                        styleEmergencyButton(button)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing EmergencyButton", t)
                    }
                }

                // 16. PIN Bouncer - KeyguardMessageArea (Message Text)
                for (messageArea in trackedMessageAreas) {
                    try {
                        styleMessageArea(messageArea)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing KeyguardMessageArea", t)
                    }
                }

                // 17. Notification cards and the shade's Clear All button
                val notificationRows = synchronized(ActiveHookTheme.notificationRows) {
                    ActiveHookTheme.notificationRows.toList()
                }
                for (row in notificationRows) {
                    try {
                        styleNotificationRow(row)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing notification row", t)
                    }
                }
                val notificationFooters = synchronized(trackedNotificationFooters) {
                    trackedNotificationFooters.toList()
                }
                for (footer in notificationFooters) {
                    try {
                        styleNotificationFooter(footer)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing notification footer", t)
                    }
                }

                // 18. Power Menu panel, action tiles and the power-options sub-dialog
                for (dialog in synchronized(trackedPowerMenuDialogs) { trackedPowerMenuDialogs.toList() }) {
                    try {
                        stylePowerMenuPanel(dialog)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing Power Menu panel", t)
                    }
                }
                val powerMenuItems = synchronized(trackedPowerMenuItems) { trackedPowerMenuItems.toList() }
                for ((item, isEmergency) in powerMenuItems) {
                    try {
                        stylePowerMenuItem(item, isEmergency)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing Power Menu tile", t)
                    }
                }
                for (dialog in synchronized(trackedPowerOptionsDialogs) { trackedPowerOptionsDialogs.toList() }) {
                    try {
                        dialog.window?.let { styleDialogWindowBackground(it) }
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing Power Options dialog", t)
                    }
                }

                // 19. SystemUIDialog / AlertDialog popups (closed ones restyle on their next onStart)
                for (dialog in synchronized(trackedSystemDialogs) { trackedSystemDialogs.toList() }) {
                    try {
                        if (dialog.isShowing) styleSystemDialog(dialog)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing SystemUI dialog", t)
                    }
                }

                // 20. Screenshot corner preview shelf and its action chips
                for (view in synchronized(trackedScreenshotViews) { trackedScreenshotViews.toList() }) {
                    try {
                        styleScreenshotView(view)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing ScreenshotView", t)
                    }
                }
                for (chip in synchronized(trackedScreenshotChips) { trackedScreenshotChips.toList() }) {
                    try {
                        styleScreenshotChip(chip)
                    } catch (t: Throwable) {
                        Log.d(TAG, "Error refreshing ScreenshotActionChip", t)
                    }
                }

                Log.d(TAG, "Successfully refreshed all hooked SystemUI surfaces with active theme")
            } catch (t: Throwable) {
                Log.e(TAG, "Error executing refreshAllHookedViews", t)
            }
        }
    }

    private fun refreshHookedViews() = refreshAllHookedViews()
}

/** Shared GradientDrawable squircle / pill / focus-ring builders. Built per styling pass, never during draw. */
internal object SystemUiDrawables {
    /**
     * Creates a pill-shaped GradientDrawable for System Toasts:
     * - Corner radius: 22dp * density
     * - Fill: ActiveHookTheme.container
     * - Stroke: 1.2dp * density of ActiveHookTheme.toastStroke (accentDeep at ~30% alpha)
     */
    internal fun createToastPillDrawable(density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 22f * density
            setColor(ActiveHookTheme.container)
            val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
            setStroke(strokeWidth, ActiveHookTheme.toastStroke)
        }
    }

    /**
     * Creates a squircle GradientDrawable for Volume dialog:
     * - Corner radius: 20dp * density
     * - Fill: ActiveHookTheme.container
     * - Stroke: 1.5dp * density of ActiveHookTheme.volumeStroke
     */
    internal fun createSquircleDrawable(density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * density
            setColor(ActiveHookTheme.container)
            val strokeWidth = (1.5f * density).toInt().coerceAtLeast(1)
            setStroke(strokeWidth, ActiveHookTheme.volumeStroke)
        }
    }

    /** Replaces the stock ?colorAccent ringer circle so it follows the Mochibun palette. */
    internal fun createRingerCircleDrawable(density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ActiveHookTheme.primary)
            val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
            setStroke(strokeWidth, ActiveHookTheme.qsTileStroke)
        }
    }

    /**
     * Creates a squircle GradientDrawable for QS tiles:
     * - Shape: Rectangle
     * - Corner radius: 14dp * density
     * - Stroke: 1.2dp * density with color ActiveHookTheme.qsTileStroke
     * - Default color: ActiveHookTheme.container
     */
    internal fun createTileSquircleDrawable(density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
            setStroke(strokeWidth, ActiveHookTheme.qsTileStroke)
            setColor(ActiveHookTheme.container)
        }
    }

    /**
     * Creates a squircle GradientDrawable for QS footer action buttons:
     * - Shape: Rectangle
     * - Corner radius: 12dp * density
     * - Stroke: 1.2dp * density with color ActiveHookTheme.qsFooterBtnStroke
     * - Fill: ActiveHookTheme.container
     */
    internal fun createFooterButtonDrawable(density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * density
            val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
            setStroke(strokeWidth, ActiveHookTheme.qsFooterBtnStroke)
            setColor(ActiveHookTheme.container)
        }
    }

    /**
     * Creates a pearl GradientDrawable for the Keyguard Lock Icon affordance:
     * - Shape: GradientDrawable.OVAL
     * - Fill: ActiveHookTheme.container
     * - Border stroke: 1.2dp * density with color ActiveHookTheme.lockPearlStroke (accentDeep at 30% alpha)
     */
    internal fun createPearlLockDrawable(density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ActiveHookTheme.container)
            val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
            setStroke(strokeWidth, ActiveHookTheme.lockPearlStroke)
        }
    }

    /**
     * Creates the squircle container used behind the Power Menu, SystemUI dialogs and screenshot shelf:
     * - Corner radius: [cornerRadiusPx] (22dp by default)
     * - Fill: ActiveHookTheme.container
     * - Stroke: 1.5dp * density of ActiveHookTheme.dialogStroke (accentDeep at ~30% alpha)
     */
    internal fun createDialogSquircleDrawable(
        density: Float,
        cornerRadiusPx: Float = DIALOG_CORNER_DP * density
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(ActiveHookTheme.container)
            val strokeWidth = (1.5f * density).toInt().coerceAtLeast(1)
            setStroke(strokeWidth, ActiveHookTheme.dialogStroke)
        }
    }

    /**
     * Creates a focus-aware squircle for keypad-navigable controls (Power Menu tiles, dialog
     * buttons, screenshot chips) so the AT-M120 D-pad position is unmistakable:
     * - Focused / pressed: ActiveHookTheme.primary fill with a crisp 2dp accentDeep ring
     * - Resting: [restFill] with a soft 1.2dp accentDeep 30% border
     * Built once per styling pass, never during draw.
     */
    internal fun createFocusSquircleDrawable(
        density: Float,
        cornerRadiusPx: Float,
        restFill: Int
    ): StateListDrawable {
        fun squircle(fill: Int, strokeDp: Float, strokeColor: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(fill)
            setStroke((strokeDp * density).toInt().coerceAtLeast(1), strokeColor)
        }
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                squircle(ActiveHookTheme.primary, 2f, ActiveHookTheme.accentDeep)
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                squircle(ActiveHookTheme.primary, 2f, ActiveHookTheme.accentDeep)
            )
            addState(intArrayOf(), squircle(restFill, 1.2f, ActiveHookTheme.qsTileStroke))
        }
    }

    /**
     * Ink matching [createFocusSquircleDrawable]: ActiveHookTheme.onPrimary while focused,
     * pressed or activated (stays legible on the primary fill in dark palettes), [restInk] otherwise.
     */
    internal fun createFocusInkList(restInk: Int): ColorStateList {
        val active = ActiveHookTheme.onPrimary
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(android.R.attr.state_activated),
                intArrayOf()
            ),
            intArrayOf(active, active, active, restInk)
        )
    }
}
