package one.crinklecod.mochibun.ui.screens.settings

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.LidLedManager
import one.crinklecod.mochibun.core.system.RootShell
import one.crinklecod.mochibun.data.model.LID_MAX_STEPS
import one.crinklecod.mochibun.data.model.LID_OFF_HEX
import one.crinklecod.mochibun.data.model.LID_WINDOW_COUNT
import one.crinklecod.mochibun.data.model.LidLedAnimation
import one.crinklecod.mochibun.data.model.LidLedStep
import one.crinklecod.mochibun.data.model.LidLedSwatch
import one.crinklecod.mochibun.data.model.isLidHexOff
import one.crinklecod.mochibun.data.model.lidHexToArgb
import one.crinklecod.mochibun.data.repository.LidAnimationSlot
import one.crinklecod.mochibun.data.repository.LidLedAnimationRepository
import one.crinklecod.mochibun.ui.components.softGlowFocus
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.destructiveColor

private const val CLASSIC_ID = "" // classic behavior: notification shimmer / solid charging color

private enum class LibraryAction(val label: String) {
    SET_NOTIF("Set Notif"), SET_CHARGING("Set Charging"), EDIT("Edit"), EXPORT("Export .yaml"), DELETE("Delete")
}

/** Action chips per card row: the two "Set" slots on top, the rest underneath. */
private const val SET_ACTION_COUNT = 2

private enum class EditorRow { NAME, STEP_BAR, STEP_EDIT, WINDOWS, SWATCHES, BOTTOM }

private enum class EditorAction(val label: String) { PREVIEW("▶ Preview"), TEST("Test Lid ♡"), SAVE("Save") }

/** Editor default for "+ Create New": a 5-step pink wave. */
private fun newDraft(): LidLedAnimation = LidLedAnimation(
    name = "Custom Wave ♡",
    steps = (0 until LID_WINDOW_COUNT).map { lit ->
        LidLedStep(List(LID_WINDOW_COUNT) { i -> if (i == lit) LidLedSwatch.HOT_PINK.hex else LID_OFF_HEX })
    }
)

