package com.crinklecodone.mochibun.core.widget

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How a hosted app widget is laid out inside its Mochibun card.
 *
 * The widget is laid out at [logicalWidthPx] x [logicalHeightPx] (the size it believes it has)
 * and then drawn at [scale], so on screen it takes [displayWidthPx] x [displayHeightPx].
 * Widgets designed for 1080p phones are wider than the AT-M120's 480 px screen; shrinking them
 * with a View scale keeps their whole layout visible instead of clipping the right edge.
 */
data class WidgetSizeSpec(
    val logicalWidthPx: Int,
    val logicalHeightPx: Int,
    val scale: Float
) {
    val displayWidthPx: Int get() = (logicalWidthPx * scale).roundToInt()
    val displayHeightPx: Int get() = (logicalHeightPx * scale).roundToInt()
}

object WidgetLayoutMath {
    /** Below this a scaled widget becomes unreadable, so it's clipped instead. */
    const val MIN_SCALE = 0.55f

    /**
     * Works out the logical size and scale for a widget card.
     *
     * All sizes are in pixels. `AppWidgetProviderInfo.minWidth` and friends are already converted
     * to pixels for this display by `AppWidgetManager`.
     */
    fun computeSize(
        availableWidthPx: Int,
        minWidthPx: Int,
        minHeightPx: Int,
        minResizeWidthPx: Int,
        resizesHorizontally: Boolean,
        floorHeightPx: Int,
        maxDisplayHeightPx: Int
    ): WidgetSizeSpec {
        val available = availableWidthPx.coerceAtLeast(1)

        // A horizontally resizable widget can shrink down to its minResizeWidth without scaling
        val neededWidth = if (resizesHorizontally && minResizeWidthPx in 1 until minWidthPx) {
            minResizeWidthPx
        } else {
            minWidthPx
        }
        val neededHeight = max(minHeightPx, floorHeightPx).coerceAtLeast(1)

        var scale = available.toFloat() / max(available, neededWidth)
        // Tall widgets get shrunk too, so one widget can't swallow the whole 800 px page
        if (maxDisplayHeightPx > 0 && neededHeight * scale > maxDisplayHeightPx) {
            scale = maxDisplayHeightPx.toFloat() / neededHeight
        }
        scale = scale.coerceIn(MIN_SCALE, 1f)

        // The logical width always fills the card exactly once scaled
        val logicalWidth = (available / scale).roundToInt()
        val logicalHeight = if (maxDisplayHeightPx > 0) {
            min(neededHeight, (maxDisplayHeightPx / scale).roundToInt())
        } else {
            neededHeight
        }
        return WidgetSizeSpec(logicalWidth, logicalHeight, scale)
    }
}

/** On-screen bounds of a tappable control inside a widget, in the widget's own pixel space. */
data class TargetBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(other: TargetBounds): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    fun sameAs(other: TargetBounds, tolerancePx: Int): Boolean =
        abs(left - other.left) <= tolerancePx && abs(top - other.top) <= tolerancePx &&
            abs(right - other.right) <= tolerancePx && abs(bottom - other.bottom) <= tolerancePx
}

enum class NavDirection { UP, DOWN, LEFT, RIGHT }

/**
 * D-pad spatial navigation between the tappable controls of a hosted widget.
 * RemoteViews controls are touch-only and usually not focusable, so Mochibun walks them itself.
 */
object WidgetTargetNavigator {

    /**
     * Returns the index of the control to move to from [current] in [direction],
     * or -1 when there's nothing further that way.
     */
    fun findNext(targets: List<TargetBounds>, current: Int, direction: NavDirection): Int {
        val from = targets.getOrNull(current) ?: return -1
        var bestIndex = -1
        var bestScore = Float.MAX_VALUE
        for (i in targets.indices) {
            if (i == current) continue
            val candidate = targets[i]
            val dx = candidate.centerX - from.centerX
            val dy = candidate.centerY - from.centerY
            // Distance along the pressed direction, and drift sideways from it
            val (along, across) = when (direction) {
                NavDirection.UP -> -dy to abs(dx)
                NavDirection.DOWN -> dy to abs(dx)
                NavDirection.LEFT -> -dx to abs(dy)
                NavDirection.RIGHT -> dx to abs(dy)
            }
            if (along <= 0.5f) continue
            // Sideways drift costs more, so moving right prefers the control on the same row
            val score = along + across * 2f
            if (score < bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * The control a fresh selection starts on: the top-most, then left-most real button.
     * Containers that wrap other controls (a widget's "open app" background) are only picked
     * when there's nothing else.
     */
    fun findStart(targets: List<TargetBounds>): Int {
        if (targets.isEmpty()) return -1
        val leaves = targets.indices.filter { i ->
            targets.indices.none { j -> j != i && targets[i].contains(targets[j]) }
        }
        return leaves.ifEmpty { targets.indices.toList() }.minWith(
            compareBy<Int>({ targets[it].top }, { targets[it].left })
        )
    }

    /**
     * Indices of the controls worth keeping when several share the same bounds (a clickable
     * root wrapping a clickable layout of the same size). [targets] is in tree pre-order, so
     * the later one is the innermost: the view a real touch at that spot would reach.
     */
    fun collapseDuplicates(targets: List<TargetBounds>, tolerancePx: Int): List<Int> {
        val kept = ArrayList<Int>(targets.size)
        for (i in targets.indices) {
            val same = kept.indexOfLast { targets[it].sameAs(targets[i], tolerancePx) }
            if (same >= 0) kept[same] = i else kept += i
        }
        return kept
    }

    /** The control whose centre is closest to [previous], for keeping the selection after a widget redraws. */
    fun findClosest(targets: List<TargetBounds>, previous: TargetBounds): Int {
        if (targets.isEmpty()) return -1
        return targets.indices.minBy {
            val dx = targets[it].centerX - previous.centerX
            val dy = targets[it].centerY - previous.centerY
            dx * dx + dy * dy
        }
    }
}
