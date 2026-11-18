package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import one.crinklecod.mochibun.data.model.BundleMascotConfig
import one.crinklecod.mochibun.data.model.BundleMascotDialogue
import one.crinklecod.mochibun.data.model.MascotAtlasValidator
import one.crinklecod.mochibun.data.model.bundleJsonFormat
import one.crinklecod.mochibun.data.model.sanitizeMascotDialogue
import one.crinklecod.mochibun.ui.components.MascotAtlasLayout
import one.crinklecod.mochibun.ui.components.MascotSpriteSource
import one.crinklecod.mochibun.ui.components.MascotSpriteStore
import one.crinklecod.mochibun.ui.components.decodeImageBounds
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * The user's custom Mochibunny atlas: stored, applied, exported and reset like the custom
 * wallpaper / cursor, but always as a validated copy in one fixed file
 * (`filesDir/custom_mascot_atlas.png`), never a path or name taken from a picker or bundle.
 *
 * Its layout (and optional custom speech lines) lives in a small prefs file. Installing or
 * resetting hot-swaps [MascotSpriteStore]'s source; the store keeps only one decoded atlas.
 * File work blocks: call from a background thread (bundle import already runs on one).
 */
object CustomMascotManager {
    private const val TAG = "CustomMascotManager"
    const val ATLAS_FILE_NAME = "custom_mascot_atlas.png"
    private const val PREFS_NAME = "mochibunny_mascot"
    private const val KEY_LAYOUT_JSON = "layout_json"
    private const val KEY_VERSION = "version"

    /**
     * Image size as Android's decoder sees it (`inJustDecodeBounds`: no pixels are decoded).
     * Swappable because plain JVM tests have no real BitmapFactory.
     */
    internal var platformBounds: (File) -> Pair<Int, Int>? = ::decodeImageBounds

    /** Reactive custom dialogue for the active custom mascot, or null when using default lines. */
    val dialogue: StateFlow<BundleMascotDialogue?>
        get() = MascotSpriteStore.dialogue

    fun atlasFile(context: Context): File = File(context.filesDir, ATLAS_FILE_NAME)

    /** Whether a custom atlas is the active mascot art. */
    fun isActive(): Boolean = MascotSpriteStore.source.value is MascotSpriteSource.Custom

    /** Current custom dialogue when a custom mascot is active, or null otherwise. */
    fun activeDialogue(): BundleMascotDialogue? =
        if (isActive()) MascotSpriteStore.dialogue.value else null

    /** At startup: re-applies the saved custom atlas and its dialogue, if still present. */
    fun restore(context: Context) {
        val file = atlasFile(context)
        val (config, layout) = savedConfigAndLayout(context) ?: return
        if (!file.isFile) return
        val version = prefs(context).getLong(KEY_VERSION, 0L)
        val sanitizedDialogue = sanitizeMascotDialogue(config.dialogue)
        MascotSpriteStore.setSource(
            MascotSpriteSource.Custom(file, layout, version, sanitizedDialogue)
        )
    }

    /** The active atlas's layout and dialogue for a bundle manifest, or null when Mochibunny is built-in. */
    fun activeConfig(context: Context): BundleMascotConfig? {
        if (!atlasFile(context).isFile) return null
        val (savedConfig, layout) = savedConfigAndLayout(context) ?: return null
        val activeDialogue = activeDialogue() ?: sanitizeMascotDialogue(savedConfig.dialogue)
        return MascotAtlasValidator.toConfig(
            layout = layout,
            name = savedConfig.name.ifBlank { "Mochibunny" },
            dialogue = activeDialogue
        )
    }

    /**
     * Picker import: copies at most 1.5 MB of [input] to a staging file, then validates and
     * installs it with an inferred layout (8 rows of square frames) and no custom dialogue.
     */
    fun importPng(context: Context, input: InputStream): Result<MascotAtlasLayout> {
        val staged = File.createTempFile("mascot_pick_", ".png", context.cacheDir)
        return try {
            val withinLimit = FileOutputStream(staged).use { out ->
                MascotAtlasValidator.copyAtMost(input, out, MascotAtlasValidator.MAX_ATLAS_BYTES)
            }
            if (!withinLimit) {
                Result.failure(IllegalArgumentException("Atlas must be 1.5 MB or smaller"))
            } else {
                installStaged(context, staged, config = null)
            }
        } finally {
            staged.delete()
        }
    }

    /**
     * Validates a staged PNG and, if good, makes it the mascot. [config] is a bundle's declared
     * layout (and optional dialogue); without one the layout is inferred and dialogue is cleared.
     * On failure nothing changes.
     */
    fun installStaged(context: Context, staged: File, config: BundleMascotConfig?): Result<MascotAtlasLayout> {
        val pngSize = MascotAtlasValidator.readPngSize(staged)
            ?: return Result.failure(IllegalArgumentException("That file isn't a PNG image"))
        val decoderSize = platformBounds(staged)
        if (decoderSize != pngSize) {
            return Result.failure(IllegalArgumentException("Couldn't read that PNG"))
        }
        val (w, h) = pngSize
        val check = if (config != null) {
            MascotAtlasValidator.layoutFromConfig(config, w, h)
        } else {
            MascotAtlasValidator.inferLayout(w, h)
        }
        val layout = when (check) {
            is MascotAtlasValidator.Result.Valid -> check.layout
            is MascotAtlasValidator.Result.Invalid -> return Result.failure(IllegalArgumentException(check.reason))
        }
        val sanitizedDialogue = sanitizeMascotDialogue(config?.dialogue)
        val mascotName = config?.name?.trim()?.ifBlank { "Mochibunny" } ?: "Mochibunny"

        return try {
            val target = atlasFile(context)
            val tmp = File(context.filesDir, "$ATLAS_FILE_NAME.tmp")
            staged.copyTo(tmp, overwrite = true)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            val version = System.currentTimeMillis()
            val configToSave = MascotAtlasValidator.toConfig(
                layout = layout,
                name = mascotName,
                dialogue = sanitizedDialogue
            )
            prefs(context).edit()
                .putString(KEY_LAYOUT_JSON, bundleJsonFormat.encodeToString(configToSave))
                .putLong(KEY_VERSION, version)
                .apply()
            MascotSpriteStore.setSource(
                MascotSpriteSource.Custom(target, layout, version, sanitizedDialogue)
            )
            Result.success(layout)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to install custom mascot atlas", e)
            Result.failure(IllegalArgumentException("Couldn't save the atlas"))
        }
    }

    /** Back to the built-in Mochibunny: deletes the custom file, layout, and custom dialogue. */
    fun reset(context: Context) {
        MascotSpriteStore.setSource(MascotSpriteSource.BuiltIn)
        atlasFile(context).delete()
        prefs(context).edit().remove(KEY_LAYOUT_JSON).remove(KEY_VERSION).apply()
    }

    private fun savedConfigAndLayout(context: Context): Pair<BundleMascotConfig, MascotAtlasLayout>? {
        val json = prefs(context).getString(KEY_LAYOUT_JSON, null) ?: return null
        return try {
            val config = bundleJsonFormat.decodeFromString<BundleMascotConfig>(json)
            val (w, h) = MascotAtlasValidator.readPngSize(atlasFile(context)) ?: return null
            val layout = (MascotAtlasValidator.layoutFromConfig(config, w, h) as? MascotAtlasValidator.Result.Valid)?.layout
                ?: return null
            config to layout
        } catch (e: Exception) {
            Log.w(TAG, "Saved mascot layout unreadable", e)
            null
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
