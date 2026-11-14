package one.crinklecod.mochibun.core.system

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import one.crinklecod.mochibun.data.prefs.PreferencesManager

/**
 * Is the LSPosed [one.crinklecod.mochibun.xposed.MochibunKeyHook] loaded this boot?
 *
 * The hook writes a heartbeat (elapsed realtime + boot count) to Settings.Global when
 * system_server loads it. A heartbeat from an earlier boot, or a globally disabled hook, counts
 * as inactive. Used by the optional accessibility service so it only filters keys when the hook
 * can't, and by settings to pick non-root defaults.
 */
object KeyHookStatus {

    /** Matches the hook's own 2 s settings cache: cheap enough to call from a key path. */
    private const val CACHE_MS = 2_000L

    @Volatile private var cachedActive = false
    @Volatile private var cachedAtMs = 0L

    /** Pure heartbeat check, unit-tested. */
    fun isHeartbeatValid(
        heartbeatElapsed: Long,
        nowElapsed: Long,
        heartbeatBootCount: Int,
        currentBootCount: Int,
        globallyEnabled: Boolean
    ): Boolean = globallyEnabled &&
        heartbeatElapsed > 0L &&
        heartbeatElapsed <= nowElapsed &&
        currentBootCount >= 0 &&
        heartbeatBootCount == currentBootCount

    /** Cached for [CACHE_MS]; safe to call on every key event. */
    fun isActive(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (cachedAtMs != 0L && now - cachedAtMs < CACHE_MS) return cachedActive
        val active = readActive(context)
        cachedActive = active
        cachedAtMs = now
        return active
    }

    /** Uncached read of the heartbeat from Settings.Global. */
    fun readActive(context: Context): Boolean {
        val resolver = context.contentResolver
        return try {
            isHeartbeatValid(
                heartbeatElapsed = Settings.Global.getLong(
                    resolver, PreferencesManager.GLOBAL_KEY_HOOK_ALIVE_ELAPSED, 0L
                ),
                nowElapsed = SystemClock.elapsedRealtime(),
                heartbeatBootCount = Settings.Global.getInt(
                    resolver, PreferencesManager.GLOBAL_KEY_HOOK_ALIVE_BOOT_COUNT, -1
                ),
                currentBootCount = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1),
                globallyEnabled = Settings.Global.getInt(
                    resolver, PreferencesManager.GLOBAL_HOOK_KEY_ENABLED, 1
                ) == 1
            )
        } catch (_: Exception) {
            false
        }
    }
}
