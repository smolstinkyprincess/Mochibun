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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.tnyemii.mochibun.ui.theme.appActionColor
import com.tnyemii.mochibun.ui.theme.cardBgColor
import com.tnyemii.mochibun.ui.theme.cardTextColor

val dialogWidth = 175.dp

@Composable
private fun LauncherMenuItem(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = 8.dp,
            vertical = 6.dp
        )
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
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
    showResetWallpaper: Boolean
) {
    val theme = LocalLauncherTheme.current
    val dialogWidth = 240.dp

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 420.dp),
            shape = RoundedCornerShape(theme.cardRoundnessDp.dp),
            color = theme.cardBgColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "♡ launcher options ♡",
                    fontWeight = FontWeight.Bold,
                    color = theme.appActionColor,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(
                        start = 8.dp,
                        top = 4.dp,
                        bottom = 8.dp
                    )
                )

                LauncherMenuItem(
                    text = "🙈 Hidden apps",
                    color = theme.appActionColor,
                    onClick = onHiddenAppsClick
                )

                LauncherMenuItem(
                    text = "🎨 Theme Studio",
                    color = theme.appActionColor,
                    onClick = onThemeStudioClick
                )

                LauncherMenuItem(
                    text = "🖼 Pick wallpaper from gallery",
                    color = theme.appActionColor,
                    onClick = onPickWallpaperClick
                )

                if (showResetWallpaper) {
                    LauncherMenuItem(
                        text = "↩ Reset to default wallpaper",
                        color = theme.appActionColor,
                        onClick = onResetWallpaperClick
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
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 400.dp),
            shape = RoundedCornerShape(cardRoundness),
            color = cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Title
                Text(
                    text = "♡ Hidden apps ♡",
                    fontWeight = FontWeight.Bold,
                    color = appActionColor,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(10.dp))

                if (hiddenApps.isEmpty()) {
                    Text(
                        text = "no hidden apps ♡",
                        color = cardTextColor,
                        fontSize = 14.sp
                    )
                } else {
                    // Scrollable hidden-app list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            hiddenApps,
                            key = { it.packageName }
                        ) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 4.dp,
                                        vertical = 4.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.imageBitmap,
                                    contentDescription = app.name,
                                    modifier = Modifier.size(32.dp)
                                )

                                Spacer(Modifier.width(8.dp))

                                Text(
                                    text = app.name,
                                    color = cardTextColor,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )

                                TextButton(
                                    onClick = {
                                        onUnhide(app)
                                    }
                                ) {
                                    Text(
                                        text = "Unhide",
                                        color = appActionColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                HorizontalDivider(
                    color = appActionColor.copy(alpha = 0.25f)
                )

                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(
                            text = "Close",
                            color = appActionColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// long-press dialog for app actions (favorite reorder, custom icon, app info, hide) <3
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
    onCreateFolder: () -> Unit
) {
    val pkgName = app.packageName
    val isFav = pkgName in favoritePackages

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        val theme = LocalLauncherTheme.current

        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 460.dp),
            shape = RoundedCornerShape(cardRoundness),
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // App title and icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = app.imageBitmap,
                        contentDescription = app.name,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = app.name,
                        color = theme.cardTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    color = appActionColor.copy(alpha = 0.25f)
                )

                // Reorder buttons if app is currently in favorites
                if (isFav) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onMoveFavoriteLeft
                        ) {
                            Text(
                                "◀ Left",
                                color = theme.appActionColor
                            )
                        }

                        TextButton(
                            onClick = onMoveFavoriteRight
                        ) {
                            Text(
                                "Right ▶",
                                color = theme.appActionColor
                            )
                        }
                    }
                }

                // Toggle favorite status
                TextButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isFav) {
                            "💔 Remove from favorites"
                        } else {
                            "💖 Add to favorites"
                        },
                        color = theme.appActionColor,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }

                // Custom icon picker
                TextButton(
                    onClick = onChangeIcon,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🎨 Change app icon",
                        color = theme.appActionColor,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }

                // Reset custom icon
                TextButton(
                    onClick = onResetIcon,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "↺ Reset icon to default",
                        color = theme.appActionColor,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }

                // System app details
                TextButton(
                    onClick = onAppInfo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ℹ️ App info",
                        color = theme.appActionColor,
                        fontSize = 14.sp
                    )
                }

                // Hide app from drawer
                TextButton(
                    onClick = onHideApp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🙈 Hide app",
                        color = theme.appActionColor,
                        fontSize = 14.sp
                    )
                }

                // Create folder
                TextButton(
                    onClick = onCreateFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📁 Make folder",
                        color = theme.appActionColor,
                        fontSize = 14.sp
                    )
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
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
    cardRoundness: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onRemoveApp: (String) -> Unit,
    onRename: (String) -> Unit,
    onDeleteFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    var editingName by remember { mutableStateOf(false) }
    var newName by remember(folder.id, folder.name) {
        mutableStateOf(folder.name)
    }
    var managing by remember { mutableStateOf(false) }

    var selectedFolderAppIndex by remember(folder.id) {
        mutableIntStateOf(0)
    }

    val folderApps = remember(folder.appPackages, apps) {
        folder.appPackages.mapNotNull { pkg ->
            apps.firstOrNull {
                it.packageName == pkg
            }
        }
    }

    val folderFocusRequester = remember { FocusRequester() }

    LaunchedEffect(folder.id, folderApps.size) {
        selectedFolderAppIndex = selectedFolderAppIndex.coerceIn(
            0,
            (folderApps.size - 1).coerceAtLeast(0)
        )
    }

    LaunchedEffect(folder.id) {
        repeat(10) {
            try {
                folderFocusRequester.requestFocus()
                return@LaunchedEffect
            } catch (_: Exception) {
                delay(50)
            }
        }
    }

    BackHandler(onBack = {
        if (editingName) {
            editingName = false
        } else if (managing) {
            managing = false
        } else {
            onDismiss()
        }
    })

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(folderFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyEvent = event.nativeKeyEvent

                if (editingName) {
                    if (event.type == KeyEventType.KeyDown &&
                        (keyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                                keyEvent.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE)
                    ) {
                        editingName = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                val isBackKey =
                    keyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                            keyEvent.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE

                if (isBackKey) {
                    if (event.type == KeyEventType.KeyUp) {
                        if (managing) {
                            managing = false
                        } else {
                            onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                val isCenterKey =
                    keyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                            keyEvent.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                            keyEvent.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A

                if (isCenterKey) {
                    if (event.type == KeyEventType.KeyUp) {
                        folderApps.getOrNull(selectedFolderAppIndex)?.let {
                            onLaunchApp(it)
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (keyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (folderApps.isNotEmpty()) {
                            selectedFolderAppIndex =
                                (selectedFolderAppIndex - 1)
                                    .coerceAtLeast(0)
                        }
                        true
                    }

                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (folderApps.isNotEmpty()) {
                            selectedFolderAppIndex =
                                (selectedFolderAppIndex + 1)
                                    .coerceAtMost(folderApps.lastIndex)
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        val theme = LocalLauncherTheme.current
        val folderListState = rememberLazyListState()

        LaunchedEffect(selectedFolderAppIndex, folderApps.size) {
            if (folderApps.isNotEmpty() && selectedFolderAppIndex in folderApps.indices) {
                val isVisible = folderListState.layoutInfo.visibleItemsInfo.any {
                    it.index == selectedFolderAppIndex
                }
                if (!isVisible) {
                    folderListState.animateScrollToItem(selectedFolderAppIndex)
                }
            }
        }

        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 340.dp),
            shape = RoundedCornerShape(cardRoundness),
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Folder title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📁 ${folder.name}",
                        color = theme.appActionColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { editingName = !editingName },
                        maxLines = 2
                    )

                    TextButton(
                        onClick = { managing = !managing }
                    ) {
                        Text(
                            text = if (managing) "Done" else "⚙",
                            color = theme.appActionColor,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Folder apps
                if (folderApps.isEmpty()) {
                    Text(
                        text = "This folder is empty ♡",
                        color = cardTextColor
                    )
                } else {
                    LazyColumn(
                        state = folderListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            folderApps,
                            key = { _, app -> app.packageName }
                        ) { index, app ->
                            val pkg = app.packageName

                            val isSelected = index == selectedFolderAppIndex

                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.04f else 1f,
                                animationSpec = tween(150),
                                label = "folderAppScale"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .background(
                                        color = if (isSelected) {
                                            theme.selectedContainerColor
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) {
                                            theme.appActionColor
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            selectedFolderAppIndex = index
                                            onLaunchApp(app)
                                        },
                                        onLongClick = {
                                            selectedFolderAppIndex = index
                                            onRemoveApp(pkg)
                                        }
                                    )
                                    .padding(
                                        horizontal = 8.dp,
                                        vertical = 4.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.imageBitmap,
                                    contentDescription = app.name,
                                    modifier = Modifier.size(40.dp)
                                )

                                Spacer(Modifier.width(10.dp))

                                Text(
                                    text = app.name,
                                    fontSize = 14.sp,
                                    color = theme.cardTextColor,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Rename controls, retained for when enabled
                if (editingName) {
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it.take(32)
                        },
                        label = {
                            Text("New folder name")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            onRename(newName.trim())
                            editingName = false
                        }
                    ) {
                        Text(
                            "Save name",
                            color = theme.appActionColor
                        )
                    }
                }

                if (managing) {
                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Long-press an app above to remove it.",
                        color = theme.cardTextColor,
                        fontSize = 12.sp
                    )

                    TextButton(
                        onClick = onDeleteFolder
                    ) {
                        Text(
                            "🗑 Delete folder",
                            color = theme.appActionColor
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(
                            text = "Close",
                            color = theme.appActionColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFavoriteFolderDialog(
    initialApp: LauncherApp,
    availableApps: List<LauncherApp>,
    existingFolders: List<FavoriteFolder>,
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
    cardRoundness: Dp,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    val initialPackage = initialApp.packageName

    var folderName by remember { mutableStateOf("") }
    var selectedPackages by remember {
        mutableStateOf(setOf(initialPackage))
    }

    val duplicateName = existingFolders.any {
        it.name.equals(folderName.trim(), ignoreCase = true)
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        val theme = LocalLauncherTheme.current

        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 480.dp),
            shape = RoundedCornerShape(cardRoundness),
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Title
                Text(
                    text = "♡ make a folder ♡",
                    color = theme.appActionColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(10.dp))

                // Folder name
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it.take(32) },
                    label = { Text("Folder name") },
                    placeholder = { Text("e.g. cute games") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (duplicateName && folderName.isNotBlank()) {
                    Text(
                        text = "That folder name already exists ♡",
                        color = theme.appActionColor,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Choose apps:",
                    color = theme.cardTextColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(4.dp))

                // Scrollable app list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        availableApps,
                        key = { it.componentKey }
                    ) { app ->
                        val pkg = app.packageName

                        val checked = pkg in selectedPackages

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPackages =
                                        if (checked) {
                                            if (pkg == initialPackage) {
                                                selectedPackages
                                            } else {
                                                selectedPackages - pkg
                                            }
                                        } else {
                                            selectedPackages + pkg
                                        }
                                }
                                .padding(
                                    horizontal = 4.dp,
                                    vertical = 5.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.imageBitmap,
                                contentDescription = app.name,
                                modifier = Modifier.size(32.dp)
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                text = app.name,
                                color = theme.cardTextColor,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )

                            Spacer(Modifier.width(4.dp))

                            Text(
                                text = if (checked) "✓" else "＋",
                                color = theme.appActionColor,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                if (existingFolders.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "You can customize folders later ♡",
                        color = theme.cardTextColor,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                HorizontalDivider(
                    color = appActionColor.copy(alpha = 0.25f)
                )

                // Dialog actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(
                            "Cancel",
                            color = theme.appActionColor
                        )
                    }

                    TextButton(
                        enabled = folderName.isNotBlank() &&
                                selectedPackages.isNotEmpty() &&
                                !duplicateName,
                        onClick = {
                            onCreate(
                                folderName.trim(),
                                selectedPackages.toList()
                            )
                        }
                    ) {
                        Text(
                            "Create ♡",
                            color = theme.appActionColor
                        )
                    }
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

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .heightIn(max = 480.dp),
            shape = RoundedCornerShape(cardRoundness),
            color = cardBgColor,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Dialog title
                Text(
                    text = "Select Kawaii Icon ♡",
                    fontWeight = FontWeight.Bold,
                    color = appActionColor,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search icon name...")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable icon grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredIcons,
                        key = { it }
                    ) { iconName ->
                        val iconBitmap = remember(iconName, context) {
                            getCachedKawaiiImageBitmap(context, iconName, 96)
                        }

                        if (iconBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onIconSelected(iconName)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = iconName,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
