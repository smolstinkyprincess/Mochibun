package com.crinklecodone.mochibun.core.system

import android.content.ComponentCallbacks2

/**
 * How much the launcher gives back at each onTrimMemory level. Tiers are cumulative.
 *
 * The launcher is shown after every app exit, so TRIM_MEMORY_UI_HIDDEN fires constantly; it only
 * drops caches nobody sees on the way back home. Coil trims its own memory cache through its own
 * ComponentCallbacks2, so nothing here touches it.
 *
 * While the accessibility services are bound, the system holds this process at a visible OOM adj
 * (verified on device: adj 100 with another app on top), so it never receives the background levels
 * (BACKGROUND / MODERATE / COMPLETE) and is not a low-memory-killer candidate. Under real pressure it
 * gets TRIM_MEMORY_RUNNING_CRITICAL instead, which is when the drawer is released if nobody is
 * looking at it: the memory goes to the app the user is in. COMPLETE still covers setups with the
 * services disabled.
 */
enum class MemoryTrimTier {
    NONE,

    /** UI hidden, or critically low while visible: drop icon picker thumbnails. */
    PICKER,

    /** In the background LRU: also drop the notification app-metadata cache. */
    BACKGROUND,

    /**
     * Critically low with the launcher hidden, or next in line for the low-memory killer: keep only
     * home-favorite icons and drop the drawer list and parsed icon pack. They reload when the
     * launcher is next shown.
     */
    RELEASE_DRAWER
}

fun memoryTrimTier(level: Int, launcherVisible: Boolean): MemoryTrimTier = when {
    level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryTrimTier.RELEASE_DRAWER
    level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryTrimTier.BACKGROUND
    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL && !launcherVisible -> MemoryTrimTier.RELEASE_DRAWER
    level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> MemoryTrimTier.PICKER
    else -> MemoryTrimTier.NONE
}
