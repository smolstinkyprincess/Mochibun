package com.crinklecodone.mochibun.ui.components

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.crinklecodone.mochibun.R
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ============================================================================
// Mochibun PixelSpray - Authentic Keitai Pixel-Art Deco Scatter Engine ♡
// ============================================================================

/**
 * Coordinated pastel palette designed for Mochibun's cute aesthetic.
 */
object PastelPalette {
    private val stickerBitmapCache = HashMap<Int, ImageBitmap>()

    @Synchronized
    fun loadSampledStickerBitmap(res: Resources, @DrawableRes resId: Int, maxDimPx: Int = 128): ImageBitmap {
        stickerBitmapCache[resId]?.let { return it }
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, boundsOpts)
        val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        var sampleSize = 1
        while (maxSide / (sampleSize * 2) >= maxDimPx) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeResource(res, resId, decodeOpts)
            .asImageBitmap()
            .also { stickerBitmapCache[resId] = it }
    }

    val Pink         = Color(0xFFFF94BA) // Sweet strawberry pink ♡
    val Lavender     = Color(0xFFC7A4F5) // Dreamy pastel purple
    val BabyBlue     = Color(0xFF90D5FB) // Soft pastel sky blue
    val Mint         = Color(0xFF86E8BA) // Fresh pastel mint green
    val Peach        = Color(0xFFFFB896) // Warm pastel peach
    val ButterYellow = Color(0xFFFFE066) // Cheerful pastel yellow

    val ALL: List<Color> = listOf(
        Pink,
        Lavender,
        BabyBlue,
        Mint,
        Peach,
        ButterYellow
    )
}

/**
 * Strategy controlling how pastel tints are applied to the sticker spray.
 */
enum class PixelSprayTintBehavior {
    ORIGINAL_ONLY,
    TINT_ELIGIBLE_ONLY,
    MIXED,
    ALWAYS_TINT_WHERE_POSSIBLE
}

/**
 * Metadata and rendering configuration for a pixel-art sticker resource.
 */
data class PixelStickerAsset(
    @DrawableRes val resId: Int,
    val name: String,
    val intrinsicWidth: Int,
    val intrinsicHeight: Int,
    val canTint: Boolean,
    val blendMode: BlendMode = BlendMode.Modulate
) {
    val aspectRatio: Float
        get() = if (intrinsicHeight > 0) intrinsicWidth.toFloat() / intrinsicHeight.toFloat() else 1f
}

/**
 * Default sticker assets catalog for Mochibun.
 */
object PixelStickers {
    // Retained strictly for UI headers/badges (not scattered on wallpaper)
    val Bow = PixelStickerAsset(
        resId = R.drawable.bow,
        name = "bow",
        intrinsicWidth = 500,
        intrinsicHeight = 500,
        canTint = false
    )

    // The 11 new 12x12 micro-pixel sprites (drawable-nodpi)
    val BrokenHeart = PixelStickerAsset(R.drawable.brokenheart, "brokenheart", 12, 12, true, BlendMode.SrcAtop)
    val Cat         = PixelStickerAsset(R.drawable.cat, "cat", 12, 12, true, BlendMode.SrcAtop)
    val Crunch      = PixelStickerAsset(R.drawable.crunch, "crunch", 12, 12, true, BlendMode.SrcAtop)
    val Flower      = PixelStickerAsset(R.drawable.flower, "flower", 12, 12, true, BlendMode.SrcAtop)
    val Heart       = PixelStickerAsset(R.drawable.heart, "heart", 12, 12, true, BlendMode.SrcAtop)
    val Hearts      = PixelStickerAsset(R.drawable.hearts, "hearts", 12, 12, true, BlendMode.SrcAtop)
    val Kitty       = PixelStickerAsset(R.drawable.kitty, "kitty", 12, 12, true, BlendMode.SrcAtop)
    val SmallHeart  = PixelStickerAsset(R.drawable.smallheart, "smallheart", 12, 12, true, BlendMode.SrcAtop)
    val Sparkles    = PixelStickerAsset(R.drawable.sparkles, "sparkles", 12, 12, true, BlendMode.SrcAtop)
    val Spiral      = PixelStickerAsset(R.drawable.spiral, "spiral", 12, 12, true, BlendMode.SrcAtop)
    val Zzz         = PixelStickerAsset(R.drawable.zzz, "zzz", 12, 12, true, BlendMode.SrcAtop)

