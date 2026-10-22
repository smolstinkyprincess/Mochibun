package com.crinklecodone.mochibun.ui.theme

import androidx.compose.ui.graphics.Color
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb

import com.crinklecodone.mochibun.data.repository.ExternalThemeManager
import com.crinklecodone.mochibun.ui.theme.*

/**
 * Unified color scheme model representing the 4 cohesive custom pastel palettes:
 * 1. Sakura Dream (Default)
 * 2. Lavender Mist
 * 3. Minty Matcha
 * 4. Milky Peach
 */
@Immutable
data class MochibunThemePalette(
    val id: String,
    val name: String,
    val primary: Color,
    val container: Color,
    val deepAccent: Color,
    val background: Color,
    val outline: Color,
    val textColor: Color,
    val squirclePrimary: Color,
    val squircleSecondary: Color,
    val cloudBack: Color,
    val cloudMid: Color,
    val cloudFront: Color,
    val catOutline: Color,
    val catBlush: Color = Color(0xFFFFADC9),
    val mascotTint: Color? = null,
    val selectionGlow: Color = primary,
    val focusedCardContainer: Color = Color(0xFFF3E9F8),
    val focusedCardContainerEnd: Color = Color(0xFFFCEFF2),
    val focusedCardBorder: Color = Color(0xFFB6A2D8),
    val unfocusedCardBg: Color = Color(0xFFFFFDF8),
    val unfocusedCardBorder: Color = outline.copy(alpha = 0.35f)
)

val SakuraDreamPalette = MochibunThemePalette(
    id = "sakura_dream",
    name = "Sakura Dream",
    primary = Color(0xFFF8BBD0),
    container = Color(0xFFFCE4EC),
    deepAccent = Color(0xFFA04064),
    background = Color(0xFFFFF5F8),
    outline = Color(0xFFF3CADC),
    textColor = Color(0xFF703350),
    squirclePrimary = Color(0xFFF8BBD0),
    squircleSecondary = Color(0xFFE1BEE7),
    cloudBack = Color(0xFFFCEBF2),
    cloudMid = Color(0xFFF8DCE7),
    cloudFront = Color(0xFFFFF2F7),
    catOutline = Color(0xFFA04064),
    catBlush = Color(0xFFFFADC9),
    mascotTint = Color(0xFFFFEBF2),
    selectionGlow = Color(0xFFF8BBD0),
    focusedCardContainer = Color(0xFFFCE4EC),
    focusedCardContainerEnd = Color(0xFFFFF0F5),
    focusedCardBorder = Color(0xFFF48FB1),
    unfocusedCardBg = Color(0xFFFFFDF8),
    unfocusedCardBorder = Color(0xFFF3CADC).copy(alpha = 0.5f)
)

val LavenderMistPalette = MochibunThemePalette(
    id = "lavender_mist",
    name = "Lavender Mist",
    primary = Color(0xFFC8A8E9),
    container = Color(0xFFEDE7F6),
    deepAccent = Color(0xFF5E35B1),
    background = Color(0xFFF8F6FD),
    outline = Color(0xFFD1C4E9),
    textColor = Color(0xFF3E2248),
    squirclePrimary = Color(0xFFC7AFE2),
    squircleSecondary = Color(0xFFB39DDB),
    cloudBack = Color(0xFFEFE3FB),
    cloudMid = Color(0xFFE2CEF5),
    cloudFront = Color(0xFFF7ECFD),
    catOutline = Color(0xFF5E35B1),
    catBlush = Color(0xFFFFADC9),
    mascotTint = Color(0xFFF3EDFF),
    selectionGlow = Color(0xFFC8A8E9),
    focusedCardContainer = Color(0xFFF3E9F8),
    focusedCardContainerEnd = Color(0xFFFCEFF2),
    focusedCardBorder = Color(0xFFB6A2D8),
    unfocusedCardBg = Color(0xFFFFFDF8),
    unfocusedCardBorder = Color(0xFFE8DEF2)
)

val MintyMatchaPalette = MochibunThemePalette(
    id = "minty_matcha",
    name = "Minty Matcha",
    primary = Color(0xFFC8E6C9),
    container = Color(0xFFE8F5E9),
    deepAccent = Color(0xFF2E7D32),
    background = Color(0xFFF4FBF4),
    outline = Color(0xFFA5D6A7),
    textColor = Color(0xFF1B5E20),
    squirclePrimary = Color(0xFFA5D6A7),
    squircleSecondary = Color(0xFFC8E6C9),
    cloudBack = Color(0xFFE8F5E9),
    cloudMid = Color(0xFFD7ECD7),
    cloudFront = Color(0xFFF2FAF2),
    catOutline = Color(0xFF2E7D32),
    catBlush = Color(0xFFFFB6C1),
    mascotTint = Color(0xFFEDF8F1),
    selectionGlow = Color(0xFFA5D6A7),
    focusedCardContainer = Color(0xFFE8F5E9),
    focusedCardContainerEnd = Color(0xFFF1F8F1),
    focusedCardBorder = Color(0xFF81C784),
    unfocusedCardBg = Color(0xFFFFFDF8),
    unfocusedCardBorder = Color(0xFFC8E6C9).copy(alpha = 0.5f)
)

