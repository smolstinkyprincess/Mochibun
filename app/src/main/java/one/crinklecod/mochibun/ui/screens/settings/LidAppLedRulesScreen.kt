package one.crinklecod.mochibun.ui.screens.settings

import android.view.KeyEvent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.LidLedManager
import one.crinklecod.mochibun.data.model.AppLedRuleMode
import one.crinklecod.mochibun.data.model.LauncherApp
import one.crinklecod.mochibun.data.model.LidAppLedRule
import one.crinklecod.mochibun.data.model.LidLedAnimation
import one.crinklecod.mochibun.data.model.lidHexToArgb
import one.crinklecod.mochibun.data.model.normalizeLidHex
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.components.MochibunSectionHeader
import one.crinklecod.mochibun.ui.components.softGlowFocus
import one.crinklecod.mochibun.ui.dialogs.KawaiiDialogItem
import one.crinklecod.mochibun.ui.dialogs.KeitaiLaceDialogWithClose
import one.crinklecod.mochibun.ui.dialogs.LidLedColorPickerDialog
import one.crinklecod.mochibun.ui.dialogs.dialogWidth
import one.crinklecod.mochibun.ui.dialogs.isBackDismissKey
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import one.crinklecod.mochibun.ui.theme.ActiveTheme

/** One row: an installed app, or a package that still has a rule but is no longer installed. */
@Immutable
private data class RuleTarget(val packageName: String, val label: String, val icon: ImageBitmap?)

/**
 * Per-App LED Rules (Lid LED Studio → "Per-App LED Rules"): every launchable app with its rule
 * badge. Center opens a picker for Default / Custom Color / Custom Animation / Muted.
 * D-pad Up/Down move between apps; Back returns to the Studio library (handled by the Studio).
 */
@Composable
internal fun LidAppLedRulesScreen(
    apps: List<LauncherApp>,
    animations: List<LidLedAnimation>,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rules by PreferencesManager.lidAppLedRules.collectAsState()
    val listState = rememberLazyListState()

    // Built once per app list; uninstalled packages with leftover rules stay listed so they can be reset
    val targets = remember(apps, rules.keys) {
        val installed = apps.distinctBy { it.packageName }
            .sortedBy { it.lowercaseName }
            .map { RuleTarget(it.packageName, it.name, it.imageBitmap) }
        val installedPkgs = installed.mapTo(HashSet()) { it.packageName }
        installed + rules.keys.filter { it !in installedPkgs }.sorted().map { RuleTarget(it, it, null) }
    }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<RuleTarget?>(null) }
    var colorTarget by remember { mutableStateOf<RuleTarget?>(null) }

    LaunchedEffect(targets.size) { focusedIndex = focusedIndex.coerceIn(0, (targets.size - 1).coerceAtLeast(0)) }
    // Reclaim D-pad focus whenever a picker closes
    requestFocusSafely(focusRequester, key = pickerTarget == null && colorTarget == null)

    fun saveRule(rule: LidAppLedRule) {
        PreferencesManager.setLidAppLedRule(context, rule)
        SoundEffectManager.play(context, SoundEffect.SELECT)
    }

    SettingsScaffold(
        title = "Per-App LED Rules",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                when {
                    native.keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                        if (event.type == KeyEventType.KeyUp) {
                            SoundEffectManager.play(context, SoundEffect.BACK)
                            onBack()
                        }
                        true
                    }
                    isCenterActivateKey(native.keyCode) -> {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            targets.getOrNull(focusedIndex)?.let { pickerTarget = it }
                        }
                        true
                    }
                    native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (event.type == KeyEventType.KeyDown && targets.isNotEmpty()) {
                            val dir = if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                            focusedIndex = (focusedIndex + dir + targets.size) % targets.size
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        if (targets.isEmpty()) {
            Text(
                text = "No apps loaded yet ♡",
                color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 48.dp, bottom = 64.dp, start = 14.dp, end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = false }
            ) {
                itemsIndexed(targets, key = { _, t -> t.packageName }, contentType = { _, _ -> "app_led_rule_row" }) { index, target ->
                    AppRuleRow(
                        target = target,
                        rule = rules[target.packageName],
                        animations = animations,
                        isFocused = index == focusedIndex,
                        onClick = {
                            focusedIndex = index
                            pickerTarget = target
                        }
                    )
                }
            }
        }
    }

    pickerTarget?.let { target ->
        AppRulePickerDialog(
            appLabel = target.label,
            current = rules[target.packageName],
            animations = animations,
            onPick = { mode, animationId ->
                pickerTarget = null
                when (mode) {
                    AppLedRuleMode.CUSTOM_COLOR -> colorTarget = target
                    else -> saveRule(LidAppLedRule(target.packageName, mode, animationId = animationId))
                }
            },
            onDismiss = { pickerTarget = null }
        )
    }

    colorTarget?.let { target ->
        val current = rules[target.packageName]?.colorHex ?: PreferencesManager.getLidLedColor(context)
        LidLedColorPickerDialog(
            currentColor = current,
            onColorSelected = { key ->
                // Picker returns a preset id or hex; store a normalized #RRGGBB
                val hex = normalizeLidHex(LidLedManager.getHexColor(key))
                saveRule(LidAppLedRule(target.packageName, AppLedRuleMode.CUSTOM_COLOR, colorHex = hex))
            },
            onDismiss = { colorTarget = null }
        )
    }
}

