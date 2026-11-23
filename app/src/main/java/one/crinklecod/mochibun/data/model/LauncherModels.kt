package one.crinklecod.mochibun.data.model

import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.URLEncoder

enum class AppDrawerSortMode(val label: String, val icon: String, val toastName: String) {
    ALPHABETICAL("A-Z", "🔤", "Alphabetical (A-Z)"),
    NEWEST("Newest", "✨", "Recently Installed"),
    MOST_USED("Used", "🔥", "Most Used");

    fun next(): AppDrawerSortMode {
        val entries = values()
        return entries[(ordinal + 1) % entries.size]
    }
}

internal fun j2mePackageKey(documentUri: String): String =
    "j2me://${URLEncoder.encode(documentUri, "UTF-8")}"

@Immutable
data class LauncherApp(
    val resolveInfo: ResolveInfo? = null,
    val name: String,
    val iconBitmap: Bitmap,
    val installTime: Long = 0L,
    val j2meDocumentUri: String? = null
) {
    val isJ2meGame: Boolean = j2meDocumentUri != null
    val packageName: String = resolveInfo?.activityInfo?.packageName
        ?: j2meDocumentUri?.let(::j2mePackageKey)
        ?: ""
    val activityName: String = resolveInfo?.activityInfo?.name ?: "j2me"
    val componentKey: String = if (j2meDocumentUri != null) {
        j2mePackageKey(j2meDocumentUri)
    } else {
        "$packageName/$activityName"
    }
    val favoriteKey: String = "app:$packageName"
    val lowercaseName: String = name.lowercase()
    val imageBitmap: ImageBitmap = iconBitmap.asImageBitmap()
}
