package com.crinklecodone.mochibun.core.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.FrameLayout
import kotlin.math.max

/**
 * Holds one [AppWidgetHostView] inside a Mochibun card.
 *
 * - **Scaling:** the widget is laid out at its logical size and shrunk with a View scale. Because
 *   it's a real View transform, `ViewGroup` maps touches correctly, so taps still hit buttons.
 * - **Size reporting:** tells the provider the logical size it actually gets (API 31 size list).
 * - **D-pad controls:** RemoteViews buttons are touch-only, so the frame collects every clickable
 *   control, lets the D-pad walk between them, and draws a highlight around the selected one.
 *   Native focus is blocked so the widget can never steal focus from the Compose page.
 */
class WidgetFrame(context: Context) : FrameLayout(context) {

    private class Target(
        val view: View,
        val bounds: TargetBounds,
        val adapterView: AdapterView<*>?,
        val position: Int
    )

    private var content: View? = null
    private var spec: WidgetSizeSpec? = null
    private var reportedWidthPx = -1
    private var reportedHeightPx = -1

    private var targets: List<Target> = emptyList()
    private var selectedIndex = -1
    private var lastSelectedBounds: TargetBounds? = null

    private val density = resources.displayMetrics.density
    private val tmpRect = Rect()
    private val highlightRect = RectF()
    private val highlightCornerPx = 6f * density
    private val highlightFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    val isInteracting: Boolean get() = selectedIndex >= 0

    init {
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        isFocusable = false
        clipChildren = true
        setHighlightColor(0xFFE06287.toInt())
    }

    fun setHighlightColor(argb: Int) {
        highlightStroke.color = argb
        highlightFill.color = (argb and 0x00FFFFFF) or 0x33000000
        if (isInteracting) invalidate()
    }

