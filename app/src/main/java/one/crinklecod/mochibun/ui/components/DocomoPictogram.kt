package one.crinklecod.mochibun.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import one.crinklecod.mochibun.R
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.palette
import kotlin.math.roundToInt

private const val DOCOMO_TILE_PX = 12
private const val DOCOMO_ATLAS_COLS = 16
private const val DOCOMO_ATLAS_ROWS = 11
private const val DOCOMO_MAX_INDEX = DOCOMO_ATLAS_COLS * DOCOMO_ATLAS_ROWS - 1
private val DOCOMO_TILE_SIZE = IntSize(DOCOMO_TILE_PX, DOCOMO_TILE_PX)

@Volatile
private var cachedDocomoAtlas: ImageBitmap? = null
private val docomoAtlasLock = Any()

private fun getOrLoadDocomoAtlas(context: Context): ImageBitmap? {
    cachedDocomoAtlas?.let { return it }
    return synchronized(docomoAtlasLock) {
        cachedDocomoAtlas ?: run {
            val opts = BitmapFactory.Options().apply { inScaled = false }
            BitmapFactory.decodeResource(context.resources, R.drawable.docomo_atlas, opts)
                ?.asImageBitmap()
                ?.also { cachedDocomoAtlas = it }
        }
    }
}

/** Drops the cached Docomo pictogram atlas on critical memory trim; reloads on demand. */
fun clearDocomoAtlasCache() {
    synchronized(docomoAtlasLock) {
        cachedDocomoAtlas = null
    }
}

/**
 * Renders a crisp 12×12 monochrome Docomo pixel-art pictogram sliced from
 * `R.drawable.docomo_atlas` (16 columns × 11 rows, indices `0..165`) using
 * nearest-neighbor scaling ([FilterQuality.None]) and a soft pastel tint matching
 * the active [LocalLauncherTheme].
 */
@Composable
fun DocomoPictogram(
    index: Int,
    tint: Color? = null,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext
    val theme = LocalLauncherTheme.current
    val effectiveTint = tint ?: remember(
        theme.name,
        theme.palette.primary,
        theme.palette.deepAccent,
        theme.greetingOutlineColor,
        theme.appActionColor
    ) {
        if (theme.name.startsWith("custom_")) {
            lerp(theme.greetingOutlineColor, theme.appActionColor, 0.35f)
        } else {
            lerp(theme.palette.primary, theme.palette.deepAccent, 0.35f)
        }
    }
    val atlas = remember(appContext) { getOrLoadDocomoAtlas(appContext) }
    val safeIndex = index.coerceIn(0, DOCOMO_MAX_INDEX)
    val srcOffset = remember(safeIndex) {
        val col = safeIndex % DOCOMO_ATLAS_COLS
        val row = safeIndex / DOCOMO_ATLAS_COLS
        IntOffset(col * DOCOMO_TILE_PX, row * DOCOMO_TILE_PX)
    }
    val colorFilter = remember(effectiveTint) {
        ColorFilter.tint(effectiveTint, BlendMode.SrcAtop)
    }

    Canvas(modifier = modifier.size(size)) {
        if (atlas == null) return@Canvas
        if (srcOffset.x + DOCOMO_TILE_PX > atlas.width ||
            srcOffset.y + DOCOMO_TILE_PX > atlas.height
        ) {
            return@Canvas
        }
        val dstSize = IntSize(
            this.size.width.roundToInt().coerceAtLeast(1),
            this.size.height.roundToInt().coerceAtLeast(1)
        )
        drawImage(
            image = atlas,
            srcOffset = srcOffset,
            srcSize = DOCOMO_TILE_SIZE,
            dstOffset = IntOffset.Zero,
            dstSize = dstSize,
            filterQuality = FilterQuality.None,
            colorFilter = colorFilter
        )
    }
}

/** Source pixels of backing around a die-cut pictogram: 1 white + 1 faint rim. */
private const val DIE_CUT_PAD = 2
private const val DIE_CUT_TILE = DOCOMO_TILE_PX + 2 * DIE_CUT_PAD

