package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import one.crinklecod.mochibun.data.model.LID_OFF_HEX
import one.crinklecod.mochibun.data.model.LidLedAnimation
import one.crinklecod.mochibun.data.model.LidLedAnimationCodec
import one.crinklecod.mochibun.data.model.LidLedStep
import one.crinklecod.mochibun.data.model.LidLedSwatch
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import java.io.File
import java.util.Locale

/** Where a lid animation plays: on unread notifications or while charging (lid closed). */
enum class LidAnimationSlot { NOTIFICATION, CHARGING }

/**
 * Library of lid LED step animations: built-in presets plus custom / imported `.yaml` files in
 * `filesDir/lid_animations/`. Each [LidAnimationSlot] has its own selected id in
 * [PreferencesManager]; a blank id keeps the classic behavior in `LidLedManager`
 * (notification shimmer / solid charging color).
 *
 * All file functions do disk IO; call them off the main thread.
 */
object LidLedAnimationRepository {

    private const val TAG = "LidLedAnimRepo"
    private const val DIR_NAME = "lid_animations"
    private const val BUILTIN_PREFIX = "builtin_"
    private const val MAX_IMPORT_BYTES = 64 * 1024

    /** Opened from a file manager (VIEW intent). `.yaml` / `.json` import only via the Studio button. */
    private const val OPENABLE_EXTENSION = ".mochibunled"

    // Cached so the LED ladder never parses YAML on a notification
    private val cachedActive = java.util.concurrent.ConcurrentHashMap<LidAnimationSlot, LidLedAnimation>()

    val BUILT_INS: List<LidLedAnimation> by lazy { buildBuiltIns() }

    fun isBuiltIn(id: String): Boolean = id.startsWith(BUILTIN_PREFIX)

    fun isAnimationFileName(name: String?): Boolean {
        val lower = name?.lowercase(Locale.US) ?: return false
        return lower.endsWith(OPENABLE_EXTENSION)
    }

    /** Built-ins first, then custom / imported sorted by name. */
    fun loadAll(context: Context): List<LidLedAnimation> = BUILT_INS + loadCustom(context)

    fun loadCustom(context: Context): List<LidLedAnimation> {
        val files = dir(context).listFiles { f -> f.isFile && f.name.endsWith(".yaml") } ?: return emptyList()
        return files.mapNotNull { file ->
            LidLedAnimationCodec.decode(file.readText())
                .map { it.copy(id = file.nameWithoutExtension) } // the file name is the id
                .onFailure { Log.w(TAG, "Skipping unreadable ${file.name}", it) }
                .getOrNull()
        }.sortedBy { it.name.lowercase(Locale.US) }
    }

    fun findById(context: Context, id: String): LidLedAnimation? {
        if (id.isBlank()) return null
        BUILT_INS.firstOrNull { it.id == id }?.let { return it }
        val file = fileFor(context, id) ?: return null
        if (!file.isFile) return null
        return LidLedAnimationCodec.decode(file.readText()).getOrNull()?.copy(id = id)
    }

    /**
     * Saves to the library. Built-ins and new drafts get a fresh custom id (editing a built-in
     * clones it). Returns the saved animation with its final id.
     */
    fun save(context: Context, animation: LidLedAnimation): LidLedAnimation {
        val clean = animation.sanitized()
        val id = if (clean.id.isBlank() || isBuiltIn(clean.id) || fileFor(context, clean.id) == null) {
            newCustomId(clean.name)
        } else {
            clean.id
        }
        val saved = clean.copy(id = id)
        val file = File(dir(context), "$id.yaml")
        val tmp = File(dir(context), "$id.yaml.tmp")
        tmp.writeText(LidLedAnimationCodec.encodeYaml(saved))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        for ((slot, cached) in cachedActive) if (cached.id == id) cachedActive[slot] = saved
        return saved
    }

    /** Deletes a custom / imported animation. Built-ins can't be deleted. */
    fun delete(context: Context, id: String): Boolean {
        if (isBuiltIn(id)) return false
        val deleted = fileFor(context, id)?.delete() == true
        for (slot in LidAnimationSlot.entries) {
            if (getActiveAnimationId(context, slot) == id) setActiveAnimationId(context, slot, "")
        }
        return deleted
    }

