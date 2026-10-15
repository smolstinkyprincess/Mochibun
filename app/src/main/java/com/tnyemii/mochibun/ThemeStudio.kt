package com.tnyemii.mochibun

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LauncherTheme(
    val name: String = "original",
    val bgColor: Color = Color(0xFFFFEAF5),
    val fgTextColor: Color = Color(0xFF9B5FA5),
    val cardBgColor: Color = Color(0xFFFFCCF1),
    val cardTextColor: Color = Color(0xFFD791E3),
    val greetingColor: Color = Color(0xFFA975B2),
    val greetingOutlineColor: Color = Color(0xFFD791E3),
    val greetingBgColor: Color = Color(0xFFFFEAF7),
    val appActionColor: Color = Color(0xFF9B5FA5),
    val cardRoundnessDp: Float = 24f
)

val OriginalTheme = LauncherTheme(
    name = "original",
    bgColor = Color(0xFFFFEAF5),
    fgTextColor = Color(0xFF9B5FA5),
    cardBgColor = Color(0xFFFFCCF1),
    cardTextColor = Color(0xFFD791E3),
    greetingColor = Color(0xFFA975B2),
    greetingOutlineColor = Color(0xFFD791E3),
    greetingBgColor = Color(0xFFFFEAF7),
    appActionColor = Color(0xFF9B5FA5),
    cardRoundnessDp = 24f
)

val StrawberryMilkTheme = LauncherTheme(
    name = "strawberry_milk",
    bgColor = Color(0xFFFFEFF6),
    fgTextColor = Color(0xFF8A335C),
    cardBgColor = Color(0xFFFFB3D9),
    cardTextColor = Color(0xFFD94680),
    greetingColor = Color(0xFFE06699),
    greetingOutlineColor = Color(0xFFFF80BF),
    greetingBgColor = Color(0xFFFFE6F2),
    appActionColor = Color(0xFFD94680),
    cardRoundnessDp = 24f
)

val LavenderDreamTheme = LauncherTheme(
    name = "lavender_dream",
    bgColor = Color(0xFFF3E8FF),
    fgTextColor = Color(0xFF4C1D95),
    cardBgColor = Color(0xFFD8B4FE),
    cardTextColor = Color(0xFF8B5CF6),
    greetingColor = Color(0xFF7C3AED),
    greetingOutlineColor = Color(0xFFC4B5FD),
    greetingBgColor = Color(0xFFFAF5FF),
    appActionColor = Color(0xFF7C3AED),
    cardRoundnessDp = 24f
)

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

val LauncherTheme.selectedContainerColor: Color
    get() = when (name) {
        "midnight_mochi" -> Color(0xFF5A3673)
        "strawberry_milk" -> Color(0xFFFFB3D9)
        "lavender_dream" -> Color(0xFFD8B4FE)
        else -> Color(0xFFFFD1F0)
    }

data class CustomPreset(
    val id: String,
    val title: String,
    val theme: LauncherTheme
)

