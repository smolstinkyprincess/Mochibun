package one.crinklecod.mochibun.ui.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import one.crinklecod.mochibun.core.haptic.HapticManager
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.components.MochibunSectionHeader
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.components.limitTextLength
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherFontFamily
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.unfocusedCardBgColor

private const val ZONE_TEXT = 0
private const val ZONE_BUTTONS = 1
private const val BUTTON_DELETE = 0
private const val BUTTON_SAVE = 1

private val SNIPPET_WHITESPACE_REGEX = Regex("\\s+")

/** One-line list label for a snippet. */
private fun snippetRowLabel(text: String): String {
    val flat = text.replace(SNIPPET_WHITESPACE_REGEX, " ")
    return if (flat.length > 26) flat.take(25).trimEnd() + "…" else flat
}

/**
 * Add / edit / delete the pinned Quick Snippets shown first on the `#`-hold Clips & Snippets
 * wheel. List: ▲▼ + Center (or digits `1`–`6`) open a snippet, the last row adds one. Editor:
 * typing goes to the keyboard (MochibunImeService multi-tap); ▼ / Enter reaches Delete / Save,
 * Back saves and returns to the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSnippetsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val maxCount = PreferencesManager.QUICK_SNIPPET_MAX_COUNT
    val maxChars = PreferencesManager.QUICK_SNIPPET_MAX_CHARS
    val snippets by PreferencesManager.quickSnippets.collectAsState()

    val canAdd = snippets.size < maxCount
    // List rows: snippets, then "Add" (when there's room), then the close pill
    val addRow = if (canAdd) snippets.size else -1
    val closeRow = snippets.size + if (canAdd) 1 else 0

    var listFocus by remember { mutableIntStateOf(0) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var focusZone by remember { mutableIntStateOf(ZONE_TEXT) }
    var buttonCol by remember { mutableIntStateOf(BUTTON_SAVE) }
    var sawCenterDown by remember { mutableStateOf(false) }
    var pendingDigitRow by remember { mutableIntStateOf(-1) }

    val dialogFocusRequester = remember { FocusRequester() }
    val textFocusRequester = remember { FocusRequester() }

    LaunchedEffect(editingIndex, focusZone) {
        if (editingIndex != null && focusZone == ZONE_TEXT) {
            textFocusRequester.awaitFocusSafely(attempts = 6)
        } else {
            keyboardController?.hide()
            dialogFocusRequester.awaitFocusSafely(attempts = 6)
        }
    }

    fun openEditor(index: Int) {
        val text = snippets.getOrElse(index) { "" }
        draft = TextFieldValue(text, TextRange(text.length))
        editingIndex = index
        focusZone = ZONE_TEXT
        buttonCol = BUTTON_SAVE
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    /** Saves the draft (a blank draft deletes the snippet) and returns to the list. */
    fun closeEditor(save: Boolean) {
        val index = editingIndex ?: return
        val updated = snippets.toMutableList()
        when {
            !save -> if (index < updated.size) updated.removeAt(index)
            index < updated.size -> updated[index] = draft.text
            else -> updated.add(draft.text)
        }
        PreferencesManager.setQuickSnippets(context, updated)
        keyboardController?.hide()
        editingIndex = null
        listFocus = index.coerceAtMost(PreferencesManager.quickSnippets.value.size)
    }

    fun activateListRow(row: Int) {
        when (row) {
            closeRow -> {
                SoundEffectManager.play(context, SoundEffect.BACK)
                onDismiss()
            }
            addRow -> openEditor(snippets.size)
            in snippets.indices -> openEditor(row)
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (editingIndex != null) closeEditor(save = true)
            onDismiss()
        },
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
                val editing = editingIndex != null

                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        if (editing) closeEditor(save = true) else onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                // Typing keys belong to the IME while the text field has focus
                if (editing && focusZone == ZONE_TEXT) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                SoundEffectManager.play(context, SoundEffect.NAV)
                                focusZone = ZONE_BUTTONS
                                buttonCol = BUTTON_SAVE
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    return@onPreviewKeyEvent false
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        if (editing) {
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                            closeEditor(save = buttonCol == BUTTON_SAVE)
                        } else {
                            activateListRow(listFocus)
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                // List: digits open that snippet on release, so the release never lands in the
                // freshly focused text field
                if (!editing) {
                    val digit = getDigitFromKeyCode(native.keyCode)
                    if (digit != null) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            if (digit - 1 in snippets.indices) {
                                listFocus = digit - 1
                                pendingDigitRow = digit - 1
                            } else {
                                HapticManager.performBoundaryHaptic(context)
                            }
                        } else if (event.type == KeyEventType.KeyUp && pendingDigitRow >= 0) {
                            val row = pendingDigitRow
                            pendingDigitRow = -1
                            openEditor(row)
                        }
                        return@onPreviewKeyEvent true
                    }
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (editing) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            focusZone = ZONE_TEXT
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            buttonCol = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) BUTTON_DELETE else BUTTON_SAVE
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> HapticManager.performBoundaryHaptic(context)
                        else -> return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        listFocus = if (listFocus > 0) listFocus - 1 else closeRow
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        listFocus = if (listFocus < closeRow) listFocus + 1 else 0
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        true
                    }
                    else -> false
                }
            }
    ) {
        ResizeWithKeyboard()
        val editing = editingIndex
        KeitaiLaceDialogWithClose(
            isCloseFocused = editing == null && listFocus == closeRow,
            onClose = onDismiss,
            showClose = editing == null,
            contentPadding = 14.dp,
            modifier = Modifier.width(dialogWidth)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                MochibunSectionHeader(
                    title = if (editing == null) "Quick Snippets ★" else "Snippet ${editing + 1} ★",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                )

                if (editing == null) {
                    Text(
                        text = "Pinned first on the Clips wheel (hold #, then ✱ or 0).",
                        color = theme.cardTextColor.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    snippets.forEachIndexed { i, text ->
                        KawaiiDialogItem(
                            text = "★ " + snippetRowLabel(text),
                            isFocused = listFocus == i,
                            jumpNumber = i + 1,
                            onClick = { listFocus = i; openEditor(i) }
                        )
                    }
                    if (canAdd) {
                        KawaiiDialogItem(
                            text = "＋ Add snippet (${snippets.size}/$maxCount)",
                            isFocused = listFocus == addRow,
                            onClick = { listFocus = addRow; openEditor(snippets.size) }
                        )
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.limitTextLength(maxChars) },
                        placeholder = { Text("email, address, signature… ♡") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = TextStyle(fontFamily = LocalLauncherFontFamily.current, fontSize = 14.sp),
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
                            .focusRequester(textFocusRequester)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "▼ buttons • Back saves",
                            color = theme.cardTextColor.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${draft.text.length}/$maxChars",
                            color = theme.cardTextColor,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KawaiiDialogPillButton(
                            text = "Delete",
                            isFocused = focusZone == ZONE_BUTTONS && buttonCol == BUTTON_DELETE,
                            isDestructive = true,
                            onClick = { closeEditor(save = false) }
                        )
                        KawaiiDialogPillButton(
                            text = "Save ♡",
                            isFocused = focusZone == ZONE_BUTTONS && buttonCol == BUTTON_SAVE,
                            isPrimary = true,
                            onClick = { closeEditor(save = true) }
                        )
                    }
                }
            }
        }
    }
}
