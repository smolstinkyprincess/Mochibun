package one.crinklecod.mochibun.ui.screens.settings

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.ui.dialogs.isBackDismissKey
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.components.KawaiiClosePill
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.crinklecod.mochibun.ui.components.BottomLavenderClouds
import one.crinklecod.mochibun.ui.components.DocomoPictogram
import one.crinklecod.mochibun.ui.components.JumpNumberBadge
import one.crinklecod.mochibun.ui.components.TopLavenderClouds
import one.crinklecod.mochibun.ui.components.mochibunCardBorder
import one.crinklecod.mochibun.ui.components.softGlowFocus
import one.crinklecod.mochibun.ui.dialogs.PixelRibbonBow
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import kotlinx.coroutines.flow.collectLatest

/**
 * True while the scaffold's floating Back pill holds D-pad focus. The screen's own focus index is
 * left untouched (so jump keys and per-screen indices keep working), so rows hide their focus
 * styling through [unlessBackPillFocused] instead. A static local holding a stable [State]: only
 * readers of `.value` recompose when it flips.
 */
internal val LocalSettingsBackPillFocused = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(false) }

/**
 * Row focus with the floating Back pill taken into account. Short-circuits on `false`, so only the
 * one focused row reads the pill state and recomposes when focus moves onto/off the pill.
 */
@Composable
@ReadOnlyComposable
internal fun Boolean.unlessBackPillFocused(): Boolean =
    this && !LocalSettingsBackPillFocused.current.value

/**
 * Subtitle / value marquee (titles stay static with an ellipsis): only the focused row scrolls (endlessly while focused); every other row is
 * static. The node always stays in the chain and only its `iterations` change: adding/removing
 * `basicMarquee` per D-pad step re-measured the text and spiked D-pad p99 on the MT6761.
 */
internal fun Modifier.settingsMarquee(focused: Boolean): Modifier = basicMarquee(
    iterations = if (focused) Int.MAX_VALUE else 0,
    initialDelayMillis = 800,
    velocity = 24.dp
)

/**
 * Standardized Settings Screen Scaffold for the Chinoe AT-M120 (480x800).
 * Features vector Bézier scalloped cloud framing, a centered kawaii title, and a Back pill floating
 * bottom-centre over the clouds (like [one.crinklecod.mochibun.ui.components.KawaiiReturnHomePill]
 * on Widgets / Notifications). Content lists need ~72dp bottom padding to scroll clear of it.
 *
 * D-pad: when [isLastItemFocused] reports the screen's last row is focused, DPAD_DOWN moves focus
 * onto the pill; DPAD_UP returns it, CENTER/ENTER triggers [onBack]. This layer sits ahead of the
 * screen's own key handler (earlier in the modifier chain = earlier preview dispatch), so screens
 * keep their focus indices unchanged. A null [isLastItemFocused] leaves the pill touch-only.
 */
