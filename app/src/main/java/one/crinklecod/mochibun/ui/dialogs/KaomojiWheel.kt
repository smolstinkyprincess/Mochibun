package one.crinklecod.mochibun.ui.dialogs

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.crinklecod.mochibun.core.keypad.WheelMode
import one.crinklecod.mochibun.core.keypad.isWheelModeToggleKey
import one.crinklecod.mochibun.core.keypad.isWheelStarToggleKey
import one.crinklecod.mochibun.core.keypad.wheelItemIndexForDigit
import one.crinklecod.mochibun.core.keypad.wheelPetalForDigit
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.repository.ClipRingEntry
import one.crinklecod.mochibun.data.repository.ClipboardRingRepository
import one.crinklecod.mochibun.data.repository.KaomojiRepository
import one.crinklecod.mochibun.ui.components.BottomLavenderClouds
import one.crinklecod.mochibun.ui.components.KeitaiLaceContainer
import one.crinklecod.mochibun.ui.components.curtainUnfurl
import one.crinklecod.mochibun.ui.components.rememberSteppedTickerText
import one.crinklecod.mochibun.ui.performBoundaryHaptic
import one.crinklecod.mochibun.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

// Lightweight alpha glow ramp matching Mochibun's soft pastel focus glow
private val GLOW_EXPANSIONS_DP = floatArrayOf(2.5f, 1.8f, 1.0f, 0.4f)
private val GLOW_ALPHAS = floatArrayOf(0.12f, 0.28f, 0.50f, 0.70f)

private val ThinBorderStroke = Stroke(width = 1.5f)
private val HighlightBorderStroke = Stroke(width = 2.0f)

private const val COMPASS_TILT_WINDOW_MS = 900L

enum class WheelStage {
    CATEGORY,
    ITEM
}

enum class CompassCardinal(val categoryIndex: Int) {
    NORTH(0), // 12:00 Happy
    EAST(2),  // 3:00 Love
    SOUTH(4), // 6:00 Sleepy
    WEST(6)   // 9:00 Shock
}

enum class CompassNavResult {
    NAVIGATED,
    LOCKED_STAGE
}

internal fun adjacentDiagonalIndex(first: CompassCardinal, second: CompassCardinal): Int? =
    when {
        (first == CompassCardinal.NORTH && second == CompassCardinal.EAST) ||
            (first == CompassCardinal.EAST && second == CompassCardinal.NORTH) -> 1 // 1:30 Hype
        (first == CompassCardinal.EAST && second == CompassCardinal.SOUTH) ||
            (first == CompassCardinal.SOUTH && second == CompassCardinal.EAST) -> 3 // 4:30 Cute
        (first == CompassCardinal.SOUTH && second == CompassCardinal.WEST) ||
            (first == CompassCardinal.WEST && second == CompassCardinal.SOUTH) -> 5 // 7:30 Shy
        (first == CompassCardinal.WEST && second == CompassCardinal.NORTH) ||
            (first == CompassCardinal.NORTH && second == CompassCardinal.WEST) -> 7 // 10:30 Silly
        else -> null
    }

/**
 * Hoisted wheel selection so the same UI can be driven by the dialog or the global overlay.
 *
 * [clipEntriesProvider] is read when the wheel switches to [WheelMode.CLIPBOARD_SNIPPETS], so the
 * petals show the clipboard as it is at that moment rather than when the wheel opened.
 */
