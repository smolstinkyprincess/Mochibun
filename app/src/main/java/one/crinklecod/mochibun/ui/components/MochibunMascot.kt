package one.crinklecod.mochibun.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.model.BundleMascotDialogue
import one.crinklecod.mochibun.data.repository.CustomMascotManager
import one.crinklecod.mochibun.data.repository.ExternalThemeManager
import one.crinklecod.mochibun.data.repository.MediaSessionTracker
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.pet.PetMood
import one.crinklecod.mochibun.ui.theme.LauncherTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.M3x6FontFamily
import one.crinklecod.mochibun.ui.theme.MidnightMochiPalette
import one.crinklecod.mochibun.ui.theme.palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Her resting animation slot right now, from her pet mood (hungry, sulky, sleepy, happy or
 * content). Used wherever she appears, so her mood shows across the launcher.
 */
fun currentMoodSlot(): MascotAnimSlot = MascotAtlasMath.moodSlot(PetManager.currentMood())

/**
 * Maps Launcher Theme Presets to their subtle mascot pastel tint washes:
 * - Sakura Dream -> Color(0xFFFFEBF2)
 * - Lavender Mist -> Color(0xFFF3EDFF)
 * - Minty Matcha  -> Color(0xFFEDF8F1)
 * - Milky Peach   -> Color(0xFFFFF0E6)
 * - Midnight Mochi -> MidnightMochiPalette.mascotTint
 * - Default       -> null (natural cream fur)
 */
