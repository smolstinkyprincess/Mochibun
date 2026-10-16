package com.tnyemii.mochibun

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import kotlinx.coroutines.launch

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

    // Sub-dialogs
    var keyForActionDialog by remember { mutableStateOf<PhysicalKey?>(null) }
    var keyForStaticDialog by remember { mutableStateOf<PhysicalKey?>(null) }
    var appPickerCallback by remember { mutableStateOf<((String, String) -> Unit)?>(null) }
    var showConfirmStageDialog by remember { mutableStateOf(false) }

    // Initial load and reconciliation
    LaunchedEffect(Unit) {
        // Load action configs
        for (key in PhysicalKey.entries) {
            actionConfigs[key] = HardwareRemapManager.getButtonActionConfig(context, key)
        }
        // Load static mappings
        val savedStatic = HardwareRemapManager.getConfiguredStaticMappings(context)
        staticMappings.clear()
        staticMappings.putAll(savedStatic)

        // Reconcile status
        isAccessibilityEnabled = HardwareRemapManager.isAccessibilityServiceEnabled(context)
        isKeyMapperActive = HardwareRemapManager.isKeyMapperActive(context)
        staticModuleStatus = HardwareRemapManager.reconcileModuleStatus()

        // Check root status quietly (without prompting unless user clicks)
        if (RootShell.isSuBinaryPresent()) {
            rootStatus = RootShell.requestRoot()
        } else {
            rootStatus = RootStatus.NotAvailable
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
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.92f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (currentTab > 0) {
                            currentTab--
                            selectedIndex = 0
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (currentTab < 2) {
                            currentTab++
                            selectedIndex = 0
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selectedIndex > 0) {
                            selectedIndex--
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val maxIndex = when (currentTab) {
                            0 -> PhysicalKey.entries.size
                            1 -> PhysicalKey.entries.filter { it.isStaticRemapSafe }.size + 1
                            else -> 4
                        }
                        if (selectedIndex < maxIndex) {
                            selectedIndex++
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        when (currentTab) {
                            0 -> {
                                if (selectedIndex < PhysicalKey.entries.size) {
                                    keyForActionDialog = PhysicalKey.entries[selectedIndex]
                                }
                            }
                            1 -> {
                                val safeKeys = PhysicalKey.entries.filter { it.isStaticRemapSafe }
                                if (selectedIndex < safeKeys.size) {
                                    keyForStaticDialog = safeKeys[selectedIndex]
                                } else if (selectedIndex == safeKeys.size) {
                                    showConfirmStageDialog = true
                                } else if (selectedIndex == safeKeys.size + 1) {
                                    // Reset static mappings
                                    staticMappings.clear()
                                    for (k in PhysicalKey.entries) {
                                        HardwareRemapManager.saveConfiguredStaticMapping(context, k, null)
                                    }
                                    statusMessage = "Static mappings cleared"
                                }
                            }
                            2 -> {
                                when (selectedIndex) {
                                    0 -> {
                                        // Request root
                                        scope.launch {
                                            rootStatus = RootStatus.Requesting
                                            rootStatus = RootShell.requestRoot()
                                        }
                                    }
                                    1 -> {
                                        // Open Accessibility Settings
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
                                        // Disable Magisk Module
                                        scope.launch {
                                            val res = HardwareRemapManager.disableModule()
                                            staticModuleStatus = HardwareRemapManager.reconcileModuleStatus()
                                            statusMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                                        }
                                    }
                                    3 -> {
                                        // Remove Magisk Module
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
        val shape = RoundedCornerShape(theme.cardRoundnessDp.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(theme.cardBgColor)
                .border(2.dp, theme.appActionColor, shape)
                .padding(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎮 Button Remapper",
                    color = theme.greetingColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "AT-M120",
                    color = theme.cardTextColor.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Navigation Tabs (Left / Right to switch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.bgColor.copy(alpha = 0.5f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val tabTitles = listOf("⚡ Actions", "🔧 Static .kl", "🛡 Status")
                tabTitles.forEachIndexed { index, title ->
                    val isTabActive = currentTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isTabActive) theme.selectedContainerColor else Color.Transparent)
                            .clickable {
                                currentTab = index
                                selectedIndex = 0
                            }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isTabActive) theme.fgTextColor else theme.cardTextColor.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Status notification bar if any
            statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.appActionColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = msg,
                        color = theme.fgTextColor,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Tab Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentTab) {
                    0 -> ActionsTabContent(
                        selectedIndex = selectedIndex,
                        actionConfigs = actionConfigs,
                        isAccessibilityEnabled = isAccessibilityEnabled,
                        isKeyMapperActive = isKeyMapperActive,
                        theme = theme,
                        onKeySelected = { keyForActionDialog = it }
                    )
                    1 -> StaticRemapTabContent(
                        selectedIndex = selectedIndex,
                        staticMappings = staticMappings,
                        moduleStatus = staticModuleStatus,
                        theme = theme,
                        onKeySelected = { keyForStaticDialog = it },
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
                        moduleStatus = staticModuleStatus,
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
            HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Footer controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "D-Pad ◄/► Tabs • ▲/▼ Select • OK Activate • Back Close",
                    color = theme.cardTextColor.copy(alpha = 0.5f),
                    fontSize = 9.sp
                )
            }
        }
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
            customMappings = staticMappings,
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

@Composable
private fun ActionsTabContent(
    selectedIndex: Int,
    actionConfigs: Map<PhysicalKey, ButtonActionConfig>,
    isAccessibilityEnabled: Boolean,
    isKeyMapperActive: Boolean,
    theme: LauncherTheme,
    onKeySelected: (PhysicalKey) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in 0 until PhysicalKey.entries.size) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isAccessibilityEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.2f))
                    .border(1.dp, Color(0xFFFF9800), RoundedCornerShape(6.dp))
                    .padding(6.dp)
            ) {
                Text(
                    text = "⚠ Accessibility Service is Disabled. Enable in Status tab to listen to keys outside Mochibun.",
                    color = theme.fgTextColor,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (isKeyMapperActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.appActionColor.copy(alpha = 0.15f))
                    .border(1.dp, theme.appActionColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(6.dp)
            ) {
                Text(
                    text = "ℹ KeyMapper Active: If a button is already mapped in KeyMapper, it consumes the key first. Unmap that button in KeyMapper or use Tab 1 (Static .kl) which runs beneath it.",
                    color = theme.fgTextColor,
                    fontSize = 9.sp,
                    lineHeight = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(PhysicalKey.entries) { index, key ->
                val isFocused = selectedIndex == index
                val config = actionConfigs[key] ?: ButtonActionConfig()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFocused) theme.selectedContainerColor else theme.bgColor.copy(alpha = 0.3f))
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) theme.appActionColor else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onKeySelected(key) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = key.keyName,
                                color = theme.fgTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Code ${key.defaultKeyCode}",
                                color = theme.cardTextColor.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val summary = buildString {
                            if (config.singlePressAction !is ActionType.None) {
                                append("1x: ${config.singlePressAction.displayName()}  ")
                            }
                            if (config.doublePressAction !is ActionType.None) {
                                append("2x: ${config.doublePressAction.displayName()}  ")
                            }
                            if (config.longPressAction !is ActionType.None) {
                                append("Long: ${config.longPressAction.displayName()}")
                            }
                            if (isEmpty()) append("No actions assigned (Default behavior)")
                        }

                        Text(
                            text = summary,
                            color = if (config.hasAnyAction()) theme.greetingColor else theme.cardTextColor.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaticRemapTabContent(
    selectedIndex: Int,
    staticMappings: Map<Int, String>,
    moduleStatus: StaticModuleStatus,
    theme: LauncherTheme,
    onKeySelected: (PhysicalKey) -> Unit,
    onApplyClick: () -> Unit,
    onResetClick: () -> Unit
) {
    val safeKeys = PhysicalKey.entries.filter { it.isStaticRemapSafe }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in 0..(safeKeys.size + 1)) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Module Status Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when (moduleStatus) {
                        is StaticModuleStatus.Active -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        is StaticModuleStatus.StagedPendingReboot -> Color(0xFF2196F3).copy(alpha = 0.2f)
                        is StaticModuleStatus.Disabled -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        else -> theme.bgColor.copy(alpha = 0.4f)
                    }
                )
                .padding(6.dp)
        ) {
            Column {
                Text(
                    text = "Magisk Module: ${moduleStatus.description()}",
                    color = theme.fgTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Vendor_2454_Product_6500.kl • Never modifies Generic.kl",
                    color = theme.cardTextColor.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(safeKeys) { index, key ->
                val isFocused = selectedIndex == index
                val defaultKey = BASELINE_MTK_KPD_MAPPINGS[key.scancode] ?: "DEFAULT"
                val mappedKey = staticMappings[key.scancode] ?: defaultKey
                val isRemapped = mappedKey != defaultKey

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFocused) theme.selectedContainerColor else theme.bgColor.copy(alpha = 0.3f))
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) theme.appActionColor else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onKeySelected(key) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = key.keyName,
                                color = theme.fgTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Scan ${key.scancode} • Orig: $defaultKey",
                                color = theme.cardTextColor.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }

                        Text(
                            text = if (isRemapped) "➔ $mappedKey" else "Default",
                            color = if (isRemapped) theme.greetingColor else theme.cardTextColor.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = if (isRemapped) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Action: Apply / Stage to Magisk
            item {
                val isFocused = selectedIndex == safeKeys.size
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFocused) theme.appActionColor else theme.selectedContainerColor)
                        .clickable { onApplyClick() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💾 Apply to Magisk Module (Reboot required)",
                        color = if (isFocused) theme.cardBgColor else theme.fgTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Action: Reset all static mappings
            item {
                val isFocused = selectedIndex == safeKeys.size + 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFocused) Color(0xFFFF5252).copy(alpha = 0.3f) else Color.Transparent)
                        .clickable { onResetClick() }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↩ Reset Staged Mappings to Defaults",
                        color = Color(0xFFFF5252),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusTabContent(
    selectedIndex: Int,
    rootStatus: RootStatus,
    isAccessibilityEnabled: Boolean,
    moduleStatus: StaticModuleStatus,
    theme: LauncherTheme,
    onRequestRoot: () -> Unit,
    onOpenSettings: () -> Unit,
    onDisableModule: () -> Unit,
    onRemoveModule: () -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Item 0: Root Access Status & Button
        item {
            val isFocused = selectedIndex == 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFocused) theme.selectedContainerColor else theme.bgColor.copy(alpha = 0.3f))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) theme.appActionColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onRequestRoot() }
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Magisk Root Permission",
                            color = theme.fgTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (rootStatus) {
                                is RootStatus.Granted -> "✔ Granted"
                                is RootStatus.Denied -> "❌ Denied"
                                is RootStatus.Requesting -> "⌛ Checking..."
                                else -> "Not Granted"
                            },
                            color = when (rootStatus) {
                                is RootStatus.Granted -> Color(0xFF4CAF50)
                                is RootStatus.Denied -> Color(0xFFFF5252)
                                else -> theme.greetingColor
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Press OK to request/verify Magisk root authorization.",
                        color = theme.cardTextColor.copy(alpha = 0.6f),
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFocused) theme.selectedContainerColor else theme.bgColor.copy(alpha = 0.3f))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) theme.appActionColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onOpenSettings() }
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Accessibility Service",
                            color = theme.fgTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isAccessibilityEnabled) "✔ Enabled" else "❌ Disabled",
                            color = if (isAccessibilityEnabled) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Required for real-time actions outside Mochibun. Press OK to open Accessibility Settings.",
                        color = theme.cardTextColor.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Item 2: Disable Magisk Module
        item {
            val isFocused = selectedIndex == 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFocused) theme.selectedContainerColor else theme.bgColor.copy(alpha = 0.3f))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) theme.appActionColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onDisableModule() }
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = "🚫 Disable Module (/disable flag)",
                        color = theme.fgTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Creates disable flag. Restores original key mappings on next reboot.",
                        color = theme.cardTextColor.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Item 3: Delete / Remove Magisk Module
        item {
            val isFocused = selectedIndex == 3
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFocused) theme.selectedContainerColor else theme.bgColor.copy(alpha = 0.3f))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) theme.appActionColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onRemoveModule() }
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = "🗑 Delete Module (/data/adb/modules/mochibun_keys)",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Completely deletes module files. Safe full reset.",
                        color = theme.cardTextColor.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Item 4: Emergency Recovery Documentation
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.bgColor.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = "🛡 Emergency Recovery (Without Mochibun)",
                        color = theme.greetingColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "If key mappings ever lock you out:\n" +
                            "1. Connect phone via ADB or boot into Recovery/TWRP.\n" +
                            "2. Run: touch /data/adb/modules/mochibun_keys/disable\n" +
                            "   or: rm -rf /data/adb/modules/mochibun_keys\n" +
                            "3. Reboot. Android will restore default Generic.kl mappings 100% safely.",
                        color = theme.cardTextColor.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}

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

    var selectedRow by remember { mutableIntStateOf(0) } // 0: single, 1: double, 2: long, 3: save
    val focusRequester = remember { FocusRequester() }

    val actionOptions = listOf(
        "None (Default)" to ActionType.None,
        "Open Mochibun AI Chat" to ActionType.OpenAiChat,
        "Global Back" to ActionType.GlobalBack,
        "Recents / Overview" to ActionType.GlobalRecents,
        "Open Notifications" to ActionType.GlobalNotifications,
        "Toggle Flashlight" to ActionType.ToggleFlashlight,
        "Launch App..." to null
    )

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selectedRow > 0) selectedRow--
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (selectedRow < 3) selectedRow++
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        when (selectedRow) {
                            0 -> {
                                // Cycle single press
                                cycleAction(singleAction, actionOptions, onPickApp) { singleAction = it }
                            }
                            1 -> {
                                // Cycle double press
                                cycleAction(doubleAction, actionOptions, onPickApp) { doubleAction = it }
                            }
                            2 -> {
                                // Cycle long press
                                cycleAction(longAction, actionOptions, onPickApp) { longAction = it }
                            }
                            3 -> {
                                onSave(ButtonActionConfig(singleAction, doubleAction, longAction))
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(theme.cardBgColor)
                .border(2.dp, theme.appActionColor, shape)
                .padding(12.dp)
        ) {
            Text(
                text = "⚡ Actions: ${physicalKey.keyName}",
                color = theme.greetingColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = physicalKey.description,
                color = theme.cardTextColor.copy(alpha = 0.6f),
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Row 0: Single Press
            ActionRow(
                label = "Single Press (1x)",
                currentAction = singleAction,
                isFocused = selectedRow == 0,
                theme = theme,
                onClick = { cycleAction(singleAction, actionOptions, onPickApp) { singleAction = it } }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Row 1: Double Press
            ActionRow(
                label = "Double Press (2x)",
                currentAction = doubleAction,
                isFocused = selectedRow == 1,
                theme = theme,
                onClick = { cycleAction(doubleAction, actionOptions, onPickApp) { doubleAction = it } }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Long Press
            ActionRow(
                label = "Long Press (Hold 500ms)",
                currentAction = longAction,
                isFocused = selectedRow == 2,
                theme = theme,
                onClick = { cycleAction(longAction, actionOptions, onPickApp) { longAction = it } }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedRow == 3) theme.appActionColor else theme.selectedContainerColor)
                    .clickable { onSave(ButtonActionConfig(singleAction, doubleAction, longAction)) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💾 Save Actions",
                    color = if (selectedRow == 3) theme.cardBgColor else theme.fgTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
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
    val currentIndex = when (current) {
        is ActionType.None -> 0
        is ActionType.OpenAiChat -> 1
        is ActionType.GlobalBack -> 2
        is ActionType.GlobalRecents -> 3
        is ActionType.GlobalNotifications -> 4
        is ActionType.ToggleFlashlight -> 5
        is ActionType.LaunchApp -> 6
    }
    val nextIndex = (currentIndex + 1) % options.size
    val nextOption = options[nextIndex].second
    if (nextOption == null) {
        // Launch App picker requested
        onPickApp { pkg, label -> onSet(ActionType.LaunchApp(pkg, label)) }
    } else {
        onSet(nextOption)
    }
}

@Composable
private fun ActionRow(
    label: String,
    currentAction: ActionType,
    isFocused: Boolean,
    theme: LauncherTheme,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) theme.selectedContainerColor else theme.bgColor.copy(alpha = 0.3f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) theme.appActionColor else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
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
                color = theme.fgTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = currentAction.displayName(),
                color = if (currentAction !is ActionType.None) theme.greetingColor else theme.cardTextColor.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = if (currentAction !is ActionType.None) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

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

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.8f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selectedIndex > 0) selectedIndex--
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (selectedIndex < installedApps.lastIndex) selectedIndex++
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        installedApps.getOrNull(selectedIndex)?.let {
                            onAppSelected(it.packageName, it.label)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(theme.cardBgColor)
                .border(2.dp, theme.appActionColor, shape)
                .padding(10.dp)
        ) {
            Text(
                text = "📱 Select Application",
                color = theme.greetingColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(installedApps) { index, app ->
                    val isFocused = selectedIndex == index
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) theme.selectedContainerColor else Color.Transparent)
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) theme.appActionColor else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onAppSelected(app.packageName, app.label) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = app.label,
                            color = theme.fgTextColor,
                            fontSize = 11.sp,
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

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

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.8f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        if (selectedIndex > 0) selectedIndex--
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (selectedIndex < CONTROLLED_STATIC_KEYCODES.lastIndex) selectedIndex++
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        CONTROLLED_STATIC_KEYCODES.getOrNull(selectedIndex)?.let { onSelect(it) }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(theme.cardBgColor)
                .border(2.dp, theme.appActionColor, shape)
                .padding(10.dp)
        ) {
            Text(
                text = "🔧 Map ${physicalKey.keyName}",
                color = theme.greetingColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Scan ${physicalKey.scancode} • Current: $currentMappedKey",
                color = theme.cardTextColor.copy(alpha = 0.6f),
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(CONTROLLED_STATIC_KEYCODES) { index, code ->
                    val isFocused = selectedIndex == index
                    val isCurrent = code == currentMappedKey

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) theme.selectedContainerColor else Color.Transparent)
                            .border(
                                width = if (isFocused) 2.dp else 0.dp,
                                color = if (isFocused) theme.appActionColor else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelect(code) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = code,
                                color = theme.fgTextColor,
                                fontSize = 11.sp,
                                fontWeight = if (isFocused || isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isCurrent) {
                                Text(
                                    text = "Current",
                                    color = theme.greetingColor,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmStageDialog(
    customMappings: Map<Int, String>,
    theme: LauncherTheme,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
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
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(theme.cardBgColor)
                .border(2.dp, theme.appActionColor, shape)
                .padding(12.dp)
        ) {
            Text(
                text = "⚠ Confirm Magisk Staging",
                color = theme.greetingColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "This will write Vendor_2454_Product_6500.kl into Magisk module /data/adb/modules/mochibun_keys.\n\n" +
                    "• Generic.kl is NEVER modified.\n" +
                    "• Changes require a manual phone reboot to activate.\n" +
                    "• Emergency rollback: touch /data/adb/modules/mochibun_keys/disable",
                color = theme.cardTextColor.copy(alpha = 0.8f),
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.bgColor.copy(alpha = 0.5f))
                        .clickable { onDismiss() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Cancel", color = theme.cardTextColor, fontSize = 11.sp)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.appActionColor)
                        .clickable { onConfirm() }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Confirm & Stage", color = theme.cardBgColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
