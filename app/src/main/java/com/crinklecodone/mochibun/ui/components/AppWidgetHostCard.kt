package com.crinklecodone.mochibun.ui.components

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.Gravity
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.crinklecodone.mochibun.core.widget.MochibunWidgetHost
import com.crinklecodone.mochibun.core.widget.WidgetFrame
import com.crinklecodone.mochibun.core.widget.WidgetLayoutMath
import com.crinklecodone.mochibun.data.repository.WidgetSlot
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.palette
import com.crinklecodone.mochibun.ui.theme.selectionGlowColor

private val WIDGET_MIN_HEIGHT = 64.dp
private val WIDGET_MAX_HEIGHT = 340.dp
private val UNAVAILABLE_CARD_HEIGHT = 96.dp

/**
 * A kawaii card hosting one third-party app widget.
 *
 * The card measures the width it gets, asks [WidgetLayoutMath] for a logical size + scale,
 * and hands that to a [WidgetFrame] wrapped in an AndroidView. If the provider is gone
 * ([info] == null) it shows a placeholder instead, so the user can remove it on purpose.
 */
@Composable
fun AppWidgetHostCard(
    slot: WidgetSlot,
    info: AppWidgetProviderInfo?,
    host: MochibunWidgetHost,
    isFocused: Boolean,
    isInteracting: Boolean,
    onFrameAttached: (appWidgetId: Int, frame: WidgetFrame) -> Unit,
    onFrameDetached: (appWidgetId: Int, frame: WidgetFrame) -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val cardShape = remember { RoundedCornerShape(22.dp) }
    val density = LocalDensity.current
    val highlightArgb = theme.selectionGlowColor.toArgb()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidthPx = constraints.maxWidth
        val spec = remember(info, availableWidthPx) {
            info?.let {
                with(density) {
                    WidgetLayoutMath.computeSize(
                        availableWidthPx = availableWidthPx,
                        minWidthPx = it.minWidth,
                        minHeightPx = it.minHeight,
                        minResizeWidthPx = it.minResizeWidth,
                        resizesHorizontally = (it.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0,
                        floorHeightPx = WIDGET_MIN_HEIGHT.roundToPx(),
                        maxDisplayHeightPx = WIDGET_MAX_HEIGHT.roundToPx()
                    )
                }
            }
        }
        val cardHeight = spec?.let { with(density) { it.displayHeightPx.toDp() } } ?: UNAVAILABLE_CARD_HEIGHT

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .softGlowFocus(isSelected = isFocused, cornerRadius = 22.dp)
                    .clip(cardShape)
                    .background(palette.container)
                    .border(
                        width = 1.5.dp,
                        color = if (isFocused) Color.Transparent else palette.outline,
                        shape = cardShape
                    )
            ) {
                if (info == null || spec == null) {
                    UnavailableWidgetContent(slot = slot, modifier = Modifier.align(Alignment.Center))
                } else {
                    key(slot.appWidgetId) {
                        AndroidView(
                            factory = { ctx ->
                                WidgetFrame(ctx).also { frame ->
                                    frame.setContent(createHostViewOrError(ctx, host, slot.appWidgetId, info))
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

                // Corner options handle for touch users (D-pad users hold OK instead)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF6F0FD))
                        .border(1.5.dp, Color(0xFFB5A4CB), RoundedCornerShape(10.dp))
                        .clickable(onClick = onOptionsClick)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⋯",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3E2452)
                    )
                }
            }

            // Hint sits under the card so it never covers the widget's own buttons
            if (isInteracting) {
                Text(
                    text = "✦ ◀▲▼▶ pick · OK tap · ↩ done ✦",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E2452),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xF2FFF0F4))
                        .border(1.dp, Color(0xFFE06287), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun createHostViewOrError(
    context: Context,
    host: MochibunWidgetHost,
    appWidgetId: Int,
    info: AppWidgetProviderInfo
): android.view.View = try {
    // createView() also binds the view and applies the provider's latest RemoteViews
    host.createView(context, appWidgetId, info)
} catch (e: Exception) {
    android.util.Log.w("AppWidgetHostCard", "createView failed for id=$appWidgetId", e)
    TextView(context).apply {
        text = "♡ Couldn't load this widget ♡"
        gravity = Gravity.CENTER
        textSize = 12f
        setTextColor(0xFF703350.toInt())
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
            text = "Its app is missing or updating. Hold OK to remove.",
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