    /**
     * Active wallpaper scatter collections.
     * Contains ONLY the 11 new 12x12 micro-sprites.
     */
    val DEFAULT: List<PixelStickerAsset> = listOf(
        BrokenHeart,
        Cat,
        Crunch,
        Flower,
        Heart,
        Hearts,
        Kitty,
        SmallHeart,
        Sparkles,
        Spiral,
        Zzz
    )

    val ALL: List<PixelStickerAsset> = DEFAULT

    /**
     * An individual positioned sticker within a generated [PixelSpray] layout.
     */
    data class PixelStickerItem(
        val asset: PixelStickerAsset,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val rotation: Float,
        val tintColor: Color?,
        val colorFilter: ColorFilter?
    ) {
        val center: Offset get() = Offset(x, y)
        val topLeft: Offset get() = Offset(x - width / 2f, y - height / 2f)
    }

    // Snapped retro keitai rotations: 0 is weighted higher so stickers look deliberate
    private val SNAPPED_ROTATIONS = floatArrayOf(0f, 0f, 0f, -12f, 12f, -24f, 24f)

    /**
     * Generates a well-dispersed, authentic keitai deco layout.
     * Uses Mitchell's Best-Candidate algorithm, snapped integer pixel scaling,
     * and stepped angles to avoid blurred or sheared pixel art.
     */
    fun generatePixelSprayLayout(
        count: Int,
        areaWidthPx: Float,
        areaHeightPx: Float,
        stickers: List<PixelStickerAsset> = DEFAULT,
        minSizePx: Float = 28f,
        maxSizePx: Float = 68f,
        minRotation: Float = 0f,
        maxRotation: Float = 0f,
        edgePaddingPx: Float = 16f,
        tintBehavior: PixelSprayTintBehavior = PixelSprayTintBehavior.ALWAYS_TINT_WHERE_POSSIBLE,
        tintProbability: Float = 0.5f,
        palette: List<Color> = PastelPalette.ALL,
        seed: Long? = null
    ): List<PixelStickerItem> {
        if (count <= 0 || areaWidthPx <= 0f || areaHeightPx <= 0f || stickers.isEmpty()) {
            return emptyList()
        }

        val random = if (seed != null) Random(seed) else Random.Default
        val actualMinSize = minOf(minSizePx, maxSizePx).coerceAtLeast(1f)
        val actualMaxSize = maxOf(minSizePx, maxSizePx).coerceAtLeast(actualMinSize)
        val actualMinRot = minOf(minRotation, maxRotation)
        val actualMaxRot = maxOf(minRotation, maxRotation)
        val hasCustomRotationRange = (actualMinRot != 0f || actualMaxRot != 0f)

        val items = ArrayList<PixelStickerItem>(count)
        val candidatesCount = 8

        for (i in 0 until count) {
            val asset = stickers[random.nextInt(stickers.size)]

            // Calculate integer-friendly target size to preserve crisp pixel clusters
            val dispW: Float
            val dispH: Float
            val maxIntrinsic = maxOf(asset.intrinsicWidth, asset.intrinsicHeight)

            if (maxIntrinsic <= 32) {
                // Exact integer scale multipliers: 2x (24px), 3x (36px), or 4x (48px)
                val scaleMultipliers = intArrayOf(2, 2, 3, 3, 4) // weights 2x and 3x higher for dainty look
                val scale = scaleMultipliers[random.nextInt(scaleMultipliers.size)]
                dispW = (asset.intrinsicWidth * scale).toFloat()
                dispH = (asset.intrinsicHeight * scale).toFloat()
            } else {
                // High-res sticker (Bow, Heart): preserve aspect ratio within target bounds
                val rawTarget = actualMinSize + random.nextFloat() * (actualMaxSize - actualMinSize)
                val ar = asset.aspectRatio
                if (ar >= 1f) {
                    dispW = rawTarget
                    dispH = rawTarget / ar
                } else {
                    dispW = rawTarget * ar
                    dispH = rawTarget
                }
            }

            // Snapped retro rotation
            val rot = if (!hasCustomRotationRange) {
                SNAPPED_ROTATIONS[random.nextInt(SNAPPED_ROTATIONS.size)]
            } else {
                actualMinRot + random.nextFloat() * (actualMaxRot - actualMinRot)
            }

            // Bounding radius for collision padding
            val radius = sqrt(dispW * dispW + dispH * dispH) / 2f
            val minX = (edgePaddingPx + radius).coerceAtMost(areaWidthPx / 2f)
            val maxX = (areaWidthPx - edgePaddingPx - radius).coerceAtLeast(minX)
            val minY = (edgePaddingPx + radius).coerceAtMost(areaHeightPx / 2f)
            val maxY = (areaHeightPx - edgePaddingPx - radius).coerceAtLeast(minY)

            // Best-Candidate placement
            var bestX = minX + random.nextFloat() * (maxX - minX)
            var bestY = minY + random.nextFloat() * (maxY - minY)

            if (items.isNotEmpty() && maxX > minX && maxY > minY) {
                var bestDist = -1f
                for (c in 0 until candidatesCount) {
                    val candX = minX + random.nextFloat() * (maxX - minX)
                    val candY = minY + random.nextFloat() * (maxY - minY)

                    var minDistToExisting = Float.MAX_VALUE
                    for (existing in items) {
                        val d = hypot(candX - existing.x, candY - existing.y)
                        if (d < minDistToExisting) {
                            minDistToExisting = d
                        }
                    }

                    if (minDistToExisting > bestDist) {
                        bestDist = minDistToExisting
                        bestX = candX
                        bestY = candY
                    }
                }
            }

            // Tint evaluation
            val shouldTint = asset.canTint && when (tintBehavior) {
                PixelSprayTintBehavior.ORIGINAL_ONLY -> false
                PixelSprayTintBehavior.TINT_ELIGIBLE_ONLY -> true
                PixelSprayTintBehavior.MIXED -> random.nextFloat() < tintProbability
                PixelSprayTintBehavior.ALWAYS_TINT_WHERE_POSSIBLE -> true
            }

            val tintColor = if (shouldTint && palette.isNotEmpty()) {
                palette[random.nextInt(palette.size)]
            } else {
                null
            }

            val colorFilter = tintColor?.let { ColorFilter.tint(it, BlendMode.SrcAtop) }

            items.add(
                PixelStickerItem(
                    asset = asset,
                    x = bestX,
                    y = bestY,
                    width = dispW,
                    height = dispH,
                    rotation = rot,
                    tintColor = tintColor,
                    colorFilter = colorFilter
                )
            )
        }

        return items
    }

