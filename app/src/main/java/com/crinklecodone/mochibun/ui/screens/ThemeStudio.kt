package com.crinklecodone.mochibun.ui.screens

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.crinklecodone.mochibun.data.repository.ExternalThemeManager
import com.crinklecodone.mochibun.ui.dialogs.KawaiiDialogPillButton
import com.crinklecodone.mochibun.ui.dialogs.PixelRibbonBow
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.theme.*
import com.crinklecodone.mochibun.ui.components.*
import kotlinx.coroutines.delay

val kawaiiSwatches = listOf(
    Color(0xFFF8BBD0), Color(0xFFFCE4EC), Color(0xFFA04064),
    Color(0xFFD1C4E9), Color(0xFFEDE7F6), Color(0xFF5E35B1),
    Color(0xFFC8E6C9), Color(0xFFE8F5E9), Color(0xFF2E7D32),
    Color(0xFFFFE0B2), Color(0xFFFFF3E0), Color(0xFF8D4E28),
    Color(0xFFFFEAF5), Color(0xFFFFCCF1), Color(0xFFFFB3D9),
    Color(0xFFD791E3), Color(0xFFF3E8FF), Color(0xFFD8B4FE),
    Color(0xFF1E1128), Color(0xFF3B224C), Color(0xFF2A1638),
    Color(0xFFFFF5FB), Color(0xFFFFFFFF), Color(0xFF703350)
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

    val themeOptions by ExternalThemeManager.themes.collectAsState()
    val externalThemes = remember(themeOptions) { themeOptions.filter { it.isExternal } }

    LaunchedEffect(Unit) {
        ExternalThemeManager.scanThemes(context)
    }

    // Main dialog focus state:
    // Row 0: Save as Preset button
    // Row 1: Built-in presets row 1 (col 0 = Sakura Dream, col 1 = Lavender Mist)
    // Row 2: Built-in presets row 2 (col 0 = Minty Matcha, col 1 = Milky Peach)
    // Row 3: External YAML themes (index 0 until externalThemes.size)
    // Row 4: Custom presets (index 0 until customPresets.size; subCol 0 = select, 1 = delete)
    // Row 5: Card Roundness slider (LEFT/RIGHT adjusts by 1dp)
    // Row 6..11: Individual Color rows (0..5)
    // Row 12: Footer buttons (col 0 = Cancel, col 1 = Apply)
    var focusRow by remember { mutableIntStateOf(1) }
    var focusCol by remember { mutableIntStateOf(0) }
    var externalThemeIndex by remember { mutableIntStateOf(0) }
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

    LaunchedEffect(externalThemes.size, customPresets.size) {
        externalThemeIndex = externalThemeIndex.coerceIn(0, (externalThemes.size - 1).coerceAtLeast(0))
        customPresetIndex = customPresetIndex.coerceIn(0, (customPresets.size - 1).coerceAtLeast(0))
        if (externalThemes.isEmpty() && focusRow == 3) {
            focusRow = if (customPresets.isNotEmpty()) 4 else 5
        } else if (customPresets.isEmpty() && focusRow == 4) {
            focusRow = 5
        }
    }

    LaunchedEffect(focusRow, editingColorField) {
        val targetScroll = when {
            editingColorField != null -> scrollState.maxValue
            focusRow <= 1 -> 0
            focusRow in 2..5 -> (scrollState.maxValue * 0.35f).toInt()
            focusRow in 6..11 -> (scrollState.maxValue * 0.78f).toInt()
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
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (selectedSwatchIndex > 0) selectedSwatchIndex--
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (selectedSwatchIndex < kawaiiSwatches.size) selectedSwatchIndex++
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (selectedSwatchIndex == kawaiiSwatches.size) {
                                selectedSwatchIndex = kawaiiSwatches.lastIndex
                            } else if (selectedSwatchIndex >= cols) {
                                selectedSwatchIndex -= cols
                            } else {
                                selectedSwatchIndex = kawaiiSwatches.size
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
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
                                    draft = if (focusCol == 0) {
                                        SakuraDreamPalette.toLauncherTheme(draft.cardRoundnessDp)
                                    } else {
                                        LavenderMistPalette.toLauncherTheme(draft.cardRoundnessDp)
                                    }
                                }
                                2 -> {
                                    draft = if (focusCol == 0) {
                                        MintyMatchaPalette.toLauncherTheme(draft.cardRoundnessDp)
                                    } else {
                                        MilkyPeachPalette.toLauncherTheme(draft.cardRoundnessDp)
                                    }
                                }
                                3 -> {
                                    externalThemes.getOrNull(externalThemeIndex)?.let { ext ->
                                        draft = ext.theme.copy(cardRoundnessDp = draft.cardRoundnessDp)
                                    }
                                }
                                4 -> {
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
                                5 -> {
                                    val next = if (draft.cardRoundnessDp >= 32f) 8f else (draft.cardRoundnessDp + 4f).coerceAtMost(32f)
                                    draft = draft.copy(cardRoundnessDp = next)
                                }
                                in 6..11 -> {
                                    val fieldKey = COLOR_FIELD_DEFS[focusRow - 6].first
                                    selectedSwatchIndex = 0
                                    editingColorField = fieldKey
                                }
                                12 -> {
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
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            when (focusRow) {
                                0 -> {
                                    focusRow = 12
                                    focusCol = 1
                                }
                                3 -> {
                                    if (externalThemeIndex > 0) {
                                        externalThemeIndex--
                                    } else {
                                        focusRow = 2
                                    }
                                }
                                4 -> {
                                    if (customPresetIndex > 0) {
                                        customPresetIndex--
                                    } else if (externalThemes.isNotEmpty()) {
                                        focusRow = 3
                                        externalThemeIndex = externalThemes.lastIndex
                                    } else {
                                        focusRow = 2
                                    }
                                }
                                5 -> {
                                    if (customPresets.isNotEmpty()) {
                                        focusRow = 4
                                        customPresetIndex = customPresets.lastIndex
                                    } else if (externalThemes.isNotEmpty()) {
                                        focusRow = 3
                                        externalThemeIndex = externalThemes.lastIndex
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
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            when (focusRow) {
                                2 -> {
                                    if (externalThemes.isNotEmpty()) {
                                        focusRow = 3
                                        externalThemeIndex = 0
                                    } else if (customPresets.isNotEmpty()) {
                                        focusRow = 4
                                        customPresetIndex = 0
                                    } else {
                                        focusRow = 5
                                    }
                                }
                                3 -> {
                                    if (externalThemeIndex < externalThemes.lastIndex) {
                                        externalThemeIndex++
                                    } else if (customPresets.isNotEmpty()) {
                                        focusRow = 4
                                        customPresetIndex = 0
                                    } else {
                                        focusRow = 5
                                    }
                                }
                                4 -> {
                                    if (customPresetIndex < customPresets.lastIndex) {
                                        customPresetIndex++
                                    } else {
                                        focusRow = 5
                                    }
                                }
                                12 -> {
                                    focusRow = 0
                                }
                                else -> {
                                    focusRow = (focusRow + 1).coerceAtMost(12)
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            when (focusRow) {
                                1, 2, 12 -> focusCol = 0
                                4 -> customPresetDeleteCol = 0
                                5 -> {
                                    draft = draft.copy(
                                        cardRoundnessDp = (draft.cardRoundnessDp - 2f).coerceIn(8f, 32f)
                                    )
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            when (focusRow) {
                                1, 2, 12 -> focusCol = 1
                                4 -> customPresetDeleteCol = 1
                                5 -> {
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
        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .width(dialogWidth)
                .graphicsLayer { clip = false },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = 580.dp)
                    .border(2.dp, draft.cardBorder, shape),
                shape = shape,
                color = draft.cardBgColor,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    MochibunSectionHeader(
                        title = "Theme Studio",
                        color = draft.accentDeep,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 6.dp)
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
                                "♡ Welcome home ♡",
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
                            "Pastel Presets 🌸",
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

                    // Preset row 1: Sakura Dream & Lavender Mist
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetThemeCard(
                            palette = SakuraDreamPalette,
                            isSelected = draft.name == SakuraDreamPalette.id || draft.name == "original" || draft.name == "strawberry_milk",
                            isFocused = editingColorField == null && focusRow == 1 && focusCol == 0,
                            theme = draft
                        ) {
                            focusRow = 1
                            focusCol = 0
                            draft = SakuraDreamPalette.toLauncherTheme(draft.cardRoundnessDp)
                        }

                        PresetThemeCard(
                            palette = LavenderMistPalette,
                            isSelected = draft.name == LavenderMistPalette.id || draft.name == "lavender_dream",
                            isFocused = editingColorField == null && focusRow == 1 && focusCol == 1,
                            theme = draft
                        ) {
                            focusRow = 1
                            focusCol = 1
                            draft = LavenderMistPalette.toLauncherTheme(draft.cardRoundnessDp)
                        }
                    }

                    // Preset row 2: Minty Matcha & Milky Peach
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetThemeCard(
                            palette = MintyMatchaPalette,
                            isSelected = draft.name == MintyMatchaPalette.id,
                            isFocused = editingColorField == null && focusRow == 2 && focusCol == 0,
                            theme = draft
                        ) {
                            focusRow = 2
                            focusCol = 0
                            draft = MintyMatchaPalette.toLauncherTheme(draft.cardRoundnessDp)
                        }

                        PresetThemeCard(
                            palette = MilkyPeachPalette,
                            isSelected = draft.name == MilkyPeachPalette.id,
                            isFocused = editingColorField == null && focusRow == 2 && focusCol == 1,
                            theme = draft
                        ) {
                            focusRow = 2
                            focusCol = 1
                            draft = MilkyPeachPalette.toLauncherTheme(draft.cardRoundnessDp)
                        }
                    }

                    // External YAML Themes section
                    if (externalThemes.isNotEmpty()) {
                        Text(
                            "External Themes 📂",
                            fontWeight = FontWeight.Bold,
                            color = draft.fgTextColor,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        externalThemes.forEachIndexed { idx, extTheme ->
                            val isRowFocused = editingColorField == null && focusRow == 3 && externalThemeIndex == idx
                            val isSelected = draft.name == extTheme.id

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isRowFocused || isSelected) {
                                            draft.selectedContainerColor
                                        } else {
                                            draft.greetingBgColor.copy(alpha = 0.65f)
                                        }
                                    )
                                    .border(
                                        width = if (isRowFocused) 2.dp else 1.dp,
                                        color = if (isRowFocused) draft.appActionColor else draft.greetingOutlineColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        focusRow = 3
                                        externalThemeIndex = idx
                                        draft = extTheme.theme.copy(cardRoundnessDp = draft.cardRoundnessDp)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isSelected) "✓ ${extTheme.name}" else extTheme.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = draft.fgTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "by ${extTheme.author} • v${extTheme.version}",
                                            fontSize = 10.sp,
                                            color = draft.fgTextColor.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clip(CircleShape)
                                                .background(extTheme.palette.primary)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clip(CircleShape)
                                                .background(extTheme.palette.deepAccent)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(13.dp)
                                                .clip(CircleShape)
                                                .background(extTheme.palette.container)
                                        )
                                    }
                                }
                            }
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
                            val isRowFocused = editingColorField == null && focusRow == 4 && customPresetIndex == idx
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
                                            focusRow = 4
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

                    val sliderFocused = editingColorField == null && focusRow == 5
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
                                focusRow = 5
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
                            isFocused = editingColorField == null && focusRow == 6 + idx,
                            theme = draft
                        ) {
                            focusRow = 6 + idx
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
                        isFocused = editingColorField == null && focusRow == 12 && focusCol == 0,
                        onClick = onDismiss
                    )

                    KawaiiDialogPillButton(
                        text = "Apply ♡",
                        isFocused = editingColorField == null && focusRow == 12 && focusCol == 1,
                        isPrimary = true,
                        onClick = { onApply(draft) }
                    )
                }
            }
        }

        // Unified ribbon bow badge overlapping the upper border rim
        PixelRibbonBow(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-23).dp)
        )
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
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    saveDialogZone = if (newPresetTitle.isNotBlank()) 2 else 1
                                    return@onPreviewKeyEvent true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
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
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            saveDialogZone = 0
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            saveDialogZone = 1
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            saveDialogZone = 2
                            true
                        }
                        else -> false
                    }
                }
        ) {
            val shape = RoundedCornerShape(22.dp)
            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = 300.dp)
                    .border(2.dp, draft.cardBorder, shape),
                shape = shape,
                color = draft.cardBgColor,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MochibunSectionHeader(
                        title = "Save Preset",
                        color = draft.accentDeep,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val softSurfaceColor = draft.greetingBgColor

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
                            onClick = {
                                keyboardController?.hide()
                                showSaveDialog = false
                            }
                        )

                        KawaiiDialogPillButton(
                            text = "Save ♡",
                            isFocused = saveDialogZone == 2,
                            isPrimary = true,
                            onClick = {
                                if (newPresetTitle.isNotBlank()) {
                                    saveCustomPreset(prefs, newPresetTitle.trim(), draft)
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
private fun PresetThemeCard(
    palette: MochibunThemePalette,
    isSelected: Boolean,
    isFocused: Boolean,
    theme: LauncherTheme,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused || isSelected) {
                    theme.selectedContainerColor
                } else {
                    theme.greetingBgColor.copy(alpha = 0.65f)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) theme.appActionColor else theme.greetingOutlineColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isSelected) "✓ ${palette.name}" else palette.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = theme.fgTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(palette.primary)
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(palette.deepAccent)
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(palette.container)
                )
            }
        }
    }
}

@Composable
private fun ColorRow(
    label: String,
    color: Color,
    isFocused: Boolean,
    theme: LauncherTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused) theme.selectedContainerColor else Color.Transparent
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) theme.appActionColor else theme.greetingOutlineColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = theme.fgTextColor
        )

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
                .border(
                    width = 1.dp,
                    color = theme.greetingOutlineColor,
                    shape = RoundedCornerShape(6.dp)
                )
        )
    }
}
