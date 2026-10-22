package com.crinklecodone.mochibun.data.model

import androidx.compose.runtime.Immutable

/**
 * Unified representation of an item that can occupy a slot in the favorites bar.
 * Supports arbitrary interleaving of favorite apps and folders.
 */
@Immutable
sealed interface FavoriteSlotItem {
    val key: String

    @Immutable
    data class App(val app: LauncherApp) : FavoriteSlotItem {
        override val key: String get() = "app:${app.packageName}"
    }

    @Immutable
    data class Folder(val folder: FavoriteFolder) : FavoriteSlotItem {
        override val key: String get() = "folder:${folder.id}"
    }
}
