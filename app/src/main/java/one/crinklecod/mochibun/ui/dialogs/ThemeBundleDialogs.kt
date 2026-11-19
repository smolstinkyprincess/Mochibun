package one.crinklecod.mochibun.ui.dialogs

import one.crinklecod.mochibun.ui.components.KawaiiClosePill
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.repository.BundlePreviewData
import one.crinklecod.mochibun.data.repository.ThemeBundleManager
import one.crinklecod.mochibun.data.repository.TypographyManager
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the user ticked in [ExportBundleDialog]. */
data class BundleExportSelection(
    val bundleName: String,
    val includeWallpaper: Boolean,
    val includeCustomCursor: Boolean,
    val includeCustomMascot: Boolean,
    val includeCustomCardBorder: Boolean,
    val includeDisplayName: Boolean,
    val includeFont: Boolean = false,
    val includeSoundPack: Boolean = false
)

/** Focus order of [ExportBundleDialog]; font / sound rows exist only when there is something to pack. */
internal fun exportDialogRows(hasCustomFont: Boolean, hasCustomSoundPack: Boolean): List<String> = buildList {
    add("name")
    add("wallpaper")
    add("cursor")
    add("mascot")
    add("card_border")
    if (hasCustomFont) add("font")
    if (hasCustomSoundPack) add("sounds")
    add("display_name")
    add("cancel")
    add("create")
}

