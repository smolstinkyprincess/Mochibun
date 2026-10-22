package com.crinklecodone.mochibun.ui.dialogs

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.core.system.RootShell
import com.crinklecodone.mochibun.core.system.RootStatus
import com.crinklecodone.mochibun.data.model.ActionType
import com.crinklecodone.mochibun.data.model.AppInfo
import com.crinklecodone.mochibun.data.model.BASELINE_MTK_KPD_MAPPINGS
import com.crinklecodone.mochibun.data.model.ButtonActionConfig
import com.crinklecodone.mochibun.data.model.CONTROLLED_STATIC_KEYCODES
import com.crinklecodone.mochibun.data.model.PhysicalKey
import com.crinklecodone.mochibun.data.model.StaticModuleStatus
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
import com.crinklecodone.mochibun.ui.components.MascotExpression
import com.crinklecodone.mochibun.ui.components.PixelArtMascot
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LauncherTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.destructiveBgColor
import com.crinklecodone.mochibun.ui.theme.destructiveColor
import com.crinklecodone.mochibun.ui.theme.selectedContainerColor
import kotlinx.coroutines.launch

/**
 * Checks whether a key event represents a Back, Escape, or Keypad Clear dismiss action.
 */
private fun isHardwareDismissKey(keyCode: Int): Boolean =
    isBackDismissKey(keyCode) || keyCode == KeyEvent.KEYCODE_CLEAR || keyCode == KeyEvent.KEYCODE_DEL

/**
 * Cute retro pixel-art badge representation of a physical hardware button.
 */
data class KeyBadgeData(
    val badgeLabel: String,
    val iconGlyph: String
)

fun PhysicalKey.toBadgeData(): KeyBadgeData = when (this) {
    PhysicalKey.F1 -> KeyBadgeData("F1", "★")
    PhysicalKey.CAMERA -> KeyBadgeData("CAM", "📷")
    PhysicalKey.KEY_ENTER -> KeyBadgeData("OK", "◉")
    PhysicalKey.F2 -> KeyBadgeData("F2", "✦")
    PhysicalKey.F3 -> KeyBadgeData("F3", "✧")
    PhysicalKey.CALL -> KeyBadgeData("CALL", "📞")
    PhysicalKey.APP_SWITCH -> KeyBadgeData("RECENTS", "⧉")
    PhysicalKey.F4 -> KeyBadgeData("SIDE / F4", "⏵")
    PhysicalKey.STAR -> KeyBadgeData("STAR (*)", "✱")
    PhysicalKey.POUND -> KeyBadgeData("POUND (#)", "＃")
}

@Composable
fun RetroKeyBadge(
    key: PhysicalKey,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val data = key.toBadgeData()
    RetroKeyBadge(
        label = data.badgeLabel,
        icon = data.iconGlyph,
        isFocused = isFocused,
        modifier = modifier
    )
}

@Composable
fun RetroKeyBadge(
    label: String,
    icon: String = "⌨",
    isFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(7.dp)
    val bg = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.surfaceTint.copy(alpha = 0.35f)
    val borderCol = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.7f)
    val textCol = if (isFocused) Color.White else ActiveTheme.accentDeep

    Row(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, borderCol, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = icon,
            fontSize = 9.sp,
            color = textCol
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = textCol,
            maxLines = 1
        )
    }
}