    fun setContent(view: View) {
        if (content === view) return
        endInteraction()
        removeAllViews()
        content = view
        reportedWidthPx = -1
        reportedHeightPx = -1
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun applySpec(newSpec: WidgetSizeSpec) {
        if (spec == newSpec) return
        spec = newSpec
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val child = content ?: return
        val s = spec
        val childWidth = if (child is AppWidgetHostView && s != null) max(s.logicalWidthPx, 1) else width
        val scale = if (childWidth > 0) width.toFloat() / childWidth else 1f
        val childHeight = if (child is AppWidgetHostView && scale > 0f) (height / scale).toInt() else height
        child.measure(
            MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childHeight.coerceAtLeast(0), MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = content ?: return
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        val scale = if (child.measuredWidth > 0) width.toFloat() / child.measuredWidth else 1f
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scale
        child.scaleY = scale

        if (child is AppWidgetHostView) {
            maybeReportSize(child)
            // The widget may have redrawn with new views; keep the highlight on a live control
            if (isInteracting) refreshTargets()
        }
    }

    private fun maybeReportSize(host: AppWidgetHostView) {
        val w = host.measuredWidth
        val h = host.measuredHeight
        if (w <= 0 || h <= 0 || (w == reportedWidthPx && h == reportedHeightPx)) return
        reportedWidthPx = w
        reportedHeightPx = h
        // Binder call: keep it out of the layout pass
        post {
            if (content !== host || host.appWidgetId <= 0) return@post
            val widthDp = (w - host.paddingLeft - host.paddingRight).coerceAtLeast(1) / density
            val heightDp = (h - host.paddingTop - host.paddingBottom).coerceAtLeast(1) / density
            try {
                host.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
            } catch (e: Exception) {
                Log.w(TAG, "updateAppWidgetSize failed for id=${host.appWidgetId}", e)
            }
        }
    }

    // ── D-pad control selection ───────────────────────────────────────

    /**
     * Starts walking the widget's controls with the D-pad.
     * Returns how many tappable controls the widget has (0 = nothing to select).
     */
    fun beginInteraction(): Int {
        targets = collectTargets()
        selectedIndex = WidgetTargetNavigator.findStart(targets.map { it.bounds })
        lastSelectedBounds = targets.getOrNull(selectedIndex)?.bounds
        invalidate()
        return targets.size
    }

    /** Moves the highlight. Returns false when there's no control in that direction. */
    fun moveSelection(direction: NavDirection): Boolean {
        if (!isInteracting) return false
        refreshTargets()
        val next = WidgetTargetNavigator.findNext(targets.map { it.bounds }, selectedIndex, direction)
        if (next < 0) return false
        selectedIndex = next
        lastSelectedBounds = targets[next].bounds
        invalidate()
        return true
    }

    /** Taps the selected control (or the only one, if [beginInteraction] found exactly one). */
    fun clickSelection(): Boolean {
        refreshTargets()
        val target = targets.getOrNull(selectedIndex) ?: return false
        return try {
            val adapterView = target.adapterView
            if (adapterView != null) {
                adapterView.performItemClick(
                    target.view,
                    target.position,
                    adapterView.getItemIdAtPosition(target.position)
                )
            } else {
                target.view.performClick()
            }
        } catch (e: Exception) {
            // A provider's PendingIntent can be cancelled underneath us
            Log.w(TAG, "Widget control click failed", e)
            false
        }
    }

    fun endInteraction() {
        if (selectedIndex < 0 && targets.isEmpty()) return
        selectedIndex = -1
        targets = emptyList()
        lastSelectedBounds = null
        invalidate()
    }

    private fun refreshTargets() {
        val previousView = targets.getOrNull(selectedIndex)?.view
        val fresh = collectTargets()
        targets = fresh
        if (fresh.isEmpty()) {
            selectedIndex = -1
            invalidate()
            return
        }
        val sameView = fresh.indexOfFirst { it.view === previousView }
        selectedIndex = when {
            sameView >= 0 -> sameView
            lastSelectedBounds != null -> WidgetTargetNavigator.findClosest(fresh.map { it.bounds }, lastSelectedBounds!!)
            else -> WidgetTargetNavigator.findStart(fresh.map { it.bounds })
        }
        lastSelectedBounds = fresh[selectedIndex].bounds
        invalidate()
    }

    private fun collectTargets(): List<Target> {
        val host = content as? AppWidgetHostView ?: return emptyList()
        val out = ArrayList<Target>()
        collectInto(host, host, out)
        val tolerancePx = (4 * density).toInt()
        return WidgetTargetNavigator.collapseDuplicates(out.map { it.bounds }, tolerancePx).map { out[it] }
    }

    private fun collectInto(view: View, host: View, out: MutableList<Target>) {
        if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return
        // RemoteViews checkboxes/switches (API 31) toggle on click without an OnClickListener
        val tappable = view.hasOnClickListeners() || view is CompoundButton
        if (view !== host && view.isEnabled && view.isClickable && tappable) {
            boundsInHost(view, host)?.let { out += Target(view, it, null, -1) }
        }
        if (view is AdapterView<*> && view.onItemClickListener != null) {
            // Collection widgets (lists/grids) click whole rows through the AdapterView
            for (i in 0 until view.childCount) {
                val row = view.getChildAt(i)
                if (row.visibility != View.VISIBLE || row.hasOnClickListeners()) continue
                boundsInHost(row, host)?.let {
                    out += Target(row, it, view, view.firstVisiblePosition + i)
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectInto(view.getChildAt(i), host, out)
        }
    }

    private fun boundsInHost(view: View, host: View): TargetBounds? {
        tmpRect.set(0, 0, view.width, view.height)
        try {
            (host as ViewGroup).offsetDescendantRectToMyCoords(view, tmpRect)
        } catch (_: IllegalArgumentException) {
            return null
        }
        // Skip controls scrolled out of view inside list widgets
        if (!tmpRect.intersect(0, 0, host.width, host.height)) return null
        return TargetBounds(tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val bounds = targets.getOrNull(selectedIndex)?.bounds ?: return
        val child = content ?: return
        val scale = child.scaleX
        val inset = highlightStroke.strokeWidth / 2f
        highlightRect.set(
            bounds.left * scale + inset,
            bounds.top * scale + inset,
            bounds.right * scale - inset,
            bounds.bottom * scale - inset
        )
        canvas.drawRoundRect(highlightRect, highlightCornerPx, highlightCornerPx, highlightFill)
        canvas.drawRoundRect(highlightRect, highlightCornerPx, highlightCornerPx, highlightStroke)
    }

    private companion object {
        const val TAG = "WidgetFrame"
    }
}