/**
 * Modal dialog for exporting a .mochibun Theme & Setup preset bundle.
 * Fully navigable via physical D-pad (Up/Down/Center/Back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBundleDialog(
    initialBundleName: String = "Mochibun Setup",
    onDismiss: () -> Unit,
    hasCustomMascot: Boolean = false,
    hasCustomCardBorder: Boolean = false,
    hasCustomFont: Boolean = false,
    hasCustomSoundPack: Boolean = false,
    onExport: (BundleExportSelection) -> Unit
) {
    val theme = LocalLauncherTheme.current
    var bundleName by remember { mutableStateOf(initialBundleName) }
    var includeWallpaper by remember { mutableStateOf(true) }
    var includeCustomCursor by remember { mutableStateOf(true) }
    // Checked by default only when a custom mascot atlas / card border / font / sound pack is active
    var includeCustomMascot by remember { mutableStateOf(hasCustomMascot) }
    var includeCustomCardBorder by remember { mutableStateOf(hasCustomCardBorder) }
    var includeFont by remember { mutableStateOf(hasCustomFont) }
    var includeSoundPack by remember { mutableStateOf(hasCustomSoundPack) }
    var includeDisplayName by remember { mutableStateOf(true) }

    val focusRequester = remember { FocusRequester() }
    val nameFieldFocusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val rows = remember(hasCustomFont, hasCustomSoundPack) {
        exportDialogRows(hasCustomFont, hasCustomSoundPack)
    }
    val totalItems = rows.size
    val focusedRow = rows.getOrNull(focusedIndex)
    val cancelIndex = rows.indexOf("cancel")
    val createIndex = rows.indexOf("create")

    fun selection() = BundleExportSelection(
        bundleName = bundleName,
        includeWallpaper = includeWallpaper,
        includeCustomCursor = includeCustomCursor,
        includeCustomMascot = includeCustomMascot,
        includeCustomCardBorder = includeCustomCardBorder,
        includeDisplayName = includeDisplayName,
        includeFont = hasCustomFont && includeFont,
        includeSoundPack = hasCustomSoundPack && includeSoundPack
    )

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        ResizeWithKeyboard()
        KawaiiDialogSurface(
            cornerRadius = 22.dp,
            containerColor = theme.cardBgColor,
            borderColor = theme.greetingOutlineColor,
            borderWidth = 2.dp,
            contentPadding = 16.dp,
            modifier = Modifier
                .width(280.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (isBackDismissKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyUp) onDismiss()
                        return@onPreviewKeyEvent true
                    }
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            when (focusedRow) {
                                "name" -> {
                                    try {
                                        nameFieldFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                                "wallpaper" -> includeWallpaper = !includeWallpaper
                                "cursor" -> includeCustomCursor = !includeCustomCursor
                                "mascot" -> includeCustomMascot = !includeCustomMascot
                                "card_border" -> includeCustomCardBorder = !includeCustomCardBorder
                                "font" -> includeFont = !includeFont
                                "sounds" -> includeSoundPack = !includeSoundPack
                                "display_name" -> includeDisplayName = !includeDisplayName
                                "cancel" -> onDismiss()
                                "create" -> onExport(selection())
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            focusedIndex = if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusedIndex = if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (focusedIndex == createIndex) {
                                focusedIndex = cancelIndex
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (focusedIndex == cancelIndex) {
                                focusedIndex = createIndex
                                true
                            } else false
                        }
                        else -> false
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "— ♡ Export Theme Bundle ♡ —",
                    color = theme.greetingColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Create a shareable .mochibundle archive",
                    color = theme.cardTextColor.copy(alpha = 0.7f),
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 0. Bundle Name Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.greetingBgColor)
                        .border(
                            if (focusedRow == "name") 2.dp else 1.dp,
                            if (focusedRow == "name") theme.greetingColor else theme.greetingOutlineColor,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    BasicTextField(
                        value = bundleName,
                        onValueChange = { bundleName = it },
                        textStyle = TextStyle(
                            color = theme.fgTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(theme.greetingColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusedIndex = rows.indexOf("wallpaper") }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFieldFocusRequester)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Checkboxes
                CheckboxRow(
                    label = "Include Wallpaper",
                    checked = includeWallpaper,
                    isFocused = focusedRow == "wallpaper",
                    onToggle = { includeWallpaper = !includeWallpaper }
                )

                CheckboxRow(
                    label = "Include Custom Cursor",
                    checked = includeCustomCursor,
                    isFocused = focusedRow == "cursor",
                    onToggle = { includeCustomCursor = !includeCustomCursor }
                )

                CheckboxRow(
                    label = "Include Custom Mascot",
                    checked = includeCustomMascot,
                    isFocused = focusedRow == "mascot",
                    onToggle = { includeCustomMascot = !includeCustomMascot }
                )

                CheckboxRow(
                    label = "Include Custom Card Border",
                    checked = includeCustomCardBorder,
                    isFocused = focusedRow == "card_border",
                    onToggle = { includeCustomCardBorder = !includeCustomCardBorder }
                )

                if (hasCustomFont) {
                    CheckboxRow(
                        label = "Include Custom Font",
                        checked = includeFont,
                        isFocused = focusedRow == "font",
                        onToggle = { includeFont = !includeFont }
                    )
                }

                if (hasCustomSoundPack) {
                    CheckboxRow(
                        label = "Include Custom Sound Pack",
                        checked = includeSoundPack,
                        isFocused = focusedRow == "sounds",
                        onToggle = { includeSoundPack = !includeSoundPack }
                    )
                }

                CheckboxRow(
                    label = "Include Display Name",
                    checked = includeDisplayName,
                    isFocused = focusedRow == "display_name",
                    onToggle = { includeDisplayName = !includeDisplayName }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KawaiiClosePill(
                        label = "Cancel",
                        isFocused = focusedRow == "cancel",
                        onClick = { onDismiss() },
                        modifier = Modifier.weight(1f)
                    )

                    KawaiiDialogPillButton(
                        text = "Create ♡",
                        isFocused = focusedRow == "create",
                        isPrimary = true,
                        onClick = { onExport(selection()) },
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}

/**
 * Inspection & Confirmation Dialog for incoming .mochibun bundles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetPreviewDialog(
    previewData: BundlePreviewData,
    onApply: (clearUnbundledAssets: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val manifest = previewData.manifest
    val hasUnbundledAssets = previewData.wallpaperPreview == null ||
        !previewData.hasCursorAsset ||
        !previewData.hasMascotAsset
    var clearUnbundledAssets by remember(manifest) { mutableStateOf(true) }

    val focusRequester = remember { FocusRequester() }
    // 0: Reset unbundled checkbox (when shown), 1: Cancel, 2: Apply
    var focusedControl by remember { mutableIntStateOf(2) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val formattedDate = remember(manifest.createdAtEpochSec) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(manifest.createdAtEpochSec * 1000L))
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        ResizeWithKeyboard()
        KawaiiDialogSurface(
            cornerRadius = 22.dp,
            containerColor = theme.cardBgColor,
            borderColor = theme.greetingOutlineColor,
            borderWidth = 2.dp,
            contentPadding = 16.dp,
            modifier = Modifier
                .width(280.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (isBackDismissKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyUp) onDismiss()
                        return@onPreviewKeyEvent true
                    }
                    if (isCenterActivateKey(native.keyCode)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            when (focusedControl) {
                                0 -> if (hasUnbundledAssets) clearUnbundledAssets = !clearUnbundledAssets
                                1 -> onDismiss()
                                else -> onApply(hasUnbundledAssets && clearUnbundledAssets)
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            focusedControl = 1
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            focusedControl = 2
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            focusedControl = when {
                                hasUnbundledAssets && focusedControl != 0 -> 0
                                focusedControl == 2 -> 1
                                else -> 2
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusedControl = when {
                                focusedControl == 0 -> 2
                                focusedControl == 1 -> 2
                                hasUnbundledAssets -> 0
                                else -> 1
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "— ♡ Theme Bundle Preview ♡ —",
                    color = theme.greetingColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = manifest.bundleName,
                    color = theme.fgTextColor,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Text(
                    text = "by ${manifest.author} • $formattedDate",
                    color = theme.cardTextColor.copy(alpha = 0.7f),
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Palette 3-dot color swatch
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.greetingBgColor)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val p = manifest.palette
                    val c1 = Color(ThemeBundleManager.hexToColorInt(p.bgColorHex, ThemeBundleManager.DEFAULT_FALLBACK_BG_INT))
                    val c2 = Color(ThemeBundleManager.hexToColorInt(p.cardColorHex, ThemeBundleManager.DEFAULT_FALLBACK_CARD_INT))
                    val c3 = Color(ThemeBundleManager.hexToColorInt(p.greetingColorHex, ThemeBundleManager.DEFAULT_FALLBACK_GREETING_INT))

                    ColorDot(c1)
                    ColorDot(c2)
                    ColorDot(c3)

                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Preset: ${p.activePresetId}",
                        color = theme.cardTextColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Wallpaper & Asset previews
                if (previewData.wallpaperPreview != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.greetingBgColor.copy(alpha = 0.6f))
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = previewData.wallpaperPreview.asImageBitmap(),
                            contentDescription = null,
                            filterQuality = FilterQuality.None,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 36.dp, height = 48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, theme.greetingOutlineColor, RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Custom Wallpaper", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.fgTextColor)
                            Text("Included in bundle", fontSize = 10.sp, color = theme.cardTextColor.copy(alpha = 0.7f))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (previewData.hasMascotAsset && previewData.mascotPreview != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.greetingBgColor.copy(alpha = 0.6f))
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = previewData.mascotPreview.asImageBitmap(),
                            contentDescription = null,
                            filterQuality = FilterQuality.None,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, theme.greetingOutlineColor, RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = manifest.mascot?.name?.ifBlank { "Custom Mascot" } ?: "Custom Mascot",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.fgTextColor
                            )
                            Text(
                                text = previewMascotBadgeText(previewData),
                                fontSize = 10.sp,
                                color = theme.greetingColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else if (previewData.hasMascotAsset) {
                    Text(
                        text = previewMascotBadgeText(previewData),
                        fontSize = 11.sp,
                        color = theme.greetingColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                if (previewData.hasCardBorderAsset && previewData.cardBorderPreview != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.greetingBgColor.copy(alpha = 0.6f))
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = previewData.cardBorderPreview.asImageBitmap(),
                            contentDescription = null,
                            filterQuality = FilterQuality.None,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, theme.greetingOutlineColor, RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("9-Slice Card Border", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = theme.fgTextColor)
                            Text("Included in bundle", fontSize = 10.sp, color = theme.greetingColor)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    previewCardBorderBadgeText(previewData)?.let { borderBadge ->
                        Text(
                            text = borderBadge,
                            fontSize = 11.sp,
                            color = theme.greetingColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                if (previewData.hasCursorAsset) {
                    Text(
                        text = "🖱️ Custom cursor included",
                        fontSize = 11.sp,
                        color = theme.greetingColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                previewFontBadgeText(previewData)?.let { fontBadge ->
                    Text(
                        text = fontBadge,
                        fontSize = 11.sp,
                        color = theme.greetingColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                previewSoundPackBadgeText(previewData)?.let { soundBadge ->
                    Text(
                        text = soundBadge,
                        fontSize = 11.sp,
                        color = theme.greetingColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                manifest.palette.fontFamilyId?.takeIf {
                    it.isNotBlank() && it != "default" && it != TypographyManager.BUNDLE_FONT_ID
                }?.let { fontId ->
                    Text(
                        text = "🔤 Font: $fontId",
                        fontSize = 10.5.sp,
                        color = theme.cardTextColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                if (manifest.greeting.userName.isNotBlank() && manifest.greeting.userName != "Mochi User") {
                    Text(
                        text = "Greeting: ♡ ${manifest.greeting.userName} ♡",
                        fontSize = 11.sp,
                        color = theme.cardTextColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                if (hasUnbundledAssets) {
                    Spacer(modifier = Modifier.height(6.dp))
                    CheckboxRow(
                        label = "Reset unbundled wallpaper / cursor / mascot",
                        checked = clearUnbundledAssets,
                        isFocused = focusedControl == 0,
                        onToggle = { clearUnbundledAssets = !clearUnbundledAssets }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions with unclipped focus rings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KawaiiClosePill(
                        label = "Cancel",
                        isFocused = focusedControl == 1,
                        onClick = { onDismiss() },
                        modifier = Modifier.weight(1f)
                    )

                    KawaiiDialogPillButton(
                        text = "Apply Setup ♡",
                        isFocused = focusedControl == 2,
                        isPrimary = true,
                        onClick = { onApply(hasUnbundledAssets && clearUnbundledAssets) },
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}

internal fun previewMascotBadgeText(preview: BundlePreviewData): String =
    if (preview.hasCustomMascotDialogue) {
        "🐰 Custom mascot + dialogue included"
    } else {
        "🐰 Custom mascot included"
    }

/** "🔤 Custom Font" badge when the bundle carries a loadable-looking `font.ttf` / `font.otf`. */
internal fun previewFontBadgeText(preview: BundlePreviewData): String? {
    if (!preview.hasFontAsset) return null
    val name = preview.manifest.fontDisplayName?.trim()?.takeIf { it.isNotEmpty() }
    return if (name != null) "🔤 Custom Font: $name" else "🔤 Custom Font"
}

