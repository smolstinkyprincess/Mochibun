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
import kotlin.math.roundToInt

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

    var moveSpeed by remember { mutableFloatStateOf(CursorPreferences.getMoveSpeed(context)) }
    var acceleration by remember { mutableFloatStateOf(CursorPreferences.getAcceleration(context)) }
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

    val totalItems = if (customUri != null) 6 else 5 // Speed, Accel, Size, ScrollStep, SpritePicker, (ResetSprite)

    val targetCardIndex = remember(focusedIndex) {
        when (focusedIndex) {
            0, 1, 2, 3 -> 0
            4, 5 -> 1
            else -> 0
        }
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
                            4 -> {
                                try {
                                    filePicker.launch(arrayOf("image/gif", "image/png"))
                                } catch (_: Exception) {}
                            }
                            5 -> {
                                CursorPreferences.setCustomCursorUri(context, null)
                                customUri = null
                                focusedIndex = 4
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
                            4 -> {
                                try {
                                    filePicker.launch(arrayOf("image/gif", "image/png"))
                                } catch (_: Exception) {}
                            }
                            5 -> {
                                CursorPreferences.setCustomCursorUri(context, null)
                                customUri = null
                                focusedIndex = 4
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
                        val handled = when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                when (focusedIndex) {
                                    0 -> {
                                        val newSpeed = (moveSpeed - 2f).coerceIn(4f, 26f)
                                        CursorPreferences.setMoveSpeed(context, newSpeed)
                                        moveSpeed = newSpeed
                                        true
                                    }
                                    1 -> {
                                        val newAccel = (acceleration - 0.02f).coerceIn(1.00f, 1.25f)
                                        CursorPreferences.setAcceleration(context, newAccel)
                                        acceleration = newAccel
                                        true
                                    }
                                    2 -> {
                                        val newSize = (cursorSizeDp - 4).coerceIn(20, 52)
                                        CursorPreferences.setCursorSizeDp(context, newSize)
                                        cursorSizeDp = newSize
                                        true
                                    }
                                    3 -> {
                                        val newStep = (scrollStepPx - CursorPreferences.SCROLL_STEP_INCREMENT_PX)
                                            .coerceIn(CursorPreferences.MIN_SCROLL_STEP_PX, CursorPreferences.MAX_SCROLL_STEP_PX)
                                        CursorPreferences.setScrollStepPx(context, newStep)
                                        scrollStepPx = newStep
                                        true
                                    }
                                    else -> false
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                when (focusedIndex) {
                                    0 -> {
                                        val newSpeed = (moveSpeed + 2f).coerceIn(4f, 26f)
                                        CursorPreferences.setMoveSpeed(context, newSpeed)
                                        moveSpeed = newSpeed
                                        true
                                    }
                                    1 -> {
                                        val newAccel = (acceleration + 0.02f).coerceIn(1.00f, 1.25f)
                                        CursorPreferences.setAcceleration(context, newAccel)
                                        acceleration = newAccel
                                        true
                                    }
                                    2 -> {
                                        val newSize = (cursorSizeDp + 4).coerceIn(20, 52)
                                        CursorPreferences.setCursorSizeDp(context, newSize)
                                        cursorSizeDp = newSize
                                        true
                                    }
                                    3 -> {
                                        val newStep = (scrollStepPx + CursorPreferences.SCROLL_STEP_INCREMENT_PX)
                                            .coerceIn(CursorPreferences.MIN_SCROLL_STEP_PX, CursorPreferences.MAX_SCROLL_STEP_PX)
                                        CursorPreferences.setScrollStepPx(context, newStep)
                                        scrollStepPx = newStep
                                        true
                                    }
                                    else -> false
                                }
                            }
                            else -> false
                        }
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
                        subtitle = "Pointer movement velocity",
                        valueDisplay = "${moveSpeed.roundToInt()} px",
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onDecrement = {
                            val newSpeed = (moveSpeed - 2f).coerceIn(4f, 26f)
                            CursorPreferences.setMoveSpeed(context, newSpeed)
                            moveSpeed = newSpeed
                        },
                        onIncrement = {
                            val newSpeed = (moveSpeed + 2f).coerceIn(4f, 26f)
                            CursorPreferences.setMoveSpeed(context, newSpeed)
                            moveSpeed = newSpeed
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Acceleration",
                        subtitle = "Velocity ramp multiplier",
                        valueDisplay = String.format(Locale.US, "%.2fx", acceleration),
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onDecrement = {
                            val newAccel = (acceleration - 0.02f).coerceIn(1.00f, 1.25f)
                            CursorPreferences.setAcceleration(context, newAccel)
                            acceleration = newAccel
                        },
                        onIncrement = {
                            val newAccel = (acceleration + 0.02f).coerceIn(1.00f, 1.25f)
                            CursorPreferences.setAcceleration(context, newAccel)
                            acceleration = newAccel
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Cursor Size",
                        subtitle = "On-screen pointer dimensions",
                        valueDisplay = "$cursorSizeDp dp",
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onDecrement = {
                            val newSize = (cursorSizeDp - 4).coerceIn(20, 52)
                            CursorPreferences.setCursorSizeDp(context, newSize)
                            cursorSizeDp = newSize
                        },
                        onIncrement = {
                            val newSize = (cursorSizeDp + 4).coerceIn(20, 52)
                            CursorPreferences.setCursorSizeDp(context, newSize)
                            cursorSizeDp = newSize
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Scroll Step",
                        subtitle = "Keys 2 (Up) & 8 (Down) swipe distance",
                        valueDisplay = "$scrollStepPx px",
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onDecrement = {
                            val newStep = (scrollStepPx - CursorPreferences.SCROLL_STEP_INCREMENT_PX)
                                .coerceIn(CursorPreferences.MIN_SCROLL_STEP_PX, CursorPreferences.MAX_SCROLL_STEP_PX)
                            CursorPreferences.setScrollStepPx(context, newStep)
                            scrollStepPx = newStep
                        },
                        onIncrement = {
                            val newStep = (scrollStepPx + CursorPreferences.SCROLL_STEP_INCREMENT_PX)
                                .coerceIn(CursorPreferences.MIN_SCROLL_STEP_PX, CursorPreferences.MAX_SCROLL_STEP_PX)
                            CursorPreferences.setScrollStepPx(context, newStep)
                            scrollStepPx = newStep
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Custom Cursor Sprite") {
                    SettingsNavigationRow(
                        title = "Cursor Graphic",
                        subtitle = if (customUri != null) "Custom sprite active" else "Default kawaii paw sprite",
                        icon = "🐾",
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
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
                            isFocused = focusedIndex == 5,
                            isDestructive = true,
                            jumpNumber = 6,
                            onClick = {
                                CursorPreferences.setCustomCursorUri(context, null)
                                customUri = null
                                focusedIndex = 4
                            }
                        )
                    }
                }
            }
        }
    }
}
