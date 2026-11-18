package one.crinklecod.mochibun.data.model

import one.crinklecod.mochibun.ui.components.MascotAnimSlot
import one.crinklecod.mochibun.ui.components.MascotAtlasLayout
import one.crinklecod.mochibun.ui.components.MascotSlotLayout
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Rules for custom mascot atlases (Settings picker and `.mochibun` bundles). Pure Kotlin + java.io,
 * so every rule is JVM-tested. Sized for the MT6761: at most 2048 x 2048 px and 1.5 MB on disk.
 */
object MascotAtlasValidator {
    const val ROWS = 8
    const val MIN_FRAME_PX = 16
    const val MAX_FRAME_PX = 256
    const val MAX_FRAMES = 16
    const val MAX_ATLAS_PX = 2048
    const val MAX_ATLAS_BYTES = 1_536L * 1024L // 1.5 MB
    const val MAX_DIALOGUE_LINE_CHARS = 48
    const val MAX_DIALOGUE_POOL_SIZE = 20

    sealed interface Result {
        data class Valid(val layout: MascotAtlasLayout) : Result
        /** [reason] is short and friendly: it is shown in a toast / status line. */
        data class Invalid(val reason: String) : Result
    }

    /**
     * A plain PNG with no layout: 8 equal rows of square frames, every slot on its default row.
     * `frameHeight = height / 8`, `frameWidth = frameHeight`, `frames = width / frameWidth`.
     */
    fun inferLayout(width: Int, height: Int): Result {
        if (width <= 0 || height <= 0) return Result.Invalid("Couldn't read the image size")
        if (height % ROWS != 0) return Result.Invalid("Height must split into 8 equal rows")
        val frame = height / ROWS
        if (frame !in MIN_FRAME_PX..MAX_FRAME_PX) {
            return Result.Invalid("Frames must be $MIN_FRAME_PX–$MAX_FRAME_PX px tall")
        }
        if (width % frame != 0) return Result.Invalid("Width must be a whole number of square frames")
        val frames = width / frame
        if (frames !in 1..MAX_FRAMES) return Result.Invalid("Each row needs 1–$MAX_FRAMES frames")
        return validate(MascotAtlasLayout.uniform(frame, frame, frames), width, height)
    }

