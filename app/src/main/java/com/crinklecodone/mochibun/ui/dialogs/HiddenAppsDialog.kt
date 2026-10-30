package com.crinklecodone.mochibun.ui.dialogs

import com.crinklecodone.mochibun.ui.components.KawaiiClosePill
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.ui.components.MochibunSectionHeader
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.selectedContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsDialog(
    hiddenApps: List<LauncherApp>,
    onUnhide: (LauncherApp) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var sawCenterDown by remember { mutableStateOf(false) }

    // 0 until hiddenApps.size -> app rows; hiddenApps.size -> Close button
    var selectedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(hiddenApps.size) {
        selectedIndex = selectedIndex.coerceIn(0, hiddenApps.size)
    }

    LaunchedEffect(selectedIndex, hiddenApps.size) {
        if (selectedIndex in hiddenApps.indices) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex }
            if (!isVisible) {
                listState.animateScrollToItem(selectedIndex)
            }
        }
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        if (selectedIndex in hiddenApps.indices) {
                            onUnhide(hiddenApps[selectedIndex])
                        } else {
                            onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(hiddenApps.size)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 420.dp)
                .border(1.5.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                MochibunSectionHeader(
                    title = "Hidden Apps",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                )

                Spacer(Modifier.height(10.dp))

                if (hiddenApps.isEmpty()) {
                    Text(
                        text = "no hidden apps ♡",
                        color = theme.cardTextColor,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            hiddenApps,
                            key = { _, app -> app.packageName }
                        ) { index, app ->
                            val isRowFocused = selectedIndex == index
                            val rowShape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(rowShape)
                                    .background(
                                        if (isRowFocused) theme.selectedContainerColor else Color.Transparent,
                                        rowShape
                                    )
                                    .border(
                                        width = if (isRowFocused) 2.dp else 0.dp,
                                        color = if (isRowFocused) theme.appActionColor else Color.Transparent,
                                        shape = rowShape
                                    )
                                    .clickable {
                                        selectedIndex = index
                                        onUnhide(app)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.imageBitmap,
                                    contentDescription = app.name,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                                    modifier = Modifier.size(32.dp)
                                )

                                Spacer(Modifier.width(10.dp))

                                Text(
                                    text = app.name,
                                    color = if (isRowFocused) theme.fgTextColor else theme.cardTextColor,
                                    fontWeight = if (isRowFocused) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(Modifier.width(6.dp))

                                Text(
                                    text = "Unhide",
                                    color = theme.appActionColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                HorizontalDivider(
                    color = theme.appActionColor.copy(alpha = 0.22f)
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    KawaiiClosePill(
                        isFocused = selectedIndex == hiddenApps.size,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}
