package com.crinklecodone.mochibun.ui.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.ui.components.curtainUnfurl
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme

// long-press dialog for app actions (favorite reorder, custom icon, app info, hide, folder creation)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionDialog(
    app: LauncherApp,
    favoritePackages: List<String>,
    cardRoundness: Dp,
    onDismiss: () -> Unit,
    onMoveFavoriteLeft: () -> Unit,
    onMoveFavoriteRight: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAppInfo: () -> Unit,
    onChangeIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onHideApp: () -> Unit,
    onCreateFolder: () -> Unit,
    onOpenLauncherOptions: (() -> Unit)? = null
) {
    val theme = LocalLauncherTheme.current
    val pkgName = app.packageName
    val isFav = pkgName in favoritePackages
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var sawCenterDown by remember { mutableStateOf(false) }

    val actions = remember(isFav, onOpenLauncherOptions) {
        buildList<Pair<String, () -> Unit>> {
            if (isFav) {
                add("◀ Move Left" to onMoveFavoriteLeft)
                add("Move Right ▶" to onMoveFavoriteRight)
            }
            add(
                (if (isFav) "💔 Remove from favorites" else "💖 Add to favorites") to onToggleFavorite
            )
            add("📁 Make folder" to onCreateFolder)
            add("🎨 Change app icon" to onChangeIcon)
            add("↺ Reset icon to default" to onResetIcon)
            add("ℹ️ App info" to onAppInfo)
            add("🙈 Hide app" to onHideApp)
            if (onOpenLauncherOptions != null) {
                add("⚙ Launcher options" to onOpenLauncherOptions)
            }
            add("♥ Close ♥" to onDismiss)
        }
    }

    var selectedIndex by remember { mutableIntStateOf(if (isFav) 2 else 0) }
    LaunchedEffect(actions.size) {
        selectedIndex = selectedIndex.coerceIn(0, (actions.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(selectedIndex) {
        val total = actions.size.coerceAtLeast(1)
        val targetScroll = ((selectedIndex.toFloat() / total) * scrollState.maxValue).toInt()
        scrollState.animateScrollTo(targetScroll)
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
                        actions.getOrNull(selectedIndex)?.second?.invoke()
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..actions.size) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        selectedIndex = targetIndex
                        actions.getOrNull(targetIndex)?.second?.invoke()
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isFav && selectedIndex == 1) {
                            selectedIndex = 0
                        } else if (selectedIndex > 0) {
                            selectedIndex--
                        } else {
                            selectedIndex = actions.lastIndex
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isFav && selectedIndex == 0) {
                            selectedIndex = 1
                        } else if (selectedIndex < actions.lastIndex) {
                            selectedIndex++
                        } else {
                            selectedIndex = 0
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isFav && (selectedIndex == 0 || selectedIndex == 1)) {
                            selectedIndex = actions.lastIndex
                        } else if (isFav && selectedIndex == 2) {
                            selectedIndex = 0
                        } else if (selectedIndex > 0) {
                            selectedIndex--
                        } else {
                            selectedIndex = actions.lastIndex
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isFav && (selectedIndex == 0 || selectedIndex == 1)) {
                            selectedIndex = 2
                        } else if (selectedIndex < actions.lastIndex) {
                            selectedIndex++
                        } else {
                            selectedIndex = 0
                        }
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
                .heightIn(max = 460.dp)
                .curtainUnfurl(visible = true)
                .border(1.5.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = app.imageBitmap,
                        contentDescription = app.name,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(38.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = app.name,
                        color = theme.fgTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    color = theme.appActionColor.copy(alpha = 0.22f)
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (isFav) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KawaiiDialogPillButton(
                            text = "◀ Left",
                            isFocused = selectedIndex == 0,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 0
                                onMoveFavoriteLeft()
                            }
                        )

                        KawaiiDialogPillButton(
                            text = "Right ▶",
                            isFocused = selectedIndex == 1,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 1
                                onMoveFavoriteRight()
                            }
                        )
                    }
                }

                val startIdx = if (isFav) 2 else 0
                for (idx in startIdx until actions.size) {
                    val (label, action) = actions[idx]
                    val isClose = idx == actions.lastIndex
                    if (isClose) {
                        Spacer(modifier = Modifier.height(2.dp))
                        HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                    }
                    KawaiiDialogItem(
                        text = label,
                        isFocused = selectedIndex == idx,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = if (isClose) TextAlign.End else TextAlign.Start,
                        jumpNumber = if (idx + 1 <= 9) idx + 1 else null,
                        onClick = {
                            selectedIndex = idx
                            action()
                        }
                    )
                }
            }
        }
    }
}