val MilkyPeachPalette = MochibunThemePalette(
    id = "milky_peach",
    name = "Milky Peach",
    primary = Color(0xFFFFE0B2),
    container = Color(0xFFFFF3E0),
    deepAccent = Color(0xFF8D4E28),
    background = Color(0xFFFFFBF5),
    outline = Color(0xFFFFCC80),
    textColor = Color(0xFF6D2C12),
    squirclePrimary = Color(0xFFFFCC80),
    squircleSecondary = Color(0xFFFFE0B2),
    cloudBack = Color(0xFFFFF0E0),
    cloudMid = Color(0xFFFFE5CC),
    cloudFront = Color(0xFFFFF7EF),
    catOutline = Color(0xFF8D4E28),
    catBlush = Color(0xFFFFAB91),
    mascotTint = Color(0xFFFFF0E6),
    selectionGlow = Color(0xFFFFCC80),
    focusedCardContainer = Color(0xFFFFF3E0),
    focusedCardContainerEnd = Color(0xFFFFF8EE),
    focusedCardBorder = Color(0xFFFFB74D),
    unfocusedCardBg = Color(0xFFFFFDF8),
    unfocusedCardBorder = Color(0xFFFFE0B2).copy(alpha = 0.5f)
)

fun MochibunThemePalette.toLauncherTheme(roundnessDp: Float = 24f): LauncherTheme = LauncherTheme(
    name = id,
    bgColor = background,
    fgTextColor = textColor,
    cardBgColor = container,
    cardTextColor = textColor.copy(alpha = 0.85f),
    greetingColor = deepAccent,
    greetingOutlineColor = outline,
    greetingBgColor = background,
    appActionColor = deepAccent,
    cardRoundnessDp = roundnessDp,
    mascotTint = mascotTint
)

@Immutable
data class LauncherTheme(
    val name: String = "sakura_dream",
    val bgColor: Color = Color(0xFFFFF5F8),
    val fgTextColor: Color = Color(0xFF703350),
    val cardBgColor: Color = Color(0xFFFCE4EC),
    val cardTextColor: Color = Color(0xFF703350),
    val greetingColor: Color = Color(0xFFA04064),
    val greetingOutlineColor: Color = Color(0xFFF3CADC),
    val greetingBgColor: Color = Color(0xFFFFF5F8),
    val appActionColor: Color = Color(0xFFA04064),
    val cardRoundnessDp: Float = 24f,
    val mascotTint: Color? = null
)

val SakuraDreamTheme = SakuraDreamPalette.toLauncherTheme(24f)
val LavenderMistTheme = LavenderMistPalette.toLauncherTheme(24f)
val MintyMatchaTheme = MintyMatchaPalette.toLauncherTheme(24f)
val MilkyPeachTheme = MilkyPeachPalette.toLauncherTheme(24f)

// Backward-compatible presets & aliases
val OriginalTheme = SakuraDreamTheme
val StrawberryMilkTheme = SakuraDreamTheme
val LavenderDreamTheme = LavenderMistTheme
val MidnightMochiTheme = LauncherTheme(
    name = "midnight_mochi",
    bgColor = Color(0xFF1E1128),
    fgTextColor = Color(0xFFF3E8FF),
    cardBgColor = Color(0xFF3B224C),
    cardTextColor = Color(0xFFD8B4FE),
    greetingColor = Color(0xFFE879F9),
    greetingOutlineColor = Color(0xFF6B21A8),
    greetingBgColor = Color(0xFF2A1638),
    appActionColor = Color(0xFFE879F9),
    cardRoundnessDp = 24f
)

val LauncherTheme.palette: MochibunThemePalette
    get() = when (name) {
        "lavender_mist", "lavender_dream" -> LavenderMistPalette
        "minty_matcha" -> MintyMatchaPalette
        "milky_peach" -> MilkyPeachPalette
        else -> ExternalThemeManager.findPalette(name) ?: SakuraDreamPalette
    }

val LauncherTheme.selectionGlowColor: Color
    get() = palette.selectionGlow

val LauncherTheme.focusedContainerColor: Color
    get() = palette.focusedCardContainer

val LauncherTheme.focusedContainerEndColor: Color
    get() = palette.focusedCardContainerEnd

val LauncherTheme.cardBorderColor: Color
    get() = palette.focusedCardBorder

