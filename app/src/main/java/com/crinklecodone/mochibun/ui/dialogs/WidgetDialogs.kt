package com.crinklecodone.mochibun.ui.dialogs

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.crinklecodone.mochibun.ui.components.curtainUnfurl
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.components.softGlowFocus
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.selectedContainerColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** One row in the "Add Widget" picker. Text only: no preview bitmaps, to keep RAM low. */
class WidgetPickerEntry(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val cellsText: String,
    val shrinksToFit: Boolean
)

/** Approximate home-screen cells for a dp size, using Android's `70 × n − 30` sizing rule. */
private fun cellsFor(sizeDp: Float): Int = ceil((sizeDp + 30f) / 70f).toInt().coerceAtLeast(1)

private fun loadWidgetProviders(context: Context, availableWidthPx: Int): List<WidgetPickerEntry> {
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    val providers = try {
        AppWidgetManager.getInstance(context).installedProviders
    } catch (_: Exception) {
        emptyList()
    }
    return providers
        .filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
        .mapNotNull { info ->
            try {
                val appLabel = pm.getApplicationLabel(info.activityInfo.applicationInfo).toString()
                val label = info.loadLabel(pm)?.takeIf { it.isNotBlank() } ?: appLabel
                val widthDp = info.minWidth / density
                val heightDp = info.minHeight / density
                val resizes = (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
                val neededWidth = if (resizes && info.minResizeWidth in 1 until info.minWidth) {
                    info.minResizeWidth
                } else {
                    info.minWidth
                }
                WidgetPickerEntry(
                    info = info,
                    label = label,
                    appLabel = appLabel,
                    cellsText = "${cellsFor(widthDp)}×${cellsFor(heightDp)}",
                    shrinksToFit = neededWidth > availableWidthPx
                )
            } catch (_: Exception) {
                null
            }
        }
        .sortedWith(
            compareBy<WidgetPickerEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
}

/**
 * In-launcher widget picker. D-pad UP/DOWN to browse, OK to add, Back to close.
 * [availableWidthPx] is the card width on the Widgets page, used to flag widgets that will shrink.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerDialog(
    availableWidthPx: Int,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var sawCenterDown by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    val entries by produceState<List<WidgetPickerEntry>?>(initialValue = null, availableWidthPx) {
        value = withContext(Dispatchers.IO) { loadWidgetProviders(context, availableWidthPx) }
    }
    val list = entries.orEmpty()

    LaunchedEffect(selectedIndex, list.size) {
        if (list.isEmpty()) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any { it.index == selectedIndex } &&
            visible.first().index != selectedIndex &&
            visible.last().index != selectedIndex
        if (!fullyVisible) {
            listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
        }
    }

    requestFocusSafely(focusRequester)
    BackHandler { onDismiss() }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
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
                        list.getOrNull(selectedIndex)?.let { onPick(it.info) }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown || list.isEmpty()) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else list.lastIndex
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        selectedIndex = if (selectedIndex < list.lastIndex) selectedIndex + 1 else 0
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.coerceIn(22f, 24f).dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 460.dp)
                .curtainUnfurl(visible = true)
                .border(1.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "✦ Add a Widget ✦",
                    color = theme.fgTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = when (entries) {
                        null -> "Gathering widgets… ♡"
                        else -> if (list.isEmpty()) "No apps offer widgets yet ♡" else "${list.size} widgets from your apps ♡"
                    },
                    color = theme.cardTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(list, key = { _, e -> e.info.provider.flattenToString() }) { idx, entry ->
                        WidgetPickerRow(
                            entry = entry,
                            isFocused = idx == selectedIndex,
                            onClick = {
                                selectedIndex = idx
                                onPick(entry.info)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPickerRow(entry: WidgetPickerEntry, isFocused: Boolean, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 12.dp)
            .clip(shape)
            .background(if (isFocused) theme.selectedContainerColor else Color.Transparent, shape)
            .border(if (isFocused) 1.5.dp else 0.dp, if (isFocused) theme.appActionColor else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = entry.label,
            color = if (isFocused) theme.fgTextColor else theme.appActionColor,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildString {
                append(entry.appLabel)
                append(" · ")
                append(entry.cellsText)
                if (entry.shrinksToFit) append(" · shrinks to fit")
            },
            color = theme.cardTextColor.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Options for a hosted widget: move, reconfigure, use its controls, or remove.
 * Remove needs a second OK press so a stray click can't delete a configured widget.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetOptionsDialog(
    widgetLabel: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canConfigure: Boolean,
    canUseControls: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onConfigure: () -> Unit,
    onUseControls: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    var sawCenterDown by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }

    class Action(val label: String, val enabled: Boolean, val destructive: Boolean = false, val run: () -> Unit)

    val actions = listOf(
        Action("👆 Use widget buttons", canUseControls, run = onUseControls),
        Action("▲ Move up", canMoveUp, run = onMoveUp),
        Action("▼ Move down", canMoveDown, run = onMoveDown),
        Action("⚙ Configure widget", canConfigure, run = onConfigure),
        Action(
            if (confirmingRemove) "🗑 Press OK again to remove" else "🗑 Remove widget",
            enabled = true,
            destructive = true,
            run = { if (confirmingRemove) onRemove() else confirmingRemove = true }
        ),
        Action("♥ Close ♥", true, run = onDismiss)
    )

    var selectedIndex by remember { mutableIntStateOf(actions.indexOfFirst { it.enabled }.coerceAtLeast(0)) }

    fun step(delta: Int) {
        var idx = selectedIndex
        repeat(actions.size) {
            idx = (idx + delta + actions.size) % actions.size
            if (actions[idx].enabled) {
                selectedIndex = idx
                confirmingRemove = false
                return
            }
        }
    }

    requestFocusSafely(focusRequester)
    BackHandler { if (confirmingRemove) confirmingRemove = false else onDismiss() }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        if (confirmingRemove) confirmingRemove = false else onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        actions.getOrNull(selectedIndex)?.takeIf { it.enabled }?.run?.invoke()
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..actions.size) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val action = actions[digit - 1]
                        if (action.enabled) {
                            if (selectedIndex != digit - 1) confirmingRemove = false
                            selectedIndex = digit - 1
                            action.run()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                        step(-1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        step(1)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.coerceIn(22f, 24f).dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 460.dp)
                .curtainUnfurl(visible = true)
                .border(1.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "✦ $widgetLabel",
                    color = theme.fgTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Widget options ♡",
                    color = theme.cardTextColor,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                Spacer(modifier = Modifier.height(2.dp))
                actions.forEachIndexed { idx, action ->
                    val isClose = idx == actions.lastIndex
                    if (isClose) {
                        Spacer(modifier = Modifier.height(2.dp))
                        HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                    }
                    KawaiiDialogItem(
                        text = action.label,
                        isFocused = selectedIndex == idx,
                        enabled = action.enabled,
                        isDestructive = action.destructive,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = if (isClose) TextAlign.End else TextAlign.Start,
                        jumpNumber = idx + 1,
                        onClick = {
                            if (selectedIndex != idx) confirmingRemove = false
                            selectedIndex = idx
                            if (action.enabled) action.run()
                        }
                    )
                }
            }
        }
    }
}