@Stable
class KaomojiWheelState(
    private val clipEntriesProvider: () -> List<ClipRingEntry> = ClipboardRingRepository::ringEntries,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    val categories = KaomojiRepository.categories
    var categoryIndex by mutableIntStateOf(0)
    var itemIndex by mutableIntStateOf(0)
    var stage by mutableStateOf(WheelStage.CATEGORY)

    var mode by mutableStateOf(WheelMode.KAOMOJI)
        private set
    var clipEntries by mutableStateOf<List<ClipRingEntry>>(emptyList())
        private set
    var clipIndex by mutableIntStateOf(0)
        private set
    var clipWedgeIndex by mutableIntStateOf(0)
        private set

    private var lastCardinal: CompassCardinal? = null
    private var lastCardinalTimeMs: Long = 0L
    private var hasPointedInCategoryStage: Boolean = false

    private var activeWedgeIndex: Int
        get() = if (mode == WheelMode.CLIPBOARD_SNIPPETS) clipWedgeIndex else categoryIndex
        set(value) {
            if (mode == WheelMode.CLIPBOARD_SNIPPETS) {
                clipWedgeIndex = value.coerceIn(0, ClipboardRingRepository.MAX_RING_PETALS - 1)
                if (clipWedgeIndex in clipEntries.indices) {
                    clipIndex = clipWedgeIndex
                }
            } else {
                categoryIndex = value
                itemIndex = 0
            }
        }

    val currentKaomoji: String
        get() {
            val c = categories[categoryIndex]
            return c.items.getOrElse(itemIndex) { c.items.firstOrNull() ?: "" }
        }

    val currentClip: ClipRingEntry?
        get() = if (stage == WheelStage.CATEGORY) {
            clipEntries.getOrNull(clipWedgeIndex)
        } else {
            clipEntries.getOrNull(clipIndex)
        }

    /** Text the wheel inserts / copies on confirm in the current mode. */
    val currentText: String
        get() = when (mode) {
            WheelMode.KAOMOJI -> currentKaomoji
            WheelMode.CLIPBOARD_SNIPPETS -> currentClip?.text.orEmpty()
        }

    fun toggleMode() {
        mode = mode.toggled()
        stage = WheelStage.CATEGORY
        lastCardinal = null
        hasPointedInCategoryStage = false
        if (mode == WheelMode.CLIPBOARD_SNIPPETS) {
            clipEntries = clipEntriesProvider()
            clipIndex = 0
            clipWedgeIndex = 0
        }
    }

    fun lockCategory(index: Int = categoryIndex): Boolean {
        if (index !in categories.indices) return false
        if (index != categoryIndex) {
            categoryIndex = index
            itemIndex = 0
        }
        stage = WheelStage.ITEM
        lastCardinal = null
        hasPointedInCategoryStage = false
        return true
    }

    fun lockClip(index: Int = clipWedgeIndex, requirePresent: Boolean = false): Boolean {
        if (index !in 0 until ClipboardRingRepository.MAX_RING_PETALS) return false
        if (clipEntries.isEmpty()) return false
        if (requirePresent && index !in clipEntries.indices) return false
        val target = index.coerceIn(0, clipEntries.lastIndex)
        clipIndex = target
        clipWedgeIndex = target
        stage = WheelStage.ITEM
        lastCardinal = null
        hasPointedInCategoryStage = false
        return true
    }

    fun returnToCategories() {
        stage = WheelStage.CATEGORY
        lastCardinal = null
        hasPointedInCategoryStage = false
        if (mode == WheelMode.CLIPBOARD_SNIPPETS && clipEntries.isNotEmpty()) {
            clipWedgeIndex = clipIndex.coerceIn(0, ClipboardRingRepository.MAX_RING_PETALS - 1)
        }
    }

    fun pointCardinal(cardinal: CompassCardinal): CompassNavResult {
        val now = clockMs()
        val prev = lastCardinal
        if (prev != null && (now - lastCardinalTimeMs) <= COMPASS_TILT_WINDOW_MS) {
            val diagonal = adjacentDiagonalIndex(prev, cardinal)
            if (diagonal != null && activeWedgeIndex == prev.categoryIndex) {
                activeWedgeIndex = diagonal
                lastCardinal = null
                hasPointedInCategoryStage = true
                return CompassNavResult.NAVIGATED
            }
        }
        if (activeWedgeIndex == cardinal.categoryIndex && hasPointedInCategoryStage) {
            val locked = if (mode == WheelMode.CLIPBOARD_SNIPPETS) {
                lockClip(cardinal.categoryIndex)
            } else {
                lockCategory(cardinal.categoryIndex)
            }
            return if (locked) CompassNavResult.LOCKED_STAGE else CompassNavResult.NAVIGATED
        }
        if (activeWedgeIndex != cardinal.categoryIndex) {
            activeWedgeIndex = cardinal.categoryIndex
        }
        lastCardinal = cardinal
        lastCardinalTimeMs = now
        hasPointedInCategoryStage = true
        return CompassNavResult.NAVIGATED
    }

    fun selectItem(index: Int): Boolean = when (mode) {
        WheelMode.KAOMOJI -> {
            val items = categories[categoryIndex].items
            if (index !in items.indices) false
            else {
                itemIndex = index
                true
            }
        }
        WheelMode.CLIPBOARD_SNIPPETS -> selectClip(index)
    }

    fun stepItem(delta: Int): Boolean = when (mode) {
        WheelMode.KAOMOJI -> {
            val count = categories[categoryIndex].items.size
            if (count == 0) false
            else {
                itemIndex = (itemIndex + delta + count) % count
                true
            }
        }
        WheelMode.CLIPBOARD_SNIPPETS -> stepClip(delta)
    }

    fun selectClip(index: Int): Boolean {
        if (index !in clipEntries.indices) return false
        clipIndex = index
        clipWedgeIndex = index.coerceIn(0, ClipboardRingRepository.MAX_RING_PETALS - 1)
        return true
    }

    fun stepClip(delta: Int): Boolean {
        val count = clipEntries.size
        if (count <= 1) return false
        clipIndex = (clipIndex + delta + count) % count
        clipWedgeIndex = clipIndex.coerceIn(0, ClipboardRingRepository.MAX_RING_PETALS - 1)
        return true
    }
}

