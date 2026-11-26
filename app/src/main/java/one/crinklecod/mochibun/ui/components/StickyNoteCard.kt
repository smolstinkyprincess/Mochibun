package one.crinklecod.mochibun.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.crinklecod.mochibun.ui.theme.ActiveTheme

private val NoteCream = Color(0xFFFFF8E1)
private val NoteLineHeight = 20.sp
private val NotePadTop = 14.dp
private val NoteFold = 16.dp

/**
 * Sticky Note card for the Widgets canvas: a pastel memo sheet with faint ruled lines, a tinted
 * pad-binding strip along the top and a folded bottom-right corner. All of it is one cached
 * draw (paths built per size in drawWithCache, nothing per frame).
 *
 * The note fills its slot exactly (it isn't a grow-to-content card), so it resizes freely and long
 * notes ellipsize at the last ruled line that fits.
 */
@Composable
fun StickyNoteCard(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val paper = lerp(NoteCream, ActiveTheme.primary, 0.16f)
    val binding = lerp(NoteCream, ActiveTheme.primary, 0.55f)
    val ruleColor = ActiveTheme.cardBorder.copy(alpha = 0.45f)
    val marginColor = ActiveTheme.primary.copy(alpha = 0.55f)
    val foldColor = lerp(paper, ActiveTheme.accentDeep, 0.18f)
    val edgeColor = if (isFocused) ActiveTheme.palette.focusedCardBorder else ActiveTheme.cardBorder
    val textColor = ActiveTheme.textPrimary

    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 6.dp)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val r = 5.dp.toPx()
                val fold = NoteFold.toPx().coerceAtMost(minOf(w, h) / 3f)
                // Rounded sheet with the bottom-right corner cut off for the fold
                val sheet = Path().apply {
                    moveTo(r, 0f)
                    lineTo(w - r, 0f)
                    quadraticTo(w, 0f, w, r)
                    lineTo(w, h - fold)
                    lineTo(w - fold, h)
                    lineTo(r, h)
                    quadraticTo(0f, h, 0f, h - r)
                    lineTo(0f, r)
                    quadraticTo(0f, 0f, r, 0f)
                    close()
                }
                // The turned-up flap: a triangle tucked against the cut
                val flap = Path().apply {
                    moveTo(w, h - fold)
                    lineTo(w - fold, h - fold + fold * 0.12f)
                    lineTo(w - fold, h)
                    close()
                }
                val bindingH = 5.dp.toPx()
                val lineStep = NoteLineHeight.toPx()
                val firstLine = NotePadTop.toPx() + lineStep
                val inset = 8.dp.toPx()
                val marginX = 14.dp.toPx()
                val hairline = 1.dp.toPx()
                val edge = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round)
                onDrawBehind {
                    drawPath(sheet, paper)
                    clipPath(sheet) {
                        drawRect(binding, size = size.copy(height = bindingH))
                        var y = firstLine
                        while (y < h - 6.dp.toPx()) {
                            drawLine(ruleColor, Offset(inset, y), Offset(w - inset, y), strokeWidth = hairline)
                            y += lineStep
                        }
                        drawLine(marginColor, Offset(marginX, bindingH), Offset(marginX, h), strokeWidth = hairline)
                    }
                    drawPath(sheet, edgeColor, style = edge)
                    drawPath(flap, foldColor)
                    drawPath(flap, edgeColor, style = edge)
                }
            }
            .clickable(onClick = onClick)
    ) {
        val isEmpty = text.isBlank()
        Text(
            text = if (isEmpty) "♡ Press OK to jot a note ♡" else text,
            color = if (isEmpty) textColor.copy(alpha = 0.55f) else textColor,
            fontSize = 13.sp,
            lineHeight = NoteLineHeight,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                // Top padding puts each text line's baseline just above a ruled line
                .padding(start = 20.dp, end = NoteFold, top = NotePadTop + 2.dp, bottom = 6.dp)
        )
    }
}

