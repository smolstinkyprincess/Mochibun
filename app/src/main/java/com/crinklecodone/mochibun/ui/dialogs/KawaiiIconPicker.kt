package com.crinklecodone.mochibun.ui.dialogs

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.data.repository.IconPackManager
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.selectedContainerColor
import com.crinklecodone.mochibun.ui.theme.softSurfaceColor

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
            searchFieldFocusRequester.awaitFocusSafely(attempts = 6)
        } else {
            keyboardController?.hide()
            dialogFocusRequester.awaitFocusSafely(attempts = 6)
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
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                focusZone = if (filteredIcons.isNotEmpty()) 1 else 2
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
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
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focusZone == 1 && selectedIconIndex > 0) {
                            selectedIconIndex--
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focusZone == 1 && selectedIconIndex < filteredIcons.lastIndex) {
                            selectedIconIndex++
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
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
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
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
                                IconPackManager.getCachedImageBitmap(context, iconName, 96)
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
                                        filterQuality = FilterQuality.None,
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
