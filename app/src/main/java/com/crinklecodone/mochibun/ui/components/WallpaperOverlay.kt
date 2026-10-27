package com.crinklecodone.mochibun.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.crinklecodone.mochibun.R

/**
 * 480 × 800 pre-rendered transparent PNG wallpaper overlays located in drawable-nodpi.
 * Loading them from drawable-nodpi guarantees 1:1 scale rendering without runtime bitmap
 * allocations or density rescaling on the Chinoe AT-M120 (480x800, MediaTek MT6761).
 */
val WALLPAPER_OVERLAY_DRAWABLES = intArrayOf(
    R.drawable.wallpaper_overlay_1,
    R.drawable.wallpaper_overlay_2,
    R.drawable.wallpaper_overlay_3,
    R.drawable.wallpaper_overlay_4
)

/**
 * Cycles deterministically between wallpaper overlays 1..4 based on calendar day
 * epoch (System.currentTimeMillis() / 86_400_000L % 4), ensuring consistent
 * display throughout the day and zero per-frame canvas calculations.
 */
@Composable
fun rememberWallpaperOverlayRes(): Int {
    return remember {
        val dayIndex = (System.currentTimeMillis() / 86_400_000L).toInt()
        val index = ((dayIndex % WALLPAPER_OVERLAY_DRAWABLES.size) + WALLPAPER_OVERLAY_DRAWABLES.size) % WALLPAPER_OVERLAY_DRAWABLES.size
        WALLPAPER_OVERLAY_DRAWABLES[index]
    }
}

/**
 * Static, non-interactive wallpaper overlay layer.
 * Attached without click, pointerInput, or focus modifiers so gestures and
 * D-pad physical navigation pass directly through to foreground cards and drawer.
 */
@Composable
fun WallpaperOverlay(
    modifier: Modifier = Modifier,
    @DrawableRes overlayRes: Int = rememberWallpaperOverlayRes()
) {
    Image(
        painter = painterResource(id = overlayRes),
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.None
    )
}

/**
 * Convenience Image wrapper supporting FilterQuality parameter when paired with a Painter.
 */
@Composable
fun Image(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    filterQuality: FilterQuality = FilterQuality.None
) {
    androidx.compose.foundation.Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
