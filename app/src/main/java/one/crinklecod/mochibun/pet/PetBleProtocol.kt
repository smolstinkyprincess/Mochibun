package one.crinklecod.mochibun.pet

import one.crinklecod.mochibun.data.model.BundleMascotDialogue
import one.crinklecod.mochibun.ui.components.MascotReactionKind
import one.crinklecod.mochibun.ui.components.petMoodLines
import one.crinklecod.mochibun.ui.components.pickLineAvoiding
import one.crinklecod.mochibun.ui.components.selectReactionLine
import java.util.UUID
import kotlin.math.roundToInt

/**
 * MicroMochi BLE protocol (v1): the wire format between Mochibun's [PetBleCompanionServer] and the
 * ESP32-S3 e-paper keychain. Pure Kotlin (no Android imports) so it is JVM-testable.
 * The firmware-facing copy of this spec lives in `docs/micromochi_ble_protocol.md`; keep both in sync.
 *
 * - **Service** [SERVICE_UUID] (primary). Advertised in the advertising packet; the scan response
 *   carries service data `"Mochibun"` under the same UUID. Android can't advertise a custom local
 *   name without renaming the whole phone, so firmware should filter on the service UUID.
 * - **State** [STATE_CHAR_UUID]: READ | NOTIFY (CCCD `0x2902`). Value = compact UTF-8 JSON, see [encodeState].
 * - **Command** [COMMAND_CHAR_UUID]: WRITE | WRITE_NO_RESPONSE. Value = one byte, see [PetBleCommand].
 * - All attributes require an encrypted (bonded) link: the keychain pairs once (Just Works).
 * - Firmware should request ATT MTU [TARGET_ATT_MTU]; notifications must fit in MTU - 3 bytes.
 */
object PetBleProtocol {
    const val VERSION = 1

    /** Random vendor 128-bit base (not the Bluetooth SIG base); characteristics vary the first group. */
    val SERVICE_UUID: UUID = UUID.fromString("1281dbd0-0fd6-4734-99b5-8f45f5403402")
    val STATE_CHAR_UUID: UUID = UUID.fromString("1281dbd1-0fd6-4734-99b5-8f45f5403402")
    val COMMAND_CHAR_UUID: UUID = UUID.fromString("1281dbd2-0fd6-4734-99b5-8f45f5403402")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Scan-response service data so scanners can show a friendly tag (8 bytes, fits in 31). */
    const val ADVERTISED_TAG = "Mochibun"

    const val TARGET_ATT_MTU = 247
    /** ATT notification header is 3 bytes. */
    const val ATT_HEADER_BYTES = 3
    const val DEFAULT_ATT_MTU = 23
    const val MAX_PAYLOAD_BYTES = TARGET_ATT_MTU - ATT_HEADER_BYTES

    fun payloadBudgetForMtu(mtu: Int): Int =
        (mtu - ATT_HEADER_BYTES).coerceIn(0, MAX_PAYLOAD_BYTES)

    /**
     * `{"v":1,"f":82,"j":75,"t":3,"m":"happy","s":0,"u":1732612345678,"r":"fed","l":"nom nom! ♡"}`
     *
     * `f`/`j` are fullness/joy in percent (0..100), `t` treats, `m` mood (lowercase [PetMood]),
     * `s` 1 while sleepy, `u` [PetState.lastUpdatedMs], `r` the command reaction (omitted when the
     * push wasn't caused by FEED/PET), `l` a dialogue line. The line is trimmed on a code-point
     * boundary (ending in `…`) so the whole payload fits in [maxBytes]; everything else always fits
     * in any budget >= ~100 bytes.
     */
    fun encodeState(
        snapshot: PetBleSnapshot,
        reaction: PetBleReaction?,
        line: String,
        maxBytes: Int = MAX_PAYLOAD_BYTES,
    ): ByteArray {
        val head = buildString {
            append("{\"v\":").append(VERSION)
            append(",\"f\":").append(snapshot.fullnessPct)
            append(",\"j\":").append(snapshot.joyPct)
            append(",\"t\":").append(snapshot.treats)
            append(",\"m\":\"").append(snapshot.mood.name.lowercase()).append('"')
            append(",\"s\":").append(if (snapshot.isSleeping) 1 else 0)
            append(",\"u\":").append(snapshot.lastUpdatedMs)
            if (reaction != null) append(",\"r\":\"").append(reaction.wire).append('"')
            append(",\"l\":\"")
        }
        val tail = "\"}"
        val fixedBytes = utf8Size(head) + utf8Size(tail)
        val lineBudget = (maxBytes - fixedBytes).coerceAtLeast(0)
        return (head + fitEscaped(line, lineBudget) + tail).toByteArray(Charsets.UTF_8)
    }