fun getMascotTintColorForTheme(themeName: String?): Color? {
    if (themeName == null) return null
    return when (themeName.lowercase()) {
        "sakura_dream", "sakura", "strawberry_milk" -> Color(0xFFFFEBF2)
        "lavender_mist", "lavender", "lavender_dream" -> Color(0xFFF3EDFF)
        "minty_matcha", "matcha" -> Color(0xFFEDF8F1)
        "milky_peach", "peach" -> Color(0xFFFFF0E6)
        MidnightMochiPalette.id, "midnight" -> MidnightMochiPalette.mascotTint
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
 * Mochibunny drawn from the single shared atlas ([rememberMascotAtlas]).
 *
 * - [slot] is her resting animation (usually [currentMoodSlot]); it loops with [frameTick].
 * - [reactionSlot] (EAT / PET / TALK / HAPPY) plays its frames twice from [reactionStartTick]
 *   ([MascotAtlasMath.REACTION_LOOPS]), then she returns to [slot].
 * - [frameTick] is a shared stepped clock (e.g. [rememberKeitaiStep]); it is read only while
 *   drawing, so each tick redraws the sprite without recomposing anything. The default `{ 0 }`
 *   shows a still first frame. ~1 GB phones always show the first frame.
 * - Frames are drawn with [FilterQuality.None] (nearest-neighbor) to keep the pixel art crisp,
 *   fitted into the [size] envelope without stretching.
 *
 * When booped (tapped), she hops, plays a tactile audio cue and plays her HAPPY frames.
 * The pastel theme tint ([createPastelTintMatrix]) is cached per tint color.
 */
@Composable
fun MochibunMascot(
    slot: MascotAnimSlot = MascotAnimSlot.IDLE,
    themeTintColor: Color? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    frameTick: () -> Int = { 0 },
    reactionSlot: MascotAnimSlot? = null,
    reactionStartTick: Int = 0,
    isBooped: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val atlas = rememberMascotAtlas()
    val staticFrames = remember(context) { MascotSpriteStore.isStaticFramesOnly(context) }
    var boopTrigger by remember { mutableIntStateOf(0) }
    var internalBounce by remember { mutableStateOf(false) }
    var boopStartTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(boopTrigger) {
        if (boopTrigger > 0) {
            internalBounce = true
            delay(MascotAtlasMath.REACTION_MS)
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

    // A caller's reaction wins; otherwise her own boop plays HAPPY
    val activeReaction = reactionSlot ?: if (internalBounce) MascotAnimSlot.HAPPY else null
    val activeReactionStart = if (reactionSlot != null) reactionStartTick else boopStartTick

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
                boopStartTick = frameTick()
                boopTrigger++
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val a = atlas ?: return@Canvas
            val frame = MascotAtlasMath.frameFor(
                layout = a.layout,
                base = slot,
                reaction = activeReaction,
                reactionStartTick = activeReactionStart,
                tick = frameTick(),
                staticFrames = staticFrames
            )
            // Fit the frame into the envelope, keeping its aspect ratio (frames may be non-square)
            val fw = frame.srcSize.width.toFloat()
            val fh = frame.srcSize.height.toFloat()
            val fit = minOf(this.size.width / fw, this.size.height / fh)
            val dstW = (fw * fit).roundToInt()
            val dstH = (fh * fit).roundToInt()
            drawImage(
                image = a.image,
                srcOffset = frame.srcOffset,
                srcSize = frame.srcSize,
                dstOffset = IntOffset(
                    ((this.size.width - dstW) / 2f).roundToInt(),
                    ((this.size.height - dstH) / 2f).roundToInt()
                ),
                dstSize = IntSize(dstW, dstH),
                colorFilter = colorFilter,
                filterQuality = FilterQuality.None
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

// Mochibunny pet lines: one pool per PetMood, plus reactions to feeding.

val PET_HAPPY_LINES = listOf(
    "so happy! ♡",
    "best day ever ٩(ˊᗜˋ*)و",
    "full & loved ♡",
    "hehe~ (˶ᵔ ᵕ ᵔ˶)",
    "mochi bliss ✨"
)

val PET_CONTENT_LINES = listOf(
    "just loafing ♡",
    "cozy~ ( ˘ ³˘)",
    "hi friend! 🌸",
    "all good here ₍ᐢ. ̫.ᐢ₎",
    "comfy loaf ♡"
)

val PET_HUNGRY_LINES = listOf(
    "tummy rumble... (｡•́︿•̀｡)",
    "snack time? 🍡",
    "so hungry... ♡",
    "treat please? ( ´･ω･)"
)

val PET_SULKY_LINES = listOf(
    "hmph... (｀へ´)",
    "pet me? ( ´･ω･)",
    "feeling lonely...",
    "no pats today? ( ˘･з･)"
)

val PET_SLEEPY_LINES = listOf(
    "eepy... zzZ",
    "yawn~ ♡",
    "bedtime soon? ( ᴗ͈ˬᴗ͈ )",
    "sweet dreams ✨",
    "five more mins... zzZ"
)

val PET_FED_LINES = listOf(
    "nom nom! ♡",
    "yummy~ ( ˶ˆᗜˆ˵ )",
    "thank you! 🍡",
    "tummy happy ♡",
    "munch munch ₍ᐢ. ̫.ᐢ₎"
)

val PET_ALREADY_FULL_LINES = listOf(
    "tummy's already full! ♡",
    "no room left~ (˶ᵔ ᵕ ᵔ˶)",
    "save that treat! 🍡",
    "bursting with mochi! ♡"
)

internal const val STEP_NO_TREATS_HINT = "go for a walk? ♡"

val PET_NO_TREATS_LINES = listOf(
    "no treats yet... ( ´･ω･)",
    STEP_NO_TREATS_HINT,
    "plug me in for a treat? 🔋",
    "clear notifs = treats! ✨",
    "treat bag's empty (｡•́︿•̀｡)",
    "daily visit = 1 treat! ♡"
)

val PET_PETTED_LINES = listOf(
    "hehe, that tickles ♡",
    "more pats please~",
    "soft paws ₍ᐢ. ̫.ᐢ₎",
    "(˶ᵔ ᵕ ᵔ˶) ♡",
    "best friend ever ♡"
)

fun noTreatsLines(
    hasStepSensor: Boolean,
    customDialogue: BundleMascotDialogue? = CustomMascotManager.activeDialogue()
): List<String> =
    customDialogue?.noTreats?.takeIf { it.isNotEmpty() }
        ?: if (hasStepSensor) PET_NO_TREATS_LINES else PET_NO_TREATS_LINES.filter { it != STEP_NO_TREATS_HINT }

/**
 * The bubble line for a pill reaction, never repeating [previousLine]. [chatLine] supplies the
 * mood-driven Chat line (it needs a Context and her current mood, so the caller provides it).
 * If the active custom mascot declares non-empty lines for `fed` or `noTreats`, those pools are
 * used; otherwise Mochibunny's built-in pools are used.
 */
fun selectReactionLine(
    kind: MascotReactionKind,
    previousLine: String?,
    hasStepSensor: Boolean = true,
    earnedCount: Int = 1,
    customDialogue: BundleMascotDialogue? = CustomMascotManager.activeDialogue(),
    chatLine: () -> String
): String = when (kind) {
    MascotReactionKind.CHAT -> chatLine()
    MascotReactionKind.FED -> pickLineAvoiding(
        customDialogue?.fed?.takeIf { it.isNotEmpty() } ?: PET_FED_LINES,
        previousLine
    )
    MascotReactionKind.ALREADY_FULL -> pickLineAvoiding(PET_ALREADY_FULL_LINES, previousLine)
    MascotReactionKind.NO_TREATS -> pickLineAvoiding(
        noTreatsLines(hasStepSensor, customDialogue),
        previousLine
    )
    MascotReactionKind.PETTED -> pickLineAvoiding(PET_PETTED_LINES, previousLine)
    MascotReactionKind.TREATS_EARNED -> if (earnedCount <= 1) "+1 🍡 treat earned! ♡" else "+$earnedCount 🍡 treats earned! ♡"
}

fun petMoodLines(
    mood: PetMood,
    customDialogue: BundleMascotDialogue? = CustomMascotManager.activeDialogue()
): List<String> = when (mood) {
    PetMood.HAPPY -> customDialogue?.happy?.takeIf { it.isNotEmpty() } ?: PET_HAPPY_LINES
    PetMood.CONTENT -> customDialogue?.content?.takeIf { it.isNotEmpty() } ?: PET_CONTENT_LINES
    PetMood.HUNGRY -> customDialogue?.hungry?.takeIf { it.isNotEmpty() } ?: PET_HUNGRY_LINES
    PetMood.SULKY -> customDialogue?.sulky?.takeIf { it.isNotEmpty() } ?: PET_SULKY_LINES
    PetMood.SLEEPY -> customDialogue?.sleepy?.takeIf { it.isNotEmpty() } ?: PET_SLEEPY_LINES
}

/** A random line from [pool] that isn't [previousLine] (unless the pool has nothing else). */
fun pickLineAvoiding(pool: List<String>, previousLine: String?): String {
    val filtered = pool.filter { it != previousLine }
    return if (filtered.isNotEmpty()) filtered.random() else pool.random()
}

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
 *
 * With a [petMood] (the Home pet), her mood leads:
 * - HUNGRY / SULKY / SLEEPY always speak from their mood pool (needs and bedtime come first;
 *   SLEEPY also replaces the late-night lines).
 * - HAPPY / CONTENT still use the low-battery / music lines 70% of the time when those apply,
 *   otherwise their mood pool.
 * Without a [petMood] the original context-only behavior is unchanged.
 */
fun selectSpeechBubbleDialogue(
    context: Context? = null,
    previousLine: String? = null,
    hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    isLowBattery: Boolean? = null,
    isMusicPlaying: Boolean? = null,
    petMood: PetMood? = null,
    customDialogue: BundleMascotDialogue? = CustomMascotManager.activeDialogue()
): String {
    if (petMood != null) {
        val moodPool = petMoodLines(petMood, customDialogue)
        if (petMood == PetMood.HUNGRY || petMood == PetMood.SULKY || petMood == PetMood.SLEEPY) {
            return pickLineAvoiding(moodPool, previousLine)
        }
        val isLowBatt = isLowBattery ?: (context != null && isDeviceBatteryLow(context))
        val isMusic = isMusicPlaying ?: MediaSessionTracker.mediaState.value.isPlaying
        val contextualPool = buildList {
            if (isLowBatt) addAll(LOW_BATTERY_LINES)
            if (isMusic) addAll(MUSIC_LINES)
        }
        val pool = if (contextualPool.isNotEmpty() && Random.nextFloat() < 0.70f) contextualPool else moodPool
        return pickLineAvoiding(pool, previousLine)
    }

    val isLateNight = hour >= 22 || hour < 5
    val isLowBatt = isLowBattery ?: (context != null && isDeviceBatteryLow(context))
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

    return pickLineAvoiding(candidatePool, previousLine)
}

/** How long a speech bubble line stays up. */
private const val SPEECH_BUBBLE_MS = 3_500L

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
        modifier = modifier
            .widthIn(min = 48.dp, max = 260.dp)
            .graphicsLayer { clip = false },
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .background(bgColor, RoundedCornerShape(12.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = M3x6FontFamily,
                fontSize = 18.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .padding(end = 20.dp)
                .offset(y = (-1.5).dp)
                .size(width = 9.dp, height = 5.5.dp)
                .drawWithCache {
                    val width = size.width
                    val height = size.height
                    val fillPath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(width, 0f)
                        lineTo(width * 0.55f, height)
                        close()
                    }
                    val strokePath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(width * 0.55f, height)
                        lineTo(width, 0f)
                    }
                    val tailStroke = Stroke(width = 1.5f * density, cap = StrokeCap.Round)
                    onDrawBehind {
                        drawPath(path = fillPath, color = bgColor)
                        drawPath(
                            path = strokePath,
                            color = borderColor,
                            style = tailStroke
                        )
                    }
                }
        )
    }
}

/**
 * Mochibunny Mascot coupled with a dynamic, keitai-inspired speech bubble reaction system.
 *
 * On Home a tap calls [onTap] (opens the Feed / Pet / Chat pill) and each new [reaction] from the
 * pill plays once: its bubble line plus its animation slot (EAT / PET / TALK). Without [onTap], a
 * tap boops her directly.
 *
 * When she speaks (boop or pill reaction):
 * - Cancels any ongoing dismiss timer.
 * - Picks a new context-aware dialogue line avoiding the immediate predecessor.
 * - Triggers cheer bounce on the mascot and a subtle pop bounce on the bubble.
 * - Holds the speech bubble for 3.5 seconds before fading/scaling out.
 * - Fixed layout size guarantees zero layout shifting of adjacent widgets (clock, favorites card).
 * - Renders speech bubble on top of mascot sprite with explicit zIndex and draw order.
 */
@Composable
fun MochibunnyWithSpeechBubble(
    baseSlot: MascotAnimSlot = MascotAnimSlot.IDLE, // her resting (mood) animation
    frameTick: () -> Int = { 0 }, // shared stepped clock; see MochibunMascot
    themeTintColor: Color? = null,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isFocused: Boolean = false,
    reaction: MascotReaction = MascotReaction(), // pet pill actions; each new id plays once
    onTap: () -> Unit // Home opens the pet pill
) {
    val context = LocalContext.current
    val hasStepSensor = remember(context) {
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }
    val theme = LocalLauncherTheme.current
    val coroutineScope = rememberCoroutineScope()

    var currentDialogue by remember { mutableStateOf<String?>(null) }
    var lastSpokenLine by remember { mutableStateOf<String?>(null) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }
    // The slot her current reaction plays (EAT / PET / TALK); IDLE when she isn't reacting.
    // The sprite plays its frames once from reactionStartTick, then returns to baseSlot.
    var activeSlot by remember { mutableStateOf(MascotAnimSlot.IDLE) }
    var reactionStartTick by remember { mutableIntStateOf(0) }
    var popAnimKey by remember { mutableIntStateOf(0) }
    val popScale = remember { Animatable(1f) }

    // Dark themes (Midnight Mochi, dark YAML / custom presets) use their own card colors
    val isDarkTheme = theme.bgColor.luminance() < 0.5f
    val bubbleBgColor = if (isDarkTheme) theme.cardBgColor else Color(0xFFFFFDFE)
    val bubbleBorderColor = theme.greetingOutlineColor
    val bubbleTextColor = if (isDarkTheme) theme.cardTextColor else theme.palette.deepAccent

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

    fun speak(line: String, slot: MascotAnimSlot) {
        dismissJob?.cancel()
        currentDialogue = line
        lastSpokenLine = line
        activeSlot = slot
        reactionStartTick = frameTick()
        popAnimKey++
        SoundEffectManager.play(context, SoundEffect.SELECT)
        dismissJob = coroutineScope.launch {
            // The reaction (and her hop) ends after 2 loops; the line stays up longer to read
            delay(MascotAtlasMath.REACTION_MS)
            activeSlot = MascotAnimSlot.IDLE
            delay(SPEECH_BUBBLE_MS - MascotAtlasMath.REACTION_MS)
            currentDialogue = null
        }
    }

    val customDialogue by CustomMascotManager.dialogue.collectAsState()

    val chatLine = {
        selectSpeechBubbleDialogue(
            context,
            lastSpokenLine ?: currentDialogue,
            petMood = PetManager.currentMood(),
            customDialogue = customDialogue
        )
    }


    // Starts at the current id, so coming back to Home never replays an old reaction.
    var handledReactionId by remember { mutableIntStateOf(reaction.id) }
    LaunchedEffect(reaction.id) {
        if (reaction.id > handledReactionId) {
            handledReactionId = reaction.id
            val line = selectReactionLine(
                kind = reaction.kind,
                previousLine = lastSpokenLine ?: currentDialogue,
                hasStepSensor = hasStepSensor,
                earnedCount = reaction.earnedCount,
                customDialogue = customDialogue,
                chatLine = chatLine
            )
            speak(line, reaction.kind.slot)
        }
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
                onTap()
            }
    ) {
        // 1. Mascot sprite rendered underneath
        MochibunMascot(
            slot = baseSlot,
            themeTintColor = themeTintColor,
            size = size,
            frameTick = frameTick,
            reactionSlot = activeSlot.takeIf { it != MascotAnimSlot.IDLE },
            reactionStartTick = reactionStartTick,
            isBooped = activeSlot != MascotAnimSlot.IDLE,
            onClick = onTap
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
                .offset(x = 10.dp, y = (-40).dp) // clears her ears
                .wrapContentWidth(align = Alignment.End, unbounded = true)
                .widthIn(max = 260.dp)
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
