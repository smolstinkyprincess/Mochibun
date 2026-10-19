package com.crinklecodone.mochibun.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.R
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.focusedContainerColor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ============================================================================
// Mochibun PixelSpray - Organic Kawaii Pixel-Art Sticker Scatter Engine ♡
// ============================================================================

/**
 * Coordinated pastel palette designed for Mochibun's cute aesthetic.
 * Contains coordinated soft pink, gentle lavender, dreamy baby blue, fresh mint,
 * warm peach, and butter yellow.
 */
object PastelPalette {
    val Pink = Color(0xFFFFB7D5)         // Soft pastel pink ♡
    val Lavender = Color(0xFFDCC4F8)     // Gentle pastel lavender
    val BabyBlue = Color(0xFFBAE6FD)     // Dreamy baby sky blue
    val Mint = Color(0xFFB7F4D8)         // Fresh pastel mint
    val Peach = Color(0xFFFFD1BA)        // Warm pastel peach
    val ButterYellow = Color(0xFFFFF0A6) // Warm butter yellow

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
    /** All stickers are rendered strictly in their original PNG colors. */
    ORIGINAL_ONLY,

    /** Only assets marked as tint-eligible receive pastel tints; others preserve original colors. */
    TINT_ELIGIBLE_ONLY,

    /**
     * Probabilistic mix: tint-eligible stickers have a configurable chance
     * ([tintProbability]) of being tinted, otherwise rendering in original colors.
     */
    MIXED,

    /** All tint-eligible stickers receive pastel tints. */
    ALWAYS_TINT_WHERE_POSSIBLE
}

/**
 * Metadata and rendering configuration for a pixel-art sticker resource.
 *
 * @property resId The Android drawable resource ID.
 * @property name Human-readable name of the sticker asset.
 * @property intrinsicWidth Natural pixel width of the source PNG.
 * @property intrinsicHeight Natural pixel height of the source PNG.
 * @property canTint Whether this asset is eligible for pastel tinting. Multi-colored artwork
 *   with intricate shading/highlights should set this to false to preserve character.
 * @property blendMode The blend mode used when tinting. Defaults to [BlendMode.Modulate]
 *   which preserves dark pixel outlines and contrast while tinting midtones and highlights.
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
 * Easily extended by adding new [PixelStickerAsset] entries.
 */
object PixelStickers {
    /** Intricate multi-toned pink ribbon bow with cream highlights. Multicolored; best in original colors. */
    val Bow = PixelStickerAsset(
        resId = R.drawable.bow,
        name = "bow",
        intrinsicWidth = 500,
        intrinsicHeight = 500,
        canTint = false
    )

    /** Soft silhouette pixel cloud. Pale monochrome-like base; looks adorable in all pastel tints. */
    val Cloud = PixelStickerAsset(
        resId = R.drawable.cloud,
        name = "cloud",
        intrinsicWidth = 23,
        intrinsicHeight = 11,
        canTint = true,
        blendMode = BlendMode.Modulate
    )

    /** Pink pixel-art heart with plum outline and white highlight. Outlines stay dark with Modulate. */
    val Heart = PixelStickerAsset(
        resId = R.drawable.heart,
        name = "heart",
        intrinsicWidth = 1188,
        intrinsicHeight = 1188,
        canTint = true,
        blendMode = BlendMode.Modulate
    )

    /** 16x16 pixel star sparkle. Perfect for recoloring into baby blue, mint, lavender, etc. */
    val Sparkle = PixelStickerAsset(
        resId = R.drawable.sparkle,
        name = "sparkle",
        intrinsicWidth = 16,
        intrinsicHeight = 16,
        canTint = true,
        blendMode = BlendMode.Modulate
    )

    /** 13x11 pastel star without dark outlines. Great candidate for all pastel tints. */
    val Star = PixelStickerAsset(
        resId = R.drawable.star,
        name = "star",
        intrinsicWidth = 12,
        intrinsicHeight = 11,
        canTint = false
    )

    /** The standard Mochibun sticker collection. */
    val DEFAULT: List<PixelStickerAsset> = listOf(
        Bow,
        Cloud,
        Heart,
        Sparkle,
        Star
    )


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