    fun importFromUri(context: Context, uri: Uri): Result<LidLedAnimation> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
            require(buffer.size <= MAX_IMPORT_BYTES) { "File is too large for a lid animation" }
            buffer
        } ?: error("Could not open file")
        val parsed = LidLedAnimationCodec.decode(bytes.toString(Charsets.UTF_8)).getOrThrow()
        save(context, parsed.copy(id = "")) // imports always become new custom entries
    }

    fun exportToUri(context: Context, uri: Uri, animation: LidLedAnimation): Result<Unit> = runCatching {
        val text = LidLedAnimationCodec.encodeYaml(animation.sanitized().copy(id = ""))
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not open file for writing")
    }

    /** YAML content, `.mochibunled` name so the file opens straight into Mochibun when shared. */
    fun exportFileName(animation: LidLedAnimation): String = "${slug(animation.name)}$OPENABLE_EXTENSION"

    /** Display name of a content / file uri, used to route incoming VIEW intents. */
    fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment
    }

    fun getActiveAnimationId(context: Context, slot: LidAnimationSlot): String = when (slot) {
        LidAnimationSlot.NOTIFICATION -> PreferencesManager.getLidLedNotificationAnimationId(context)
        LidAnimationSlot.CHARGING -> PreferencesManager.getLidLedChargingAnimationId(context)
    }

    fun setActiveAnimationId(context: Context, slot: LidAnimationSlot, id: String) {
        when (slot) {
            LidAnimationSlot.NOTIFICATION -> PreferencesManager.setLidLedNotificationAnimationId(context, id)
            LidAnimationSlot.CHARGING -> PreferencesManager.setLidLedChargingAnimationId(context, id)
        }
        val anim = if (id.isBlank()) null else findById(context, id)
        if (anim == null) cachedActive.remove(slot) else cachedActive[slot] = anim
    }

    /** The slot's selected animation, or null for classic behavior. Cached after the first load. */
    fun getActiveAnimation(context: Context, slot: LidAnimationSlot): LidLedAnimation? {
        val id = getActiveAnimationId(context, slot)
        if (id.isBlank()) return null
        cachedActive[slot]?.takeIf { it.id == id }?.let { return it }
        return findById(context, id)?.also { cachedActive[slot] = it }
    }

    private fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** Null for ids that aren't plain custom file names (blocks path tricks like `../`). */
    private fun fileFor(context: Context, id: String): File? {
        if (isBuiltIn(id) || !id.matches(Regex("[a-z0-9_-]{1,80}"))) return null
        return File(dir(context), "$id.yaml")
    }

    private fun newCustomId(name: String): String =
        "custom_${slug(name).take(32)}_${System.currentTimeMillis().toString(36)}"

    private fun slug(name: String): String =
        name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "lid_animation" }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun buildBuiltIns(): List<LidLedAnimation> {
        val off = LID_OFF_HEX
        fun lit(color: LidLedSwatch, vararg on: Int) =
            LidLedStep(List(5) { i -> if (i + 1 in on) color.hex else off })
        val pink = LidLedSwatch.HOT_PINK
        val violet = LidLedSwatch.VIOLET
        val mint = LidLedSwatch.EMERALD_MINT
        val lemon = LidLedSwatch.LEMON_GOLD
        val all = intArrayOf(1, 2, 3, 4, 5)

        return listOf(
            LidLedAnimation(
                id = "${BUILTIN_PREFIX}sakura_chase",
                name = "Sakura Chase ♡",
                stepMs = 500,
                steps = (1..5).map { lit(pink, it) }
            ),
            // One color per step: the lid helper lights each frame in a single color
            LidLedAnimation(
                id = "${BUILTIN_PREFIX}keitai_rainbow",
                name = "Keitai Rainbow",
                stepMs = 500,
                steps = listOf(
                    LidLedSwatch.HOT_PINK, LidLedSwatch.TANGERINE, LidLedSwatch.LEMON_GOLD,
                    LidLedSwatch.EMERALD_MINT, LidLedSwatch.SKY_BLUE, LidLedSwatch.VIOLET
                ).map { lit(it, *all) }
            ),
            LidLedAnimation(
                id = "${BUILTIN_PREFIX}mint_heartbeat",
                name = "Mint Heartbeat",
                stepMs = 250,
                steps = listOf(
                    lit(mint, 2, 3, 4), LidLedAnimation.blankStep(),
                    lit(mint, *all), lit(mint, 3), lit(mint, 3), lit(mint, 3)
                )
            ),
            LidLedAnimation(
                id = "${BUILTIN_PREFIX}berry_sparkle",
                name = "Berry Sparkle",
                stepMs = 400,
                steps = listOf(lit(pink, 1, 3, 5), lit(violet, 2, 4), lit(pink, 2, 4), lit(violet, 1, 3, 5))
            ),
            LidLedAnimation(
                id = "${BUILTIN_PREFIX}lemon_ping_pong",
                name = "Lemon Ping-Pong",
                stepMs = 300,
                steps = listOf(1, 2, 3, 4, 5, 4, 3, 2).map { lit(lemon, it) }
            )
        )
    }
}