/**
 * The IME's Kaomoji / Snippet Wheel, staged "rising out of the cloud shelf" like
 * FavoriteFolderDialog. Z-order, back to front:
 *   1. flat dim scrim + backdrop sparkles (never blur: PowerVR GE8300); tapping it dismisses
 *   2. wheel card anchored low, crowned with the ribbon bow; its bottom rim tucks behind the clouds
 *   3. raised BottomLavenderClouds shelf (+ solid base strip to the screen edge)
 * No close pill: BACK / F4 / `#` (or a tap on the scrim) dismiss it.
 *
 * Hosted only by MochibunImeService (`#` hold while typing), which owns the keypad routing and
 * makes its window touchable while this is shown.
 */
@Composable
fun KaomojiWheelCloudStage(
    state: KaomojiWheelState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dismissWithSound = {
        SoundEffectManager.play(context, SoundEffect.BACK)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(onClick = dismissWithSound)
    ) {
        FloatingPixelBackdrop()

        // Taps inside the card must not reach the dismissing scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WHEEL_CARD_BOTTOM_INSET)
                .graphicsLayer { clip = false }
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            KaomojiWheelCard(
                state = state,
                onConfirm = onConfirm,
                bottomContentInset = WHEEL_CARD_INNER_BOTTOM
            )
            // Pixel ribbon bow straddling the top lace rim. KaomojiWheelCard keeps 8dp of outer
            // padding above the lace, hence -4dp rather than FavoriteFolderDialog's -12dp.
            PixelRibbonBow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-4).dp)
                    .graphicsLayer { clip = false },
                width = 34.dp,
                height = 28.dp
            )
        }

        // Cloud shelf over the card's lower rim. The cloud paths are anchored to their box
        // bottom, so the shelf is lifted with an offset rather than made taller.
        BottomLavenderClouds(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -WHEEL_CLOUD_SHELF_LIFT)
        )
        // Solid base under the lifted shelf: fills the gap to the screen edge and hides the
        // cloud path's closing outline along its (now visible) bottom edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WHEEL_CLOUD_SHELF_LIFT + 1.dp)
                .background(ActiveTheme.palette.cloudFront)
        )
    }
}

// Cloud-shelf geometry (dp above the screen's bottom edge), matching FavoriteFolderDialog for
// 480 × 800: the shelf's back puffs cover the lace card's bottom rim (35dp up: the inset plus the
// card's own 8dp outer padding), and WHEEL_CARD_INNER_BOTTOM lifts the hint pill to ~80dp, clear
// of the clouds. Clip mode is the tallest layout (~410dp + bow) and still fits under the status bar.
private val WHEEL_CLOUD_SHELF_LIFT = 10.dp
private val WHEEL_CARD_BOTTOM_INSET = 27.dp
private val WHEEL_CARD_INNER_BOTTOM = 16.dp

/**
 * Shared D-pad / digit / action navigation. Returns true if the key was handled.
 *
 * Stage 1 (`WheelStage.CATEGORY`):
 * - Cardinal D-pad points to N/E/S/W; pressing an adjacent perpendicular direction within 900 ms
 *   tilts onto the diagonal wedge between them (NE/SE/SW/NW).
 * - Pressing the same cardinal again or `DPAD_CENTER`/`ENTER` locks the wedge into Stage 2.
 * - `1`–`8` jump directly to a wedge and lock into Stage 2.
 * - `BACK` / `#` closes the wheel.
 *
 * Stage 2 (`WheelStage.ITEM`):
 * - `1`–`9` (`0..8`) and `0` (`9`) immediately pick that item/clip and confirm via [onConfirm].
 * - `DPAD_LEFT` / `DPAD_RIGHT` cycle items; `DPAD_CENTER` / `ENTER` confirms.
 * - `BACK` / `DPAD_UP` unlocks back to Stage 1; `#` closes the wheel.
 *
 * `*` toggles Kaomoji <-> Clips & Snippets in both stages (`0` also toggles outside Stage 2).
 */
