package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.MediaVolumeLockSync
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_KEYPAD_SOUND_MODE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_LAUNCHER_AUDIO_STREAM
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_LAUNCHER_SOUND_VOLUME
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_LID_CLOSE_SNAP_SOUND
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_MEDIA_VOLUME_LOCK_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_KEYPAD_SOUND_MODE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LAUNCHER_AUDIO_STREAM
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LAUNCHER_SOUND_VOLUME
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LID_CLOSE_SNAP_SOUND
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_MEDIA_VOLUME_LOCK_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs
import one.crinklecod.mochibun.data.prefs.PreferencesManager.initialized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keypad sound mode, launcher audio stream & volume, lid-close snap sound, and the media volume
 * lock.
 *
 * Internal store behind [PreferencesManager]; callers keep using the PreferencesManager API.
 * Keys and defaults stay declared on PreferencesManager so stored names never change.
 */
internal object SoundPrefs {
    private val _isLidCloseSnapSoundEnabled = MutableStateFlow(DEFAULT_LID_CLOSE_SNAP_SOUND)
    val isLidCloseSnapSoundEnabled: StateFlow<Boolean> = _isLidCloseSnapSoundEnabled.asStateFlow()

    private val _keypadSoundMode = MutableStateFlow(DEFAULT_KEYPAD_SOUND_MODE)
    val keypadSoundMode: StateFlow<String> = _keypadSoundMode.asStateFlow()

    private val _launcherAudioStream = MutableStateFlow(DEFAULT_LAUNCHER_AUDIO_STREAM)
    val launcherAudioStream: StateFlow<String> = _launcherAudioStream.asStateFlow()

    private val _launcherSoundVolume = MutableStateFlow(DEFAULT_LAUNCHER_SOUND_VOLUME)
    val launcherSoundVolume: StateFlow<Int> = _launcherSoundVolume.asStateFlow()

    private val _isMediaVolumeLockEnabled = MutableStateFlow(DEFAULT_MEDIA_VOLUME_LOCK_ENABLED)
    val isMediaVolumeLockEnabled: StateFlow<Boolean> = _isMediaVolumeLockEnabled.asStateFlow()

    fun reload(prefs: SharedPreferences) {
        _isLidCloseSnapSoundEnabled.value = prefs.getBoolean(
            KEY_LID_CLOSE_SNAP_SOUND,
            DEFAULT_LID_CLOSE_SNAP_SOUND
        )
        _keypadSoundMode.value = prefs.getString(
            KEY_KEYPAD_SOUND_MODE,
            DEFAULT_KEYPAD_SOUND_MODE
        ) ?: DEFAULT_KEYPAD_SOUND_MODE
        _launcherAudioStream.value = prefs.getString(
            KEY_LAUNCHER_AUDIO_STREAM,
            DEFAULT_LAUNCHER_AUDIO_STREAM
        ) ?: DEFAULT_LAUNCHER_AUDIO_STREAM
        _launcherSoundVolume.value = prefs.getInt(
            KEY_LAUNCHER_SOUND_VOLUME,
            DEFAULT_LAUNCHER_SOUND_VOLUME
        )
        _isMediaVolumeLockEnabled.value = prefs.getBoolean(
            KEY_MEDIA_VOLUME_LOCK_ENABLED,
            DEFAULT_MEDIA_VOLUME_LOCK_ENABLED
        )
    }

    fun isLidCloseSnapSoundEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_LID_CLOSE_SNAP_SOUND, DEFAULT_LID_CLOSE_SNAP_SOUND)

    fun setLidCloseSnapSoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LID_CLOSE_SNAP_SOUND, enabled).apply()
        _isLidCloseSnapSoundEnabled.value = enabled
    }

    fun getKeypadSoundMode(context: Context? = null): String {
        return if (initialized) {
            _keypadSoundMode.value
        } else if (context != null) {
            getPrefs(context).getString(
                KEY_KEYPAD_SOUND_MODE,
                DEFAULT_KEYPAD_SOUND_MODE
            ) ?: DEFAULT_KEYPAD_SOUND_MODE
        } else {
            _keypadSoundMode.value
        }
    }

    fun setKeypadSoundMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_KEYPAD_SOUND_MODE, mode).apply()
        _keypadSoundMode.value = mode
    }

    fun getLauncherAudioStream(context: Context? = null): String {
        return if (initialized) {
            _launcherAudioStream.value
        } else if (context != null) {
            getPrefs(context).getString(
                KEY_LAUNCHER_AUDIO_STREAM,
                DEFAULT_LAUNCHER_AUDIO_STREAM
            ) ?: DEFAULT_LAUNCHER_AUDIO_STREAM
        } else {
            _launcherAudioStream.value
        }
    }

    fun setLauncherAudioStream(context: Context, stream: String) {
        getPrefs(context).edit().putString(KEY_LAUNCHER_AUDIO_STREAM, stream).apply()
        _launcherAudioStream.value = stream
        SoundEffectManager.updateAudioStream(context)
    }

    fun getLauncherSoundVolume(context: Context? = null): Int {
        return if (initialized) {
            _launcherSoundVolume.value
        } else if (context != null) {
            getPrefs(context).getInt(
                KEY_LAUNCHER_SOUND_VOLUME,
                DEFAULT_LAUNCHER_SOUND_VOLUME
            )
        } else {
            _launcherSoundVolume.value
        }
    }

    fun setLauncherSoundVolume(context: Context, volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        getPrefs(context).edit().putInt(KEY_LAUNCHER_SOUND_VOLUME, clamped).apply()
        _launcherSoundVolume.value = clamped
    }

    fun isMediaVolumeLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_MEDIA_VOLUME_LOCK_ENABLED,
            DEFAULT_MEDIA_VOLUME_LOCK_ENABLED
        )
    }

    fun setMediaVolumeLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MEDIA_VOLUME_LOCK_ENABLED, enabled).apply()
        _isMediaVolumeLockEnabled.value = enabled
        MediaVolumeLockSync.push(context, enabled)
    }
}
