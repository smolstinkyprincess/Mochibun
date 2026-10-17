package com.tnyemii.mochibun

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tnyemii.mochibun.ui.theme.destructiveBgColor
import com.tnyemii.mochibun.ui.theme.destructiveColor
import com.tnyemii.mochibun.ui.theme.softSurfaceColor
import java.util.Locale
import kotlin.math.round

/**
 * Mochibun Kawaii Configuration Screen for the system-wide Virtual Mouse Service.
 * Fully optimized for AT-M120 physical D-pad navigation and 480x800 resolution.
 */
@Composable
fun CursorSettingsScreen(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Live preference states
    var toggleKeyCode by remember { mutableIntStateOf(CursorPreferences.getToggleKeyCode(context)) }
    var moveSpeed by remember { mutableFloatStateOf(CursorPreferences.getMoveSpeed(context)) }
    var acceleration by remember { mutableFloatStateOf(CursorPreferences.getAcceleration(context)) }
    var clickMethod by remember { mutableStateOf(CursorPreferences.getClickMethod(context)) }
    var hotspotAlignment by remember { mutableStateOf(CursorPreferences.getHotspotAlignment(context)) }
    var selectedUri by remember { mutableStateOf(CursorPreferences.getCustomCursorUri(context)) }
    var cursorSizeDp by remember { mutableIntStateOf(CursorPreferences.getCursorSizeDp(context)) }

    var isServiceActive by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Function to check if our AccessibilityService is enabled in Android settings
    fun checkServiceStatus() {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isEnabledInSettings = enabledServices?.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name.contains("VirtualCursorService")
        } == true
        isServiceActive = isEnabledInSettings || VirtualCursorService.isServiceRunning
    }

    // Refresh status on lifecycle resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkServiceStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        checkServiceStatus()
    }

    // Custom asset picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            CursorPreferences.setCustomCursorUri(context, uri)
            selectedUri = uri
            VirtualCursorService.reloadActiveConfig(context)
            statusMessage = "Custom cursor loaded! (*^‿^*)"
        }
    }

    // Focusable item index for physical D-pad navigation:
    // 0: Accessibility Status Banner ("Settings →")
    // 1: Pick Custom GIF / PNG Button
    // 2: Reset to Default (cursor.gif) Button
    // 3: Base Speed Row Stepper
    // 4: Acceleration Rate Row Stepper
    // 5: Cursor Size Row Stepper
    // 6: Click Method Selector
    // 7: Hotspot Selector
    // 8: Toggle Key Selector
    // 9: Test Mouse / Toggle Now Button
    // 10: Close Settings Button (if onClose != null)
    val maxFocusIndex = if (onClose != null) 10 else 9
    var focusedIndex by remember { mutableIntStateOf(0) }

    // Auto-scroll list when focus index changes so the focused item is always clearly in view
    LaunchedEffect(focusedIndex) {
        val targetScrollIndex = when (focusedIndex) {
            0 -> 0 // Status Banner (Item 0/1)
            1, 2 -> 2 // Cursor Asset Card
            3, 4, 5 -> 3 // Movement & Speed Card
            6, 7 -> 4 // Click & Hotspot Card
            8, 9 -> 5 // Hardware Controls Card
            10 -> 6 // Close Settings Button
            else -> 0
        }
        try {
            listState.animateScrollToItem(
                targetScrollIndex.coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            )
        } catch (_: Exception) {}
    }

    requestFocusSafely(focusRequester)

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        onClose?.invoke()
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when {
                    native.keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        if (focusedIndex > 0) {
                            focusedIndex--
                        }
                        true
                    }
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (focusedIndex < maxFocusIndex) {
                            focusedIndex++
                        }
                        true
                    }
                    native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when (focusedIndex) {
                            3 -> { // Base speed down (1..50 px)
                                val newSpeed = (moveSpeed - 2f).coerceIn(1f, 50f)
                                moveSpeed = newSpeed
                                CursorPreferences.setMoveSpeed(context, newSpeed)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            4 -> { // Acceleration down (1.00x..2.00x)
                                val newAcc = (round((acceleration - 0.02f) * 100f) / 100f).coerceIn(1.00f, 2.00f)
                                acceleration = newAcc
                                CursorPreferences.setAcceleration(context, newAcc)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            5 -> { // Cursor size down (16..64 dp)
                                val newSize = (cursorSizeDp - 4).coerceIn(16, 64)
                                cursorSizeDp = newSize
                                CursorPreferences.setCursorSizeDp(context, newSize)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            6 -> { // Click method: Accessibility Gesture
                                clickMethod = ClickMethod.ACCESSIBILITY_GESTURE
                                CursorPreferences.setClickMethod(context, clickMethod)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            7 -> { // Hotspot: Top-Left Tip
                                hotspotAlignment = HotspotAlignment.TOP_LEFT
                                CursorPreferences.setHotspotAlignment(context, hotspotAlignment)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            8 -> { // Toggle keycode cycle backwards
                                toggleKeyCode = cycleToggleKey(toggleKeyCode, forward = false)
                                CursorPreferences.setToggleKeyCode(context, toggleKeyCode)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                        }
                        true
                    }
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when (focusedIndex) {
                            3 -> { // Base speed up (1..50 px)
                                val newSpeed = (moveSpeed + 2f).coerceIn(1f, 50f)
                                moveSpeed = newSpeed
                                CursorPreferences.setMoveSpeed(context, newSpeed)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            4 -> { // Acceleration up (1.00x..2.00x)
                                val newAcc = (round((acceleration + 0.02f) * 100f) / 100f).coerceIn(1.00f, 2.00f)
                                acceleration = newAcc
                                CursorPreferences.setAcceleration(context, newAcc)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            5 -> { // Cursor size up (16..64 dp)
                                val newSize = (cursorSizeDp + 4).coerceIn(16, 64)
                                cursorSizeDp = newSize
                                CursorPreferences.setCursorSizeDp(context, newSize)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            6 -> { // Click method: Root Shell
                                clickMethod = ClickMethod.ROOT_INPUT_TAP
                                CursorPreferences.setClickMethod(context, clickMethod)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            7 -> { // Hotspot: Center
                                hotspotAlignment = HotspotAlignment.CENTER
                                CursorPreferences.setHotspotAlignment(context, hotspotAlignment)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            8 -> { // Toggle keycode cycle forwards
                                toggleKeyCode = cycleToggleKey(toggleKeyCode, forward = true)
                                CursorPreferences.setToggleKeyCode(context, toggleKeyCode)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                        }
                        true
                    }
                    isCenterActivateKey(native.keyCode) -> {
                        when (focusedIndex) {
                            0 -> { // Open Accessibility settings
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (e: Exception) {
                                    statusMessage = "Cannot open settings: ${e.message}"
                                }
                            }
                            1 -> { // Pick file
                                try {
                                    filePickerLauncher.launch(arrayOf("image/gif", "image/png", "image/*"))
                                } catch (e: Exception) {
                                    statusMessage = "Picker error: ${e.message}"
                                }
                            }
                            2 -> { // Reset cursor
                                CursorPreferences.setCustomCursorUri(context, null)
                                selectedUri = null
                                VirtualCursorService.reloadActiveConfig(context)
                                statusMessage = "Reset to default cursor.gif (*^‿^*)"
                            }
                            3 -> { // Increment speed on Center click
                                val newSpeed = if (moveSpeed >= 50f) 1f else (moveSpeed + 2f).coerceIn(1f, 50f)
                                moveSpeed = newSpeed
                                CursorPreferences.setMoveSpeed(context, newSpeed)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            4 -> { // Increment acceleration on Center click
                                val newAcc = if (acceleration >= 2.00f) 1.00f else (round((acceleration + 0.02f) * 100f) / 100f).coerceIn(1.00f, 2.00f)
                                acceleration = newAcc
                                CursorPreferences.setAcceleration(context, newAcc)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            5 -> { // Increment size on Center click
                                val newSize = if (cursorSizeDp >= 64) 16 else (cursorSizeDp + 4).coerceIn(16, 64)
                                cursorSizeDp = newSize
                                CursorPreferences.setCursorSizeDp(context, newSize)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            6 -> { // Toggle click method
                                val nextMethod = if (clickMethod == ClickMethod.ACCESSIBILITY_GESTURE)
                                    ClickMethod.ROOT_INPUT_TAP else ClickMethod.ACCESSIBILITY_GESTURE
                                clickMethod = nextMethod
                                CursorPreferences.setClickMethod(context, nextMethod)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            7 -> { // Toggle hotspot
                                val nextAlignment = if (hotspotAlignment == HotspotAlignment.TOP_LEFT)
                                    HotspotAlignment.CENTER else HotspotAlignment.TOP_LEFT
                                hotspotAlignment = nextAlignment
                                CursorPreferences.setHotspotAlignment(context, nextAlignment)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            8 -> { // Cycle keycode
                                toggleKeyCode = cycleToggleKey(toggleKeyCode, forward = true)
                                CursorPreferences.setToggleKeyCode(context, toggleKeyCode)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                            9 -> { // Toggle cursor now
                                if (VirtualCursorService.isServiceRunning) {
                                    VirtualCursorService.toggleCursor()
                                    statusMessage = if (VirtualCursorService.isCursorVisible)
                                        "Mouse pointer is visible on screen! ✨"
                                    else
                                        "Mouse pointer hidden 🌸"
                                } else {
                                    statusMessage = "Please enable VirtualCursorService in Accessibility settings first!"
                                }
                            }
                            10 -> { // Close Settings
                                onClose?.invoke()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Item 0: Header & optional status message
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "✨ Virtual Mouse",
                                color = theme.greetingColor,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "C9-style pointer for AT-M120",
                                color = theme.cardTextColor.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        // Status chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isServiceActive) Color(0xFFE8F5E9) else destructiveBgColor)
                                .border(
                                    1.dp,
                                    if (isServiceActive) Color(0xFF4CAF50) else destructiveColor,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isServiceActive) "● Active" else "○ Disabled",
                                color = if (isServiceActive) Color(0xFF2E7D32) else destructiveColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Status message alert if present
                    if (statusMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.selectedContainerColor)
                                .border(1.dp, theme.appActionColor, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = statusMessage!!,
                                color = theme.fgTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Item 1: Accessibility Status Banner (Focus Index 0)
            item {
                AccessibilityBanner(
                    isServiceActive = isServiceActive,
                    isFocused = focusedIndex == 0,
                    onClick = {
                        focusedIndex = 0
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            statusMessage = "Cannot open settings: ${e.message}"
                        }
                    }
                )
            }

            // Item 2: Live Cursor Asset Card (Focus Indices 1, 2)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBgColor),
                    border = BorderStroke(1.dp, theme.greetingOutlineColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🎨 Cursor Pointer Asset",
                                color = theme.greetingColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Preview badge
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(softSurfaceColor)
                                    .border(1.dp, theme.appActionColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedUri != null) "GIF" else "↖",
                                    color = theme.appActionColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = if (selectedUri != null) "Using custom file: ${selectedUri?.lastPathSegment ?: "selected"}"
                            else "Using default cursor.gif (drawable-nodpi)",
                            color = theme.cardTextColor.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        )

                        Spacer(Modifier.height(2.dp))

                        // Button 1: Pick GIF/PNG Button
                        MochibunActionButton(
                            text = "📁 Pick Custom GIF / PNG",
                            isFocused = focusedIndex == 1,
                            onClick = {
                                focusedIndex = 1
                                try {
                                    filePickerLauncher.launch(arrayOf("image/gif", "image/png", "image/*"))
                                } catch (e: Exception) {
                                    statusMessage = "Picker error: ${e.message}"
                                }
                            }
                        )

                        // Button 2: Reset to Built-in
                        MochibunActionButton(
                            text = "↩ Reset to Default (cursor.gif)",
                            isFocused = focusedIndex == 2,
                            onClick = {
                                focusedIndex = 2
                                CursorPreferences.setCustomCursorUri(context, null)
                                selectedUri = null
                                VirtualCursorService.reloadActiveConfig(context)
                                statusMessage = "Reset to default cursor.gif (*^‿^*)"
                            }
                        )
                    }
                }
            }

            // Item 3: Movement & Speed Card (Focus Indices 3, 4, 5)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBgColor),
                    border = BorderStroke(1.dp, theme.greetingOutlineColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🚀 Movement & Speed",
                            color = theme.greetingColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Stepper 3: Base Speed (1..50 px)
                        KawaiiStepperRow(
                            label = "Base Speed (px):",
                            valueText = "${moveSpeed.toInt()} px",
                            isFocused = focusedIndex == 3,
                            onDecrement = {
                                val newSpeed = (moveSpeed - 2f).coerceIn(1f, 50f)
                                moveSpeed = newSpeed
                                CursorPreferences.setMoveSpeed(context, newSpeed)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 3
                            },
                            onIncrement = {
                                val newSpeed = (moveSpeed + 2f).coerceIn(1f, 50f)
                                moveSpeed = newSpeed
                                CursorPreferences.setMoveSpeed(context, newSpeed)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 3
                            },
                            onClick = { focusedIndex = 3 }
                        )

                        // Stepper 4: Acceleration Rate (1.00x..2.00x)
                        KawaiiStepperRow(
                            label = "Acceleration Rate:",
                            valueText = String.format(Locale.US, "%.2fx", acceleration),
                            isFocused = focusedIndex == 4,
                            onDecrement = {
                                val newAcc = (round((acceleration - 0.02f) * 100f) / 100f).coerceIn(1.00f, 2.00f)
                                acceleration = newAcc
                                CursorPreferences.setAcceleration(context, newAcc)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 4
                            },
                            onIncrement = {
                                val newAcc = (round((acceleration + 0.02f) * 100f) / 100f).coerceIn(1.00f, 2.00f)
                                acceleration = newAcc
                                CursorPreferences.setAcceleration(context, newAcc)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 4
                            },
                            onClick = { focusedIndex = 4 }
                        )

                        // Stepper 5: Cursor Size (16..64 dp)
                        KawaiiStepperRow(
                            label = "Cursor Size:",
                            valueText = "$cursorSizeDp dp",
                            isFocused = focusedIndex == 5,
                            onDecrement = {
                                val newSize = (cursorSizeDp - 4).coerceIn(16, 64)
                                cursorSizeDp = newSize
                                CursorPreferences.setCursorSizeDp(context, newSize)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 5
                            },
                            onIncrement = {
                                val newSize = (cursorSizeDp + 4).coerceIn(16, 64)
                                cursorSizeDp = newSize
                                CursorPreferences.setCursorSizeDp(context, newSize)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 5
                            },
                            onClick = { focusedIndex = 5 }
                        )
                    }
                }
            }

            // Item 4: Click & Hotspot Card (Focus Indices 6, 7)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBgColor),
                    border = BorderStroke(1.dp, theme.greetingOutlineColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "⚡ Click & Hotspot",
                            color = theme.greetingColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Selector 6: Click Method
                        KawaiiSelectorRow(
                            label = "Click Method:",
                            valueText = if (clickMethod == ClickMethod.ACCESSIBILITY_GESTURE) "A11y Gesture" else "Root Shell",
                            description = clickMethod.description,
                            isFocused = focusedIndex == 6,
                            onPrevious = {
                                clickMethod = ClickMethod.ACCESSIBILITY_GESTURE
                                CursorPreferences.setClickMethod(context, clickMethod)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 6
                            },
                            onNext = {
                                clickMethod = ClickMethod.ROOT_INPUT_TAP
                                CursorPreferences.setClickMethod(context, clickMethod)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 6
                            },
                            onClick = {
                                focusedIndex = 6
                                val nextMethod = if (clickMethod == ClickMethod.ACCESSIBILITY_GESTURE)
                                    ClickMethod.ROOT_INPUT_TAP else ClickMethod.ACCESSIBILITY_GESTURE
                                clickMethod = nextMethod
                                CursorPreferences.setClickMethod(context, nextMethod)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                        )

                        // Selector 7: Hotspot Alignment
                        KawaiiSelectorRow(
                            label = "Hotspot Point:",
                            valueText = hotspotAlignment.label,
                            description = hotspotAlignment.description,
                            isFocused = focusedIndex == 7,
                            onPrevious = {
                                hotspotAlignment = HotspotAlignment.TOP_LEFT
                                CursorPreferences.setHotspotAlignment(context, hotspotAlignment)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 7
                            },
                            onNext = {
                                hotspotAlignment = HotspotAlignment.CENTER
                                CursorPreferences.setHotspotAlignment(context, hotspotAlignment)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 7
                            },
                            onClick = {
                                focusedIndex = 7
                                val nextAlignment = if (hotspotAlignment == HotspotAlignment.TOP_LEFT)
                                    HotspotAlignment.CENTER else HotspotAlignment.TOP_LEFT
                                hotspotAlignment = nextAlignment
                                CursorPreferences.setHotspotAlignment(context, nextAlignment)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                        )
                    }
                }
            }

            // Item 5: Keycode & Testing Card (Focus Indices 8, 9)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBgColor),
                    border = BorderStroke(1.dp, theme.greetingOutlineColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎮 Hardware Controls",
                            color = theme.greetingColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Selector 8: Toggle Keycode
                        KawaiiSelectorRow(
                            label = "Toggle Key:",
                            valueText = getKeyLabel(toggleKeyCode),
                            description = "Hardware key that toggles virtual pointer mode",
                            isFocused = focusedIndex == 8,
                            onPrevious = {
                                toggleKeyCode = cycleToggleKey(toggleKeyCode, forward = false)
                                CursorPreferences.setToggleKeyCode(context, toggleKeyCode)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 8
                            },
                            onNext = {
                                toggleKeyCode = cycleToggleKey(toggleKeyCode, forward = true)
                                CursorPreferences.setToggleKeyCode(context, toggleKeyCode)
                                VirtualCursorService.reloadActiveConfig(context)
                                focusedIndex = 8
                            },
                            onClick = {
                                focusedIndex = 8
                                toggleKeyCode = cycleToggleKey(toggleKeyCode, forward = true)
                                CursorPreferences.setToggleKeyCode(context, toggleKeyCode)
                                VirtualCursorService.reloadActiveConfig(context)
                            }
                        )

                        // Button 9: Test Mouse / Toggle Now
                        MochibunActionButton(
                            text = if (VirtualCursorService.isCursorVisible) "🌸 Hide Cursor Now" else "✨ Show Cursor Now",
                            isFocused = focusedIndex == 9,
                            onClick = {
                                focusedIndex = 9
                                if (VirtualCursorService.isServiceRunning) {
                                    VirtualCursorService.toggleCursor()
                                    statusMessage = if (VirtualCursorService.isCursorVisible)
                                        "Mouse pointer is visible on screen! ✨"
                                    else
                                        "Mouse pointer hidden 🌸"
                                } else {
                                    statusMessage = "Please enable VirtualCursorService in Accessibility settings first!"
                                }
                            }
                        )
                    }
                }
            }

            // Item 6: Optional Close Button (Focus Index 10)
            if (onClose != null) {
                item {
                    MochibunActionButton(
                        text = "Close Settings",
                        isFocused = focusedIndex == 10,
                        onClick = {
                            focusedIndex = 10
                            onClose()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Mochibun dialog wrapper for [CursorSettingsScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CursorSettingsDialog(
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.94f)
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.dp)
        Surface(
            shape = shape,
            color = theme.bgColor,
            border = BorderStroke(2.dp, theme.appActionColor),
            modifier = Modifier.fillMaxSize()
        ) {
            CursorSettingsScreen(
                modifier = Modifier.fillMaxSize(),
                onClose = onDismiss
            )
        }
    }
}

/**
 * Clean Accessibility Status Banner that prevents text collisions and ensures clean wrapping.
 */
@Composable
private fun AccessibilityBanner(
    isServiceActive: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(12.dp)

    val borderColor = when {
        isFocused -> theme.appActionColor
        isServiceActive -> Color(0xFF81C784)
        else -> destructiveColor
    }

    val bgColor = when {
        isFocused -> theme.selectedContainerColor
        isServiceActive -> Color(0xFFF1F8E9)
        else -> destructiveBgColor
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor, shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side (weight 1f)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isServiceActive) "⚙️ Accessibility Service Enabled" else "⚠️ Tap to Enable in Accessibility",
                    color = if (isServiceActive) Color(0xFF2E7D32) else destructiveColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Android requires VirtualCursorService enabled to read physical keys & dispatch clicks.",
                    color = theme.cardTextColor.copy(alpha = 0.85f),
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp
                )
            }

            // Right side: Settings -> button/pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isFocused) theme.appActionColor else theme.selectedContainerColor
                    )
                    .border(
                        1.dp,
                        theme.appActionColor,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Settings →",
                    color = if (isFocused) Color.White else theme.appActionColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Stepper Row with physical D-pad LEFT/RIGHT adjustment and touchable arrow buttons.
 */
@Composable
private fun KawaiiStepperRow(
    label: String,
    valueText: String,
    isFocused: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isFocused) theme.selectedContainerColor else softSurfaceColor.copy(alpha = 0.6f),
                shape
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) theme.appActionColor else theme.greetingOutlineColor.copy(alpha = 0.35f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = theme.fgTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Decrement button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isFocused) theme.bgColor else Color.Transparent)
                        .clickable(onClick = onDecrement),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◀",
                        color = theme.appActionColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Current value
                Text(
                    text = valueText,
                    color = theme.appActionColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                // Increment button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isFocused) theme.bgColor else Color.Transparent)
                        .clickable(onClick = onIncrement),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶",
                        color = theme.appActionColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Selector row for toggling options (Click Method, Hotspot, Keys) with D-pad & touch.
 */
@Composable
private fun KawaiiSelectorRow(
    label: String,
    valueText: String,
    description: String? = null,
    isFocused: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isFocused) theme.selectedContainerColor else softSurfaceColor.copy(alpha = 0.6f),
                shape
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) theme.appActionColor else theme.greetingOutlineColor.copy(alpha = 0.35f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = theme.fgTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) theme.bgColor else Color.Transparent)
                            .clickable(onClick = onPrevious),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "◀",
                            color = theme.appActionColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = valueText,
                        color = theme.appActionColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) theme.bgColor else Color.Transparent)
                            .clickable(onClick = onNext),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            color = theme.appActionColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!description.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    color = theme.cardTextColor.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                )
            }
        }
    }
}

/**
 * Standard Mochibun Action Button with unclipped Kawaii focus rings.
 */
@Composable
private fun MochibunActionButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isFocused) theme.appActionColor else theme.bgColor,
                shape
            )
            .border(
                width = if (isFocused) 2.dp else 1.5.dp,
                color = theme.appActionColor,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.White else theme.appActionColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun cycleToggleKey(current: Int, forward: Boolean): Int {
    val supportedKeys = listOf(
        KeyEvent.KEYCODE_F4,
        KeyEvent.KEYCODE_F1,
        KeyEvent.KEYCODE_CAMERA,
        KeyEvent.KEYCODE_F2,
        KeyEvent.KEYCODE_F3,
        KeyEvent.KEYCODE_CALL
    )
    val idx = supportedKeys.indexOf(current)
    return if (idx == -1) {
        KeyEvent.KEYCODE_F4
    } else if (forward) {
        supportedKeys[(idx + 1) % supportedKeys.size]
    } else {
        supportedKeys[(idx - 1 + supportedKeys.size) % supportedKeys.size]
    }
}

private fun getKeyLabel(keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_F4 -> "Side Key (F4)"
    KeyEvent.KEYCODE_F1 -> "Upper-left (F1)"
    KeyEvent.KEYCODE_CAMERA -> "Upper-right (Camera)"
    KeyEvent.KEYCODE_F2 -> "Lower-left (F2)"
    KeyEvent.KEYCODE_F3 -> "Lower-right (F3)"
    KeyEvent.KEYCODE_CALL -> "Call Key"
    else -> "KeyCode $keyCode"
}