    /** JSON-escapes [text], keeping whole code points, so the result is at most [maxBytes] in UTF-8. */
    internal fun fitEscaped(text: String, maxBytes: Int): String {
        val full = escapeJson(text)
        if (utf8Size(full) <= maxBytes) return full
        val ellipsis = "…"
        val budget = maxBytes - utf8Size(ellipsis)
        if (budget < 0) return ""
        val out = StringBuilder()
        var used = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val piece = escapeJson(String(Character.toChars(cp)))
            val size = utf8Size(piece)
            if (used + size > budget) break
            out.append(piece)
            used += size
            i += Character.charCount(cp)
        }
        return out.append(ellipsis).toString()
    }

    internal fun escapeJson(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (c in text) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c < ' ' -> sb.append("\\u").append(Integer.toHexString(c.code).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun utf8Size(s: String): Int = s.toByteArray(Charsets.UTF_8).size
}

/** Single-byte commands written to the Command characteristic. */
enum class PetBleCommand(val code: Byte) {
    FEED(0x01),
    PET(0x02),
    REFRESH(0x03);

    companion object {
        /** Exactly one known byte, otherwise null (wrong length or unknown code). */
        fun fromBytes(value: ByteArray?): PetBleCommand? {
            if (value == null || value.size != 1) return null
            return entries.firstOrNull { it.code == value[0] }
        }
    }
}

/** The `"r"` field: what a command just did. */
enum class PetBleReaction(val wire: String) {
    FED("fed"),
    NO_TREATS("noTreats"),
    /** FEED refused because her tummy is already full (no treat spent). */
    FULL("full"),
    PET("pet"),
}

/** The pet calls a command needs; [PetManager] in the app, a fake in tests. */
interface PetBleActions {
    fun tryFeed(): FeedResult
    fun pet()
    fun refresh()

    object Live : PetBleActions {
        override fun tryFeed(): FeedResult = PetManager.tryFeed()
        override fun pet() = PetManager.pet()
        override fun refresh() = PetManager.refresh()
    }
}

/** Runs [command] and returns the reaction to push (null for REFRESH: plain mood line). */
fun dispatchPetBleCommand(command: PetBleCommand, actions: PetBleActions): PetBleReaction? =
    when (command) {
        PetBleCommand.FEED -> when (actions.tryFeed()) {
            FeedResult.FED -> PetBleReaction.FED
            FeedResult.NO_TREATS -> PetBleReaction.NO_TREATS
            FeedResult.ALREADY_FULL -> PetBleReaction.FULL
        }
        PetBleCommand.PET -> {
            actions.pet()
            PetBleReaction.PET
        }
        PetBleCommand.REFRESH -> {
            actions.refresh()
            null
        }
    }

/**
 * What the keychain shows. Data-class equality minus [lastUpdatedMs] ([visibleKey]) decides whether
 * a state change is worth a notification: plain decay ticks that move nothing visible are skipped.
 */
data class PetBleSnapshot(
    val fullnessPct: Int,
    val joyPct: Int,
    val treats: Int,
    val mood: PetMood,
    val lastUpdatedMs: Long,
) {
    val isSleeping: Boolean get() = mood == PetMood.SLEEPY

    fun visibleKey(): PetBleSnapshot = copy(lastUpdatedMs = 0L)

    companion object {
        fun of(state: PetState, mood: PetMood): PetBleSnapshot = PetBleSnapshot(
            fullnessPct = percent(state.fullness),
            joyPct = percent(state.joy),
            treats = state.treats,
            mood = mood,
            lastUpdatedMs = state.lastUpdatedMs,
        )

        private fun percent(v: Float): Int =
            if (v.isNaN()) 0 else (v * 100f).roundToInt().coerceIn(0, 100)
    }
}

/** Picks the `"l"` line from the same pools as Home's speech bubble (custom mascot pools first). */
object PetBleDialogue {
    fun line(
        reaction: PetBleReaction?,
        mood: PetMood,
        previousLine: String?,
        hasStepSensor: Boolean,
        customDialogue: BundleMascotDialogue?,
    ): String {
        val moodLine = { pickLineAvoiding(petMoodLines(mood, customDialogue), previousLine) }
        val kind = when (reaction) {
            null -> return moodLine()
            PetBleReaction.FED -> MascotReactionKind.FED
            PetBleReaction.NO_TREATS -> MascotReactionKind.NO_TREATS
            PetBleReaction.FULL -> MascotReactionKind.ALREADY_FULL
            PetBleReaction.PET -> MascotReactionKind.PETTED
        }
        return selectReactionLine(
            kind = kind,
            previousLine = previousLine,
            hasStepSensor = hasStepSensor,
            customDialogue = customDialogue,
            chatLine = moodLine,
        )
    }
}
