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
import kotlinx.coroutines.delay
import one.crinklecod.mochibun.core.haptic.HapticManager
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.components.KeitaiLaceContainer
import one.crinklecod.mochibun.ui.components.MochibunSectionHeader
import one.crinklecod.mochibun.ui.components.limitTextLength
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherFontFamily
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.softSurfaceColor

// Focus zones: 0 = memo text field, 1 = bottom row (Clear / Done)
private const val ZONE_TEXT = 0
private const val ZONE_BUTTONS = 1
private const val BUTTON_CLEAR = 0
private const val BUTTON_DONE = 1

/**
 * Quick Memo: a tiny keitai sticky note (`#` hold on Home, the Launcher Menu, a key remap or the
 * Lid Open Action). The text is saved whenever the dialog goes away (Done, Back, a flip close,
 * Home...), so a draft is never lost. A non-empty memo scrolls in the Home sub-display ticker.
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

    var memoValue by remember {
        val saved = PreferencesManager.quickMemoText.value
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
    val latestText by rememberUpdatedState(memoValue.text)
    DisposableEffect(Unit) {
        onDispose { PreferencesManager.setQuickMemoText(context, latestText) }
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
        PreferencesManager.setQuickMemoText(context, memoValue.text)
        onDismiss()
    }
    val clearMemo: () -> Unit = {
        PreferencesManager.clearQuickMemo(context)
        memoValue = TextFieldValue("")
        focusZone = ZONE_TEXT
    }

    fun activateFocused() {
        SoundEffectManager.play(context, SoundEffect.SELECT)
        when (focusZone) {
            ZONE_BUTTONS -> if (buttonCol == BUTTON_CLEAR) clearMemo() else saveAndDismiss()
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

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = memoValue,
                    onValueChange = { memoValue = it.limitTextLength(maxChars) },
                    placeholder = { Text("jot something cute ♡") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(fontFamily = LocalLauncherFontFamily.current, fontSize = 14.sp),
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
                        .focusRequester(textFocusRequester)
                )

                Text(
                    text = "${memoValue.text.length}/$maxChars",
                    color = theme.cardTextColor,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp, end = 4.dp)
                )

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
