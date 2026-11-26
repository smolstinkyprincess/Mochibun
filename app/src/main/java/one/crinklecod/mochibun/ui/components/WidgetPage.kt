package one.crinklecod.mochibun.ui.components

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import one.crinklecod.mochibun.core.haptic.HapticManager
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.widget.CollagePreset
import one.crinklecod.mochibun.core.widget.DecoStyle
import one.crinklecod.mochibun.core.widget.MochibunWidgetHost
import one.crinklecod.mochibun.core.widget.NavDirection
import one.crinklecod.mochibun.core.widget.StickerKind
import one.crinklecod.mochibun.core.widget.WidgetCanvasLayout
import one.crinklecod.mochibun.core.widget.WidgetFrame
import one.crinklecod.mochibun.core.widget.WidgetPlacement
import one.crinklecod.mochibun.core.widget.WidgetTargetNavigator
import one.crinklecod.mochibun.core.util.findActivity
import one.crinklecod.mochibun.core.util.loadDownsampledPhotoBitmap
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.WidgetRepository
import one.crinklecod.mochibun.data.repository.currentCardBorderState
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.data.repository.MediaSessionTracker
import one.crinklecod.mochibun.ui.dialogs.BuiltInPickerEntry
import one.crinklecod.mochibun.ui.dialogs.PhotoFrameActionDialog
import one.crinklecod.mochibun.ui.dialogs.StickerPickerDialog
import one.crinklecod.mochibun.ui.dialogs.StickyNoteEditDialog
import one.crinklecod.mochibun.ui.dialogs.PhotoFrameLabelDialog
import one.crinklecod.mochibun.ui.dialogs.WidgetOptionsDialog
import one.crinklecod.mochibun.ui.dialogs.WidgetPickerDialog
import one.crinklecod.mochibun.ui.dialogs.isBackDismissKey
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "WidgetPage"

/** Holding OK this long on a card opens Arrange mode instead of using it. */
private const val WIDGET_LONG_PRESS_MS = 400L

// First-placement heights for the native cards, in 16dp rows. They only need to be close:
// native cards never squeeze below their content (see the canvas slot modifier).
private const val NOW_PLAYING_ROWS = 10
private const val NOW_PLAYING_MINI_ROWS = 4
private const val SYSTEM_MONITOR_ROWS = 14
private const val PHOTO_FRAME_ROWS = 17
private const val STICKY_NOTE_ROWS = 8

/** Space kept on each side of the canvas so focus glows aren't cut by the screen edge. */
private const val CANVAS_SIDE_PADDING_DP = 12

/** Room the top clouds take, and what the Home pill (and, while editing, the pill bar) covers. */
private val SCROLL_TOP_INSET = 44.dp
private val SCROLL_BOTTOM_INSET = 116.dp
private val EDIT_BOTTOM_INSET = 180.dp

enum class WidgetFocusSection {
    HEADER,
    PRESETS,
    QUICK_SETTINGS,
    CANVAS,
    ADD_WIDGET,
    HOME_PILL
}

/** Which Arrange tool the D-pad is driving; BAR means it's choosing one from the pill bar. */
/** Presets bar rows: the built-in presets + ✕, then apply/save pills for each My Layout slot. */
private val PRESET_ROW1 = CollagePreset.entries.size + 1
private val PRESET_ROW2 = WidgetRepository.CUSTOM_PRESET_SLOTS * 2

private enum class ArrangeTool { BAR, MOVE, RESIZE, LAYER }

// ⋯ holds the per-card extras (deco, Now Playing skin, hosted-widget options) so the bar stays
// at five pills, which is all that fits across the 320dp screen
private enum class ArrangeAction(val label: String) {
    MOVE("Move"),
    RESIZE("Resize"),
    LAYER("Layer ▲▼"),
    OPTIONS("⋯"),
    DONE("Done")
}

/**
 * Widgets page (Page 0): a freeform canvas of native cards (Now Playing, System Monitor,
 * Photo Frame) and hosted Android AppWidgets, each placed on a 16dp grid by [WidgetPlacement].
 *
 * Browse: the D-pad moves spatially between cards. OK uses a card; holding OK (or `*`) opens
 * Arrange mode, where a pill bar offers Move / Resize / Layer / Done and the D-pad edits the
 * placement in whole grid steps. Pager swipes are blocked while editing ([onArrangeModeChanged]).
 *
 * Listening for widget updates is tied to MainActivity's onStart/onStop through
 * [MochibunWidgetHost], not to this composable.
 */
