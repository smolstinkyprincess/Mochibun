package com.crinklecodone.mochibun.ui.dialogs

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.data.repository.BundlePreviewData
import com.crinklecodone.mochibun.data.repository.ThemeBundleManager
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modal dialog for exporting a .mochibun Theme & Setup preset bundle.
 * Fully navigable via physical D-pad (Up/Down/Center/Back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBundleDialog(
    initialBundleName: String = "Mochibun Setup",
    onDismiss: () -> Unit,
    onExport: (bundleName: String, includeWallpaper: Boolean, includeCustomCursor: Boolean, includeDisplayName: Boolean) -> Unit
) {
    val theme = LocalLauncherTheme.current
    var bundleName by remember { mutableStateOf(initialBundleName) }
    var includeWallpaper by remember { mutableStateOf(true) }
    var includeCustomCursor by remember { mutableStateOf(true) }
    var includeDisplayName by remember { mutableStateOf(true) }

    val focusRequester = remember { FocusRequester() }
    val nameFieldFocusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val totalItems = 6 // 0: Name, 1: Wallpaper, 2: Cursor, 3: DisplayName, 4: Cancel, 5: Create

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(theme.cardBgColor)
                .border(2.dp, theme.greetingOutlineColor, RoundedCornerShape(22.dp))
                .padding(16.dp)
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
                            when (focusedIndex) {
                                0 -> {
                                    try {
                                        nameFieldFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                                1 -> includeWallpaper = !includeWallpaper
                                2 -> includeCustomCursor = !includeCustomCursor
                                3 -> includeDisplayName = !includeDisplayName
                                4 -> onDismiss()
                                5 -> onExport(bundleName, includeWallpaper, includeCustomCursor, includeDisplayName)
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
                            if (focusedIndex == 5) {
                                focusedIndex = 4
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (focusedIndex == 4) {
                                focusedIndex = 5
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
                    text = "Create a shareable .mochibun archive",
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
                            if (focusedIndex == 0) 2.dp else 1.dp,
                            if (focusedIndex == 0) theme.greetingColor else theme.greetingOutlineColor,
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
                        keyboardActions = KeyboardActions(onDone = { focusedIndex = 1 }),
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
                    isFocused = focusedIndex == 1,
                    onToggle = { includeWallpaper = !includeWallpaper }
                )

                CheckboxRow(
                    label = "Include Custom Cursor",
                    checked = includeCustomCursor,
                    isFocused = focusedIndex == 2,
                    onToggle = { includeCustomCursor = !includeCustomCursor }
                )

                CheckboxRow(
                    label = "Include Display Name",
                    checked = includeDisplayName,
                    isFocused = focusedIndex == 3,
                    onToggle = { includeDisplayName = !includeDisplayName }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (focusedIndex == 4) theme.greetingOutlineColor else Color.Transparent)
                            .border(1.dp, theme.greetingOutlineColor, RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "♥ Cancel (◄) ♥",
                            color = theme.cardTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (focusedIndex == 5) theme.greetingColor else theme.greetingOutlineColor)
                            .border(1.dp, theme.greetingColor, RoundedCornerShape(12.dp))
                            .clickable { onExport(bundleName, includeWallpaper, includeCustomCursor, includeDisplayName) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create ♡",
                            color = if (focusedIndex == 5) Color.White else theme.greetingColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val manifest = previewData.manifest

    val focusRequester = remember { FocusRequester() }
    var focusedButton by remember { mutableIntStateOf(1) } // 0: Cancel, 1: Apply
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
        Box(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(theme.cardBgColor)
                .border(2.dp, theme.greetingOutlineColor, RoundedCornerShape(22.dp))
                .padding(16.dp)
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
                            if (focusedButton == 1) onApply() else onDismiss()
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            focusedButton = 0
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            focusedButton = 1
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusedButton = if (focusedButton == 0) 1 else 0
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

                if (previewData.hasCursorAsset) {
                    Text(
                        text = "🖱️ Custom cursor included",
                        fontSize = 11.sp,
                        color = theme.greetingColor,
                        fontWeight = FontWeight.SemiBold,
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

                Spacer(modifier = Modifier.height(14.dp))

                // Actions with unclipped focus rings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (focusedButton == 0) theme.greetingOutlineColor else Color.Transparent)
                            .border(
                                if (focusedButton == 0) 2.dp else 1.dp,
                                if (focusedButton == 0) theme.greetingColor else theme.greetingOutlineColor,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onDismiss() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "♥ Cancel (◄) ♥",
                            color = theme.cardTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (focusedButton == 1) theme.greetingColor else theme.greetingOutlineColor)
                            .border(
                                if (focusedButton == 1) 2.dp else 1.dp,
                                theme.greetingColor,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onApply() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Apply Setup ♡",
                            color = if (focusedButton == 1) Color.White else theme.greetingColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
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