fun handleKaomojiWheelNavKey(
    state: KaomojiWheelState,
    keyCode: Int,
    context: Context,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
): Boolean {
    val inItemStage = state.stage == WheelStage.ITEM
    val shouldToggleMode = if (inItemStage) isWheelStarToggleKey(keyCode) else isWheelModeToggleKey(keyCode)
    if (shouldToggleMode) {
        state.toggleMode()
        SoundEffectManager.play(context, SoundEffect.NAV)
        return true
    }

    if (keyCode == KeyEvent.KEYCODE_POUND || keyCode == KeyEvent.KEYCODE_F4) {
        SoundEffectManager.play(context, SoundEffect.BACK)
        onDismiss()
        return true
    }

    if (state.stage == WheelStage.CATEGORY) {
        val petal = wheelPetalForDigit(keyCode)
        if (petal != null) {
            val locked = if (state.mode == WheelMode.CLIPBOARD_SNIPPETS) {
                state.lockClip(petal, requirePresent = true)
            } else {
                state.lockCategory(petal)
            }
            if (locked) {
                SoundEffectManager.play(context, SoundEffect.SELECT)
            } else {
                performBoundaryHaptic(context)
            }
            return true
        }
        val cardinal = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> CompassCardinal.NORTH
            KeyEvent.KEYCODE_DPAD_RIGHT -> CompassCardinal.EAST
            KeyEvent.KEYCODE_DPAD_DOWN -> CompassCardinal.SOUTH
            KeyEvent.KEYCODE_DPAD_LEFT -> CompassCardinal.WEST
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                val locked = if (state.mode == WheelMode.CLIPBOARD_SNIPPETS) {
                    state.lockClip()
                } else {
                    state.lockCategory()
                }
                if (locked) {
                    SoundEffectManager.play(context, SoundEffect.SELECT)
                } else {
                    performBoundaryHaptic(context)
                }
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                SoundEffectManager.play(context, SoundEffect.BACK)
                onDismiss()
                return true
            }
            else -> return false
        }
        val result = state.pointCardinal(cardinal)
        SoundEffectManager.play(
            context,
            if (result == CompassNavResult.LOCKED_STAGE) SoundEffect.SELECT else SoundEffect.NAV
        )
        return true
    }

    // Stage 2: WheelStage.ITEM (shared by Kaomoji and Clips & Snippets)
    val quickPick = wheelItemIndexForDigit(keyCode)
    if (quickPick != null) {
        if (state.selectItem(quickPick)) {
            onConfirm()
        } else {
            performBoundaryHaptic(context)
        }
        return true
    }
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (state.stepItem(-1)) SoundEffectManager.play(context, SoundEffect.NAV)
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (state.stepItem(1)) SoundEffectManager.play(context, SoundEffect.NAV)
            true
        }
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            onConfirm()
            true
        }
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_DPAD_UP -> {
            state.returnToCategories()
            SoundEffectManager.play(context, SoundEffect.BACK)
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> true
        else -> false
    }
}

internal fun wheelDigitBadge(index: Int): String = "[${if (index == 9) 0 else index + 1}]"

/** Single-line petal label: whitespace runs collapsed, cut to a few characters. */
internal fun clipPetalLabel(entry: ClipRingEntry, maxChars: Int = 7): String {
    val flat = entry.text.replace(WHITESPACE_RUN, " ")
    val cut = if (flat.length > maxChars) flat.take(maxChars - 1).trimEnd() + "…" else flat
    return if (entry.pinned) "★$cut" else cut
}

private val WHITESPACE_RUN = Regex("\\s+")

