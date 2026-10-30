package com.crinklecodone.mochibun.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.ui.dialogs.PixelRibbonBow
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.M3x6FontFamily

/** Frame styles for [PhotoFrameCard]; [id] is what PreferencesManager stores. */
enum class PhotoFrameStyle(val id: String, val displayName: String) {
    INSTAX("INSTAX", "Instax"),
    PURIKURA("PURIKURA", "Purikura"),
    PEARL_LACE("PEARL_LACE", "Pearl Lace");

    fun next(): PhotoFrameStyle = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String?): PhotoFrameStyle = entries.firstOrNull { it.id == id } ?: INSTAX
    }
}

private val FrameCream = Color(0xFFFFFDF8)
private val LabelInk = Color(0xFF4A3E4E)
private val PurikuraPink = Color(0xFFFFF0F6)
private val WashiMint = Color(0xD9E2F0D9)
private val WashiLavender = Color(0xD9F3E5F5)
private val PlaceholderFill = Color(0xFFFBF3FA)
private val PlaceholderDash = Color(0xFFD9B8D9)

private val FrameWidth = 240.dp

/**
 * Widget-page photo frame. The page owns D-pad focus and decoding: [photo] is already
 * downsampled (see loadDownsampledPhotoBitmap), null shows the "add photo" placeholder.
 * Decorations that poke past the frame (bow, washi tape, stickers) sit outside the clip.
 */
@Composable
fun PhotoFrameCard(
    photo: ImageBitmap?,
    frameStyle: String,
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.graphicsLayer { clip = false },
        contentAlignment = Alignment.Center
    ) {
        when (PhotoFrameStyle.fromId(frameStyle)) {
            PhotoFrameStyle.INSTAX -> InstaxFrame(photo, label, isFocused, onClick)
            PhotoFrameStyle.PURIKURA -> PurikuraFrame(photo, label, isFocused, onClick)
            PhotoFrameStyle.PEARL_LACE -> PearlLaceFrame(photo, label, isFocused, onClick)
        }
    }
}

@Composable
private fun InstaxFrame(photo: ImageBitmap?, label: String, isFocused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(FrameWidth)
            .padding(top = 10.dp) // room for the bow above the frame
            .graphicsLayer { clip = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .softGlowFocus(isSelected = isFocused, cornerRadius = 16.dp)
                .clip(shape)
                .background(FrameCream)
                .border(1.2.dp, ActiveTheme.cardBorder, shape)
                .clickable(onClick = onClick)
                .padding(start = 8.dp, top = 8.dp, end = 8.dp)
        ) {
            PhotoArea(photo)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                contentAlignment = Alignment.Center
            ) {
                FrameLabel(label)
            }
        }
        PixelRibbonBow(
            width = 24.dp,
            height = 20.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-10).dp)
        )
    }
}

@Composable
private fun PurikuraFrame(photo: ImageBitmap?, label: String, isFocused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(FrameWidth)
            .padding(top = 8.dp) // room for the washi tape above the frame
            .graphicsLayer { clip = false }
            // Washi tape drawn over the frame's top edge, after the content so it overlaps the photo
            .drawWithContent {
                drawContent()
                val tapeW = 64.dp.toPx()
                val tapeH = 16.dp.toPx()
                val left = (size.width - tapeW) / 2f
                val top = -tapeH / 2f
                rotate(degrees = -6f, pivot = Offset(size.width / 2f, 0f)) {
                    drawRect(WashiMint, Offset(left, top), Size(tapeW, tapeH))
                    val stripe = 3.dp.toPx()
                    drawRect(WashiLavender, Offset(left, top + stripe), Size(tapeW, stripe))
                    drawRect(WashiLavender, Offset(left, top + tapeH - stripe * 2f), Size(tapeW, stripe))
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .softGlowFocus(isSelected = isFocused, cornerRadius = 16.dp)
                .clip(shape)
                .background(PurikuraPink)
                .border(1.2.dp, ActiveTheme.cardBorder, shape)
                .clickable(onClick = onClick)
                .padding(start = 10.dp, top = 12.dp, end = 10.dp)
        ) {
            PhotoArea(photo)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                FrameLabel(label)
            }
        }
        HeartSticker(
            size = 14.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .graphicsLayer { clip = false }
        )
        SparkleSticker(
            size = 14.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-4).dp, y = 4.dp)
                .graphicsLayer { clip = false }
        )
        FlowerSticker(
            size = 14.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .graphicsLayer { clip = false }
        )
    }
}

@Composable
private fun PearlLaceFrame(photo: ImageBitmap?, label: String, isFocused: Boolean, onClick: () -> Unit) {
    KeitaiLaceContainer(
        cardCornerRadius = 14.dp,
        scallopDepth = 4.dp,
        beadDiameter = 5.dp,
        bowWidth = 20.dp,
        bowHeight = 17.dp,
        modifier = Modifier
            .width(FrameWidth)
            .softGlowFocus(isSelected = isFocused, cornerRadius = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PhotoArea(photo)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                FrameLabel(label)
            }
        }
    }
}

@Composable
private fun FrameLabel(label: String) {
    Text(
        text = label,
        fontFamily = M3x6FontFamily,
        fontSize = 16.sp,
        color = LabelInk,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
}

/** Square photo window: the picture cropped to fill, or a dashed "add photo" placeholder. */
@Composable
private fun PhotoArea(photo: ImageBitmap?) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = "Framed photo",
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlaceholderFill)
                    .drawBehind {
                        val stroke = 1.5.dp.toPx()
                        val inset = stroke / 2f
                        drawRoundRect(
                            color = PlaceholderDash,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "( ˘ ³˘)♥",
                        fontSize = 20.sp,
                        color = LabelInk
                    )
                    Text(
                        text = "[ Click to Add Photo ♡ ]",
                        fontFamily = M3x6FontFamily,
                        fontSize = 16.sp,
                        color = LabelInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
