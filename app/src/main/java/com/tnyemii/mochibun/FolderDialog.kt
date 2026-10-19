/**
 * Favorite Folder modal dialog and management subsystems for Mochibun.
 * Handles folder viewing, app picker checklists, inline renaming, and deletion
 * while enforcing strict D-pad focus isolation so background launcher keys don't leak.
 */
package com.tnyemii.mochibun

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.imageResource
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

private val SUGGESTED_FOLDER_NAMES = listOf(
    "Messages & Mail",
    "Cute Apps",
    "Social & Chat",
    "Daily",
    "Games",
    "Tools",
    "Mochi"
)

private data class FolderSquircleTint(val container: Color, val border: Color)

/**
 * Alternating soft pastel background tints for app squircle tiles matching the reference design:
 * 0: Soft Mint   (#D4F1E8) - e.g., Via Browser
 * 1: Blush Pink  (#FCE2E6) - e.g., ChatGPT
 * 2: Soft Lilac  (#E2D9F7) - e.g., Mew Notes
 * 3: Soft Mint   (#D4F1E8) - e.g., Files
 */
private val SQUIRCLE_TINTS = listOf(
    FolderSquircleTint(container = Color(0xFFD4F1E8), border = Color(0xFFA5E2CD)),
    FolderSquircleTint(container = Color(0xFFFCE2E6), border = Color(0xFFF6ACB9)),
    FolderSquircleTint(container = Color(0xFFE2D9F7), border = Color(0xFFC7B6EE)),
    FolderSquircleTint(container = Color(0xFFD4F1E8), border = Color(0xFFA5E2CD))
)

/**
 * Crisp pixel-art ribbon bow badge overlapping the top perimeter border of the folder card.
 * Uses the pixel-art ribbon bow asset from the home screen greeting card ([R.drawable.bow]).
 *
 * FilterQuality.None is strictly required here: on the AT-M120's 480x800 display, default bilinear
 * filtering causes muddy interpolation artifacts along 1-bit pixel art edges.
 */
@Composable
fun PixelRibbonBow(
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 46.dp
) {
    Image(
        bitmap = ImageBitmap.imageResource(id = R.drawable.bow),
        contentDescription = "Ribbon Bow",
        filterQuality = FilterQuality.None,
        modifier = modifier.size(width = width, height = height)
    )
}

/**
 * Scalloped/beaded pill button for "♥ Close (◄) ♥" overlapping the bottom card rim.
 * Features a continuous pearl bead chain drawn directly to hardware Canvas with 'graphicsLayer { clip = false }'.
 * Drawing procedural circles directly avoids texture memory overhead on the MT6761's limited RAM budget.
 */