    /** A bundled layout: declared frame size and slots, checked against the real image size. */
    fun layoutFromConfig(config: BundleMascotConfig, width: Int, height: Int): Result {
        val fw = config.frameWidth
        val fh = config.frameHeight
        if (fw !in MIN_FRAME_PX..MAX_FRAME_PX || fh !in MIN_FRAME_PX..MAX_FRAME_PX) {
            return Result.Invalid("Frames must be $MIN_FRAME_PX–$MAX_FRAME_PX px")
        }
        val declared = config.slots.mapNotNull { (key, slot) ->
            val animSlot = MascotAnimSlot.entries.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) }
            animSlot?.let { it to MascotSlotLayout(slot.row, slot.frames) } // unknown keys are ignored
        }.toMap()
        val layout = if (declared.isEmpty()) {
            MascotAtlasLayout.uniform(fw, fh, (width / fw).coerceAtMost(MAX_FRAMES))
        } else {
            MascotAtlasLayout(fw, fh, declared)
        }
        return validate(layout, width, height)
    }

    /** Size limits, then every slot's row and frames must fit inside the image. */
    fun validate(layout: MascotAtlasLayout, width: Int, height: Int): Result {
        val fw = layout.frameWidth
        val fh = layout.frameHeight
        if (fw !in MIN_FRAME_PX..MAX_FRAME_PX || fh !in MIN_FRAME_PX..MAX_FRAME_PX) {
            return Result.Invalid("Frames must be $MIN_FRAME_PX–$MAX_FRAME_PX px")
        }
        if (width > MAX_ATLAS_PX || height > MAX_ATLAS_PX) {
            return Result.Invalid("Atlas must be at most $MAX_ATLAS_PX × $MAX_ATLAS_PX px")
        }
        if (width > fw * MAX_FRAMES || height > fh * ROWS) {
            return Result.Invalid("Atlas is bigger than 8 rows × $MAX_FRAMES frames")
        }
        if (layout.slots.isEmpty()) return Result.Invalid("No animation rows")
        for ((slot, s) in layout.slots) {
            val name = slot.name.lowercase()
            if (s.row !in 0 until ROWS) return Result.Invalid("Row for $name must be 0–7")
            if (s.frames !in 1..MAX_FRAMES) return Result.Invalid("$name needs 1–$MAX_FRAMES frames")
            if ((s.row + 1) * fh > height) return Result.Invalid("Row for $name is below the image")
            if (s.frames * fw > width) return Result.Invalid("$name frames run past the image edge")
        }
        return Result.Valid(layout)
    }

    /** The bundle form of [layout], for exporting and for remembering the active custom atlas. */
    fun toConfig(
        layout: MascotAtlasLayout,
        name: String = "Mochibunny",
        dialogue: BundleMascotDialogue? = null
    ): BundleMascotConfig =
        BundleMascotConfig(
            name = name,
            frameWidth = layout.frameWidth,
            frameHeight = layout.frameHeight,
            slots = layout.slots.entries.associate { (slot, s) ->
                slot.name.lowercase() to BundleMascotSlot(s.row, s.frames)
            },
            dialogue = sanitizeMascotDialogue(dialogue)
        )

    /**
     * Cleans bundled custom dialogue lines:
     * - Strips control characters and collapses newlines/tabs into single spaces, then trims.
     * - Drops blank lines and truncates lines longer than [MAX_DIALOGUE_LINE_CHARS] (48 chars).
     * - Caps each mood/action pool at [MAX_DIALOGUE_POOL_SIZE] (20 lines).
     * - Returns `null` when all 7 pools are empty.
     */
    fun sanitizeMascotDialogue(raw: BundleMascotDialogue?): BundleMascotDialogue? {
        if (raw == null) return null
        val sanitized = BundleMascotDialogue(
            happy = sanitizePool(raw.happy),
            content = sanitizePool(raw.content),
            hungry = sanitizePool(raw.hungry),
            sulky = sanitizePool(raw.sulky),
            sleepy = sanitizePool(raw.sleepy),
            fed = sanitizePool(raw.fed),
            noTreats = sanitizePool(raw.noTreats)
        )
        return sanitized.takeIf { it.hasAnyLines() }
    }

    private fun sanitizePool(lines: List<String>): List<String> =
        lines.asSequence()
            .mapNotNull(::sanitizeDialogueLine)
            .take(MAX_DIALOGUE_POOL_SIZE)
            .toList()

    private fun sanitizeDialogueLine(line: String): String? {
        if (line.isBlank()) return null
        val sb = StringBuilder(line.length)
        var lastWasSpace = false
        for (ch in line) {
            when {
                ch == '\r' || ch == '\n' || ch == '\t' || ch == ' ' -> {
                    if (sb.isNotEmpty() && !lastWasSpace) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                }
                ch.isISOControl() -> Unit
                else -> {
                    sb.append(ch)
                    lastWasSpace = false
                }
            }
        }
        val cleaned = sb.toString().trim()
        if (cleaned.isEmpty()) return null
        val clamped = if (cleaned.length > MAX_DIALOGUE_LINE_CHARS) {
            cleaned.take(MAX_DIALOGUE_LINE_CHARS).trimEnd()
        } else {
            cleaned
        }
        return clamped.ifEmpty { null }
    }

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    /**
     * Width and height from a PNG's IHDR header, or null when it isn't a PNG. Reads 24 bytes;
     * the real decoder still confirms the image afterwards.
     */
    fun readPngSize(header: ByteArray): Pair<Int, Int>? {
        if (header.size < 24) return null
        for (i in PNG_SIGNATURE.indices) if (header[i] != PNG_SIGNATURE[i]) return null
        if (header[12] != 'I'.code.toByte() || header[13] != 'H'.code.toByte() ||
            header[14] != 'D'.code.toByte() || header[15] != 'R'.code.toByte()
        ) return null
        fun int(at: Int) = ((header[at].toInt() and 0xFF) shl 24) or
            ((header[at + 1].toInt() and 0xFF) shl 16) or
            ((header[at + 2].toInt() and 0xFF) shl 8) or
            (header[at + 3].toInt() and 0xFF)
        val w = int(16)
        val h = int(20)
        return if (w > 0 && h > 0) w to h else null
    }

    fun readPngSize(file: File): Pair<Int, Int>? = try {
        file.inputStream().use { input ->
            val header = ByteArray(24)
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) break
                read += n
            }
            if (read < header.size) null else readPngSize(header)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Copies at most [maxBytes] from [input] to [output], counting real bytes (it never trusts a
     * declared size such as `ZipEntry.size`). Returns false, having stopped early, when [input]
     * has more than [maxBytes].
     */
    fun copyAtMost(input: InputStream, output: OutputStream, maxBytes: Long): Boolean {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return true
            if (total + n > maxBytes) return false
            output.write(buffer, 0, n)
            total += n
        }
    }
}

fun sanitizeMascotDialogue(raw: BundleMascotDialogue?): BundleMascotDialogue? =
    MascotAtlasValidator.sanitizeMascotDialogue(raw)
