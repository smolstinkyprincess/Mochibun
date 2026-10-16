package com.tnyemii.mochibun

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.tnyemii.mochibun.ui.theme.destructiveColor
import com.tnyemii.mochibun.ui.theme.softSurfaceColor
import kotlinx.coroutines.delay

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
        "strawberry_milk" -> Color(0xFFFFCCE6)
        "lavender_dream" -> Color(0xFFE9D5FF)
        else -> Color(0xFFFFDEF5)
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

private val COLOR_FIELD_DEFS = listOf(
    "bg" to "Main Background",
    "cardBg" to "Card Background",
    "cardText" to "Card Text",
    "accent" to "Accent / Action",
    "outline" to "Card Outline",
    "fg" to "Primary Text"
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
    val keyboardController = LocalSoftwareKeyboardController.current
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

    // Main dialog focus state:
    // Row 0: Save as Preset button
    // Row 1: Built-in presets row 1 (col 0 = Original, col 1 = Strawberry)
    // Row 2: Built-in presets row 2 (col 0 = Lavender, col 1 = Midnight)
    // Row 3: Custom presets (index 0 until customPresets.size; subCol 0 = select, 1 = delete)
    // Row 4: Card Roundness slider (LEFT/RIGHT adjusts by 1dp)
    // Row 5..10: Individual Color rows (0..5)
    // Row 11: Footer buttons (col 0 = Cancel, col 1 = Apply)
    var focusRow by remember { mutableIntStateOf(1) }
    var focusCol by remember { mutableIntStateOf(0) }
    var customPresetIndex by remember { mutableIntStateOf(0) }
    var customPresetDeleteCol by remember { mutableIntStateOf(0) }
    var selectedSwatchIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    // Save Preset sub-dialog focus state:
    // 0 = Name field, 1 = Cancel, 2 = Save
    var saveDialogZone by remember { mutableIntStateOf(0) }

    val studioFocusRequester = remember { FocusRequester() }
    val saveDialogFocusRequester = remember { FocusRequester() }
    val saveNameFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(customPresets.size) {
        customPresetIndex = customPresetIndex.coerceIn(0, (customPresets.size - 1).coerceAtLeast(0))
        if (customPresets.isEmpty() && focusRow == 3) {
            focusRow = 4
        }
    }

    LaunchedEffect(focusRow, editingColorField) {
        val targetScroll = when {
            editingColorField != null -> scrollState.maxValue
            focusRow <= 1 -> 0
            focusRow in 2..4 -> (scrollState.maxValue * 0.35f).toInt()
            focusRow in 5..10 -> (scrollState.maxValue * 0.78f).toInt()
            else -> scrollState.maxValue
        }
        scrollState.animateScrollTo(targetScroll)
    }

    LaunchedEffect(showSaveDialog, saveDialogZone, editingColorField) {
        if (showSaveDialog) {
            if (saveDialogZone == 0) {
                repeat(6) {
                    try {
                        saveNameFocusRequester.requestFocus()
                        return@LaunchedEffect
                    } catch (_: Exception) {
                        delay(35)
                    }
                }
            } else {
                keyboardController?.hide()
                repeat(6) {
                    try {
                        saveDialogFocusRequester.requestFocus()
                        return@LaunchedEffect
                    } catch (_: Exception) {
                        delay(35)
                    }
                }
            }
        } else {
            keyboardController?.hide()
            repeat(6) {
                try {
                    studioFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        }
    }

    val applySwatch: (Color) -> Unit = { swatch ->
        draft = when (editingColorField) {
            "bg" -> draft.copy(bgColor = swatch)
            "cardBg" -> draft.copy(cardBgColor = swatch)
            "cardText" -> draft.copy(cardTextColor = swatch)
            "accent" -> draft.copy(appActionColor = swatch, greetingColor = swatch)
            "outline" -> draft.copy(greetingOutlineColor = swatch)
            "fg" -> draft.copy(fgTextColor = swatch)
            else -> draft
        }
        editingColorField = null
    }

    BackHandler {
        when {
            showSaveDialog -> showSaveDialog = false
            editingColorField != null -> editingColorField = null
            else -> onDismiss()
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            when {
                showSaveDialog -> showSaveDialog = false
                editingColorField != null -> editingColorField = null
                else -> onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(studioFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (showSaveDialog) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent

                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        if (editingColorField != null) {
                            editingColorField = null
                        } else {
                            onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                // Swatch picker active
                if (editingColorField != null) {
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            if (selectedSwatchIndex == kawaiiSwatches.size) {
                                editingColorField = null
                            } else {
                                kawaiiSwatches.getOrNull(selectedSwatchIndex)?.let(applySwatch)
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val cols = 6
                    when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (selectedSwatchIndex > 0) selectedSwatchIndex--
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (selectedSwatchIndex < kawaiiSwatches.size) selectedSwatchIndex++
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (selectedSwatchIndex == kawaiiSwatches.size) {
                                selectedSwatchIndex = kawaiiSwatches.lastIndex
                            } else if (selectedSwatchIndex >= cols) {
                                selectedSwatchIndex -= cols
                            } else {
                                selectedSwatchIndex = kawaiiSwatches.size
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (selectedSwatchIndex == kawaiiSwatches.size) {
                                selectedSwatchIndex = 0
                            } else if (selectedSwatchIndex + cols < kawaiiSwatches.size) {
                                selectedSwatchIndex += cols
                            } else {
                                selectedSwatchIndex = kawaiiSwatches.size
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    // Main Theme Studio navigation
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            when (focusRow) {
                                0 -> {
                                    saveDialogZone = 0
                                    showSaveDialog = true
                                }
                                1 -> {
                                    draft = if (focusCol == 0) OriginalTheme else StrawberryMilkTheme
                                }
                                2 -> {
                                    draft = if (focusCol == 0) LavenderDreamTheme else MidnightMochiTheme
                                }
                                3 -> {
                                    customPresets.getOrNull(customPresetIndex)?.let { preset ->
                                        if (customPresetDeleteCol == 1) {
                                            deleteCustomPreset(prefs, preset.id)
                                            customPresets = loadCustomPresets(prefs)
                                            customPresetDeleteCol = 0
                                        } else {
                                            draft = preset.theme
                                        }
                                    }
                                }
                                4 -> {
                                    // Cycle common roundness values or step up on OK
                                    val next = if (draft.cardRoundnessDp >= 32f) 8f else (draft.cardRoundnessDp + 4f).coerceAtMost(32f)
                                    draft = draft.copy(cardRoundnessDp = next)
                                }
                                in 5..10 -> {
                                    val fieldKey = COLOR_FIELD_DEFS[focusRow - 5].first
                                    selectedSwatchIndex = 0
                                    editingColorField = fieldKey
                                }
                                11 -> {
                                    if (focusCol == 0) {
                                        onDismiss()
                                    } else {
                                        onApply(draft)
                                    }
                                }
                            }
                        }
                        return@onPreviewKeyEvent true
                    }

                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            when (focusRow) {
                                0 -> {
                                    focusRow = 11
                                    focusCol = 1
                                }
                                3 -> {
                                    if (customPresetIndex > 0) {
                                        customPresetIndex--
                                    } else {
                                        focusRow = 2
                                    }
                                }
                                4 -> {
                                    if (customPresets.isNotEmpty()) {
                                        focusRow = 3
                                        customPresetIndex = customPresets.lastIndex
                                    } else {
                                        focusRow = 2
                                    }
                                }
                                else -> {
                                    focusRow = (focusRow - 1).coerceAtLeast(0)
                                }
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            when (focusRow) {
                                2 -> {
                                    if (customPresets.isNotEmpty()) {
                                        focusRow = 3
                                        customPresetIndex = 0
                                    } else {
                                        focusRow = 4
                                    }
                                }
                                3 -> {
                                    if (customPresetIndex < customPresets.lastIndex) {
                                        customPresetIndex++
                                    } else {
                                        focusRow = 4
                                    }
                                }
                                11 -> {
                                    focusRow = 0
                                }
                                else -> {
                                    focusRow = (focusRow + 1).coerceAtMost(11)
                                }
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            when (focusRow) {
                                1, 2, 11 -> focusCol = 0
                                3 -> customPresetDeleteCol = 0
                                4 -> {
                                    draft = draft.copy(
                                        cardRoundnessDp = (draft.cardRoundnessDp - 2f).coerceIn(8f, 32f)
                                    )
                                }
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            when (focusRow) {
                                1, 2, 11 -> focusCol = 1
                                3 -> customPresetDeleteCol = 1
                                4 -> {
                                    draft = draft.copy(
                                        cardRoundnessDp = (draft.cardRoundnessDp + 2f).coerceIn(8f, 32f)
                                    )
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        val shape = RoundedCornerShape(draft.cardRoundnessDp.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 580.dp)
                .border(2.5.dp, draft.greetingOutlineColor, shape),
            shape = shape,
            color = draft.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "🎨 Theme Studio ♡",
                    fontWeight = FontWeight.Bold,
                    color = draft.fgTextColor,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Live Preview ✨",
                        fontWeight = FontWeight.Bold,
                        color = draft.fgTextColor,
                        fontSize = 13.sp
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                2.dp,
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
                            fontSize = 13.sp
                        )

                        KawaiiDialogPillButton(
                            text = "💾 Save Preset",
                            isFocused = focusRow == 0 && editingColorField == null,
                            onClick = {
                                focusRow = 0
                                saveDialogZone = 0
                                showSaveDialog = true
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetButton(
                            text = "Original",
                            isSelected = draft.name == "original",
                            isFocused = editingColorField == null && focusRow == 1 && focusCol == 0,
                            theme = draft
                        ) {
                            focusRow = 1
                            focusCol = 0
                            draft = OriginalTheme
                        }

                        PresetButton(
                            text = "Strawberry",
                            isSelected = draft.name == "strawberry_milk",
                            isFocused = editingColorField == null && focusRow == 1 && focusCol == 1,
                            theme = draft
                        ) {
                            focusRow = 1
                            focusCol = 1
                            draft = StrawberryMilkTheme
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetButton(
                            text = "Lavender",
                            isSelected = draft.name == "lavender_dream",
                            isFocused = editingColorField == null && focusRow == 2 && focusCol == 0,
                            theme = draft
                        ) {
                            focusRow = 2
                            focusCol = 0
                            draft = LavenderDreamTheme
                        }

                        PresetButton(
                            text = "Midnight",
                            isSelected = draft.name == "midnight_mochi",
                            isFocused = editingColorField == null && focusRow == 2 && focusCol == 1,
                            theme = draft
                        ) {
                            focusRow = 2
                            focusCol = 1
                            draft = MidnightMochiTheme
                        }
                    }

                    if (customPresets.isNotEmpty()) {
                        Text(
                            "Custom Presets ♡",
                            fontWeight = FontWeight.Bold,
                            color = draft.fgTextColor,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        customPresets.forEachIndexed { idx, preset ->
                            val isRowFocused = editingColorField == null && focusRow == 3 && customPresetIndex == idx
                            val selectFocused = isRowFocused && customPresetDeleteCol == 0
                            val deleteFocused = isRowFocused && customPresetDeleteCol == 1
                            val isSelected = draft.name == preset.theme.name

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selectFocused || isSelected) {
                                                draft.selectedContainerColor
                                            } else {
                                                draft.greetingBgColor.copy(alpha = 0.65f)
                                            }
                                        )
                                        .border(
                                            width = if (selectFocused) 2.dp else 1.dp,
                                            color = if (selectFocused) draft.appActionColor else draft.greetingOutlineColor,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            focusRow = 3
                                            customPresetIndex = idx
                                            customPresetDeleteCol = 0
                                            draft = preset.theme
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (isSelected) "✓ ${preset.title}" else preset.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = draft.fgTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                KawaiiDialogPillButton(
                                    text = "✕",
                                    isFocused = deleteFocused,
                                    isDestructive = true,
                                    onClick = {
                                        deleteCustomPreset(prefs, preset.id)
                                        customPresets = loadCustomPresets(prefs)
                                    }
                                )
                            }
                        }
                    }

                    val sliderFocused = editingColorField == null && focusRow == 4
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (sliderFocused) draft.selectedContainerColor else Color.Transparent
                            )
                            .border(
                                width = if (sliderFocused) 2.dp else 0.dp,
                                color = if (sliderFocused) draft.appActionColor else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Card Roundness: ${draft.cardRoundnessDp.toInt()} dp",
                                fontWeight = FontWeight.Bold,
                                color = draft.fgTextColor,
                                fontSize = 13.sp
                            )
                            if (sliderFocused) {
                                Text(
                                    "◀ / ▶",
                                    fontWeight = FontWeight.Bold,
                                    color = draft.appActionColor,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Slider(
                            value = draft.cardRoundnessDp,
                            onValueChange = {
                                focusRow = 4
                                draft = draft.copy(cardRoundnessDp = it)
                            },
                            valueRange = 8f..32f,
                            steps = 23,
                            colors = SliderDefaults.colors(
                                thumbColor = draft.appActionColor,
                                activeTrackColor = draft.cardTextColor
                            )
                        )
                    }

                    Text(
                        "Individual Colors 🎨",
                        fontWeight = FontWeight.Bold,
                        color = draft.fgTextColor,
                        fontSize = 13.sp
                    )

                    COLOR_FIELD_DEFS.forEachIndexed { idx, (key, label) ->
                        val colorVal = when (key) {
                            "bg" -> draft.bgColor
                            "cardBg" -> draft.cardBgColor
                            "cardText" -> draft.cardTextColor
                            "accent" -> draft.appActionColor
                            "outline" -> draft.greetingOutlineColor
                            else -> draft.fgTextColor
                        }
                        ColorRow(
                            label = label,
                            color = colorVal,
                            isFocused = editingColorField == null && focusRow == 5 + idx,
                            theme = draft
                        ) {
                            focusRow = 5 + idx
                            selectedSwatchIndex = 0
                            editingColorField = key
                        }
                    }

                    if (editingColorField != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, draft.appActionColor, RoundedCornerShape(14.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = draft.greetingBgColor
                            ),
                            shape = RoundedCornerShape(14.dp)
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
                                        "Pick swatch ($editingColorField)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = draft.fgTextColor
                                    )

                                    KawaiiDialogPillButton(
                                        text = "Close",
                                        isFocused = selectedSwatchIndex == kawaiiSwatches.size,
                                        onClick = { editingColorField = null }
                                    )
                                }

                                kawaiiSwatches.chunked(6).forEachIndexed { rIdx, rowSwatches ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        rowSwatches.forEachIndexed { cIdx, swatch ->
                                            val swatchIndex = rIdx * 6 + cIdx
                                            val isSwatchFocused = selectedSwatchIndex == swatchIndex
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(swatch)
                                                    .border(
                                                        width = if (isSwatchFocused) 3.dp else 1.dp,
                                                        color = if (isSwatchFocused) {
                                                            draft.fgTextColor
                                                        } else {
                                                            Color.Gray.copy(alpha = 0.5f)
                                                        },
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        selectedSwatchIndex = swatchIndex
                                                        applySwatch(swatch)
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

                HorizontalDivider(color = draft.appActionColor.copy(alpha = 0.22f))

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    KawaiiDialogPillButton(
                        text = "Cancel",
                        isFocused = editingColorField == null && focusRow == 11 && focusCol == 0,
                        onClick = onDismiss
                    )

                    KawaiiDialogPillButton(
                        text = "Apply ♡",
                        isFocused = editingColorField == null && focusRow == 11 && focusCol == 1,
                        isPrimary = true,
                        onClick = { onApply(draft) }
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        BasicAlertDialog(
            onDismissRequest = { showSaveDialog = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            ),
            modifier = Modifier
                .focusRequester(saveDialogFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (isBackDismissKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyUp) {
                            keyboardController?.hide()
                            showSaveDialog = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (saveDialogZone == 0) {
                        if (event.type == KeyEventType.KeyDown) {
                            when (native.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                android.view.KeyEvent.KEYCODE_ENTER,
                                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    saveDialogZone = if (newPresetTitle.isNotBlank()) 2 else 1
                                    return@onPreviewKeyEvent true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    saveDialogZone = 1
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                        return@onPreviewKeyEvent false
                    }
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            if (saveDialogZone == 1) {
                                showSaveDialog = false
                            } else if (saveDialogZone == 2 && newPresetTitle.isNotBlank()) {
                                saveCustomPreset(prefs, newPresetTitle.trim(), draft)
                                customPresets = loadCustomPresets(prefs)
                                showSaveDialog = false
                                newPresetTitle = ""
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            saveDialogZone = 0
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            saveDialogZone = 1
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            saveDialogZone = 2
                            true
                        }
                        else -> false
                    }
                }
        ) {
            val shape = RoundedCornerShape(draft.cardRoundnessDp.dp)
            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = 300.dp)
                    .border(2.5.dp, draft.greetingOutlineColor, shape),
                shape = shape,
                color = draft.cardBgColor,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Save Custom Preset ♡",
                        fontWeight = FontWeight.Bold,
                        color = draft.fgTextColor,
                        fontSize = 17.sp
                    )

                    OutlinedTextField(
                        value = newPresetTitle,
                        onValueChange = { newPresetTitle = it.take(24) },
                        placeholder = {
                            Text("Preset name (e.g. Kitty Pink)")
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = draft.appActionColor,
                            unfocusedBorderColor = draft.greetingOutlineColor,
                            focusedTextColor = draft.fgTextColor,
                            unfocusedTextColor = draft.fgTextColor,
                            focusedContainerColor = softSurfaceColor,
                            unfocusedContainerColor = softSurfaceColor.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(saveNameFocusRequester)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        KawaiiDialogPillButton(
                            text = "Cancel",
                            isFocused = saveDialogZone == 1,
                            onClick = { showSaveDialog = false }
                        )

                        KawaiiDialogPillButton(
                            text = "Save ♡",
                            isFocused = saveDialogZone == 2,
                            enabled = newPresetTitle.isNotBlank(),
                            isPrimary = true,
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
                            }
                        )
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
    isFocused: Boolean = false,
    theme: LauncherTheme,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(
                if (isFocused || isSelected) {
                    theme.selectedContainerColor
                } else {
                    theme.greetingBgColor.copy(alpha = 0.65f)
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
                color = if (isFocused || isSelected) theme.appActionColor else theme.greetingOutlineColor,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSelected) "✓ $text" else text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = theme.fgTextColor,
            maxLines = 1
        )
    }
}

@Composable
fun ColorRow(
    label: String,
    color: Color,
    isFocused: Boolean = false,
    theme: LauncherTheme,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isFocused) theme.selectedContainerColor else Color.Transparent
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) theme.appActionColor else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            color = theme.fgTextColor
        )

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) theme.fgTextColor else Color.Gray.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp)
                )
        )
    }
}