@Composable
fun ScallopedClosePillButton(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonWidth = 154.dp
    val buttonHeight = 36.dp

    Box(
        modifier = modifier
            .size(width = buttonWidth + 24.dp, height = buttonHeight + 20.dp)
            .graphicsLayer { clip = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pillW = buttonWidth.toPx()
            val pillH = buttonHeight.toPx()
            val pillR = pillH / 2f
            val pillLeft = (size.width - pillW) / 2f
            val pillTop = (size.height - pillH) / 2f
            val beadRadius = 4.2.dp.toPx()
            val beadColor = Color(0xFFE2D4EB)
            val beadBorderColor = Color(0xFFBCA7CE)

            val straightLen = pillW - 2 * pillR
            val topStart = pillLeft + pillR
            val topEnd = pillLeft + pillW - pillR
            val beadCountStraight = 7
            val beadCountArc = 6

            // Top straight edge beads
            for (i in 0..beadCountStraight) {
                val bx = topStart + (straightLen * i / beadCountStraight)
                val by = pillTop
                drawCircle(color = beadColor, radius = beadRadius, center = Offset(bx, by))
                drawCircle(color = beadBorderColor, radius = beadRadius, center = Offset(bx, by), style = Stroke(width = 1.2.dp.toPx()))
            }

            // Right arc beads
            val rightCenter = Offset(topEnd, pillTop + pillR)
            for (i in 1..beadCountArc) {
                val angle = -Math.PI / 2 + (Math.PI * i / (beadCountArc + 1))
                val bx = (rightCenter.x + pillR * Math.cos(angle)).toFloat()
                val by = (rightCenter.y + pillR * Math.sin(angle)).toFloat()
                drawCircle(color = beadColor, radius = beadRadius, center = Offset(bx, by))
                drawCircle(color = beadBorderColor, radius = beadRadius, center = Offset(bx, by), style = Stroke(width = 1.2.dp.toPx()))
            }

            // Bottom straight edge beads
            val bottomY = pillTop + pillH
            for (i in 0..beadCountStraight) {
                val bx = topEnd - (straightLen * i / beadCountStraight)
                val by = bottomY
                drawCircle(color = beadColor, radius = beadRadius, center = Offset(bx, by))
                drawCircle(color = beadBorderColor, radius = beadRadius, center = Offset(bx, by), style = Stroke(width = 1.2.dp.toPx()))
            }

            // Left arc beads
            val leftCenter = Offset(topStart, pillTop + pillR)
            for (i in 1..beadCountArc) {
                val angle = Math.PI / 2 + (Math.PI * i / (beadCountArc + 1))
                val bx = (leftCenter.x + pillR * Math.cos(angle)).toFloat()
                val by = (leftCenter.y + pillR * Math.sin(angle)).toFloat()
                drawCircle(color = beadColor, radius = beadRadius, center = Offset(bx, by))
                drawCircle(color = beadBorderColor, radius = beadRadius, center = Offset(bx, by), style = Stroke(width = 1.2.dp.toPx()))
            }

            // Inner pill surface
            drawRoundRect(
                color = if (isFocused) Color(0xFFFFF0F4) else Color(0xFFFAF5FE),
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillR, pillR)
            )
            drawRoundRect(
                color = if (isFocused) Color(0xFFE06287) else Color(0xFFC7B3D6),
                topLeft = Offset(pillLeft, pillTop),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillR, pillR),
                style = Stroke(width = if (isFocused) 2.dp.toPx() else 1.2.dp.toPx())
            )
        }

        // Focused glow indicator wrapping the button
        if (isFocused) {
            Box(
                modifier = Modifier
                    .size(width = buttonWidth + 8.dp, height = buttonHeight + 8.dp)
                    .graphicsLayer { clip = false }
                    .glowingDoubleBorder(isSelected = true, shape = RoundedCornerShape(50), cornerRadius = 50.dp)
            )
        }

        // Text inside: ♥ Close (◄) ♥
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "♥ Close (◄) ♥",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) Color(0xFFE5607E) else Color(0xFF3B1B44)
            )
        }
    }
}

/**
 * Subtle floating pixel sparkle stars and hearts on the dialog backdrop.
 */
@Composable
fun FloatingPixelBackdrop(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val sparkles = listOf(
            Triple(0.08f, 0.28f, Color(0xFF4A2A56) to 10.dp),
            Triple(0.28f, 0.16f, Color(0xFF4A2A56) to 7.dp),
            Triple(0.34f, 0.18f, Color(0xFFFFA6B8) to 6.dp),
            Triple(0.64f, 0.15f, Color(0xFF4A2A56) to 6.dp),
            Triple(0.77f, 0.24f, Color(0xFF3B1B44) to 13.dp),
            Triple(0.88f, 0.32f, Color(0xFF4A2A56) to 7.dp),
            Triple(0.09f, 0.34f, Color(0xFF5A3066) to 6.dp),
            Triple(0.92f, 0.38f, Color(0xFFFFA6B8) to 6.dp),
            Triple(0.22f, 0.76f, Color(0xFF3B1B44) to 12.dp),
            Triple(0.27f, 0.79f, Color(0xFFFFA6B8) to 6.dp),
            Triple(0.18f, 0.85f, Color(0xFF5A3066) to 7.dp),
            Triple(0.75f, 0.79f, Color(0xFFFFA6B8) to 6.dp),
            Triple(0.81f, 0.78f, Color(0xFF3B1B44) to 6.dp),
            Triple(0.93f, 0.76f, Color(0xFF5A3066) to 7.dp)
        )

        for ((fx, fy, item) in sparkles) {
            val (col, sDp) = item
            val cx = w * fx
            val cy = h * fy
            val s = sDp.toPx()
            val path = Path().apply {
                moveTo(cx, cy - s / 2f)
                cubicTo(cx, cy, cx, cy, cx + s / 2f, cy)
                cubicTo(cx, cy, cx, cy, cx, cy + s / 2f)
                cubicTo(cx, cy, cx, cy, cx - s / 2f, cy)
                cubicTo(cx, cy, cx, cy, cx, cy - s / 2f)
                close()
            }
            drawPath(path, col)
        }
    }
}