val LauncherTheme.unfocusedCardBgColor: Color
    get() = if (name == "midnight_mochi") Color(0xFF2E1A3C) else palette.unfocusedCardBg

val LauncherTheme.unfocusedCardBorderColor: Color
    get() = if (name == "midnight_mochi") Color(0xFF4C2A60) else palette.unfocusedCardBorder

val LauncherTheme.selectedContainerColor: Color
    get() = focusedContainerColor

val LauncherTheme.background: Color get() = palette.background
val LauncherTheme.surface: Color get() = palette.container
val LauncherTheme.container: Color get() = palette.container
val LauncherTheme.cardBorder: Color get() = palette.outline
val LauncherTheme.textPrimary: Color get() = palette.textColor
val LauncherTheme.accentDeep: Color get() = palette.deepAccent
val LauncherTheme.primary: Color get() = palette.primary
val LauncherTheme.selectionGlow: Color get() = palette.selectionGlow
val LauncherTheme.surfaceTint: Color get() = palette.squirclePrimary

/**
 * Mochibun Unified Design Language Tokens (OEM System Standards).
 * Exposes dynamic Material 3 and Mochibun tokens reactively derived from [LocalLauncherTheme].
 */
object ActiveTheme {
    val current: LauncherTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalLauncherTheme.current

    val palette: MochibunThemePalette
        @Composable
        @ReadOnlyComposable
        get() = current.palette

    val background: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.background

    val surface: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.container

    val container: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.container

    val cardBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.outline

    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.textColor

    val accentDeep: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.deepAccent

    val primary: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.primary

    val selectionGlow: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.selectionGlow

    val surfaceTint: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.squirclePrimary

    val unfocusedCardBg: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.unfocusedCardBg

    val unfocusedCardBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = palette.unfocusedCardBorder

    val mascotTint: Color?
        @Composable
        @ReadOnlyComposable
        get() = current.mascotTint ?: palette.mascotTint
}

data class CustomPreset(
    val id: String,
    val title: String,
    val theme: LauncherTheme
)

fun loadCustomPresets(prefs: SharedPreferences): List<CustomPreset> {
    val ids = prefs.getStringSet("custom_preset_ids", emptySet()) ?: emptySet()

    return ids.sorted().mapNotNull { id ->
        val title = prefs.getString("custom_preset_title_$id", id) ?: id
        val name = "custom_$id"

        val bgColor = prefs.getInt(
            "preset_custom_${id}_bg",
            SakuraDreamTheme.bgColor.toArgb()
        )
        val fgColor = prefs.getInt(
            "preset_custom_${id}_fg",
            SakuraDreamTheme.fgTextColor.toArgb()
        )
        val cardBg = prefs.getInt(
            "preset_custom_${id}_card_bg",
            SakuraDreamTheme.cardBgColor.toArgb()
        )
        val cardText = prefs.getInt(
            "preset_custom_${id}_card_text",
            SakuraDreamTheme.cardTextColor.toArgb()
        )
        val greeting = prefs.getInt(
            "preset_custom_${id}_greeting",
            SakuraDreamTheme.greetingColor.toArgb()
        )
        val outline = prefs.getInt(
            "preset_custom_${id}_outline",
            SakuraDreamTheme.greetingOutlineColor.toArgb()
        )
        val gBg = prefs.getInt(
            "preset_custom_${id}_gbg",
            SakuraDreamTheme.greetingBgColor.toArgb()
        )
        val action = prefs.getInt(
            "preset_custom_${id}_action",
            SakuraDreamTheme.appActionColor.toArgb()
        )
        val roundness = prefs.getFloat(
            "preset_custom_${id}_round",
            SakuraDreamTheme.cardRoundnessDp
        )

        CustomPreset(
            id = id,
            title = title,
            theme = LauncherTheme(
                name = name,
                bgColor = Color(bgColor),
                fgTextColor = Color(fgColor),
                cardBgColor = Color(cardBg),
                cardTextColor = Color(cardText),
                greetingColor = Color(greeting),
                greetingOutlineColor = Color(outline),
                greetingBgColor = Color(gBg),
                appActionColor = Color(action),
                cardRoundnessDp = roundness
            )
        )
    }
}