@Composable
fun SettingsScaffold(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    isLastItemFocused: (() -> Boolean)? = null,
    headerDocomoIndex: Int? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val backPillFocused = remember { mutableStateOf(false) }
    var sawPillCenterDown by remember { mutableStateOf(false) }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentIsLastItemFocused by rememberUpdatedState(isLastItemFocused)

    val backPillKeys = if (showBack) {
        Modifier.onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val keyCode = native.keyCode
            if (!backPillFocused.value) {
                val lastFocused = currentIsLastItemFocused ?: return@onPreviewKeyEvent false
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && lastFocused()) {
                    if (event.type == KeyEventType.KeyDown) {
                        backPillFocused.value = true
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    return@onPreviewKeyEvent true
                }
                return@onPreviewKeyEvent false
            }
            when {
                keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.type == KeyEventType.KeyDown) {
                        backPillFocused.value = false
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    true
                }
                isCenterActivateKey(keyCode) -> {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawPillCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawPillCenterDown) {
                        sawPillCenterDown = false
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        currentOnBack()
                    }
                    true
                }
                // The pill is a dead end sideways/downwards; swallow so the hidden row isn't edited
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> true
                // Back / Escape / RSK: let the screen handle it (pill stays focused for the exit)
                isBackDismissKey(keyCode) || keyCode == KeyEvent.KEYCODE_SOFT_RIGHT -> false
                // Anything else (jump digits, etc.) hands focus back to the rows first
                else -> {
                    backPillFocused.value = false
                    false
                }
            }
        }
    } else {
        Modifier
    }

    CompositionLocalProvider(LocalSettingsBackPillFocused provides backPillFocused) {
        Box(
            modifier = backPillKeys
                .then(modifier)
                .fillMaxSize()
                .background(ActiveTheme.background)
        ) {
            // Content container
            content()

            // 1. Top Decorative Scalloped Clouds
            TopLavenderClouds(
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // 2. Bottom Decorative Scalloped Clouds
            BottomLavenderClouds(
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // 3. Top Header Bar: title gets the full width between two ribbon bows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PixelRibbonBow(
                    width = 26.dp,
                    height = 21.dp
                )

                // Centered Title (+ optional 14.dp Docomo pictogram)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (headerDocomoIndex != null) {
                        DocomoPictogram(
                            index = headerDocomoIndex,
                            size = 14.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "— ♡ $title ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                // Right decorative ribbon balance
                PixelRibbonBow(
                    width = 26.dp,
                    height = 21.dp
                )
            }

            // 4. Floating Back pill, bottom-centre over the clouds
            if (showBack) {
                KawaiiClosePill(
                    isFocused = backPillFocused.value,
                    onClick = {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onBack()
                    },
                    label = "Back",
                    docomoIndex = 40,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}

/**
 * Scrolls [listState] to the LazyColumn item [targetItem] returns (null = leave it) whenever
 * that target changes.
 *
 * Why not `LaunchedEffect(focusedIndex) { ... }`: keying an effect on the focus index reads it in
 * the screen body, so every D-pad step re-ran the whole settings screen (key handler, row-id
 * lists, LazyColumn content) before reaching the two rows whose `isFocused` actually flipped.
 * Reading it inside [snapshotFlow] keeps that read out of composition, so a step only recomposes
 * the card scopes that read the index: no full-screen recomposition per key press on the JIT-only
 * MT6761.
 *
 * With [onlyIfHidden] (default) it only scrolls when the target item is not on screen at all,
 * matching the original per-screen effects. `collectLatest` cancels an in-flight scroll when focus
 * moves again, like a re-keyed LaunchedEffect did. By default it re-runs only when the target
 * changes; [retriggerKey] (read lazily too) makes it re-run whenever that key changes as well,
 * for screens whose old effect was keyed on the focus index itself.
 */
@Composable
internal fun ScrollToFocusedItem(
    listState: LazyListState,
    onlyIfHidden: Boolean = true,
    retriggerKey: (() -> Any?)? = null,
    targetItem: () -> Int?
) {
    val currentTargetItem by rememberUpdatedState(targetItem)
    val currentRetriggerKey by rememberUpdatedState(retriggerKey)
    LaunchedEffect(listState, onlyIfHidden) {
        snapshotFlow { currentTargetItem() to currentRetriggerKey?.invoke() }.collectLatest { (target, _) ->
            if (target == null) return@collectLatest
            if (onlyIfHidden) {
                val visible = listState.layoutInfo.visibleItemsInfo
                if (visible.isEmpty() || visible.any { it.index == target }) return@collectLatest
            }
            listState.animateScrollToItem(target)
        }
    }
}

/**
 * Themed settings card container with rounded corners (22.dp) and category pill.
 */
@Composable
fun SettingsCard(
    categoryTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Category Pill
        Row(
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                    .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "♡ $categoryTitle ♡",
                    color = ActiveTheme.accentDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Card Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ActiveTheme.container)
                .mochibunCardBorder(
                    cornerRadius = 22.dp,
                    borderColor = ActiveTheme.cardBorder,
                    borderWidth = 1.5.dp,
                    allowCustomNineSlice = false
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            content()
        }
    }
}

/**
 * Navigation row leading to a dedicated sub-screen.
 */
@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String? = null,
    icon: String = "",
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    docomoIndex: Int? = null,
    jumpNumber: Int? = null,
    onClick: () -> Unit
) {
    val focused = isFocused.unlessBackPillFocused()
    val shape = RoundedCornerShape(14.dp)
    val bg = if (focused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = focused, cornerRadius = 14.dp)
            .clip(shape)
            .background(bg, shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading squircle icon badge
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ActiveTheme.surfaceTint.copy(alpha = 0.45f))
                .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (docomoIndex != null) {
                DocomoPictogram(
                    index = docomoIndex,
                    size = 16.dp
                )
            } else {
                Text(
                    text = icon,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title and Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (focused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.settingsMarquee(focused)
                )
            }
        }

        // Trailing Navigation Arrow & Jump Number
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            if (jumpNumber != null) {
                JumpNumberBadge(
                    digit = jumpNumber,
                    isFocused = focused,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            Text(
                text = "›",
                color = if (focused) ActiveTheme.accentDeep else ActiveTheme.cardBorder,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Kawaii Toggle Switch Row (Boolean preference).
 */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    jumpNumber: Int? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val focused = isFocused.unlessBackPillFocused()
    val shape = RoundedCornerShape(14.dp)
    val bg = if (focused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = focused, cornerRadius = 14.dp)
            .clip(shape)
            .background(bg, shape)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (focused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.settingsMarquee(focused)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (jumpNumber != null) {
                JumpNumberBadge(
                    digit = jumpNumber,
                    isFocused = focused,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Pill-shaped kawaii switch
            val switchBg = if (checked) ActiveTheme.primary else ActiveTheme.cardBorder.copy(alpha = 0.4f)
            val switchText = if (checked) "ON ♡" else "OFF"
            val switchTextColor = if (checked) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.6f)

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(switchBg, CircleShape)
                    .border(1.dp, ActiveTheme.cardBorder, CircleShape)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = switchText,
                    color = switchTextColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Numeric Stepper Row supporting physical D-pad Left/Right navigation.
 */
@Composable
fun SettingsStepperRow(
    title: String,
    subtitle: String? = null,
    valueDisplay: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    jumpNumber: Int? = null,
    onClick: (() -> Unit)? = null,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    val focused = isFocused.unlessBackPillFocused()
    val shape = RoundedCornerShape(14.dp)
    val bg = if (focused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = focused, cornerRadius = 14.dp)
            .clip(shape)
            .background(bg, shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onClick?.invoke() ?: onIncrement() }
        ) {
            Text(
                text = title,
                color = if (focused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.settingsMarquee(focused)
                )
            }
        }

        // Stepper controls
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (jumpNumber != null) {
                JumpNumberBadge(
                    digit = jumpNumber,
                    isFocused = focused,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Decrement button
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ActiveTheme.container)
                    .border(1.dp, ActiveTheme.cardBorder, CircleShape)
                    .clickable { onDecrement() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◄",
                    color = ActiveTheme.accentDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Value badge
            Box(
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.45f))
                    .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(8.dp))
                    .clickable { onIncrement() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueDisplay,
                    color = ActiveTheme.accentDeep,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.settingsMarquee(focused)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ActiveTheme.container)
                    .border(1.dp, ActiveTheme.cardBorder, CircleShape)
                    .clickable { onIncrement() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "►",
                    color = ActiveTheme.accentDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Text input row opening focused inline / modal editing.
 */
@Composable
fun SettingsTextInputRow(
    title: String,
    subtitle: String? = null,
    value: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    jumpNumber: Int? = null,
    onClick: () -> Unit
) {
    val focused = isFocused.unlessBackPillFocused()
    val shape = RoundedCornerShape(14.dp)
    val bg = if (focused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = focused, cornerRadius = 14.dp)
            .clip(shape)
            .background(bg, shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (focused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.settingsMarquee(focused)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (jumpNumber != null) {
                JumpNumberBadge(
                    digit = jumpNumber,
                    isFocused = focused,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.45f))
                    .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.ifBlank { "Not set" },
                    color = ActiveTheme.accentDeep,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.settingsMarquee(focused)
                )
            }
        }
    }
}

/**
 * Informational display row for read-only system and hardware statistics.
 */
@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false
) {
    val focused = isFocused.unlessBackPillFocused()
    val shape = RoundedCornerShape(14.dp)
    val bg = if (focused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = ActiveTheme.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = ActiveTheme.accentDeep,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f, fill = false)
                .settingsMarquee(focused)
        )
    }
}

/**
 * Action button row (e.g. Reset, Pick, Launch).
 */
@Composable
fun SettingsActionRow(
    title: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    jumpNumber: Int? = null,
    onClick: () -> Unit
) {
    val focused = isFocused.unlessBackPillFocused()
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        focused && isDestructive -> Color(0xFFFFEBEE)
        focused -> ActiveTheme.surfaceTint.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isDestructive -> Color(0xFFD32F2F)
        focused -> ActiveTheme.accentDeep
        else -> ActiveTheme.textPrimary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = focused, cornerRadius = 12.dp)
            .clip(shape)
            .background(bg, shape)
            .border(
                width = 1.dp,
                color = if (focused) Color.Transparent else ActiveTheme.cardBorder.copy(alpha = 0.6f),
                shape = shape
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = if (jumpNumber != null) 22.dp else 0.dp)
        )
        if (jumpNumber != null) {
            JumpNumberBadge(
                digit = jumpNumber,
                isFocused = focused,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
