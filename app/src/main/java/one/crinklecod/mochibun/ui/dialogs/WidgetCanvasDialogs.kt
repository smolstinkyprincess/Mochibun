package one.crinklecod.mochibun.ui.dialogs

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import one.crinklecod.mochibun.core.widget.StickerKind
import one.crinklecod.mochibun.ui.components.KeitaiLaceContainer
import one.crinklecod.mochibun.ui.components.MochibunSectionHeader
import one.crinklecod.mochibun.ui.components.ScrapbookSticker
import one.crinklecod.mochibun.ui.components.curtainUnfurl
import one.crinklecod.mochibun.ui.components.limitTextLength
import one.crinklecod.mochibun.ui.components.softGlowFocus
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherFontFamily
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.unfocusedCardBgColor

/**
 * Compact editor for the Widgets-page Sticky Note. Typing (multi-tap / T9) goes to the IME while
 * the field has focus; DOWN or Enter moves to the buttons. The draft is saved on every exit path
 * (Done, Back, a tap outside, or the page closing it), like Quick Memo, so a note is never lost;
 * only Cancel throws the draft away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickyNoteEditDialog(
    initialText: String,
    /** Shown under the title, e.g. which Quick Memo slot the note mirrors. */
    sourceLabel: String,
    maxChars: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var value by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var focusZone by remember { mutableIntStateOf(0) } // 0 = text, 1 = buttons
    var buttonCol by remember { mutableIntStateOf(1) } // 0 = Cancel, 1 = Done
    var sawCenterDown by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val dialogFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }

    // Save on every way out, reading the latest draft at dispose time
    val latestValue by rememberUpdatedState(value)
    val latestDiscard by rememberUpdatedState(discard)
    val latestOnSave by rememberUpdatedState(onSave)
    DisposableEffect(Unit) {
        onDispose {
            if (!latestDiscard) latestOnSave(latestValue.text)
        }
    }

    val done: () -> Unit = {
        keyboardController?.hide()
        onDismiss()
    }
    val cancel: () -> Unit = {
        discard = true
        keyboardController?.hide()
        onDismiss()
    }

    LaunchedEffect(focusZone) {
        if (focusZone == 0) {
            fieldFocusRequester.awaitFocusSafely(attempts = 6)
        } else {
            keyboardController?.hide()
            dialogFocusRequester.awaitFocusSafely(attempts = 6)
        }
    }

    BasicAlertDialog(
        onDismissRequest = done,
        properties = DialogProperties(
            dismissOnBackPress = false, // handled below so Back saves and never leaves Page 0
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(dialogFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) done()
                    return@onPreviewKeyEvent true
                }
                if (focusZone == 0) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                focusZone = 1
                                buttonCol = 1
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
                        if (buttonCol == 0) cancel() else done()
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusZone = 0
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        buttonCol = 0
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        buttonCol = 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> true
                    else -> false
                }
            }
    ) {
        ResizeWithKeyboard()
        KeitaiLaceContainer(
            cardCornerRadius = 22.dp,
            contentPadding = 14.dp,
            modifier = Modifier
                .width(dialogWidth)
                .curtainUnfurl(visible = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                MochibunSectionHeader(
                    title = "Sticky Note ✎",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp)
                )
                Text(
                    text = sourceLabel,
                    color = theme.cardTextColor.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                val fontFamily = LocalLauncherFontFamily.current
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.limitTextLength(maxChars) },
                    placeholder = { Text("jot something cute ♡") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.appActionColor,
                        unfocusedBorderColor = theme.greetingOutlineColor,
                        focusedTextColor = theme.fgTextColor,
                        unfocusedTextColor = theme.fgTextColor,
                        focusedPlaceholderColor = theme.cardTextColor.copy(alpha = 0.55f),
                        unfocusedPlaceholderColor = theme.cardTextColor.copy(alpha = 0.55f),
                        focusedContainerColor = theme.unfocusedCardBgColor,
                        unfocusedContainerColor = theme.unfocusedCardBgColor.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocusRequester)
                )
                Text(
                    text = "${value.text.length}/$maxChars · ▼ buttons · ↩ save",
                    color = theme.fgTextColor.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KawaiiDialogPillButton(
                        text = "Cancel",
                        isFocused = focusZone == 1 && buttonCol == 0,
                        modifier = Modifier.weight(1f),
                        onClick = cancel
                    )
                    KawaiiDialogPillButton(
                        text = "Done ♡",
                        isFocused = focusZone == 1 && buttonCol == 1,
                        isPrimary = true,
                        modifier = Modifier.weight(1f),
                        onClick = done
                    )
                }
            }
        }
    }
}

private const val STICKER_GRID_COLS = 5

/**
 * `+ Sticker` catalog: a 5-wide grid of the scrapbook stickers. D-pad moves, OK places one,
 * Back closes. [placedCount] / [maxStickers] shows how many of the canvas cap are used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerDialog(
    placedCount: Int,
    maxStickers: Int,
    onPick: (StickerKind) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val kinds = StickerKind.entries
    val focusRequester = remember { FocusRequester() }
    var selected by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    requestFocusSafely(focusRequester)
    BackHandler { onDismiss() }

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
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        kinds.getOrNull(selected)?.let(onPick)
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val next = when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> selected - 1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> selected + 1
                    KeyEvent.KEYCODE_DPAD_UP -> selected - STICKER_GRID_COLS
                    KeyEvent.KEYCODE_DPAD_DOWN -> selected + STICKER_GRID_COLS
                    else -> return@onPreviewKeyEvent false
                }
                if (next in kinds.indices) selected = next
                true
            }
    ) {
        KeitaiLaceContainer(
            cardCornerRadius = 22.dp,
            contentPadding = 14.dp,
            containerColor = theme.cardBgColor,
            borderColor = theme.greetingOutlineColor,
            modifier = Modifier
                .width(dialogWidth)
                .curtainUnfurl(visible = true)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "✿ Add a Sticker ✿",
                    color = theme.fgTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "${kinds.getOrNull(selected)?.label ?: ""} · $placedCount/$maxStickers placed",
                    color = theme.cardTextColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                kinds.chunked(STICKER_GRID_COLS).forEachIndexed { row, rowKinds ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .graphicsLayer { clip = false }
                            .padding(bottom = 4.dp)
                    ) {
                        rowKinds.forEachIndexed { col, kind ->
                            val index = row * STICKER_GRID_COLS + col
                            val isFocused = index == selected
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .graphicsLayer { clip = false }
                                    .softGlowFocus(isSelected = isFocused, cornerRadius = 10.dp)
                                    .background(if (isFocused) ActiveTheme.palette.focusedCardContainerEnd else Color.Transparent, shape)
                                    .border(
                                        if (isFocused) 1.5.dp else 0.dp,
                                        if (isFocused) ActiveTheme.palette.focusedCardBorder else Color.Transparent,
                                        shape
                                    )
                                    .clickable {
                                        selected = index
                                        onPick(kind)
                                    }
                            ) {
                                ScrapbookSticker(kind = kind, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
