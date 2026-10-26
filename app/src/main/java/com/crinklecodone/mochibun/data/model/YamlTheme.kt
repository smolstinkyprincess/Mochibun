package com.crinklecodone.mochibun.data.model

import androidx.compose.ui.graphics.Color
import com.crinklecodone.mochibun.ui.theme.LauncherTheme
import com.crinklecodone.mochibun.ui.theme.MochibunThemePalette
import com.crinklecodone.mochibun.ui.theme.SakuraDreamPalette
import com.crinklecodone.mochibun.ui.theme.toLauncherTheme
import kotlinx.serialization.Serializable

/**
 * Serializable data representation of an external YAML theme file.
 * Stored in `/sdcard/Android/data/com.crinklecodone.mochibun/files/themes/`.
 */
@Serializable
data class YamlTheme(
    val id: String = "",
    val name: String = "Custom Theme",
    val author: String = "Community",
    val version: String = "1.0",
    val colors: YamlThemeColors = YamlThemeColors(),
    val decorations: YamlThemeDecorations = YamlThemeDecorations()
)

@Serializable
data class YamlThemeColors(
    val primary: String? = null,
    val container: String? = null,
    val cardBorder: String? = null,
    val accentDeep: String? = null,
    val background: String? = null,
    val outline: String? = null,
    val selectionGlow: String? = null,
    val textColor: String? = null,
    val squirclePrimary: String? = null,
    val squircleSecondary: String? = null,
    val cloudBack: String? = null,
    val cloudMid: String? = null,
    val cloudFront: String? = null,
    val catOutline: String? = null,
    val catBlush: String? = null,
    val mascotTint: String? = null
)

@Serializable
data class YamlThemeDecorations(
    val greetingText: String = "— ♡ Welcome Home ♡ —",
    val glowExpansionDp: Float = 4f,
    val cardRoundnessDp: Float = 24f
)

/**
 * Parses an optional hex string (#RRGGBB, #AARRGGBB, #RGB, #ARGB, or without leading #) into a Compose [Color] or null.
 * Never throws exceptions, returning null if empty, invalid, or explicitly "none"/"null".
 */
fun safeParseColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank() || hex.equals("none", ignoreCase = true) || hex.equals("null", ignoreCase = true)) return null
    val trimmed = hex.trim().removePrefix("#")
    return try {
        when (trimmed.length) {
            6 -> {
                val value = trimmed.toLong(16)
                Color((0xFF000000 or value).toInt())
            }
            8 -> {
                val value = trimmed.toLong(16)
                Color(value.toInt())
            }
            3 -> {
                val r = trimmed[0].digitToInt(16) * 0x11
                val g = trimmed[1].digitToInt(16) * 0x11
                val b = trimmed[2].digitToInt(16) * 0x11
                Color((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
            4 -> {
                val a = trimmed[0].digitToInt(16) * 0x11
                val r = trimmed[1].digitToInt(16) * 0x11
                val g = trimmed[2].digitToInt(16) * 0x11
                val b = trimmed[3].digitToInt(16) * 0x11
                Color((a shl 24) or (r shl 16) or (g shl 8) or b)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Parses a hex string (#RRGGBB, #AARRGGBB, or without leading #) into a Compose [Color].
 * Never throws exceptions, falling back to [fallback] if parsing fails.
 */
fun safeParseColor(hex: String?, fallback: Color): Color {
    return safeParseColorOrNull(hex) ?: fallback
}

/**
 * Formats an ARGB color integer as an uppercase `#AARRGGBB` hex string.
 */
fun colorIntToHex(colorInt: Int): String = String.format("#%08X", colorInt)

/**
 * Parses a hex color string into an ARGB color integer, falling back to [defaultColor].
 */
fun safeParseColorInt(hex: String?, defaultColor: Int): Int {
    val parsed = safeParseColorOrNull(hex) ?: return defaultColor
    val a = (parsed.alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
    val r = (parsed.red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (parsed.green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (parsed.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Converts this [YamlTheme] into a full [MochibunThemePalette].
 * Missing or invalid color tokens gracefully fall back to [fallback] (default Sakura Dream).
 */
fun YamlTheme.toLauncherPalette(fallback: MochibunThemePalette = SakuraDreamPalette): MochibunThemePalette {
    val parsedPrimary = safeParseColor(colors.primary, fallback.primary)
    val parsedContainer = safeParseColor(colors.container, fallback.container)
    val parsedAccentDeep = safeParseColor(colors.accentDeep, fallback.deepAccent)
    val parsedBackground = safeParseColor(colors.background, fallback.background)
    val parsedOutline = safeParseColor(colors.outline, fallback.outline)
    val parsedTextColor = safeParseColor(colors.textColor, fallback.textColor)
    val parsedCardBorder = safeParseColor(colors.cardBorder, fallback.focusedCardBorder)
    val parsedGlow = safeParseColor(colors.selectionGlow, parsedPrimary)

    val parsedSquirclePrimary = safeParseColor(colors.squirclePrimary, parsedPrimary)
    val parsedSquircleSecondary = safeParseColor(colors.squircleSecondary, fallback.squircleSecondary)

    val parsedCloudBack = safeParseColor(colors.cloudBack, fallback.cloudBack)
    val parsedCloudMid = safeParseColor(colors.cloudMid, fallback.cloudMid)
    val parsedCloudFront = safeParseColor(colors.cloudFront, fallback.cloudFront)
    val parsedCatOutline = safeParseColor(colors.catOutline, parsedAccentDeep)
    val parsedCatBlush = safeParseColor(colors.catBlush, fallback.catBlush)
    val parsedMascotTint = safeParseColorOrNull(colors.mascotTint)

    val effectiveId = id.ifBlank { "custom_${System.currentTimeMillis()}" }
    val effectiveName = name.ifBlank { "Custom Theme" }

    return MochibunThemePalette(
        id = effectiveId,
        name = effectiveName,
        primary = parsedPrimary,
        container = parsedContainer,
        deepAccent = parsedAccentDeep,
        background = parsedBackground,
        outline = parsedOutline,
        textColor = parsedTextColor,
        squirclePrimary = parsedSquirclePrimary,
        squircleSecondary = parsedSquircleSecondary,
        cloudBack = parsedCloudBack,
        cloudMid = parsedCloudMid,
        cloudFront = parsedCloudFront,
        catOutline = parsedCatOutline,
        catBlush = parsedCatBlush,
        mascotTint = parsedMascotTint,
        selectionGlow = parsedGlow,
        focusedCardContainer = parsedContainer,
        focusedCardContainerEnd = parsedContainer,
        focusedCardBorder = parsedCardBorder,
        unfocusedCardBg = fallback.unfocusedCardBg,
        unfocusedCardBorder = parsedOutline.copy(alpha = 0.35f)
    )
}

/**
 * Converts this [YamlTheme] directly into a [LauncherTheme].
 */
fun YamlTheme.toLauncherTheme(fallback: MochibunThemePalette = SakuraDreamPalette): LauncherTheme {
    return toLauncherPalette(fallback).toLauncherTheme(decorations.cardRoundnessDp)
}
