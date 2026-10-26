package com.crinklecodone.mochibun.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sound effects triggered by physical keypad and launcher events.
 */
enum class SoundEffect {
    NAV,
    CLICK,
    SELECT,
    BACK,
    BOUNDARY
}

/**
 * Descriptor for selectable keypad sound themes.
 */
data class SoundTheme(
    val id: String,
    val name: String,
    val description: String
)

/**
 * Low-latency keypad audio feedback coordinator.
 * Synthesizes tiny 16-bit PCM waveforms procedurally in memory on first run,
 * caching them to avoid bundling binary audio assets in the APK.
 * Audio playback is routed via Android's native SoundPool for sub-5ms tactile response.
 */
object SoundEffectManager {

    private const val TAG = "SoundEffectManager"
    private const val SAMPLE_RATE = 22050

    val SoundThemes = listOf(
        SoundTheme("kawaii", "Kawaii Pop", "Sweet resonant chimes & bubbly drops"),
        SoundTheme("retro", "Retro Keitai", "Authentic 2000s flip phone button click"),
        SoundTheme("chiptune", "8-Bit Chiptune", "Classic Game Boy & Famicom pulse waves"),
        SoundTheme("pager", "Cyber Pager", "90s Pokebell & Tamagotchi crystal pings"),
        SoundTheme("thock", "Tactile Thock", "Deep mechanical switch & clamshell acoustics"),
        SoundTheme("water", "Water Drop", "Organic calming water bubbles & droplets"),
        SoundTheme("off", "Muted / Off", "Silent keypad navigation")
    )

    private val ALL_SYNTHESIZED_THEMES = listOf("kawaii", "retro", "chiptune", "pager", "thock", "water")

    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<Pair<String, SoundEffect>, Int>()
    @Volatile
    private var isInitialized = false
    @Volatile
    private var currentStreamSetting: String? = null

