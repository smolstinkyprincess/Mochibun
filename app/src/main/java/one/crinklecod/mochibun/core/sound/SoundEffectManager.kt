package one.crinklecod.mochibun.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    BOUNDARY,
    SNAP,
    FEED,
    PET
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
 *
 * Only the active sound theme is synthesized and loaded into [SoundPool] (and always off the
 * main thread) so startup stays fast and muted profiles allocate zero native audio buffers.
 */
object SoundEffectManager {

    private const val TAG = "SoundEffectManager"
    private const val SAMPLE_RATE = 22050
    private const val PENDING_PLAY_MAX_AGE_MS = 500L

    val SoundThemes = listOf(
        SoundTheme("kawaii", "Kawaii Pop", "Sweet resonant chimes & bubbly drops"),
        SoundTheme("retro", "Retro Keitai", "Authentic 2000s flip phone button click"),
        SoundTheme("chiptune", "8-Bit Chiptune", "Classic Game Boy & Famicom pulse waves"),
        SoundTheme("pager", "Cyber Pager", "90s Pokebell & Tamagotchi crystal pings"),
        SoundTheme("thock", "Tactile Thock", "Deep mechanical switch & clamshell acoustics"),
        SoundTheme("water", "Water Drop", "Organic calming water bubbles & droplets"),
        SoundTheme("bundle", "Custom Bundle", "Sound pack imported from a .mochibundle"),
        SoundTheme("off", "Muted / Off", "Silent keypad navigation")
    )

    // ── Sound pack imported from a .mochibundle ("sounds/<cue>.ogg") ─────────────────────────

    /** Sound-profile id of the imported pack; cues it lacks use [BUNDLE_FALLBACK_THEME]. */
    const val BUNDLE_THEME_ID = "bundle"
    const val BUNDLE_SOUNDS_DIR_NAME = "bundle_sounds"
    /** Per-cue uncompressed cap: SoundPool decodes each sample fully into native memory. */
    const val MAX_BUNDLE_SOUND_BYTES = 256L * 1024L
    /** Synthesized theme used for any cue a bundle pack doesn't include (or fails to decode). */
    const val BUNDLE_FALLBACK_THEME = "kawaii"
    private const val BUNDLE_PACK_NAME_FILE = "pack_name.txt"
    private const val BUNDLE_FALLBACK_NAME = "Custom Bundle"
    private const val MAX_PACK_NAME_CHARS = 40

    /** File name of [effect] inside a bundle (`sounds/<name>`) and in [BUNDLE_SOUNDS_DIR_NAME]. */
    fun bundleCueFileName(effect: SoundEffect): String = "${effect.name.lowercase()}.ogg"

    /** Every supported cue file name, e.g. `nav.ogg`, `click.ogg`, ... */
    val BundleCueFileNames: List<String> = SoundEffect.entries.map { bundleCueFileName(it) }

    fun effectForBundleCueFileName(fileName: String): SoundEffect? =
        SoundEffect.entries.firstOrNull { bundleCueFileName(it) == fileName.lowercase() }

    /** True for an Ogg container page header (`OggS`). */
    fun isOggHeader(header: ByteArray): Boolean =
        header.size >= 4 &&
            header[0] == 'O'.code.toByte() &&
            header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() &&
            header[3] == 'S'.code.toByte()

