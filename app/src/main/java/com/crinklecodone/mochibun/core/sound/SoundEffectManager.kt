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
 * Low-latency keypad audio feedback coordinator.
 * Synthesizes tiny 16-bit PCM waveforms procedurally in memory on first run,
 * caching them to avoid bundling binary audio assets in the APK.
 * Audio playback is routed via Android's native SoundPool for sub-5ms tactile response.
 */
object SoundEffectManager {

    private const val TAG = "SoundEffectManager"
    private const val SAMPLE_RATE = 22050

    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<Pair<String, SoundEffect>, Int>()
    @Volatile
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build()

            soundPool = pool
            val soundDir = File(context.cacheDir, "mochibun_sounds").apply { mkdirs() }

            loadTheme(soundDir, "kawaii")
            loadTheme(soundDir, "retro")

            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundEffectManager", e)
        }
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
     */
    fun play(context: Context, effect: SoundEffect, volume: Float = 0.8f) {
        val mode = PreferencesManager.getKeypadSoundMode(context)
        if (mode == "off") return

        if (!isInitialized) {
            init(context.applicationContext)
        }

        val pool = soundPool ?: return
        val soundId = soundMap[mode to effect]
            ?: soundMap["kawaii" to effect]
            ?: return

        pool.play(soundId, volume, volume, 1, 0, 1.0f)
    }

    /**
     * Synthesizes 16-bit PCM waveforms in memory.
     */
    internal fun synthesizePcm(theme: String, effect: SoundEffect): ShortArray {
        return if (theme == "retro") {
            synthesizeRetroPcm(effect)
        } else {
            synthesizeKawaiiPcm(effect)
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