@Composable
fun KaomojiWheelCard(
    state: KaomojiWheelState,
    onConfirm: () -> Unit,
    /** Extra space under the hint pill so it clears a cloud shelf drawn over the card's rim. */
    bottomContentInset: Dp = 0.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val theme = LocalLauncherTheme.current

    // High-contrast theme & translucent surface tokens (independent of D-pad state)
    val plumOutline = theme.greetingOutlineColor
    val cardBackground = theme.cardBgColor.copy(alpha = 0.82f)
    val glowColor = theme.selectionGlowColor
    val surfaceTint = theme.surfaceTint
    val isDarkTheme = ColorContrast.luminance(theme.cardBgColor.toArgb()) < 0.35

    val headerColor = if (isDarkTheme) theme.appActionColor else plumOutline
    val deepPlumText = if (isDarkTheme) theme.fgTextColor else Color(0xFF4A2E56)
    val selectedVioletText = if (isDarkTheme) theme.fgTextColor else Color(0xFF3A1C44)
    val unselectedGlyphColor = if (isDarkTheme) theme.cardTextColor else Color(0xFF6E4A74)
    val selectedGlyphColor = if (isDarkTheme) theme.appActionColor else Color(0xFF5A2248)
    val activeCategoryRose = if (isDarkTheme) theme.appActionColor else Color(0xFF8A2E54)
    val activeKaomojiPlum = if (isDarkTheme) theme.fgTextColor else Color(0xFF34153B)
    val stepperPlum = if (isDarkTheme) theme.cardTextColor else Color(0xFF5A335E)
    val helperRose = if (isDarkTheme) theme.appActionColor.copy(alpha = 0.9f) else Color(0xFF7D2C4D)
    val selectedFillAlpha = if (isDarkTheme) 0.32f else 0.55f
    val unselectedFillAlpha = if (isDarkTheme) 0.24f else 0.38f

    // Snug geometric dimensions optimized for 480x800 portrait display
    val wheelDiameter = 220.dp
    val rOuter = 106.dp
    val rInner = 56.dp
    val strokeWidth = (rOuter - rInner)
    val rMid = (rInner + rOuter) / 2f

    val bodyStroke = remember(density) { Stroke(width = strokeWidth.value * density) }
    val glowStrokes = remember(density) {
        Array(4) { g ->
            Stroke(width = (strokeWidth.value + GLOW_EXPANSIONS_DP[g] * 2f) * density)
        }
    }

    val onSelectPetal: (Int) -> Unit = remember(state, context) {
        { index ->
            if (state.stage == WheelStage.CATEGORY) {
                val locked = if (state.mode == WheelMode.CLIPBOARD_SNIPPETS) {
                    state.lockClip(index, requirePresent = true)
                } else {
                    state.lockCategory(index)
                }
                if (locked) {
                    SoundEffectManager.play(context, SoundEffect.SELECT)
                } else if (state.mode == WheelMode.CLIPBOARD_SNIPPETS) {
                    performBoundaryHaptic(context)
                }
            } else {
                val activeWedge = if (state.mode == WheelMode.CLIPBOARD_SNIPPETS) {
                    state.clipWedgeIndex
                } else {
                    state.categoryIndex
                }
                if (index == activeWedge) {
                    state.returnToCategories()
                    SoundEffectManager.play(context, SoundEffect.BACK)
                } else if (state.mode == WheelMode.CLIPBOARD_SNIPPETS) {
                    if (state.selectClip(index)) SoundEffectManager.play(context, SoundEffect.NAV)
                } else if (state.lockCategory(index)) {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                }
            }
        }
    }

    val onToggleMode: () -> Unit = remember(state, context) {
        {
            state.toggleMode()
            SoundEffectManager.play(context, SoundEffect.NAV)
        }
    }

    KeitaiLaceContainer(
        cardCornerRadius = 24.dp,
        containerColor = cardBackground,
        borderColor = plumOutline.copy(alpha = 0.75f),
        modifier = Modifier
            .width(280.dp)
            .wrapContentSize()
            .curtainUnfurl(visible = true)
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KaomojiWheelHeader(
                state = state,
                headerColor = headerColor,
                onToggleMode = onToggleMode
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(wheelDiameter)
                    .graphicsLayer { clip = false },
                contentAlignment = Alignment.Center
            ) {
                KaomojiWheelCanvas(
                    state = state,
                    wheelDiameter = wheelDiameter,
                    rInner = rInner,
                    rOuter = rOuter,
                    rMid = rMid,
                    density = density,
                    isDarkTheme = isDarkTheme,
                    glowColor = glowColor,
                    surfaceTint = surfaceTint,
                    plumOutline = plumOutline,
                    appActionColor = theme.appActionColor,
                    selectedFillAlpha = selectedFillAlpha,
                    unselectedFillAlpha = unselectedFillAlpha,
                    bodyStroke = bodyStroke,
                    glowStrokes = glowStrokes
                )

                KaomojiWheelPetals(
                    state = state,
                    rMid = rMid,
                    selectedVioletText = selectedVioletText,
                    deepPlumText = deepPlumText,
                    selectedGlyphColor = selectedGlyphColor,
                    unselectedGlyphColor = unselectedGlyphColor,
                    onSelectPetal = onSelectPetal
                )

                KaomojiWheelCenterHub(
                    state = state,
                    rInner = rInner,
                    cardBackground = cardBackground,
                    glowColor = glowColor,
                    plumOutline = plumOutline,
                    activeCategoryRose = activeCategoryRose,
                    activeKaomojiPlum = activeKaomojiPlum,
                    stepperPlum = stepperPlum,
                    helperRose = helperRose,
                    onConfirm = onConfirm
                )
            }

            KaomojiWheelClipPreview(
                state = state,
                cardBackground = cardBackground,
                glowColor = glowColor,
                plumOutline = plumOutline,
                activeKaomojiPlum = activeKaomojiPlum,
                deepPlumText = deepPlumText,
                onConfirm = onConfirm
            )

            Spacer(modifier = Modifier.height(8.dp))

            KaomojiWheelBottomHint(
                state = state,
                isDarkTheme = isDarkTheme,
                unfocusedCardBgColor = theme.unfocusedCardBgColor,
                surfaceTint = surfaceTint,
                plumOutline = plumOutline,
                deepPlumText = deepPlumText
            )
            if (bottomContentInset > 0.dp) Spacer(modifier = Modifier.height(bottomContentInset))
        }
    }
}

