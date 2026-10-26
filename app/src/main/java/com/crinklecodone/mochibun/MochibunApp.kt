package com.crinklecodone.mochibun

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.memory.MemoryCache
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.core.system.ClamshellManager
import com.crinklecodone.mochibun.core.system.LidLedManager
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.ExternalThemeManager
import com.crinklecodone.mochibun.data.repository.TypographyManager
import com.topjohnwu.superuser.Shell

class MochibunApp : Application(), ImageLoaderFactory {

    init {
        // Set flags to enable verbose logging in debug builds
        Shell.enableVerboseLogging = BuildConfig.DEBUG

        // Establish default builder configuration
        // Process creation is lazy: no su process or thread is spawned
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(10))
    }

    override fun onCreate() {
        super.onCreate()
        ClamshellManager.init(this)
        PreferencesManager.init(this)
        ExternalThemeManager.init(this)
        TypographyManager.scanFonts(this)
        LidLedManager.init(this)
        SoundEffectManager.init(this)
        com.crinklecodone.mochibun.core.system.RootAccessibilityWatchdog.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    // Low memory budget (~1.8 GB RAM / MT6761): limit cache to 5% of app memory
                    .maxSizePercent(0.05)
                    .build()
            }
            .allowHardware(false) // Use software bitmap allocation to prevent GPU memory exhaustion
            .crossfade(false)
            .build()
    }
}
