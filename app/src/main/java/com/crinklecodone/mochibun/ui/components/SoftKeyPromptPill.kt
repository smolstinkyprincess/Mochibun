package com.crinklecodone.mochibun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.palette

/**
 * Keitai Softkey Prompt Pill for physical function keys (LSK / RSK).
 * Features rounded squircle styling, subtle frosted backdrop, and optional status badge.
 */
@Composable
fun SoftKeyPromptPill(
    label: String,
    icon: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badgeCount: Int = 0,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val pillShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .alpha(if (enabled) 1f else 0.4f)
            .background(theme.greetingBgColor.copy(alpha = 0.85f), pillShape)
            .border(
                width = 1.dp,
                color = theme.greetingOutlineColor.copy(alpha = 0.7f),
                shape = pillShape
            )
            .clip(pillShape)
            .clickable(enabled = enabled) {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                onClick()
            }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = icon,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.appActionColor
            )
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(theme.palette.squirclePrimary, CircleShape)
                )
            }
        }
    }
}
