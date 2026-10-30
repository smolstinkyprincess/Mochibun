package com.crinklecodone.mochibun.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.crinklecodone.mochibun.R
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.repository.ExternalThemeManager
import com.crinklecodone.mochibun.data.repository.MediaSessionTracker
import com.crinklecodone.mochibun.ui.theme.LauncherTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.M3x6FontFamily
import com.crinklecodone.mochibun.ui.theme.palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

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

// ---------------------------------------------------------------------------
// Keitai-Inspired Dynamic Speech Bubble System
// ---------------------------------------------------------------------------

val DEFAULT_BOOP_LINES = listOf(
    "boop! ( ˶ˆᗜˆ˵ )",
    "yay! ♡",
    "paws up! ₍ᐢ. ̫.ᐢ₎",
    "mochi squish! ♡",
    "hi friend! 🌸",
    "cheer! ٩(ˊᗜˋ*)و"
)

val LATE_NIGHT_LINES = listOf(
    "eepy... zzZ",
    "bedtime soon? ( ᴗ͈ˬᴗ͈ )",
    "yawn~ ♡",
    "sweet dreams ✨"
)

val LOW_BATTERY_LINES = listOf(
    "need juice... ( ´･ω･)",
    "getting tired... 🔋",
    "hungry for charge ♡"
)

val MUSIC_LINES = listOf(
    "jammin! ♫",
    "good beats ♡",
    "bobbing along ₍ᐢ. ̫.ᐢ₎"
)

/**
 * Checks if device battery is below 20% using sticky broadcast or BatteryManager.
 */
