package one.crinklecod.mochibun.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the [ContextWrapper] chain (Compose's LocalContext, themed wrappers, …) to the hosting
 * [Activity], or returns null when the context isn't backed by one (services, application).
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