@Composable
private fun KaomojiWheelHeader(
    state: KaomojiWheelState,
    headerColor: Color,
    onToggleMode: () -> Unit
) {
    val isClipMode = state.mode == WheelMode.CLIPBOARD_SNIPPETS
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isClipMode) "— ✂ Clips & Snippets —" else "— ♡ Kaomoji ♡ —",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = headerColor,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleMode)
                .padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun KaomojiWheelCanvas(
    state: KaomojiWheelState,
    wheelDiameter: Dp,
    rInner: Dp,
    rOuter: Dp,
    rMid: Dp,
    density: Float,
    isDarkTheme: Boolean,
    glowColor: Color,
    surfaceTint: Color,
    plumOutline: Color,
    appActionColor: Color,
    selectedFillAlpha: Float,
    unselectedFillAlpha: Float,
    bodyStroke: Stroke,
    glowStrokes: Array<Stroke>
) {
    Canvas(
        modifier = Modifier
            .size(wheelDiameter)
            .graphicsLayer { clip = false }
    ) {
        val isClipMode = state.mode == WheelMode.CLIPBOARD_SNIPPETS
        val isItemStage = state.stage == WheelStage.ITEM
        val petalCount = if (isClipMode) ClipboardRingRepository.MAX_RING_PETALS else state.categories.size
        val selectedPetal = if (isClipMode) state.clipWedgeIndex else state.categoryIndex

        val centerPx = Offset(size.width / 2f, size.height / 2f)
        val rInnerPx = rInner.toPx()
        val rOuterPx = rOuter.toPx()
        val rMidPx = rMid.toPx()

        val gapAngle = 5f
        val sweepAngle = (360f / petalCount) - gapAngle
        val ringDimFactor = if (isItemStage) 0.55f else 1f
        val unselectedDimFactor = if (isItemStage) 0.45f else 1f

        for (i in 0 until petalCount) {
            val centerAngle = -90f + i * (360f / petalCount)
            val startAngle = centerAngle - sweepAngle / 2f
            val isSelected = (i == selectedPetal)

            if (isSelected) {
                // Concentric glow passes matching softGlowFocus
                for (g in 0 until 4) {
                    val expansion = GLOW_EXPANSIONS_DP[g] * density
                    drawArc(
                        color = glowColor.copy(alpha = GLOW_ALPHAS[g] * ringDimFactor),
                        startAngle = startAngle - 1f,
                        sweepAngle = sweepAngle + 2f,
                        useCenter = false,
                        topLeft = Offset(centerPx.x - rMidPx - expansion, centerPx.y - rMidPx - expansion),
                        size = Size((rMidPx + expansion) * 2f, (rMidPx + expansion) * 2f),
                        style = glowStrokes[g]
                    )
                }

                // Selected petal body fill
                drawArc(
                    color = glowColor.copy(alpha = selectedFillAlpha * ringDimFactor),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - rMidPx, centerPx.y - rMidPx),
                    size = Size(rMidPx * 2f, rMidPx * 2f),
                    style = bodyStroke
                )

                // Soft outline
                val selectedOutline = (if (isDarkTheme) appActionColor else plumOutline)
                    .copy(alpha = if (isItemStage) 0.65f else 1f)
                drawArc(
                    color = selectedOutline,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - rOuterPx, centerPx.y - rOuterPx),
                    size = Size(rOuterPx * 2f, rOuterPx * 2f),
                    style = HighlightBorderStroke
                )
                drawArc(
                    color = selectedOutline,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - rInnerPx, centerPx.y - rInnerPx),
                    size = Size(rInnerPx * 2f, rInnerPx * 2f),
                    style = HighlightBorderStroke
                )
            } else {
                // Unselected translucent petal fill
                drawArc(
                    color = surfaceTint.copy(alpha = unselectedFillAlpha * unselectedDimFactor),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - rMidPx, centerPx.y - rMidPx),
                    size = Size(rMidPx * 2f, rMidPx * 2f),
                    style = bodyStroke
                )

                // Unselected subtle border
                drawArc(
                    color = plumOutline.copy(alpha = 0.30f * unselectedDimFactor),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - rOuterPx, centerPx.y - rOuterPx),
                    size = Size(rOuterPx * 2f, rOuterPx * 2f),
                    style = ThinBorderStroke
                )
                drawArc(
                    color = plumOutline.copy(alpha = 0.30f * unselectedDimFactor),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerPx.x - rInnerPx, centerPx.y - rInnerPx),
                    size = Size(rInnerPx * 2f, rInnerPx * 2f),
                    style = ThinBorderStroke
                )
            }
        }
    }
}

@Composable
private fun BoxScope.KaomojiWheelPetals(
    state: KaomojiWheelState,
    rMid: Dp,
    selectedVioletText: Color,
    deepPlumText: Color,
    selectedGlyphColor: Color,
    unselectedGlyphColor: Color,
    onSelectPetal: (Int) -> Unit
) {
    val isClipMode = state.mode == WheelMode.CLIPBOARD_SNIPPETS
    val clipEntries = state.clipEntries
    val categories = state.categories
    val petalCount = if (isClipMode) ClipboardRingRepository.MAX_RING_PETALS else categories.size

    for (i in 0 until petalCount) {
        val title: String
        val subtitle: String
        val hasEntry: Boolean
        if (isClipMode) {
            val entry = clipEntries.getOrNull(i)
            hasEntry = entry != null
            title = if (entry != null) clipPetalLabel(entry) else "—"
            subtitle = "${i + 1}"
        } else {
            hasEntry = true
            title = categories[i].shortLabel
            subtitle = categories[i].icon
        }
        WheelPetalLabel(
            index = i,
            petalCount = petalCount,
            state = state,
            rMid = rMid,
            title = title,
            subtitle = subtitle,
            hasEntry = hasEntry,
            selectedVioletText = selectedVioletText,
            deepPlumText = deepPlumText,
            selectedGlyphColor = selectedGlyphColor,
            unselectedGlyphColor = unselectedGlyphColor,
            onSelectPetal = onSelectPetal
        )
    }
}

