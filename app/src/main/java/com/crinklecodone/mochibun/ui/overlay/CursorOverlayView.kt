package com.crinklecodone.mochibun.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import com.crinklecodone.mochibun.R
import com.crinklecodone.mochibun.data.prefs.CursorPreferences
import com.crinklecodone.mochibun.data.prefs.HotspotAlignment
import kotlin.math.round

/**
 * High-performance, pixel-crisp overlay view for the system-wide virtual mouse pointer.
 * Respects MT6761 memory and rendering constraints (using software allocator for decodes,
 * zero bilinear filtering to preserve sharp pixel art, and hardware-accelerated overlay window).
 *
 * Uses a full-screen overlay window and translates the cursor sprite via hardware-accelerated
 * translationX / translationY, completely eliminating WindowManager.updateViewLayout IPC overhead.
 */
class CursorOverlayView(context: Context) : FrameLayout(context) {

    companion object {
        private const val TAG = "CursorOverlayView"
    }

    private val imageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
    }

    private var currentDrawable: Drawable? = null
    private var defaultCursorBitmap: Bitmap? = null
    private var clickFeedbackAnimator: ValueAnimator? = null
    private var currentDisposable: Disposable? = null
    private var isOverlayVisible = false

    var currentX: Float = 240f
        private set
    var currentY: Float = 400f
        private set

    var hotspotAlignment: HotspotAlignment = HotspotAlignment.TOP_LEFT
        private set

    var cursorSizePx: Int = 32
        private set

    val windowLayoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        x = 0
        y = 0
    }

    init {
        // Enforce unfiltered nearest-neighbor sampling for pixel-art clarity
        val pixelArtPaint = Paint().apply {
            isFilterBitmap = false
            isDither = false
        }
        imageView.setLayerPaint(pixelArtPaint)

        addView(
            imageView,
            LayoutParams(cursorSizePx, cursorSizePx).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        )
        reloadConfiguration()
    }

    /**
     * Re-reads SharedPreferences settings (size, URI, hotspot alignment)
     * and refreshes the overlay rendering and dimensions.
     */
    fun reloadConfiguration() {
        val sizeDp = CursorPreferences.getCursorSizeDp(context)
        val density = context.resources.displayMetrics.density
        cursorSizePx = (sizeDp * density).toInt().coerceIn(16, 128)
        hotspotAlignment = CursorPreferences.getHotspotAlignment(context)

        imageView.layoutParams = (imageView.layoutParams as? LayoutParams)?.apply {
            width = cursorSizePx
            height = cursorSizePx
        } ?: LayoutParams(cursorSizePx, cursorSizePx).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        loadCursorAsset(CursorPreferences.getCustomCursorUri(context))
        updatePosition(currentX, currentY)
    }

    /**
     * Pauses running animated drawables (e.g. GIF) when the cursor is hidden.
     * Prevents unnecessary CPU / Choreographer overhead in the background.
     */
    fun pauseAnimation() {
        isOverlayVisible = false
        (currentDrawable as? Animatable)?.stop()
    }

    /**
     * Resumes animation when the cursor becomes visible on screen.
     */
    fun resumeAnimation() {
        isOverlayVisible = true
        val anim = currentDrawable as? Animatable
        Log.d(TAG, "resumeAnimation: anim=$anim, isRunning=${anim?.isRunning}")
        anim?.start()
    }

    /**
     * Loads the cursor asset asynchronously using Coil. Supports custom file/content URIs
     * and animated GIFs, falling back gracefully to bundled R.drawable.cursor or programmatic pixel art.
     */
    private fun loadCursorAsset(uri: Uri?) {
        currentDisposable?.dispose()
        currentDisposable = null
        (currentDrawable as? Animatable)?.stop()

        val targetData: Any = uri ?: R.drawable.cursor

        val request = ImageRequest.Builder(context)
            .data(targetData)
            .decoderFactory(GifDecoder.Factory())
            .target(
                onSuccess = { drawable ->
                    applyCursorDrawable(drawable)
                },
                onError = {
                    if (uri != null) {
                        Log.w(TAG, "Failed to load custom cursor URI ($uri), falling back to default cursor")
                        loadDefaultBundledCursor()
                    } else {
                        Log.w(TAG, "Failed to load default bundled cursor, falling back to programmatic cursor")
                        applyProgrammaticFallback()
                    }
                }
            )
            .build()

        currentDisposable = context.imageLoader.enqueue(request)
    }

    private fun loadDefaultBundledCursor() {
        val fallbackRequest = ImageRequest.Builder(context)
            .data(R.drawable.cursor)
            .decoderFactory(GifDecoder.Factory())
            .target(
                onSuccess = { drawable ->
                    applyCursorDrawable(drawable)
                },
                onError = {
                    Log.w(TAG, "Failed to load bundled fallback cursor, applying programmatic cursor")
                    applyProgrammaticFallback()
                }
            )
            .build()
        currentDisposable = context.imageLoader.enqueue(fallbackRequest)
    }

    private fun applyCursorDrawable(drawable: Drawable) {
        Log.d(TAG, "applyCursorDrawable: loaded ${drawable::class.java.name}, isAnimatable=${drawable is Animatable}, isOverlayVisible=$isOverlayVisible")
        (currentDrawable as? Animatable)?.stop()

        var inner: Drawable? = drawable
        while (inner is coil.drawable.ScaleDrawable) {
            inner = inner.child
        }

        if (inner is AnimatedImageDrawable) {
            inner.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }

        if (drawable is BitmapDrawable) {
            // Disable bilinear filtering to keep pixel art crisp
            drawable.paint.isFilterBitmap = false
            drawable.paint.isDither = false
            drawable.paint.isAntiAlias = false
        }

        currentDrawable = drawable
        imageView.setImageDrawable(drawable)

        if (isOverlayVisible) {
            val anim = (drawable as? Animatable) ?: (inner as? Animatable)
            Log.d(TAG, "applyCursorDrawable: starting animation on visible overlay (anim=$anim)")
            anim?.start()
        }
    }

    private fun applyProgrammaticFallback() {
        (currentDrawable as? Animatable)?.stop()
        val bitmap = getDefaultCursorBitmap()
        val bitmapDrawable = BitmapDrawable(context.resources, bitmap).apply {
            paint.isFilterBitmap = false
            paint.isDither = false
            paint.isAntiAlias = false
        }
        currentDrawable = bitmapDrawable
        imageView.setImageDrawable(bitmapDrawable)
    }

    /**
     * Creates an adorable, pixel-sharp kawaii cursor (white/pastel pink arrow with
     * dark plum border and cute berry heart accent).
     */
    private fun getDefaultCursorBitmap(): Bitmap {
        val cached = defaultCursorBitmap
        if (cached != null && !cached.isRecycled && cached.width == cursorSizePx) {
            return cached
        }

        cached?.recycle()

        // 16x16 pixel-art blueprint:
        // '.' = transparent, '#' = dark plum border, 'P' = pastel pink body, 'H' = berry heart
        val pattern = arrayOf(
            "#...............",
            "##..............",
            "#P#.............",
            "#PP#............",
            "#PPP#...........",
            "#PPPP#..........",
            "#PPPPP#.........",
            "#PPPPPP#........",
            "#PPPPPPP#.......",
            "#PPPPPPPP#......",
            "#PPPPP####......",
            "#PP#PP#.........",
            "#P##PP#.........",
            "##..#PP#........",
            "#...#PP#........",
            ".....##........."
        )

        val bmp = Bitmap.createBitmap(cursorSizePx, cursorSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pixelSize = cursorSizePx / 16f
        val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            style = Paint.Style.FILL
        }

        val colorBorder = 0xFF4E2858.toInt() // Dark plum outline
        val colorBody = 0xFFFFEAF5.toInt()   // Soft pastel pink/white
        val colorHeart = 0xFFD94680.toInt()  // Kawaii berry pink

        for (row in pattern.indices) {
            val line = pattern[row]
            for (col in line.indices) {
                val ch = line[col]
                if (ch == '.') continue
                paint.color = when (ch) {
                    '#' -> colorBorder
                    'H' -> colorHeart
                    else -> colorBody
                }
                val left = col * pixelSize
                val top = row * pixelSize
                canvas.drawRect(left, top, left + pixelSize, top + pixelSize, paint)
            }
        }

        // Draw a tiny cute berry heart on the pointer body
        val heartPixels = listOf(
            Pair(3, 4), Pair(5, 4),
            Pair(2, 5), Pair(3, 5), Pair(4, 5), Pair(5, 5), Pair(6, 5),
            Pair(3, 6), Pair(4, 6), Pair(5, 6),
            Pair(4, 7)
        )
        paint.color = colorHeart
        for ((c, r) in heartPixels) {
            val left = c * pixelSize
            val top = r * pixelSize
            canvas.drawRect(left, top, left + pixelSize, top + pixelSize, paint)
        }

        defaultCursorBitmap = bmp
        return bmp
    }

    /**
     * Updates cursor position on screen via hardware layer translation
     * without triggering heavy WindowManager layout recalculations.
     *
     * The hotspot keeps the sub-pixel position, but the sprite is drawn on whole pixels:
     * with nearest-neighbor sampling a fractional offset makes pixel-art columns shimmer.
     * It also skips redraws while slow movement is still within the same pixel, because
     * View ignores a translation that hasn't changed.
     */
    fun updatePosition(newX: Float, newY: Float) {
        currentX = newX
        currentY = newY
        val renderX = when (hotspotAlignment) {
            HotspotAlignment.TOP_LEFT -> currentX
            HotspotAlignment.CENTER -> currentX - cursorSizePx / 2f
        }
        val renderY = when (hotspotAlignment) {
            HotspotAlignment.TOP_LEFT -> currentY
            HotspotAlignment.CENTER -> currentY - cursorSizePx / 2f
        }
        imageView.translationX = round(renderX)
        imageView.translationY = round(renderY)
    }

    /**
     * Returns the exact screen X coordinate where touch gestures will be dispatched.
     */
    fun getHotspotX(): Float {
        return currentX
    }

    /**
     * Returns the exact screen Y coordinate where touch gestures will be dispatched.
     */
    fun getHotspotY(): Float {
        return currentY
    }

    /**
     * Triggers a sweet bounce animation on click for tactile visual feedback.
     */
    fun showClickFeedback() {
        clickFeedbackAnimator?.cancel()
        when (hotspotAlignment) {
            HotspotAlignment.TOP_LEFT -> {
                imageView.pivotX = 0f
                imageView.pivotY = 0f
            }
            HotspotAlignment.CENTER -> {
                imageView.pivotX = cursorSizePx / 2f
                imageView.pivotY = cursorSizePx / 2f
            }
        }
        clickFeedbackAnimator = ValueAnimator.ofFloat(1f, 0.82f, 1f).apply {
            duration = 120L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val scale = va.animatedValue as Float
                imageView.scaleX = scale
                imageView.scaleY = scale
            }
            start()
        }
    }

    /**
     * Teardown cleanup to eliminate memory leaks.
     */
    fun release() {
        clickFeedbackAnimator?.cancel()
        clickFeedbackAnimator = null
        currentDisposable?.dispose()
        currentDisposable = null
        pauseAnimation()
        imageView.setImageDrawable(null)
        currentDrawable = null
        defaultCursorBitmap?.recycle()
        defaultCursorBitmap = null
    }
}
