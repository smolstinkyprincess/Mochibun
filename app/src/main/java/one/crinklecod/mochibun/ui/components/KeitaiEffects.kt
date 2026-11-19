package one.crinklecod.mochibun.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 4-frame stepped keitai mascot bob (0px -> 1px -> 2px -> 1px), driven by a shared stepped clock
 * such as [rememberKeitaiStep] (4 Hz) instead of its own timer, so the bob and the sprite frames
 * tick together. [step] is read in the layout phase only: no recomposition per tick.
 */
fun Modifier.keitaiMascotBob(step: () -> Int): Modifier =
    this.offset { IntOffset(x = 0, y = MASCOT_BOB_OFFSETS[Math.floorMod(step(), MASCOT_BOB_OFFSETS.size)]) }

private val MASCOT_BOB_OFFSETS = intArrayOf(0, 1, 2, 1)

/**
 * Shared 250ms 8-step keitai rhythm (2000ms full period).
 * Automatically pauses on [Lifecycle.State.RESUMED] boundary to avoid CPU cycles when backgrounded.
 * Resets to step 0 when [active] is false.
 */
@Composable
fun rememberKeitaiStep(
    active: Boolean = true,
    totalSteps: Int = 8,
    stepDurationMs: Long = 250L
): IntState {
    val lifecycleOwner = LocalLifecycleOwner.current
    val step = remember { mutableIntStateOf(0) }
    LaunchedEffect(active, lifecycleOwner) {
        if (!active) {
            step.intValue = 0
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(stepDurationMs)
                step.intValue = (step.intValue + 1) % totalSteps
            }
        }
    }
    return step
}

/**
 * Keitai "Curtain / Blinds" Modal Unfurl:
 * Replaces modern alpha-fading with a snappy vertical unroll from the top edge downward.
 * Uses GPU scissor clipping to keep render passes cheap on low-spec hardware.
 *
 * Starts rolled up and unrolls on entry in [CURTAIN_STEPS] discrete blinds steps. Each step is
 * held for at least [CURTAIN_STEP_MS] and gets its own drawn frame, so a heavy dialog's slow first
 * frame on the MT6761 can't swallow the whole unroll (a 140 ms tween finished before its second
 * frame). Stepping with delay() also means the system animator duration scale (0 on this device)
 * doesn't skip it. The progress is read only in the graphics layer, so each step redraws the layer
 * without recomposing the dialog.
 */
fun Modifier.curtainUnfurl(visible: Boolean): Modifier = this
    .clip(RectangleShape)
    .then(CurtainUnfurlElement(visible))

private data class CurtainUnfurlElement(val visible: Boolean) : ModifierNodeElement<CurtainUnfurlNode>() {
    override fun create() = CurtainUnfurlNode(visible)
    override fun update(node: CurtainUnfurlNode) = node.update(visible)
}

/**
 * [Modifier.Node] form of the curtain (no `composed` subcomposition per modal): the blinds steps run
 * in the node's coroutine scope and the progress is read only inside the layer block.
 */
private class CurtainUnfurlNode(private var visible: Boolean) : Modifier.Node(), LayoutModifierNode {

    private val progress = mutableFloatStateOf(0f)
    private var unfurlJob: Job? = null

    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        scaleY = progress.floatValue
        transformOrigin = CURTAIN_ORIGIN
    }

    // Visibility changes animate via the layer state; nothing to re-measure.
    override val shouldAutoInvalidate: Boolean get() = false

    override fun onAttach() {
        // Equivalent to the first LaunchedEffect(visible) run: starts rolled up and unrolls.
        if (progress.floatValue != targetProgress()) launchUnfurl()
    }

    override fun onDetach() {
        unfurlJob = null
    }

    override fun onReset() {
        // Recycled nodes start rolled up again, like a freshly remembered state would.
        progress.floatValue = 0f
    }

    fun update(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (isAttached) launchUnfurl()
    }

    private fun targetProgress(): Float = if (visible) 1f else 0f

    private fun launchUnfurl() {
        unfurlJob?.cancel()
        unfurlJob = coroutineScope.launch {
            val from = progress.floatValue
            val target = targetProgress()
            for (step in 1..CURTAIN_STEPS) {
                delay(CURTAIN_STEP_MS)
                withFrameNanos { progress.floatValue = from + (target - from) * step / CURTAIN_STEPS }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = layerBlock)
        }
    }
}

private val CURTAIN_ORIGIN = TransformOrigin(0.5f, 0f)
private const val CURTAIN_STEPS = 5
private const val CURTAIN_STEP_MS = 28L

/**
 * Stepped Keitai Marquee String Generator:
 * Takes a string and returns a cyclic slice that steps by character offsets at a discrete 3 Hz (320ms).
 * Avoids 60 FPS sub-pixel text measurement while providing authentic flip-phone ticker vibes.
 */
@Composable
fun rememberSteppedTickerText(
    text: String,
    visibleCharCount: Int = 16,
    enabled: Boolean = true
): String {
    if (!enabled || text.length <= visibleCharCount) return text

    val padded = remember(text) { "$text   ★   " }
    var offset by remember(text) { mutableIntStateOf(0) }

    LaunchedEffect(padded, enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(320L) // 3 Hz tick
            offset = (offset + 1) % padded.length
        }
    }

    return remember(offset, padded) {
        val end = offset + visibleCharCount
        if (end <= padded.length) {
            padded.substring(offset, end)
        } else {
            padded.substring(offset) + padded.substring(0, end - padded.length)
        }
    }
}