/**
 * Primary Favorite Folder modal dialog.
 *
 * D-pad Focus Isolation Contract:
 * When this dialog is open, focus MUST be strictly trapped inside the modal container.
 * In Jetpack Compose, unconsumed physical keypad events bubble through the window hierarchy;
 * without isolating the dialog, D-pad events would simultaneously navigate favorite items
 * on the HomeScreen underneath. We capture keys at the dialog root using onPreviewKeyEvent
 * and route them across 3 distinct zones (Header, App Grid, Close Button).
 */
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
    val palette = theme.palette
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var editingName by remember(folder.id) { mutableStateOf(initialEditingName) }
    var newName by remember(folder.id, folder.name) { mutableStateOf(folder.name) }
    var managing by remember(folder.id) { mutableStateOf(false) }
    var pickingApps by remember(folder.id) { mutableStateOf(initialPickingApps) }
    var confirmingDelete by remember(folder.id) { mutableStateOf(false) }

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

    // Main Focus sections:
    // 0 = Header Title (rename / edit toggle)
    // 1 = App Grid (selectedAppIndex)
    // 2 = Bottom Scalloped Close button
    var focusSection by remember(folder.id) {
        mutableIntStateOf(if (folderApps.isNotEmpty()) 1 else 2)
    }
    var selectedAppIndex by remember(folder.id) { mutableIntStateOf(0) }

    // Rename sub-view focus: 0 = TextField, 1 = Suggest, 2 = Cancel, 3 = Save
    var renameFocusZone by remember { mutableIntStateOf(1) }
    var isEditingRenameField by remember { mutableStateOf(false) }
    val renameFieldFocusRequester = remember { FocusRequester() }

    // App picker checklist focus: 0 = List, 1 = Buttons (pickerButtonCol: 0 = Cancel, 1 = Save)
    var pickerZone by remember { mutableIntStateOf(0) }
    var pickerAppIndex by remember { mutableIntStateOf(0) }
    var pickerButtonCol by remember { mutableIntStateOf(1) }

    var deleteConfirmCol by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }
    val folderFocusRequester = remember { FocusRequester() }

    LaunchedEffect(folder.id, folderApps.size) {
        selectedAppIndex = selectedAppIndex.coerceIn(
            0,
            (folderApps.size - 1).coerceAtLeast(0)
        )
        if (folderApps.isEmpty() && focusSection == 1) {
            focusSection = 2
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
            .fillMaxSize()
            .focusRequester(folderFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent

                // Intercept KEYCODE_F4 (OEM side key) alongside standard back keys so the physical
                // shortcut button consistently returns the user to the launcher home state.
                if (isBackDismissKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_F4) {
                    if (event.type == KeyEventType.KeyUp) {
                        handleBack()
                    }
                    return@onPreviewKeyEvent true
                }

                // Rename sub-view key navigation
                if (editingName) {
                    if (renameFocusZone == 0 && isEditingRenameField) {
                        if (event.type == KeyEventType.KeyDown) {
                            when (native.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    isEditingRenameField = false
                                    renameFocusZone = 1
                                    return@onPreviewKeyEvent true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    isEditingRenameField = false
                                    renameFocusZone = 3
                                    return@onPreviewKeyEvent true
                                }
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
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
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                renameFocusZone = when (renameFocusZone) {
                                    3, 2 -> 1
                                    1 -> 0
                                    0 -> 3
                                    else -> 0
                                }
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                renameFocusZone = when (renameFocusZone) {
                                    0 -> 1
                                    1 -> if (newName.isNotBlank() && !duplicateRename) 3 else 2
                                    else -> 0
                                }
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (renameFocusZone == 3) renameFocusZone = 2
                                else if (renameFocusZone == 1) renameFocusZone = 0
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (renameFocusZone == 2) renameFocusZone = 3
                                else if (renameFocusZone == 0) renameFocusZone = 1
                                return@onPreviewKeyEvent true
                            }
                        }
                        return@onPreviewKeyEvent false
                    }
                }

                // Delete confirmation key navigation
                if (confirmingDelete) {
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
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            deleteConfirmCol = 0
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            deleteConfirmCol = 1
                            return@onPreviewKeyEvent true
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                // App picker checklist key navigation
                if (pickingApps) {
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            if (pickerZone == 0) {
                                availableApps.getOrNull(pickerAppIndex)?.let { app ->
                                    val pkg = app.packageName
                                    draftPackages = if (pkg in draftPackages) draftPackages - pkg else draftPackages + pkg
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
                        KeyEvent.KEYCODE_DPAD_UP -> {
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
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
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
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            pickerButtonCol = 0
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            pickerButtonCol = if (draftPackages.isNotEmpty()) 1 else 0
                            return@onPreviewKeyEvent true
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                // Main Folder View key navigation
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (sawCenterDown && focusSection == 1 && !managing) {
                            // Long-press Center on an app toggles manage mode
                            sawCenterDown = false
                            managing = true
                        }
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusSection) {
                            0 -> {
                                newName = folder.name
                                renameFocusZone = 0
                                editingName = true
                            }
                            1 -> {
                                folderApps.getOrNull(selectedAppIndex)?.let { app ->
                                    if (managing) {
                                        onRemoveApp(app.packageName)
                                    } else {
                                        onLaunchApp(app)
                                        onDismiss()
                                    }
                                }
                            }
                            2 -> {
                                onDismiss()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val totalApps = folderApps.size
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusSection) {
                            1 -> {
                                if (selectedAppIndex % 2 == 1) {
                                    selectedAppIndex--
                                }
                            }
                            2 -> {
                                // "♥ Close (◄) ♥": Left dismisses folder cleanly
                                onDismiss()
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (focusSection) {
                            1 -> {
                                if (selectedAppIndex % 2 == 0 && selectedAppIndex + 1 < totalApps) {
                                    selectedAppIndex++
                                }
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusSection) {
                            0 -> {
                                // Topmost
                            }
                            1 -> {
                                if (selectedAppIndex >= 2) {
                                    selectedAppIndex -= 2
                                } else {
                                    focusSection = 0
                                }
                            }
                            2 -> {
                                if (totalApps > 0) {
                                    focusSection = 1
                                    selectedAppIndex = (totalApps - 1).coerceAtLeast(0)
                                } else {
                                    focusSection = 0
                                }
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusSection) {
                            0 -> {
                                if (totalApps > 0) {
                                    focusSection = 1
                                    selectedAppIndex = 0
                                } else {
                                    focusSection = 2
                                }
                            }
                            1 -> {
                                if (selectedAppIndex + 2 < totalApps) {
                                    selectedAppIndex += 2
                                } else {
                                    focusSection = 2
                                }
                            }
                            2 -> {
                                // Bottommost
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val pickerListState = rememberLazyListState()
        val gridState = rememberLazyGridState()

        LaunchedEffect(selectedAppIndex, folderApps.size, focusSection) {
            if (focusSection == 1 && folderApps.isNotEmpty() && selectedAppIndex in folderApps.indices) {
                gridState.animateScrollToItem(selectedAppIndex)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { handleBack() },
            contentAlignment = Alignment.Center
        ) {
            // 1. Top Scalloped Lavender Clouds lining top screen boundary
            TopLavenderClouds(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            // 2. Bottom Scalloped Lavender Clouds lining bottom screen boundary
            BottomLavenderClouds(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )

            // 3. Centered Content Unit (Folder Card + Bottom Navigation Pill)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .graphicsLayer { clip = false }
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                // Main Folder Modal Card Container
                val cardCornerRadius = cardRoundness.coerceIn(22.dp, 24.dp)
                val cardShape = RoundedCornerShape(cardCornerRadius)
                val cardBg = theme.unfocusedCardBgColor
                val cardBorderColor = theme.unfocusedCardBorderColor

                Box(
                    modifier = Modifier
                        .width(308.dp)
                        .graphicsLayer { clip = false }
                        .background(cardBg, cardShape)
                        .border(1.5.dp, cardBorderColor, cardShape)
                        .padding(top = 22.dp, bottom = 22.dp, start = 14.dp, end = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PixelRibbonBow(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-23).dp)
                    )
                    when {
                        confirmingDelete -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🗑 Delete '${folder.name}'?",
                                    color = destructiveColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )

                                Text(
                                    text = "This folder will be removed from favorites. Apps inside remain in your app drawer ♡",
                                    color = Color(0xFF3B1B44),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
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
                                    .padding(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "— ♡ Apps in ${folder.name} ♡ —",
                                    color = Color(0xFF3B1B44),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(8.dp))

                                LazyColumn(
                                    state = pickerListState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp),
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
                                                    if (isRowFocused) theme.selectedContainerColor else Color.Transparent
                                                )
                                                .border(
                                                    width = if (isRowFocused) 2.dp else 0.dp,
                                                    color = if (isRowFocused) Color(0xFFE06287) else Color.Transparent,
                                                    shape = rowShape
                                                )
                                                .clickable {
                                                    pickerZone = 0
                                                    pickerAppIndex = index
                                                    draftPackages = if (checked) draftPackages - pkg else draftPackages + pkg
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Image(
                                                bitmap = app.imageBitmap,
                                                contentDescription = app.name,
                                                filterQuality = FilterQuality.None,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = app.name,
                                                color = Color(0xFF3B1B44),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (checked) Color(0xFFE06287) else Color.Transparent)
                                                    .border(1.5.dp, Color(0xFFE06287), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (checked) "✓" else "＋",
                                                    color = if (checked) Color.White else Color(0xFFE06287),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
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
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "— ♡ Rename Folder ♡ —",
                                    color = Color(0xFF3B1B44),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it.take(32) },
                                    label = { Text("Folder name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFE06287),
                                        unfocusedBorderColor = Color(0xFFDFB3C4),
                                        focusedTextColor = Color(0xFF3B1B44),
                                        unfocusedTextColor = Color(0xFF3B1B44),
                                        focusedContainerColor = Color(0xFFFFF6F8),
                                        unfocusedContainerColor = Color(0xFFFFF6F8)
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

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
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
                            // Main 2×2 Folder View
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Folder Header: — ♡ [Folder Title] ♡ —
                                val isHeaderFocused = focusSection == 0
                                val headerShape = RoundedCornerShape(12.dp)
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { clip = false }
                                        .softGlowFocus(
                                            isSelected = isHeaderFocused,
                                            cornerRadius = 12.dp
                                        )
                                        .background(
                                            if (isHeaderFocused) theme.focusedContainerColor else Color.Transparent,
                                            headerShape
                                        )
                                        .clickable {
                                            newName = folder.name
                                            renameFocusZone = 0
                                            editingName = true
                                        }
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "— ♡ ${folder.name.ifBlank { "Favorites" }} ♡ —",
                                        fontSize = 17.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.palette.textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (managing) {
                                    Text(
                                        text = "Edit mode: tap an app to remove ♡",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = destructiveColor,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                // Symmetrical 2-Column App Grid
                                if (folderApps.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(theme.unfocusedCardBgColor)
                                            .border(1.dp, theme.unfocusedCardBorderColor, RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Folder is empty ♡\nTap header to manage apps!",
                                            color = theme.palette.textColor,
                                            fontSize = 13.5.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        state = gridState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .graphicsLayer { clip = false },
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                    ) {
                                        itemsIndexed(folderApps, key = { _, app -> app.packageName }) { index, app ->
                                            val isTileFocused = focusSection == 1 && selectedAppIndex == index
                                            val tint = SQUIRCLE_TINTS[index % SQUIRCLE_TINTS.size]
                                            val tileShape = RoundedCornerShape(20.dp)
                                            val squircleShape = RoundedCornerShape(16.dp)

                                            val scale by animateFloatAsState(
                                                targetValue = if (isTileFocused) 1.04f else 1.0f,
                                                animationSpec = tween(120),
                                                label = "folderAppTileScale"
                                            )

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .graphicsLayer {
                                                        scaleX = scale
                                                        scaleY = scale
                                                        clip = false
                                                    }
                                                    .softGlowFocus(
                                                        isSelected = isTileFocused,
                                                        cornerRadius = 20.dp
                                                    )
                                                    .background(
                                                        if (isTileFocused) theme.focusedContainerColor else Color.Transparent,
                                                        tileShape
                                                    )
                                                    .combinedClickable(
                                                        onClick = {
                                                            focusSection = 1
                                                            selectedAppIndex = index
                                                            if (managing) {
                                                                onRemoveApp(app.packageName)
                                                            } else {
                                                                onLaunchApp(app)
                                                                onDismiss()
                                                            }
                                                        },
                                                        onLongClick = {
                                                            focusSection = 1
                                                            selectedAppIndex = index
                                                            managing = !managing
                                                        }
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                // Squircle Icon Container
                                                Box(
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(squircleShape)
                                                        .background(tint.container, squircleShape)
                                                        .border(
                                                            width = 1.dp,
                                                            color = tint.border,
                                                            shape = squircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Image(
                                                        bitmap = app.imageBitmap,
                                                        contentDescription = app.name,
                                                        filterQuality = FilterQuality.None,
                                                        modifier = Modifier.size(34.dp)
                                                    )

                                                    if (managing) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(3.dp)
                                                                .size(18.dp)
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(destructiveColor),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("✕", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }

                                                Spacer(Modifier.height(8.dp))

                                                // App Label
                                                Text(
                                                    text = app.name,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = theme.palette.textColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // Floating Bottom Home Navigation Pill: ^ ♥ Back to Home (F4) ♥
                DrawerHomeNavigation(
                    isFocused = focusSection == 2,
                    onClick = onDismiss
                )
            }
        }
    }
}