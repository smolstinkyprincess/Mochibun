package com.tnyemii.mochibun

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.tnyemii.mochibun.ui.theme.destructiveBgColor
import com.tnyemii.mochibun.ui.theme.destructiveColor
import com.tnyemii.mochibun.ui.theme.softSurfaceColor
import kotlinx.coroutines.delay

val dialogWidth = 268.dp

private val SUGGESTED_FOLDER_NAMES = listOf(
    "Cute Apps",
    "Games",
    "Daily",
    "Social",
    "Tools",
    "Mochi"
)

fun isCenterActivateKey(keyCode: Int): Boolean =
    keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
        keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A

fun isBackDismissKey(keyCode: Int): Boolean =
    keyCode == android.view.KeyEvent.KEYCODE_BACK ||
        keyCode == android.view.KeyEvent.KEYCODE_ESCAPE

@Composable
fun requestFocusSafely(focusRequester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        repeat(8) {
            try {
                focusRequester.requestFocus()
                return@LaunchedEffect
            } catch (_: Exception) {
                delay(35)
            }
        }
    }
}

@Composable
fun KawaiiDialogItem(
    text: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    onClick: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        !enabled -> Color.Transparent
        isFocused && isDestructive -> destructiveBgColor
        isFocused -> theme.selectedContainerColor
        else -> Color.Transparent
    }
    val borderColor = when {
        !enabled -> Color.Transparent
        isFocused && isDestructive -> destructiveColor
        isFocused -> theme.appActionColor
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> theme.fgTextColor.copy(alpha = 0.38f)
        isDestructive -> destructiveColor
        isFocused -> theme.fgTextColor
        else -> theme.appActionColor
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(
                width = if (isFocused && enabled) 2.dp else 0.dp,
                color = borderColor,
                shape = shape
            )
            .then(
                if (enabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = when (textAlign) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun KawaiiDialogPillButton(
    text: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        !enabled -> softSurfaceColor.copy(alpha = 0.45f)
        isDestructive && isFocused -> destructiveColor
        isDestructive -> destructiveBgColor
        isPrimary && isFocused -> theme.appActionColor
        isPrimary -> theme.appActionColor.copy(alpha = 0.85f)
        isFocused -> theme.selectedContainerColor
        else -> Color.Transparent
    }
    val borderColor = when {
        !enabled -> theme.greetingOutlineColor.copy(alpha = 0.3f)
        isDestructive -> destructiveColor
        isFocused -> theme.fgTextColor
        isPrimary -> theme.appActionColor
        else -> theme.greetingOutlineColor.copy(alpha = 0.6f)
    }
    val contentColor = when {
        !enabled -> theme.fgTextColor.copy(alpha = 0.38f)
        isDestructive && isFocused -> Color.White
        isDestructive -> destructiveColor
        isPrimary -> Color.White
        isFocused -> theme.fgTextColor
        else -> theme.appActionColor
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .then(
                if (enabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// launcher menu dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherMenuDialog(
    onDismiss: () -> Unit,
    onHiddenAppsClick: () -> Unit,
    onThemeStudioClick: () -> Unit,
    onPickWallpaperClick: () -> Unit,
    onResetWallpaperClick: () -> Unit,
    showResetWallpaper: Boolean,
    onCreateFolderClick: (() -> Unit)? = null
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    var sawCenterDown by remember { mutableStateOf(false) }

    val actions = remember(showResetWallpaper, onCreateFolderClick) {
        buildList<Pair<String, () -> Unit>> {
            if (onCreateFolderClick != null) {
                add("📁 Make folder" to onCreateFolderClick)
            }
            add("🙈 Hidden apps" to onHiddenAppsClick)
            add("🎨 Theme Studio" to onThemeStudioClick)
            add("🖼 Pick wallpaper from gallery" to onPickWallpaperClick)
            if (showResetWallpaper) {
                add("↩ Reset to default wallpaper" to onResetWallpaperClick)
            }
            add("Close" to onDismiss)
        }
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(actions.size) {
        selectedIndex = selectedIndex.coerceIn(0, (actions.size - 1).coerceAtLeast(0))
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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        selectedIndex = if (selectedIndex > 0) {
                            selectedIndex - 1
                        } else {
                            actions.lastIndex.coerceAtLeast(0)
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        selectedIndex = if (selectedIndex < actions.lastIndex) {
                            selectedIndex + 1
                        } else {
                            0
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 440.dp)
                .border(2.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "♡ launcher options ♡",
                    fontWeight = FontWeight.Bold,
                    color = theme.fgTextColor,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        top = 2.dp,
                        bottom = 6.dp
                    )
                )

                HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                Spacer(modifier = Modifier.height(2.dp))

                actions.forEachIndexed { idx, (label, action) ->
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

// dialog listing hidden apps so they can be unhidden again <3
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
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(hiddenApps.size)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 420.dp)
                .border(2.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = "♡ hidden apps ♡",
                    fontWeight = FontWeight.Bold,
                    color = theme.fgTextColor,
                    fontSize = 18.sp
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
                    KawaiiDialogPillButton(
                        text = "Close",
                        isFocused = selectedIndex == hiddenApps.size,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

// long-press dialog for app actions (favorite reorder, custom icon, app info, hide, folder creation) <3
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionDialog(
    app: LauncherApp,
    favoritePackages: List<String>,
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
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
            add("Close" to onDismiss)
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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isFav && selectedIndex == 1) {
                            selectedIndex = 0
                        } else if (selectedIndex > 0) {
                            selectedIndex--
                        } else {
                            selectedIndex = actions.lastIndex
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isFav && selectedIndex == 0) {
                            selectedIndex = 1
                        } else if (selectedIndex < actions.lastIndex) {
                            selectedIndex++
                        } else {
                            selectedIndex = 0
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
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
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
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
        val shape = RoundedCornerShape(cardRoundness)

        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 460.dp)
                .border(2.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
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

// D-pad accessible folder options menu (reorder, rename, edit apps, change/reset icon, delete with confirmation) <3
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderActionDialog(
    folder: FavoriteFolder,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onOpenFolder: () -> Unit,
    onRenameFolder: () -> Unit,
    onEditFolderApps: () -> Unit,
    onChangeIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onDeleteFolder: () -> Unit,
    onOpenLauncherOptions: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var sawCenterDown by remember { mutableStateOf(false) }
    var confirmingDelete by remember(folder.id) { mutableStateOf(false) }
    var confirmDeleteIndex by remember { mutableIntStateOf(0) } // 0 = Cancel, 1 = Delete

    data class FolderMenuAction(
        val label: String,
        val enabled: Boolean = true,
        val isDestructive: Boolean = false,
        val onClick: () -> Unit
    )

    val actions = remember(folder.id, canMoveLeft, canMoveRight, onOpenLauncherOptions) {
        buildList {
            add(FolderMenuAction("◀ Move Left", enabled = canMoveLeft, onClick = onMoveLeft))
            add(FolderMenuAction("Move Right ▶", enabled = canMoveRight, onClick = onMoveRight))
            add(FolderMenuAction("📂 Open folder", onClick = onOpenFolder))
            add(FolderMenuAction("✎ Rename folder", onClick = onRenameFolder))
            add(FolderMenuAction("＋ Add / remove apps", onClick = onEditFolderApps))
            add(FolderMenuAction("🎨 Change folder icon", onClick = onChangeIcon))
            add(FolderMenuAction("↺ Reset folder icon", onClick = onResetIcon))
            add(
                FolderMenuAction(
                    "🗑 Delete folder",
                    isDestructive = true,
                    onClick = {
                        confirmDeleteIndex = 0
                        confirmingDelete = true
                    }
                )
            )
            if (onOpenLauncherOptions != null) {
                add(FolderMenuAction("⚙ Launcher options", onClick = onOpenLauncherOptions))
            }
            add(FolderMenuAction("Close", onClick = onDismiss))
        }
    }

    var selectedIndex by remember { mutableIntStateOf(2) }

    LaunchedEffect(selectedIndex) {
        val total = actions.size.coerceAtLeast(1)
        val targetScroll = ((selectedIndex.toFloat() / total) * scrollState.maxValue).toInt()
        scrollState.animateScrollTo(targetScroll)
    }

    requestFocusSafely(focusRequester, confirmingDelete)

    BackHandler {
        if (confirmingDelete) {
            confirmingDelete = false
        } else {
            onDismiss()
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (confirmingDelete) {
                confirmingDelete = false
            } else {
                onDismiss()
            }
        },
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
                        if (confirmingDelete) {
                            confirmingDelete = false
                        } else {
                            onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        if (confirmingDelete) {
                            if (confirmDeleteIndex == 1) {
                                onDeleteFolder()
                            } else {
                                confirmingDelete = false
                            }
                        } else {
                            val item = actions.getOrNull(selectedIndex)
                            if (item != null && item.enabled) {
                                item.onClick()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (confirmingDelete) {
                    when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            confirmDeleteIndex = 0
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            confirmDeleteIndex = 1
                            true
                        }
                        else -> false
                    }
                } else {
                    when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (selectedIndex == 1) {
                                selectedIndex = 0
                            } else if (selectedIndex > 0) {
                                selectedIndex--
                            } else {
                                selectedIndex = actions.lastIndex
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (selectedIndex == 0) {
                                selectedIndex = 1
                            } else if (selectedIndex < actions.lastIndex) {
                                selectedIndex++
                            } else {
                                selectedIndex = 0
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (selectedIndex == 0 || selectedIndex == 1) {
                                selectedIndex = actions.lastIndex
                            } else if (selectedIndex == 2) {
                                selectedIndex = if (canMoveLeft) 0 else if (canMoveRight) 1 else 0
                            } else {
                                selectedIndex--
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (selectedIndex == 0 || selectedIndex == 1) {
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
            }
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 460.dp)
                .border(2.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            if (confirmingDelete) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🗑 Delete '${folder.name}'?",
                        fontWeight = FontWeight.Bold,
                        color = destructiveColor,
                        fontSize = 17.sp
                    )

                    Text(
                        text = "This removes the folder from favorites. None of your installed apps will be uninstalled ♡",
                        color = theme.fgTextColor,
                        fontSize = 13.sp
                    )

                    HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        KawaiiDialogPillButton(
                            text = "Cancel",
                            isFocused = confirmDeleteIndex == 0,
                            onClick = { confirmingDelete = false }
                        )
                        KawaiiDialogPillButton(
                            text = "Delete folder",
                            isFocused = confirmDeleteIndex == 1,
                            isDestructive = true,
                            onClick = onDeleteFolder
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "📁 ${folder.name}",
                        color = theme.fgTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${folder.appPackages.size} app(s) inside ♡",
                        color = theme.cardTextColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KawaiiDialogPillButton(
                            text = "◀ Left",
                            isFocused = selectedIndex == 0,
                            enabled = canMoveLeft,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 0
                                if (canMoveLeft) onMoveLeft()
                            }
                        )
                        KawaiiDialogPillButton(
                            text = "Right ▶",
                            isFocused = selectedIndex == 1,
                            enabled = canMoveRight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 1
                                if (canMoveRight) onMoveRight()
                            }
                        )
                    }

                    for (idx in 2 until actions.size) {
                        val item = actions[idx]
                        val isClose = idx == actions.lastIndex
                        if (isClose) {
                            Spacer(modifier = Modifier.height(2.dp))
                            HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                        }
                        KawaiiDialogItem(
                            text = item.label,
                            isFocused = selectedIndex == idx,
                            enabled = item.enabled,
                            isDestructive = item.isDestructive,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isClose) TextAlign.End else TextAlign.Start,
                            onClick = {
                                selectedIndex = idx
                                if (item.enabled) item.onClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteFolderDialog(
    folder: FavoriteFolder,
    apps: List<LauncherApp>,
    availableApps: List<LauncherApp> = apps,
    existingFolders: List<FavoriteFolder> = emptyList(),
    initialEditingName: Boolean = false,
    initialPickingApps: Boolean = false,
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
    cardRoundness: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onRemoveApp: (String) -> Unit,
    onUpdateApps: (List<String>) -> Unit = {},
    onRename: (String) -> Unit,
    onDeleteFolder: () -> Unit,
    onOpenFolderOptions: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var editingName by remember(folder.id) { mutableStateOf(initialEditingName) }
    var newName by remember(folder.id, folder.name) { mutableStateOf(folder.name) }
    var managing by remember(folder.id) { mutableStateOf(false) }
    var pickingApps by remember(folder.id) { mutableStateOf(initialPickingApps) }
    var confirmingDelete by remember(folder.id) { mutableStateOf(false) }

    // Draft set when adding/removing apps in checklist mode
    var draftPackages by remember(folder.id, folder.appPackages, pickingApps) {
        mutableStateOf(folder.appPackages.toSet())
    }

    val folderApps = remember(folder.appPackages, apps) {
        folder.appPackages.mapNotNull { pkg ->
            apps.firstOrNull { it.packageName == pkg }
        }
    }

    val duplicateRename = remember(newName, folder.id, existingFolders) {
        val trimmed = newName.trim()
        trimmed.isNotEmpty() && existingFolders.any {
            it.id != folder.id && it.name.equals(trimmed, ignoreCase = true)
        }
    }

    // Focus states for Main Folder View:
    // focusSection:
    // 0 = Header (col 0 = Rename title, col 1 = Edit/Manage toggle)
    // 1 = Folder Apps list (selectedFolderAppIndex)
    // 2 = Manage controls (col 0 = Add/Remove apps, col 1 = Rename, col 2 = Icon/Order, col 3 = Delete folder)
    // 3 = Footer (col 0 = + Apps shortcut, col 1 = Close)
    var focusSection by remember(folder.id) {
        mutableIntStateOf(if (folderApps.isNotEmpty()) 1 else 3)
    }
    var headerCol by remember(folder.id) { mutableIntStateOf(1) }
    var selectedFolderAppIndex by remember(folder.id) { mutableIntStateOf(0) }
    var manageActionIndex by remember(folder.id) { mutableIntStateOf(0) }
    var footerCol by remember(folder.id) { mutableIntStateOf(if (folderApps.isEmpty()) 0 else 1) }

    // Focus states for Rename sub-view:
    // 0 = OutlinedTextField, 1 = Suggest name button, 2 = Cancel button, 3 = Save name button
    var renameFocusZone by remember { mutableIntStateOf(1) }
    var isEditingRenameField by remember { mutableStateOf(false) }
    val renameFieldFocusRequester = remember { FocusRequester() }

    // Focus states for App Picker sub-view:
    // pickerZone: 0 = App list (pickerAppIndex), 1 = Cancel / Save buttons (pickerButtonCol)
    var pickerZone by remember { mutableIntStateOf(0) }
    var pickerAppIndex by remember { mutableIntStateOf(0) }
    var pickerButtonCol by remember { mutableIntStateOf(1) } // 0 = Cancel, 1 = Save

    // Focus states for Delete Confirmation sub-view:
    // 0 = Cancel (safe default), 1 = Delete
    var deleteConfirmCol by remember { mutableIntStateOf(0) }

    var sawCenterDown by remember { mutableStateOf(false) }
    val folderFocusRequester = remember { FocusRequester() }

    val manageActionsCount = if (onOpenFolderOptions != null) 4 else 3

    LaunchedEffect(folder.id, folderApps.size) {
        selectedFolderAppIndex = selectedFolderAppIndex.coerceIn(
            0,
            (folderApps.size - 1).coerceAtLeast(0)
        )
        if (folderApps.isEmpty() && focusSection == 1) {
            focusSection = if (managing) 2 else 3
        }
    }

    LaunchedEffect(availableApps.size) {
        pickerAppIndex = pickerAppIndex.coerceIn(0, (availableApps.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(editingName, renameFocusZone, isEditingRenameField, pickingApps, confirmingDelete) {
        if (editingName && renameFocusZone == 0 && isEditingRenameField) {
            repeat(6) {
                try {
                    renameFieldFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            delay(16)
            repeat(6) {
                try {
                    folderFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        }
    }

    val handleBack: () -> Unit = {
        when {
            confirmingDelete -> confirmingDelete = false
            pickingApps -> {
                draftPackages = folder.appPackages.toSet()
                pickingApps = false
            }
            editingName && isEditingRenameField -> {
                isEditingRenameField = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
            editingName -> {
                newName = folder.name
                isEditingRenameField = false
                editingName = false
                keyboardController?.hide()
            }
            managing -> {
                managing = false
                if (focusSection == 2) {
                    focusSection = if (folderApps.isNotEmpty()) 1 else 3
                }
            }
            else -> onDismiss()
        }
    }

    BackHandler(onBack = handleBack)

    BasicAlertDialog(
        onDismissRequest = handleBack,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(folderFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent

                // 1. Rename mode key handling
                if (editingName) {
                    if (isBackDismissKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyUp) {
                            if (isEditingRenameField) {
                                isEditingRenameField = false
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                            } else {
                                newName = folder.name
                                editingName = false
                                keyboardController?.hide()
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (renameFocusZone == 0 && isEditingRenameField) {
                        // Actively editing OutlinedTextField: allow normal typing; intercept UP/DOWN/ENTER to exit field
                        if (event.type == KeyEventType.KeyDown) {
                            when (native.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    isEditingRenameField = false
                                    renameFocusZone = 1
                                    return@onPreviewKeyEvent true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    isEditingRenameField = false
                                    renameFocusZone = 3
                                    return@onPreviewKeyEvent true
                                }
                                android.view.KeyEvent.KEYCODE_ENTER,
                                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    isEditingRenameField = false
                                    renameFocusZone = if (newName.isNotBlank() && !duplicateRename) 3 else 1
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                        return@onPreviewKeyEvent false
                    } else {
                        if (isCenterActivateKey(native.keyCode)) {
                            if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                                sawCenterDown = true
                            } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                                sawCenterDown = false
                                when (renameFocusZone) {
                                    0 -> isEditingRenameField = true
                                    1 -> {
                                        val nextSuggestion = SUGGESTED_FOLDER_NAMES.firstOrNull { candidate ->
                                            !candidate.equals(newName.trim(), ignoreCase = true) &&
                                                existingFolders.none {
                                                    it.id != folder.id && it.name.equals(candidate, ignoreCase = true)
                                                }
                                        } ?: SUGGESTED_FOLDER_NAMES.first()
                                        newName = nextSuggestion
                                    }
                                    2 -> {
                                        newName = folder.name
                                        isEditingRenameField = false
                                        editingName = false
                                    }
                                    3 -> {
                                        val trimmed = newName.trim()
                                        if (trimmed.isNotEmpty() && !duplicateRename) {
                                            onRename(trimmed)
                                            isEditingRenameField = false
                                            editingName = false
                                        }
                                    }
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (renameFocusZone == 0) {
                            if (native.keyCode == android.view.KeyEvent.KEYCODE_DEL) {
                                if (newName.isNotEmpty()) newName = newName.dropLast(1)
                                return@onPreviewKeyEvent true
                            }
                            val ch = native.unicodeChar
                            if (ch >= 32 && newName.length < 32) {
                                newName += ch.toChar()
                                return@onPreviewKeyEvent true
                            }
                        }
                        when (native.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                renameFocusZone = when (renameFocusZone) {
                                    3, 2 -> 1
                                    1 -> 0
                                    0 -> 3
                                    else -> 0
                                }
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                renameFocusZone = when (renameFocusZone) {
                                    0 -> 1
                                    1 -> if (newName.isNotBlank() && !duplicateRename) 3 else 2
                                    else -> 0
                                }
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (renameFocusZone == 3) renameFocusZone = 2
                                else if (renameFocusZone == 1) renameFocusZone = 0
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (renameFocusZone == 2) renameFocusZone = 3
                                else if (renameFocusZone == 0) renameFocusZone = 1
                                return@onPreviewKeyEvent true
                            }
                        }
                        return@onPreviewKeyEvent false
                    }
                }

                // 2. Delete confirmation mode key handling
                if (confirmingDelete) {
                    if (isBackDismissKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyUp) {
                            confirmingDelete = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            if (deleteConfirmCol == 1) {
                                onDeleteFolder()
                            } else {
                                confirmingDelete = false
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            deleteConfirmCol = 0
                            return@onPreviewKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            deleteConfirmCol = 1
                            return@onPreviewKeyEvent true
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                // 3. App Picker checklist mode key handling
                if (pickingApps) {
                    if (isBackDismissKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyUp) {
                            draftPackages = folder.appPackages.toSet()
                            pickingApps = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            if (pickerZone == 0) {
                                availableApps.getOrNull(pickerAppIndex)?.let { app ->
                                    val pkg = app.packageName
                                    draftPackages = if (pkg in draftPackages) {
                                        draftPackages - pkg
                                    } else {
                                        draftPackages + pkg
                                    }
                                }
                            } else {
                                if (pickerButtonCol == 0) {
                                    draftPackages = folder.appPackages.toSet()
                                    pickingApps = false
                                } else if (draftPackages.isNotEmpty()) {
                                    onUpdateApps(draftPackages.toList())
                                    pickingApps = false
                                }
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (pickerZone == 1) {
                                pickerZone = 0
                            } else if (pickerAppIndex > 0) {
                                pickerAppIndex--
                            } else {
                                pickerZone = 1
                                pickerButtonCol = if (draftPackages.isNotEmpty()) 1 else 0
                            }
                            return@onPreviewKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (pickerZone == 0) {
                                if (pickerAppIndex < availableApps.lastIndex) {
                                    pickerAppIndex++
                                } else {
                                    pickerZone = 1
                                    pickerButtonCol = if (draftPackages.isNotEmpty()) 1 else 0
                                }
                            } else {
                                pickerZone = 0
                                pickerAppIndex = 0
                            }
                            return@onPreviewKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (pickerZone == 0) {
                                pickerZone = 1
                                pickerButtonCol = 0
                            } else {
                                pickerButtonCol = 0
                            }
                            return@onPreviewKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (pickerZone == 0) {
                                pickerZone = 1
                                pickerButtonCol = if (draftPackages.isNotEmpty()) 1 else 0
                            } else {
                                pickerButtonCol = 1
                            }
                            return@onPreviewKeyEvent true
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                // 4. Main Folder View key handling
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        handleBack()
                    }
                    return@onPreviewKeyEvent true
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (sawCenterDown && focusSection == 1 && !managing) {
                            // Long-press on an app in the folder opens manage mode!
                            sawCenterDown = false
                            managing = true
                        }
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusSection) {
                            0 -> {
                                if (headerCol == 0) {
                                    newName = folder.name
                                    renameFocusZone = 0
                                    editingName = true
                                } else {
                                    managing = !managing
                                }
                            }
                            1 -> {
                                folderApps.getOrNull(selectedFolderAppIndex)?.let { app ->
                                    if (managing) {
                                        onRemoveApp(app.packageName)
                                    } else {
                                        onLaunchApp(app)
                                    }
                                }
                            }
                            2 -> {
                                when (manageActionIndex) {
                                    0 -> {
                                        draftPackages = folder.appPackages.toSet()
                                        pickerZone = 0
                                        pickingApps = true
                                    }
                                    1 -> {
                                        newName = folder.name
                                        renameFocusZone = 0
                                        editingName = true
                                    }
                                    2 -> {
                                        if (onOpenFolderOptions != null) {
                                            onOpenFolderOptions()
                                        } else {
                                            deleteConfirmCol = 0
                                            confirmingDelete = true
                                        }
                                    }
                                    3 -> {
                                        deleteConfirmCol = 0
                                        confirmingDelete = true
                                    }
                                }
                            }
                            3 -> {
                                if (footerCol == 0) {
                                    draftPackages = folder.appPackages.toSet()
                                    pickerZone = 0
                                    pickingApps = true
                                } else {
                                    onDismiss()
                                }
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusSection) {
                            0 -> {
                                focusSection = 3
                            }
                            1 -> {
                                if (selectedFolderAppIndex > 0) {
                                    selectedFolderAppIndex--
                                } else {
                                    focusSection = 0
                                }
                            }
                            2 -> {
                                if (manageActionIndex > 0) {
                                    manageActionIndex--
                                } else if (folderApps.isNotEmpty()) {
                                    focusSection = 1
                                    selectedFolderAppIndex = folderApps.lastIndex
                                } else {
                                    focusSection = 0
                                }
                            }
                            3 -> {
                                if (managing) {
                                    focusSection = 2
                                    manageActionIndex = manageActionsCount - 1
                                } else if (folderApps.isNotEmpty()) {
                                    focusSection = 1
                                    selectedFolderAppIndex = folderApps.lastIndex
                                } else {
                                    focusSection = 0
                                }
                            }
                        }
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusSection) {
                            0 -> {
                                if (folderApps.isNotEmpty()) {
                                    focusSection = 1
                                    selectedFolderAppIndex = 0
                                } else if (managing) {
                                    focusSection = 2
                                    manageActionIndex = 0
                                } else {
                                    focusSection = 3
                                }
                            }
                            1 -> {
                                if (selectedFolderAppIndex < folderApps.lastIndex) {
                                    selectedFolderAppIndex++
                                } else if (managing) {
                                    focusSection = 2
                                    manageActionIndex = 0
                                } else {
                                    focusSection = 3
                                }
                            }
                            2 -> {
                                if (manageActionIndex < manageActionsCount - 1) {
                                    manageActionIndex++
                                } else {
                                    focusSection = 3
                                }
                            }
                            3 -> {
                                focusSection = 0
                            }
                        }
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusSection) {
                            0 -> headerCol = 0
                            1 -> {
                                if (selectedFolderAppIndex > 0) {
                                    selectedFolderAppIndex--
                                } else {
                                    focusSection = 0
                                    headerCol = 0
                                }
                            }
                            3 -> footerCol = 0
                        }
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (focusSection) {
                            0 -> headerCol = 1
                            1 -> {
                                if (selectedFolderAppIndex < folderApps.lastIndex) {
                                    selectedFolderAppIndex++
                                } else {
                                    focusSection = 3
                                    footerCol = 1
                                }
                            }
                            3 -> footerCol = 1
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        val folderListState = rememberLazyListState()
        val pickerListState = rememberLazyListState()

        LaunchedEffect(selectedFolderAppIndex, folderApps.size, focusSection) {
            if (focusSection == 1 && folderApps.isNotEmpty() && selectedFolderAppIndex in folderApps.indices) {
                val isVisible = folderListState.layoutInfo.visibleItemsInfo.any {
                    it.index == selectedFolderAppIndex
                }
                if (!isVisible) {
                    folderListState.animateScrollToItem(selectedFolderAppIndex)
                }
            }
        }

        LaunchedEffect(pickerAppIndex, availableApps.size, pickingApps) {
            if (pickingApps && availableApps.isNotEmpty() && pickerAppIndex in availableApps.indices) {
                val isVisible = pickerListState.layoutInfo.visibleItemsInfo.any {
                    it.index == pickerAppIndex
                }
                if (!isVisible) {
                    pickerListState.animateScrollToItem(pickerAppIndex)
                }
            }
        }

        val shape = RoundedCornerShape(cardRoundness)

        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 500.dp)
                .border(2.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            when {
                confirmingDelete -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🗑 Delete '${folder.name}'?",
                            color = destructiveColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        Text(
                            text = "This folder will be removed from favorites. Apps inside remain in your app drawer ♡",
                            color = theme.fgTextColor,
                            fontSize = 13.sp
                        )

                        HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            KawaiiDialogPillButton(
                                text = "Cancel",
                                isFocused = deleteConfirmCol == 0,
                                onClick = { confirmingDelete = false }
                            )
                            KawaiiDialogPillButton(
                                text = "Delete",
                                isFocused = deleteConfirmCol == 1,
                                isDestructive = true,
                                onClick = onDeleteFolder
                            )
                        }
                    }
                }

                pickingApps -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "♡ apps in ${folder.name} ♡",
                            color = theme.fgTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "OK to check/uncheck • ▶ to jump to Save",
                            color = theme.cardTextColor,
                            fontSize = 11.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        LazyColumn(
                            state = pickerListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                availableApps,
                                key = { _, app -> app.componentKey }
                            ) { index, app ->
                                val pkg = app.packageName
                                val checked = pkg in draftPackages
                                val isRowFocused = pickerZone == 0 && pickerAppIndex == index
                                val rowShape = RoundedCornerShape(12.dp)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(rowShape)
                                        .background(
                                            when {
                                                isRowFocused -> theme.selectedContainerColor
                                                checked -> softSurfaceColor.copy(alpha = 0.55f)
                                                else -> Color.Transparent
                                            },
                                            rowShape
                                        )
                                        .border(
                                            width = if (isRowFocused) 2.dp else if (checked) 1.dp else 0.dp,
                                            color = when {
                                                isRowFocused -> theme.appActionColor
                                                checked -> theme.greetingOutlineColor
                                                else -> Color.Transparent
                                            },
                                            shape = rowShape
                                        )
                                        .clickable {
                                            pickerZone = 0
                                            pickerAppIndex = index
                                            draftPackages = if (checked) {
                                                draftPackages - pkg
                                            } else {
                                                draftPackages + pkg
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = app.imageBitmap,
                                        contentDescription = app.name,
                                        modifier = Modifier.size(32.dp)
                                    )

                                    Spacer(Modifier.width(10.dp))

                                    Text(
                                        text = app.name,
                                        color = if (isRowFocused || checked) theme.fgTextColor else theme.cardTextColor,
                                        fontWeight = if (isRowFocused || checked) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(Modifier.width(6.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (checked) theme.appActionColor else Color.Transparent
                                            )
                                            .border(
                                                1.5.dp,
                                                theme.appActionColor,
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (checked) "✓" else "＋",
                                            color = if (checked) Color.White else theme.appActionColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KawaiiDialogPillButton(
                                text = "Cancel",
                                isFocused = pickerZone == 1 && pickerButtonCol == 0,
                                onClick = {
                                    draftPackages = folder.appPackages.toSet()
                                    pickingApps = false
                                }
                            )
                            KawaiiDialogPillButton(
                                text = "Save (${draftPackages.size}) ♡",
                                isFocused = pickerZone == 1 && pickerButtonCol == 1,
                                enabled = draftPackages.isNotEmpty(),
                                isPrimary = true,
                                onClick = {
                                    if (draftPackages.isNotEmpty()) {
                                        onUpdateApps(draftPackages.toList())
                                        pickingApps = false
                                    }
                                }
                            )
                        }
                    }
                }

                editingName -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✎ Rename folder ♡",
                            color = theme.fgTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(32) },
                            label = { Text("Folder name") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.appActionColor,
                                unfocusedBorderColor = theme.greetingOutlineColor,
                                focusedTextColor = theme.fgTextColor,
                                unfocusedTextColor = theme.fgTextColor,
                                focusedContainerColor = softSurfaceColor,
                                unfocusedContainerColor = softSurfaceColor.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(renameFieldFocusRequester)
                        )

                        if (duplicateRename) {
                            Text(
                                text = "That folder name already exists ♡",
                                color = destructiveColor,
                                fontSize = 12.sp
                            )
                        }

                        KawaiiDialogPillButton(
                            text = "✨ Suggest cute name",
                            isFocused = renameFocusZone == 1,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                renameFocusZone = 1
                                val nextSuggestion = SUGGESTED_FOLDER_NAMES.firstOrNull { candidate ->
                                    !candidate.equals(newName.trim(), ignoreCase = true) &&
                                        existingFolders.none {
                                            it.id != folder.id && it.name.equals(candidate, ignoreCase = true)
                                        }
                                } ?: SUGGESTED_FOLDER_NAMES.first()
                                newName = nextSuggestion
                            }
                        )

                        HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            KawaiiDialogPillButton(
                                text = "Cancel",
                                isFocused = renameFocusZone == 2,
                                onClick = {
                                    newName = folder.name
                                    editingName = false
                                }
                            )
                            KawaiiDialogPillButton(
                                text = "Save name ♡",
                                isFocused = renameFocusZone == 3,
                                enabled = newName.isNotBlank() && !duplicateRename,
                                isPrimary = true,
                                onClick = {
                                    val trimmed = newName.trim()
                                    if (trimmed.isNotEmpty() && !duplicateRename) {
                                        onRename(trimmed)
                                        editingName = false
                                    }
                                }
                            )
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Folder header row (Rename title button + Edit/Done button)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val titleFocused = focusSection == 0 && headerCol == 0
                            val titleShape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(titleShape)
                                    .background(
                                        if (titleFocused) theme.selectedContainerColor else Color.Transparent,
                                        titleShape
                                    )
                                    .border(
                                        width = if (titleFocused) 2.dp else 0.dp,
                                        color = if (titleFocused) theme.appActionColor else Color.Transparent,
                                        shape = titleShape
                                    )
                                    .clickable {
                                        focusSection = 0
                                        headerCol = 0
                                        newName = folder.name
                                        renameFocusZone = 0
                                        editingName = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📁 ${folder.name}",
                                    color = theme.fgTextColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "✎",
                                    color = theme.appActionColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            KawaiiDialogPillButton(
                                text = if (managing) "Done" else "⚙ Edit",
                                isFocused = focusSection == 0 && headerCol == 1,
                                onClick = {
                                    focusSection = 0
                                    headerCol = 1
                                    managing = !managing
                                }
                            )
                        }

                        if (managing) {
                            Text(
                                text = "Edit mode: OK on an app removes it from this folder ♡",
                                color = theme.appActionColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Folder apps list
                        if (folderApps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(softSurfaceColor.copy(alpha = 0.55f))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "This folder is empty ♡ Add apps below!",
                                    color = theme.fgTextColor,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                state = folderListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = if (managing) 170.dp else 230.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(
                                    folderApps,
                                    key = { _, app -> app.packageName }
                                ) { index, app ->
                                    val pkg = app.packageName
                                    val isSelected = focusSection == 1 && index == selectedFolderAppIndex
                                    val scale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.03f else 1f,
                                        animationSpec = tween(120),
                                        label = "folderAppScale"
                                    )
                                    val rowShape = RoundedCornerShape(12.dp)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                            .background(
                                                color = if (isSelected) {
                                                    if (managing) destructiveBgColor else theme.selectedContainerColor
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = rowShape
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) {
                                                    if (managing) destructiveColor else theme.appActionColor
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = rowShape
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    focusSection = 1
                                                    selectedFolderAppIndex = index
                                                    if (managing) {
                                                        onRemoveApp(pkg)
                                                    } else {
                                                        onLaunchApp(app)
                                                    }
                                                },
                                                onLongClick = {
                                                    focusSection = 1
                                                    selectedFolderAppIndex = index
                                                    onRemoveApp(pkg)
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Image(
                                            bitmap = app.imageBitmap,
                                            contentDescription = app.name,
                                            modifier = Modifier.size(36.dp)
                                        )

                                        Spacer(Modifier.width(10.dp))

                                        Text(
                                            text = app.name,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) theme.fgTextColor else theme.cardTextColor,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (managing) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "✕ Remove",
                                                color = destructiveColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else if (isSelected) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "♡",
                                                color = theme.appActionColor,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (managing) {
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                            Spacer(Modifier.height(4.dp))

                            KawaiiDialogItem(
                                text = "＋ Add / remove apps",
                                isFocused = focusSection == 2 && manageActionIndex == 0,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    focusSection = 2
                                    manageActionIndex = 0
                                    draftPackages = folder.appPackages.toSet()
                                    pickerZone = 0
                                    pickingApps = true
                                }
                            )

                            KawaiiDialogItem(
                                text = "✎ Rename folder",
                                isFocused = focusSection == 2 && manageActionIndex == 1,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    focusSection = 2
                                    manageActionIndex = 1
                                    newName = folder.name
                                    renameFocusZone = 0
                                    editingName = true
                                }
                            )

                            if (onOpenFolderOptions != null) {
                                KawaiiDialogItem(
                                    text = "🎨 Folder icon & order",
                                    isFocused = focusSection == 2 && manageActionIndex == 2,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        focusSection = 2
                                        manageActionIndex = 2
                                        onOpenFolderOptions()
                                    }
                                )
                            }

                            val deleteIdx = if (onOpenFolderOptions != null) 3 else 2
                            KawaiiDialogItem(
                                text = "🗑 Delete folder",
                                isFocused = focusSection == 2 && manageActionIndex == deleteIdx,
                                isDestructive = true,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    focusSection = 2
                                    manageActionIndex = deleteIdx
                                    deleteConfirmCol = 0
                                    confirmingDelete = true
                                }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                        Spacer(Modifier.height(6.dp))

                        // Footer bar: + Apps shortcut & Close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KawaiiDialogPillButton(
                                text = "＋ Apps",
                                isFocused = focusSection == 3 && footerCol == 0,
                                onClick = {
                                    focusSection = 3
                                    footerCol = 0
                                    draftPackages = folder.appPackages.toSet()
                                    pickerZone = 0
                                    pickingApps = true
                                }
                            )

                            KawaiiDialogPillButton(
                                text = "Close",
                                isFocused = focusSection == 3 && footerCol == 1,
                                onClick = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFavoriteFolderDialog(
    initialApp: LauncherApp?,
    availableApps: List<LauncherApp>,
    existingFolders: List<FavoriteFolder>,
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
    cardRoundness: Dp,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val initialPackage = initialApp?.packageName

    var folderName by remember { mutableStateOf("") }
    var selectedPackages by remember(initialPackage) {
        mutableStateOf(if (initialPackage != null) setOf(initialPackage) else emptySet())
    }

    val duplicateName = remember(folderName, existingFolders) {
        val trimmed = folderName.trim()
        trimmed.isNotEmpty() && existingFolders.any {
            it.name.equals(trimmed, ignoreCase = true)
        }
    }

    val canCreate = folderName.isNotBlank() && selectedPackages.isNotEmpty() && !duplicateName

    // Focus zones:
    // 0 = Folder name OutlinedTextField
    // 1 = "✨ Suggest name" quick-fill button
    // 2 = App selection list (selectedAppIndex)
    // 3 = Bottom buttons (buttonCol: 0 = Cancel, 1 = Create)
    var focusZone by remember { mutableIntStateOf(0) }
    var selectedAppIndex by remember {
        val idx = if (initialPackage != null) {
            availableApps.indexOfFirst { it.packageName == initialPackage }.coerceAtLeast(0)
        } else {
            0
        }
        mutableIntStateOf(idx)
    }
    var buttonCol by remember { mutableIntStateOf(1) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val dialogFocusRequester = remember { FocusRequester() }
    val nameFieldFocusRequester = remember { FocusRequester() }
    val appListState = rememberLazyListState()

    LaunchedEffect(availableApps.size) {
        selectedAppIndex = selectedAppIndex.coerceIn(0, (availableApps.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(selectedAppIndex, availableApps.size, focusZone) {
        if (focusZone == 2 && availableApps.isNotEmpty() && selectedAppIndex in availableApps.indices) {
            val isVisible = appListState.layoutInfo.visibleItemsInfo.any {
                it.index == selectedAppIndex
            }
            if (!isVisible) {
                appListState.animateScrollToItem(selectedAppIndex)
            }
        }
    }

    LaunchedEffect(focusZone) {
        if (focusZone == 0) {
            repeat(6) {
                try {
                    nameFieldFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        } else {
            keyboardController?.hide()
            repeat(6) {
                try {
                    dialogFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        }
    }

    val suggestNextFolderName: () -> Unit = {
        val next = SUGGESTED_FOLDER_NAMES.firstOrNull { candidate ->
            !candidate.equals(folderName.trim(), ignoreCase = true) &&
                existingFolders.none { it.name.equals(candidate, ignoreCase = true) }
        } ?: "Mochi ${existingFolders.size + 1}"
        folderName = next
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(dialogFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent

                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        keyboardController?.hide()
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                // When Folder Name field has focus, allow normal typing; only intercept UP/DOWN/ENTER
                if (focusZone == 0) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                focusZone = 1
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                focusZone = 3
                                buttonCol = if (canCreate) 1 else 0
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                focusZone = if (folderName.isBlank()) 1 else 2
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusZone) {
                            1 -> suggestNextFolderName()
                            2 -> {
                                availableApps.getOrNull(selectedAppIndex)?.let { app ->
                                    val pkg = app.packageName
                                    selectedPackages = if (pkg in selectedPackages) {
                                        selectedPackages - pkg
                                    } else {
                                        selectedPackages + pkg
                                    }
                                }
                            }
                            3 -> {
                                if (buttonCol == 0) {
                                    onDismiss()
                                } else if (canCreate) {
                                    onCreate(folderName.trim(), selectedPackages.toList())
                                } else if (folderName.isBlank()) {
                                    // Help user by jumping to name suggestion if they forgot to name it
                                    suggestNextFolderName()
                                }
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusZone) {
                            1 -> focusZone = 0
                            2 -> {
                                if (selectedAppIndex > 0) {
                                    selectedAppIndex--
                                } else {
                                    focusZone = 1
                                }
                            }
                            3 -> {
                                focusZone = if (availableApps.isNotEmpty()) 2 else 1
                            }
                        }
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusZone) {
                            1 -> {
                                focusZone = if (availableApps.isNotEmpty()) 2 else 3
                            }
                            2 -> {
                                if (selectedAppIndex < availableApps.lastIndex) {
                                    selectedAppIndex++
                                } else {
                                    focusZone = 3
                                    buttonCol = if (canCreate) 1 else 0
                                }
                            }
                            3 -> {
                                focusZone = 0
                            }
                        }
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusZone) {
                            1 -> focusZone = 0
                            2 -> {
                                focusZone = 3
                                buttonCol = 0
                            }
                            3 -> buttonCol = 0
                        }
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (focusZone) {
                            2 -> {
                                focusZone = 3
                                buttonCol = if (canCreate) 1 else 0
                            }
                            3 -> buttonCol = 1
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(cardRoundness)

        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 500.dp)
                .border(2.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = "♡ make a folder ♡",
                    color = theme.fgTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it.take(32) },
                    label = { Text("Folder name") },
                    placeholder = { Text("e.g. Cute Apps") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.appActionColor,
                        unfocusedBorderColor = theme.greetingOutlineColor,
                        focusedTextColor = theme.fgTextColor,
                        unfocusedTextColor = theme.fgTextColor,
                        focusedContainerColor = softSurfaceColor,
                        unfocusedContainerColor = softSurfaceColor.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFieldFocusRequester)
                )

                Spacer(Modifier.height(6.dp))

                KawaiiDialogPillButton(
                    text = "✨ Suggest cute name",
                    isFocused = focusZone == 1,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        focusZone = 1
                        suggestNextFolderName()
                    }
                )

                if (duplicateName) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "That folder name already exists ♡",
                        color = destructiveColor,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose apps (${selectedPackages.size} selected):",
                        color = theme.fgTextColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "▶ buttons",
                        color = theme.cardTextColor,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    state = appListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        availableApps,
                        key = { _, app -> app.componentKey }
                    ) { index, app ->
                        val pkg = app.packageName
                        val checked = pkg in selectedPackages
                        val isRowFocused = focusZone == 2 && selectedAppIndex == index
                        val rowShape = RoundedCornerShape(12.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(
                                    when {
                                        isRowFocused -> theme.selectedContainerColor
                                        checked -> softSurfaceColor.copy(alpha = 0.55f)
                                        else -> Color.Transparent
                                    },
                                    rowShape
                                )
                                .border(
                                    width = if (isRowFocused) 2.dp else if (checked) 1.dp else 0.dp,
                                    color = when {
                                        isRowFocused -> theme.appActionColor
                                        checked -> theme.greetingOutlineColor
                                        else -> Color.Transparent
                                    },
                                    shape = rowShape
                                )
                                .clickable {
                                    focusZone = 2
                                    selectedAppIndex = index
                                    selectedPackages = if (checked) {
                                        selectedPackages - pkg
                                    } else {
                                        selectedPackages + pkg
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.imageBitmap,
                                contentDescription = app.name,
                                modifier = Modifier.size(32.dp)
                            )

                            Spacer(Modifier.width(10.dp))

                            Text(
                                text = app.name,
                                color = if (isRowFocused || checked) theme.fgTextColor else theme.cardTextColor,
                                fontWeight = if (isRowFocused || checked) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.width(6.dp))

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (checked) theme.appActionColor else Color.Transparent
                                    )
                                    .border(
                                        1.5.dp,
                                        theme.appActionColor,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (checked) "✓" else "＋",
                                    color = if (checked) Color.White else theme.appActionColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                HorizontalDivider(
                    color = theme.appActionColor.copy(alpha = 0.22f)
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KawaiiDialogPillButton(
                        text = "Cancel",
                        isFocused = focusZone == 3 && buttonCol == 0,
                        onClick = onDismiss
                    )

                    KawaiiDialogPillButton(
                        text = "Create ♡",
                        isFocused = focusZone == 3 && buttonCol == 1,
                        enabled = canCreate,
                        isPrimary = true,
                        onClick = {
                            if (canCreate) {
                                onCreate(
                                    folderName.trim(),
                                    selectedPackages.toList()
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

// dialog search picker for manual Kawaii Icon Pack icon assignment <3
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KawaiiIconPicker(
    context: Context,
    iconNames: List<String>,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredIcons = remember(searchQuery, iconNames) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            iconNames.take(60)
        } else {
            iconNames
                .asSequence()
                .filter { it.contains(trimmed, ignoreCase = true) }
                .take(60)
                .toList()
        }
    }

    // Focus zones:
    // 0 = Search field
    // 1 = 4-column Icon Grid (selectedIconIndex)
    // 2 = Cancel button
    var focusZone by remember { mutableIntStateOf(1) }
    var selectedIconIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val dialogFocusRequester = remember { FocusRequester() }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(filteredIcons.size) {
        selectedIconIndex = selectedIconIndex.coerceIn(0, (filteredIcons.size - 1).coerceAtLeast(0))
        if (filteredIcons.isEmpty() && focusZone == 1) {
            focusZone = 0
        }
    }

    LaunchedEffect(selectedIconIndex, filteredIcons.size, focusZone) {
        if (focusZone == 1 && filteredIcons.isNotEmpty() && selectedIconIndex in filteredIcons.indices) {
            val isVisible = gridState.layoutInfo.visibleItemsInfo.any {
                it.index == selectedIconIndex
            }
            if (!isVisible) {
                gridState.animateScrollToItem(selectedIconIndex)
            }
        }
    }

    LaunchedEffect(focusZone) {
        if (focusZone == 0) {
            repeat(6) {
                try {
                    searchFieldFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        } else {
            keyboardController?.hide()
            repeat(6) {
                try {
                    dialogFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(dialogFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        keyboardController?.hide()
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                if (focusZone == 0) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                focusZone = if (filteredIcons.isNotEmpty()) 1 else 2
                                return@onPreviewKeyEvent true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                focusZone = 2
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusZone) {
                            1 -> filteredIcons.getOrNull(selectedIconIndex)?.let(onIconSelected)
                            2 -> onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val cols = 4
                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focusZone == 1 && selectedIconIndex > 0) {
                            selectedIconIndex--
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focusZone == 1 && selectedIconIndex < filteredIcons.lastIndex) {
                            selectedIconIndex++
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusZone) {
                            1 -> {
                                if (selectedIconIndex >= cols) {
                                    selectedIconIndex -= cols
                                } else {
                                    focusZone = 0
                                }
                            }
                            2 -> {
                                focusZone = if (filteredIcons.isNotEmpty()) 1 else 0
                            }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusZone) {
                            1 -> {
                                val nextIdx = selectedIconIndex + cols
                                val currentRow = selectedIconIndex / cols
                                val lastRow = (filteredIcons.size - 1) / cols
                                if (nextIdx <= filteredIcons.lastIndex) {
                                    selectedIconIndex = nextIdx
                                } else if (currentRow < lastRow) {
                                    selectedIconIndex = filteredIcons.lastIndex
                                } else {
                                    focusZone = 2
                                }
                            }
                            2 -> focusZone = 0
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .widthIn(max = 400.dp)
                .heightIn(max = 480.dp)
                .border(2.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = "♡ select kawaii icon ♡",
                    fontWeight = FontWeight.Bold,
                    color = theme.fgTextColor,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search icon name...")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFieldFocusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.appActionColor,
                        unfocusedBorderColor = theme.greetingOutlineColor,
                        focusedTextColor = theme.fgTextColor,
                        unfocusedTextColor = theme.fgTextColor,
                        focusedContainerColor = softSurfaceColor,
                        unfocusedContainerColor = softSurfaceColor.copy(alpha = 0.7f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredIcons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching icons found ♡",
                            color = theme.cardTextColor,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        itemsIndexed(
                            items = filteredIcons,
                            key = { _, name -> name }
                        ) { index, iconName ->
                            val isSelected = focusZone == 1 && index == selectedIconIndex
                            val iconBitmap = remember(iconName, context) {
                                getCachedKawaiiImageBitmap(context, iconName, 96)
                            }
                            val cellShape = RoundedCornerShape(12.dp)

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(cellShape)
                                    .background(
                                        if (isSelected) theme.selectedContainerColor else softSurfaceColor.copy(alpha = 0.45f),
                                        cellShape
                                    )
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) theme.appActionColor else theme.greetingOutlineColor.copy(alpha = 0.4f),
                                        shape = cellShape
                                    )
                                    .clickable {
                                        focusZone = 1
                                        selectedIconIndex = index
                                        onIconSelected(iconName)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = iconName,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    KawaiiDialogPillButton(
                        text = "Cancel",
                        isFocused = focusZone == 2,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}
