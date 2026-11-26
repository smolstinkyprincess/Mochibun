package one.crinklecod.mochibun.ui.components

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import one.crinklecod.mochibun.core.widget.MochibunWidgetHost
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.core.widget.WidgetCanvasLayout
import one.crinklecod.mochibun.core.widget.WidgetFrame
import one.crinklecod.mochibun.core.widget.WidgetLayoutMath
import one.crinklecod.mochibun.data.repository.WidgetSlot
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.palette
import one.crinklecod.mochibun.ui.theme.selectionGlowColor

private val WIDGET_MIN_HEIGHT = 64.dp
private val WIDGET_MAX_HEIGHT = 340.dp
private val UNAVAILABLE_CARD_HEIGHT = 96.dp

/**
 * Default canvas height for a newly placed widget, in grid rows: the height the old list
 * layout gave it at [widthPx] (its scaled minHeight, 64–340dp).
 */
fun defaultAppWidgetRows(info: AppWidgetProviderInfo?, widthPx: Int, stepPx: Int, density: Density): Int {
    if (info == null) return with(density) { WidgetCanvasLayout.rowsFor(UNAVAILABLE_CARD_HEIGHT.roundToPx(), stepPx) }
    val spec = with(density) {
        WidgetLayoutMath.computeSize(
            availableWidthPx = widthPx,
            minWidthPx = info.minWidth,
            minHeightPx = info.minHeight,
            minResizeWidthPx = info.minResizeWidth,
            resizesHorizontally = (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0,
            floorHeightPx = WIDGET_MIN_HEIGHT.roundToPx(),
            maxDisplayHeightPx = WIDGET_MAX_HEIGHT.roundToPx()
        )
    }
    return WidgetCanvasLayout.rowsFor(spec.displayHeightPx, stepPx)
}

/**
 * A kawaii card hosting one third-party app widget, filling the canvas slot it's given.
 *
 * The card measures its slot, asks [WidgetLayoutMath] for a logical size + scale, and hands that
 * to a [WidgetFrame] wrapped in an AndroidView. If the provider is gone ([info] == null) it
 * shows a placeholder instead, so the user can remove it on purpose.
 */
@Composable
fun AppWidgetHostCard(
    slot: WidgetSlot,
    info: AppWidgetProviderInfo?,
    host: MochibunWidgetHost,
    isFocused: Boolean,
    onFrameAttached: (appWidgetId: Int, frame: WidgetFrame) -> Unit,
    onFrameDetached: (appWidgetId: Int, frame: WidgetFrame) -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Under washi tape / a corner pin: swap a custom 9-slice frame for a plain stitched border
    plainBorder: Boolean = false
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val cardShape = remember { RoundedCornerShape(22.dp) }
    val highlightArgb = theme.selectionGlowColor.toArgb()
    val errorTextArgb = palette.textColor.toArgb()

    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = constraints.maxWidth
        val cardHeightPx = constraints.maxHeight
        val floorHeightPx = with(LocalDensity.current) { WIDGET_MIN_HEIGHT.roundToPx() }
        val spec = remember(info, availableWidthPx, cardHeightPx) {
            info?.let {
                WidgetLayoutMath.computeSize(
                    availableWidthPx = availableWidthPx,
                    minWidthPx = it.minWidth,
                    minHeightPx = it.minHeight,
                    minResizeWidthPx = it.minResizeWidth,
                    resizesHorizontally = (it.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0,
                    floorHeightPx = floorHeightPx,
                    maxDisplayHeightPx = cardHeightPx
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .softGlowFocus(isSelected = isFocused, cornerRadius = 22.dp)
                .clip(cardShape)
                .background(palette.container)
                .then(
                    if (!isFocused) {
                        Modifier.mochibunCardBorder(
                            cornerRadius = 22.dp,
                            borderColor = palette.outline,
                            borderWidth = 1.5.dp,
                            allowCustomNineSlice = !plainBorder,
                            styleOverride = if (plainBorder) CardBorderStyle.STITCH else null
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            if (info == null || spec == null) {
                UnavailableWidgetContent(slot = slot, modifier = Modifier.align(Alignment.Center))
            } else {
                key(slot.appWidgetId) {
                    AndroidView(
                        factory = { ctx ->
                            WidgetFrame(ctx).apply {
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                isFocusable = false
                                isFocusableInTouchMode = false
                            }.also { frame ->
                                val hostView = createHostViewOrError(
                                    ctx, host, slot.appWidgetId, info, errorTextArgb
                                ).apply {
                                    isFocusable = false
                                    isFocusableInTouchMode = false
                                    if (this is ViewGroup) {
                                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                    }
                                }
                                frame.setContent(hostView)
                                onFrameAttached(slot.appWidgetId, frame)
                            }
                        },
                        update = { frame ->
                            frame.applySpec(spec)
                            frame.setHighlightColor(highlightArgb)
                        },
                        onRelease = { frame ->
                            frame.endInteraction()
                            onFrameDetached(slot.appWidgetId, frame)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Corner options handle for touch users (D-pad users pick ⋯ in the arrange bar)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.unfocusedCardBg)
                    .border(1.5.dp, palette.outline, RoundedCornerShape(10.dp))
                    .clickable(onClick = onOptionsClick)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "⋯",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textColor
                )
            }
        }
    }
}

private fun createHostViewOrError(
    context: Context,
    host: MochibunWidgetHost,
    appWidgetId: Int,
    info: AppWidgetProviderInfo,
    errorTextArgb: Int
): android.view.View = try {
    // createView() also binds the view and applies the provider's latest RemoteViews
    host.createView(context, appWidgetId, info)
} catch (e: Exception) {
    android.util.Log.w("AppWidgetHostCard", "createView failed for id=$appWidgetId", e)
    TextView(context).apply {
        text = "♡ Couldn't load this widget ♡"
        gravity = Gravity.CENTER
        textSize = 12f
        setTextColor(errorTextArgb)
    }
}

@Composable
private fun UnavailableWidgetContent(slot: WidgetSlot, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val appLabel = remember(slot.provider) { providerAppLabel(context, slot.provider) }
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "♡ Widget unavailable ♡",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = theme.palette.deepAccent
        )
        if (appLabel != null) {
            Text(
                text = appLabel,
                fontSize = 12.sp,
                color = theme.palette.textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "Its app is missing or updating. Press OK to remove it.",
            fontSize = 11.sp,
            color = theme.palette.textColor.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}

private fun providerAppLabel(context: Context, provider: String?): String? {
    val component = provider?.let { ComponentName.unflattenFromString(it) } ?: return null
    return try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(component.packageName, 0)).toString()
    } catch (_: Exception) {
        component.packageName
    }
}
