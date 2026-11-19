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