    /** Size + `OggS` header check; SoundPool reports real decode failures asynchronously. */
    fun isValidBundleCueFile(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_BUNDLE_SOUND_BYTES) return false
        val header = ByteArray(4)
        val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
        return read == 4 && isOggHeader(header)
    }

    fun bundleSoundsDir(context: Context): File = File(context.filesDir, BUNDLE_SOUNDS_DIR_NAME)

    /**
     * Where each cue of the bundle profile comes from: the pack's `.ogg`, or null meaning the
     * synthesized [BUNDLE_FALLBACK_THEME] sample.
     */
    internal fun resolveBundleCueSources(bundleDir: File): Map<SoundEffect, File?> =
        SoundEffect.entries.associateWith { effect ->
            File(bundleDir, bundleCueFileName(effect)).takeIf { isValidBundleCueFile(it) }
        }

    /** Valid installed cue files (what an export packs as `sounds/<cue>.ogg`). */
    fun bundleSoundFiles(context: Context): Map<SoundEffect, File> =
        resolveBundleCueSources(bundleSoundsDir(context))
            .mapNotNull { (effect, file) -> file?.let { effect to it } }
            .toMap()

    private val _bundleSoundPackName = MutableStateFlow<String?>(null)

    /** Display name of the installed bundle sound pack, or null when none is installed. */
    val bundleSoundPackName: StateFlow<String?> = _bundleSoundPackName.asStateFlow()

    private fun sanitizePackName(raw: String?): String? =
        raw?.filterNot { it.isISOControl() }?.trim()?.take(MAX_PACK_NAME_CHARS)?.trim()?.takeIf { it.isNotEmpty() }

    /** Re-reads [BUNDLE_SOUNDS_DIR_NAME] into [bundleSoundPackName]. Reads files: call off the main thread. */
    fun refreshBundleSoundPackState(context: Context) {
        val dir = bundleSoundsDir(context)
        _bundleSoundPackName.value = if (bundleSoundFiles(context).isEmpty()) {
            null
        } else {
            sanitizePackName(runCatching { File(dir, BUNDLE_PACK_NAME_FILE).readText() }.getOrNull())
                ?: BUNDLE_FALLBACK_NAME
        }
    }

    /**
     * Replaces the installed pack with the valid entries of [staged] (oversized / non-Ogg files
     * are dropped). The new pack is assembled in a sibling temp dir, then swapped in, so a
     * failure never leaves a half-written pack. Call off the main thread.
     *
     * @return number of cues installed
     */
    fun installBundleSounds(context: Context, staged: Map<SoundEffect, File>, packName: String?): Result<Int> {
        return try {
            val valid = staged.filterValues { isValidBundleCueFile(it) }
            if (valid.isEmpty()) {
                return Result.failure(IllegalArgumentException("no valid .ogg sound cues"))
            }
            val dir = bundleSoundsDir(context)
            val temp = File(context.filesDir, "$BUNDLE_SOUNDS_DIR_NAME.tmp")
            temp.deleteRecursively()
            if (!temp.mkdirs()) return Result.failure(IllegalStateException("couldn't create sound folder"))
            valid.forEach { (effect, file) ->
                file.copyTo(File(temp, bundleCueFileName(effect)), overwrite = true)
            }
            sanitizePackName(packName)?.let { File(temp, BUNDLE_PACK_NAME_FILE).writeText(it) }

            dir.deleteRecursively()
            if (!temp.renameTo(dir)) {
                temp.deleteRecursively()
                return Result.failure(IllegalStateException("couldn't save sound pack"))
            }
            refreshBundleSoundPackState(context)
            reloadBundleSounds(context)
            Result.success(valid.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to install bundle sounds", e)
            Result.failure(e)
        }
    }

    /** Deletes the bundle pack; the "bundle" profile then plays [BUNDLE_FALLBACK_THEME] sounds. */
    fun resetBundleSounds(context: Context) {
        bundleSoundsDir(context).deleteRecursively()
        refreshBundleSoundPackState(context)
        reloadBundleSounds(context)
    }

    /**
     * Drops cached "bundle" samples so the next play (or an immediate reload, when the bundle
     * profile is active and the pool exists) picks up the new `.ogg` files.
     */
    fun reloadBundleSounds(context: Context) {
        val appContext = context.applicationContext ?: context
        loaderExecutor.execute {
            synchronized(poolLock) {
                val pool = soundPool
                for (effect in SoundEffect.entries) {
                    val oldId = soundMap.remove(BUNDLE_THEME_ID to effect) ?: continue
                    readySampleIds.remove(oldId)
                    bundleSampleEffects.remove(oldId)
                    runCatching { pool?.unload(oldId) }
                }
                loadedThemes.remove(BUNDLE_THEME_ID)
                if (pool != null && PreferencesManager.getKeypadSoundMode(appContext) == BUNDLE_THEME_ID) {
                    loadThemeLocked(
                        File(appContext.cacheDir, SYNTH_CACHE_DIR_NAME).apply { mkdirs() },
                        bundleSoundsDir(appContext),
                        BUNDLE_THEME_ID
                    )
                }
            }
        }
    }

    private val ALL_SYNTHESIZED_THEMES = listOf("kawaii", "retro", "chiptune", "pager", "thock", "water")
    private val SYNTHESIZED_THEME_SET = ALL_SYNTHESIZED_THEMES.toSet()
    private const val SYNTH_CACHE_DIR_NAME = "mochibun_sounds"

    /**
     * Theme id one step from [currentId] in [SoundThemes] order, wrapping; unknown ids start at
     * the first theme. The "bundle" profile is only part of the cycle when [includeBundle]
     * (i.e. a bundle sound pack is installed).
     */
    fun nextThemeId(currentId: String, direction: Int, includeBundle: Boolean = false): String {
        val cycle = if (includeBundle) SoundThemes else SoundThemes.filterNot { it.id == BUNDLE_THEME_ID }
        val index = cycle.indexOfFirst { it.id == currentId }
        if (index < 0) return cycle.first().id
        return cycle[Math.floorMod(index + direction, cycle.size)].id
    }

    /**
     * Display name for a theme id, e.g. "retro" -> "Retro Keitai". The bundle profile shows
     * [bundlePackName] when known.
     */
    fun themeName(id: String, bundlePackName: String? = null): String {
        if (id == BUNDLE_THEME_ID && !bundlePackName.isNullOrBlank()) return bundlePackName
        return SoundThemes.firstOrNull { it.id == id }?.name ?: SoundThemes.first().name
    }

    private data class PendingPlay(
        val soundId: Int,
        val volume: Float,
        val requestedAtMs: Long
    )

    private val poolLock = Any()
    private val loaderExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MochibunSoundLoader").apply { isDaemon = true }
    }

    @Volatile
    private var soundPool: SoundPool? = null
    private val soundMap = ConcurrentHashMap<Pair<String, SoundEffect>, Int>()
    private val loadedThemes = ConcurrentHashMap.newKeySet<String>()
    private val readySampleIds = ConcurrentHashMap.newKeySet<Int>()
    /** Samples loaded from bundle `.ogg` files, so a decode failure can fall back per cue. */
    private val bundleSampleEffects = ConcurrentHashMap<Int, SoundEffect>()

    @Volatile
    private var pendingPlay: PendingPlay? = null

    @Volatile
    private var currentStreamSetting: String? = null

    /**
     * Schedules background initialization for only the active theme (or `"thock"` if keypad sounds
     * are `"off"` while lid-close snap is enabled). If both are disabled, no [SoundPool] or WAV
     * files are allocated at startup.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        loaderExecutor.execute { refreshBundleSoundPackState(appContext) }
        val mode = PreferencesManager.getKeypadSoundMode(appContext)
        val snapEnabled = PreferencesManager.isLidCloseSnapSoundEnabled(appContext)
        if (mode == "off" && !snapEnabled) return

        val initialTheme = resolveActiveTheme(mode, fallback = "thock")
        val streamPref = PreferencesManager.getLauncherAudioStream(appContext)
        scheduleLoadThemes(appContext, listOf(initialTheme), streamPref)
    }

    /**
     * Preloads all synthesized themes asynchronously (used when entering Sound Settings so
     * live cycling previews respond immediately).
     */
    fun preloadAllThemes(context: Context) {
        val appContext = context.applicationContext
        val streamPref = PreferencesManager.getLauncherAudioStream(appContext)
        loaderExecutor.execute { refreshBundleSoundPackState(appContext) }
        // Settings can cycle to the bundle profile, so warm it too when a pack is installed
        val themes = if (_bundleSoundPackName.value != null) {
            ALL_SYNTHESIZED_THEMES + BUNDLE_THEME_ID
        } else {
            ALL_SYNTHESIZED_THEMES
        }
        scheduleLoadThemes(appContext, themes, streamPref)
    }

    /**
     * Re-initializes SoundPool off the main thread when the audio stream preference changes.
     */
    fun updateAudioStream(context: Context) {
        val appContext = context.applicationContext
        val streamPref = PreferencesManager.getLauncherAudioStream(appContext)
        if (currentStreamSetting == streamPref && soundPool != null) {
            return
        }
        val mode = PreferencesManager.getKeypadSoundMode(appContext)
        val snapEnabled = PreferencesManager.isLidCloseSnapSoundEnabled(appContext)
        if (soundPool == null && mode == "off" && !snapEnabled) {
            currentStreamSetting = streamPref
            return
        }
        val themesToReload = buildSet {
            addAll(loadedThemes)
            if (mode != "off") add(resolveActiveTheme(mode, fallback = "kawaii"))
            else if (snapEnabled) add("thock")
        }.toList()
        if (themesToReload.isEmpty()) return
        scheduleLoadThemes(appContext, themesToReload, streamPref)
    }

    fun release() {
        synchronized(poolLock) {
            try {
                soundPool?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing SoundPool", e)
            }
            soundPool = null
            soundMap.clear()
            loadedThemes.clear()
            readySampleIds.clear()
            bundleSampleEffects.clear()
            pendingPlay = null
            currentStreamSetting = null
        }
    }

    private fun resolveActiveTheme(mode: String, fallback: String): String =
        if (mode in SYNTHESIZED_THEME_SET || mode == BUNDLE_THEME_ID) mode else fallback

    private fun scheduleLoadThemes(
        appContext: Context,
        themes: List<String>,
        streamPref: String,
        pendingEffectForFirstTheme: SoundEffect? = null,
        pendingVolume: Float = 0f
    ) {
        loaderExecutor.execute {
            synchronized(poolLock) {
                ensurePoolLocked(streamPref)
                val soundDir = File(appContext.cacheDir, SYNTH_CACHE_DIR_NAME).apply { mkdirs() }
                synthCacheDir = soundDir
                val bundleDir = bundleSoundsDir(appContext)
                for (theme in themes) {
                    if (!loadedThemes.contains(theme)) {
                        loadThemeLocked(soundDir, bundleDir, theme)
                    }
                }
                if (pendingEffectForFirstTheme != null && themes.isNotEmpty() && pendingVolume > 0f) {
                    val soundId = soundMap[themes.first() to pendingEffectForFirstTheme]
                    val pool = soundPool
                    if (soundId != null && pool != null) {
                        if (readySampleIds.contains(soundId)) {
                            pool.play(soundId, pendingVolume, pendingVolume, 1, 0, 1.0f)
                        } else {
                            pendingPlay = PendingPlay(
                                soundId = soundId,
                                volume = pendingVolume,
                                requestedAtMs = SystemClock.uptimeMillis()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun ensurePoolLocked(streamPref: String) {
        if (soundPool != null && currentStreamSetting == streamPref) return

        if (soundPool != null) {
            try {
                soundPool?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed releasing SoundPool", e)
            }
            soundPool = null
            soundMap.clear()
            loadedThemes.clear()
            readySampleIds.clear()
            bundleSampleEffects.clear()
            pendingPlay = null
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

            pool.setOnLoadCompleteListener { sp, sampleId, status ->
                val bundleEffect = bundleSampleEffects.remove(sampleId)
                if (status != 0 && bundleEffect != null) {
                    // A bundle .ogg passed the header check but SoundPool couldn't decode it
                    loaderExecutor.execute { replaceFailedBundleSample(sp, sampleId, bundleEffect) }
                }
                if (status == 0) {
                    readySampleIds.add(sampleId)
                    val pending = pendingPlay
                    if (pending != null && pending.soundId == sampleId) {
                        pendingPlay = null
                        if (SystemClock.uptimeMillis() - pending.requestedAtMs <= PENDING_PLAY_MAX_AGE_MS) {
                            sp.play(sampleId, pending.volume, pending.volume, 1, 0, 1.0f)
                        }
                    }
                }
            }

            soundPool = pool
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundEffectManager", e)
        }
    }

    @Volatile
    private var synthCacheDir: File? = null

    /**
     * Loads every cue of [theme] into the pool. For the bundle profile each cue prefers the
     * pack's `.ogg` from [bundleDir] and otherwise uses the synthesized fallback theme, so a
     * pack with only `click.ogg` still has every other sound. Always runs on [loaderExecutor].
     */
    private fun loadThemeLocked(soundDir: File, bundleDir: File, theme: String) {
        val pool = soundPool ?: return
        val bundleSources = if (theme == BUNDLE_THEME_ID) resolveBundleCueSources(bundleDir) else emptyMap()
        var loadedAny = false
        for (effect in SoundEffect.entries) {
            try {
                val bundleFile = bundleSources[effect]
                val file = bundleFile ?: synthesizedSampleFile(soundDir, theme, effect)
                val id = pool.load(file.absolutePath, 1)
                if (id != 0) {
                    soundMap[theme to effect] = id
                    if (bundleFile != null) bundleSampleEffects[id] = effect
                    loadedAny = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading sound: $theme/${effect.name}", e)
            }
        }
        if (loadedAny) {
            loadedThemes.add(theme)
        }
    }

    /** Cached synthesized WAV for [theme] (the bundle profile maps to [BUNDLE_FALLBACK_THEME]). */
    private fun synthesizedSampleFile(soundDir: File, theme: String, effect: SoundEffect): File {
        val synthTheme = if (theme in SYNTHESIZED_THEME_SET) theme else BUNDLE_FALLBACK_THEME
        val file = File(soundDir, "${synthTheme}_${effect.name.lowercase()}.wav")
        if (!file.exists() || file.length() < 44) {
            writeWavFile(file, synthesizePcm(synthTheme, effect), SAMPLE_RATE)
        }
        return file
    }

    /** Swaps an undecodable bundle sample for the synthesized fallback cue. */
    private fun replaceFailedBundleSample(sp: SoundPool, failedId: Int, effect: SoundEffect) {
        synchronized(poolLock) {
            val pool = soundPool
            val soundDir = synthCacheDir
            if (pool !== sp || soundDir == null) return
            if (soundMap[BUNDLE_THEME_ID to effect] != failedId) return
            try {
                runCatching { pool.unload(failedId) }
                val file = synthesizedSampleFile(soundDir, BUNDLE_THEME_ID, effect)
                val id = pool.load(file.absolutePath, 1)
                if (id != 0) soundMap[BUNDLE_THEME_ID to effect] = id else soundMap.remove(BUNDLE_THEME_ID to effect)
                Log.w(TAG, "Bundle sound ${bundleCueFileName(effect)} failed to decode; using fallback")
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading fallback for bundle cue ${effect.name}", e)
            }
        }
    }

    /**
     * Plays a physical keypad or navigation sound with immediate, sub-5ms low latency.
     * Safely non-blocking when called from the main thread; lazily loads the active theme
     * off the main thread on first use.
     *
     * @param volume optional specific volume multiplier. If negative (-1f default),
     * uses the user-configured launcher sound volume preference directly.
     */
    fun play(context: Context, effect: SoundEffect, volume: Float = -1f) {
        val mode = PreferencesManager.getKeypadSoundMode(context)
        if (mode == "off") return

        val volumePercent = PreferencesManager.getLauncherSoundVolume(context)
        if (volumePercent <= 0) return

        val masterVolume = (volumePercent / 100f).coerceIn(0f, 1f)
        val finalVolume = if (volume >= 0f) {
            (masterVolume * (volume / 0.8f)).coerceIn(0f, 1f)
        } else {
            masterVolume
        }
        if (finalVolume <= 0f) return

        val targetTheme = resolveActiveTheme(mode, fallback = "kawaii")
        val streamPref = PreferencesManager.getLauncherAudioStream(context)
        val pool = soundPool

        if (pool == null || currentStreamSetting != streamPref || !loadedThemes.contains(targetTheme)) {
            scheduleLoadThemes(
                appContext = context.applicationContext,
                themes = listOf(targetTheme),
                streamPref = streamPref,
                pendingEffectForFirstTheme = effect,
                pendingVolume = finalVolume
            )
            return
        }

        val soundId = soundMap[targetTheme to effect] ?: return
        val streamId = pool.play(soundId, finalVolume, finalVolume, 1, 0, 1.0f)
        if (streamId == 0 && !readySampleIds.contains(soundId)) {
            pendingPlay = PendingPlay(
                soundId = soundId,
                volume = finalVolume,
                requestedAtMs = SystemClock.uptimeMillis()
            )
        }
    }

    /**
     * Plays the clamshell close snap. Unlike [play], it still sounds when keypad sounds are off,
     * falling back to the "thock" theme.
     */
    fun playSnap(context: Context) {
        if (!PreferencesManager.isLidCloseSnapSoundEnabled(context)) return
        val volume = (PreferencesManager.getLauncherSoundVolume(context) / 100f).coerceIn(0f, 1f)
        if (volume <= 0f) return

        val rawMode = PreferencesManager.getKeypadSoundMode(context).let { if (it == "off") "thock" else it }
        val targetTheme = resolveActiveTheme(rawMode, fallback = "thock")
        val streamPref = PreferencesManager.getLauncherAudioStream(context)
        val pool = soundPool

        if (pool == null || currentStreamSetting != streamPref || !loadedThemes.contains(targetTheme)) {
            scheduleLoadThemes(
                appContext = context.applicationContext,
                themes = listOf(targetTheme),
                streamPref = streamPref,
                pendingEffectForFirstTheme = SoundEffect.SNAP,
                pendingVolume = volume
            )
            return
        }

        val soundId = soundMap[targetTheme to SoundEffect.SNAP] ?: return
        val streamId = pool.play(soundId, volume, volume, 1, 0, 1.0f)
        if (streamId == 0 && !readySampleIds.contains(soundId)) {
            pendingPlay = PendingPlay(
                soundId = soundId,
                volume = volume,
                requestedAtMs = SystemClock.uptimeMillis()
            )
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

            SoundEffect.SNAP -> {
                // 30ms resonant chime pop with quick snap decay (2093 Hz + 1046 Hz)
                val durationMs = 30
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s = (sin(2.0 * PI * 2093.0 * t) * 0.6 + sin(2.0 * PI * 1046.5 * t) * 0.4) * exp(-t * 170.0)
                    (s * 22000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.FEED -> {
                // Cheerful ascending two-note nibble/sparkle chime (85ms: E6 -> A6 + C7 sparkle)
                val durationMs = 85
                val samples = (SAMPLE_RATE * durationMs / 1000)
                val splitSec = 0.036
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s = if (t < splitSec) {
                        val env = sin(PI * (t / splitSec)) * exp(-t * 35.0)
                        sin(2.0 * PI * 1318.51 * t) * env
                    } else {
                        val t2 = t - splitSec
                        val env = exp(-t2 * 45.0) * (1.0 - exp(-t2 * 420.0))
                        (sin(2.0 * PI * 1760.0 * t2) * 0.65 + sin(2.0 * PI * 2093.0 * t2) * 0.35) * env
                    }
                    (s * 25000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.PET -> {
                // Soft warm purr/chirp chime (95ms: gentle purr swell into a soft C6 chirp)
                val durationMs = 95
                val samples = (SAMPLE_RATE * durationMs / 1000)
                val splitSec = 0.048
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s = if (t < splitSec) {
                        val purrMod = 0.65 + 0.35 * sin(2.0 * PI * 28.0 * t)
                        val env = sin(PI * (t / splitSec))
                        sin(2.0 * PI * 340.0 * t) * purrMod * env * 0.85
                    } else {
                        val t2 = t - splitSec
                        val progress = t2 / (durationMs / 1000.0 - splitSec)
                        val freq = 880.0 + progress * 220.0
                        val env = sin(PI * progress.coerceIn(0.0, 1.0)) * exp(-t2 * 32.0)
                        sin(2.0 * PI * freq * t2) * env
                    }
                    (s * 22000).toInt().coerceIn(-32767, 32767).toShort()
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

            SoundEffect.SNAP -> {
                // 25ms plastic flip latch click (1500 Hz impulse + 300 Hz body)
                val durationMs = 25
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s = (sin(2.0 * PI * 1500.0 * t) * exp(-t * 400.0) * 0.6 + sin(2.0 * PI * 300.0 * t) * exp(-t * 180.0) * 0.4)
                    (s * 26000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.FEED -> {
                // 70ms ascending two-note keitai treat chime (1046 Hz -> 1568 Hz)
                val durationMs = 70
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val isFirst = t < 0.032
                    val localT = if (isFirst) t else t - 0.032
                    val freq = if (isFirst) 1046.5 else 1567.98
                    val s = sin(2.0 * PI * freq * localT) * exp(-localT * 65.0)
                    (s * 24000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.PET -> {
                // 75ms warm keitai purr-chirp (360 Hz soft pulse -> 880 Hz chirp)
                val durationMs = 75
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val isFirst = t < 0.035
                    val s = if (isFirst) {
                        val mod = 0.6 + 0.4 * sin(2.0 * PI * 30.0 * t)
                        sin(2.0 * PI * 360.0 * t) * mod * sin(PI * (t / 0.035))
                    } else {
                        val t2 = t - 0.035
                        sin(2.0 * PI * 880.0 * t2) * exp(-t2 * 70.0)
                    }
                    (s * 21000).toInt().coerceIn(-32767, 32767).toShort()
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

            SoundEffect.SNAP -> {
                // 30ms dual-pulse square snap (1320 Hz then 660 Hz)
                val durationMs = 30
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s = (if (t < 0.012) pulseWave(t * 1320.0, 0.25) else pulseWave(t * 660.0, 0.5)) * exp(-t * 110.0)
                    (s * 18000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.FEED -> {
                // 80ms 8-bit ascending two-note treat arpeggio (C6 -> G6)
                val durationMs = 80
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val isFirst = t < 0.036
                    val noteT = if (isFirst) t else t - 0.036
                    val s = if (isFirst) {
                        pulseWave(t * 1046.5, 0.25) * exp(-noteT * 55.0)
                    } else {
                        pulseWave(t * 1567.98, 0.5) * exp(-noteT * 50.0)
                    }
                    (s * 19000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.PET -> {
                // 80ms warm 8-bit purr-chirp (C4 square purr -> C5 chirp)
                val durationMs = 80
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val isFirst = t < 0.040
                    val noteT = if (isFirst) t else t - 0.040
                    val freq = if (isFirst) 261.63 else 523.25
                    val env = if (isFirst) sin(PI * (t / 0.040)) * 0.7 else exp(-noteT * 55.0)
                    val s = pulseWave(t * freq, 0.5) * env
                    (s * 16500).toInt().coerceIn(-32767, 32767).toShort()
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

            SoundEffect.SNAP -> {
                // 25ms crystal dual-tone click (2637 Hz + 3136 Hz)
                val durationMs = 25
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s = (sin(2.0 * PI * 2637.0 * t) * 0.5 + sin(2.0 * PI * 3136.0 * t) * 0.5) * exp(-t * 220.0)
                    (s * 21000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.FEED -> {
                // 76ms crystal Pokebell two-note ascending chime (A6 -> E7)
                val durationMs = 76
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val isFirst = t < 0.034
                    val localT = if (isFirst) t else t - 0.034
                    val freq = if (isFirst) 1760.0 else 2637.02
                    val s = sin(2.0 * PI * freq * localT) * exp(-localT * 60.0)
                    (s * 22000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.PET -> {
                // 80ms soft cyber purr/chirp ping (784 Hz warm swell -> 1175 Hz chirp)
                val durationMs = 80
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val freq = 784.0 + progress * 391.0
                    val env = sin(PI * progress) * exp(-t * 24.0)
                    val s = sin(2.0 * PI * freq * t) * env
                    (s * 20000).toInt().coerceIn(-32767, 32767).toShort()
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

            SoundEffect.SNAP -> {
                // 35ms mechanical latch: 2400 Hz transient + damped 380 Hz casing resonance
                val durationMs = 35
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val latch = if (t < 0.003) sin(2.0 * PI * 2400.0 * t) * 0.5 else 0.0
                    val s = latch + sin(2.0 * PI * 380.0 * t) * exp(-t * 150.0) * 0.6
                    (s * 26000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.FEED -> {
                // 70ms warm wooden marimba double-tap (520 Hz -> 780 Hz)
                val durationMs = 70
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val s1 = sin(2.0 * PI * 520.0 * t) * exp(-t * 110.0) * 0.55
                    val s2 = if (t >= 0.028) {
                        val t2 = t - 0.028
                        sin(2.0 * PI * 780.0 * t2) * exp(-t2 * 95.0) * 0.65
                    } else 0.0
                    ( (s1 + s2) * 25000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.PET -> {
                // 80ms cozy wooden purr resonance into soft 480 Hz bloom
                val durationMs = 80
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val purr = sin(2.0 * PI * 240.0 * t) * (0.65 + 0.35 * sin(2.0 * PI * 26.0 * t)) * 0.6
                    val chirp = sin(2.0 * PI * 480.0 * t) * 0.4
                    val env = sin(PI * progress) * exp(-t * 22.0)
                    ((purr + chirp) * env * 22000).toInt().coerceIn(-32767, 32767).toShort()
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

            SoundEffect.SNAP -> {
                // 30ms deep droplet pop (520 Hz -> 260 Hz)
                val durationMs = 30
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val freq = 520.0 - (i.toDouble() / samples * 260.0)
                    val s = sin(2.0 * PI * freq * t) * exp(-t * 100.0)
                    (s * 23000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.FEED -> {
                // 80ms cheerful ascending twin bubble sparkle (900->1250 Hz then 1350->1900 Hz)
                val durationMs = 80
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val splitSec = 0.036
                    val s = if (t < splitSec) {
                        val p = t / splitSec
                        val freq = 900.0 + p * 350.0
                        sin(2.0 * PI * freq * t) * sin(PI * p) * exp(-t * 35.0)
                    } else {
                        val t2 = t - splitSec
                        val p = t2 / (durationMs / 1000.0 - splitSec)
                        val freq = 1350.0 + p * 550.0
                        sin(2.0 * PI * freq * t2) * sin(PI * p.coerceIn(0.0, 1.0)) * exp(-t2 * 30.0)
                    }
                    (s * 24000).toInt().coerceIn(-32767, 32767).toShort()
                }
            }

            SoundEffect.PET -> {
                // 90ms soft warm bubble purr/chirp ripple (420 -> 680 Hz with gentle swell)
                val durationMs = 90
                val samples = (SAMPLE_RATE * durationMs / 1000)
                ShortArray(samples) { i ->
                    val t = i.toDouble() / SAMPLE_RATE
                    val progress = i.toDouble() / samples
                    val freq = 420.0 + progress * 260.0
                    val ripple = 0.72 + 0.28 * sin(2.0 * PI * 24.0 * t)
                    val env = sin(PI * progress) * exp(-t * 18.0)
                    val s = sin(2.0 * PI * freq * t) * ripple * env
                    (s * 22000).toInt().coerceIn(-32767, 32767).toShort()
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