@Composable
fun WidgetPage(
    context: Context,
    cardBgColor: Color,
    cardTextColor: Color,
    appActionColor: Color,
    cardRoundness: Dp,
    modifier: Modifier = Modifier,
    isActivePage: Boolean = true,
    onNavigateHome: () -> Unit = {},
    onArrangeModeChanged: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val isSystemMonitorEnabled by PreferencesManager.isSystemMonitorEnabled.collectAsState()
    val isPhotoFrameEnabled by PreferencesManager.isPhotoFrameEnabled.collectAsState()
    val photoFrameUri by PreferencesManager.photoFrameUri.collectAsState()
    val photoFrameStyle by PreferencesManager.photoFrameStyle.collectAsState()
    val photoFrameLabel by PreferencesManager.photoFrameLabel.collectAsState()

    // Sticky Note: mirrors the Quick Memo the Home ticker shows, else its own short note
    val isStickyNoteEnabled by WidgetRepository.stickyNoteEnabled.collectAsState()
    val stickyNoteOwnText by WidgetRepository.stickyNoteText.collectAsState()
    val quickMemoSlots by PreferencesManager.quickMemoSlots.collectAsState()
    val pinnedQuickMemoSlot by PreferencesManager.pinnedQuickMemoSlot.collectAsState()
    val stickyNoteMemoSlot = remember(quickMemoSlots, pinnedQuickMemoSlot) {
        WidgetRepository.stickyNoteSource(quickMemoSlots, pinnedQuickMemoSlot)
    }
    val stickyNoteText = stickyNoteMemoSlot?.let { quickMemoSlots.getOrNull(it - 1) } ?: stickyNoteOwnText
    val customPresets by WidgetRepository.customPresets.collectAsState()

    // Decoded here rather than in the card so scrolling the card off and back doesn't re-decode
    val photoFrameBitmap by produceState<ImageBitmap?>(null, photoFrameUri, isPhotoFrameEnabled) {
        val uri = photoFrameUri
        value = if (uri == null || !isPhotoFrameEnabled) null else withContext(Dispatchers.IO) {
            loadDownsampledPhotoBitmap(context, Uri.parse(uri))?.asImageBitmap()
        }
    }

    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val widgetHost = remember { MochibunWidgetHost.get(context) }
    val widgetSlots by WidgetRepository.slots.collectAsState()
    val providersVersion by widgetHost.providersVersion.collectAsState()
    val slotsById = remember(widgetSlots) { widgetSlots.associateBy { it.appWidgetId } }

    // Provider info per widget; null means the provider is unavailable (uninstalled, updating, disabled)
    val widgetInfos = remember(widgetSlots, providersVersion) {
        widgetSlots.associate { slot ->
            slot.appWidgetId to try {
                appWidgetManager.getAppWidgetInfo(slot.appWidgetId)
            } catch (_: Exception) {
                null
            }
        }
    }

    // Live WidgetFrames by widget ID, registered/unregistered by each card's AndroidView
    val widgetFrames = remember { HashMap<Int, WidgetFrame>() }

    // When providers change, drop widgets whose app was uninstalled and delete orphaned IDs
    LaunchedEffect(providersVersion) {
        if (providersVersion == 0) return@LaunchedEffect // MainActivity already reconciled at startup
        widgetHost.hostIdsOrNull()?.let { hostIds ->
            WidgetRepository.reconcile(context, hostIds, keepPending = true)
                .forEach { widgetHost.deleteAppWidgetIdSafely(it) }
        }
    }

    // MediaSession Tracking on Android 12 (API 31)
    val mediaState by MediaSessionTracker.mediaState.collectAsState()

    DisposableEffect(isActivePage) {
        if (isActivePage) {
            MediaSessionTracker.startTracking(context)
        }
        onDispose {
            MediaSessionTracker.stopTracking()
        }
    }

    // Quick Settings card renders while the toggle is on. Without root, Wi-Fi / Bluetooth taps
    // open the matching Android Settings panel instead of toggling in place.
    val isRootQuickSettingsEnabled by PreferencesManager.isRootQuickSettingsEnabled.collectAsState()
    val isQuickSettingsVisible = isRootQuickSettingsEnabled

    // ── Freeform canvas layout ─────────────────────────────────────────

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val cols = remember(screenWidthDp) { WidgetCanvasLayout.columnsFor(screenWidthDp - 2 * CANVAS_SIDE_PADDING_DP) }
    val stepPx = with(density) { WidgetCanvasLayout.STEP_DP.dp.roundToPx() }
    val canvasWidthPx = cols * stepPx

    // Cards on the canvas right now, in the old list's top-to-bottom order (also the order
    // first-time placements stack in)
    val savedPlacements by WidgetRepository.placements.collectAsState()
    val nowPlayingMini by WidgetRepository.nowPlayingMini.collectAsState()
    // Standalone stickers live only in the layout (unknown kinds from a newer build are skipped)
    val stickerKeys = remember(savedPlacements) {
        WidgetCanvasLayout.stickerKeys(savedPlacements).filter { WidgetCanvasLayout.stickerKindOf(it) != null }
    }
    val canvasKeys = remember(mediaState.isPlaying, isSystemMonitorEnabled, isPhotoFrameEnabled, isStickyNoteEnabled, widgetSlots, stickerKeys) {
        buildList {
            if (mediaState.isPlaying) add(WidgetCanvasLayout.KEY_NOW_PLAYING)
            if (isSystemMonitorEnabled) add(WidgetCanvasLayout.KEY_SYSTEM_MONITOR)
            if (isPhotoFrameEnabled) add(WidgetCanvasLayout.KEY_PHOTO_FRAME)
            if (isStickyNoteEnabled) add(WidgetCanvasLayout.KEY_STICKY_NOTE)
            widgetSlots.forEach { add(WidgetCanvasLayout.appWidgetKey(it.appWidgetId)) }
            addAll(stickerKeys)
        }
    }
    // Cards (not stickers) the presets arrange and the Presets pill needs at least one of
    val cardKeys = remember(canvasKeys) { canvasKeys.filterNot(WidgetCanvasLayout::isStickerKey) }
    // Cards without a saved spot (an upgrade from the list layout, a new widget, Now Playing
    // appearing for the first time) are stacked into the first free gap
    val layout = remember(savedPlacements, canvasKeys, widgetInfos, cols, stepPx, nowPlayingMini) {
        WidgetCanvasLayout.withDefaults(
            savedPlacements,
            canvasKeys.map { key ->
                key to when {
                    key == WidgetCanvasLayout.KEY_NOW_PLAYING -> if (nowPlayingMini) NOW_PLAYING_MINI_ROWS else NOW_PLAYING_ROWS
                    key == WidgetCanvasLayout.KEY_SYSTEM_MONITOR -> SYSTEM_MONITOR_ROWS
                    key == WidgetCanvasLayout.KEY_PHOTO_FRAME -> PHOTO_FRAME_ROWS
                    key == WidgetCanvasLayout.KEY_STICKY_NOTE -> STICKY_NOTE_ROWS
                    WidgetCanvasLayout.isStickerKey(key) -> WidgetCanvasLayout.STICKER_SIZE
                    else -> defaultAppWidgetRows(
                        widgetInfos[WidgetCanvasLayout.appWidgetIdOf(key)],
                        canvasWidthPx,
                        stepPx,
                        density
                    )
                }
            },
            cols
        )
    }
    LaunchedEffect(layout) {
        if (layout !== savedPlacements) WidgetRepository.setPlacements(context, layout)
    }
    // Saved spots refitted to this screen's width (a layout from a wider screen stays usable)
    val placed = remember(layout, cols) { layout.mapValues { WidgetCanvasLayout.clampFor(it.key, it.value, cols) } }

    // Native cards grow past their slot rather than squash their content; their real height
    // lets resize stop at the content and lets the canvas leave room below them
    val nativeHeightsPx = remember { mutableStateMapOf<String, Int>() }
    val scrollState = rememberScrollState()

    // D-Pad Focus State Management
    fun topCanvasKey(): String? = WidgetCanvasLayout.readingOrder(placed, canvasKeys).firstOrNull()

    fun bottomCanvasKey(): String? = canvasKeys.filter { it in placed }.maxWithOrNull(
        compareBy<String>({ placed.getValue(it).bottom }, { -placed.getValue(it).gridX })
    )

    // Quick Settings drawer under the header pill, collapsed by default
    var isQuickSettingsExpanded by rememberSaveable { mutableStateOf(false) }

    var focusSection by remember {
        mutableStateOf(
            when {
                isQuickSettingsVisible -> WidgetFocusSection.HEADER
                topCanvasKey() != null -> WidgetFocusSection.CANVAS
                else -> WidgetFocusSection.ADD_WIDGET
            }
        )
    }
    var focusedKey by remember { mutableStateOf(topCanvasKey()) }
    var nowPlayingCol by remember { mutableIntStateOf(1) } // 0 = Prev, 1 = Play/Pause, 2 = Next
    var quickSettingsIndex by remember { mutableIntStateOf(0) } // 0 Wi-Fi, 1 BT, 2 Torch, 3 Sound
    var quickSettingsRefresh by remember { mutableIntStateOf(0) }
    var dpadScrollTick by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }
    var centerDownAt by remember { mutableLongStateOf(0L) }
    var centerLongPressFired by remember { mutableStateOf(false) }
    var systemMonitorRefreshTrigger by remember { mutableIntStateOf(0) }

    // Hosted widget state
    var interactingWidgetId by remember { mutableStateOf<Int?>(null) }
    // Card whose ⋯ options dialog is open (any card key; hosted widgets also via their corner ⋯)
    var optionsKey by remember { mutableStateOf<String?>(null) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showPhotoFrameDialog by remember { mutableStateOf(false) }
    var showPhotoLabelDialog by remember { mutableStateOf(false) }
    var showStickyEditor by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    // A card just added from the picker: focused as soon as it reaches the canvas
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }
    val anyWidgetDialogOpen = optionsKey != null || showWidgetPicker ||
        showPhotoFrameDialog || showPhotoLabelDialog || showStickyEditor || showStickerPicker

    // Arrange mode: [draft] is the live Move/Resize placement, saved when the tool closes, so
    // a held D-pad key doesn't write preferences on every repeat
    var arrangeKey by remember { mutableStateOf<String?>(null) }
    var arrangeTool by remember { mutableStateOf(ArrangeTool.BAR) }
    var arrangeBarIndex by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<WidgetPlacement?>(null) }
    var presetsOpen by remember { mutableStateOf(false) }
    // Presets bar: row 1 = built-in presets + ✕ (0 until PRESET_ROW1), row 2 = My Layout slots
    // as [apply 1][save 1][apply 2][save 2] (PRESET_ROW1 onward)
    var presetIndex by remember { mutableIntStateOf(0) }
    val isEditing = arrangeKey != null || presetsOpen

    val currentOnArrangeModeChanged by rememberUpdatedState(onArrangeModeChanged)
    LaunchedEffect(isEditing) { currentOnArrangeModeChanged(isEditing) }
    DisposableEffect(Unit) { onDispose { currentOnArrangeModeChanged(false) } }

    fun placementOf(key: String?): WidgetPlacement? {
        if (key == null) return null
        return if (key == arrangeKey) draft ?: placed[key] else placed[key]
    }

    fun focusCanvas(key: String) {
        focusSection = WidgetFocusSection.CANVAS
        focusedKey = key
        if (key == WidgetCanvasLayout.KEY_NOW_PLAYING) nowPlayingCol = 1
    }

    fun spatialNeighbour(from: String, direction: NavDirection): String? {
        val keys = canvasKeys.filter { it in placed }
        val index = keys.indexOf(from)
        if (index < 0) return null
        val next = WidgetTargetNavigator.findNext(keys.map { placed.getValue(it).toBounds() }, index, direction)
        return keys.getOrNull(next)
    }

    /** UP off the top card: the Quick Settings grid when open, else the header row. */
    fun focusAboveCanvas() {
        when {
            isQuickSettingsVisible && isQuickSettingsExpanded -> {
                focusSection = WidgetFocusSection.QUICK_SETTINGS
                quickSettingsIndex = quickSettingsIndex.coerceIn(0, 3)
            }
            isQuickSettingsVisible -> focusSection = WidgetFocusSection.HEADER
            else -> HapticManager.performBoundaryHaptic(context) // Header is decorative without QS
        }
    }

    fun focusBelowHeader() {
        if (isQuickSettingsVisible && isQuickSettingsExpanded) {
            focusSection = WidgetFocusSection.QUICK_SETTINGS
            quickSettingsIndex = 0
        } else {
            topCanvasKey()?.let { focusCanvas(it) } ?: run { focusSection = WidgetFocusSection.ADD_WIDGET }
        }
    }

    fun resetFocus() {
        if (isQuickSettingsVisible) {
            focusSection = WidgetFocusSection.HEADER
            focusedKey = topCanvasKey()
        } else {
            topCanvasKey()?.let { focusCanvas(it) } ?: run { focusSection = WidgetFocusSection.ADD_WIDGET }
        }
        nowPlayingCol = 1
    }

    fun endWidgetInteraction() {
        interactingWidgetId?.let { widgetFrames[it]?.endInteraction() }
        interactingWidgetId = null
    }

    fun focusWidget(appWidgetId: Int) {
        focusCanvas(WidgetCanvasLayout.appWidgetKey(appWidgetId))
        dpadScrollTick++
    }

    // ── Arrange mode ───────────────────────────────────────────────────


    /** Saves the Move/Resize draft. A native card's slot grows to cover what it really drew. */
    fun commitDraft() {
        val key = arrangeKey ?: return
        var p = draft ?: return
        draft = null
        if (WidgetCanvasLayout.appWidgetIdOf(key) == null) {
            val drawnRows = nativeHeightsPx[key]?.let { WidgetCanvasLayout.rowsFor(it, stepPx) } ?: 0
            if (drawnRows > p.gridH) p = WidgetCanvasLayout.clamp(p.copy(gridH = drawnRows), cols)
        }
        WidgetRepository.setPlacements(context, LinkedHashMap(layout).apply { put(key, p) })
    }

    fun enterArrange(key: String) {
        if (key !in placed) return
        endWidgetInteraction()
        presetsOpen = false
        focusCanvas(key)
        arrangeKey = key
        arrangeTool = ArrangeTool.BAR
        arrangeBarIndex = 0
        draft = null
        sawCenterDown = false
        SoundEffectManager.play(context, SoundEffect.SELECT)
        dpadScrollTick++
    }

    fun exitArrange() {
        commitDraft()
        arrangeKey = null
        arrangeTool = ArrangeTool.BAR
    }

    fun finishArrangeTool() {
        commitDraft()
        arrangeTool = ArrangeTool.BAR
    }

    fun cycleDeco(key: String) {
        val current = layout[key] ?: return
        WidgetRepository.setPlacements(
            context,
            LinkedHashMap(layout).apply { put(key, current.copy(deco = current.deco.next())) }
        )
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    /** Swaps Now Playing's skin and refits its slot height to the new skin. */
    fun toggleNowPlayingSkin() {
        val mini = !nowPlayingMini
        WidgetRepository.setNowPlayingMini(context, mini)
        nativeHeightsPx.remove(WidgetCanvasLayout.KEY_NOW_PLAYING)
        layout[WidgetCanvasLayout.KEY_NOW_PLAYING]?.let { p ->
            val rows = if (mini) NOW_PLAYING_MINI_ROWS else NOW_PLAYING_ROWS
            WidgetRepository.setPlacements(
                context,
                LinkedHashMap(layout).apply { put(WidgetCanvasLayout.KEY_NOW_PLAYING, WidgetCanvasLayout.clamp(p.copy(gridH = rows), cols)) }
            )
        }
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    fun applyPreset(preset: CollagePreset) {
        val updated = WidgetCanvasLayout.applyPreset(layout, cardKeys, preset, cols)
        WidgetRepository.setPlacements(context, updated)
        presetsOpen = false
        SoundEffectManager.play(context, SoundEffect.SELECT)
        WidgetCanvasLayout.readingOrder(updated, cardKeys).firstOrNull()?.let { focusCanvas(it) }
        scope.launch { scrollState.animateScrollTo(0) }
    }

    fun applyCustomPreset(slot: Int) {
        val snapshot = customPresets.getOrNull(slot - 1)
        if (snapshot == null) {
            HapticManager.performBoundaryHaptic(context)
            Toast.makeText(context, "My Layout $slot is empty · save one first ♡", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = WidgetCanvasLayout.applySnapshot(layout, snapshot, cols)
        WidgetRepository.setPlacements(context, updated)
        presetsOpen = false
        SoundEffectManager.play(context, SoundEffect.SELECT)
        WidgetCanvasLayout.readingOrder(updated, cardKeys).firstOrNull()?.let { focusCanvas(it) }
        scope.launch { scrollState.animateScrollTo(0) }
    }

    /** Captures every card's spot, size, layer and deco plus the stickers. The bar stays open. */
    fun saveCustomPreset(slot: Int) {
        WidgetRepository.saveCustomPreset(context, slot, layout)
        SoundEffectManager.play(context, SoundEffect.SELECT)
        Toast.makeText(context, "Saved as My Layout $slot ♡", Toast.LENGTH_SHORT).show()
    }

    /** Slot-sized footprint of each card on screen, grown to what native cards really drew. */
    fun occupiedFootprints(): List<WidgetPlacement> = canvasKeys.mapNotNull { key ->
        val p = placed[key] ?: return@mapNotNull null
        val drawnRows = nativeHeightsPx[key]?.let { WidgetCanvasLayout.rowsFor(it, stepPx) } ?: 0
        if (drawnRows > p.gridH) p.copy(gridH = drawnRows) else p
    }

    fun addSticker(kind: StickerKind) {
        showStickerPicker = false
        val added = WidgetCanvasLayout.addSticker(layout, kind, cols, occupiedFootprints())
        if (added == null) {
            HapticManager.performBoundaryHaptic(context)
            Toast.makeText(context, "Sticker sheet is full (${WidgetCanvasLayout.MAX_STICKERS}) ♡", Toast.LENGTH_SHORT).show()
            return
        }
        WidgetRepository.setPlacements(context, added.first)
        pendingFocusKey = added.second
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    fun removeSticker(key: String) {
        if (key !in layout) return
        WidgetRepository.setPlacements(context, LinkedHashMap(layout).apply { remove(key) })
        SoundEffectManager.play(context, SoundEffect.BACK)
    }

    /** Cycles a sticker to the next design in place; its key changes, so focus follows it. */
    fun swapSticker(key: String) {
        val kind = WidgetCanvasLayout.stickerKindOf(key) ?: return
        val (updated, newKey) = WidgetCanvasLayout.swapSticker(layout, key, kind.next()) ?: return
        if (arrangeKey == key) {
            commitDraft()
            arrangeKey = newKey
        }
        if (focusedKey == key) focusedKey = newKey
        if (optionsKey == key) optionsKey = newKey
        WidgetRepository.setPlacements(context, updated)
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    fun showStickyNote() {
        showWidgetPicker = false
        WidgetRepository.setStickyNoteEnabled(context, true)
        pendingFocusKey = WidgetCanvasLayout.KEY_STICKY_NOTE
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    fun saveStickyNote(text: String) {
        val slot = stickyNoteMemoSlot
        if (slot != null) {
            PreferencesManager.setQuickMemoSlotText(context, slot, text)
        } else {
            WidgetRepository.setStickyNoteText(context, text)
        }
    }

    // ── Add-widget flow: allocate -> bind (permission) -> configure -> save ──
    // The in-flight ID is persisted so startup cleanup can tell it apart from orphans.

    fun cancelPendingWidget(id: Int) {
        widgetHost.deleteAppWidgetIdSafely(id)
        WidgetRepository.setPendingWidgetId(context, null)
    }

    fun finishWidget(id: Int) {
        WidgetRepository.setPendingWidgetId(context, null)
        val provider = try {
            appWidgetManager.getAppWidgetInfo(id)?.provider?.flattenToString()
        } catch (_: Exception) {
            null
        }
        WidgetRepository.add(context, id, provider)
        focusWidget(id)
    }

    fun configureIntent(id: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            // No FLAG_ACTIVITY_NEW_TASK: with it, startActivityForResult returns RESULT_CANCELED at once
            component = info.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }

    /** Fallback for config activities that aren't exported: the host starts them via an IntentSender. */
    fun startConfigureViaHost(id: Int): Boolean {
        val activity = context.findActivity() ?: return false
        return try {
            widgetHost.startAppWidgetConfigureActivityForResult(activity, id, 0, 0, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Host could not open config for id=$id", e)
            false
        }
    }

    val configLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = WidgetRepository.pendingWidgetId(context) ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            finishWidget(id)
        } else {
            Log.d(TAG, "Config cancelled for new widget id=$id")
            cancelPendingWidget(id)
        }
    }

    // Reconfiguring an existing widget: the result doesn't matter, the widget stays either way
    val reconfigureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun continueAfterBind(id: Int, retriesLeft: Int = 1) {
        val info = try {
            appWidgetManager.getAppWidgetInfo(id)
        } catch (_: Exception) {
            null
        }
        if (info == null) {
            if (retriesLeft > 0) {
                scope.launch {
                    delay(300)
                    continueAfterBind(id, retriesLeft - 1)
                }
            } else {
                Log.e(TAG, "Widget id=$id is not bound to a provider")
                cancelPendingWidget(id)
            }
            return
        }
        // widgetFeatures is Android 9+, and CONFIGURATION_OPTIONAL is only honoured from Android 12
        val features = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.widgetFeatures else 0
        val configOptional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0 &&
                (features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
        if (info.configure == null || configOptional) {
            finishWidget(id)
            return
        }
        try {
            configLauncher.launch(configureIntent(id, info))
        } catch (e: Exception) {
            Log.w(TAG, "Direct config launch failed for id=$id, trying host", e)
            // We can't observe the result this way, so keep the widget
            if (startConfigureViaHost(id)) finishWidget(id) else cancelPendingWidget(id)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = WidgetRepository.pendingWidgetId(context) ?: return@rememberLauncherForActivityResult
        val bound = result.resultCode == Activity.RESULT_OK ||
            (try { appWidgetManager.getAppWidgetInfo(id) } catch (_: Exception) { null }) != null
        Log.d(TAG, "Bind result=${result.resultCode}, id=$id, bound=$bound")
        if (bound) continueAfterBind(id) else cancelPendingWidget(id)
    }

    fun addWidget(info: AppWidgetProviderInfo) {
        showWidgetPicker = false
        val id = try {
            widgetHost.allocateAppWidgetId()
        } catch (e: Exception) {
            Log.e(TAG, "allocateAppWidgetId failed", e)
            Toast.makeText(context, "Couldn't add that widget ♡", Toast.LENGTH_SHORT).show()
            return
        }
        WidgetRepository.setPendingWidgetId(context, id)
        val bound = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        } catch (e: Exception) {
            Log.w(TAG, "bindAppWidgetIdIfAllowed failed for id=$id", e)
            false
        }
        if (bound) {
            continueAfterBind(id)
            return
        }
        // Not allowed yet: ask the user once (the system dialog offers "always allow")
        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        }
        try {
            bindLauncher.launch(bindIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Bind screen failed for id=$id", e)
            cancelPendingWidget(id)
        }
    }

    fun reconfigureWidget(id: Int) {
        val info = widgetInfos[id] ?: return
        if (info.configure == null) return
        try {
            reconfigureLauncher.launch(configureIntent(id, info))
        } catch (e: Exception) {
            Log.w(TAG, "Direct reconfigure failed for id=$id, trying host", e)
            if (!startConfigureViaHost(id)) {
                Toast.makeText(context, "This widget can't be configured ♡", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeWidget(id: Int) {
        if (interactingWidgetId == id) endWidgetInteraction()
        widgetFrames.remove(id)
        widgetHost.deleteAppWidgetIdSafely(id)
        WidgetRepository.remove(context, id)
        // Focus falls back to another card via the canvasKeys effect below
    }

    /**
     * OK on a widget card. One control: tap it. Several: walk them with the D-pad.
     * None: hint at the options menu.
     */
    fun activateWidget(id: Int) {
        if (widgetInfos[id] == null) {
            optionsKey = WidgetCanvasLayout.appWidgetKey(id)
            return
        }
        val frame = widgetFrames[id] ?: return
        when (frame.beginInteraction()) {
            0 -> {
                frame.endInteraction()
                Toast.makeText(context, "This widget has no buttons ♡ Hold OK to arrange", Toast.LENGTH_SHORT).show()
            }
            1 -> {
                frame.clickSelection()
                frame.endInteraction()
            }
            else -> interactingWidgetId = id
        }
    }

    /** Drops our read grant on a photo the frame no longer shows, unless the wallpaper still uses it. */
    fun releasePhotoFrameUri(uri: String?) {
        if (uri == null || uri == PreferencesManager.getWallpaperUri(context)) return
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "No persistable permission for photo $uri", e)
            }
            val old = PreferencesManager.photoFrameUri.value
            PreferencesManager.setPhotoFrameUri(context, uri.toString())
            if (old != uri.toString()) releasePhotoFrameUri(old)
        }
    }

    val pageFocusRequester = remember { FocusRequester() }

    val navigateHomeAndReset: () -> Unit = {
        endWidgetInteraction()
        if (arrangeKey != null) exitArrange()
        presetsOpen = false
        isQuickSettingsExpanded = false
        resetFocus()
        quickSettingsIndex = 0
        sawCenterDown = false
        onNavigateHome()
    }

    val openWidgetPicker: () -> Unit = {
        endWidgetInteraction()
        focusSection = WidgetFocusSection.ADD_WIDGET
        showWidgetPicker = true
    }

    // Graceful D-pad fallback when a card leaves the canvas (playback stops, a card is toggled
    // off, a widget is removed or its app uninstalled)
    LaunchedEffect(canvasKeys) {
        pendingFocusKey?.let { pending ->
            if (pending in canvasKeys) {
                pendingFocusKey = null
                focusCanvas(pending)
                dpadScrollTick++
            }
        }
        if (WidgetCanvasLayout.KEY_STICKY_NOTE !in canvasKeys) showStickyEditor = false
        val arranging = arrangeKey
        if (arranging != null && arranging !in canvasKeys) {
            exitArrange()
        }
        if (focusedKey !in canvasKeys) {
            val fallback = topCanvasKey()
            focusedKey = fallback
            if (focusSection == WidgetFocusSection.CANVAS) {
                if (fallback != null) focusCanvas(fallback) else focusSection = WidgetFocusSection.ADD_WIDGET
            }
        }
        if (!isPhotoFrameEnabled) {
            showPhotoFrameDialog = false
            showPhotoLabelDialog = false
        }
        if (interactingWidgetId != null && widgetSlots.none { it.appWidgetId == interactingWidgetId }) {
            interactingWidgetId = null
        }
    }

    // Placement of the canvas and the Add pill inside the scrolling column, for keeping the
    // D-pad target on screen. Only read by the scroll effect, so updating them doesn't recompose.
    val canvasTopPx = remember { mutableIntStateOf(0) }
    val addPillTopPx = remember { mutableIntStateOf(0) }
    val addPillHeightPx = remember { mutableIntStateOf(0) }

    // Keep the D-pad target fully visible between the top clouds and the bottom pill(s)
    LaunchedEffect(dpadScrollTick) {
        if (dpadScrollTick == 0) return@LaunchedEffect
        val top: Int
        val bottom: Int
        when (focusSection) {
            WidgetFocusSection.HEADER,
            WidgetFocusSection.QUICK_SETTINGS -> {
                scrollState.animateScrollTo(0)
                return@LaunchedEffect
            }
            WidgetFocusSection.CANVAS -> {
                val key = focusedKey
                val p = placementOf(key) ?: return@LaunchedEffect
                top = canvasTopPx.intValue + p.gridY * stepPx
                bottom = top + max(p.gridH * stepPx, nativeHeightsPx[key] ?: 0)
            }
            WidgetFocusSection.ADD_WIDGET,
            WidgetFocusSection.PRESETS -> {
                top = addPillTopPx.intValue
                bottom = top + addPillHeightPx.intValue
            }
            WidgetFocusSection.HOME_PILL -> return@LaunchedEffect // Fixed overlay
        }
        val topInset = with(density) { SCROLL_TOP_INSET.roundToPx() }
        val bottomInset = with(density) { (if (isEditing) EDIT_BOTTOM_INSET else SCROLL_BOTTOM_INSET).roundToPx() }
        val current = scrollState.value
        var target = current
        if (bottom + bottomInset > current + scrollState.viewportSize) {
            target = bottom + bottomInset - scrollState.viewportSize
        }
        // A card taller than the screen shows its top
        if (top - topInset < target) target = top - topInset
        target = target.coerceIn(0, scrollState.maxValue)
        if (target == current) return@LaunchedEffect
        // Held-key nudges jump instead of queueing a scroll animation per repeat
        if (arrangeKey != null) scrollState.scrollTo(target) else scrollState.animateScrollTo(target)
    }

    // Re-take key focus when the page becomes active and after a widget dialog closes
    LaunchedEffect(isActivePage, anyWidgetDialogOpen) {
        if (isActivePage) {
            if (anyWidgetDialogOpen) return@LaunchedEffect
            repeat(6) {
                try {
                    pageFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        } else {
            endWidgetInteraction()
            if (arrangeKey != null) exitArrange()
            presetsOpen = false
            optionsKey = null
            showWidgetPicker = false
            showPhotoFrameDialog = false
            showPhotoLabelDialog = false
            showStickyEditor = false
            showStickerPicker = false
            isQuickSettingsExpanded = false
            resetFocus()
            quickSettingsIndex = 0
            sawCenterDown = false
        }
    }

    /** OK on a canvas card. */
    fun activateCard(key: String) {
        when (key) {
            WidgetCanvasLayout.KEY_NOW_PLAYING -> when (nowPlayingCol) {
                0 -> MediaSessionTracker.skipToPrevious()
                1 -> MediaSessionTracker.togglePlayPause()
                2 -> MediaSessionTracker.skipToNext()
            }
            WidgetCanvasLayout.KEY_SYSTEM_MONITOR -> systemMonitorRefreshTrigger++
            WidgetCanvasLayout.KEY_PHOTO_FRAME -> showPhotoFrameDialog = true
            WidgetCanvasLayout.KEY_STICKY_NOTE -> showStickyEditor = true
            else -> {
                if (WidgetCanvasLayout.isStickerKey(key)) {
                    optionsKey = key
                    return
                }
                val id = WidgetCanvasLayout.appWidgetIdOf(key) ?: return
                val interacting = interactingWidgetId
                if (interacting == id) widgetFrames[id]?.clickSelection() else activateWidget(id)
            }
        }
    }

    fun runArrangeAction(action: ArrangeAction) {
        val key = arrangeKey ?: return
        when (action) {
            ArrangeAction.MOVE, ArrangeAction.RESIZE -> {
                if (action == ArrangeAction.RESIZE && WidgetCanvasLayout.isStickerKey(key)) {
                    HapticManager.performBoundaryHaptic(context)
                    Toast.makeText(context, "Stickers keep their size ♡", Toast.LENGTH_SHORT).show()
                    return
                }
                arrangeTool = if (action == ArrangeAction.MOVE) ArrangeTool.MOVE else ArrangeTool.RESIZE
                draft = placed[key]
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            ArrangeAction.LAYER -> {
                arrangeTool = ArrangeTool.LAYER
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            ArrangeAction.OPTIONS -> {
                // Arrange stays active underneath, so deco changes show live behind the dialog
                // and closing it lands back on the pill bar
                optionsKey = key
            }
            ArrangeAction.DONE -> {
                exitArrange()
                SoundEffectManager.play(context, SoundEffect.BACK)
            }
        }
    }

    /**
     * Arrange mode owns every key while active (no page swipes, no card activation) so a nudge
     * can never leak into the pager or a widget. HOME is handled by MainLauncher before this.
     */
    fun handleArrangeKey(native: KeyEvent, type: KeyEventType): Boolean {
        val key = arrangeKey ?: return false
        val actions = ArrangeAction.entries

        if (isBackDismissKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_F4) {
            if (type == KeyEventType.KeyUp) {
                if (arrangeTool != ArrangeTool.BAR) finishArrangeTool() else exitArrange()
                SoundEffectManager.play(context, SoundEffect.BACK)
            }
            return true
        }
        if (isCenterActivateKey(native.keyCode)) {
            if (type == KeyEventType.KeyDown && native.repeatCount == 0) {
                sawCenterDown = true
            } else if (type == KeyEventType.KeyUp && sawCenterDown) {
                // Only a fresh press counts: the release of the hold that opened Arrange is ignored
                sawCenterDown = false
                if (arrangeTool == ArrangeTool.BAR) {
                    actions.getOrNull(arrangeBarIndex)?.let { runArrangeAction(it) }
                } else {
                    finishArrangeTool()
                    SoundEffectManager.play(context, SoundEffect.SELECT)
                }
            }
            return true
        }
        if (type != KeyEventType.KeyDown) return true

        val direction = when (native.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> NavDirection.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> NavDirection.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> NavDirection.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> NavDirection.RIGHT
            else -> null
        }
        when (arrangeTool) {
            ArrangeTool.BAR -> {
                if (native.keyCode == KeyEvent.KEYCODE_STAR && native.repeatCount == 0) {
                    exitArrange()
                    SoundEffectManager.play(context, SoundEffect.BACK)
                    return true
                }
                val delta = when (direction) {
                    NavDirection.LEFT -> -1
                    NavDirection.RIGHT -> 1
                    else -> return true
                }
                val next = arrangeBarIndex + delta
                if (next in actions.indices) {
                    arrangeBarIndex = next
                    SoundEffectManager.play(context, SoundEffect.NAV)
                } else {
                    HapticManager.performBoundaryHaptic(context)
                }
            }
            ArrangeTool.MOVE, ArrangeTool.RESIZE -> {
                val current = draft ?: placed[key] ?: return true
                val (dx, dy) = when (direction) {
                    NavDirection.UP -> 0 to -1
                    NavDirection.DOWN -> 0 to 1
                    NavDirection.LEFT -> -1 to 0
                    NavDirection.RIGHT -> 1 to 0
                    null -> return true
                }
                val next = if (arrangeTool == ArrangeTool.MOVE) {
                    WidgetCanvasLayout.move(current, dx, dy, cols, key)
                } else if (WidgetCanvasLayout.isStickerKey(key)) {
                    null
                } else {
                    // A native card already taller than its slot is at its content height
                    val isNative = WidgetCanvasLayout.appWidgetIdOf(key) == null
                    val drawnPx = nativeHeightsPx[key] ?: 0
                    val minH = if (isNative && drawnPx > current.gridH * stepPx) current.gridH else WidgetCanvasLayout.MIN_H
                    WidgetCanvasLayout.resize(current, dx, dy, cols, minH)
                }
                if (next == null) {
                    HapticManager.performBoundaryHaptic(context)
                } else {
                    draft = next
                    SoundEffectManager.play(context, SoundEffect.CLICK)
                    dpadScrollTick++
                }
            }
            ArrangeTool.LAYER -> {
                val forward = when {
                    direction == NavDirection.UP || native.keyCode == KeyEvent.KEYCODE_2 -> true
                    direction == NavDirection.DOWN || native.keyCode == KeyEvent.KEYCODE_8 -> false
                    else -> return true
                }
                val updated = WidgetCanvasLayout.shiftLayer(layout, key, forward, canvasKeys)
                if (updated == null) {
                    HapticManager.performBoundaryHaptic(context)
                } else {
                    WidgetRepository.setPlacements(context, updated)
                    SoundEffectManager.play(context, SoundEffect.SELECT)
                }
            }
        }
        return true
    }

    fun runPresetPill(index: Int) {
        if (index < PRESET_ROW1) {
            val preset = CollagePreset.entries.getOrNull(index)
            if (preset != null) applyPreset(preset) else presetsOpen = false
            return
        }
        val pill = index - PRESET_ROW1
        val slot = pill / 2 + 1
        if (pill % 2 == 0) applyCustomPreset(slot) else saveCustomPreset(slot)
    }

    fun handlePresetsKey(native: KeyEvent, type: KeyEventType): Boolean {
        if (isBackDismissKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_F4) {
            if (type == KeyEventType.KeyUp) {
                presetsOpen = false
                SoundEffectManager.play(context, SoundEffect.BACK)
            }
            return true
        }
        if (isCenterActivateKey(native.keyCode)) {
            if (type == KeyEventType.KeyDown && native.repeatCount == 0) {
                sawCenterDown = true
            } else if (type == KeyEventType.KeyUp && sawCenterDown) {
                sawCenterDown = false
                runPresetPill(presetIndex)
            }
            return true
        }
        if (type != KeyEventType.KeyDown) return true
        val inRow2 = presetIndex >= PRESET_ROW1
        val rowStart = if (inRow2) PRESET_ROW1 else 0
        val rowSize = if (inRow2) PRESET_ROW2 else PRESET_ROW1
        val col = presetIndex - rowStart
        val next = when (native.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (col > 0) presetIndex - 1 else null
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (col < rowSize - 1) presetIndex + 1 else null
            KeyEvent.KEYCODE_DPAD_UP -> if (inRow2) col.coerceAtMost(PRESET_ROW1 - 1) else null
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!inRow2) PRESET_ROW1 + col.coerceAtMost(PRESET_ROW2 - 1) else null
            else -> return true
        }
        if (next != null) {
            presetIndex = next
            SoundEffectManager.play(context, SoundEffect.NAV)
        } else {
            HapticManager.performBoundaryHaptic(context)
        }
        return true
    }

    fun openPresets() {
        if (cardKeys.none { it in placed }) {
            HapticManager.performBoundaryHaptic(context)
            Toast.makeText(context, "Add a widget first ♡", Toast.LENGTH_SHORT).show()
            return
        }
        endWidgetInteraction()
        presetsOpen = true
        presetIndex = 0
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent) // Let wallpaper + sticker overlay show through
            .focusRequester(pageFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (!isActivePage) return@onPreviewKeyEvent false

                // With Soft-Key Nav on, MochibunKeyHook re-sends BACK to the activity window, so it
                // lands here even while one of this page's dialogs has focus (MainActivity only
                // reroutes it for launcher-level modals). Close the dialog instead of letting it
                // exit Arrange or leave the page behind the dialog's back.
                if (anyWidgetDialogOpen && (isBackDismissKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_F4)) {
                    if (event.type == KeyEventType.KeyUp) {
                        when {
                            // Closing the editor disposes it, which saves the draft
                            showStickyEditor -> showStickyEditor = false
                            showStickerPicker -> showStickerPicker = false
                            showPhotoLabelDialog -> showPhotoLabelDialog = false
                            showPhotoFrameDialog -> showPhotoFrameDialog = false
                            showWidgetPicker -> showWidgetPicker = false
                            else -> optionsKey = null
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (arrangeKey != null) return@onPreviewKeyEvent handleArrangeKey(native, event.type)
                if (presetsOpen) return@onPreviewKeyEvent handlePresetsKey(native, event.type)

                if (isBackDismissKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_F4) {
                    if (event.type == KeyEventType.KeyUp) {
                        if (interactingWidgetId != null) {
                            endWidgetInteraction()
                        } else if (isQuickSettingsExpanded) {
                            isQuickSettingsExpanded = false
                            focusSection = WidgetFocusSection.HEADER
                            dpadScrollTick++
                        } else {
                            navigateHomeAndReset()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        centerDownAt = native.eventTime
                        centerLongPressFired = false
                    } else if (event.type == KeyEventType.KeyDown) {
                        // Held OK on a card: Arrange mode
                        val heldMs = native.eventTime - centerDownAt
                        val key = focusedKey
                        if (sawCenterDown && !centerLongPressFired &&
                            focusSection == WidgetFocusSection.CANVAS && key != null &&
                            heldMs >= WIDGET_LONG_PRESS_MS
                        ) {
                            centerLongPressFired = true
                            enterArrange(key)
                        }
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusSection) {
                            WidgetFocusSection.HEADER -> {
                                if (isQuickSettingsVisible) isQuickSettingsExpanded = !isQuickSettingsExpanded
                            }
                            WidgetFocusSection.PRESETS -> openPresets()
                            WidgetFocusSection.QUICK_SETTINGS -> {
                                performQuickSettingsAction(context, quickSettingsIndex)
                                quickSettingsRefresh++
                            }
                            WidgetFocusSection.CANVAS -> focusedKey?.let { activateCard(it) }
                            WidgetFocusSection.ADD_WIDGET -> openWidgetPicker()
                            WidgetFocusSection.HOME_PILL -> navigateHomeAndReset()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (native.keyCode == KeyEvent.KEYCODE_STAR) {
                    val key = focusedKey
                    if (native.repeatCount == 0 && focusSection == WidgetFocusSection.CANVAS && key != null) {
                        enterArrange(key)
                    }
                    return@onPreviewKeyEvent true
                }

                // Inside a widget: the D-pad walks its buttons until it runs off the top/bottom
                val interacting = interactingWidgetId
                if (interacting != null) {
                    val direction = when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> NavDirection.UP
                        KeyEvent.KEYCODE_DPAD_DOWN -> NavDirection.DOWN
                        KeyEvent.KEYCODE_DPAD_LEFT -> NavDirection.LEFT
                        KeyEvent.KEYCODE_DPAD_RIGHT -> NavDirection.RIGHT
                        else -> null
                    }
                    if (direction != null) {
                        val frame = widgetFrames[interacting]
                        if (frame != null && frame.moveSelection(direction)) {
                            return@onPreviewKeyEvent true
                        }
                        if (direction == NavDirection.LEFT || direction == NavDirection.RIGHT) {
                            HapticManager.performBoundaryHaptic(context)
                            return@onPreviewKeyEvent true
                        }
                        // UP/DOWN past the edge: leave the widget and move to the next card
                        endWidgetInteraction()
                    }
                }

                val prevSection = focusSection
                val prevKey = focusedKey
                val onCanvas = focusSection == WidgetFocusSection.CANVAS
                val canvasKey = focusedKey
                val handled = when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        when (focusSection) {
                            WidgetFocusSection.HEADER -> HapticManager.performBoundaryHaptic(context)
                            WidgetFocusSection.QUICK_SETTINGS -> {
                                val move = QuickSettingsNav.up(quickSettingsIndex)
                                if (move is QuickSettingsNav.Move.To) quickSettingsIndex = move.index
                                else focusSection = WidgetFocusSection.HEADER
                            }
                            WidgetFocusSection.CANVAS -> {
                                val next = canvasKey?.let { spatialNeighbour(it, NavDirection.UP) }
                                if (next != null) focusCanvas(next) else focusAboveCanvas()
                            }
                            WidgetFocusSection.ADD_WIDGET,
                            WidgetFocusSection.PRESETS -> {
                                bottomCanvasKey()?.let { focusCanvas(it) } ?: focusAboveCanvas()
                            }
                            WidgetFocusSection.HOME_PILL -> focusSection = WidgetFocusSection.ADD_WIDGET
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when (focusSection) {
                            WidgetFocusSection.HEADER -> focusBelowHeader()
                            WidgetFocusSection.QUICK_SETTINGS -> {
                                when (val move = QuickSettingsNav.down(quickSettingsIndex)) {
                                    is QuickSettingsNav.Move.To -> quickSettingsIndex = move.index
                                    else -> topCanvasKey()?.let { focusCanvas(it) }
                                        ?: run { focusSection = WidgetFocusSection.ADD_WIDGET }
                                }
                            }
                            WidgetFocusSection.CANVAS -> {
                                val next = canvasKey?.let { spatialNeighbour(it, NavDirection.DOWN) }
                                if (next != null) focusCanvas(next) else focusSection = WidgetFocusSection.ADD_WIDGET
                            }
                            WidgetFocusSection.ADD_WIDGET,
                            WidgetFocusSection.PRESETS -> focusSection = WidgetFocusSection.HOME_PILL
                            WidgetFocusSection.HOME_PILL -> {
                                // Already at bottom
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusSection) {
                            WidgetFocusSection.QUICK_SETTINGS -> {
                                val move = QuickSettingsNav.left(quickSettingsIndex)
                                if (move is QuickSettingsNav.Move.To) quickSettingsIndex = move.index
                                else HapticManager.performBoundaryHaptic(context)
                            }
                            WidgetFocusSection.CANVAS -> {
                                if (canvasKey == WidgetCanvasLayout.KEY_NOW_PLAYING && nowPlayingCol > 0) {
                                    nowPlayingCol--
                                } else {
                                    val next = canvasKey?.let { spatialNeighbour(it, NavDirection.LEFT) }
                                    if (next != null) focusCanvas(next) else HapticManager.performBoundaryHaptic(context)
                                }
                            }
                            WidgetFocusSection.PRESETS -> focusSection = WidgetFocusSection.ADD_WIDGET
                            WidgetFocusSection.HEADER,
                            WidgetFocusSection.ADD_WIDGET -> HapticManager.performBoundaryHaptic(context)
                            else -> {}
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (focusSection) {
                            WidgetFocusSection.QUICK_SETTINGS -> {
                                val move = QuickSettingsNav.right(quickSettingsIndex)
                                if (move is QuickSettingsNav.Move.To) quickSettingsIndex = move.index
                                else navigateHomeAndReset()
                            }
                            WidgetFocusSection.ADD_WIDGET -> focusSection = WidgetFocusSection.PRESETS
                            WidgetFocusSection.CANVAS -> {
                                if (canvasKey == WidgetCanvasLayout.KEY_NOW_PLAYING && nowPlayingCol < 2) {
                                    nowPlayingCol++
                                } else {
                                    // A card to the right first; past the right edge it's Home, as before
                                    val next = canvasKey?.let { spatialNeighbour(it, NavDirection.RIGHT) }
                                    if (next != null) focusCanvas(next) else navigateHomeAndReset()
                                }
                            }
                            else -> navigateHomeAndReset()
                        }
                        true
                    }
                    else -> false
                }
                if (focusSection != prevSection || (onCanvas && focusedKey != prevKey)) {
                    dpadScrollTick++
                }
                handled
            }
    ) {
        // 1. Top Scalloped Lavender Clouds
        TopLavenderClouds(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // 2. Scrollable content: header, Quick Settings, freeform canvas, Add Widget.
        // Spacers (not padding) hold the top/bottom room so child offsets are scroll offsets.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(SCROLL_TOP_INSET))

            WidgetHeader(
                isQuickSettingsVisible = isQuickSettingsVisible,
                isQuickSettingsExpanded = isQuickSettingsExpanded,
                isHeaderFocused = focusSection == WidgetFocusSection.HEADER && !isEditing,
                onHeaderClick = {
                    focusSection = WidgetFocusSection.HEADER
                    isQuickSettingsExpanded = !isQuickSettingsExpanded
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 8.dp, bottom = 26.dp)
            )

            if (isQuickSettingsVisible) {
                AnimatedVisibility(
                    visible = isQuickSettingsExpanded,
                    enter = expandVertically(animationSpec = tween(160), clip = false) + fadeIn(animationSpec = tween(120)),
                    exit = shrinkVertically(animationSpec = tween(140), clip = false) + fadeOut(animationSpec = tween(100))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 14.dp)
                            .curtainUnfurl(visible = isQuickSettingsExpanded)
                            .graphicsLayer { clip = false }
                    ) {
                        QuickSettingsCard(
                            // Polling only runs while the drawer is open
                            isActivePage = isActivePage && isQuickSettingsExpanded,
                            focusedIndex = if (focusSection == WidgetFocusSection.QUICK_SETTINGS) quickSettingsIndex else -1,
                            onPillClick = { index ->
                                focusSection = WidgetFocusSection.QUICK_SETTINGS
                                quickSettingsIndex = index
                                performQuickSettingsAction(context, index)
                                quickSettingsRefresh++
                            },
                            refreshKey = quickSettingsRefresh,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Freeform canvas. Each card is offset into its grid slot; zIndex decides overlap.
            val canvasBottomPx = canvasKeys.maxOfOrNull { key ->
                val p = placementOf(key) ?: return@maxOfOrNull 0
                p.gridY * stepPx + max(p.gridH * stepPx, nativeHeightsPx[key] ?: 0)
            } ?: 0
            Box(
                modifier = Modifier
                    .width(with(density) { canvasWidthPx.toDp() })
                    .height(with(density) { canvasBottomPx.toDp() })
                    .onPlaced { canvasTopPx.intValue = it.positionInParent().y.roundToInt() }
                    .graphicsLayer { clip = false }
            ) {
                canvasKeys.forEach { cardKey ->
                    val livePlacement = placementOf(cardKey) ?: return@forEach
                    val appWidgetId = WidgetCanvasLayout.appWidgetIdOf(cardKey)
                    // Resizing a hosted widget only moves the outline; the widget itself is
                    // resized once on commit so RemoteViews don't re-inflate per keypress
                    val p = if (appWidgetId != null && cardKey == arrangeKey && arrangeTool == ArrangeTool.RESIZE) {
                        placed[cardKey] ?: livePlacement
                    } else {
                        livePlacement
                    }
                    val isCardFocused = focusSection == WidgetFocusSection.CANVAS && focusedKey == cardKey && !presetsOpen
                    key(cardKey) {
                        val slotHeight = with(density) { (p.gridH * stepPx).toDp() }
                        val slotWidth = with(density) { (p.gridW * stepPx).toDp() }
                        val deco = livePlacement.deco
                        // Tape / pins replace a custom 9-slice frame with a plain stitched border
                        val plainBorder = deco != DecoStyle.NONE
                        val isNative = appWidgetId == null
                        val stickerKind = WidgetCanvasLayout.stickerKindOf(cardKey)
                        // Native cards grow to their content; the sticky note and stickers fill
                        // their slot exactly, like hosted widgets
                        val growsWithContent = isNative && stickerKind == null &&
                            cardKey != WidgetCanvasLayout.KEY_STICKY_NOTE
                        // One unclipped wrapper per slot: carries the canvas position and zIndex,
                        // and lets tape, pins and focus glows draw past the card edge
                        Box(
                            modifier = Modifier
                                .zIndex(p.zIndex)
                                .offset { IntOffset(p.gridX * stepPx, p.gridY * stepPx) }
                                .width(slotWidth)
                                .then(
                                    if (growsWithContent) {
                                        // Native cards: the slot is a minimum, so content never gets squashed
                                        Modifier
                                            .heightIn(min = slotHeight)
                                            .onSizeChanged { nativeHeightsPx[cardKey] = it.height }
                                    } else {
                                        Modifier.height(slotHeight)
                                    }
                                )
                                .graphicsLayer { clip = false }
                        ) {
                            when (cardKey) {
                                WidgetCanvasLayout.KEY_NOW_PLAYING -> NowPlayingCard(
                                    mediaState = mediaState,
                                    focusedControl = if (isCardFocused && arrangeKey == null) nowPlayingCol else -1,
                                    onPrevious = { MediaSessionTracker.skipToPrevious() },
                                    onPlayPause = { MediaSessionTracker.togglePlayPause() },
                                    onNext = { MediaSessionTracker.skipToNext() },
                                    onOpenMediaApp = { MediaSessionTracker.openCurrentMediaApp(context) },
                                    miniSkin = nowPlayingMini,
                                    plainBorder = plainBorder,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                WidgetCanvasLayout.KEY_SYSTEM_MONITOR -> SystemMonitorCard(
                                    isActivePage = isActivePage,
                                    isFocused = isCardFocused,
                                    onCardClick = {
                                        if (arrangeKey == null) {
                                            focusCanvas(cardKey)
                                            systemMonitorRefreshTrigger++
                                        }
                                    },
                                    triggerRefreshKey = systemMonitorRefreshTrigger,
                                    plainBorder = plainBorder,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                WidgetCanvasLayout.KEY_PHOTO_FRAME -> PhotoFrameCard(
                                    photo = photoFrameBitmap,
                                    frameStyle = photoFrameStyle,
                                    label = photoFrameLabel,
                                    isFocused = isCardFocused,
                                    onClick = {
                                        if (arrangeKey == null) {
                                            focusCanvas(cardKey)
                                            showPhotoFrameDialog = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                WidgetCanvasLayout.KEY_STICKY_NOTE -> StickyNoteCard(
                                    text = stickyNoteText,
                                    isFocused = isCardFocused,
                                    onClick = {
                                        if (arrangeKey == null) {
                                            focusCanvas(cardKey)
                                            showStickyEditor = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> if (stickerKind != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { clip = false }
                                            .softGlowFocus(isSelected = isCardFocused, cornerRadius = 24.dp)
                                            .clickable {
                                                if (arrangeKey == null) {
                                                    focusCanvas(cardKey)
                                                    optionsKey = cardKey
                                                }
                                            }
                                    ) {
                                        ScrapbookSticker(kind = stickerKind, modifier = Modifier.fillMaxSize())
                                    }
                                } else {
                                    val slot = appWidgetId?.let { slotsById[it] }
                                    if (slot != null) {
                                        AppWidgetHostCard(
                                            slot = slot,
                                            info = widgetInfos[slot.appWidgetId],
                                            host = widgetHost,
                                            isFocused = isCardFocused,
                                            onFrameAttached = { id, frame -> widgetFrames[id] = frame },
                                            onFrameDetached = { id, frame ->
                                                if (widgetFrames[id] === frame) widgetFrames.remove(id)
                                                if (interactingWidgetId == id) interactingWidgetId = null
                                            },
                                            onOptionsClick = {
                                                if (arrangeKey == null) {
                                                    endWidgetInteraction()
                                                    focusCanvas(cardKey)
                                                    optionsKey = cardKey
                                                }
                                            },
                                            plainBorder = plainBorder,
                                            showOptionsHandle = isCardFocused || arrangeKey != null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                            if (stickerKind == null) WidgetDecoOverlay(
                                deco = deco,
                                // Pins avoid the corner a card already uses: Now Playing's mascot
                                // (top-left), a hosted widget's ⋯ handle (top-right)
                                pinAtEnd = when {
                                    cardKey == WidgetCanvasLayout.KEY_NOW_PLAYING -> !nowPlayingMini
                                    !isNative -> false
                                    else -> null
                                },
                                topInset = when (cardKey) {
                                    WidgetCanvasLayout.KEY_NOW_PLAYING -> if (nowPlayingMini) 0.dp else 18.dp
                                    WidgetCanvasLayout.KEY_PHOTO_FRAME -> 10.dp
                                    else -> 0.dp
                                },
                                // The photo frame is a fixed 240dp centred in its slot
                                sideInset = if (cardKey == WidgetCanvasLayout.KEY_PHOTO_FRAME) {
                                    ((slotWidth - FrameWidth) / 2).coerceAtLeast(0.dp)
                                } else {
                                    0.dp
                                }
                            )
                        }
                    }
                }

                // Arrange outline: drawn above every card so the slot stays visible under overlaps
                val outlined = placementOf(arrangeKey)
                if (outlined != null) {
                    ArrangeStitchFrame(
                        showResizeHandle = arrangeTool == ArrangeTool.RESIZE,
                        modifier = Modifier
                            .zIndex(Float.MAX_VALUE)
                            .offset { IntOffset(outlined.gridX * stepPx, outlined.gridY * stepPx) }
                            .size(
                                width = with(density) { (outlined.gridW * stepPx).toDp() },
                                height = with(density) { (outlined.gridH * stepPx).toDp() }
                            )
                    )
                }
            }

            // Bottom action row: [ + Add Widget ] [ ✦ Presets ], LEFT/RIGHT between them
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 8.dp)
                    // Hidden (but still laid out) while the edit bar floats over this spot
                    .graphicsLayer {
                        clip = false
                        alpha = if (isEditing) 0f else 1f
                    }
                    .onPlaced {
                        addPillTopPx.intValue = it.positionInParent().y.roundToInt()
                        addPillHeightPx.intValue = it.size.height
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CanvasActionPill(
                    text = "+ Add Widget ♡",
                    isFocused = focusSection == WidgetFocusSection.ADD_WIDGET && !isEditing,
                    onClick = openWidgetPicker
                )
                CanvasActionPill(
                    text = "✦ Presets",
                    isFocused = focusSection == WidgetFocusSection.PRESETS && !isEditing,
                    onClick = {
                        focusSection = WidgetFocusSection.PRESETS
                        openPresets()
                    }
                )
            }

            Spacer(modifier = Modifier.height(if (isEditing) EDIT_BOTTOM_INSET else SCROLL_BOTTOM_INSET))
        }

        // 4. Bottom Layered Lavender Clouds
        BottomLavenderClouds(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )

        // 5. "Back to Home" Navigation Pill
        KawaiiReturnHomePill(
            isFocused = focusSection == WidgetFocusSection.HOME_PILL && !isEditing,
            onClick = { navigateHomeAndReset() },
            direction = HomePillDirection.RIGHT,
            animateAlways = isActivePage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )

        // 6a. Arrange HUD: mode, snapped size (dp) and layer, pinned at the top of the screen
        val hudKey = arrangeKey
        val hudPlacement = placementOf(hudKey)
        if (hudKey != null && hudPlacement != null) {
            val visible = canvasKeys.filter { it in placed }
            val layerRank = 1 + visible.count { other ->
                other != hudKey && (placed[other]?.zIndex ?: 0f) < hudPlacement.zIndex
            }
            val mode = when (arrangeTool) {
                ArrangeTool.BAR -> "ARRANGE"
                ArrangeTool.MOVE -> "MOVE"
                ArrangeTool.RESIZE -> "RESIZE"
                ArrangeTool.LAYER -> "LAYER"
            }
            val step = WidgetCanvasLayout.STEP_DP
            Text(
                text = "✦ $mode • ${hudPlacement.gridW * step}×${hudPlacement.gridH * step} • Layer $layerRank/${visible.size} ✦",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ActiveTheme.accentDeep,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ActiveTheme.palette.focusedCardContainerEnd.copy(alpha = 0.95f))
                    .border(1.2.dp, ActiveTheme.palette.focusedCardBorder, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // 6. Arrange / Presets pill bar, or the widget-buttons hint, floating above the Home pill
        val barModifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 64.dp, start = 8.dp, end = 8.dp)
        val arranging = arrangeKey
        when {
            arranging != null -> {
                val actions = ArrangeAction.entries
                val shown = placementOf(arranging)
                EditPillBar(
                    hint = when (arrangeTool) {
                        ArrangeTool.BAR -> "✦ Arrange ✦ ◀▶ pick · OK · ↩ done"
                        ArrangeTool.MOVE -> "✥ ◀▲▼▶ move · OK done"
                        ArrangeTool.RESIZE -> "⤡ ${shown?.gridW ?: 0}×${shown?.gridH ?: 0} · ◀▶ width · ▲▼ height"
                        ArrangeTool.LAYER -> "▲/2 to front · ▼/8 to back · OK done"
                    },
                    labels = actions.map { it.label },
                    selectedIndex = if (arrangeTool == ArrangeTool.BAR) arrangeBarIndex else -1,
                    activeIndex = when (arrangeTool) {
                        ArrangeTool.BAR -> -1
                        ArrangeTool.MOVE -> actions.indexOf(ArrangeAction.MOVE)
                        ArrangeTool.RESIZE -> actions.indexOf(ArrangeAction.RESIZE)
                        ArrangeTool.LAYER -> actions.indexOf(ArrangeAction.LAYER)
                    },
                    onPillClick = { index ->
                        if (arrangeTool != ArrangeTool.BAR) finishArrangeTool()
                        arrangeBarIndex = index
                        actions.getOrNull(index)?.let { runArrangeAction(it) }
                    },
                    modifier = barModifier
                )
            }
            presetsOpen -> {
                val labels = CollagePreset.entries.map { it.label } + "✕"
                val customLabels = (1..WidgetRepository.CUSTOM_PRESET_SLOTS).flatMap { slot ->
                    listOf("♡ My $slot", "✚ Save $slot")
                }
                // Apply pills for empty slots are dimmed
                val dimmed = (1..WidgetRepository.CUSTOM_PRESET_SLOTS)
                    .filter { customPresets.getOrNull(it - 1) == null }
                    .mapTo(HashSet()) { labels.size + (it - 1) * 2 }
                val hint = if (presetIndex < PRESET_ROW1) {
                    "✦ Quick collage ✦ ◀▶ pick · ▼ my layouts · OK"
                } else {
                    val pill = presetIndex - PRESET_ROW1
                    val slot = pill / 2 + 1
                    when {
                        pill % 2 == 1 -> "✚ OK saves this layout as My $slot"
                        customPresets.getOrNull(slot - 1) == null -> "My $slot is empty · ▶ save first"
                        else -> "♡ OK restores My Layout $slot"
                    }
                }
                EditPillBar(
                    hint = hint,
                    labels = labels,
                    secondRow = customLabels,
                    dimmedIndices = dimmed,
                    selectedIndex = presetIndex,
                    activeIndex = -1,
                    onPillClick = { index ->
                        presetIndex = index
                        runPresetPill(index)
                    },
                    modifier = barModifier
                )
            }
            interactingWidgetId != null -> {
                EditPillBar(
                    hint = "✦ ◀▲▼▶ pick · OK tap · ↩ done ✦",
                    labels = emptyList(),
                    selectedIndex = -1,
                    activeIndex = -1,
                    onPillClick = {},
                    modifier = barModifier
                )
            }
        }

        // 7. Widget dialogs (own windows, so they take keys away from this page while open)
        if (showWidgetPicker) {
            val stickerCount = stickerKeys.size
            WidgetPickerDialog(
                availableWidthPx = canvasWidthPx,
                onPick = { info -> addWidget(info) },
                onDismiss = { showWidgetPicker = false },
                builtIns = listOf(
                    BuiltInPickerEntry(
                        label = "✎ Sticky Note",
                        subtitle = if (isStickyNoteEnabled) "Built-in · already on your canvas ♡" else "Built-in · ruled memo card",
                        enabled = !isStickyNoteEnabled,
                        onPick = { showStickyNote() }
                    ),
                    BuiltInPickerEntry(
                        label = "✿ Sticker",
                        subtitle = if (stickerCount < WidgetCanvasLayout.MAX_STICKERS) {
                            "Built-in · scrapbook trim · $stickerCount/${WidgetCanvasLayout.MAX_STICKERS}"
                        } else {
                            "Built-in · sticker sheet full (${WidgetCanvasLayout.MAX_STICKERS}) ♡"
                        },
                        enabled = stickerCount < WidgetCanvasLayout.MAX_STICKERS,
                        onPick = {
                            showWidgetPicker = false
                            showStickerPicker = true
                        }
                    )
                )
            )
        }

        if (showStickerPicker) {
            StickerPickerDialog(
                placedCount = stickerKeys.size,
                maxStickers = WidgetCanvasLayout.MAX_STICKERS,
                onPick = { kind -> addSticker(kind) },
                onDismiss = { showStickerPicker = false }
            )
        }

        if (showStickyEditor) {
            val memoSlot = stickyNoteMemoSlot
            StickyNoteEditDialog(
                initialText = stickyNoteText,
                sourceLabel = if (memoSlot != null) "Quick Memo slot $memoSlot ♡" else "Note on your Widgets page ♡",
                maxChars = WidgetRepository.STICKY_NOTE_MAX_CHARS,
                onSave = { saveStickyNote(it) },
                onDismiss = { showStickyEditor = false }
            )
        }

        if (showPhotoFrameDialog) {
            PhotoFrameActionDialog(
                hasPhoto = photoFrameUri != null,
                styleDisplayName = PhotoFrameStyle.fromId(photoFrameStyle).displayName,
                label = photoFrameLabel,
                onPickPhoto = {
                    showPhotoFrameDialog = false
                    try {
                        photoPickerLauncher.launch(arrayOf("image/*"))
                    } catch (e: Exception) {
                        Log.e(TAG, "Photo picker failed", e)
                        Toast.makeText(context, "Couldn't open the photo picker ♡", Toast.LENGTH_SHORT).show()
                    }
                },
                onCycleStyle = {
                    PreferencesManager.setPhotoFrameStyle(
                        context,
                        PhotoFrameStyle.fromId(photoFrameStyle).next().id
                    )
                },
                onEditLabel = {
                    showPhotoFrameDialog = false
                    showPhotoLabelDialog = true
                },
                onRemovePhoto = {
                    showPhotoFrameDialog = false
                    val old = photoFrameUri
                    PreferencesManager.setPhotoFrameUri(context, null)
                    releasePhotoFrameUri(old)
                },
                onDismiss = { showPhotoFrameDialog = false }
            )
        }

        if (showPhotoLabelDialog) {
            PhotoFrameLabelDialog(
                initialLabel = photoFrameLabel,
                onSave = { newLabel ->
                    PreferencesManager.setPhotoFrameLabel(context, newLabel)
                    showPhotoLabelDialog = false
                },
                onDismiss = { showPhotoLabelDialog = false }
            )
        }

        optionsKey?.let { key ->
            val id = WidgetCanvasLayout.appWidgetIdOf(key)
            if (key in canvasKeys && (id == null || slotsById.containsKey(id))) {
                val info = id?.let { widgetInfos[it] }
                val stickerKind = WidgetCanvasLayout.stickerKindOf(key)
                val isStickyNote = key == WidgetCanvasLayout.KEY_STICKY_NOTE
                val label = remember(key, info) {
                    when (key) {
                        WidgetCanvasLayout.KEY_NOW_PLAYING -> "Now Playing"
                        WidgetCanvasLayout.KEY_SYSTEM_MONITOR -> "System Monitor"
                        WidgetCanvasLayout.KEY_PHOTO_FRAME -> "Photo Frame"
                        WidgetCanvasLayout.KEY_STICKY_NOTE -> "Sticky Note"
                        else -> stickerKind?.label
                            ?: info?.loadLabel(context.packageManager)?.takeIf { it.isNotBlank() } ?: "Widget"
                    }
                }
                // Actions that leave the page (configure, use buttons, remove) end Arrange first
                fun leaveArrange() {
                    optionsKey = null
                    if (arrangeKey != null) exitArrange()
                }
                WidgetOptionsDialog(
                    widgetLabel = label,
                    isHostedWidget = id != null,
                    canConfigure = info?.configure != null,
                    canUseControls = id != null && info != null && widgetFrames.containsKey(id),
                    decoLabel = if (stickerKind != null) null else (layout[key]?.deco ?: DecoStyle.NONE).label,
                    onCycleDeco = { cycleDeco(key) },
                    skinLabel = if (key == WidgetCanvasLayout.KEY_NOW_PLAYING) {
                        if (nowPlayingMini) "MiniDisc deck" else "Classic"
                    } else {
                        null
                    },
                    onToggleSkin = { toggleNowPlayingSkin() },
                    extraActions = when {
                        isStickyNote -> listOf(
                            "✎ Edit note" to {
                                leaveArrange()
                                showStickyEditor = true
                            }
                        )
                        stickerKind != null -> listOf("⇄ Swap: ${stickerKind.label}" to { swapSticker(key) })
                        else -> emptyList()
                    },
                    removeLabel = when {
                        isStickyNote -> "Hide sticky note"
                        stickerKind != null -> "Remove sticker"
                        else -> null
                    },
                    subtitle = if (stickerKind != null) "Sticker options ♡" else null,
                    onArrange = {
                        optionsKey = null
                        if (arrangeKey != key) enterArrange(key)
                    },
                    onConfigure = {
                        leaveArrange()
                        id?.let { reconfigureWidget(it) }
                    },
                    onUseControls = {
                        leaveArrange()
                        id?.let { activateWidget(it) }
                    },
                    onRemove = {
                        leaveArrange()
                        when {
                            // Hiding keeps its spot and text, so adding it back restores both
                            isStickyNote -> WidgetRepository.setStickyNoteEnabled(context, false)
                            stickerKind != null -> removeSticker(key)
                            else -> id?.let { removeWidget(it) }
                        }
                    },
                    onDismiss = { optionsKey = null }
                )
            }
        }
    }
}

/** Centered "♡ Widgets ♡" header pill (opens Quick Settings) with its Now Playing sub-badge. */
@Composable
private fun WidgetHeader(
    isQuickSettingsVisible: Boolean,
    isQuickSettingsExpanded: Boolean,
    isHeaderFocused: Boolean,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(modifier = modifier) {
        val hasCustomBorder = currentCardBorderState().hasCustomBorder
        val effectiveCornerRadius = if (hasCustomBorder) 16.dp else 32.dp
        // Frosted pastel pill inside the same dainty lace + pearl rim as the Home cards.
        // The 6dp frill sits inside the original 56dp height.
        KeitaiLaceContainer(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .softGlowFocus(isSelected = isHeaderFocused, cornerRadius = effectiveCornerRadius),
            cardCornerRadius = effectiveCornerRadius,
            scallopDepth = 3.dp,
            beadDiameter = 4.dp,
            contentPadding = 6.dp,
            containerColor = ActiveTheme.surface,
            borderColor = ActiveTheme.cardBorder,
            beadBorderColor = ActiveTheme.accentDeep.copy(alpha = 0.45f),
            showCornerBows = false
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(effectiveCornerRadius))
                    .clickable(enabled = isQuickSettingsVisible, onClick = onHeaderClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        !isQuickSettingsVisible -> "— ♡ Widgets ♡ —"
                        isQuickSettingsExpanded -> "— ♡ Widgets ▴ ♡ —"
                        else -> "— ♡ Widgets ▾ ♡ —"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActiveTheme.accentDeep,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Overlapping Sub-Badge: ✦ Now Playing ✦
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 13.dp)
                .clip(RoundedCornerShape(50))
                .background(ActiveTheme.unfocusedCardBg)
                .border(1.2.dp, ActiveTheme.cardBorder, RoundedCornerShape(50))
                .clickable {
                    if (!MediaSessionTracker.hasNotificationAccess(context)) {
                        Toast.makeText(
                            context,
                            "Please allow Notification Access for Mochibun to track media ♡",
                            Toast.LENGTH_LONG
                        ).show()
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {}
                    } else {
                        Toast.makeText(
                            context,
                            "Now Playing media service active ♡",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦ Now Playing ✦",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = ActiveTheme.textPrimary
            )
        }
    }
}

/** Pastel pill button for the row under the canvas. */
@Composable
private fun CanvasActionPill(text: String, isFocused: Boolean, onClick: () -> Unit) {
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 50.dp)
            .defaultMinSize(minHeight = 44.dp)
            .clip(pillShape)
            .background(if (isFocused) ActiveTheme.palette.focusedCardContainerEnd else ActiveTheme.unfocusedCardBg)
            .border(1.5.dp, if (isFocused) ActiveTheme.palette.focusedCardBorder else ActiveTheme.cardBorder, pillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ActiveTheme.textPrimary,
            maxLines = 1
        )
    }
}

/**
 * Compact floating bar: a hint line over a row of pills. [selectedIndex] is the D-pad cursor;
 * [activeIndex] marks the tool currently being used.
 */
@Composable
private fun EditPillBar(
    hint: String,
    labels: List<String>,
    selectedIndex: Int,
    activeIndex: Int,
    onPillClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Optional second row; its pills are indexed after [labels] (labels.size + i)
    secondRow: List<String> = emptyList(),
    dimmedIndices: Set<Int> = emptySet()
) {
    Column(
        modifier = modifier.graphicsLayer { clip = false },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = hint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ActiveTheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ActiveTheme.palette.focusedCardContainerEnd.copy(alpha = 0.95f))
                .border(1.dp, ActiveTheme.palette.focusedCardBorder, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
        listOf(labels to 0, secondRow to labels.size).forEach { (rowLabels, offset) ->
            if (rowLabels.isEmpty()) return@forEach
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.graphicsLayer { clip = false }
            ) {
                rowLabels.forEachIndexed { i, label ->
                    val index = offset + i
                    val isSelected = index == selectedIndex
                    val isActive = index == activeIndex
                    val shape = RoundedCornerShape(50)
                    Box(
                        modifier = Modifier
                            .graphicsLayer { clip = false }
                            // Outside the scroller: a bring-into-view request would reach the pager
                            .softGlowFocus(isSelected = isSelected, cornerRadius = 50.dp, bringIntoView = false)
                            .clip(shape)
                            .background(
                                if (isSelected || isActive) ActiveTheme.palette.focusedCardContainerEnd
                                else ActiveTheme.unfocusedCardBg
                            )
                            .border(
                                if (isActive) 2.dp else 1.5.dp,
                                if (isSelected || isActive) ActiveTheme.palette.focusedCardBorder else ActiveTheme.cardBorder,
                                shape
                            )
                            .clickable(role = Role.Button) { onPillClick(index) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ActiveTheme.textPrimary.copy(alpha = if (index in dimmedIndices) 0.45f else 1f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
