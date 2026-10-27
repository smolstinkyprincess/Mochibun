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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.core.util.drawableToBitmap
import com.crinklecodone.mochibun.data.repository.IconPackInfo
import com.crinklecodone.mochibun.data.repository.IconPackManager
import com.crinklecodone.mochibun.ui.components.MochibunSectionHeader
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.selectedContainerColor
import com.crinklecodone.mochibun.ui.theme.softSurfaceColor
import kotlin.text.ifEmpty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPackSelectionDialog(
    context: Context,
    onDismiss: () -> Unit,
    onIconPackSelected: (String) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    var installedPacks by remember { mutableStateOf<List<IconPackInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPackPkg by remember {
        mutableStateOf(IconPackManager.getSelectedIconPack(context))
    }

    LaunchedEffect(Unit) {
        val packs = IconPackManager.queryInstalledIconPacks(context)
        installedPacks = packs
        isLoading = false
    }

    // Interactive items:
    // index in 0 until installedPacks.size -> icon pack item
    // index == installedPacks.size -> Close button
    val totalItems = (if (isLoading) 0 else installedPacks.size) + 1
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    LaunchedEffect(totalItems) {
        focusedIndex = focusedIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
    }

    LaunchedEffect(focusedIndex) {
        if (!isLoading && focusedIndex < installedPacks.size) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == focusedIndex }
            if (!isVisible) {
                listState.animateScrollToItem(focusedIndex)
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
                        if (focusedIndex < installedPacks.size) {
                            val pack = installedPacks[focusedIndex]
                            selectedPackPkg = pack.packageName
                            IconPackManager.setSelectedIconPack(context, pack.packageName)
                            onIconPackSelected(pack.packageName)
                        } else {
                            onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (focusedIndex > 0) {
                            focusedIndex--
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (focusedIndex < totalItems - 1) {
                            focusedIndex++
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
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
                .widthIn(max = 400.dp)
                .heightIn(max = 500.dp)
                .border(2.dp, ActiveTheme.cardBorder, shape),
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
                    title = "Icon Packs",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose an icon theme for Mochibun:",
                    color = theme.cardTextColor,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Finding icon packs... ♡",
                            color = theme.cardTextColor,
                            fontSize = 13.sp
                        )
                    }
                } else if (installedPacks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No icon packs found ♡",
                            color = theme.cardTextColor,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        itemsIndexed(
                            items = installedPacks,
                            key = { _, pack -> pack.packageName.ifEmpty { "__system_default__" } }
                        ) { index, pack ->
                            val isFocused = index == focusedIndex
                            val isSelected = pack.packageName == selectedPackPkg
                            val packShape = RoundedCornerShape(12.dp)
                            val packBitmap = remember(pack) {
                                drawableToBitmap(pack.icon, 72).asImageBitmap()
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { clip = false }
                                    .clip(packShape)
                                    .background(
                                        when {
                                            isFocused -> theme.selectedContainerColor
                                            isSelected -> softSurfaceColor.copy(alpha = 0.65f)
                                            else -> Color.Transparent
                                        },
                                        packShape
                                    )
                                    .border(
                                        width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
                                        color = when {
                                            isFocused -> theme.appActionColor
                                            isSelected -> theme.appActionColor.copy(alpha = 0.7f)
                                            else -> theme.greetingOutlineColor.copy(alpha = 0.4f)
                                        },
                                        shape = packShape
                                    )
                                    .clickable {
                                        focusedIndex = index
                                        selectedPackPkg = pack.packageName
                                        IconPackManager.setSelectedIconPack(context, pack.packageName)
                                        onIconPackSelected(pack.packageName)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = packBitmap,
                                        contentDescription = pack.label,
                                        filterQuality = FilterQuality.None,
                                        modifier = Modifier.size(36.dp)
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pack.label,
                                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isFocused || isSelected) theme.fgTextColor else theme.cardTextColor,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Text(
                                            text = if (pack.isDefault) "Default launcher icons" else pack.packageName,
                                            color = theme.cardTextColor.copy(alpha = 0.75f),
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(theme.appActionColor.copy(alpha = 0.18f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "♥ active",
                                                color = theme.appActionColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
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
                        text = "♥ Close ♥",
                        isFocused = focusedIndex == totalItems - 1,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}