/** "🎵 Custom Sound Pack" badge with the number of bundled cues. */
internal fun previewSoundPackBadgeText(preview: BundlePreviewData): String? {
    if (!preview.hasSoundPackAsset) return null
    val name = preview.manifest.soundPackName?.trim()?.takeIf { it.isNotEmpty() }
    val cues = preview.soundCueCount
    val cueText = "$cues/${SoundEffect.entries.size} sound${if (cues == 1) "" else "s"}"
    return if (name != null) "🎵 Custom Sound Pack: $name ($cueText)" else "🎵 Custom Sound Pack ($cueText)"
}

internal fun previewCardBorderBadgeText(preview: BundlePreviewData): String? = when {
    preview.hasCustomCardBorder -> "🎀 Custom card border included"
    preview.borderStyle != CardBorderStyle.SOLID -> "🎀 Border style: ${preview.borderStyle.label}"
    else -> null
}

@Composable
private fun ColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
    )
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    isFocused: Boolean,
    onToggle: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) theme.greetingOutlineColor.copy(alpha = 0.4f) else Color.Transparent)
            .clickable { onToggle() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) theme.greetingColor else Color.Transparent)
                .border(
                    if (isFocused) 2.dp else 1.dp,
                    if (isFocused) theme.greetingColor else theme.greetingOutlineColor,
                    RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = if (isFocused) theme.greetingColor else theme.cardTextColor,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}
