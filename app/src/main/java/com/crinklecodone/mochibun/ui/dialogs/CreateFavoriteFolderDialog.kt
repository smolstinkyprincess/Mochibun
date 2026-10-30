package com.crinklecodone.mochibun.ui.dialogs

import com.crinklecodone.mochibun.ui.components.KawaiiClosePill
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.ui.components.MochibunSectionHeader
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.destructiveColor
import com.crinklecodone.mochibun.ui.theme.selectedContainerColor
import com.crinklecodone.mochibun.ui.theme.softSurfaceColor

private val SUGGESTED_FOLDER_NAMES = listOf(
    "Cute Apps",
    "Games",
    "Daily",
    "Social",
    "Tools",
    "Mochi"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFavoriteFolderDialog(
    initialApp: LauncherApp?,
    availableApps: List<LauncherApp>,
    existingFolders: List<FavoriteFolder>,
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
            nameFieldFocusRequester.awaitFocusSafely(attempts = 6)
        } else {
            keyboardController?.hide()
            dialogFocusRequester.awaitFocusSafely(attempts = 6)
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
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                focusZone = 1
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                focusZone = 3
                                buttonCol = if (canCreate) 1 else 0
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
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
                    KeyEvent.KEYCODE_DPAD_UP -> {
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

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
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

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
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

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
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
        val shape = RoundedCornerShape(22.dp)

        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 500.dp)
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
                    title = "Make Folder",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
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
                    KawaiiClosePill(
                        label = "Cancel",
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