    /**
     * Scatters decorative pixel-art stickers across the available layout area.
     */
    @Composable
    fun PixelSpray(
        count: Int = 14,
        modifier: Modifier = Modifier.fillMaxSize(),
        stickers: List<PixelStickerAsset> = DEFAULT,
        minSize: Dp = 26.dp,
        maxSize: Dp = 56.dp,
        minRotation: Float = 0f,
        maxRotation: Float = 0f,
        edgePadding: Dp = 20.dp,
        tintBehavior: PixelSprayTintBehavior = PixelSprayTintBehavior.ALWAYS_TINT_WHERE_POSSIBLE,
        tintProbability: Float = 0.5f,
        palette: List<Color> = PastelPalette.ALL,
        seed: Long? = 101L
    ) {
        if (count <= 0 || stickers.isEmpty()) return

        val density = LocalDensity.current
        val minSizePx = with(density) { minSize.toPx() }
        val maxSizePx = with(density) { maxSize.toPx() }
        val edgePaddingPx = with(density) { edgePadding.toPx() }

        val context = LocalContext.current
        val stickerBitmaps = remember(stickers) {
            stickers.associate { asset ->
                asset.resId to PastelPalette.loadSampledStickerBitmap(context.resources, asset.resId, maxDimPx = 128)
            }
        }

        BoxWithConstraints(modifier = modifier) {
            val widthPx = with(density) {
                if (maxWidth != Dp.Infinity && maxWidth > 0.dp) maxWidth.toPx() else 480.dp.toPx()
            }
            val heightPx = with(density) {
                if (maxHeight != Dp.Infinity && maxHeight > 0.dp) maxHeight.toPx() else 800.dp.toPx()
            }

            val items = remember(
                count,
                widthPx,
                heightPx,
                stickers,
                minSizePx,
                maxSizePx,
                minRotation,
                maxRotation,
                edgePaddingPx,
                tintBehavior,
                tintProbability,
                palette,
                seed
            ) {
                generatePixelSprayLayout(
                    count = count,
                    areaWidthPx = widthPx,
                    areaHeightPx = heightPx,
                    stickers = stickers,
                    minSizePx = minSizePx,
                    maxSizePx = maxSizePx,
                    minRotation = minRotation,
                    maxRotation = maxRotation,
                    edgePaddingPx = edgePaddingPx,
                    tintBehavior = tintBehavior,
                    tintProbability = tintProbability,
                    palette = palette,
                    seed = seed
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                for (item in items) {
                    val bitmap = stickerBitmaps[item.asset.resId] ?: continue
                    rotate(degrees = item.rotation, pivot = item.center) {
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(
                                item.topLeft.x.roundToInt(),
                                item.topLeft.y.roundToInt()
                            ),
                            dstSize = IntSize(item.width.roundToInt(), item.height.roundToInt()),
                            colorFilter = item.colorFilter,
                            filterQuality = FilterQuality.None
                        )
                    }
                }
            }
        }
    }

// ============================================================================
// Previews
// ============================================================================

