package com.crinklecodone.mochibun.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.prefs.CursorPreferences
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import java.util.Locale

/**
 * Tier 2: Virtual Cursor Sub-Screen
 */
@Composable
internal fun CursorHardwareSettingsSubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    var tapStepPx by remember { mutableIntStateOf(CursorPreferences.getTapStepPx(context)) }
    var maxSpeed by remember { mutableIntStateOf(CursorPreferences.getMaxSpeed(context)) }
    var accelRampMs by remember { mutableIntStateOf(CursorPreferences.getAccelRampMs(context)) }
    var cursorSizeDp by remember { mutableIntStateOf(CursorPreferences.getCursorSizeDp(context)) }
    var scrollStepPx by remember { mutableIntStateOf(CursorPreferences.getScrollStepPx(context)) }
    var customUri by remember { mutableStateOf(CursorPreferences.getCustomCursorUri(context)) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            CursorPreferences.setCustomCursorUri(context, uri)
            customUri = uri
        }
    }

    // Step, MaxSpeed, Accel, Size, ScrollStep, SpritePicker, (ResetSprite)
    val totalItems = if (customUri != null) 7 else 6

    val targetCardIndex = remember(focusedIndex) {
        when (focusedIndex) {
            0, 1, 2, 3, 4 -> 0
            5, 6 -> 1
            else -> 0
        }
    }

    // Moves the stepper at [index] one notch; [direction] is -1 (left) or +1 (right).
    // Returns false when [index] is not a stepper row.
    fun adjustSetting(index: Int, direction: Int): Boolean {
        when (index) {
            0 -> {
                tapStepPx = (tapStepPx + direction)
                    .coerceIn(CursorPreferences.MIN_TAP_STEP_PX, CursorPreferences.MAX_TAP_STEP_PX)
                CursorPreferences.setTapStepPx(context, tapStepPx)
            }
            1 -> {
                maxSpeed = (maxSpeed + direction * CursorPreferences.MAX_SPEED_INCREMENT)
                    .coerceIn(CursorPreferences.MIN_MAX_SPEED, CursorPreferences.MAX_MAX_SPEED)
                CursorPreferences.setMaxSpeed(context, maxSpeed)
            }
            2 -> {
                accelRampMs = (accelRampMs + direction * CursorPreferences.ACCEL_RAMP_INCREMENT_MS)
                    .coerceIn(CursorPreferences.MIN_ACCEL_RAMP_MS, CursorPreferences.MAX_ACCEL_RAMP_MS)
                CursorPreferences.setAccelRampMs(context, accelRampMs)
            }
            3 -> {
                cursorSizeDp = (cursorSizeDp + direction * 4).coerceIn(20, 52)
                CursorPreferences.setCursorSizeDp(context, cursorSizeDp)
            }
            4 -> {
                scrollStepPx = (scrollStepPx + direction * CursorPreferences.SCROLL_STEP_INCREMENT_PX)
                    .coerceIn(CursorPreferences.MIN_SCROLL_STEP_PX, CursorPreferences.MAX_SCROLL_STEP_PX)
                CursorPreferences.setScrollStepPx(context, scrollStepPx)
            }
            else -> return false
        }
        return true
    }

    LaunchedEffect(targetCardIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || targetCardIndex in visible) return@LaunchedEffect
        listState.animateScrollToItem(targetCardIndex)
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Virtual Cursor",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onBack()
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        when (focusedIndex) {
                            5 -> {
                                try {
                                    filePicker.launch(arrayOf("image/gif", "image/png"))
                                } catch (_: Exception) {}
                            }
                            6 -> {
                                CursorPreferences.setCustomCursorUri(context, null)
                                customUri = null
                                focusedIndex = 5
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        when (targetIndex) {
                            5 -> {
                                try {
                                    filePicker.launch(arrayOf("image/gif", "image/png"))
                                } catch (_: Exception) {}
                            }
                            6 -> {
                                CursorPreferences.setCustomCursorUri(context, null)
                                customUri = null
                                focusedIndex = 5
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        focusedIndex = if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                        } else {
                            if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                        }
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        val handled = adjustSetting(focusedIndex, direction)
                        if (handled) {
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 64.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsCard(categoryTitle = "Movement & Physics") {
                    SettingsStepperRow(
                        title = "Base Speed",
                        subtitle = "Precision step per tap",
                        valueDisplay = "$tapStepPx px",
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onDecrement = { adjustSetting(0, -1) },
                        onIncrement = { adjustSetting(0, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Max Speed",
                        subtitle = "Top speed while holding",
                        valueDisplay = "$maxSpeed px/s",
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onDecrement = { adjustSetting(1, -1) },
                        onIncrement = { adjustSetting(1, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Acceleration",
                        subtitle = "Time to max speed (short = snappy)",
                        valueDisplay = String.format(Locale.US, "%.2f s", accelRampMs / 1000f),
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onDecrement = { adjustSetting(2, -1) },
                        onIncrement = { adjustSetting(2, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Cursor Size",
                        subtitle = "On-screen pointer dimensions",
                        valueDisplay = "$cursorSizeDp dp",
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onDecrement = { adjustSetting(3, -1) },
                        onIncrement = { adjustSetting(3, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Scroll Step",
                        subtitle = "Keys 2 (Up) & 8 (Down) swipe distance",
                        valueDisplay = "$scrollStepPx px",
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
                        onDecrement = { adjustSetting(4, -1) },
                        onIncrement = { adjustSetting(4, 1) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Custom Cursor Sprite") {
                    SettingsNavigationRow(
                        title = "Cursor Graphic",
                        subtitle = if (customUri != null) "Custom sprite active" else "Default kawaii paw sprite",
                        icon = "🐾",
                        isFocused = focusedIndex == 5,
                        jumpNumber = 6,
                        onClick = {
                            try {
                                filePicker.launch(arrayOf("image/gif", "image/png"))
                            } catch (_: Exception) {}
                        }
                    )
                    if (customUri != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsActionRow(
                            title = "Reset to Default Sprite",
                            isFocused = focusedIndex == 6,
                            isDestructive = true,
                            jumpNumber = 7,
                            onClick = {
                                CursorPreferences.setCustomCursorUri(context, null)
                                customUri = null
                                focusedIndex = 5
                            }
                        )
                    }
                }
            }
        }
    }
}