fun saveCustomPreset(
    prefs: SharedPreferences,
    title: String,
    theme: LauncherTheme
) {
    val currentIds = (
            prefs.getStringSet("custom_preset_ids", emptySet()) ?: emptySet()
            ).toMutableSet()

    val id = System.currentTimeMillis().toString()
    currentIds.add(id)

    prefs.edit()
        .putStringSet("custom_preset_ids", currentIds)
        .putString("custom_preset_title_$id", title)
        .putInt("preset_custom_${id}_bg", theme.bgColor.toArgb())
        .putInt("preset_custom_${id}_fg", theme.fgTextColor.toArgb())
        .putInt("preset_custom_${id}_card_bg", theme.cardBgColor.toArgb())
        .putInt("preset_custom_${id}_card_text", theme.cardTextColor.toArgb())
        .putInt("preset_custom_${id}_greeting", theme.greetingColor.toArgb())
        .putInt("preset_custom_${id}_outline", theme.greetingOutlineColor.toArgb())
        .putInt("preset_custom_${id}_gbg", theme.greetingBgColor.toArgb())
        .putInt("preset_custom_${id}_action", theme.appActionColor.toArgb())
        .putFloat("preset_custom_${id}_round", theme.cardRoundnessDp)
        .apply()
}

fun deleteCustomPreset(prefs: SharedPreferences, id: String) {
    val currentIds = (
            prefs.getStringSet("custom_preset_ids", emptySet()) ?: emptySet()
            ).toMutableSet()

    currentIds.remove(id)

    prefs.edit()
        .putStringSet("custom_preset_ids", currentIds)
        .remove("custom_preset_title_$id")
        .remove("preset_custom_${id}_bg")
        .remove("preset_custom_${id}_fg")
        .remove("preset_custom_${id}_card_bg")
        .remove("preset_custom_${id}_card_text")
        .remove("preset_custom_${id}_greeting")
        .remove("preset_custom_${id}_outline")
        .remove("preset_custom_${id}_gbg")
        .remove("preset_custom_${id}_action")
        .remove("preset_custom_${id}_round")
        .apply()
}

val LocalLauncherTheme = compositionLocalOf { SakuraDreamTheme }

fun loadSavedTheme(prefs: SharedPreferences): LauncherTheme {
    val presetName = prefs.getString("theme_preset", "sakura_dream") ?: "sakura_dream"

    if (presetName.startsWith("custom_")) {
        val id = presetName.removePrefix("custom_")
        val found = loadCustomPresets(prefs).firstOrNull { it.id == id }
        if (found != null) return found.theme
    }

    val externalTheme = ExternalThemeManager.findTheme(presetName)
    if (externalTheme != null) {
        if (!prefs.contains("theme_bg_color")) {
            return externalTheme.theme
        }
    }

    val base = externalTheme?.theme ?: when (presetName) {
        "lavender_mist", "lavender_dream" -> LavenderMistTheme
        "minty_matcha" -> MintyMatchaTheme
        "milky_peach" -> MilkyPeachTheme
        "midnight_mochi" -> MidnightMochiTheme
        else -> SakuraDreamTheme
    }

    if (!prefs.contains("theme_bg_color") && (presetName == "sakura_dream" || presetName == "original")) {
        return base
    }

    return LauncherTheme(
        name = presetName,
        bgColor = Color(
            prefs.getInt("theme_bg_color", base.bgColor.toArgb())
        ),
        fgTextColor = Color(
            prefs.getInt("theme_fg_text_color", base.fgTextColor.toArgb())
        ),
        cardBgColor = Color(
            prefs.getInt("theme_card_bg_color", base.cardBgColor.toArgb())
        ),
        cardTextColor = Color(
            prefs.getInt("theme_card_text_color", base.cardTextColor.toArgb())
        ),
        greetingColor = Color(
            prefs.getInt("theme_greeting_color", base.greetingColor.toArgb())
        ),
        greetingOutlineColor = Color(
            prefs.getInt(
                "theme_greeting_outline_color",
                base.greetingOutlineColor.toArgb()
            )
        ),
        greetingBgColor = Color(
            prefs.getInt(
                "theme_greeting_bg_color",
                base.greetingBgColor.toArgb()
            )
        ),
        appActionColor = Color(
            prefs.getInt("theme_app_action_color", base.appActionColor.toArgb())
        ),
        cardRoundnessDp = prefs.getFloat(
            "theme_card_roundness",
            base.cardRoundnessDp
        ),
        mascotTint = base.mascotTint
    )
}

fun saveTheme(prefs: SharedPreferences, theme: LauncherTheme) {
    prefs.edit()
        .putString("theme_preset", theme.name)
        .putInt("theme_bg_color", theme.bgColor.toArgb())
        .putInt("theme_fg_text_color", theme.fgTextColor.toArgb())
        .putInt("theme_card_bg_color", theme.cardBgColor.toArgb())
        .putInt("theme_card_text_color", theme.cardTextColor.toArgb())
        .putInt("theme_greeting_color", theme.greetingColor.toArgb())
        .putInt("theme_greeting_outline_color", theme.greetingOutlineColor.toArgb())
        .putInt("theme_greeting_bg_color", theme.greetingBgColor.toArgb())
        .putInt("theme_app_action_color", theme.appActionColor.toArgb())
        .putFloat("theme_card_roundness", theme.cardRoundnessDp)
        .apply()
}