fun isDeviceBatteryLow(context: Context): Boolean {
    return try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (capacity in 0..19) {
            true
        } else {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                (level * 100 / scale) < 20
            } else {
                false
            }
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Selects a context-aware dialogue line for Mochibunny:
 * - Low battery (< 20%)
 * - Music playing (MediaSessionTracker)
 * - Late night (10:00 PM – 5:00 AM)
 * - Default boops
 * Guarantees that the chosen line avoids the immediate predecessor [previousLine].
 */
fun selectSpeechBubbleDialogue(
    context: Context? = null,
    previousLine: String? = null,
    hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    isLowBattery: Boolean? = null,
    isMusicPlaying: Boolean? = null
): String {
    val isLateNight = hour >= 22 || hour < 5
    val isLowBatt = isLowBattery ?: (context?.let { isDeviceBatteryLow(it) } ?: false)
    val isMusic = isMusicPlaying ?: MediaSessionTracker.mediaState.value.isPlaying

    val contextualPool = mutableListOf<String>()
    if (isLowBatt) contextualPool.addAll(LOW_BATTERY_LINES)
    if (isMusic) contextualPool.addAll(MUSIC_LINES)
    if (isLateNight) contextualPool.addAll(LATE_NIGHT_LINES)

    val candidatePool = if (contextualPool.isNotEmpty()) {
        if (Random.nextFloat() < 0.70f) contextualPool else DEFAULT_BOOP_LINES
    } else {
        DEFAULT_BOOP_LINES
    }

    val filtered = candidatePool.filter { it != previousLine }
    return if (filtered.isNotEmpty()) {
        filtered.random()
    } else {
        candidatePool.random()
    }
}

/**
 * Kawaii keitai-inspired speech bubble with ultra-rounded pill and subtle downward pointer tail.
 */
@Composable
fun SpeechBubble(
    text: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.graphicsLayer { clip = false },
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 9.dp, vertical = 3.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = M3x6FontFamily,
                fontSize = 18.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }

        Canvas(
            modifier = Modifier
                .padding(end = 20.dp)
                .offset(y = (-1.5).dp)
                .size(width = 9.dp, height = 5.5.dp)
        ) {
            val width = size.width
            val height = size.height
            val strokePx = 1.5.dp.toPx()

            // Fill triangle with background color overlapping the pill bottom stroke
            val fillPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(width, 0f)
                lineTo(width * 0.55f, height)
                close()
            }
            drawPath(path = fillPath, color = bgColor)

            // Outline the two downward slanted sides
            val strokePath = Path().apply {
                moveTo(0f, 0f)
                lineTo(width * 0.55f, height)
                lineTo(width, 0f)
            }
            drawPath(
                path = strokePath,
                color = borderColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Mochibunny Mascot coupled with a dynamic, keitai-inspired speech bubble reaction system.
 *
 * When booped (tapped / DPAD_CENTER clicked):
 * - Cancels any ongoing dismiss timer.
 * - Picks a new context-aware dialogue line avoiding the immediate predecessor.
 * - Triggers cheer bounce on the mascot and a subtle pop bounce on the bubble.
 * - Holds the speech bubble for 2.2 seconds before fading/scaling out.
 * - Fixed layout size guarantees zero layout shifting of adjacent widgets (clock, favorites card).
 * - Renders speech bubble on top of mascot sprite with explicit zIndex and draw order.
 */
@Composable
fun MochibunnyWithSpeechBubble(
    mood: MascotMood = getDefaultTimeOfDayMood(),
    useAnimation: Boolean = true,
    themeTintColor: Color? = null,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isFocused: Boolean = false,
    boopRequest: Int = 0, // bumped by the D-pad (Center on the focused mascot); boops like a tap
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val coroutineScope = rememberCoroutineScope()

    var currentDialogue by remember { mutableStateOf<String?>(null) }
    var lastSpokenLine by remember { mutableStateOf<String?>(null) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }
    var popAnimKey by remember { mutableIntStateOf(0) }
    val popScale = remember { Animatable(1f) }

    val bubbleBgColor = if (theme.name == "midnight_mochi") theme.cardBgColor else Color(0xFFFFFDFE)
    val bubbleBorderColor = theme.greetingOutlineColor
    val bubbleTextColor = if (theme.name == "midnight_mochi") theme.cardTextColor else theme.palette.deepAccent

    LaunchedEffect(popAnimKey) {
        if (popAnimKey > 0) {
            popScale.snapTo(0.82f)
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.55f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val onBoop = {
        dismissJob?.cancel()
        val newLine = selectSpeechBubbleDialogue(context, lastSpokenLine ?: currentDialogue)
        currentDialogue = newLine
        lastSpokenLine = newLine
        popAnimKey++
        SoundEffectManager.play(context, SoundEffect.SELECT)
        onClick?.invoke()
        dismissJob = coroutineScope.launch {
            delay(1250L)
            currentDialogue = null
        }
    }

    LaunchedEffect(boopRequest) {
        if (boopRequest > 0) onBoop()
    }

    Box(
        modifier = modifier
            .size(size)
            .softGlowFocus(isSelected = isFocused, cornerRadius = size / 2)
            .graphicsLayer { clip = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onBoop()
            }
    ) {
        // 1. Mascot sprite rendered underneath
        MochibunMascot(
            mood = mood,
            useAnimation = useAnimation,
            themeTintColor = themeTintColor,
            size = size,
            isBooped = currentDialogue != null,
            onClick = onBoop
        )

        // 2. Floating speech bubble rendered on top with higher z-index
        AnimatedVisibility(
            visible = currentDialogue != null,
            enter = fadeIn(tween(140)) + scaleIn(
                initialScale = 0.75f,
                animationSpec = spring(dampingRatio = 0.6f)
            ),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.75f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-32).dp)
                .zIndex(1f)
                .graphicsLayer {
                    scaleX = popScale.value
                    scaleY = popScale.value
                    clip = false
                }
        ) {
            currentDialogue?.let { text ->
                SpeechBubble(
                    text = text,
                    bgColor = bubbleBgColor,
                    borderColor = bubbleBorderColor,
                    textColor = bubbleTextColor
                )
            }
        }
    }
}