    /**
     * Lightweight, deterministic layout generation helper for scattering stickers.
     *
     * Uses Mitchell's Best-Candidate algorithm to distribute stickers organically across
     * the canvas, preventing artificial grid rows/columns while avoiding unnatural center clumping.
     *
     * Aspect ratios are strictly preserved, and dimensions are clamped within the specified
     * area and edge padding.
     */
    fun generatePixelSprayLayout(
        count: Int,
        areaWidthPx: Float,
        areaHeightPx: Float,
        stickers: List<PixelStickerAsset> = DEFAULT,
        minSizePx: Float,
        maxSizePx: Float,
        minRotation: Float = -35f,
        maxRotation: Float = 35f,
        edgePaddingPx: Float = 0f,
        tintBehavior: PixelSprayTintBehavior = PixelSprayTintBehavior.MIXED,
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

        val items = ArrayList<PixelStickerItem>(count)
        val candidatesCount = 8

        for (i in 0 until count) {
            val asset = stickers[random.nextInt(stickers.size)]

            // Randomize target scale while strictly preserving intrinsic aspect ratio
            val targetSize = actualMinSize + random.nextFloat() * (actualMaxSize - actualMinSize)
            val ar = asset.aspectRatio
            val (dispW, dispH) = if (ar >= 1f) {
                targetSize to (targetSize / ar)
            } else {
                (targetSize * ar) to targetSize
            }

            // Randomize rotation
            val rot = actualMinRot + random.nextFloat() * (actualMaxRot - actualMinRot)

            // Bounding radius accounting for rotation
            val radius = sqrt(dispW * dispW + dispH * dispH) / 2f

            // Clamp placement within available area and padding
            val minX = (edgePaddingPx + radius).coerceAtMost(areaWidthPx / 2f)
            val maxX = (areaWidthPx - edgePaddingPx - radius).coerceAtLeast(minX)
            val minY = (edgePaddingPx + radius).coerceAtMost(areaHeightPx / 2f)
            val maxY = (areaHeightPx - edgePaddingPx - radius).coerceAtLeast(minY)

            // Best-Candidate placement for organic, non-grid, well-dispersed scatter
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

            // Determine tinting per sticker
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

            val colorFilter = tintColor?.let { ColorFilter.tint(it, asset.blendMode) }

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
     * Scatters a specified number of cute pixel-art stickers across the available layout area.
     *
     * Designed for Mochibun's kawaii aesthetic and tuned for performance on 480 × 800 low-RAM devices:
     * - Employs nearest-neighbor sampling ([FilterQuality.None]) to preserve crisp pixel-art edges.
     * - Reuses cached [ImageBitmap] instances with zero per-frame bitmap duplication.
     * - Renders all stickers in a single fast [Canvas] pass without Composable overhead.
     * - Retains a stable arrangement across ordinary parent recompositions.
     *
     * @param count Number of stickers to scatter.
     * @param modifier Modifier to apply to the container layout.
     * @param stickers List of sticker assets to randomly select from. Defaults to [PixelStickers.DEFAULT].
     * @param minSize Minimum bounding dimension for stickers.
     * @param maxSize Maximum bounding dimension for stickers.
     * @param minRotation Minimum rotation in degrees (defaults to -35°).
     * @param maxRotation Maximum rotation in degrees (defaults to +35°).
     * @param edgePadding Inset from the outer edges of the layout area.
     * @param tintBehavior Pastel tinting strategy ([PixelSprayTintBehavior]). Defaults to [PixelSprayTintBehavior.MIXED].
     * @param tintProbability Probability (0.0f..1.0f) of applying a pastel tint to an eligible sticker in [PixelSprayTintBehavior.MIXED] mode.
     * @param palette Coordinated pastel colors to draw tints from. Defaults to [PastelPalette.ALL].
     * @param seed Optional random seed for reproducible or preview layouts.
     */
    @Composable
    fun PixelSpray(
        count: Int = 20,
        modifier: Modifier = Modifier.fillMaxSize(),
        stickers: List<PixelStickerAsset> = DEFAULT,
        minSize: Dp = 28.dp,
        maxSize: Dp = 68.dp,
        minRotation: Float = -35f,
        maxRotation: Float = 35f,
        edgePadding: Dp = 16.dp,
        tintBehavior: PixelSprayTintBehavior = PixelSprayTintBehavior.MIXED,
        tintProbability: Float = 0.5f,
        palette: List<Color> = PastelPalette.ALL,
        seed: Long? = null
    ) {
        if (count <= 0 || stickers.isEmpty()) return

        val density = LocalDensity.current
        val minSizePx = with(density) { minSize.toPx() }
        val maxSizePx = with(density) { maxSize.toPx() }
        val edgePaddingPx = with(density) { edgePadding.toPx() }

        // Preload & remember unique sticker ImageBitmaps; avoids duplicate allocations
        val context = LocalContext.current
        val stickerBitmaps = remember(stickers) {
            stickers.associate { asset ->
                asset.resId to ImageBitmap.imageResource(context.resources, asset.resId)
            }
        }

        BoxWithConstraints(modifier = modifier) {
            val widthPx = with(density) {
                if (maxWidth != Dp.Infinity && maxWidth > 0.dp) maxWidth.toPx() else 480.dp.toPx()
            }
            val heightPx = with(density) {
                if (maxHeight != Dp.Infinity && maxHeight > 0.dp) maxHeight.toPx() else 800.dp.toPx()
            }

            // Arrangement is kept stable across recompositions unless inputs change
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

            // Render all stickers in a single fast, nearest-neighbor canvas draw pass
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
        name = "PixelSpray - Kawaii Mixed",
        widthDp = 480,
        heightDp = 800,
        showBackground = true,
        backgroundColor = 0xFFFFFAF5
    )
    @Composable
    fun PixelSprayPreview() {
        PixelSpray(
            count = 20
        )
    }
}

/**
 * Scatters a specified number of cute pixel-art stickers across the available layout area.
 * Top-level convenience composable delegating to [PixelStickers.PixelSpray].
 */
@Composable
fun PixelSpray(
    count: Int = 20,
    modifier: Modifier = Modifier.fillMaxSize(),
    stickers: List<PixelStickerAsset> = PixelStickers.DEFAULT,
    minSize: Dp = 28.dp,
    maxSize: Dp = 68.dp,
    minRotation: Float = -35f,
    maxRotation: Float = 35f,
    edgePadding: Dp = 16.dp,
    tintBehavior: PixelSprayTintBehavior = PixelSprayTintBehavior.MIXED,
    tintProbability: Float = 0.5f,
    palette: List<Color> = PastelPalette.ALL,
    seed: Long? = null
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

/**
 * Dedicated settings row component for toggling PixelSpray wallpaper stickers.
 * Follows Mochibun's pastel design system and supports physical D-pad focus on the Chinoe AT-M120.
 */
@Composable
fun PixelSpraySettingsRow(
    isEnabled: Boolean,
    isFocused: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 2.dp)
            .glowingDoubleBorder(isSelected = isFocused, shape = cardShape, cornerRadius = 18.dp)
            .background(
                color = if (isFocused) ActiveTheme.current.focusedContainerColor else ActiveTheme.surface,
                shape = cardShape
            )
            .border(
                width = if (isFocused) 2.dp else 1.2.dp,
                color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                shape = cardShape
            )
            .clip(cardShape)
            .clickable { onToggle(!isEnabled) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = "♡ Wallpaper PixelSpray ♡",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActiveTheme.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scatter pastel pixel stickers above home wallpaper",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                    lineHeight = 15.sp
                )
            }

            // Kawaii Pill Switch (ON / OFF)
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isEnabled) ActiveTheme.primary else ActiveTheme.cardBorder)
                    .border(
                        1.2.dp,
                        if (isEnabled) ActiveTheme.accentDeep else ActiveTheme.cardBorder,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isEnabled) Arrangement.End else Arrangement.Start
                ) {
                    if (isEnabled) {
                        Text(
                            text = "ON",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ActiveTheme.accentDeep,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    // Knob circle
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .border(1.dp, ActiveTheme.accentDeep.copy(alpha = 0.5f), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEnabled) "♡" else "・",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.6f)
                        )
                    }

                    if (!isEnabled) {
                        Text(
                            text = "OFF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ActiveTheme.textPrimary,
                            modifier = Modifier.padding(start = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

