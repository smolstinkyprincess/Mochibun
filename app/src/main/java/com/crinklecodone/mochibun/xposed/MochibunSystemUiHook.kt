package com.crinklecodone.mochibun.xposed

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.ui.theme.ColorContrast
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

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
            try { primaryColorStateList = ColorStateList.valueOf(value) } catch (_: Throwable) {}
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
            try { accentDeepColorStateList = ColorStateList.valueOf(value) } catch (_: Throwable) {}
            recomputeDerivedInks()
        }
    @Volatile var track: Int = DEFAULT_TRACK
        set(value) {
            field = value
            try { trackColorStateList = ColorStateList.valueOf(value) } catch (_: Throwable) {}
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
                (0xEB shl 24) or (value and 0x00FFFFFF)
            } else {
                value
            }
        }
    @Volatile var lowBattery: Int = DEFAULT_LOW_BATTERY

    val notificationRows: MutableSet<View> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))

    // Pre-allocated / pre-cached ColorStateLists to avoid allocations on draw
    @Volatile var primaryColorStateList: ColorStateList? = try { ColorStateList.valueOf(DEFAULT_PRIMARY) } catch (_: Throwable) { null }
        internal set
    @Volatile var trackColorStateList: ColorStateList? = try { ColorStateList.valueOf(DEFAULT_TRACK) } catch (_: Throwable) { null }
        internal set
    @Volatile var accentDeepColorStateList: ColorStateList? = try { ColorStateList.valueOf(DEFAULT_ACCENT_DEEP) } catch (_: Throwable) { null }
        internal set
    @Volatile var onPrimaryColorStateList: ColorStateList? = try { ColorStateList.valueOf(DEFAULT_ACCENT_DEEP) } catch (_: Throwable) { null }
        internal set
    @Volatile var onTrackColorStateList: ColorStateList? = try { ColorStateList.valueOf(DEFAULT_ACCENT_DEEP) } catch (_: Throwable) { null }
        internal set

    private fun recomputeDerivedInks() {
        val newOnPrimary = ColorContrast.legibleInk(primary, accentDeep, container)
        if (newOnPrimary != onPrimary) {
            onPrimary = newOnPrimary
            try { onPrimaryColorStateList = ColorStateList.valueOf(newOnPrimary) } catch (_: Throwable) {}
        }
        val newOnTrack = ColorContrast.legibleInk(track, accentDeep, container)
        if (newOnTrack != onTrack) {
            onTrack = newOnTrack
            try { onTrackColorStateList = ColorStateList.valueOf(newOnTrack) } catch (_: Throwable) {}
        }
        statusIconDarkTone = ColorContrast.darkerOf(accentDeep, container)
        statusIconLightTone = ColorContrast.lighterOf(accentDeep, container)
    }

    // Alpha-derived dynamic colors for Volume dialog and Quick Settings
    val volumeStroke: Int
        get() = (0x66 shl 24) or (accentDeep and 0x00FFFFFF)

    val qsTileStroke: Int
        get() = (0x4D shl 24) or (accentDeep and 0x00FFFFFF)

    val qsTileUnavailableBg: Int
        get() = (0x66 shl 24) or (track and 0x00FFFFFF)

    val qsTileInactiveFg: Int
        get() = (0x99 shl 24) or (accentDeep and 0x00FFFFFF)

    val qsTileUnavailableFg: Int
        get() = (0x4D shl 24) or (accentDeep and 0x00FFFFFF)

    val qsFooterBtnStroke: Int
        get() = (0x4D shl 24) or (accentDeep and 0x00FFFFFF)

    val toastStroke: Int
        get() = (0x4D shl 24) or (accentDeep and 0x00FFFFFF)

    val lockPearlStroke: Int
        get() = (0x4D shl 24) or (accentDeep and 0x00FFFFFF)

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
 */
class MochibunSystemUiHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "MochibunSysUI"
        private const val TARGET_PACKAGE = "com.android.systemui"
        /** Notification.COLOR_INVALID: CachingIconView reports this for legacy, non-colorable small icons. */
        private const val NOTIFICATION_COLOR_INVALID = 1

        // Canonical action and extra keys for dynamic theme synchronization
        const val ACTION_THEME_CHANGED = "com.crinklecodone.mochibun.action.THEME_CHANGED"
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

        /**
         * Creates the circular Volume dialog sound-mode (ringer) button background:
         * - Shape: GradientDrawable.OVAL
         * - Fill: ActiveHookTheme.primary (replaces stock ?android:attr/colorAccent)
         * - Stroke: 1.2dp * density of accentDeep at ~30% alpha
         */
        internal fun createRingerCircleDrawable(density: Float): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ActiveHookTheme.primary)
                val strokeWidth = (1.2f * density).toInt().coerceAtLeast(1)
                setStroke(strokeWidth, (0x4D shl 24) or (ActiveHookTheme.accentDeep and 0x00FFFFFF))
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

        // Stage F Lockscreen Greeting & Pearl Affordance
        const val CARRIER_GREETING = "— ♡ Welcome home, emii ♡ —"

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

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val isReceiverRegistered = AtomicBoolean(false)
    private val hooksInstalled = AtomicBoolean(false)

    // Weak sets for tracking active hooked view instances across SystemUI
    private val trackedDarkDispatchers = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
    private val trackedBatteryViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
    private val trackedVolumeDialogs = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
    private val trackedTileViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val trackedBrightnessSliders = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val trackedFooters = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val trackedScrimControllers = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<Any, Boolean>()))
    private val trackedToastViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val trackedClockViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    private val trackedCarrierTexts = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    private val trackedLockIconViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val trackedPasswordViews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val trackedNumPadKeys = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))
    private val trackedNumPadButtons = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ImageView, Boolean>()))
    private val trackedEmergencyButtons = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    private val trackedMessageAreas = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))
    private val trackedNotificationFooters = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<View, Boolean>()))

    @Volatile
    private var trackedNotificationPanelView: WeakReference<View>? = null

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
    private fun ensureReceiverRegistered(context: Context) {
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

                Log.d(TAG, "Successfully refreshed all hooked SystemUI surfaces with active theme")
            } catch (t: Throwable) {
                Log.e(TAG, "Error executing refreshAllHookedViews", t)
            }
        }
    }

    private fun refreshHookedViews() = refreshAllHookedViews()

    private fun applyHeartBattery(instance: Any) {
        val textView = XposedHelpers.getObjectField(instance, "mBatteryPercentView") as? TextView ?: return
        val level = XposedHelpers.getIntField(instance, "mLevel")
        val charging = XposedHelpers.getBooleanField(instance, "mCharging")

        textView.text = getBatteryHearts(level, charging)

        if (level <= 15 && !charging) {
            textView.setTextColor(ActiveHookTheme.lowBattery)
        } else {
            val textColor = XposedHelpers.getIntField(instance, "mTextColor")
            if (textColor != 0) {
                textView.setTextColor(textColor)
            } else {
                textView.setTextColor(ActiveHookTheme.accentDeep)
            }
        }
    }

    private fun hideBatteryIcon(instance: Any) {
        val iconView = XposedHelpers.getObjectField(instance, "mBatteryIconView") as? View
        iconView?.visibility = View.GONE
    }

    private fun styleVolumeRow(row: Any) {
        val slider = XposedHelpers.getObjectField(row, "slider") as? SeekBar
        val icon = XposedHelpers.getObjectField(row, "icon") as? ImageButton
        val sliderProgressSolid = XposedHelpers.getObjectField(row, "sliderProgressSolid") as? Drawable
        val sliderBgSolid = XposedHelpers.getObjectField(row, "sliderBgSolid") as? Drawable
        val sliderProgressIcon = XposedHelpers.getObjectField(row, "sliderProgressIcon") as? Drawable
        val sliderBgIcon = XposedHelpers.getObjectField(row, "sliderBgIcon") as? Drawable

        sliderProgressSolid?.setTintList(ActiveHookTheme.primaryColorStateList)
        slider?.progressTintList = ActiveHookTheme.primaryColorStateList

        sliderBgSolid?.setTintList(ActiveHookTheme.trackColorStateList)
        slider?.progressBackgroundTintList = ActiveHookTheme.trackColorStateList
        val progressDrawable = slider?.progressDrawable as? LayerDrawable
        progressDrawable?.findDrawableByLayerId(android.R.id.background)?.setTintList(ActiveHookTheme.trackColorStateList)

        icon?.imageTintList = ActiveHookTheme.accentDeepColorStateList
        icon?.imageAlpha = 255
        sliderProgressIcon?.setTintList(ActiveHookTheme.onPrimaryColorStateList)
        sliderBgIcon?.setTintList(ActiveHookTheme.onTrackColorStateList)

        val number = XposedHelpers.getObjectField(row, "number") as? TextView
        number?.setTextColor(ActiveHookTheme.accentDeep)
    }

    @android.annotation.SuppressLint("DiscouragedApi")
    private fun applySquircleToDialog(instance: Any) {
        val dialog = XposedHelpers.getObjectField(instance, "mDialog") as? Dialog ?: return
        val context = dialog.context
        val res = context.resources
        val density = res.displayMetrics.density

        fun findView(idName: String): View? {
            val id = res.getIdentifier(idName, "id", TARGET_PACKAGE)
            return if (id != 0) dialog.findViewById(id) else null
        }

        val topContainer = findView("volume_dialog_top_container")
        if (topContainer != null) {
            topContainer.background = createSquircleDrawable(density)
            topContainer.clipToOutline = true
        }

        val ringerDrawerContainer = findView("volume_ringer_and_drawer_container")
        ringerDrawerContainer?.background = LayerDrawable(arrayOf(ColorDrawable(0)))

        val settingsContainer = findView("settings_container")
        settingsContainer?.background = ColorDrawable(0)

        val captionsView = findView("odi_captions")
        if (captionsView != null) {
            captionsView.background = createSquircleDrawable(density)
            captionsView.clipToOutline = true
        }

        val isDrawerOpen = try {
            XposedHelpers.getBooleanField(instance, "mIsRingerDrawerOpen")
        } catch (_: Throwable) {
            false
        }
        if (!isDrawerOpen) {
            val drawerContainer = findView("volume_drawer_container")
            drawerContainer?.visibility = View.GONE
        }

        val settingsIcon = findView("settings") as? ImageButton
        settingsIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        val ringerMuteIcon = findView("volume_drawer_mute_icon") as? ImageView
        ringerMuteIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        val ringerVibrateIcon = findView("volume_drawer_vibrate_icon") as? ImageView
        ringerVibrateIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        val ringerNormalIcon = findView("volume_drawer_normal_icon") as? ImageView
        ringerNormalIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        val captionsIcon = findView("odi_captions_icon") as? ImageView
        captionsIcon?.imageTintList = ActiveHookTheme.accentDeepColorStateList

        styleRingerButton(instance)
    }

    /**
     * Styles the volume dialog's sound-mode (ringer) button.
     * On this Android 12 build the visible button is the ringer drawer's
     * `volume_new_ringer_active_icon_container` (stock: colorAccent circle) holding
     * `volume_new_ringer_active_icon` (stock: textColorPrimaryInverse glyph). The open drawer's
     * `volume_drawer_selection_background` uses the same accent circle. The legacy `ringer` /
     * `ringer_icon` pair is `visibility="gone"` in the stock layout but is styled too in case a
     * ROM variant shows it. Each view is looked up separately so a missing field is just skipped.
     * SystemUI never resets these backgrounds after initDialog, so frequent callers
     * (updateRingerH) pass includeBackgrounds = false and only re-tint the glyphs.
     */
    private fun styleRingerButton(instance: Any, includeBackgrounds: Boolean = true) {
        fun field(name: String): View? = try {
            XposedHelpers.getObjectField(instance, name) as? View
        } catch (_: Throwable) {
            null
        }

        val circleViews = if (!includeBackgrounds) emptyList() else listOf(
            field("mSelectedRingerContainer"),
            field("mRingerDrawerNewSelectionBg"),
            field("mRinger"),
        )
        for (view in circleViews) {
            if (view == null) continue
            try {
                view.background = createRingerCircleDrawable(view.resources.displayMetrics.density)
                view.backgroundTintList = null
                view.clipToOutline = true
            } catch (t: Throwable) {
                Log.d(TAG, "Failed styling volume ringer circle", t)
            }
        }

        for (name in listOf("mSelectedRingerIcon", "mRingerIcon")) {
            try {
                (field(name) as? ImageView)?.imageTintList = ActiveHookTheme.onPrimaryColorStateList
            } catch (t: Throwable) {
                Log.d(TAG, "Failed tinting volume ringer glyph $name", t)
            }
        }
    }

    private fun refreshTileView(tileView: View) {
        val stateInt = try {
            val s = XposedHelpers.getIntField(tileView, "lastState")
            if (s == -1) {
                val tileState = try { XposedHelpers.getBooleanField(tileView, "tileState") } catch (_: Throwable) { false }
                if (tileState) 2 else 1
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

        val fillColor = when (stateInt) {
            2 -> ActiveHookTheme.primary
            1 -> ActiveHookTheme.container
            0 -> ActiveHookTheme.qsTileUnavailableBg
            else -> ActiveHookTheme.container
        }
        val fgColor = when (stateInt) {
            2 -> ActiveHookTheme.onPrimary
            1 -> ActiveHookTheme.qsTileInactiveFg
            0 -> ActiveHookTheme.qsTileUnavailableFg
            else -> ActiveHookTheme.qsTileInactiveFg
        }

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

    private fun styleBrightnessSlider(sliderView: View) {
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

    private fun styleFooterView(footerView: View) {
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
                                installHooksIfEnabled(app, lpparam.classLoader)
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
                                    lpparam.classLoader
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

    private fun installHooksIfEnabled(context: Context, classLoader: ClassLoader) {
        if (!isSystemUiHookEnabled(context)) {
            Log.i(TAG, "SystemUI styling hooks disabled by ${PreferencesManager.GLOBAL_HOOK_SYSUI_ENABLED}")
            return
        }
        if (!hooksInstalled.compareAndSet(false, true)) return

        ensureReceiverRegistered(context)
        hookStatusBar(classLoader)
        hookBattery(classLoader)
        hookVolume(classLoader)
        hookQuickSettings(classLoader)
        hookNotifications(classLoader)
        hookToast(classLoader)
        hookLockscreen(classLoader)
        hookPinBouncer(classLoader)
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
     * A2: Hook carrier and operator name text.
     * Displays Mochibun's signature greeting ("— ♡ Welcome home, emii ♡ —") on lockscreen CarrierText,
     * while suppressing operator name in the status bar (OperatorNameView).
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
                                val view = param.thisObject as? TextView ?: return
                                view.visibility = View.VISIBLE
                                view.text = CARRIER_GREETING
                                view.setTextColor(ActiveHookTheme.accentDeep)
                                trackedCarrierTexts.add(view)
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
                            val view = param.thisObject as? TextView ?: return
                            view.visibility = View.VISIBLE
                            view.text = CARRIER_GREETING
                            view.setTextColor(ActiveHookTheme.accentDeep)
                            trackedCarrierTexts.add(view)
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
                            param.args[1] = View.VISIBLE
                            val view = param.thisObject as? TextView
                            if (view != null) {
                                view.text = CARRIER_GREETING
                                view.setTextColor(ActiveHookTheme.accentDeep)
                                trackedCarrierTexts.add(view)
                            }
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

        // 2. Hook CarrierTextManager to supply Mochibun greeting
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
                                XposedHelpers.setObjectField(info, "carrierText", CARRIER_GREETING)
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
                                trackedDarkDispatchers.add(param.thisObject)
                                XposedHelpers.setIntField(
                                    param.thisObject,
                                    "mDarkModeIconColorSingleTone",
                                    ActiveHookTheme.statusIconDarkTone
                                )
                                XposedHelpers.setIntField(
                                    param.thisObject,
                                    "mLightModeIconColorSingleTone",
                                    ActiveHookTheme.statusIconLightTone
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
     * - Tint rose (ActiveHookTheme.lowBattery) when level <= 15 and not charging.
     */
    private fun hookBattery(classLoader: ClassLoader) {
        val batteryMeterViewClassName = "com.android.systemui.BatteryMeterView"

        // 1. Hook BatteryMeterView constructors to force percent mode and hide icon
        try {
            val clazz = XposedHelpers.findClass(batteryMeterViewClassName, classLoader)
            for (constructor in clazz.declaredConstructors) {
                XposedBridge.hookMethod(
                    constructor,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                trackedBatteryViews.add(param.thisObject)
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
                            trackedBatteryViews.add(param.thisObject)
                            val view = param.thisObject as? View
                            if (view != null) {
                                ensureReceiverRegistered(view.context.applicationContext ?: view.context)
                            }
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

        // 5. Hook updateColors to preserve low battery rose tint across dark-mode transitions
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
                                textView?.setTextColor(ActiveHookTheme.lowBattery)
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
     * - Apply a GradientDrawable (corner radius 20dp * density, fill ActiveHookTheme.container,
     *   1.5dp stroke ActiveHookTheme.volumeStroke) to dialog content views by id name (do NOT touch window background).
     * - Style volume rows (initRow & updateVolumeRowTintH):
     *   slider progress tint ActiveHookTheme.primaryColorStateList,
     *   slider track tint ActiveHookTheme.trackColorStateList,
     *   icons ActiveHookTheme.accentDeepColorStateList.
     * - Re-apply on updates without allocating drawables per update.
     */
    @android.annotation.SuppressLint("DiscouragedApi")
    private fun hookVolume(classLoader: ClassLoader) {
        val volumeDialogImplClassName = "com.android.systemui.volume.VolumeDialogImpl"
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
                            trackedVolumeDialogs.add(param.thisObject)
                            val dialog = XposedHelpers.getObjectField(param.thisObject, "mDialog") as? Dialog
                            val context = dialog?.context
                            if (context != null) {
                                ensureReceiverRegistered(context.applicationContext ?: context)
                            }
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

        // 7a. Hook updateRingerH so ringer mode changes (Ring / Vibrate / Silent) keep the themed button
        try {
            XposedHelpers.findAndHookMethod(
                volumeDialogImplClassName,
                classLoader,
                "updateRingerH",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            styleRingerButton(param.thisObject, includeBackgrounds = false)
                        } catch (t: Throwable) {
                            Log.d(TAG, "Error in VolumeDialogImpl.updateRingerH hook", t)
                        }
                    }
                }
            )
            Log.d(TAG, "Installed hook: VolumeDialogImpl.updateRingerH")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to hook VolumeDialogImpl.updateRingerH", t)
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
                            // Force active stream to STREAM_MUSIC (3) whenever the dialog is shown
                            XposedHelpers.setIntField(param.thisObject, "mActiveStream", android.media.AudioManager.STREAM_MUSIC)

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

    private fun styleNotificationRow(row: View) {
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
                val border = (0x4D shl 24) or (ActiveHookTheme.accentDeep and 0x00FFFFFF)
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
            val border = (0x4D shl 24) or (ActiveHookTheme.accentDeep and 0x00FFFFFF)
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
        val viewName = try {
            if (root.id != View.NO_ID) root.resources.getResourceEntryName(root.id) else null
        } catch (_: Throwable) {
            null
        }
        if (viewName == targetName) return root

        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findNotificationViewByName(root.getChildAt(index), targetName)?.let { return it }
            }
        }
        return null
    }

    private fun styleNotificationContentTree(view: View, inActionContainer: Boolean) {
        val idName = try {
            if (view.id != View.NO_ID) view.resources.getResourceEntryName(view.id).lowercase() else ""
        } catch (_: Throwable) {
            ""
        }
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
                    view.setTextColor((0xD9 shl 24) or (ActiveHookTheme.accentDeep and 0x00FFFFFF))
                idName.contains("app_name") || idName.contains("header") ||
                    idName.contains("time") || idName.contains("chronometer") ->
                    view.setTextColor((0xA6 shl 24) or (ActiveHookTheme.accentDeep and 0x00FFFFFF))
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
            val idName = try {
                if (root.id != View.NO_ID) root.resources.getResourceEntryName(root.id).lowercase() else ""
            } catch (_: Throwable) {
                ""
            }
            styleNotificationSmallIconView(root, idName)
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

    private fun styleNotificationFooter(footer: View) {
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
            val button = dismissButton as? TextView ?: return
            val density = resources.displayMetrics.density
            val border = (0x4D shl 24) or (ActiveHookTheme.accentDeep and 0x00FFFFFF)
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * density
                setColor(ActiveHookTheme.container)
                setStroke((1.2f * density).toInt().coerceAtLeast(1), border)
            }
            button.text = "♡ Clear All ♡"
            button.setTextColor(ActiveHookTheme.accentDeep)
            button.clipToOutline = true
            button.invalidate()
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
                        param.result = when (state) {
                            2 -> ActiveHookTheme.primary
                            1 -> ActiveHookTheme.container
                            0 -> ActiveHookTheme.qsTileUnavailableBg
                            else -> ActiveHookTheme.container
                        }
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
                        param.result = when (state) {
                            2 -> ActiveHookTheme.onPrimary
                            1 -> ActiveHookTheme.qsTileInactiveFg
                            0 -> ActiveHookTheme.qsTileUnavailableFg
                            else -> ActiveHookTheme.qsTileInactiveFg
                        }
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
                        param.result = when (state) {
                            2 -> ActiveHookTheme.onPrimary
                            1 -> ActiveHookTheme.qsTileInactiveFg
                            0 -> ActiveHookTheme.qsTileUnavailableFg
                            else -> ActiveHookTheme.qsTileInactiveFg
                        }
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
                        param.result = when (state) {
                            2 -> ActiveHookTheme.onPrimary
                            1 -> ActiveHookTheme.qsTileInactiveFg
                            0 -> ActiveHookTheme.qsTileUnavailableFg
                            else -> ActiveHookTheme.qsTileInactiveFg
                        }
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
                        param.result = when (state) {
                            2 -> ActiveHookTheme.onPrimary
                            1 -> ActiveHookTheme.qsTileInactiveFg
                            0 -> ActiveHookTheme.qsTileUnavailableFg
                            else -> ActiveHookTheme.qsTileInactiveFg
                        }
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

                            val fillColor = when (stateInt) {
                                2 -> ActiveHookTheme.primary
                                1 -> ActiveHookTheme.container
                                0 -> ActiveHookTheme.qsTileUnavailableBg
                                else -> ActiveHookTheme.container
                            }
                            val fgColor = when (stateInt) {
                                2 -> ActiveHookTheme.onPrimary
                                1 -> ActiveHookTheme.qsTileInactiveFg
                                0 -> ActiveHookTheme.qsTileUnavailableFg
                                else -> ActiveHookTheme.qsTileInactiveFg
                            }

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

    private fun findTextViewRecursive(view: View): TextView? {
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
    private fun styleNumPadKey(key: View) {
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
    private fun styleNumPadButton(button: ImageView) {
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
    private fun styleEmergencyButton(button: TextView) {
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
    private fun styleMessageArea(messageArea: TextView) {
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
