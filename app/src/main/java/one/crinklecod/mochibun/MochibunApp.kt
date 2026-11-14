package one.crinklecod.mochibun

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.memory.MemoryCache
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.ClamshellManager
import one.crinklecod.mochibun.core.system.CursorServiceWatchdog
import one.crinklecod.mochibun.core.system.LidLedManager
import one.crinklecod.mochibun.core.system.MediaVolumeLockSync
import one.crinklecod.mochibun.core.system.MemoryTrimTier
import one.crinklecod.mochibun.core.system.memoryTrimTier
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.ExternalThemeManager
import one.crinklecod.mochibun.data.repository.HardwareRemapManager
import one.crinklecod.mochibun.data.repository.IconPackManager
import one.crinklecod.mochibun.data.repository.NotificationTracker
import one.crinklecod.mochibun.data.repository.TypographyManager
import one.crinklecod.mochibun.data.repository.WidgetRepository
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
        val userManager = getSystemService(android.os.UserManager::class.java)
        if (userManager != null && !userManager.isUserUnlocked) {
            registerReceiver(
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                        if (android.content.Intent.ACTION_USER_UNLOCKED == intent?.action) {
                            try {
                                unregisterReceiver(this)
                            } catch (_: Exception) {}
                            initUnlockedServices()
                        }
                    }
                },
                android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED)
            )
        } else {
            initUnlockedServices()
        }
    }

    private fun initUnlockedServices() {
        PreferencesManager.init(this)
        ExternalThemeManager.init(this)
        HardwareRemapManager.init(this)
        TypographyManager.scanFonts(this)
        LidLedManager.init(this)
        SoundEffectManager.init(this)
        WidgetRepository.init(this)
        CursorServiceWatchdog.init(this)
        MediaVolumeLockSync.init(this)
        PreferencesManager.syncSoftKeyNavToGlobal(this)
        one.crinklecod.mochibun.core.system.TorchManager.init(this)
        // VirtualCursorService is started on demand (F4 / cursor toggle) and stops when hidden.
    }

    // Process-wide caches. The drawer list itself is released by MainActivity, which owns the ViewModel.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val tier = memoryTrimTier(level, launcherVisible = one.crinklecod.mochibun.ui.MainActivity.isForeground)
        if (tier >= MemoryTrimTier.PICKER) IconPackManager.trimPickerCache()
        if (tier >= MemoryTrimTier.BACKGROUND) NotificationTracker.clearCache()
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
