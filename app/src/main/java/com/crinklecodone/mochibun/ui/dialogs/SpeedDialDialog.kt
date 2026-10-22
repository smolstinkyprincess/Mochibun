package com.crinklecodone.mochibun.ui.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.ui.components.MochibunSectionHeader
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.selectedContainerColor

/**
 * Japanese Keitai Speed Dial dialog (Keys 2-9).
 * Allows users to assign physical number pad shortcuts for one-handed launching from the Home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedDialDialog(
    apps: List<LauncherApp>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var sawCenterDown by remember { mutableStateOf(false) }

    // Speed dial state: digit 2..9
    var speedDials by remember {
        mutableStateOf(PreferencesManager.getAllSpeedDials(context))
    }

    var pickingForDigit by remember { mutableStateOf<Int?>(null) }

    // 0..7 -> digits 2..9, 8 -> Close
    var selectedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in 0..7) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex }
            if (!isVisible) {
                listState.animateScrollToItem(selectedIndex)
            }
        }
    }

    if (pickingForDigit != null) {
        SpeedDialAppPicker(
            digit = pickingForDigit!!,
            currentPackage = speedDials[pickingForDigit!!],
            apps = apps,
            onSelect = { pkg ->
                PreferencesManager.setSpeedDialPackage(context, pickingForDigit!!, pkg)
                speedDials = PreferencesManager.getAllSpeedDials(context)
                pickingForDigit = null
            },
            onDismiss = { pickingForDigit = null }
        )
        return
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (isBackDismissKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyUp) onDismiss()
                        return@onPreviewKeyEvent true
                    }
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            if (selectedIndex in 0..7) {
                                val digit = selectedIndex + 2
                                pickingForDigit = digit
                            } else {
                                onDismiss()
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (selectedIndex > 0) selectedIndex--
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (selectedIndex < 8) selectedIndex++
                                return@onPreviewKeyEvent true
                            }
                            // Direct keypress: pressing 2..9 immediately configures that digit
                            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { pickingForDigit = 2; return@onPreviewKeyEvent true }
                            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { pickingForDigit = 3; return@onPreviewKeyEvent true }
                            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { pickingForDigit = 4; return@onPreviewKeyEvent true }
                            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { pickingForDigit = 5; return@onPreviewKeyEvent true }
                            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { pickingForDigit = 6; return@onPreviewKeyEvent true }
                            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { pickingForDigit = 7; return@onPreviewKeyEvent true }
                            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { pickingForDigit = 8; return@onPreviewKeyEvent true }
                            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { pickingForDigit = 9; return@onPreviewKeyEvent true }
                        }
                    }
                    false
                },
            shape = shape,
            color = ActiveTheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ActiveTheme.cardBorder),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MochibunSectionHeader(title = "⌨️ Speed Dial (Keys 2-9)")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Hold numbers on Home screen to quick-launch apps ♡",
                    color = theme.cardTextColor.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = ActiveTheme.cardBorder.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    items(count = 8, key = { index -> index + 2 }) { index ->
                        val digit = index + 2
                        val pkg = speedDials[digit]
                        val app = apps.firstOrNull { it.packageName == pkg }
                        val isFocused = selectedIndex == index

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isFocused) theme.selectedContainerColor else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isFocused) ActiveTheme.selectionGlow else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { pickingForDigit = digit }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Digit Badge
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.45f))
                                    .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$digit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ActiveTheme.accentDeep
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            if (app != null) {
                                Image(
                                    bitmap = app.imageBitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = app.name,
                                    color = theme.cardTextColor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Text(
                                    text = "Unassigned (Press Enter to set)",
                                    color = theme.cardTextColor.copy(alpha = 0.5f),
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = ActiveTheme.cardBorder.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                val isCloseFocused = selectedIndex == 8
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCloseFocused) theme.selectedContainerColor else Color.Transparent)
                        .border(
                            1.dp,
                            if (isCloseFocused) ActiveTheme.selectionGlow else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onDismiss() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♡ Close ♡",
                        color = theme.appActionColor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Quick app selector for assigning a Speed Dial digit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedDialAppPicker(
    digit: Int,
    currentPackage: String?,
    apps: List<LauncherApp>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var sawCenterDown by remember { mutableStateOf(false) }

    // 0 -> Clear/Unassign, 1..apps.size -> apps
    var selectedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (isBackDismissKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyUp) onDismiss()
                        return@onPreviewKeyEvent true
                    }
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            if (selectedIndex == 0) {
                                onSelect(null)
                            } else {
                                apps.getOrNull(selectedIndex - 1)?.let { onSelect(it.packageName) }
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (selectedIndex > 0) selectedIndex--
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (selectedIndex < apps.size) selectedIndex++
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    false
                },
            shape = shape,
            color = ActiveTheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ActiveTheme.cardBorder),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MochibunSectionHeader(title = "Assign Key [$digit]")
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = ActiveTheme.cardBorder.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))

                val speedDialRowShape = RoundedCornerShape(10.dp)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    item(key = "__unassign__") {
                        val isFocused = selectedIndex == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(speedDialRowShape)
                                .background(if (isFocused) theme.selectedContainerColor else Color.Transparent)
                                .clickable { onSelect(null) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🚫 Unassign Shortcut",
                                color = Color(0xFFD32F2F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    itemsIndexed(
                        items = apps,
                        key = { _, app -> app.packageName }
                    ) { index, app ->
                        val isFocused = selectedIndex == index + 1
                        val isCurrent = app.packageName == currentPackage

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                                .clip(speedDialRowShape)
                                .background(if (isFocused) theme.selectedContainerColor else Color.Transparent)
                                .clickable { onSelect(app.packageName) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.imageBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = app.name,
                                color = theme.cardTextColor,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Text(
                                    text = "★ Active",
                                    color = ActiveTheme.accentDeep,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