    @Preview(
        name = "PixelSpray - Keitai Pastel",
        widthDp = 480,
        heightDp = 800,
        showBackground = true,
        backgroundColor = 0xFFFFFAF5
    )
    @Composable
    fun PixelSprayPreview() {
        PixelSpray(
            count = 14,
            tintBehavior = PixelSprayTintBehavior.ALWAYS_TINT_WHERE_POSSIBLE,
            seed = 101L
        )
    }
}

/**
 * Top-level convenience composable delegating to [PixelStickers.PixelSpray].
 */
@Composable
fun PixelSpray(
    count: Int = 14,
    modifier: Modifier = Modifier.fillMaxSize(),
    stickers: List<PixelStickerAsset> = PixelStickers.DEFAULT,
    minSize: Dp = 26.dp,
    maxSize: Dp = 56.dp,
    minRotation: Float = 0f,
    maxRotation: Float = 0f,
    edgePadding: Dp = 20.dp,
    tintBehavior: PixelSprayTintBehavior = PixelSprayTintBehavior.ALWAYS_TINT_WHERE_POSSIBLE,
    tintProbability: Float = 0.5f,
    palette: List<Color> = PastelPalette.ALL,
    seed: Long? = 101L
) {
    PixelStickers.PixelSpray(
        count = count,
        modifier = modifier,
        stickers = stickers,
        minSize = minSize,
        maxSize = maxSize,
        minRotation = minRotation,
        maxRotation = maxRotation,
        edgePadding = edgePadding,
        tintBehavior = tintBehavior,
        tintProbability = tintProbability,
        palette = palette,
        seed = seed
    )
}