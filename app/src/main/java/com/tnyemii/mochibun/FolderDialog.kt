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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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

private val SUGGESTED_FOLDER_NAMES = listOf(
    "Messages & Mail",
    "Cute Apps",
    "Social & Chat",
    "Daily",
    "Games",
    "Tools",
    "Mochi"
)

/**
 * Alternating soft pastel background tints for app squircle tiles matching the reference design:
 * 0: Soft Lilac (#DDD5F6) - e.g., Discord
 * 1: Blush Pink (#FBD2CE) - e.g., Proton Mail
 * 2: Soft Mint   (#CEF0E2) - e.g., FairEmail
 * 3: Soft Peach  (#FDE0B6) - e.g., SMS
 */
private val APP_SLOT_TINTS = listOf(
    Color(0xFFDDD5F6),
    Color(0xFFFBD2CE),
    Color(0xFFCEF0E2),
    Color(0xFFFDE0B6)
)

/**
 * Crisp 24×16 pixel-art pastel pink ribbon bow overlapping the top perimeter border of the folder card.
 * Rendered with discrete 1:1 integer pixel Canvas rects to eliminate GPU overhead on MT6761.
 */
@Composable
fun PixelRibbonBow(
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 38.dp
) {
    Canvas(modifier = modifier.size(width = width, height = height)) {
        val pixelW = size.width / 24f
        val pixelH = size.height / 16f

        val rows = listOf(
            "....OOOO........OOOO....", // 0
            "..OOPHHPOO....OOPHHPOO..", // 1
            ".OPHHHHHHPO..OPHHHHHHPO.", // 2
            ".OPPPPPPPPO..OPPPPPPPPO.", // 3
            "OPPPSSPPPPO..OPPPPSSPPPO", // 4
            "OPSSSSSSSSOOOOISSSSSSSSO", // 5
            ".OPSSSSSPOOKKKOOPSSSSSPO.", // 6
            "..OOPPPOOKPHPKKOOPPPOO..", // 7
            "....OOPPOKPPPPOOPPOO....", // 8
            "....OPPOOOKKKOOOOPPO....", // 9
            "...OPPO...OO...OPPO.....", // 10
            "...OPPO........OPPO.....", // 11
            "..OPHPO........OPHPO....", // 12
            "..OPSPO........OPSPO....", // 13
            "..OP.PO........OP.PO....", // 14
            "..OO.OO........OO.OO...."  // 15
        )

        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, char ->
                val color = when (char) {
                    'O' -> Color(0xFF6E2B3E) // Dark berry outline
                    'P' -> Color(0xFFF9B4C4) // Soft pastel pink fill
                    'H' -> Color(0xFFFFF0F5) // Pale highlight
                    'S', 'I' -> Color(0xFFE5879F) // Pink shadow fold
                    'K' -> Color(0xFFB85970) // Knot shadow
                    else -> null
                }
                if (color != null) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x * pixelW, y * pixelH),
                        size = Size(pixelW + 0.5f, pixelH + 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Scalloped/beaded pill button for "♥ Close (◄) ♥" overlapping the bottom card rim.
 * Features a continuous pearl bead chain along the perimeter and glowing focus outline.
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
 * Refactored Folder modal dialog faithfully matching the reference mockup.
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
                .background(Color(0xFFF9D6DC).copy(alpha = 0.55f)) // Translucent peach-pink backdrop
                .clickable { handleBack() },
            contentAlignment = Alignment.Center
        ) {
            // Floating pixel sparkle stars & hearts
            FloatingPixelBackdrop()

            // Top and Bottom Lavender Clouds lining the screen boundaries
            TopLavenderClouds(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
            BottomLavenderClouds(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )

            // Main Folder Modal Card Container
            Box(
                modifier = Modifier
                    .width(308.dp)
                    .clickable(enabled = false) {}, // Intercept clicks inside card
                contentAlignment = Alignment.Center
            ) {
                // Frosted cream-lavender card body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFFFAF5FC))
                        .border(1.5.dp, Color(0xFFDFB3C4), RoundedCornerShape(26.dp))
                        .padding(top = 22.dp, bottom = 26.dp, start = 14.dp, end = 14.dp)
                ) {
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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isHeaderFocused) Color(0xFFFFF0F4) else Color.Transparent)
                                        .glowingDoubleBorder(isSelected = isHeaderFocused, shape = RoundedCornerShape(12.dp), cornerRadius = 12.dp)
                                        .clickable {
                                            newName = folder.name
                                            renameFocusZone = 0
                                            editingName = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "— ♡ ${folder.name} ♡ —",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF3B1B44),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (managing) {
                                    Text(
                                        text = "Edit mode: tap an app to remove ♡",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE06287),
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
                                            .background(Color(0xFFFFF6F8)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Folder is empty ♡\nTap header to manage apps!",
                                            color = Color(0xFF3B1B44),
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
                                            .heightIn(max = 240.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        itemsIndexed(folderApps, key = { _, app -> app.packageName }) { index, app ->
                                            val isTileFocused = focusSection == 1 && selectedAppIndex == index
                                            val tint = APP_SLOT_TINTS[index % APP_SLOT_TINTS.size]
                                            val squircleShape = RoundedCornerShape(16.dp)

                                            val scale by animateFloatAsState(
                                                targetValue = if (isTileFocused) 1.05f else 1.0f,
                                                animationSpec = tween(120),
                                                label = "folderAppTileScale"
                                            )

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .graphicsLayer {
                                                        scaleX = scale
                                                        scaleY = scale
                                                    }
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
                                                    ),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                // Squircle Icon Container
                                                Box(
                                                    modifier = Modifier
                                                        .size(68.dp)
                                                        .clip(squircleShape)
                                                        .background(tint)
                                                        .glowingDoubleBorder(isSelected = isTileFocused, shape = squircleShape, cornerRadius = 16.dp)
                                                        .border(
                                                            width = if (isTileFocused) 2.dp else 1.dp,
                                                            color = if (isTileFocused) Color(0xFFE06287) else tint.copy(alpha = 0.6f),
                                                            shape = squircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Image(
                                                        bitmap = app.imageBitmap,
                                                        contentDescription = app.name,
                                                        modifier = Modifier.size(36.dp)
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

                                                Spacer(Modifier.height(6.dp))

                                                // App Label
                                                Text(
                                                    text = app.name,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF3B1B44),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }

                // Top-Center Overlapping Pixel Ribbon Bow
                PixelRibbonBow(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-18).dp)
                )

                // Top-Left Overlapping Bunny Pin Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 8.dp, y = (-10).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFFDFE))
                        .border(1.5.dp, Color(0xFFDFB3C4), RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PixelBunnyAvatar(size = 20.dp)
                }

                // Bottom Scalloped "♥ Close (◄) ♥" Pill Button overlapping card bottom rim
                ScallopedClosePillButton(
                    isFocused = focusSection == 2,
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 18.dp)
                )
            }
        }
    }
}
