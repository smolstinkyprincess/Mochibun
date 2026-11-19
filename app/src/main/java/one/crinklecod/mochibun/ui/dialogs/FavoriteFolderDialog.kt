/**
 * Favorite Folder modal dialog and management subsystems for Mochibun.
 * Handles folder viewing, app picker checklists, inline renaming, and deletion
 * while enforcing strict D-pad focus isolation so background launcher keys don't leak.
 */
package one.crinklecod.mochibun.ui.dialogs

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.components.KeitaiLaceContainer
import one.crinklecod.mochibun.ui.components.*
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.R
import one.crinklecod.mochibun.data.model.FavoriteFolder
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.model.LauncherApp
import one.crinklecod.mochibun.ui.theme.focusedContainerColor
import one.crinklecod.mochibun.ui.theme.selectedContainerColor
import one.crinklecod.mochibun.ui.theme.destructiveColor
import one.crinklecod.mochibun.ui.theme.unfocusedCardBgColor
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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
 * 0: Soft Lavender (#E2D9F7)
 * 1: Blush Pink    (#FCE2E6)
 * 2: Mint/Seafoam  (#D4F1E8)
 * 3: Soft Peach    (#FFE5D9)
 */
private val SQUIRCLE_TINTS = listOf(
    FolderSquircleTint(container = Color(0xFFE2D9F7), border = Color(0xFFC7B6EE)),
    FolderSquircleTint(container = Color(0xFFFCE2E6), border = Color(0xFFF6ACB9)),
    FolderSquircleTint(container = Color(0xFFD4F1E8), border = Color(0xFFA5E2CD)),
    FolderSquircleTint(container = Color(0xFFFFE5D9), border = Color(0xFFF9C6B3))
)

/**
 * Subtle floating pixel sparkle stars and hearts on the dialog backdrop.
 */
private val BACKDROP_SPARKLES = listOf(
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

@Composable
fun FloatingPixelBackdrop(
    modifier: Modifier = Modifier
) {
    val path = remember { Path() }
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        for ((fx, fy, item) in BACKDROP_SPARKLES) {
            val (col, sDp) = item
            val cx = w * fx
            val cy = h * fy
            val s = sDp.toPx()
            path.reset()
            path.apply {
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
 * and route them across 3 distinct zones (Header, App List, Close Button).
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
    cardRoundness: Dp,
    onLaunchApp: (LauncherApp) -> Unit,
    onRemoveApp: (String) -> Unit,
    onUpdateApps: (List<String>) -> Unit = {},
    onRename: (String) -> Unit,
    onDeleteFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusManager = LocalFocusManager.current
    val scatterLayout by PreferencesManager.isFolderScatterLayout.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var editingName by remember(folder.id) { mutableStateOf(initialEditingName) }
    var newNameValue by remember(folder.id, folder.name) {
        mutableStateOf(TextFieldValue(folder.name))
    }
    val newName = newNameValue.text
    val setNewName: (String) -> Unit = {
        val text = it.take(32)
        newNameValue = TextFieldValue(text, TextRange(text.length))
    }
    val setNameFieldValue: (TextFieldValue) -> Unit = {
        newNameValue = it.limitTextLength(32)
    }
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
    // 1 = App List (selectedAppIndex)
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
            renameFieldFocusRequester.awaitFocusSafely(attempts = 6)
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            delay(16)
            folderFocusRequester.awaitFocusSafely(attempts = 6)
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
                setNewName(folder.name)
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

                // Left soft key / Menu (same key set as the App Drawer's LSK): opens the app picker
                // from the main tray, which is what the empty-folder prompt tells users to press.
                // Swallowed in every sub-view so the matching KeyUp never leaks to Home.
                val isLeftSoftKey = native.keyCode == KeyEvent.KEYCODE_MENU ||
                    native.keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                    native.keyCode == KeyEvent.KEYCODE_F1 ||
                    native.scanCode == 580 ||
                    native.scanCode == 59
                if (isLeftSoftKey && !(editingName && isEditingRenameField)) {
                    val isMainTray = !editingName && !confirmingDelete && !pickingApps
                    if (isMainTray && event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        managing = false
                        pickerZone = 0
                        pickerAppIndex = 0
                        pickingApps = true
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
                                        setNewName(nextSuggestion)
                                    }
                                    2 -> {
                                        setNewName(folder.name)
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
                                setNewName(folder.name)
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

                // 2D sticker-tray traversal (math in FolderTrayNav). selectedAppIndex is kept while
                // the header or Close pill is focused, so the shown page and column survive the trip.
                val totalApps = folderApps.size
                val trayPage = FolderTrayNav.pageOf(selectedAppIndex, totalApps)
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusSection) {
                            1 -> {
                                selectedAppIndex = FolderTrayNav.left(selectedAppIndex, totalApps)
                                true
                            }
                            2 -> {
                                // Close pill: Left dismisses folder cleanly
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focusSection == 1) {
                            selectedAppIndex = FolderTrayNav.right(selectedAppIndex, totalApps)
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusSection) {
                            0 -> {
                                // Topmost
                            }
                            1 -> {
                                when (val move = FolderTrayNav.up(selectedAppIndex, totalApps, scatterLayout)) {
                                    is FolderTrayNav.Move.To -> selectedAppIndex = move.index
                                    else -> focusSection = 0
                                }
                            }
                            2 -> {
                                if (totalApps > 0) {
                                    focusSection = 1
                                    selectedAppIndex = FolderTrayNav.bottomRowTarget(
                                        page = trayPage,
                                        rememberedIndex = selectedAppIndex,
                                        total = totalApps,
                                        scatter = scatterLayout
                                    )
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
                                    selectedAppIndex = FolderTrayNav.headerDownTarget(trayPage, totalApps)
                                } else {
                                    focusSection = 2
                                }
                            }
                            1 -> {
                                when (val move = FolderTrayNav.down(selectedAppIndex, totalApps, scatterLayout)) {
                                    is FolderTrayNav.Move.To -> selectedAppIndex = move.index
                                    else -> focusSection = 2
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
        ResizeWithKeyboard()
        val pickerListState = rememberLazyListState()

        val isMainView = !confirmingDelete && !pickingApps && !editingName

        // "Shaker charm" parallax: scattered stickers drift a few dp as the phone tilts.
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val stickerNudge = remember { StickerNudge() }
        // Same ~1 GB check the mascot uses for its static-frames fallback (cached after Home).
        val isOneGbClassDevice = remember(context) { MascotSpriteStore.isStaticFramesOnly(context) }
        val nudgeEnabled = isMainView && scatterLayout && !isOneGbClassDevice
        StickerNudgeSensorEffect(nudgeEnabled, context, lifecycleOwner, stickerNudge)

        // Z-order, back to front (main sticker view):
        //   1. scrim + backdrop sparkles
        //   2. folder card, anchored low so its bottom rim tucks behind the clouds
        //   3. raised BottomLavenderClouds shelf (+ solid base strip)
        //   4. Close pill resting on the clouds
        // Rename / picker / delete keep the centred card with no shelf, so nothing covers their
        // buttons or the IME. No top cloud drape: Home already draws its own under the scrim.
        // Scrim is a flat dim, never blur (PowerVR GE8300).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f))
                .clickable { handleBack() },
            contentAlignment = Alignment.Center
        ) {
            // Subtle floating pixel sparkle stars and hearts on the dialog backdrop
            FloatingPixelBackdrop()

            // Folder card (taps inside it must not reach the dismissing scrim)
            Box(
                modifier = Modifier
                    .then(
                        if (isMainView) {
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = FOLDER_CARD_BOTTOM_INSET)
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer { clip = false }
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center
            ) {
                // Main Folder Modal Card Container
                val cardCornerRadius = cardRoundness.coerceIn(22.dp, 24.dp)

                KeitaiLaceContainer(
                    cardCornerRadius = cardCornerRadius,
                    // Slightly sheer card so the dimmed wallpaper and backdrop sparkles show
                    // through. Plain alpha, never blur (PowerVR GE8300).
                    containerColor = ActiveTheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .width(308.dp)
                        .curtainUnfurl(visible = true)
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false }
                        .padding(
                            top = if (isMainView) 6.dp else 24.dp,
                            bottom = if (isMainView) FOLDER_CARD_INNER_BOTTOM else 16.dp,
                            start = 8.dp,
                            end = 8.dp
                        ),
                    contentAlignment = Alignment.Center
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
                                    color = ActiveTheme.textPrimary,
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
                                    color = ActiveTheme.accentDeep,
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
                                        .heightIn(max = 240.dp)
                                        .graphicsLayer { clip = false },
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    itemsIndexed(
                                        availableApps,
                                        key = { _, app -> app.componentKey },
                                        contentType = { _, _ -> "folder_picker_row" }
                                    ) { index, app ->
                                        val pkg = app.packageName
                                        val checked = pkg in draftPackages
                                        FolderPickerAppRow(
                                            app = app,
                                            checked = checked,
                                            isFocused = pickerZone == 0 && pickerAppIndex == index,
                                            selectedContainerColor = theme.selectedContainerColor,
                                            onClick = {
                                                pickerZone = 0
                                                pickerAppIndex = index
                                                draftPackages = if (checked) draftPackages - pkg else draftPackages + pkg
                                            }
                                        )
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
                                    color = ActiveTheme.accentDeep,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                OutlinedTextField(
                                    value = newNameValue,
                                    onValueChange = setNameFieldValue,
                                    label = { Text("Folder name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ActiveTheme.primary,
                                        unfocusedBorderColor = ActiveTheme.cardBorder,
                                        focusedTextColor = ActiveTheme.textPrimary,
                                        unfocusedTextColor = ActiveTheme.textPrimary,
                                        focusedContainerColor = ActiveTheme.current.unfocusedCardBgColor,
                                        unfocusedContainerColor = ActiveTheme.current.unfocusedCardBgColor
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
                                        setNewName(nextSuggestion)
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
                                            setNewName(folder.name)
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
                            // Main Vertical Scaled-Down App Drawer List Folder View
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { clip = false },
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
                                            cornerRadius = 12.dp,
                                            glowColor = ActiveTheme.selectionGlow,
                                            borderColor = ActiveTheme.primary,
                                            containerTint = ActiveTheme.surfaceTint
                                        )
                                        .background(
                                            if (isHeaderFocused) ActiveTheme.current.focusedContainerColor else Color.Transparent,
                                            headerShape
                                        )
                                        .clickable {
                                            setNewName(folder.name)
                                            renameFocusZone = 0
                                            editingName = true
                                        }
                                        .padding(horizontal = 12.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "— ♡ ${folder.name.ifBlank { "Favorites" }} ♡ —",
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ActiveTheme.accentDeep,
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

                                Spacer(Modifier.height(6.dp))

                                // Recessed sticker tray of icon-only charms (up to 6 per page; scattered stickers or a neat 3 × 2 grid)
                                val trayShape = RoundedCornerShape(18.dp)
                                val trayModifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { clip = false }
                                    // background/border only, no clip(): the focus halo of an
                                    // edge charm has to spill over the tray rim.
                                    .background(ActiveTheme.background.copy(alpha = 0.65f), trayShape)
                                    .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.45f), trayShape)
                                    .padding(horizontal = 12.dp, vertical = 14.dp)

                                if (folderApps.isEmpty()) {
                                    Box(
                                        modifier = trayModifier.height(110.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "♡ Empty folder — press Menu to add apps ♡",
                                            color = ActiveTheme.textPrimary,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    val totalApps = folderApps.size
                                    val pageCount = FolderTrayNav.pageCount(totalApps)
                                    val trayPage = FolderTrayNav.pageOf(selectedAppIndex, totalApps)
                                    val pageStart = FolderTrayNav.pageStart(trayPage)
                                    val pageEnd = FolderTrayNav.pageLast(trayPage, totalApps)
                                    val layout = FolderTrayNav.layoutFor(pageEnd - pageStart + 1, scatterLayout)
                                    // Paged folders reserve the tallest page's depth so the card
                                    // doesn't jump in height when a page flips.
                                    val minDepth = if (pageCount > 1) {
                                        maxOf(
                                            FolderTrayNav.layoutFor(FolderTrayNav.PAGE_SIZE, scatterLayout).depth,
                                            FolderTrayNav.layoutFor(
                                                FolderTrayNav.countOnPage(pageCount - 1, totalApps),
                                                scatterLayout
                                            ).depth
                                        )
                                    } else {
                                        0f
                                    }

                                    FolderStickerScatter(
                                        scatter = layout,
                                        minDepth = minDepth,
                                        nudge = if (nudgeEnabled) stickerNudge else null,
                                        modifier = trayModifier
                                    ) {
                                        for (index in pageStart..pageEnd) {
                                            val app = folderApps[index]
                                            key(app.packageName) {
                                                FolderTrayCharm(
                                                    app = app,
                                                    tint = SQUIRCLE_TINTS[index % SQUIRCLE_TINTS.size],
                                                    isFocused = focusSection == 1 && selectedAppIndex == index,
                                                    managing = managing,
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
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // Live name banner for the charm in focus (or the remembered
                                    // one while the header / Close pill holds focus).
                                    val bannerApp = folderApps.getOrNull(selectedAppIndex)
                                    FolderNameBanner(label = bannerApp?.name.orEmpty())

                                    if (pageCount > 1) {
                                        Spacer(Modifier.height(4.dp))
                                        FolderPageDots(page = trayPage, pageCount = pageCount)
                                    }
                                }
                            }
                        }
                    }
                }
                }

                // Pixel ribbon bow straddling the top lace rim (same PixelRibbonBow as Home's
                // greeting card). The wrapper Box above is unclipped, so the overhang survives.
                // Shown with custom 9-slice borders too: those only decorate the corners, and the
                // greeting banner keeps its centred bow alongside them.
                if (isMainView) {
                    PixelRibbonBow(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-12).dp)
                            .graphicsLayer { clip = false },
                        width = 34.dp,
                        height = 28.dp
                    )
                }
            }

            if (isMainView) {
                // Cloud shelf over the card's lower rim. The cloud paths are anchored to their
                // box bottom, so the shelf is lifted with an offset rather than made taller.
                BottomLavenderClouds(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -CLOUD_SHELF_LIFT)
                )
                // Solid base under the lifted shelf: fills the gap to the screen edge and hides
                // the cloud path's closing outline along its (now visible) bottom edge.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(CLOUD_SHELF_LIFT + 1.dp)
                        .background(ActiveTheme.palette.cloudFront)
                )

                // Topmost: Close pill resting on the clouds; nothing above it can clip its glow.
                KawaiiClosePill(
                    isFocused = focusSection == 2,
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = CLOSE_PILL_BOTTOM_INSET)
                        .graphicsLayer { clip = false }
                        .width(154.dp)
                )
            }
        }
    }
}

// "Rising out of the clouds" geometry (dp above the screen's bottom edge), tuned for 480 × 800:
// BottomLavenderClouds is 72dp tall; lifted by CLOUD_SHELF_LIFT its front puffs peak ~54-56dp
// (troughs ~36-42dp) and its back puffs ~67-69dp (troughs ~50-54dp, ~38-40dp at the screen
// edges). The card bottom at FOLDER_CARD_BOTTOM_INSET stays under the back layer across the
// card's width; its content ends FOLDER_CARD_INNER_BOTTOM + the lace padding (20dp) higher, at
// ~80dp, so the name pill and page dots stay clear of the clouds.
private val CLOUD_SHELF_LIFT = 10.dp
private val FOLDER_CARD_BOTTOM_INSET = 35.dp
private val FOLDER_CARD_INNER_BOTTOM = 22.dp
/** Close pill (36dp tall) spans 16..52dp: sitting on the front puffs. */
private val CLOSE_PILL_BOTTOM_INSET = 16.dp

/** Smoothed tilt (-1..1) shared by the sensor listener and the sticker layers. */
@Stable
private class StickerNudge {
    val x = mutableFloatStateOf(0f)
    val y = mutableFloatStateOf(0f)

    fun clear() {
        x.floatValue = 0f
        y.floatValue = 0f
    }
}

/**
 * Listens to the accelerometer only while [enabled] (main sticker view, scatter layout, not a
 * ~1 GB phone) and the activity is resumed: closing the folder, switching to a sub-view, or
 * pausing (flip closed, screen off) unregisters it, so it costs nothing otherwise. Phones and
 * emulators without an accelerometer simply get no nudge.
 */
@Composable
private fun StickerNudgeSensorEffect(
    enabled: Boolean,
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    nudge: StickerNudge
) {
    DisposableEffect(enabled, context, lifecycleOwner) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || sensorManager == null || sensor == null) {
            nudge.clear()
            return@DisposableEffect onDispose { }
        }

        val filter = TiltNudgeFilter()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (filter.update(event.values[0], event.values[1])) {
                    nudge.x.floatValue = filter.tiltX
                    nudge.y.floatValue = filter.tiltY
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun start() {
            if (registered) return
            // Re-capture the neutral angle on every resume: the phone may be held differently.
            filter.reset()
            registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        fun stop() {
            if (registered) sensorManager.unregisterListener(listener)
            registered = false
            nudge.clear()
        }

        // addObserver replays the current state, so this starts at once if already resumed.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
}

private val CHARM_SIZE = 54.dp
private val CHARM_ICON_SIZE = 34.dp
private val CHARM_CORNER = 15.dp
/** One D-pad row step in [FolderTrayNav.Slot.y] units. */
private val TRAY_ROW_PITCH = 66.dp

/**
 * Places the page's charms at their [FolderTrayNav.Layout] slots (emitted in slot order) with
 * each sticker's tilt, and draws the layout's tiny sparkles into the empty space behind them.
 * A plain `Layout`, not a `BoxWithConstraints`, so a D-pad step costs no subcomposition.
 */
@Composable
private fun FolderStickerScatter(
    scatter: FolderTrayNav.Layout,
    minDepth: Float,
    nudge: StickerNudge?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val sparkleA = ActiveTheme.accentDeep.copy(alpha = 0.32f)
    val sparkleB = ActiveTheme.primary.copy(alpha = 0.55f)
    val depth = maxOf(scatter.depth, minDepth)
    // Vertical slack when a short page sits in a tray sized for a deeper one: centre it.
    val slackRows = (depth - scatter.depth) / 2f

    val motifModifier = remember(scatter, slackRows, sparkleA, sparkleB) {
        Modifier.drawWithCache {
            val charm = CHARM_SIZE.toPx()
            val pitch = TRAY_ROW_PITCH.toPx()
            val room = size.width - charm
            // Built once per layout/size; the draw lambda below only replays them.
            val paths = scatter.motifs.map { motif ->
                val cx = charm / 2f + motif.x * room
                val cy = (motif.y + slackRows) * pitch + charm / 2f
                val r = motif.sizeDp.dp.toPx() / 2f
                Path().apply {
                    moveTo(cx, cy - r)
                    quadraticTo(cx, cy, cx + r, cy)
                    quadraticTo(cx, cy, cx, cy + r)
                    quadraticTo(cx, cy, cx - r, cy)
                    quadraticTo(cx, cy, cx, cy - r)
                    close()
                }
            }
            onDrawBehind {
                paths.forEachIndexed { i, path ->
                    drawPath(path, if (i % 2 == 0) sparkleA else sparkleB)
                }
            }
        }
    }

    Layout(
        content = content,
        modifier = modifier.then(motifModifier)
    ) { measurables, constraints ->
        val charm = CHARM_SIZE.roundToPx()
        val pitch = TRAY_ROW_PITCH.toPx()
        val width = constraints.maxWidth
        val height = (depth * pitch).toInt() + charm
        val charmConstraints = Constraints.fixed(charm, charm)
        val maxShift = TiltNudgeFilter.MAX_SHIFT_DP.dp.toPx()
        val placeables = measurables.map { it.measure(charmConstraints) }
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { i, placeable ->
                val slot = scatter.slots.getOrNull(i) ?: return@forEachIndexed
                val x = (slot.x * (width - charm)).toInt()
                val y = ((slot.y + slackRows) * pitch).toInt()
                if (nudge != null) {
                    // The layer block reads the nudge state, so a tilt update only re-runs this
                    // block on the RenderNode: no recomposition, no relayout, no allocation.
                    val depth = TiltNudgeFilter.depthFor(i)
                    placeable.placeWithLayer(x, y) {
                        val tx = nudge.x.floatValue
                        val ty = nudge.y.floatValue
                        // Stickers slide "downhill": left edge down -> drift left; phone laid
                        // flatter (less gravity down the screen) -> drift up.
                        translationX = -tx * maxShift * depth
                        translationY = ty * maxShift * depth
                        rotationZ = slot.tilt - tx * TiltNudgeFilter.MAX_ROTATION_DEG * depth
                    }
                } else if (slot.tilt == 0f) {
                    // Untilted charms (neat grid, 0° stickers) skip the extra layer.
                    placeable.place(x, y)
                } else {
                    placeable.placeWithLayer(x, y) { rotationZ = slot.tilt }
                }
            }
        }
    }
}

/**
 * One icon-only pastel squircle charm in the folder tray. The label lives in [FolderNameBanner]
 * instead. A separate skippable composable so a D-pad step only recomposes the two charms whose
 * [isFocused] flips.
 */
@Composable
private fun FolderTrayCharm(
    app: LauncherApp,
    tint: FolderSquircleTint,
    isFocused: Boolean,
    managing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(CHARM_CORNER)
    Box(modifier = Modifier.size(CHARM_SIZE)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .softGlowFocus(
                    isSelected = isFocused,
                    cornerRadius = CHARM_CORNER,
                    glowColor = ActiveTheme.selectionGlow,
                    borderColor = ActiveTheme.primary,
                    containerTint = ActiveTheme.surfaceTint,
                    // Fixed-size tray, nothing to scroll; skip the bring-into-view request.
                    bringIntoView = false
                )
                .background(tint.container, shape)
                .border(1.dp, tint.border, shape)
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = app.imageBitmap,
                contentDescription = app.name,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(CHARM_ICON_SIZE)
            )
        }

        if (managing) {
            // Remove badge hangs off the corner, outside the charm's clip.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .size(18.dp)
                    .background(destructiveColor, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    fontSize = 10.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Centered pill showing the focused charm's name. `basicMarquee` only scrolls when the text is
 * wider than the pill, and stops after a few loops so an idle open folder doesn't keep
 * rendering at full frame rate on the MT6761.
 */
@Composable
private fun FolderNameBanner(label: String) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .widthIn(min = 120.dp, max = 240.dp)
            .background(ActiveTheme.surfaceTint, shape)
            .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.5f), shape)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✦ $label ✦",
            color = ActiveTheme.accentDeep,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = 3, initialDelayMillis = 800)
        )
    }
}

@Composable
private fun FolderPageDots(page: Int, pageCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        for (p in 0 until pageCount) {
            val active = p == page
            Text(
                text = if (active) "♥" else "•",
                color = if (active) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * One row of the "add apps" picker. Skippable for the same reason as [FolderAppRow]: picker D-pad
 * steps and check toggles recompose only the rows that changed.
 */
@Composable
private fun FolderPickerAppRow(
    app: LauncherApp,
    checked: Boolean,
    isFocused: Boolean,
    selectedContainerColor: Color,
    onClick: () -> Unit
) {
    val rowShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 12.dp)
            .clip(rowShape)
            .background(
                if (isFocused) selectedContainerColor else Color.Transparent
            )
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) ActiveTheme.palette.focusedCardBorder else Color.Transparent,
                shape = rowShape
            )
            .clickable(onClick = onClick)
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
            color = ActiveTheme.textPrimary,
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
                .background(if (checked) ActiveTheme.accentDeep else Color.Transparent)
                .border(1.5.dp, ActiveTheme.accentDeep, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (checked) "✓" else "＋",
                color = if (checked) Color.White else ActiveTheme.accentDeep,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
