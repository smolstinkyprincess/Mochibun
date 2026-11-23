package one.crinklecod.mochibun.ui.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import one.crinklecod.mochibun.core.haptic.HapticManager
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.components.KeitaiLaceContainer
import one.crinklecod.mochibun.ui.components.MochibunSectionHeader
import one.crinklecod.mochibun.ui.components.limitTextLength
import one.crinklecod.mochibun.ui.components.softGlowFocus
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherFontFamily
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.cardBorderColor
import one.crinklecod.mochibun.ui.theme.unfocusedCardBgColor

// Focus zones: slot bar on top, memo text field, bottom row (Clear / Done)
private const val ZONE_SLOTS = -1
private const val ZONE_TEXT = 0
private const val ZONE_BUTTONS = 1
private const val BUTTON_CLEAR = 0
private const val BUTTON_DONE = 1

/**
 * Quick Memo: a tiny keitai sticky note (`#` hold on Home, the Launcher Menu, a key remap or the
 * Lid Open Action) with 5 slots. The text is saved whenever the dialog goes away (Done, Back, a
 * flip close, Home...) and on every slot switch, so a draft is never lost. The pinned slot (★)
 * scrolls in the Home sub-display ticker.
 *
 * Slot bar (D-pad Up from the text): Left/Right or digits 1-5 switch slots, Center or `*` pins.
 * Digits only switch slots while the bar has focus; in the text field they stay multi-tap input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoDialog(
    onDismiss: () -> Unit,
    /** True when opened by holding '#': keep the text field (and IME) unfocused until it's released. */
    waitForPoundRelease: Boolean = false
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val maxChars = PreferencesManager.QUICK_MEMO_MAX_CHARS

    val slotCount = PreferencesManager.QUICK_MEMO_SLOT_COUNT
    val savedSlots by PreferencesManager.quickMemoSlots.collectAsState()
    val pinnedSlot by PreferencesManager.pinnedQuickMemoSlot.collectAsState()
    var slot by remember {
        mutableIntStateOf(PreferencesManager.lastOpenedQuickMemoSlot.value.coerceIn(1, slotCount))
    }
    var memoValue by remember {
        val saved = PreferencesManager.getQuickMemoSlotText(slot)
        mutableStateOf(TextFieldValue(saved, TextRange(saved.length)))
    }
    var focusZone by remember { mutableIntStateOf(ZONE_TEXT) }
    var buttonCol by remember { mutableIntStateOf(BUTTON_DONE) }
    var sawCenterDown by remember { mutableStateOf(false) }

    var awaitingPoundRelease by remember { mutableStateOf(waitForPoundRelease) }
    // Safety net in case the release never reaches this window
    LaunchedEffect(Unit) {
        if (awaitingPoundRelease) {
            delay(1_500L)
            awaitingPoundRelease = false
        }
    }
    val dialogFocusRequester = remember { FocusRequester() }
    val textFocusRequester = remember { FocusRequester() }

    // Auto-save on every exit path (the modal can also be closed from outside, e.g. a flip close).
    // onDispose reads the states directly (latest values) instead of rememberUpdatedState(memoValue.text),
    // which read the draft in this scope and recomposed the whole dialog on every typed character.
    DisposableEffect(Unit) {
        onDispose {
            PreferencesManager.setQuickMemoSlotText(context, slot, memoValue.text)
            PreferencesManager.setLastOpenedQuickMemoSlot(context, slot)
        }
    }
    // Flips only when the open slot goes blank <-> non-blank, so the slot bar skips other keystrokes
    val isOpenSlotFilled by remember { derivedStateOf { memoValue.text.isNotBlank() } }

    /** Saves the current draft, then loads [target] into the editor. */
    fun switchSlot(target: Int) {
        if (target !in 1..slotCount) {
            HapticManager.performBoundaryHaptic(context)
            return
        }
        if (target == slot) return
        PreferencesManager.setQuickMemoSlotText(context, slot, memoValue.text)
        slot = target
        PreferencesManager.setLastOpenedQuickMemoSlot(context, target)
        val text = PreferencesManager.getQuickMemoSlotText(target)
        memoValue = TextFieldValue(text, TextRange(text.length))
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun pinSlot() {
        PreferencesManager.setQuickMemoSlotText(context, slot, memoValue.text)
        PreferencesManager.setPinnedQuickMemoSlot(context, slot)
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    LaunchedEffect(focusZone, awaitingPoundRelease) {
        if (focusZone == ZONE_TEXT && !awaitingPoundRelease) {
            textFocusRequester.awaitFocusSafely(attempts = 6)
        } else {
            keyboardController?.hide()
            dialogFocusRequester.awaitFocusSafely(attempts = 6)
        }
    }

    val saveAndDismiss: () -> Unit = {
        keyboardController?.hide()
        PreferencesManager.setQuickMemoSlotText(context, slot, memoValue.text)
        onDismiss()
    }
    val clearMemo: () -> Unit = {
        PreferencesManager.setQuickMemoSlotText(context, slot, "")
        memoValue = TextFieldValue("")
        focusZone = ZONE_TEXT
    }

    fun activateFocused() {
        when (focusZone) {
            ZONE_SLOTS -> pinSlot()
            ZONE_BUTTONS -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                if (buttonCol == BUTTON_CLEAR) clearMemo() else saveAndDismiss()
            }
        }
    }

    BasicAlertDialog(
        onDismissRequest = saveAndDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(dialogFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent

                // Swallow the still-held '#' that opened the memo (repeats + release) so it
                // never reaches the IME as typed input.
                if (awaitingPoundRelease && native.keyCode == KeyEvent.KEYCODE_POUND) {
                    if (event.type == KeyEventType.KeyUp) awaitingPoundRelease = false
                    return@onPreviewKeyEvent true
                }

                // Back always saves, even while typing.
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        saveAndDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                // Typing keys belong to the IME (holding '#' there opens the Kaomoji Wheel).
                if (focusZone == ZONE_TEXT) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                SoundEffectManager.play(context, SoundEffect.NAV)
                                focusZone = ZONE_SLOTS
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                SoundEffectManager.play(context, SoundEffect.NAV)
                                focusZone = ZONE_BUTTONS
                                buttonCol = BUTTON_DONE
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                // Slot bar: digits 1-5 jump to a slot, '*' pins (the text field isn't focused here,
                // so these never reach the IME's multi-tap).
                if (focusZone == ZONE_SLOTS) {
                    val digitSlot = native.keyCode - KeyEvent.KEYCODE_0
                    when {
                        digitSlot in 1..slotCount -> {
                            if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) switchSlot(digitSlot)
                            return@onPreviewKeyEvent true
                        }
                        native.keyCode == KeyEvent.KEYCODE_STAR -> {
                            if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) pinSlot()
                            return@onPreviewKeyEvent true
                        }
                    }
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                switchSlot(slot - 1)
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                switchSlot(slot + 1)
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                HapticManager.performBoundaryHaptic(context)
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                SoundEffectManager.play(context, SoundEffect.NAV)
                                focusZone = ZONE_TEXT
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        activateFocused()
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusZone = ZONE_TEXT
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        HapticManager.performBoundaryHaptic(context)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focusZone == ZONE_BUTTONS && buttonCol != BUTTON_CLEAR) {
                            buttonCol = BUTTON_CLEAR
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focusZone == ZONE_BUTTONS && buttonCol != BUTTON_DONE) {
                            buttonCol = BUTTON_DONE
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        ResizeWithKeyboard()
        KeitaiLaceContainer(
            cardCornerRadius = 22.dp,
            // 14dp just clears the pearl ring (same as the other converted dialogs).
            contentPadding = 14.dp,
            modifier = Modifier.width(dialogWidth)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                MochibunSectionHeader(
                    title = "Quick Memo ✎",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                )

                Spacer(Modifier.height(6.dp))

                QuickMemoSlotBar(
                    slotCount = slotCount,
                    activeSlot = slot,
                    pinnedSlot = pinnedSlot,
                    // The open slot's dot follows the live draft, the others their saved text
                    isFilled = { n -> if (n == slot) isOpenSlotFilled else savedSlots.getOrElse(n - 1) { "" }.isNotBlank() },
                    barFocused = focusZone == ZONE_SLOTS,
                    onSlotClick = { n ->
                        // Touch: tap a slot to open it, tap the open slot again to pin it
                        if (n == slot) pinSlot() else switchSlot(n)
                    }
                )

                Spacer(Modifier.height(8.dp))

                // Built here (this scope no longer recomposes per keystroke) and handed to the
                // field, so typing does not rebuild TextFieldColors / TextStyle each character.
                val fontFamily = LocalLauncherFontFamily.current
                val memoTextStyle = remember(fontFamily) {
                    TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp)
                }
                val memoFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.appActionColor,
                    unfocusedBorderColor = theme.greetingOutlineColor,
                    focusedTextColor = theme.fgTextColor,
                    unfocusedTextColor = theme.fgTextColor,
                    focusedPlaceholderColor = theme.cardTextColor.copy(alpha = 0.55f),
                    unfocusedPlaceholderColor = theme.cardTextColor.copy(alpha = 0.55f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
                QuickMemoTextField(
                    value = { memoValue },
                    onValueChange = { memoValue = it.limitTextLength(maxChars) },
                    textStyle = memoTextStyle,
                    colors = memoFieldColors,
                    isFocused = focusZone == ZONE_TEXT,
                    containerColor = theme.unfocusedCardBgColor,
                    ruledLineColor = theme.cardBorderColor.copy(alpha = 0.35f),
                    focusRequester = textFocusRequester
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (focusZone == ZONE_SLOTS) {
                            "Center: Pin ★ • Back: Close"
                        } else {
                            "↑ slots • Back: Close"
                        },
                        color = theme.cardTextColor.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    MemoCharCounter(
                        length = { memoValue.text.length },
                        maxChars = maxChars,
                        color = theme.cardTextColor
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KawaiiDialogPillButton(
                        text = "Clear",
                        isFocused = focusZone == ZONE_BUTTONS && buttonCol == BUTTON_CLEAR,
                        isDestructive = true,
                        onClick = clearMemo
                    )
                    KawaiiDialogPillButton(
                        text = "Done ♡",
                        isFocused = focusZone == ZONE_BUTTONS && buttonCol == BUTTON_DONE,
                        isPrimary = true,
                        onClick = saveAndDismiss
                    )
                }
            }
        }
    }
}

