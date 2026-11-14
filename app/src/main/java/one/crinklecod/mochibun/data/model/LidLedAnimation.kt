package one.crinklecod.mochibun.data.model

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import java.util.Locale

/** Number of physical windows on the AT-M120 lid (W1..W5, left to right). */
const val LID_WINDOW_COUNT = 5
const val LID_MAX_STEPS = 16
const val LID_MIN_STEP_MS = 100
const val LID_MAX_STEP_MS = 5_000
const val LID_DEFAULT_STEP_MS = 500
const val LID_OFF_HEX = "#000000"

/** The 7 saturated colors offered by the step maker. Imported files may still use any hex. */
enum class LidLedSwatch(val label: String, val hex: String) {
    OFF("Off", "#000000"),
    HOT_PINK("Sakura Pink", "#FF1493"),
    VIOLET("Violet Purple", "#9D00FF"),
    SKY_BLUE("Electric Blue", "#00BFFF"),
    EMERALD_MINT("Emerald Mint", "#00FF7F"),
    TANGERINE("Tangerine Coral", "#FF5E00"),
    LEMON_GOLD("Lemon Gold", "#FFD000");

    /** Opaque ARGB value for Compose `Color(...)`. */
    val argb: Long get() = 0xFF000000L or hex.removePrefix("#").toLong(16)

    fun next(): LidLedSwatch = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromHex(hex: String): LidLedSwatch? {
            val normalized = normalizeLidHex(hex)
            return entries.firstOrNull { it.hex == normalized }
        }
    }
}

/**
 * Normalizes `#RRGGBB`, `RRGGBB`, `0xRRGGBB` or `#AARRGGBB` to upper-case `#RRGGBB`.
 * Anything unreadable becomes [LID_OFF_HEX] so a bad file can never crash playback.
 */
fun normalizeLidHex(raw: String): String {
    val clean = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    val rgb = when {
        clean.length == 6 -> clean
        clean.length == 8 -> clean.substring(2) // drop alpha
        else -> return LID_OFF_HEX
    }
    if (rgb.toLongOrNull(16) == null) return LID_OFF_HEX
    return "#" + rgb.uppercase(Locale.US)
}

fun isLidHexOff(hex: String): Boolean = normalizeLidHex(hex) == LID_OFF_HEX

/** Opaque ARGB value for any lid hex string. */
fun lidHexToArgb(hex: String): Long = 0xFF000000L or normalizeLidHex(hex).removePrefix("#").toLong(16)

/** One frame: exactly [LID_WINDOW_COUNT] hex colors, W1 first. */
@Serializable
data class LidLedStep(
    val windows: List<String> = List(LID_WINDOW_COUNT) { LID_OFF_HEX }
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LidLedAnimation(
    val id: String = "",
    val name: String = "Custom Wave ♡",
    val author: String = "Mochibun",
    @SerialName("step_ms")
    @JsonNames("stepMs")
    val stepMs: Int = LID_DEFAULT_STEP_MS,
    val loop: Boolean = true,
    val steps: List<LidLedStep> = emptyList()
) {
    val durationMs: Long get() = stepMs.toLong() * steps.size

    /** "5 steps • 2.5s" */
    fun summary(): String {
        val count = steps.size
        val seconds = String.format(Locale.US, "%.1f", durationMs / 1000.0)
        return "$count ${if (count == 1) "step" else "steps"} • ${seconds}s"
    }

    /** Clamps every field to what the editor and the lid can show. */
    fun sanitized(): LidLedAnimation = copy(
        name = name.trim().take(MAX_NAME_LENGTH).ifBlank { "Untitled ♡" },
        author = author.trim().take(MAX_NAME_LENGTH).ifBlank { "Mochibun" },
        stepMs = stepMs.coerceIn(LID_MIN_STEP_MS, LID_MAX_STEP_MS),
        steps = steps.take(LID_MAX_STEPS).map { step ->
            LidLedStep(
                List(LID_WINDOW_COUNT) { i -> normalizeLidHex(step.windows.getOrNull(i) ?: LID_OFF_HEX) }
            )
        }
    )

    companion object {
        const val MAX_NAME_LENGTH = 40

        fun blankStep(): LidLedStep = LidLedStep(List(LID_WINDOW_COUNT) { LID_OFF_HEX })
    }
}

/**
 * Lenient YAML / JSON reader and writer for lid animations.
 * Unknown keys are ignored; out-of-range values are clamped by [LidLedAnimation.sanitized].
 */
object LidLedAnimationCodec {

    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    /** JSON if the text is a JSON object, YAML otherwise. */
    fun decode(text: String): Result<LidLedAnimation> = runCatching {
        val trimmed = text.trimStart('﻿', ' ', '\t', '\r', '\n')
        val parsed = if (trimmed.startsWith("{")) {
            json.decodeFromString(LidLedAnimation.serializer(), trimmed)
        } else {
            yaml.decodeFromString(LidLedAnimation.serializer(), trimmed)
        }
        val clean = parsed.sanitized()
        require(clean.steps.isNotEmpty()) { "Animation has no steps" }
        clean
    }

    fun encodeJson(animation: LidLedAnimation): String =
        json.encodeToString(LidLedAnimation.serializer(), animation)

    /**
     * Hand-written so each step stays on one line, matching the documented format:
     * `- windows: ["#FF1493", "#000000", ...]`. Strings use YAML double-quoted escapes.
     */
    fun encodeYaml(animation: LidLedAnimation): String = buildString {
        if (animation.id.isNotBlank()) append("id: ").append(quote(animation.id)).append('\n')
        append("name: ").append(quote(animation.name)).append('\n')
        append("author: ").append(quote(animation.author)).append('\n')
        append("step_ms: ").append(animation.stepMs).append('\n')
        append("loop: ").append(animation.loop).append('\n')
        append("steps:\n")
        for (step in animation.steps) {
            append("  - windows: [")
            append(step.windows.joinToString(", ") { quote(it) })
            append("]\n")
        }
    }

    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when {
                c == '\\' -> append("\\\\")
                c == '"' -> append("\\\"")
                c == '\n' -> append("\\n")
                c == '\t' -> append("\\t")
                c < ' ' -> append(String.format(Locale.US, "\\x%02X", c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
