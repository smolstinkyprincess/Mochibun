package com.crinklecodone.mochibun

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PixelFormat
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
import java.nio.ByteBuffer
import kotlin.math.roundToInt

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

    var currentX: Float = 240f
        private set
    var currentY: Float = 400f
        private set

    var hotspotAlignment: HotspotAlignment = HotspotAlignment.TOP_LEFT
        private set

    var cursorSizePx: Int = 32
        private set

    var gifPlaybackSpeed: Float = CursorPreferences.DEFAULT_GIF_PLAYBACK_SPEED
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
     * Re-reads SharedPreferences settings (size, URI, hotspot alignment, gif playback speed)
     * and refreshes the overlay rendering and dimensions.
     */
    fun reloadConfiguration() {
        val sizeDp = CursorPreferences.getCursorSizeDp(context)
        val density = context.resources.displayMetrics.density
        cursorSizePx = (sizeDp * density).toInt().coerceIn(16, 128)
        hotspotAlignment = CursorPreferences.getHotspotAlignment(context)
        gifPlaybackSpeed = CursorPreferences.getGifPlaybackSpeed(context)

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
     * Patches the Graphic Control Extension (GCE) delay bytes in a GIF file byte stream
     * so that playback speed matches the user's chosen playback speed slider.
     */
    private fun patchGifPlaybackSpeed(rawBytes: ByteArray, speed: Float): ByteArray {
        if (rawBytes.size < 13) return rawBytes
        // Verify GIF header (GIF87a or GIF89a)
        if (rawBytes[0] != 'G'.code.toByte() || rawBytes[1] != 'I'.code.toByte() || rawBytes[2] != 'F'.code.toByte()) {
            return rawBytes
        }
        val output = rawBytes.clone()
        val safeSpeed = speed.coerceIn(0.1f, 3.0f)
        var i = 0
        val limit = output.size - 7
        while (i < limit) {
            // GCE block signature: 0x21, 0xF9, 0x04
            if (output[i] == 0x21.toByte() &&
                output[i + 1] == 0xF9.toByte() &&
                output[i + 2] == 0x04.toByte()
            ) {
                val low = output[i + 4].toInt() and 0xFF
                val high = output[i + 5].toInt() and 0xFF
                val oldDelay = low or (high shl 8)
                // When original delay is 0 (like in cursor.gif) or too small (<2), default base delay is 10 (100ms / 10 fps)
                val baseDelay = if (oldDelay >= 2) oldDelay else 10
                val newDelay = (baseDelay / safeSpeed).roundToInt().coerceIn(1, 65535)

                output[i + 4] = (newDelay and 0xFF).toByte()
                output[i + 5] = ((newDelay shr 8) and 0xFF).toByte()
                i += 7
            } else {
                i++
            }
        }
        return output
    }

    private fun decodeToDrawable(rawBytes: ByteArray): Drawable {
        val patched = patchGifPlaybackSpeed(rawBytes, gifPlaybackSpeed)
        val source = ImageDecoder.createSource(ByteBuffer.wrap(patched))
        val decoded = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }

        if (decoded is AnimatedImageDrawable) {
            decoded.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            decoded.start()
        } else if (decoded is BitmapDrawable) {
            // Disable bilinear filtering to keep pixel art crisp
            decoded.paint.isFilterBitmap = false
            decoded.paint.isDither = false
        }
        return decoded
    }

    private fun loadCursorAsset(uri: Uri?) {
        // Stop any running animated drawable
        (currentDrawable as? AnimatedImageDrawable)?.stop()

        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val decoded = decodeToDrawable(bytes)
                    currentDrawable = decoded
                    imageView.setImageDrawable(decoded)
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load custom cursor URI ($uri), using default: ${e.message}")
            }
        }

        // Fallback to default cursor.gif located in res/drawable-nodpi/
        try {
            @Suppress("ResourceType")
            val bytes = context.resources.openRawResource(R.drawable.cursor).use { it.readBytes() }
            val decoded = decodeToDrawable(bytes)
            currentDrawable = decoded
            imageView.setImageDrawable(decoded)
            return
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load default R.drawable.cursor: ${e.message}")
        }

        // Secondary fallback to programmatic kawaii pixel cursor
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
     */
    fun updatePosition(newX: Float, newY: Float, windowManager: WindowManager? = null) {
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
        imageView.translationX = renderX
        imageView.translationY = renderY
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
        (currentDrawable as? AnimatedImageDrawable)?.stop()
        imageView.setImageDrawable(null)
        currentDrawable = null
        defaultCursorBitmap?.recycle()
        defaultCursorBitmap = null
    }
}