@Composable
private fun AppRuleRow(
    target: RuleTarget,
    rule: LidAppLedRule?,
    animations: List<LidLedAnimation>,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val mode = rule?.mode ?: AppLedRuleMode.DEFAULT
    val detail = when (mode) {
        AppLedRuleMode.DEFAULT -> "Global notification light"
        AppLedRuleMode.CUSTOM_COLOR -> "Shimmer ${rule?.colorHex.orEmpty()}"
        AppLedRuleMode.CUSTOM_ANIMATION ->
            animations.firstOrNull { it.id == rule?.animationId }?.name ?: "Missing animation • uses global"
        AppLedRuleMode.MUTED -> "Never lights the lid"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 14.dp)
            .clip(shape)
            .background(ActiveTheme.container, shape)
            .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        if (target.icon != null) {
            Image(bitmap = target.icon, contentDescription = null, modifier = Modifier.size(26.dp))
        } else {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Text("?", color = ActiveTheme.accentDeep, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = target.label,
                color = ActiveTheme.accentDeep,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                color = ActiveTheme.textPrimary.copy(alpha = 0.7f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (mode == AppLedRuleMode.CUSTOM_COLOR && rule?.colorHex != null) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(lidHexToArgb(rule.colorHex)), CircleShape)
                    .border(0.6.dp, ActiveTheme.cardBorder, CircleShape)
            )
        }
        if (mode != AppLedRuleMode.DEFAULT) RuleBadge(mode.badge)
    }
}

@Composable
private fun RuleBadge(label: String) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = label,
        color = ActiveTheme.accentDeep,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(shape)
            .background(ActiveTheme.primary.copy(alpha = 0.55f), shape)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

/**
 * Two-stage picker: the 4 modes, then (for Custom Animation) the Studio animation list.
 * Custom Color hands off to [LidLedColorPickerDialog] through [onPick].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRulePickerDialog(
    appLabel: String,
    current: LidAppLedRule?,
    animations: List<LidLedAnimation>,
    onPick: (AppLedRuleMode, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val modes = AppLedRuleMode.entries
    var choosingAnimation by remember { mutableStateOf(false) }
    val itemCount = if (choosingAnimation) animations.size else modes.size
    // Index itemCount = the floating close pill
    var focusedIndex by remember(choosingAnimation) {
        mutableIntStateOf(
            if (choosingAnimation) {
                animations.indexOfFirst { it.id == current?.animationId }.coerceAtLeast(0)
            } else {
                modes.indexOf(current?.mode ?: AppLedRuleMode.DEFAULT)
            }
        )
    }
    var sawCenterDown by remember { mutableStateOf(false) }
    val dialogFocus = remember { FocusRequester() }
    requestFocusSafely(dialogFocus)

    fun back() {
        SoundEffectManager.play(context, SoundEffect.BACK)
        if (choosingAnimation) choosingAnimation = false else onDismiss()
    }

    fun activate(index: Int) {
        if (index >= itemCount) {
            back()
            return
        }
        SoundEffectManager.play(context, SoundEffect.SELECT)
        if (choosingAnimation) {
            animations.getOrNull(index)?.let { onPick(AppLedRuleMode.CUSTOM_ANIMATION, it.id) }
            return
        }
        when (val mode = modes[index]) {
            AppLedRuleMode.CUSTOM_ANIMATION -> if (animations.isNotEmpty()) choosingAnimation = true
            else -> onPick(mode, null)
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = true, usePlatformDefaultWidth = false),
        modifier = Modifier
            .focusRequester(dialogFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                when {
                    isBackDismissKey(native.keyCode) -> {
                        if (event.type == KeyEventType.KeyUp) back()
                        true
                    }
                    isCenterActivateKey(native.keyCode) -> {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            sawCenterDown = true
                        } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                            sawCenterDown = false
                            activate(focusedIndex)
                        }
                        true
                    }
                    native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (event.type == KeyEventType.KeyDown) {
                            val total = itemCount + 1
                            val dir = if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                            focusedIndex = (focusedIndex + dir + total) % total
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        KeitaiLaceDialogWithClose(
            isCloseFocused = focusedIndex == itemCount,
            onClose = ::back,
            closeLabel = if (choosingAnimation) "back" else "close",
            contentPadding = 14.dp,
            modifier = Modifier.width(dialogWidth)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                MochibunSectionHeader(
                    title = if (choosingAnimation) "Pick Animation" else "Lid LED ♡",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = appLabel,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .graphicsLayer { clip = false }
                ) {
                    if (choosingAnimation) {
                        itemsIndexed(animations, key = { _, a -> a.id }) { index, anim ->
                            KawaiiDialogItem(
                                text = (if (anim.id == current?.animationId) "♥ " else "") + anim.name,
                                isFocused = index == focusedIndex,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { activate(index) }
                            )
                        }
                    } else {
                        itemsIndexed(modes, key = { _, m -> m.name }) { index, mode ->
                            val selected = mode == (current?.mode ?: AppLedRuleMode.DEFAULT)
                            val suffix = if (mode == AppLedRuleMode.CUSTOM_COLOR || mode == AppLedRuleMode.CUSTOM_ANIMATION) "…" else ""
                            KawaiiDialogItem(
                                text = (if (selected) "♥ " else "") + mode.label + suffix,
                                isFocused = index == focusedIndex,
                                enabled = mode != AppLedRuleMode.CUSTOM_ANIMATION || animations.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { activate(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}