    @Synchronized
    fun init(context: Context) {
        val streamPref = PreferencesManager.getLauncherAudioStream(context)
        if (isInitialized && soundPool != null && currentStreamSetting == streamPref) return

        if (soundPool != null) {
            try {
                soundPool?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed releasing SoundPool", e)
            }
            soundPool = null
            soundMap.clear()
            isInitialized = false
        }

        try {
            currentStreamSetting = streamPref
            val audioAttributes = when (streamPref) {
                PreferencesManager.AUDIO_STREAM_NOTIFICATION -> {
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                else -> {
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
            }

            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()

            soundPool = pool
            val soundDir = File(context.cacheDir, "mochibun_sounds").apply { mkdirs() }

            for (theme in ALL_SYNTHESIZED_THEMES) {
                loadTheme(soundDir, theme)
            }

            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundEffectManager", e)
        }
    }

    /**
     * Re-initializes SoundPool with updated audio attributes when audio stream preference changes.
     * Safely releases previous native audio resources to prevent leaks.
     */
    @Synchronized
    fun updateAudioStream(context: Context) {
        val streamPref = PreferencesManager.getLauncherAudioStream(context)
        if (currentStreamSetting == streamPref && soundPool != null) {
            return
        }
        init(context)
    }

    @Synchronized
    fun release() {
        try {
            soundPool?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing SoundPool", e)
        }
        soundPool = null
        soundMap.clear()
        isInitialized = false
        currentStreamSetting = null
    }

    private fun loadTheme(soundDir: File, theme: String) {
        val pool = soundPool ?: return
        for (effect in SoundEffect.entries) {
            try {
                val file = File(soundDir, "${theme}_${effect.name.lowercase()}.wav")
                if (!file.exists() || file.length() < 44) {
                    val pcm = synthesizePcm(theme, effect)
                    writeWavFile(file, pcm, SAMPLE_RATE)
                }
                val id = pool.load(file.absolutePath, 1)
                soundMap[theme to effect] = id
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading sound: $theme/${effect.name}", e)
            }
        }
    }

    /**
     * Plays a physical keypad or navigation sound with immediate, sub-5ms low latency.
     * Safely non-blocking when called from the main thread.
     *
     * @param volume optional specific volume multiplier. If negative (-1f default),
     * uses the user-configured launcher sound volume preference directly.
     */
    fun play(context: Context, effect: SoundEffect, volume: Float = -1f) {
        val mode = PreferencesManager.getKeypadSoundMode(context)
        if (mode == "off") return

        val streamPref = PreferencesManager.getLauncherAudioStream(context)
        if (!isInitialized || soundPool == null || currentStreamSetting != streamPref) {
            init(context.applicationContext)
        }

        val pool = soundPool ?: return
        val soundId = soundMap[mode to effect]
            ?: soundMap["kawaii" to effect]
            ?: return

        val volumePercent = PreferencesManager.getLauncherSoundVolume(context)
        if (volumePercent <= 0) return

        val masterVolume = (volumePercent / 100f).coerceIn(0f, 1f)
        val finalVolume = if (volume >= 0f) {
            (masterVolume * (volume / 0.8f)).coerceIn(0f, 1f)
        } else {
            masterVolume
        }

        if (finalVolume > 0f) {
            pool.play(soundId, finalVolume, finalVolume, 1, 0, 1.0f)
        }
    }

    /**
     * Synthesizes 16-bit PCM waveforms in memory.
     */
    internal fun synthesizePcm(theme: String, effect: SoundEffect): ShortArray {
        return when (theme) {
            "retro" -> synthesizeRetroPcm(effect)
            "chiptune" -> synthesizeChiptunePcm(effect)
            "pager" -> synthesizePagerPcm(effect)
            "thock" -> synthesizeThockPcm(effect)
            "water" -> synthesizeWaterPcm(effect)
            else -> synthesizeKawaiiPcm(effect)
        }
    }

    private fun synthesizeKawaiiPcm(effect: SoundEffect): ShortArray {
        return when (effect) {
            SoundEffect.NAV -> {
                // Soft bubbly tap (16ms, C6 = 1046 Hz with quick exponential decay)
                val durationMs = 16
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val freq = 1046.0
                    val envelope = exp(-t * 220.0)
                    val s = sin(2.0 * PI * freq * t) * envelope
                    (s * 20000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.CLICK -> {
                // Gentle 20ms pop: frequency drops 880 Hz -> 520 Hz
                val durationMs = 20
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val freq = 880.0 - (progress * 360.0)
                    val envelope = exp(-t * 180.0)
                    val s = sin(2.0 * PI * freq * t) * envelope
                    (s * 22000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.SELECT -> {
                // Sweet 55ms dual chime: C6 (1046 Hz) + G6 (1568 Hz) + slight E6 (1318 Hz)
                val durationMs = 55
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 60.0)
                    val s1 = sin(2.0 * PI * 1046.5 * t) * 0.5
                    val s2 = sin(2.0 * PI * 1567.98 * t) * 0.35
                    val s3 = sin(2.0 * PI * 1318.51 * t) * 0.15
                    val s = (s1 + s2 + s3) * envelope
                    (s * 26000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BACK -> {
                // Soft falling droplet: 784 Hz -> 440 Hz (30ms)
                val durationMs = 30
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val freq = 784.0 - (progress * 344.0)
                    val envelope = exp(-t * 110.0)
                    val s = sin(2.0 * PI * freq * t) * envelope
                    (s * 21000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BOUNDARY -> {
                // Muted low wooden bump (16ms, 220 Hz)
                val durationMs = 16
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 190.0)
                    val s = sin(2.0 * PI * 220.0 * t) * envelope
                    (s * 18000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }
    }

    private fun synthesizeRetroPcm(effect: SoundEffect): ShortArray {
        return when (effect) {
            SoundEffect.NAV -> {
                // Authentic Keitai plastic click: 12ms 1200 Hz sharp impulse
                val durationMs = 12
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 350.0)
                    val s = sin(2.0 * PI * 1200.0 * t) * envelope
                    (s * 24000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.CLICK -> {
                // Solid mechanical button click: 15ms 900 Hz impulse
                val durationMs = 15
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 280.0)
                    val s = sin(2.0 * PI * 900.0 * t) * envelope
                    (s * 26000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.SELECT -> {
                // High-pitch dual retro electronic confirmation: 1760 Hz + 2200 Hz (35ms)
                val durationMs = 35
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 90.0)
                    val s = (sin(2.0 * PI * 1760.0 * t) * 0.55 + sin(2.0 * PI * 2200.0 * t) * 0.45) * envelope
                    (s * 25000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BACK -> {
                // Retro dismiss tick: 440 Hz (22ms)
                val durationMs = 22
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 160.0)
                    val s = sin(2.0 * PI * 440.0 * t) * envelope
                    (s * 22000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BOUNDARY -> {
                // Mechanical boundary stop: 180 Hz (14ms)
                val durationMs = 14
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 220.0)
                    val s = sin(2.0 * PI * 180.0 * t) * envelope
                    (s * 19000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }
    }

    private fun pulseWave(phase: Double, duty: Double = 0.5): Double {
        val norm = (phase % 1.0 + 1.0) % 1.0
        return if (norm < duty) 1.0 else -1.0
    }

    private fun synthesizeChiptunePcm(effect: SoundEffect): ShortArray {
        return when (effect) {
            SoundEffect.NAV -> {
                // 14ms crisp 8-bit cursor pulse blip (1760 Hz, A6)
                val durationMs = 14
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 200.0)
                    val s = pulseWave(t * 1760.0, 0.5) * envelope
                    (s * 17000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.CLICK -> {
                // 22ms snappy 8-bit pitch slide (880 Hz -> 1320 Hz) with 25% pulse width
                val durationMs = 22
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val freq = 880.0 + (progress * 440.0)
                    val envelope = exp(-t * 140.0)
                    val s = pulseWave(t * freq, 0.25) * envelope
                    (s * 18000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.SELECT -> {
                // 60ms 8-bit coin pickup arpeggio (B5 987 Hz -> E6 1318 Hz)
                val durationMs = 60
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val isFirstNote = t < 0.030
                    val freq = if (isFirstNote) 987.77 else 1318.51
                    val noteT = if (isFirstNote) t else t - 0.030
                    val envelope = exp(-noteT * 75.0)
                    val s = pulseWave(t * freq, 0.5) * envelope
                    (s * 20000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BACK -> {
                // 28ms descending 8-bit blip (660 Hz -> 330 Hz)
                val durationMs = 28
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val freq = 660.0 - (progress * 330.0)
                    val envelope = exp(-t * 120.0)
                    val s = pulseWave(t * freq, 0.5) * envelope
                    (s * 17000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BOUNDARY -> {
                // 18ms low 110 Hz square bounce
                val durationMs = 18
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 160.0)
                    val s = pulseWave(t * 110.0, 0.5) * envelope
                    (s * 16000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }
    }

    private fun synthesizePagerPcm(effect: SoundEffect): ShortArray {
        return when (effect) {
            SoundEffect.NAV -> {
                // 12ms high-crystal Pokebell micro-ping (C7 = 2093 Hz)
                val durationMs = 12
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 260.0)
                    val s = sin(2.0 * PI * 2093.0 * t) * envelope
                    (s * 21000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.CLICK -> {
                // 20ms cyber FM chirp (1800 Hz modulated by 300 Hz)
                val durationMs = 20
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val mod = sin(2.0 * PI * 300.0 * t) * 350.0
                    val envelope = exp(-t * 180.0)
                    val s = sin(2.0 * PI * (1800.0 + mod) * t) * envelope
                    (s * 20000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.SELECT -> {
                // 66ms Y2K digital tri-tone chime: G6 (1568 Hz) -> B6 (1975 Hz) -> D7 (2349 Hz)
                val durationMs = 66
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val step = (t / 0.022).toInt().coerceIn(0, 2)
                    val freq = when (step) {
                        0 -> 1567.98
                        1 -> 1975.53
                        else -> 2349.32
                    }
                    val stepT = t - (step * 0.022)
                    val envelope = exp(-stepT * 120.0)
                    val s = sin(2.0 * PI * freq * t) * envelope
                    (s * 23000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BACK -> {
                // 24ms fast laser sweep: 1400 Hz -> 400 Hz
                val durationMs = 24
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val freq = 1400.0 - (progress * 1000.0)
                    val envelope = exp(-t * 150.0)
                    val s = sin(2.0 * PI * freq * t) * envelope
                    (s * 19000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BOUNDARY -> {
                // 16ms double micro-chirp
                val durationMs = 16
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val localT = if (t < 0.008) t else t - 0.008
                    val envelope = exp(-localT * 320.0)
                    val s = sin(2.0 * PI * 650.0 * localT) * envelope
                    (s * 17000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }
    }

    private fun synthesizeThockPcm(effect: SoundEffect): ShortArray {
        return when (effect) {
            SoundEffect.NAV -> {
                // 14ms crisp latch + 480 Hz body resonance
                val durationMs = 14
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val latch = if (t < 0.0025) sin(2.0 * PI * 2400.0 * t) * 0.4 else 0.0
                    val body = sin(2.0 * PI * 480.0 * t) * exp(-t * 280.0) * 0.6
                    val s = latch + body
                    (s * 23000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.CLICK -> {
                // 20ms deep mechanical spacebar/switch thock (320 Hz + 160 Hz sub)
                val durationMs = 20
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s1 = sin(2.0 * PI * 320.0 * t) * 0.65
                    val s2 = sin(2.0 * PI * 160.0 * t) * 0.35
                    val envelope = exp(-t * 210.0)
                    val s = (s1 + s2) * envelope
                    (s * 27000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.SELECT -> {
                // 36ms double-latch tactile confirmation click
                val durationMs = 36
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s1 = sin(2.0 * PI * 520.0 * t) * exp(-t * 260.0) * 0.55
                    val s2 = if (t >= 0.014) {
                        val t2 = t - 0.014
                        sin(2.0 * PI * 680.0 * t2) * exp(-t2 * 240.0) * 0.45
                    } else 0.0
                    val s = s1 + s2
                    (s * 26000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BACK -> {
                // 16ms return stroke switch release (400 Hz)
                val durationMs = 16
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 260.0)
                    val s = sin(2.0 * PI * 400.0 * t) * envelope
                    (s * 21000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BOUNDARY -> {
                // 16ms muted bottom-out thud (140 Hz low damped)
                val durationMs = 16
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 220.0)
                    val s = sin(2.0 * PI * 140.0 * t) * envelope
                    (s * 22000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }
    }

    private fun synthesizeWaterPcm(effect: SoundEffect): ShortArray {
        return when (effect) {
            SoundEffect.NAV -> {
                // 22ms rising bubble pop: 850 Hz -> 1350 Hz
                val durationMs = 22
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val freq = 850.0 + (progress * 500.0)
                    val envelope = sin(PI * progress) * exp(-t * 70.0)
                    val s = sin(2.0 * PI * freq * t) * envelope
                    (s * 23000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.CLICK -> {
                // 26ms crystal dewdrop ping (1568 Hz)
                val durationMs = 26
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val envelope = sin(PI * progress) * exp(-t * 85.0)
                    val s = sin(2.0 * PI * 1568.0 * t) * envelope
                    (s * 24000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.SELECT -> {
                // 55ms dual ripple droplet chord (1200 Hz + 1800 Hz)
                val durationMs = 55
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s1 = sin(2.0 * PI * 1200.0 * t) * 0.55
                    val s2 = sin(2.0 * PI * 1800.0 * t) * 0.45
                    val envelope = exp(-t * 65.0)
                    val s = (s1 + s2) * envelope
                    (s * 25000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BACK -> {
                // 30ms water droplet drip (750 Hz -> 420 Hz)
                val durationMs = 30
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val freq = 750.0 - (progress * 330.0)
                    val envelope = exp(-t * 85.0)
                    val s = sin(2.0 * PI * freq * t) * envelope
                    (s * 22000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.BOUNDARY -> {
                // 18ms hollow wooden/underwater tap (260 Hz)
                val durationMs = 18
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = exp(-t * 170.0)
                    val s = sin(2.0 * PI * 260.0 * t) * envelope
                    (s * 19000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }
    }

    /**
     * Writes standard 44-byte RIFF/WAVE header and 16-bit PCM little-endian samples.
     */
    internal fun writeWavFile(file: File, pcmData: ShortArray, sampleRate: Int) {
        val byteDataSize = pcmData.size * 2
        val totalFileSize = 36 + byteDataSize
        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(totalFileSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size for PCM
            putShort(1) // AudioFormat 1 = PCM
            putShort(1) // NumChannels = 1 (Mono)
            putInt(sampleRate)
            putInt(sampleRate * 2) // ByteRate = SampleRate * NumChannels * BitsPerSample/8
            putShort(2) // BlockAlign = NumChannels * BitsPerSample/8
            putShort(16) // BitsPerSample
            put("data".toByteArray())
            putInt(byteDataSize)
        }

        val pcmBuffer = ByteBuffer.allocate(byteDataSize).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcmData) {
                putShort(sample)
            }
        }

        FileOutputStream(file).use { out ->
            out.write(header.array())
            out.write(pcmBuffer.array())
            out.flush()
        }
    }
}

typealias KeypadSoundManager = SoundEffectManager