private fun toast(context: Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

/**
 * Lid LED Studio: animation library (Screen 1) and the 5-window step maker (Screen 2).
 *
 * D-pad: Up/Down move between rows, Left/Right move within a row (or switch frames on the step
 * bar), Center activates. Back closes the editor first, then returns to Layout & Features.
 */
@Composable
internal fun LidLedStudioSubScreen(
    onBack: () -> Unit,
    onNestedBackHandlerChanged: ((() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var animations by remember { mutableStateOf<List<LidLedAnimation>>(emptyList()) }
    var notifId by remember { mutableStateOf(CLASSIC_ID) }
    var chargingId by remember { mutableStateOf(CLASSIC_ID) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<LidLedAnimation?>(null) }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            Triple(
                LidLedAnimationRepository.loadAll(context),
                LidLedAnimationRepository.getActiveAnimationId(context, LidAnimationSlot.NOTIFICATION),
                LidLedAnimationRepository.getActiveAnimationId(context, LidAnimationSlot.CHARGING)
            )
        }.let { (all, notif, charging) ->
            animations = all
            notifId = notif
            chargingId = charging
        }
    }

    // The hub owns Back; while the editor is open, Back returns to the library
    val editorOpen = draft != null
    DisposableEffect(editorOpen) {
        onNestedBackHandlerChanged(if (editorOpen) ({ draft = null }) else null)
        onDispose { onNestedBackHandlerChanged(null) }
    }

    requestFocusSafely(focusRequester, key = editorOpen)

    val currentDraft = draft
    if (currentDraft == null) {
        LidLedLibrary(
            animations = animations,
            notifId = notifId,
            chargingId = chargingId,
            onBack = onBack,
            onApply = { slot, id ->
                scope.launch {
                    withContext(Dispatchers.IO) { LidLedAnimationRepository.setActiveAnimationId(context, slot, id) }
                    when (slot) {
                        LidAnimationSlot.NOTIFICATION -> notifId = id
                        LidAnimationSlot.CHARGING -> chargingId = id
                    }
                    toast(
                        context,
                        if (slot == LidAnimationSlot.NOTIFICATION) "Set for notifications ♡" else "Set for charging ♡"
                    )
                }
            },
            onEdit = { anim ->
                // Editing a built-in clones it on save
                draft = if (LidLedAnimationRepository.isBuiltIn(anim.id)) anim.copy(id = "") else anim
            },
            onCreate = { draft = newDraft() },
            onDelete = { anim ->
                scope.launch {
                    withContext(Dispatchers.IO) { LidLedAnimationRepository.delete(context, anim.id) }
                    toast(context, "Deleted ${anim.name}")
                    refreshKey++
                }
            },
            onImported = { refreshKey++ },
            focusRequester = focusRequester,
            modifier = modifier
        )
    } else {
        LidLedStepMaker(
            initial = currentDraft,
            onClose = { draft = null },
            onSaved = {
                draft = null
                refreshKey++
            },
            focusRequester = focusRequester,
            modifier = modifier
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Screen 1: Library & Importer
// ---------------------------------------------------------------------------------------------

@Composable
private fun LidLedLibrary(
    animations: List<LidLedAnimation>,
    notifId: String,
    chargingId: String,
    onBack: () -> Unit,
    onApply: (LidAnimationSlot, String) -> Unit,
    onEdit: (LidLedAnimation) -> Unit,
    onCreate: () -> Unit,
    onDelete: (LidLedAnimation) -> Unit,
    onImported: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Row 0 = Create / Import, row 1 = classic shimmer, rows 2.. = animations
    var focusedRow by remember { mutableIntStateOf(0) }
    var focusedCol by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }
    var armedDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<LidLedAnimation?>(null) }
    // Each animation card has two D-pad rows matching its two chip lines:
    // part 0 = Set Notif / Set Charging, part 1 = Edit / Export / Delete
    val rowCount = 2 + animations.size * 2

    fun animIndexOf(row: Int): Int = if (row < 2) -1 else (row - 2) / 2
    fun partOf(row: Int): Int = if (row < 2) 0 else (row - 2) % 2

    LaunchedEffect(rowCount) { focusedRow = focusedRow.coerceIn(0, rowCount - 1) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { LidLedAnimationRepository.importFromUri(context, uri) }
                result.onSuccess {
                    toast(context, "Imported ${it.name} ♡")
                    onImported()
                }.onFailure { toast(context, "Import failed: ${it.message ?: "invalid file"}") }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-yaml")
    ) { uri ->
        val anim = pendingExport
        pendingExport = null
        if (uri != null && anim != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { LidLedAnimationRepository.exportToUri(context, uri, anim) }
                toast(context, if (result.isSuccess) "Exported ♡" else "Export failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun actionsFor(anim: LidLedAnimation): List<LibraryAction> =
        if (LidLedAnimationRepository.isBuiltIn(anim.id)) {
            LibraryAction.entries - LibraryAction.DELETE
        } else {
            LibraryAction.entries
        }

    fun colCount(row: Int): Int = when (row) {
        0 -> 2
        1 -> SET_ACTION_COUNT
        else -> animations.getOrNull(animIndexOf(row))?.let { anim ->
            if (partOf(row) == 0) SET_ACTION_COUNT else actionsFor(anim).size - SET_ACTION_COUNT
        } ?: 1
    }

    fun activate(row: Int, col: Int) {
        if (row == 0) {
            if (col == 0) onCreate() else importLauncher.launch(arrayOf("*/*"))
            return
        }
        if (row == 1) {
            onApply(if (col == 0) LidAnimationSlot.NOTIFICATION else LidAnimationSlot.CHARGING, CLASSIC_ID)
            return
        }
        val anim = animations.getOrNull(animIndexOf(row)) ?: return
        when (actionsFor(anim).getOrNull(partOf(row) * SET_ACTION_COUNT + col)) {
            LibraryAction.SET_NOTIF -> onApply(LidAnimationSlot.NOTIFICATION, anim.id)
            LibraryAction.SET_CHARGING -> onApply(LidAnimationSlot.CHARGING, anim.id)
            LibraryAction.EDIT -> onEdit(anim)
            LibraryAction.EXPORT -> {
                pendingExport = anim
                exportLauncher.launch(LidLedAnimationRepository.exportFileName(anim))
            }
            LibraryAction.DELETE -> {
                // Second press confirms
                if (armedDeleteId == anim.id) {
                    armedDeleteId = null
                    onDelete(anim)
                } else {
                    armedDeleteId = anim.id
                }
            }
            null -> Unit
        }
    }

    fun moveRow(direction: Int) {
        focusedRow = (focusedRow + direction + rowCount) % rowCount
        focusedCol = focusedCol.coerceIn(0, colCount(focusedRow) - 1)
        armedDeleteId = null
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun moveCol(direction: Int) {
        val count = colCount(focusedRow)
        focusedCol = (focusedCol + direction + count) % count
        armedDeleteId = null
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    SettingsScaffold(
        title = "Lid LED Studio",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                when {
                    native.keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                        if (event.type == KeyEventType.KeyUp) {
                            SoundEffectManager.play(context, SoundEffect.BACK)
                            onBack()
                        }
                        true
                    }
                    isCenterActivateKey(native.keyCode) -> {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            activate(focusedRow, focusedCol)
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                        }
                        true
                    }
                    native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (event.type == KeyEventType.KeyDown) {
                            moveRow(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                        }
                        true
                    }
                    native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.type == KeyEventType.KeyDown) {
                            moveCol(if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 48.dp, bottom = 64.dp, start = 14.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StudioChip(
                        label = "+ Create New",
                        isFocused = focusedRow == 0 && focusedCol == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { activate(0, 0) }
                    )
                    StudioChip(
                        label = "Import .yaml / .json",
                        isFocused = focusedRow == 0 && focusedCol == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { activate(0, 1) }
                    )
                }
            }
            item {
                AnimationCard(
                    title = "Classic Keitai Light",
                    subtitle = "Shimmer / solid • Outer LED Color",
                    previewColors = null,
                    isNotif = notifId == CLASSIC_ID,
                    isCharging = chargingId == CLASSIC_ID,
                    isFocused = focusedRow == 1,
                    actions = listOf(LibraryAction.SET_NOTIF.label, LibraryAction.SET_CHARGING.label),
                    focusedAction = if (focusedRow == 1) focusedCol else -1,
                    armedDelete = false,
                    onAction = { col ->
                        focusedRow = 1
                        focusedCol = col
                        activate(1, col)
                    }
                )
            }
            itemsIndexed(animations, key = { _, anim -> anim.id }) { index, anim ->
                val firstRow = 2 + index * 2
                val cardFocused = animIndexOf(focusedRow) == index
                val builtIn = LidLedAnimationRepository.isBuiltIn(anim.id)
                AnimationCard(
                    title = anim.name,
                    subtitle = anim.summary() + if (builtIn) " • built-in" else " • ${anim.author}",
                    previewColors = anim.steps.firstOrNull()?.windows,
                    isNotif = notifId == anim.id,
                    isCharging = chargingId == anim.id,
                    isFocused = cardFocused,
                    actions = actionsFor(anim).map { it.label },
                    focusedAction = if (cardFocused) partOf(focusedRow) * SET_ACTION_COUNT + focusedCol else -1,
                    armedDelete = armedDeleteId == anim.id,
                    onAction = { action ->
                        val part = if (action < SET_ACTION_COUNT) 0 else 1
                        focusedRow = firstRow + part
                        focusedCol = action - part * SET_ACTION_COUNT
                        activate(focusedRow, focusedCol)
                    }
                )
            }
        }
    }
}

@Composable
private fun AnimationCard(
    title: String,
    subtitle: String,
    previewColors: List<String>?,
    isNotif: Boolean,
    isCharging: Boolean,
    isFocused: Boolean,
    actions: List<String>,
    focusedAction: Int,
    armedDelete: Boolean,
    onAction: (Int) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 16.dp)
            .clip(shape)
            .background(ActiveTheme.container, shape)
            .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.45f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isNotif || isCharging) "♥" else "♡",
                color = ActiveTheme.accentDeep,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isNotif) SlotBadge("Notif")
                    if (isCharging) SlotBadge("Charging")
                }
                Text(
                    text = subtitle,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (previewColors != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    previewColors.forEach { hex -> MiniWindow(hex) }
                }
            }
        }
        // Two chip lines; each is its own D-pad row in the library (indices stay linear here)
        val indexed = actions.withIndex().toList()
        listOf(indexed.take(SET_ACTION_COUNT), indexed.drop(SET_ACTION_COUNT)).filter { it.isNotEmpty() }.forEach { chunk ->
            Spacer(modifier = Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                chunk.forEach { (col, label) ->
                    val isDelete = label == LibraryAction.DELETE.label
                    StudioChip(
                        label = if (isDelete && armedDelete) "Sure?" else label,
                        isFocused = col == focusedAction,
                        destructive = isDelete,
                        compact = true,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(col) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotBadge(label: String) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = label,
        color = ActiveTheme.accentDeep,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(shape)
            .background(ActiveTheme.primary.copy(alpha = 0.55f), shape)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
private fun MiniWindow(hex: String) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(shape)
            .background(windowColor(hex), shape)
            .border(0.6.dp, ActiveTheme.cardBorder.copy(alpha = 0.5f), shape)
    )
}

// ---------------------------------------------------------------------------------------------
// Screen 2: 5-Window Step Maker
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LidLedStepMaker(
    initial: LidLedAnimation,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initial.name) }
    var steps by remember { mutableStateOf(initial.sanitized().steps.map { it.windows }.ifEmpty { listOf(List(LID_WINDOW_COUNT) { LID_OFF_HEX }) }) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var row by remember { mutableStateOf(EditorRow.WINDOWS) }
    var stepEditCol by remember { mutableIntStateOf(0) } // 0 = + Dup, 1 = - Del
    var windowIndex by remember { mutableIntStateOf(0) } // focused window = swatch target
    var swatchIndex by remember { mutableIntStateOf(1) }
    var bottomCol by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var previewFrame by remember { mutableIntStateOf(0) }
    var editingName by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun currentAnimation(): LidLedAnimation =
        initial.copy(name = name, steps = steps.map { LidLedStep(it) }).sanitized()

    LaunchedEffect(previewing) {
        previewFrame = stepIndex
        while (previewing) {
            delay(initial.stepMs.toLong())
            previewFrame = (previewFrame + 1) % steps.size
        }
    }

    fun setWindow(window: Int, hex: String) {
        steps = steps.mapIndexed { i, w -> if (i == stepIndex) w.mapIndexed { j, c -> if (j == window) hex else c } else w }
    }

    fun activate() {
        when (row) {
            EditorRow.NAME -> editingName = true
            EditorRow.STEP_BAR -> Unit
            EditorRow.STEP_EDIT -> if (stepEditCol == 0) {
                if (steps.size < LID_MAX_STEPS) {
                    steps = steps.take(stepIndex + 1) + listOf(steps[stepIndex]) + steps.drop(stepIndex + 1)
                    stepIndex++
                } else {
                    toast(context, "Max $LID_MAX_STEPS steps")
                }
            } else {
                if (steps.size > 1) {
                    steps = steps.filterIndexed { i, _ -> i != stepIndex }
                    stepIndex = stepIndex.coerceAtMost(steps.size - 1)
                } else {
                    toast(context, "At least 1 step")
                }
            }
            EditorRow.WINDOWS -> {
                val current = LidLedSwatch.fromHex(steps[stepIndex][windowIndex]) ?: LidLedSwatch.OFF
                val next = current.next()
                setWindow(windowIndex, next.hex)
                swatchIndex = next.ordinal
            }
            EditorRow.SWATCHES -> setWindow(windowIndex, LidLedSwatch.entries[swatchIndex].hex)
            EditorRow.BOTTOM -> when (EditorAction.entries[bottomCol]) {
                EditorAction.PREVIEW -> previewing = !previewing
                EditorAction.TEST -> {
                    if (!RootShell.isRootAvailable()) {
                        toast(context, "Root is needed to drive the lid LEDs")
                    } else if (RootShell.isGranted() == false) {
                        // su exists but Magisk denied this app: LED writes would silently fail
                        toast(context, "Mochibun has no root access ♡ Grant it in Magisk")
                    } else {
                        toast(context, "Playing on the lid ♡ (~5s)")
                        val anim = currentAnimation()
                        // Lid windows light with the flip open, so the test starts right away
                        scope.launch(Dispatchers.IO) { LidLedManager.playStepAnimation(anim) }
                    }
                }
                EditorAction.SAVE -> if (!saving) {
                    saving = true
                    previewing = false
                    val anim = currentAnimation()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { LidLedAnimationRepository.save(context, anim) }
                        }
                        saving = false
                        result.onSuccess {
                            toast(context, "Saved ${it.name} ♡")
                            onSaved()
                        }.onFailure { toast(context, "Save failed: ${it.message}") }
                    }
                }
            }
        }
    }

    fun horizontal(direction: Int) {
        when (row) {
            EditorRow.NAME -> return
            EditorRow.STEP_BAR -> {
                stepIndex = (stepIndex + direction + steps.size) % steps.size
                previewing = false
            }
            EditorRow.STEP_EDIT -> stepEditCol = (stepEditCol + direction + 2) % 2
            EditorRow.WINDOWS -> windowIndex = (windowIndex + direction + LID_WINDOW_COUNT) % LID_WINDOW_COUNT
            EditorRow.SWATCHES -> swatchIndex = (swatchIndex + direction + LidLedSwatch.entries.size) % LidLedSwatch.entries.size
            EditorRow.BOTTOM -> bottomCol = (bottomCol + direction + EditorAction.entries.size) % EditorAction.entries.size
        }
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun vertical(direction: Int) {
        val rows = EditorRow.entries
        row = rows[(row.ordinal + direction + rows.size) % rows.size]
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    val shownStep = if (previewing) steps[previewFrame.coerceIn(0, steps.size - 1)] else steps[stepIndex]
    val litColors = steps[stepIndex].filterNot(::isLidHexOff).distinct()

    SettingsScaffold(
        title = "Step Maker",
        showBack = true,
        onBack = onClose,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                when {
                    native.keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                        if (event.type == KeyEventType.KeyUp) {
                            SoundEffectManager.play(context, SoundEffect.BACK)
                            onClose()
                        }
                        true
                    }
                    isCenterActivateKey(native.keyCode) -> {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            activate()
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                        }
                        true
                    }
                    native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (event.type == KeyEventType.KeyDown) {
                            vertical(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                        }
                        true
                    }
                    native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (event.type == KeyEventType.KeyDown) {
                            horizontal(if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, start = 14.dp, end = 14.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Name
            StudioChip(
                label = "✎  $name",
                isFocused = row == EditorRow.NAME,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    row = EditorRow.NAME
                    editingName = true
                }
            )

            // Step bar: ◄ Step n / N ►
            StudioChip(
                label = "◄   Step ${stepIndex + 1} / ${steps.size}" +
                    (if (stepIndex == steps.lastIndex) " (Resting)" else "") + "   ►",
                isFocused = row == EditorRow.STEP_BAR,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    row = EditorRow.STEP_BAR
                    stepIndex = (stepIndex + 1) % steps.size
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StudioChip(
                    label = "+ Dup",
                    isFocused = row == EditorRow.STEP_EDIT && stepEditCol == 0,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        row = EditorRow.STEP_EDIT
                        stepEditCol = 0
                        activate()
                    }
                )
                StudioChip(
                    label = "- Del",
                    isFocused = row == EditorRow.STEP_EDIT && stepEditCol == 1,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        row = EditorRow.STEP_EDIT
                        stepEditCol = 1
                        activate()
                    }
                )
            }

            // W1..W5
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                shownStep.forEachIndexed { i, hex ->
                    WindowBox(
                        hex = hex,
                        label = "W${i + 1}",
                        isFocused = row == EditorRow.WINDOWS && windowIndex == i,
                        isTarget = windowIndex == i,
                        onClick = {
                            row = EditorRow.WINDOWS
                            windowIndex = i
                            activate()
                        }
                    )
                }
            }
            Text(
                text = when {
                    previewing -> "Previewing ♡ ${initial.stepMs}ms per step"
                    litColors.size > 1 -> "Lid shows one color per step (the most used one)"
                    else -> "Center: cycle W${windowIndex + 1} • pick a swatch below"
                },
                color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // 7 swatches
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                LidLedSwatch.entries.forEachIndexed { i, swatch ->
                    SwatchChip(
                        swatch = swatch,
                        isFocused = row == EditorRow.SWATCHES && swatchIndex == i,
                        onClick = {
                            row = EditorRow.SWATCHES
                            swatchIndex = i
                            activate()
                        }
                    )
                }
            }
            Text(
                text = "${LidLedSwatch.entries[swatchIndex].label} → W${windowIndex + 1}",
                color = ActiveTheme.accentDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom bar
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                EditorAction.entries.forEachIndexed { i, action ->
                    StudioChip(
                        label = if (action == EditorAction.PREVIEW && previewing) "■ Stop" else action.label,
                        isFocused = row == EditorRow.BOTTOM && bottomCol == i,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            row = EditorRow.BOTTOM
                            bottomCol = i
                            activate()
                        }
                    )
                }
            }
        }
    }

    if (editingName) {
        val nameRequester = remember { FocusRequester() }
        var input by remember { mutableStateOf(name) }
        requestFocusSafely(nameRequester)
        fun commit() {
            name = input.trim().take(LidLedAnimation.MAX_NAME_LENGTH).ifBlank { "Custom Wave ♡" }
            editingName = false
        }
        BasicAlertDialog(
            onDismissRequest = { editingName = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ActiveTheme.container)
                    .border(1.5.dp, ActiveTheme.cardBorder, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "— ♡ Animation Name ♡ —",
                    color = ActiveTheme.accentDeep,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(LidLedAnimation.MAX_NAME_LENGTH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                        .border(1.5.dp, ActiveTheme.selectionGlow, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .focusRequester(nameRequester),
                    textStyle = TextStyle(
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(ActiveTheme.accentDeep),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() })
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SettingsActionRow(title = "Save", isFocused = true, modifier = Modifier.weight(1f), onClick = { commit() })
                    SettingsActionRow(
                        title = "Cancel",
                        isFocused = false,
                        modifier = Modifier.weight(1f),
                        onClick = { editingName = false }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------------------------

/** "Off" windows render as a soft tinted tile instead of a harsh black square. */
@Composable
private fun windowColor(hex: String): Color =
    if (isLidHexOff(hex)) ActiveTheme.surfaceTint.copy(alpha = 0.6f) else Color(lidHexToArgb(hex))

@Composable
private fun StudioChip(
    label: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 12.dp)
            .clip(shape)
            .background(
                if (isFocused) ActiveTheme.primary.copy(alpha = 0.55f) else ActiveTheme.surfaceTint.copy(alpha = 0.35f),
                shape
            )
            .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = if (compact) 5.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (destructive) destructiveColor else ActiveTheme.accentDeep,
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WindowBox(
    hex: String,
    label: String,
    isFocused: Boolean,
    isTarget: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { clip = false }
                .softGlowFocus(isSelected = isFocused, cornerRadius = 10.dp)
                .clip(shape)
                .background(windowColor(hex), shape)
                .border(
                    width = if (isTarget) 2.dp else 1.dp,
                    color = if (isTarget) ActiveTheme.accentDeep else ActiveTheme.cardBorder.copy(alpha = 0.5f),
                    shape = shape
                )
                .clickable(onClick = onClick)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = ActiveTheme.accentDeep,
            fontSize = 10.sp,
            fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SwatchChip(swatch: LidLedSwatch, isFocused: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(32.dp)
            .height(32.dp)
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 16.dp)
            .clip(CircleShape)
            .background(windowColor(swatch.hex), CircleShape)
            .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (swatch == LidLedSwatch.OFF) {
            Text(text = "off", color = ActiveTheme.accentDeep, fontSize = 9.sp)
        }
    }
}