@Composable
private fun ActionPill(
    prefix: String,
    actionText: String,
    isConfigured: Boolean,
    isFocused: Boolean
) {
    val shape = RoundedCornerShape(6.dp)
    val bg = when {
        isFocused && isConfigured -> ActiveTheme.surfaceTint.copy(alpha = 0.5f)
        isConfigured -> ActiveTheme.surfaceTint.copy(alpha = 0.25f)
        else -> Color.Transparent
    }
    val borderCol = if (isConfigured) ActiveTheme.cardBorder.copy(alpha = 0.5f) else Color.Transparent
    val textCol = if (isConfigured) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg, shape)
            .then(if (isConfigured) Modifier.border(0.5.dp, borderCol, shape) else Modifier)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$prefix: ",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isConfigured) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.45f)
        )
        Text(
            text = actionText,
            fontSize = 9.5.sp,
            fontWeight = if (isConfigured) FontWeight.SemiBold else FontWeight.Normal,
            color = textCol,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Refactored Hardware Button Remapper Dialog.
 * Achieves 100% aesthetic and functional parity with the updated Settings UI and the Mochibun design system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareButtonsDialog(
    installedApps: List<AppInfo>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var currentTab by remember { mutableIntStateOf(0) } // 0: Actions, 1: Static Remap, 2: Status & Recovery
    var selectedIndex by remember { mutableIntStateOf(0) }

    // Live states
    var rootStatus by remember { mutableStateOf<RootStatus>(RootStatus.Unknown) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isKeyMapperActive by remember { mutableStateOf(false) }
    var staticModuleStatus by remember { mutableStateOf<StaticModuleStatus>(StaticModuleStatus.NotInstalled) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Local configs for editing
    val actionConfigs = remember { mutableStateMapOf<PhysicalKey, ButtonActionConfig>() }
    val staticMappings = remember { mutableStateMapOf<Int, String>() }

    var useRootRecentsFallback by remember {
        mutableStateOf(HardwareRemapManager.isRootRecentsFallbackEnabled(context))
    }

    // Sub-dialog states
    var isListeningForKey by remember { mutableStateOf(false) }
    var keyForActionDialog by remember { mutableStateOf<PhysicalKey?>(null) }
    var keyForStaticDialog by remember { mutableStateOf<PhysicalKey?>(null) }
    var appPickerCallback by remember { mutableStateOf<((String, String) -> Unit)?>(null) }
    var showConfirmStageDialog by remember { mutableStateOf(false) }

    val safeKeys = remember { PhysicalKey.entries.filter { it.isStaticRemapSafe } }

    // Initial load and reconciliation
    LaunchedEffect(Unit) {
        for (key in PhysicalKey.entries) {
            actionConfigs[key] = HardwareRemapManager.getButtonActionConfig(context, key)
        }
        val savedStatic = HardwareRemapManager.getConfiguredStaticMappings(context)
        staticMappings.clear()
        staticMappings.putAll(savedStatic)

        useRootRecentsFallback = HardwareRemapManager.isRootRecentsFallbackEnabled(context)
        isAccessibilityEnabled = HardwareRemapManager.isAccessibilityServiceEnabled(context)
        isKeyMapperActive = HardwareRemapManager.isKeyMapperActive(context)
        staticModuleStatus = HardwareRemapManager.reconcileModuleStatus()

        if (!RootShell.isSuBinaryPresent()) {
            rootStatus = RootStatus.NotAvailable
        } else {
            rootStatus = RootStatus.Unknown
        }
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.92f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isHardwareDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (currentTab > 0) {
                            currentTab--
                            selectedIndex = 0
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (currentTab < 2) {
                            currentTab++
                            selectedIndex = 0
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selectedIndex > 0) {
                            selectedIndex--
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val maxIndex = when (currentTab) {
                            0 -> PhysicalKey.entries.size // 0 is Detect Key, 1..size are keys
                            1 -> safeKeys.size + 2 // 0 is Detect Key, 1..size keys, +1 apply, +2 reset
                            else -> 4 // 5 status rows (0..4)
                        }
                        if (selectedIndex < maxIndex) {
                            selectedIndex++
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        when (currentTab) {
                            0 -> {
                                if (selectedIndex == 0) {
                                    isListeningForKey = true
                                } else {
                                    val keyIndex = selectedIndex - 1
                                    if (keyIndex in PhysicalKey.entries.indices) {
                                        keyForActionDialog = PhysicalKey.entries[keyIndex]
                                    }
                                }
                            }
                            1 -> {
                                if (selectedIndex == 0) {
                                    isListeningForKey = true
                                } else {
                                    val keyIndex = selectedIndex - 1
                                    if (keyIndex < safeKeys.size) {
                                        keyForStaticDialog = safeKeys[keyIndex]
                                    } else if (keyIndex == safeKeys.size) {
                                        showConfirmStageDialog = true
                                    } else if (keyIndex == safeKeys.size + 1) {
                                        staticMappings.clear()
                                        for (k in PhysicalKey.entries) {
                                            HardwareRemapManager.saveConfiguredStaticMapping(context, k, null)
                                        }
                                        statusMessage = "Static mappings cleared"
                                    }
                                }
                            }
                            2 -> {
                                when (selectedIndex) {
                                    0 -> {
                                        scope.launch {
                                            rootStatus = RootStatus.Requesting
                                            rootStatus = RootShell.requestRoot()
                                        }
                                    }
                                    1 -> {
                                        try {
                                            context.startActivity(
                                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        } catch (e: Exception) {
                                            statusMessage = "Cannot open settings: ${e.message}"
                                        }
                                    }
                                    2 -> {
                                        val newTarget = !useRootRecentsFallback
                                        if (newTarget) {
                                            if (rootStatus !is RootStatus.Granted && RootShell.isSuBinaryPresent()) {
                                                scope.launch {
                                                    rootStatus = RootStatus.Requesting
                                                    val res = RootShell.requestRoot()
                                                    rootStatus = res
                                                    if (res is RootStatus.Granted) {
                                                        useRootRecentsFallback = true
                                                        HardwareRemapManager.setRootRecentsFallbackEnabled(context, true)
                                                        statusMessage = "Root-shell Recents fallback enabled"
                                                    } else {
                                                        statusMessage = "Root access denied. Authorize Mochibun in Magisk."
                                                    }
                                                }
                                            } else if (!RootShell.isSuBinaryPresent()) {
                                                statusMessage = "su binary not found. Device is not rooted."
                                            } else {
                                                useRootRecentsFallback = true
                                                HardwareRemapManager.setRootRecentsFallbackEnabled(context, true)
                                                statusMessage = "Root-shell Recents fallback enabled"
                                            }
                                        } else {
                                            useRootRecentsFallback = false
                                            HardwareRemapManager.setRootRecentsFallbackEnabled(context, false)
                                            statusMessage = "Root-shell Recents fallback disabled"
                                        }
                                    }
                                    3 -> {
                                        scope.launch {
                                            val res = HardwareRemapManager.disableModule()
                                            staticModuleStatus = HardwareRemapManager.reconcileModuleStatus()
                                            statusMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                                        }
                                    }
                                    4 -> {
                                        scope.launch {
                                            val res = HardwareRemapManager.removeModule()
                                            staticModuleStatus = HardwareRemapManager.reconcileModuleStatus()
                                            statusMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                                        }
                                    }
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val dialogShape = RoundedCornerShape(22.dp)

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .border(1.5.dp, ActiveTheme.cardBorder, dialogShape),
            shape = dialogShape,
            color = ActiveTheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // Header with balanced pixel ribbon bows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PixelRibbonBow(width = 22.dp, height = 18.dp)
                    Text(
                        text = "— ♡ Button Remapper ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    PixelRibbonBow(width = 22.dp, height = 18.dp)
                }

                // Subtitle device badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AT-M120 • mtk-kpd & gpio-keys",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ActiveTheme.surfaceTint.copy(alpha = 0.25f))
                        .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tabTitles = listOf("⚡ Actions", "🔧 Static .kl", "🛡 Status")
                    tabTitles.forEachIndexed { index, title ->
                        val isTabActive = currentTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isTabActive) ActiveTheme.container else Color.Transparent)
                                .then(
                                    if (isTabActive) Modifier.border(1.dp, ActiveTheme.selectionGlow, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable {
                                    currentTab = index
                                    selectedIndex = 0
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isTabActive) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Notification bar for feedback
                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                            .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "♡",
                                fontSize = 10.sp,
                                color = ActiveTheme.accentDeep,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = msg,
                                color = ActiveTheme.accentDeep,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = ActiveTheme.cardBorder.copy(alpha = 0.4f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable tab content container
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (currentTab) {
                        0 -> ActionsTabContent(
                            selectedIndex = selectedIndex,
                            actionConfigs = actionConfigs,
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            isKeyMapperActive = isKeyMapperActive,
                            useRootRecentsFallback = useRootRecentsFallback,
                            theme = theme,
                            onDetectKeyClick = { isListeningForKey = true },
                            onKeySelected = {
                                keyForActionDialog = it
                            }
                        )
                        1 -> StaticRemapTabContent(
                            selectedIndex = selectedIndex,
                            safeKeys = safeKeys,
                            staticMappings = staticMappings,
                            moduleStatus = staticModuleStatus,
                            theme = theme,
                            onDetectKeyClick = { isListeningForKey = true },
                            onKeySelected = {
                                keyForStaticDialog = it
                            },
                            onApplyClick = { showConfirmStageDialog = true },
                            onResetClick = {
                                staticMappings.clear()
                                for (k in PhysicalKey.entries) {
                                    HardwareRemapManager.saveConfiguredStaticMapping(context, k, null)
                                }
                                statusMessage = "Static mappings cleared"
                            }
                        )
                        2 -> StatusTabContent(
                            selectedIndex = selectedIndex,
                            rootStatus = rootStatus,
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            useRootRecentsFallback = useRootRecentsFallback,
                            theme = theme,
                            onRequestRoot = {
                                scope.launch {
                                    rootStatus = RootStatus.Requesting
                                    rootStatus = RootShell.requestRoot()
                                }
                            },
                            onOpenSettings = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (e: Exception) {
                                    statusMessage = "Cannot open settings: ${e.message}"
                                }
                            },
                            onToggleRootRecentsFallback = {
                                val newTarget = !useRootRecentsFallback
                                if (newTarget) {
                                    if (rootStatus !is RootStatus.Granted && RootShell.isSuBinaryPresent()) {
                                        scope.launch {
                                            rootStatus = RootStatus.Requesting
                                            val res = RootShell.requestRoot()
                                            rootStatus = res
                                            if (res is RootStatus.Granted) {
                                                useRootRecentsFallback = true
                                                HardwareRemapManager.setRootRecentsFallbackEnabled(context, true)
                                                statusMessage = "Root-shell Recents fallback enabled"
                                            } else {
                                                statusMessage = "Root access denied. Authorize Mochibun in Magisk."
                                            }
                                        }
                                    } else if (!RootShell.isSuBinaryPresent()) {
                                        statusMessage = "su binary not found. Device is not rooted."
                                    } else {
                                        useRootRecentsFallback = true
                                        HardwareRemapManager.setRootRecentsFallbackEnabled(context, true)
                                        statusMessage = "Root-shell Recents fallback enabled"
                                    }
                                } else {
                                    useRootRecentsFallback = false
                                    HardwareRemapManager.setRootRecentsFallbackEnabled(context, false)
                                    statusMessage = "Root-shell Recents fallback disabled"
                                }
                            },
                            onDisableModule = {
                                scope.launch {
                                    val res = HardwareRemapManager.disableModule()
                                    staticModuleStatus = HardwareRemapManager.reconcileModuleStatus()
                                    statusMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                                }
                            },
                            onRemoveModule = {
                                scope.launch {
                                    val res = HardwareRemapManager.removeModule()
                                    staticModuleStatus = HardwareRemapManager.reconcileModuleStatus()
                                    statusMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = ActiveTheme.cardBorder.copy(alpha = 0.4f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(4.dp))

                // Footer controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "D-Pad ◄/► Tabs • ▲/▼ Select • OK Activate • Back Close",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Sub-dialog: Key Capture & Detection Mode
    if (isListeningForKey) {
        KeyCaptureListeningDialog(
            theme = theme,
            onDismiss = { isListeningForKey = false },
            onConfigureActions = { detectedKey ->
                isListeningForKey = false
                keyForActionDialog = detectedKey
            },
            onConfigureStatic = { detectedKey ->
                isListeningForKey = false
                keyForStaticDialog = detectedKey
            }
        )
    }

    // Sub-dialog: Edit high-level actions for a key
    keyForActionDialog?.let { key ->
        ActionConfigSubDialog(
            physicalKey = key,
            currentConfig = actionConfigs[key] ?: ButtonActionConfig(),
            installedApps = installedApps,
            theme = theme,
            onDismiss = { keyForActionDialog = null },
            onPickApp = { callback -> appPickerCallback = callback },
            onSave = { newConfig ->
                actionConfigs[key] = newConfig
                HardwareRemapManager.saveButtonActionConfig(context, key, newConfig)
                keyForActionDialog = null
                statusMessage = "Saved actions for ${key.keyName}"
            }
        )
    }

    // Sub-dialog: Pick an app for LaunchApp action
    appPickerCallback?.let { callback ->
        AppPickerSubDialog(
            installedApps = installedApps,
            theme = theme,
            onDismiss = { appPickerCallback = null },
            onAppSelected = { pkg, label ->
                callback(pkg, label)
                appPickerCallback = null
            }
        )
    }

    // Sub-dialog: Pick target KeyCode for static remapping
    keyForStaticDialog?.let { key ->
        StaticKeySubDialog(
            physicalKey = key,
            currentMappedKey = staticMappings[key.scancode] ?: BASELINE_MTK_KPD_MAPPINGS[key.scancode] ?: "DEFAULT",
            theme = theme,
            onDismiss = { keyForStaticDialog = null },
            onSelect = { selectedCode ->
                if (selectedCode == BASELINE_MTK_KPD_MAPPINGS[key.scancode]) {
                    staticMappings.remove(key.scancode)
                } else {
                    staticMappings[key.scancode] = selectedCode
                }
                HardwareRemapManager.saveConfiguredStaticMapping(context, key, staticMappings[key.scancode])
                keyForStaticDialog = null
                statusMessage = "Configured ${key.keyName} -> $selectedCode (Staging needed)"
            }
        )
    }

    // Sub-dialog: Confirm staging and deployment to Magisk
    if (showConfirmStageDialog) {
        ConfirmStageDialog(
            theme = theme,
            onDismiss = { showConfirmStageDialog = false },
            onConfirm = {
                showConfirmStageDialog = false
                scope.launch {
                    val result = HardwareRemapManager.stageAndApplyStaticMapping(context, staticMappings)
                    staticModuleStatus = HardwareRemapManager.reconcileModuleStatus()
                    statusMessage = result.getOrNull() ?: result.exceptionOrNull()?.message
                }
            }
        )
    }
}

/**
 * Tab 0: High-Level Dynamic Actions Content.
 */
@Composable
private fun ActionsTabContent(
    selectedIndex: Int,
    actionConfigs: Map<PhysicalKey, ButtonActionConfig>,
    isAccessibilityEnabled: Boolean,
    isKeyMapperActive: Boolean,
    useRootRecentsFallback: Boolean,
    theme: LauncherTheme,
    onDetectKeyClick: () -> Unit,
    onKeySelected: (PhysicalKey) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isAccessibilityEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                    .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(8.dp))
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "⚠️", fontSize = 11.sp)
                    Text(
                        text = "Accessibility Service is Disabled. Enable in Status tab to listen to keys outside Mochibun.",
                        color = ActiveTheme.accentDeep,
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (isKeyMapperActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.25f))
                    .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "ℹ️", fontSize = 11.sp)
                    Text(
                        text = "KeyMapper Active: Unmap keys in KeyMapper if they conflict, or use Tab 1 (Static .kl).",
                        color = ActiveTheme.textPrimary,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (useRootRecentsFallback) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.25f))
                    .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "⚡", fontSize = 11.sp)
                    Text(
                        text = "Root-Shell Recents Fallback is Active: Recents launches directly using root shell.",
                        color = ActiveTheme.accentDeep,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        val keyCardShape = RoundedCornerShape(10.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Index 0: Interactive Key Capture / Detect Button
            item {
                val isDetectFocused = selectedIndex == 0
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(keyCardShape)
                        .background(if (isDetectFocused) theme.selectedContainerColor else ActiveTheme.surfaceTint.copy(alpha = 0.22f))
                        .border(
                            width = if (isDetectFocused) 1.5.dp else 1.dp,
                            color = if (isDetectFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.6f),
                            shape = keyCardShape
                        )
                        .clickable { onDetectKeyClick() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.5f))
                                    .border(1.dp, ActiveTheme.cardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🎯", fontSize = 11.sp)
                            }
                            Column {
                                Text(
                                    text = "Listen for Key Press",
                                    color = if (isDetectFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Press any hardware button to identify & configure",
                                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Text(
                            text = "Listen ›",
                            color = if (isDetectFocused) ActiveTheme.accentDeep else ActiveTheme.primary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Index 1..size: Physical Keys
            itemsIndexed(
                items = PhysicalKey.entries,
                key = { _, key -> key.name }
            ) { index, key ->
                val isFocused = selectedIndex == index + 1
                val config = actionConfigs[key] ?: ButtonActionConfig()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(keyCardShape)
                        .background(if (isFocused) theme.selectedContainerColor else ActiveTheme.container.copy(alpha = 0.5f))
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.4f),
                            shape = keyCardShape
                        )
                        .clickable { onKeySelected(key) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RetroKeyBadge(key = key, isFocused = isFocused)
                                Text(
                                    text = key.keyName,
                                    color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "${key.hexScancode} • #${key.defaultKeyCode}",
                                color = ActiveTheme.textPrimary.copy(alpha = 0.5f),
                                fontSize = 9.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (config.hasAnyAction()) {
                                if (config.singlePressAction !is ActionType.None) {
                                    ActionPill("1x", config.singlePressAction.displayName(), true, isFocused)
                                }
                                if (config.doublePressAction !is ActionType.None) {
                                    ActionPill("2x", config.doublePressAction.displayName(), true, isFocused)
                                }
                                if (config.longPressAction !is ActionType.None) {
                                    ActionPill("Hold", config.longPressAction.displayName(), true, isFocused)
                                }
                            } else {
                                Text(
                                    text = "No custom action (system default)",
                                    color = ActiveTheme.textPrimary.copy(alpha = 0.5f),
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: Static Magisk .kl Keylayout Remap Content.
 */
@Composable
private fun StaticRemapTabContent(
    selectedIndex: Int,
    safeKeys: List<PhysicalKey>,
    staticMappings: Map<Int, String>,
    moduleStatus: StaticModuleStatus,
    theme: LauncherTheme,
    onDetectKeyClick: () -> Unit,
    onKeySelected: (PhysicalKey) -> Unit,
    onApplyClick: () -> Unit,
    onResetClick: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Module Status Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Magisk Module:",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActiveTheme.accentDeep
                    )
                    Text(
                        text = moduleStatus.description(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = ActiveTheme.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Target: Vendor_2454_Product_6500.kl • Generic.kl untouched",
                    color = ActiveTheme.textPrimary.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val safeKeyCardShape = RoundedCornerShape(10.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Index 0: Interactive Key Capture / Detect Button
            item {
                val isDetectFocused = selectedIndex == 0
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(safeKeyCardShape)
                        .background(if (isDetectFocused) theme.selectedContainerColor else ActiveTheme.surfaceTint.copy(alpha = 0.22f))
                        .border(
                            width = if (isDetectFocused) 1.5.dp else 1.dp,
                            color = if (isDetectFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.6f),
                            shape = safeKeyCardShape
                        )
                        .clickable { onDetectKeyClick() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.5f))
                                    .border(1.dp, ActiveTheme.cardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "🎯", fontSize = 11.sp)
                            }
                            Column {
                                Text(
                                    text = "Listen for Key Press",
                                    color = if (isDetectFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Press any hardware button to select for static remap",
                                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Text(
                            text = "Listen ›",
                            color = if (isDetectFocused) ActiveTheme.accentDeep else ActiveTheme.primary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Index 1..safeKeys.size: Safe physical keys
            itemsIndexed(
                items = safeKeys,
                key = { _, key -> key.name }
            ) { index, key ->
                val isFocused = selectedIndex == index + 1
                val defaultKey = BASELINE_MTK_KPD_MAPPINGS[key.scancode] ?: "DEFAULT"
                val mappedKey = staticMappings[key.scancode] ?: defaultKey
                val isRemapped = mappedKey != defaultKey

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(safeKeyCardShape)
                        .background(if (isFocused) theme.selectedContainerColor else ActiveTheme.container.copy(alpha = 0.5f))
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.4f),
                            shape = safeKeyCardShape
                        )
                        .clickable { onKeySelected(key) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RetroKeyBadge(key = key, isFocused = isFocused)
                            Column {
                                Text(
                                    text = key.keyName,
                                    color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold
                                )
                                Text(
                                    text = "Scan ${key.scancode} • Orig: $defaultKey",
                                    color = ActiveTheme.textPrimary.copy(alpha = 0.55f),
                                    fontSize = 9.5.sp
                                )
                            }
                        }

                        if (isRemapped) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.5f))
                                    .border(1.dp, ActiveTheme.selectionGlow, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "➔ $mappedKey",
                                    color = ActiveTheme.accentDeep,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "Default",
                                color = ActiveTheme.textPrimary.copy(alpha = 0.45f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Index safeKeys.size + 1: Apply to Magisk Module
            item {
                val isFocused = selectedIndex == safeKeys.size + 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isFocused) ActiveTheme.accentDeep else ActiveTheme.container)
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onApplyClick() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💾 Apply to Magisk Module (Reboot required)",
                        color = if (isFocused) Color.White else ActiveTheme.accentDeep,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Index safeKeys.size + 2: Reset static mappings
            item {
                val isFocused = selectedIndex == safeKeys.size + 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isFocused) destructiveColor else destructiveBgColor)
                        .border(1.dp, destructiveColor, RoundedCornerShape(10.dp))
                        .clickable { onResetClick() }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↺ Reset Staged Mappings to Defaults",
                        color = if (isFocused) Color.White else destructiveColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Tab 2: Status, Recovery, and Permissions Content.
 */
@Composable
private fun StatusTabContent(
    selectedIndex: Int,
    rootStatus: RootStatus,
    isAccessibilityEnabled: Boolean,
    useRootRecentsFallback: Boolean,
    theme: LauncherTheme,
    onRequestRoot: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleRootRecentsFallback: () -> Unit,
    onDisableModule: () -> Unit,
    onRemoveModule: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    val itemShape = RoundedCornerShape(10.dp)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Item 0: Root Access Status & Button
        item {
            val isFocused = selectedIndex == 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(if (isFocused) theme.selectedContainerColor else ActiveTheme.container.copy(alpha = 0.5f))
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.4f),
                        shape = itemShape
                    )
                    .clickable { onRequestRoot() }
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Magisk Root Permission",
                            color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                                .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when (rootStatus) {
                                    is RootStatus.Granted -> "✔ Granted"
                                    is RootStatus.Denied -> "✖ Denied"
                                    is RootStatus.Requesting -> "⏳ Checking..."
                                    else -> "Not Granted"
                                },
                                color = when (rootStatus) {
                                    is RootStatus.Granted -> ActiveTheme.accentDeep
                                    is RootStatus.Denied -> destructiveColor
                                    else -> ActiveTheme.textPrimary.copy(alpha = 0.7f)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Press OK to request or verify Magisk root authorization.",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Item 1: Accessibility Service Status & Button
        item {
            val isFocused = selectedIndex == 1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(if (isFocused) theme.selectedContainerColor else ActiveTheme.container.copy(alpha = 0.5f))
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.4f),
                        shape = itemShape
                    )
                    .clickable { onOpenSettings() }
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Accessibility Service",
                            color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                                .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isAccessibilityEnabled) "✔ Enabled" else "✖ Disabled",
                                color = if (isAccessibilityEnabled) ActiveTheme.accentDeep else destructiveColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Required for real-time actions outside Mochibun. Press OK to open settings.",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Item 2: Root-Shell Recents Fallback Toggle
        item {
            val isFocused = selectedIndex == 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(if (isFocused) theme.selectedContainerColor else ActiveTheme.container.copy(alpha = 0.5f))
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.4f),
                        shape = itemShape
                    )
                    .clickable { onToggleRootRecentsFallback() }
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Root-Shell Recents fallback",
                            color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                                .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (useRootRecentsFallback) "✔ ON" else "✖ OFF",
                                color = if (useRootRecentsFallback) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "If normal Recents fails on this firmware, launch known-working Recents using root.",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        }

        // Item 3: Disable Magisk Module
        item {
            val isFocused = selectedIndex == 3
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(if (isFocused) theme.selectedContainerColor else ActiveTheme.container.copy(alpha = 0.5f))
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.4f),
                        shape = itemShape
                    )
                    .clickable { onDisableModule() }
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = "🚫 Disable Module (/disable flag)",
                        color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Creates disable flag. Restores original key mappings on next reboot.",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Item 4: Delete / Remove Magisk Module
        item {
            val isFocused = selectedIndex == 4
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(if (isFocused) destructiveBgColor else ActiveTheme.container.copy(alpha = 0.5f))
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) destructiveColor else ActiveTheme.cardBorder.copy(alpha = 0.4f),
                        shape = itemShape
                    )
                    .clickable { onRemoveModule() }
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = "🗑 Delete Module (/data/adb/modules/mochibun_keys)",
                        color = destructiveColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Completely deletes module files. Safe full reset.",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Emergency Recovery Info card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.25f))
                    .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = "🛡 Emergency Recovery (Without Mochibun)",
                        color = ActiveTheme.accentDeep,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "If key mappings ever lock you out:\n" +
                            "1. Connect phone via ADB or boot into Recovery/TWRP.\n" +
                            "2. Run: touch /data/adb/modules/mochibun_keys/disable\n" +
                            "   or: rm -rf /data/adb/modules/mochibun_keys\n" +
                            "3. Reboot. Android will restore default Generic.kl mappings 100% safely.",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.75f),
                        fontSize = 8.5.sp,
                        lineHeight = 11.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Key Capture & Listening Dialog.
 * Intercepts physical key events live and presents soft mascot feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyCaptureListeningDialog(
    theme: LauncherTheme,
    onDismiss: () -> Unit,
    onConfigureActions: (PhysicalKey) -> Unit,
    onConfigureStatic: (PhysicalKey) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var detectedKey by remember { mutableStateOf<PhysicalKey?>(null) }
    var rawKeyEventInfo by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isHardwareDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type == KeyEventType.KeyDown) {
                    val key = PhysicalKey.fromKeyCode(native.keyCode)
                        ?: PhysicalKey.fromScancode(native.scanCode)
                    if (key != null) {
                        detectedKey = key
                    } else {
                        rawKeyEventInfo = "KeyCode: ${native.keyCode} • Scan: ${native.scanCode}"
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with ribbon bows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PixelRibbonBow(width = 22.dp, height = 18.dp)
                    Text(
                        text = "— ♡ Key Capture ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    PixelRibbonBow(width = 22.dp, height = 18.dp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mascot indicator with expression feedback
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer {
                            scaleX = if (detectedKey == null) pulseScale else 1f
                            scaleY = if (detectedKey == null) pulseScale else 1f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PixelArtMascot(
                        expression = if (detectedKey != null) MascotExpression.RESPONDING else MascotExpression.THINKING,
                        size = 48.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (detectedKey == null) {
                    Text(
                        text = "Listening for physical key...",
                        color = ActiveTheme.accentDeep,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Press any button on your AT-M120 keypad\n(Side Key, Call, Camera, F1–F4, etc.)",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )

                    rawKeyEventInfo?.let { raw ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Unrecognized button: $raw",
                            color = destructiveColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    val key = detectedKey!!
                    Text(
                        text = "Key Captured! ♡",
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ActiveTheme.surfaceTint.copy(alpha = 0.4f))
                            .border(1.dp, ActiveTheme.selectionGlow, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        RetroKeyBadge(key = key, isFocused = true)
                        Column {
                            Text(
                                text = key.keyName,
                                color = ActiveTheme.accentDeep,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Scan ${key.hexScancode} (${key.scancode}) • KeyCode #${key.defaultKeyCode}",
                                color = ActiveTheme.textPrimary.copy(alpha = 0.6f),
                                fontSize = 9.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ActiveTheme.accentDeep)
                                .clickable { onConfigureActions(key) }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚡ Actions",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (key.isStaticRemapSafe) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ActiveTheme.container)
                                    .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(8.dp))
                                    .clickable { onConfigureStatic(key) }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🔧 Static .kl",
                                    color = ActiveTheme.accentDeep,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ActiveTheme.surfaceTint.copy(alpha = 0.25f))
                        .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel (Back)",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Sub-dialog: Configure Single, Double, and Long-press Actions for a specific key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionConfigSubDialog(
    physicalKey: PhysicalKey,
    currentConfig: ButtonActionConfig,
    installedApps: List<AppInfo>,
    theme: LauncherTheme,
    onDismiss: () -> Unit,
    onPickApp: (((String, String) -> Unit)) -> Unit,
    onSave: (ButtonActionConfig) -> Unit
) {
    var singleAction by remember { mutableStateOf(currentConfig.singlePressAction) }
    var doubleAction by remember { mutableStateOf(currentConfig.doublePressAction) }
    var longAction by remember { mutableStateOf(currentConfig.longPressAction) }

    var selectedRow by remember { mutableIntStateOf(0) } // 0: single, 1: double, 2: long, 3: save, 4: cancel
    val focusRequester = remember { FocusRequester() }

    val actionOptions = listOf(
        "None (Default)" to ActionType.None,
        "Global Back" to ActionType.GlobalBack,
        "Recents / Overview" to ActionType.GlobalRecents,
        "Open Recents (System)" to ActionType.OpenRecentsSystem,
        "Open Notifications" to ActionType.GlobalNotifications,
        "Toggle Flashlight" to ActionType.ToggleFlashlight,
        "Launch App..." to null
    )

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isHardwareDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selectedRow > 0) selectedRow--
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (selectedRow < 4) selectedRow++
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        when (selectedRow) {
                            0 -> cycleAction(singleAction, actionOptions, onPickApp) { singleAction = it }
                            1 -> cycleAction(doubleAction, actionOptions, onPickApp) { doubleAction = it }
                            2 -> cycleAction(longAction, actionOptions, onPickApp) { longAction = it }
                            3 -> onSave(ButtonActionConfig(singleAction, doubleAction, longAction))
                            4 -> onDismiss()
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(20.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PixelRibbonBow(width = 20.dp, height = 16.dp)
                    Text(
                        text = "— ♡ Configure Actions ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    PixelRibbonBow(width = 20.dp, height = 16.dp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RetroKeyBadge(key = physicalKey, isFocused = true)
                    Text(
                        text = physicalKey.keyName,
                        color = ActiveTheme.accentDeep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action rows
                ActionConfigRow(
                    label = "Single Press (1x)",
                    currentAction = singleAction,
                    isFocused = selectedRow == 0,
                    onClick = { cycleAction(singleAction, actionOptions, onPickApp) { singleAction = it } }
                )

                Spacer(modifier = Modifier.height(4.dp))

                ActionConfigRow(
                    label = "Double Press (2x)",
                    currentAction = doubleAction,
                    isFocused = selectedRow == 1,
                    onClick = { cycleAction(doubleAction, actionOptions, onPickApp) { doubleAction = it } }
                )

                Spacer(modifier = Modifier.height(4.dp))

                ActionConfigRow(
                    label = "Long Press (Hold 500ms)",
                    currentAction = longAction,
                    isFocused = selectedRow == 2,
                    onClick = { cycleAction(longAction, actionOptions, onPickApp) { longAction = it } }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Save button
                val isSaveFocused = selectedRow == 3
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSaveFocused) ActiveTheme.accentDeep else ActiveTheme.container)
                        .border(
                            width = if (isSaveFocused) 1.5.dp else 1.dp,
                            color = if (isSaveFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSave(ButtonActionConfig(singleAction, doubleAction, longAction)) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💾 Save Actions",
                        color = if (isSaveFocused) Color.White else ActiveTheme.accentDeep,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Cancel button
                val isCancelFocused = selectedRow == 4
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCancelFocused) ActiveTheme.surfaceTint.copy(alpha = 0.45f) else Color.Transparent)
                        .clickable { onDismiss() }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun cycleAction(
    current: ActionType,
    options: List<Pair<String, ActionType?>>,
    onPickApp: (((String, String) -> Unit)) -> Unit,
    onSet: (ActionType) -> Unit
) {
    val currentIndex = options.indexOfFirst { (_, action) ->
        when {
            action == null -> current is ActionType.LaunchApp
            current is ActionType.LaunchApp -> false
            else -> action::class == current::class
        }
    }.coerceAtLeast(0)

    val nextIndex = (currentIndex + 1) % options.size
    val nextOption = options[nextIndex].second
    if (nextOption == null) {
        onPickApp { pkg, label -> onSet(ActionType.LaunchApp(pkg, label)) }
    } else {
        onSet(nextOption)
    }
}

@Composable
private fun ActionConfigRow(
    label: String,
    currentAction: ActionType,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val isCustom = currentAction !is ActionType.None

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isFocused) ActiveTheme.surfaceTint.copy(alpha = 0.45f) else ActiveTheme.container.copy(alpha = 0.5f))
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.4f),
                shape = shape
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                fontSize = 11.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isCustom) ActiveTheme.surfaceTint.copy(alpha = 0.5f) else Color.Transparent)
                    .then(if (isCustom) Modifier.border(0.5.dp, ActiveTheme.cardBorder, RoundedCornerShape(6.dp)) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = currentAction.displayName(),
                    color = if (isCustom) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.5f),
                    fontSize = 9.5.sp,
                    fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Sub-dialog: Application picker for LaunchApp actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSubDialog(
    installedApps: List<AppInfo>,
    theme: LauncherTheme,
    onDismiss: () -> Unit,
    onAppSelected: (String, String) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.78f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isHardwareDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selectedIndex > 0) selectedIndex--
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (selectedIndex < installedApps.lastIndex) selectedIndex++
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        installedApps.getOrNull(selectedIndex)?.let {
                            onAppSelected(it.packageName, it.label)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(20.dp)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .border(1.5.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PixelRibbonBow(width = 20.dp, height = 16.dp)
                    Text(
                        text = "— ♡ Select Application ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    PixelRibbonBow(width = 20.dp, height = 16.dp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                val appRowShape = RoundedCornerShape(8.dp)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(
                        items = installedApps,
                        key = { _, app -> app.packageName }
                    ) { index, app ->
                        val isFocused = selectedIndex == index
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(appRowShape)
                                .background(if (isFocused) theme.selectedContainerColor else Color.Transparent)
                                .border(
                                    width = if (isFocused) 1.5.dp else 0.dp,
                                    color = if (isFocused) ActiveTheme.selectionGlow else Color.Transparent,
                                    shape = appRowShape
                                )
                                .clickable { onAppSelected(app.packageName, app.label) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(ActiveTheme.surfaceTint.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = app.label.take(1).uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ActiveTheme.accentDeep
                                    )
                                }
                                Text(
                                    text = app.label,
                                    color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sub-dialog: Map target Android KeyCode for static key layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaticKeySubDialog(
    physicalKey: PhysicalKey,
    currentMappedKey: String,
    theme: LauncherTheme,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.78f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isHardwareDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selectedIndex > 0) selectedIndex--
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (selectedIndex < CONTROLLED_STATIC_KEYCODES.lastIndex) selectedIndex++
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        CONTROLLED_STATIC_KEYCODES.getOrNull(selectedIndex)?.let { onSelect(it) }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(20.dp)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .border(1.5.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PixelRibbonBow(width = 20.dp, height = 16.dp)
                    Text(
                        text = "— ♡ Map ${physicalKey.keyName} ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    PixelRibbonBow(width = 20.dp, height = 16.dp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scan ${physicalKey.scancode} (${physicalKey.hexScancode}) • Current: $currentMappedKey",
                        color = ActiveTheme.textPrimary.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val staticKeyRowShape = RoundedCornerShape(8.dp)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    itemsIndexed(
                        items = CONTROLLED_STATIC_KEYCODES,
                        key = { _, code -> code }
                    ) { index, code ->
                        val isFocused = selectedIndex == index
                        val isCurrent = code == currentMappedKey

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(staticKeyRowShape)
                                .background(if (isFocused) theme.selectedContainerColor else Color.Transparent)
                                .border(
                                    width = if (isFocused) 1.5.dp else 0.dp,
                                    color = if (isFocused) ActiveTheme.selectionGlow else Color.Transparent,
                                    shape = staticKeyRowShape
                                )
                                .clickable { onSelect(code) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = code,
                                    color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isFocused || isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ActiveTheme.surfaceTint.copy(alpha = 0.5f))
                                            .border(0.5.dp, ActiveTheme.cardBorder, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Current",
                                            color = ActiveTheme.accentDeep,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sub-dialog: Confirmation before writing and staging changes to Magisk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmStageDialog(
    theme: LauncherTheme,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isHardwareDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown && isCenterActivateKey(native.keyCode)) {
                    onConfirm()
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        val shape = RoundedCornerShape(20.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PixelRibbonBow(width = 20.dp, height = 16.dp)
                    Text(
                        text = "— ♡ Confirm Magisk Staging ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    PixelRibbonBow(width = 20.dp, height = 16.dp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ActiveTheme.surfaceTint.copy(alpha = 0.25f))
                        .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Writes Vendor_2454_Product_6500.kl into Magisk module /data/adb/modules/mochibun_keys.",
                            color = ActiveTheme.textPrimary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 12.sp
                        )
                        Text(
                            text = "• Generic.kl is NEVER modified.",
                            color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                            fontSize = 9.sp
                        )
                        Text(
                            text = "• Changes require a manual phone reboot to activate.",
                            color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                            fontSize = 9.sp
                        )
                        Text(
                            text = "• Emergency rollback: touch /data/adb/modules/mochibun_keys/disable",
                            color = ActiveTheme.accentDeep,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ActiveTheme.container)
                            .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ActiveTheme.accentDeep)
                            .border(1.dp, ActiveTheme.selectionGlow, RoundedCornerShape(8.dp))
                            .clickable { onConfirm() }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Confirm & Stage",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