@Composable
private fun BoxScope.WheelPetalLabel(
    index: Int,
    petalCount: Int,
    state: KaomojiWheelState,
    rMid: Dp,
    title: String,
    subtitle: String,
    hasEntry: Boolean,
    selectedVioletText: Color,
    deepPlumText: Color,
    selectedGlyphColor: Color,
    unselectedGlyphColor: Color,
    onSelectPetal: (Int) -> Unit
) {
    val isSelected by remember(index, state) {
        derivedStateOf {
            if (state.mode == WheelMode.CLIPBOARD_SNIPPETS) {
                state.clipWedgeIndex == index
            } else {
                state.categoryIndex == index
            }
        }
    }
    val isItemStage by remember(state) {
        derivedStateOf { state.stage == WheelStage.ITEM }
    }
    val labelAlpha = when {
        !hasEntry -> if (isSelected) 0.72f else 0.40f
        !isItemStage -> 1f
        isSelected -> 0.80f
        else -> 0.45f
    }
    val (xOffset, yOffset) = remember(index, petalCount, rMid) {
        val centerAngle = -90.0 + index * (360.0 / petalCount)
        val angleRad = Math.toRadians(centerAngle)
        (rMid.value * cos(angleRad)).dp to (rMid.value * sin(angleRad)).dp
    }
    val onClick = remember(index, onSelectPetal) { { onSelectPetal(index) } }

    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = xOffset, y = yOffset)
            .widthIn(max = 54.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = if (isSelected) 10.sp else 9.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = (if (isSelected) selectedVioletText else deepPlumText).copy(alpha = labelAlpha),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        Text(
            text = subtitle,
            fontSize = if (isSelected) 8.sp else 7.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            color = (if (isSelected) selectedGlyphColor else unselectedGlyphColor).copy(alpha = labelAlpha)
        )
    }
}