/**
 * Compact `[1★] [2•] [3 ] [4 ] [5 ]` strip: ★ = pinned to the Home ticker, • = slot has text.
 * The open slot is filled; the D-pad glow shows only while the bar has focus.
 */
@Composable
private fun QuickMemoSlotBar(
    slotCount: Int,
    activeSlot: Int,
    pinnedSlot: Int,
    isFilled: (Int) -> Boolean,
    barFocused: Boolean,
    onSlotClick: (Int) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Unclipped so the focused slot's halo isn't cut at the row edges
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
    ) {
        for (n in 1..slotCount) {
            val active = n == activeSlot
            val mark = when {
                n == pinnedSlot -> "★"
                isFilled(n) -> "•"
                else -> ""
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .softGlowFocus(isSelected = barFocused && active, cornerRadius = 10.dp, bringIntoView = false)
                    .clip(shape)
                    .background(
                        if (active) theme.appActionColor.copy(alpha = 0.35f) else theme.unfocusedCardBgColor.copy(alpha = 0.7f),
                        shape
                    )
                    .border(
                        if (active) 1.5.dp else 1.dp,
                        if (active) theme.appActionColor else theme.cardBorderColor.copy(alpha = 0.55f),
                        shape
                    )
                    .clickable { onSlotClick(n) }
            ) {
                Text(
                    text = "$n$mark",
                    color = theme.fgTextColor,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = LocalLauncherFontFamily.current,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The memo field, isolated so only this scope reads the draft: each keystroke recomposes the
 * field, not the slot bar, hints and buttons around it.
 */
@Composable
private fun QuickMemoTextField(
    value: () -> TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    textStyle: TextStyle,
    colors: TextFieldColors,
    isFocused: Boolean,
    containerColor: Color,
    ruledLineColor: Color,
    focusRequester: FocusRequester
) {
    val fieldShape = RoundedCornerShape(14.dp)
    val containerBg = if (isFocused) containerColor else containerColor.copy(alpha = 0.7f)
    OutlinedTextField(
        value = value(),
        onValueChange = onValueChange,
        placeholder = { Text("jot something cute ♡") },
        minLines = 3,
        maxLines = 5,
        shape = fieldShape,
        textStyle = textStyle,
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .background(containerBg, fieldShape)
            .drawBehind {
                val strokePx = 1.dp.toPx()
                val insetX = 10.dp.toPx()
                val topPaddingPx = 16.dp.toPx()
                val lineStepPx = 20.sp.toPx()
                val bottomGuardPx = 10.dp.toPx()
                var y = topPaddingPx + lineStepPx
                while (y <= size.height - bottomGuardPx) {
                    drawLine(
                        color = ruledLineColor,
                        start = Offset(insetX, y),
                        end = Offset(size.width - insetX, y),
                        strokeWidth = strokePx
                    )
                    y += lineStepPx
                }
            }
            .focusRequester(focusRequester)
    )
}

/** "n/140" counter; reads the length in its own scope so typing only recomposes this Text. */
@Composable
private fun MemoCharCounter(length: () -> Int, maxChars: Int, color: Color) {
    Text(
        text = "${length()}/$maxChars",
        color = color,
        fontSize = 11.sp
    )
}
