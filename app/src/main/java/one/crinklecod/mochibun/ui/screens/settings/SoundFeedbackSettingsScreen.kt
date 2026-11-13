package one.crinklecod.mochibun.ui.screens.settings

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely

@Composable
internal fun SoundFeedbackSettingsSubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val audioStream by PreferencesManager.launcherAudioStream.collectAsState()
    val soundVolume by PreferencesManager.launcherSoundVolume.collectAsState()
    val keypadSoundMode by PreferencesManager.keypadSoundMode.collectAsState()
    val mediaVolumeLock by PreferencesManager.isMediaVolumeLockEnabled.collectAsState()
    val lidSnapSound by PreferencesManager.isLidCloseSnapSoundEnabled.collectAsState()
    val totalItems = 5

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + totalItems) % totalItems
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun changeAudioStream() {
        val next = if (audioStream == PreferencesManager.AUDIO_STREAM_MEDIA) {
            PreferencesManager.AUDIO_STREAM_NOTIFICATION
        } else {
            PreferencesManager.AUDIO_STREAM_MEDIA
        }
        PreferencesManager.setLauncherAudioStream(context, next)
    }

    fun changeSoundProfile(direction: Int) {
        val profiles = listOf("retro", "kawaii", "chiptune")
        val index = profiles.indexOf(keypadSoundMode).coerceAtLeast(0)
        val nextIndex = (index + direction + profiles.size) % profiles.size
        PreferencesManager.setKeypadSoundMode(context, profiles[nextIndex])
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    fun setMediaVolumeLock(enabled: Boolean) {
        PreferencesManager.setMediaVolumeLockEnabled(context, enabled)
    }

    fun setLidSnapSound(enabled: Boolean) {
        PreferencesManager.setLidCloseSnapSoundEnabled(context, enabled)
        if (enabled) SoundEffectManager.playSnap(context)
    }

    fun adjustSetting(index: Int, direction: Int): Boolean {
        when (index) {
            0 -> changeAudioStream()
            1 -> PreferencesManager.setLauncherSoundVolume(
                context,
                (soundVolume + direction * 10).coerceIn(0, 100)
            )
            3 -> changeSoundProfile(direction)
            else -> return false
        }
        if (index != 3) SoundEffectManager.play(context, SoundEffect.NAV)
        return true
    }

    fun activate(index: Int) {
        when (index) {
            0 -> changeAudioStream()
            1 -> PreferencesManager.setLauncherSoundVolume(
                context,
                if (soundVolume >= 100) 0 else soundVolume + 10
            )
            2 -> setMediaVolumeLock(!mediaVolumeLock)
            3 -> changeSoundProfile(1)
            4 -> setLidSnapSound(!lidSnapSound)
        }
    }

    LaunchedEffect(focusedIndex) {
        val cardIndex = if (focusedIndex <= 2) 0 else 1
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || cardIndex in visible) return@LaunchedEffect
        listState.animateScrollToItem(cardIndex)
    }

    requestFocusSafely(focusRequester)
    SettingsScaffold(
        title = "Sound & Feedback",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
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
                        activate(focusedIndex)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        focusedIndex = digit - 1
                        activate(focusedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        if (!adjustSetting(focusedIndex, direction)) stepIndex(direction)
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 64.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsCard(categoryTitle = "Master Routing & Volume") {
                    SettingsStepperRow(
                        title = "Audio Routing Stream",
                        subtitle = "Choose where keypad sounds are played",
                        valueDisplay = if (audioStream == PreferencesManager.AUDIO_STREAM_MEDIA) "Media" else "Notification",
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onDecrement = { adjustSetting(0, -1) },
                        onIncrement = { adjustSetting(0, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Keypad Volume",
                        subtitle = "Master level for keypad and launcher sounds",
                        valueDisplay = "$soundVolume%",
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onDecrement = { adjustSetting(1, -1) },
                        onIncrement = { adjustSetting(1, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Lock Volume Keys to Media",
                        subtitle = "Keep hardware volume keys on the media stream",
                        checked = mediaVolumeLock,
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onCheckedChange = { setMediaVolumeLock(it) }
                    )
                }
            }
            item {
                SettingsCard(categoryTitle = "Sound Effects") {
                    SettingsStepperRow(
                        title = "Keypad Sound Profile",
                        subtitle = "Choose Keitai, Pop, or Chiptune effects",
                        valueDisplay = when (keypadSoundMode) {
                            "retro" -> "Keitai"
                            "chiptune" -> "Chiptune"
                            else -> "Pop"
                        },
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onDecrement = { changeSoundProfile(-1) },
                        onIncrement = { changeSoundProfile(1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Lid Close Snap Sound",
                        subtitle = "Play the mechanical snap when closing the clamshell",
                        checked = lidSnapSound,
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
                        onCheckedChange = {
                            setLidSnapSound(it)
                        }
                    )
                }
            }
        }
    }
}
