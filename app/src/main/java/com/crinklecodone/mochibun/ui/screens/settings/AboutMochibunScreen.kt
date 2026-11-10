package com.crinklecodone.mochibun.ui.screens.settings

import android.os.Build
import android.app.ActivityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.BuildConfig
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.ui.components.MascotMood
import com.crinklecodone.mochibun.ui.components.MochibunMascot
import com.crinklecodone.mochibun.ui.components.mascotTintColor
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme

/**
 * Tier 4: About & System Sub-Screen
 */
@Composable
internal fun AboutMochibunSubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val memoryInfo = remember(context) {
        ActivityManager.MemoryInfo().also { info ->
            (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager)
                .getMemoryInfo(info)
        }
    }
    val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
    val usedRamMb = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)

    val dm = context.resources.displayMetrics
    val displayResolution = remember(configuration, dm.widthPixels, dm.heightPixels) {
        "${dm.widthPixels} × ${dm.heightPixels} px (${configuration.densityDpi} dpi)"
    }

    val deviceDisplay = remember { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }

    val osVersion = remember {
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    val primaryAbi = remember {
        Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
    }

    val isTargetDevice = remember {
        Build.MODEL.contains("M120", ignoreCase = true) || Build.DEVICE.contains("M120", ignoreCase = true)
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "About Mochibun",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onBack()
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 64.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                val theme = LocalLauncherTheme.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MochibunMascot(
                        mood = MascotMood.DEFAULT,
                        size = 78.dp,
                        themeTintColor = theme.mascotTintColor
                    )
                }
                SettingsCard(categoryTitle = "Application Info") {
                    SettingsInfoRow(label = "Application", value = "Mochibun")
                    SettingsInfoRow(label = "Version", value = BuildConfig.VERSION_NAME)
                    SettingsInfoRow(label = "Package Name", value = context.packageName)
                }
            }

            item {
                SettingsCard(categoryTitle = "Hardware Specifications") {
                    SettingsInfoRow(label = "Model / Device", value = deviceDisplay)
                    if (isTargetDevice) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ActiveTheme.surfaceTint.copy(alpha = 0.25f))
                                .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🌸",
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = "Home sweet home! Running on native M120 hardware ✨",
                                    color = ActiveTheme.accentDeep,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    SettingsInfoRow(label = "OS Version", value = osVersion)
                    SettingsInfoRow(label = "Architecture", value = primaryAbi)
                    SettingsInfoRow(label = "Display", value = displayResolution)
                    SettingsInfoRow(label = "Memory", value = "$usedRamMb MB used / $totalRamMb MB total")
                }
            }

            item {
                SettingsCard(categoryTitle = "Open Source, License & Credits") {
                    SettingsInfoRow(label = "License", value = "Apache 2.0 License")
                    SettingsInfoRow(label = "Root Bridge", value = "libsu:core daemon")
                    SettingsInfoRow(label = "Image Loader", value = "Coil (GIF & Bitmap)")
                    SettingsInfoRow(label = "YAML Parser", value = "kaml")
                    SettingsInfoRow(label = "Typography", value = "MaruMinya (マルミーニャ)")
                }
            }
        }
    }
}