/**
 * Bakes one pictogram into a tiny 16x16 die-cut sprite: the glyph in [tint]; its silhouette
 * (enclosed holes included, so outline glyphs aren't see-through) grown one pixel in every
 * direction in white; and a one-pixel [rim] round that. Pure pixel work on a 1 KB bitmap.
 */
private fun bakeDieCutPictogram(atlas: ImageBitmap, srcX: Int, srcY: Int, tint: Int, rim: Int): ImageBitmap {
    val n = DIE_CUT_TILE
    val tile = IntArray(DOCOMO_TILE_PX * DOCOMO_TILE_PX)
    atlas.readPixels(tile, startX = srcX, startY = srcY, width = DOCOMO_TILE_PX, height = DOCOMO_TILE_PX)
    val glyph = BooleanArray(n * n)
    for (y in 0 until DOCOMO_TILE_PX) for (x in 0 until DOCOMO_TILE_PX) {
        glyph[(y + DIE_CUT_PAD) * n + x + DIE_CUT_PAD] = (tile[y * DOCOMO_TILE_PX + x] ushr 24) > 127
    }
    // Flood the outside from the padded border; whatever it can't reach is part of the sticker
    val outside = BooleanArray(n * n)
    val stack = ArrayDeque<Int>()
    for (i in 0 until n) {
        stack.add(i); stack.add((n - 1) * n + i); stack.add(i * n); stack.add(i * n + n - 1)
    }
    while (stack.isNotEmpty()) {
        val c = stack.removeLast()
        if (outside[c] || glyph[c]) continue
        outside[c] = true
        val x = c % n
        val y = c / n
        if (x > 0) stack.add(c - 1)
        if (x < n - 1) stack.add(c + 1)
        if (y > 0) stack.add(c - n)
        if (y < n - 1) stack.add(c + n)
    }
    fun nearSolid(x: Int, y: Int, radiusSq: Int): Boolean {
        for (dy in -2..2) for (dx in -2..2) {
            if (dx * dx + dy * dy > radiusSq) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until n && ny in 0 until n && !outside[ny * n + nx]) return true
        }
        return false
    }
    val out = IntArray(n * n)
    for (y in 0 until n) for (x in 0 until n) {
        val c = y * n + x
        out[c] = when {
            glyph[c] -> tint
            !outside[c] || nearSolid(x, y, 2) -> android.graphics.Color.WHITE
            nearSolid(x, y, 5) -> rim
            else -> 0
        }
    }
    return android.graphics.Bitmap.createBitmap(out, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * A Docomo pictogram as a die-cut vinyl sticker, baked once into a 16x16 sprite (see
 * [bakeDieCutPictogram]) and drawn at the largest whole-number scale that fits, centred, with
 * [FilterQuality.None], so every 1-bit pixel stays a crisp square. One blit per frame.
 */
@Composable
fun DieCutPictogram(
    index: Int,
    tint: Color,
    rimColor: Color,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext
    val atlas = remember(appContext) { getOrLoadDocomoAtlas(appContext) }
    val safeIndex = index.coerceIn(0, DOCOMO_MAX_INDEX)
    val sprite = remember(atlas, safeIndex, tint, rimColor) {
        val srcX = (safeIndex % DOCOMO_ATLAS_COLS) * DOCOMO_TILE_PX
        val srcY = (safeIndex / DOCOMO_ATLAS_COLS) * DOCOMO_TILE_PX
        if (atlas == null || srcX + DOCOMO_TILE_PX > atlas.width || srcY + DOCOMO_TILE_PX > atlas.height) {
            null
        } else {
            bakeDieCutPictogram(atlas, srcX, srcY, tint.toArgb(), rimColor.toArgb())
        }
    }

    Canvas(modifier = modifier) {
        if (sprite == null) return@Canvas
        val scale = (minOf(size.width, size.height) / DIE_CUT_TILE).toInt().coerceAtLeast(1)
        val side = DIE_CUT_TILE * scale
        drawImage(
            image = sprite,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(DIE_CUT_TILE, DIE_CUT_TILE),
            dstOffset = IntOffset(((size.width - side) / 2f).roundToInt(), ((size.height - side) / 2f).roundToInt()),
            dstSize = IntSize(side, side),
            filterQuality = FilterQuality.None
        )
    }
}
