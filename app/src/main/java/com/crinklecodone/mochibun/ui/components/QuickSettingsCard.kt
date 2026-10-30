package com.crinklecodone.mochibun.ui.components

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.core.haptic.HapticManager
import com.crinklecodone.mochibun.core.system.RootShell
import com.crinklecodone.mochibun.core.system.TorchManager
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.palette
import kotlinx.coroutines.delay

private const val QUICK_SETTINGS_POLL_MS = 2_500L

/** Bumped after an in-place root toggle so the card re-samples quickly. */
private val rootToggleCount = mutableIntStateOf(0)

/** Pure 2x2 grid navigation + ringer label logic (unit-tested). Layout: 0 1 / 2 3. */
object QuickSettingsNav {
    const val WIFI = 0
    const val BLUETOOTH = 1
    const val TORCH = 2
    const val SOUND = 3

    /** Result of moving inside the grid: a new index, or an exit off an edge. */
    sealed class Move {
        data class To(val index: Int) : Move()
        object Boundary : Move()
        object ExitRight : Move()
        object ExitDown : Move()
    }

    fun left(index: Int): Move = if (index % 2 == 1) Move.To(index - 1) else Move.Boundary
    fun right(index: Int): Move = if (index % 2 == 0) Move.To(index + 1) else Move.ExitRight
    fun up(index: Int): Move = if (index >= 2) Move.To(index - 2) else Move.Boundary
    fun down(index: Int): Move = if (index < 2) Move.To(index + 2) else Move.ExitDown

    fun nextRingerMode(mode: Int): Int = when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
        AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
        else -> AudioManager.RINGER_MODE_NORMAL
    }

    fun ringerLabel(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_VIBRATE -> "📳 Vibrate"
        AudioManager.RINGER_MODE_SILENT -> "🔇 Silent"
        else -> "🔔 Normal"
    }
}

private data class QuickSettingsState(
    val wifiOn: Boolean,
    val bluetoothOn: Boolean,
    val torchOn: Boolean,
    val ringerMode: Int
)

private fun sampleQuickSettings(context: Context): QuickSettingsState {
    val wifiOn = try {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true
    } catch (_: Exception) { false }
    val bluetoothOn = try {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
    } catch (_: Exception) { false }
    val ringer = try {
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode
            ?: AudioManager.RINGER_MODE_NORMAL
    } catch (_: Exception) { AudioManager.RINGER_MODE_NORMAL }
    return QuickSettingsState(wifiOn, bluetoothOn, TorchManager.isTorchOn, ringer)
}

private fun openPanel(context: Context, panelAction: String, fallbackAction: String) {
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    try {
        context.startActivity(Intent(panelAction).addFlags(flags))
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(fallbackAction).addFlags(flags))
        } catch (e: Exception) {
            Log.w("QuickSettingsCard", "No settings activity for $fallbackAction", e)
        }
    }
}

private fun cycleRingerMode(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    try {
        audioManager.ringerMode = QuickSettingsNav.nextRingerMode(audioManager.ringerMode)
    } catch (_: SecurityException) {
        // Silent needs DND access; fall back to the Normal <-> Vibrate cycle
        try {
            audioManager.ringerMode = if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.w("QuickSettingsCard", "Failed to change ringer mode", e)
        }
    }
}

private fun toggleWifiWithRoot(currentState: Boolean) {
    RootShell.submit(if (currentState) "svc wifi disable" else "svc wifi enable")
}

private fun toggleBluetoothWithRoot(currentState: Boolean) {
    // Android 12 cmd first, service call fallback
    RootShell.submit(
        if (currentState) "cmd bluetooth_manager disable || service call bluetooth_manager 8"
        else "cmd bluetooth_manager enable || service call bluetooth_manager 6"
    )
}

/** Runs the toggle/action for a grid index. Shared by D-pad OK and touch. */
fun performQuickSettingsAction(context: Context, index: Int) {
    val useRoot = PreferencesManager.isRootQuickSettingsEnabled(context) && RootShell.isRootAvailable()
    when (index) {
        QuickSettingsNav.WIFI -> if (useRoot) {
            toggleWifiWithRoot(sampleQuickSettings(context).wifiOn)
            rootToggleCount.intValue++
            HapticManager.performBoundaryHaptic(context)
        } else {
            openPanel(context, Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
        }
        QuickSettingsNav.BLUETOOTH -> if (useRoot) {
            toggleBluetoothWithRoot(sampleQuickSettings(context).bluetoothOn)
            rootToggleCount.intValue++
            HapticManager.performBoundaryHaptic(context)
        } else {
            openPanel(context, "android.settings.panel.action.BLUETOOTH", Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        QuickSettingsNav.TORCH -> {
            if (TorchManager.toggleTorch(context) == null) HapticManager.performBoundaryHaptic(context)
        }
        QuickSettingsNav.SOUND -> {
            cycleRingerMode(context)
            HapticManager.performBoundaryHaptic(context)
        }
    }
}

@Composable
fun QuickSettingsCard(
    isActivePage: Boolean,
    focusedIndex: Int,
    onPillClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0
) {
    val context = LocalContext.current
    val palette = LocalLauncherTheme.current.palette
    var state by remember { mutableStateOf(sampleQuickSettings(context)) }

    // Poll only while page 0 is showing; the effect is cancelled when it isn't
    LaunchedEffect(isActivePage, refreshKey) {
        if (!isActivePage) return@LaunchedEffect
        while (true) {
            state = sampleQuickSettings(context)
            delay(QUICK_SETTINGS_POLL_MS)
        }
    }

    // Fast re-sample after a root toggle so the pill flips without waiting for the poll
    val rootToggles = rootToggleCount.intValue
    LaunchedEffect(rootToggles) {
        if (rootToggles == 0) return@LaunchedEffect
        for (wait in longArrayOf(150L, 350L, 700L)) {
            delay(wait)
            state = sampleQuickSettings(context)
        }
    }

    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .background(palette.unfocusedCardBg, shape)
            .border(1.5.dp, palette.outline, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "— ♡ Quick Settings ♡ —",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = palette.deepAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        val pills = listOf(
            "📶 Wi-Fi" to state.wifiOn,
            "ᛒ Bluetooth" to state.bluetoothOn,
            "🔦 Torch" to state.torchOn,
            QuickSettingsNav.ringerLabel(state.ringerMode) to (state.ringerMode == AudioManager.RINGER_MODE_NORMAL)
        )
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0..1) {
                    val index = row * 2 + col
                    val (label, active) = pills[index]
                    val status = if (index == QuickSettingsNav.SOUND) "Profile" else if (active) "On" else "Off"
                    QuickSettingsPill(
                        label = label,
                        status = status,
                        active = active,
                        isFocused = focusedIndex == index,
                        onClick = { onPillClick(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSettingsPill(
    label: String,
    status: String,
    active: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLauncherTheme.current.palette
    val shape = RoundedCornerShape(14.dp)
    val bg = if (active) palette.primary.copy(alpha = 0.35f) else palette.background
    val border = when {
        isFocused -> palette.focusedCardBorder
        active -> palette.primary
        else -> palette.unfocusedCardBorder
    }
    Column(
        modifier = modifier
            .softGlowFocus(isSelected = isFocused, cornerRadius = 14.dp)
            .background(bg, shape)
            .border(if (isFocused) 2.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) palette.deepAccent else palette.textColor,
            maxLines = 1
        )
        Text(
            text = status,
            fontSize = 10.sp,
            color = palette.textColor.copy(alpha = if (active) 0.9f else 0.6f)
        )
    }
}