fun loadCustomPresets(prefs: SharedPreferences): List<CustomPreset> {
    val ids = prefs.getStringSet("custom_preset_ids", emptySet()) ?: emptySet()

    return ids.mapNotNull { id ->
        val title = prefs.getString("custom_preset_title_$id", id) ?: id
        val name = "custom_$id"

        val bgColor = prefs.getInt(
            "preset_custom_${id}_bg",
            OriginalTheme.bgColor.toArgb()
        )
        val fgColor = prefs.getInt(
            "preset_custom_${id}_fg",
            OriginalTheme.fgTextColor.toArgb()
        )
        val cardBg = prefs.getInt(
            "preset_custom_${id}_card_bg",
            OriginalTheme.cardBgColor.toArgb()
        )
        val cardText = prefs.getInt(
            "preset_custom_${id}_card_text",
            OriginalTheme.cardTextColor.toArgb()
        )
        val greeting = prefs.getInt(
            "preset_custom_${id}_greeting",
            OriginalTheme.greetingColor.toArgb()
        )
        val outline = prefs.getInt(
            "preset_custom_${id}_outline",
            OriginalTheme.greetingOutlineColor.toArgb()
        )
        val gBg = prefs.getInt(
            "preset_custom_${id}_gbg",
            OriginalTheme.greetingBgColor.toArgb()
        )
        val action = prefs.getInt(
            "preset_custom_${id}_action",
            OriginalTheme.appActionColor.toArgb()
        )
        val roundness = prefs.getFloat(
            "preset_custom_${id}_round",
            OriginalTheme.cardRoundnessDp
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

val LocalLauncherTheme = compositionLocalOf { OriginalTheme }

fun loadSavedTheme(prefs: SharedPreferences): LauncherTheme {
    val presetName = prefs.getString("theme_preset", "original") ?: "original"

    if (presetName.startsWith("custom_")) {
        val id = presetName.removePrefix("custom_")
        val found = loadCustomPresets(prefs).firstOrNull { it.id == id }
        if (found != null) return found.theme
    }

    val base = when (presetName) {
        "strawberry_milk" -> StrawberryMilkTheme
        "lavender_dream" -> LavenderDreamTheme
        "midnight_mochi" -> MidnightMochiTheme
        else -> OriginalTheme
    }

    if (!prefs.contains("theme_bg_color") && presetName == "original") {
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
        )
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

val kawaiiSwatches = listOf(
    Color(0xFFFFEAF5), Color(0xFFFFCCF1), Color(0xFFFFB3D9),
    Color(0xFFFF66B2), Color(0xFFD791E3), Color(0xFFE675FF),
    Color(0xFFF3E8FF), Color(0xFFD8B4FE), Color(0xFFA975B2),
    Color(0xFF8B5CF6), Color(0xFF7C3AED), Color(0xFF6B21A8),
    Color(0xFF1E1128), Color(0xFF3B224C), Color(0xFF2A1638),
    Color(0xFFFFF5FB), Color(0xFFFFFFFF), Color(0xFF42234F),
    Color(0xFF9B5FA5), Color(0xFF8A335C), Color(0xFFD94680),
    Color(0xFFE879F9), Color(0xFFF472B6), Color(0xFFFF80BF)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioDialog(
    currentTheme: LauncherTheme,
    dialogWidth: Dp,
    onDismiss: () -> Unit,
    onApply: (LauncherTheme) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(
            "launcher_preferences",
            Context.MODE_PRIVATE
        )
    }

    var draft by remember(currentTheme) {
        mutableStateOf(currentTheme)
    }
    var editingColorField by remember { mutableStateOf<String?>(null) }
    var customPresets by remember { mutableStateOf(loadCustomPresets(prefs)) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetTitle by remember { mutableStateOf("") }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(draft.cardRoundnessDp.dp),
            color = draft.cardBgColor
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "🎨 Theme Studio ♡",
                    fontWeight = FontWeight.Bold,
                    color = draft.appActionColor,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Live Preview ✨",
                        fontWeight = FontWeight.Bold,
                        color = draft.fgTextColor,
                        fontSize = 14.sp
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.5.dp,
                                draft.greetingOutlineColor,
                                RoundedCornerShape(draft.cardRoundnessDp.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = draft.greetingBgColor
                        ),
                        shape = RoundedCornerShape(draft.cardRoundnessDp.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "♡ Hello, Mochibun ♡",
                                color = draft.greetingColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(
                                        RoundedCornerShape(
                                            (draft.cardRoundnessDp * 0.75f).dp
                                        )
                                    )
                                    .background(draft.cardBgColor)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Battery: 100% 🔋",
                                    color = draft.cardTextColor,
                                    fontSize = 12.sp
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(draft.appActionColor)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "Sample App",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Presets 🌸",
                            fontWeight = FontWeight.Bold,
                            color = draft.fgTextColor,
                            fontSize = 14.sp
                        )

                        TextButton(
                            onClick = { showSaveDialog = true },
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            Text(
                                "💾 Save as Preset",
                                fontSize = 12.sp,
                                color = draft.appActionColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetButton(
                            "Original",
                            draft.name == "original",
                            draft
                        ) {
                            draft = OriginalTheme
                        }

                        PresetButton(
                            "Strawberry",
                            draft.name == "strawberry_milk",
                            draft
                        ) {
                            draft = StrawberryMilkTheme
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetButton(
                            "Lavender",
                            draft.name == "lavender_dream",
                            draft
                        ) {
                            draft = LavenderDreamTheme
                        }

                        PresetButton(
                            "Midnight",
                            draft.name == "midnight_mochi",
                            draft
                        ) {
                            draft = MidnightMochiTheme
                        }
                    }

                    if (customPresets.isNotEmpty()) {
                        Text(
                            "Custom Presets ♡",
                            fontWeight = FontWeight.Bold,
                            color = draft.fgTextColor,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        customPresets.chunked(2).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowPresets.forEach { preset ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (draft.name == preset.theme.name) {
                                                    draft.selectedContainerColor
                                                } else {
                                                    draft.cardBgColor
                                                }
                                            )
                                            .clickable {
                                                draft = preset.theme
                                            }
                                            .padding(
                                                horizontal = 8.dp,
                                                vertical = 6.dp
                                            ),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            preset.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = draft.fgTextColor,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Text(
                                            "✕",
                                            fontSize = 12.sp,
                                            color = draft.appActionColor,
                                            modifier = Modifier
                                                .clickable {
                                                    deleteCustomPreset(
                                                        prefs,
                                                        preset.id
                                                    )
                                                    customPresets =
                                                        loadCustomPresets(prefs)
                                                }
                                                .padding(2.dp)
                                        )
                                    }
                                }

                                if (rowPresets.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    Text(
                        "Card Roundness: ${draft.cardRoundnessDp.toInt()} dp",
                        fontWeight = FontWeight.Bold,
                        color = draft.fgTextColor,
                        fontSize = 14.sp
                    )

                    Slider(
                        value = draft.cardRoundnessDp,
                        onValueChange = {
                            draft = draft.copy(cardRoundnessDp = it)
                        },
                        valueRange = 8f..32f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = draft.appActionColor,
                            activeTrackColor = draft.cardTextColor
                        )
                    )

                    Text(
                        "Individual Colors 🎨",
                        fontWeight = FontWeight.Bold,
                        color = draft.fgTextColor,
                        fontSize = 14.sp
                    )

                    ColorRow("Main Background", draft.bgColor, draft) {
                        editingColorField = "bg"
                    }
                    ColorRow("Card Background", draft.cardBgColor, draft) {
                        editingColorField = "cardBg"
                    }
                    ColorRow("Card Text", draft.cardTextColor, draft) {
                        editingColorField = "cardText"
                    }
                    ColorRow("Accent / Action", draft.appActionColor, draft) {
                        editingColorField = "accent"
                    }
                    ColorRow("Card Outline", draft.greetingOutlineColor, draft) {
                        editingColorField = "outline"
                    }
                    ColorRow("Primary Text", draft.fgTextColor, draft) {
                        editingColorField = "fg"
                    }

                    if (editingColorField != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = draft.greetingBgColor
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Select swatch for $editingColorField",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = draft.fgTextColor
                                    )

                                    TextButton(
                                        onClick = { editingColorField = null }
                                    ) {
                                        Text(
                                            "Close",
                                            fontSize = 12.sp,
                                            color = draft.appActionColor
                                        )
                                    }
                                }

                                kawaiiSwatches.chunked(6).forEach { rowSwatches ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        rowSwatches.forEach { swatch ->
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(swatch)
                                                    .border(
                                                        1.dp,
                                                        Color.Gray.copy(alpha = 0.5f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable {
                                                        draft = when (editingColorField) {
                                                            "bg" -> draft.copy(
                                                                bgColor = swatch
                                                            )
                                                            "cardBg" -> draft.copy(
                                                                cardBgColor = swatch
                                                            )
                                                            "cardText" -> draft.copy(
                                                                cardTextColor = swatch
                                                            )
                                                            "accent" -> draft.copy(
                                                                appActionColor = swatch,
                                                                greetingColor = swatch
                                                            )
                                                            "outline" -> draft.copy(
                                                                greetingOutlineColor = swatch
                                                            )
                                                            "fg" -> draft.copy(
                                                                fgTextColor = swatch
                                                            )
                                                            else -> draft
                                                        }

                                                        editingColorField = null
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Cancel",
                            color = draft.appActionColor,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { onApply(draft) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = draft.appActionColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Apply ♡",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        BasicAlertDialog(
            onDismissRequest = { showSaveDialog = false }
        ) {
            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = 300.dp),
                shape = RoundedCornerShape(draft.cardRoundnessDp.dp),
                color = draft.cardBgColor
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Save Custom Preset ♡",
                        fontWeight = FontWeight.Bold,
                        color = draft.appActionColor
                    )

                    OutlinedTextField(
                        value = newPresetTitle,
                        onValueChange = { newPresetTitle = it },
                        placeholder = {
                            Text("Preset name (e.g. Kitty Pink)")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showSaveDialog = false }
                        ) {
                            Text("Cancel", color = draft.appActionColor)
                        }

                        Button(
                            onClick = {
                                if (newPresetTitle.isNotBlank()) {
                                    saveCustomPreset(
                                        prefs,
                                        newPresetTitle.trim(),
                                        draft
                                    )
                                    customPresets = loadCustomPresets(prefs)
                                    showSaveDialog = false
                                    newPresetTitle = ""
                                }
                            },
                            enabled = newPresetTitle.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = draft.appActionColor
                            )
                        ) {
                            Text(
                                "Save",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.PresetButton(
    text: String,
    isSelected: Boolean,
    theme: LauncherTheme,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                theme.selectedContainerColor
            } else {
                theme.cardBgColor
            }
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = theme.fgTextColor
        )
    }
}

@Composable
fun ColorRow(
    label: String,
    color: Color,
    theme: LauncherTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = theme.fgTextColor
        )

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .border(
                    1.dp,
                    Color.Gray.copy(alpha = 0.5f),
                    RoundedCornerShape(6.dp)
                )
        )
    }
}