@Composable
private fun BoxScope.KaomojiWheelCenterHub(
    state: KaomojiWheelState,
    rInner: Dp,
    cardBackground: Color,
    glowColor: Color,
    plumOutline: Color,
    activeCategoryRose: Color,
    activeKaomojiPlum: Color,
    stepperPlum: Color,
    helperRose: Color,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val isClipMode = state.mode == WheelMode.CLIPBOARD_SNIPPETS
    val isItemStage = state.stage == WheelStage.ITEM
    val categories = state.categories
    val currentCategory = categories[state.categoryIndex]
    val currentKaomoji = state.currentKaomoji
    val clipEntries = state.clipEntries
    val currentClip = state.currentClip

    val hubDiameter = (rInner * 2f) - 4.dp
    Box(
        modifier = Modifier
            .size(if (isItemStage) hubDiameter + 4.dp else hubDiameter)
            .align(Alignment.Center)
            .then(
                if (isItemStage) {
                    Modifier.border(2.5.dp, glowColor.copy(alpha = 0.65f), CircleShape)
                } else {
                    Modifier
                }
            )
            .padding(if (isItemStage) 2.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(hubDiameter)
                .clip(CircleShape)
                .background(if (isItemStage) cardBackground.copy(alpha = 0.96f) else cardBackground)
                .border(
                    width = if (isItemStage) 2.4.dp else 1.8.dp,
                    color = if (isItemStage) plumOutline else plumOutline.copy(alpha = 0.85f),
                    shape = CircleShape
                )
                .clickable {
                    if (state.stage == WheelStage.CATEGORY) {
                        val locked = if (isClipMode) state.lockClip() else state.lockCategory()
                        if (locked) {
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                        } else {
                            performBoundaryHaptic(context)
                        }
                    } else {
                        onConfirm()
                    }
                }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    !isClipMode && isItemStage -> {
                        val isWideKaomoji = currentKaomoji.length > 13
                        val displayedKaomoji = rememberSteppedTickerText(
                            text = currentKaomoji,
                            visibleCharCount = 13,
                            enabled = isWideKaomoji
                        )
                        Text(
                            text = "♥ ${currentCategory.shortLabel} ♥",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeCategoryRose,
                            maxLines = 1
                        )
                        Text(
                            text = displayedKaomoji,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeKaomojiPlum,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = if (isWideKaomoji) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                        Text(
                            text = "${wheelDigitBadge(state.itemIndex)}  ${state.itemIndex + 1} / ${currentCategory.items.size}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stepperPlum,
                            maxLines = 1
                        )
                        Text(
                            text = "♥ Center: Insert ♥",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = helperRose,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    !isClipMode -> {
                        Text(
                            text = "♥ ${currentCategory.shortLabel} ♥",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeCategoryRose,
                            maxLines = 1
                        )
                        Text(
                            text = currentKaomoji,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeKaomojiPlum,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                        Text(
                            text = "♡ ${state.categoryIndex + 1} / ${categories.size} ♡",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stepperPlum
                        )
                        Text(
                            text = "♥ Center: Open ♥",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = helperRose,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    isItemStage && currentClip != null -> {
                        Text(
                            text = if (currentClip.pinned) "★ Snippet ★" else "✂ Clip ✂",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeCategoryRose,
                            maxLines = 1
                        )
                        Text(
                            text = clipPetalLabel(currentClip.copy(pinned = false), maxChars = 10),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeKaomojiPlum,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                        Text(
                            text = "${wheelDigitBadge(state.clipIndex)}  ${state.clipIndex + 1} / ${clipEntries.size}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stepperPlum,
                            maxLines = 1
                        )
                        Text(
                            text = "♥ Center: Insert ♥",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = helperRose,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    currentClip == null -> {
                        Text(
                            text = if (clipEntries.isEmpty()) "✂ Empty ✂" else "✂ Slot ${state.clipWedgeIndex + 1} ✂",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeCategoryRose,
                            maxLines = 1
                        )
                        Text(
                            text = if (clipEntries.isEmpty()) "no clips yet" else "empty slot",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeKaomojiPlum,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                        Text(
                            text = if (clipEntries.isEmpty()) "♡ 0 / 0 ♡" else "♡ ${state.clipWedgeIndex + 1} / ${ClipboardRingRepository.MAX_RING_PETALS} ♡",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stepperPlum
                        )
                        Text(
                            text = if (clipEntries.isEmpty()) "♥ Copy to Pin ♥" else "♥ Center: Open ♥",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = helperRose,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = if (currentClip.pinned) "★ Snippet ★" else "✂ Clip ✂",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeCategoryRose,
                            maxLines = 1
                        )
                        Text(
                            text = clipPetalLabel(currentClip.copy(pinned = false), maxChars = 10),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeKaomojiPlum,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                        Text(
                            text = "♡ ${state.clipWedgeIndex + 1} / ${ClipboardRingRepository.MAX_RING_PETALS} ♡",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stepperPlum
                        )
                        Text(
                            text = "♥ Center: Open ♥",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = helperRose,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KaomojiWheelClipPreview(
    state: KaomojiWheelState,
    cardBackground: Color,
    glowColor: Color,
    plumOutline: Color,
    activeKaomojiPlum: Color,
    deepPlumText: Color,
    onConfirm: () -> Unit
) {
    if (state.mode != WheelMode.CLIPBOARD_SNIPPETS) return
    val context = LocalContext.current
    val clipEntries = state.clipEntries
    val currentClip = state.currentClip
    Spacer(modifier = Modifier.height(6.dp))
    if (state.stage == WheelStage.ITEM && clipEntries.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (idx in clipEntries.indices) {
                val isActive = idx == state.clipIndex
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isActive) glowColor.copy(alpha = 0.55f)
                            else cardBackground.copy(alpha = 0.85f)
                        )
                        .border(
                            width = if (isActive) 1.2.dp else 0.8.dp,
                            color = if (isActive) plumOutline else plumOutline.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            if (isActive) onConfirm()
                            else if (state.selectClip(idx)) SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = wheelDigitBadge(idx),
                        fontSize = 8.5.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) activeKaomojiPlum else deepPlumText,
                        maxLines = 1
                    )
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (state.stage == WheelStage.ITEM) 68.dp else 92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBackground)
            .border(1.dp, plumOutline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = currentClip?.text
                ?: if (clipEntries.isEmpty()) {
                    "Copy some text, or add Quick Snippets in Settings → Keyboard ♡"
                } else {
                    "Empty slot — point to another petal or press Center ♡"
                },
            fontSize = 10.sp,
            fontWeight = if (currentClip != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (currentClip != null) activeKaomojiPlum else deepPlumText,
            textAlign = if (currentClip != null) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun KaomojiWheelBottomHint(
    state: KaomojiWheelState,
    isDarkTheme: Boolean,
    unfocusedCardBgColor: Color,
    surfaceTint: Color,
    plumOutline: Color,
    deepPlumText: Color
) {
    val modeAndStage by remember(state) {
        derivedStateOf { state.mode to state.stage }
    }
    val (mode, stage) = modeAndStage
    val isClipMode = mode == WheelMode.CLIPBOARD_SNIPPETS
    val isItemStage = stage == WheelStage.ITEM
    val bottomHintBg = if (isDarkTheme) {
        unfocusedCardBgColor.copy(alpha = 0.85f)
    } else {
        surfaceTint.copy(alpha = 0.55f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bottomHintBg)
            .border(1.dp, plumOutline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                isClipMode && isItemStage -> "1-8: Quick Pick • ◀▶ Browse • Back: Ring"
                isClipMode -> "D-Pad: Pick • Center: Open • */0 Kaomoji"
                isItemStage -> "1-0: Quick Pick • ◀▶ Browse • Back: Moods"
                else -> "D-Pad: Mood • Center: Open • * Clips"
            },
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = deepPlumText,
            textAlign = TextAlign.Center
        )
    }
}
