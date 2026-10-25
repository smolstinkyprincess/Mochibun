package com.crinklecodone.mochibun.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.crinklecodone.mochibun.R
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.repository.ExternalThemeManager
import com.crinklecodone.mochibun.ui.theme.LauncherTheme
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Mascot moods mapped to static PNG fallbacks and animated GIF loops.
 */
enum class MascotMood(
    @DrawableRes val pngResId: Int,
    @DrawableRes val gifResId: Int?,
    val description: String
) {
    DEFAULT(
        pngResId = R.drawable.mochibunny,
        gifResId = R.drawable.mochibunny_idle_anim,
        description = "Idle squish loaf"
    ),
    MUSIC(
        pngResId = R.drawable.mochibunny_music,
        gifResId = R.drawable.mochibunny_music_anim,
        description = "Music bob loop"
    ),
    SLEEP(
        pngResId = R.drawable.mochibunny_sleep,
        gifResId = R.drawable.mochibunny_sleep_anim,
        description = "Sleeping loaf loop"
    ),
    WORRIED(
        pngResId = R.drawable.mochibunny_worried,
        gifResId = null,
        description = "Worried / concerned loaf"
    ),
    CHEER(
        pngResId = R.drawable.mochibunny_cheer,
        gifResId = null,
        description = "Cheering / celebratory loaf"
    )
}

/**
 * Returns [MascotMood.SLEEP] during late night hours (22:00 - 06:00),
 * otherwise relaxed [MascotMood.DEFAULT].
 */
fun getDefaultTimeOfDayMood(): MascotMood {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (hour >= 22 || hour < 6) MascotMood.SLEEP else MascotMood.DEFAULT
}

/**
 * Maps Launcher Theme Presets to their subtle mascot pastel tint washes:
 * - Sakura Dream -> Color(0xFFFFEBF2)
 * - Lavender Mist -> Color(0xFFF3EDFF)
 * - Minty Matcha  -> Color(0xFFEDF8F1)
 * - Milky Peach   -> Color(0xFFFFF0E6)
 * - Default       -> null (natural cream fur)
 */
fun getMascotTintColorForTheme(themeName: String?): Color? {
    if (themeName == null) return null
    return when (themeName.lowercase()) {
        "sakura_dream", "sakura", "strawberry_milk" -> Color(0xFFFFEBF2)
        "lavender_mist", "lavender", "lavender_dream" -> Color(0xFFF3EDFF)
        "minty_matcha", "matcha" -> Color(0xFFEDF8F1)
        "milky_peach", "peach" -> Color(0xFFFFF0E6)
        else -> null
    }
}

/**
 * Extension on [LauncherTheme] to cleanly resolve the active mascot tint color.
 * Resolves theme/palette `mascotTint` first, falling back to preset name defaults.
 */
val LauncherTheme.mascotTintColor: Color?
    get() = mascotTint ?: ExternalThemeManager.findPalette(name)?.mascotTint ?: getMascotTintColorForTheme(name)

/**
 * Shifts sprite highlights toward the specified theme tint while strictly preserving
 * deep black/plum outline contrast and rosy blush cheek details.
 */
fun createPastelTintMatrix(tintColor: Color, intensity: Float = 0.22f): ColorMatrix {
    val rScale = (1f - intensity) + intensity * tintColor.red
    val gScale = (1f - intensity) + intensity * tintColor.green
    val bScale = (1f - intensity) + intensity * tintColor.blue
    return ColorMatrix(
        floatArrayOf(
            rScale, 0f, 0f, 0f, 0f,
            0f, gScale, 0f, 0f, 0f,
            0f, 0f, bScale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

/**
 * Adaptive Animated Mochibun Mascot Component.
 *
 * Constrains pixel art assets to exact Dp envelopes (default 48.dp) with
 * [FilterQuality.None] nearest-neighbor scaling to preserve 32x32 pixel art fidelity
 * on physical hardware (e.g. ALT Mive Style Folder AT-M120).
 *
 * Decodes animated GIF loops via Coil (`ImageDecoderDecoder`/`GifDecoder`) when
 * [useAnimation] is enabled, with seamless fallback to static PNGs.
 *
 * When booped (tapped / clicked), the mascot bounces with a cheerful vertical hop,
 * plays a tactile audio cue, and displays `mochibunny_cheer.png` before returning
 * smoothly to its base idle loop.
 *
 * Supports dynamic pastel theme tinting via [createPastelTintMatrix] with zero
 * per-recomposition allocations.
 */
@Composable
fun MochibunMascot(
    mood: MascotMood = MascotMood.DEFAULT,
    useAnimation: Boolean = true,
    themeTintColor: Color? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isBooped: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var boopTrigger by remember { mutableIntStateOf(0) }
    var internalBounce by remember { mutableStateOf(false) }

    LaunchedEffect(boopTrigger) {
        if (boopTrigger > 0) {
            internalBounce = true
            delay(1000)
            internalBounce = false
        }
    }

    val isBounce = isBooped || internalBounce

    val scale by animateFloatAsState(
        targetValue = if (isBounce) 1.22f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "mascotScale"
    )

    val bounceTranslationY by animateFloatAsState(
        targetValue = if (isBounce) -14f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "mascotTranslationY"
    )

    // Cached ColorFilter matrix preserving dark outline contrast
    val colorFilter = remember(themeTintColor) {
        themeTintColor?.let { ColorFilter.colorMatrix(createPastelTintMatrix(it)) }
    }

    // When bouncing (booped), pause GIF loop to showcase cheer sprite
    val activeGifRes = if (useAnimation && !isBounce) mood.gifResId else null

    // Pre-cache cheer bitmap and mood bitmap with nearest-neighbor rendering
    val cheerBitmap = remember(context) {
        runCatching { ImageBitmap.imageResource(context.resources, R.drawable.mochibunny_cheer) }.getOrNull()
    }
    val defaultBitmap = remember(mood.pngResId, context) {
        ImageBitmap.imageResource(context.resources, mood.pngResId)
    }
    val activeStaticBitmap = if (isBounce && cheerBitmap != null) cheerBitmap else defaultBitmap

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = bounceTranslationY
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                boopTrigger++
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        if (activeGifRes != null) {
            // Render animated GIF via Coil with FilterQuality.None nearest-neighbor scaling
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(activeGifRes)
                    .build(),
                imageLoader = context.imageLoader,
                filterQuality = FilterQuality.None
            )
            Image(
                painter = painter,
                contentDescription = mood.description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter
            )
        } else {
            // Render static PNG using direct ImageBitmap with nearest-neighbor FilterQuality.None
            Image(
                bitmap = activeStaticBitmap,
                contentDescription = if (isBounce) "Cheering loaf" else mood.description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                colorFilter = colorFilter
            )
        }
    }
}

/**
 * Backward-compatible alias for [MochibunMascot].
 */
@Composable
fun KawaiiMascot(
    modifier: Modifier = Modifier,
    emote: MascotMood = MascotMood.DEFAULT,
    size: Dp = 48.dp,
    themeTintColor: Color? = null,
    useAnimation: Boolean = true,
    isBooped: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    MochibunMascot(
        mood = emote,
        useAnimation = useAnimation,
        themeTintColor = themeTintColor,
        modifier = modifier,
        size = size,
        isBooped = isBooped,
        onClick = onClick
    )
}
