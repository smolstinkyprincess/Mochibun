package com.crinklecodone.mochibun.core.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one AppWidgetHost for the Widgets page (page 0).
 *
 * Lifecycle (driven by MainActivity, not by Compose):
 * - `onStart`  -> [startListeningFor]: receive widget updates while the launcher is visible.
 * - `onStop`   -> [stopListeningFor]: stop updates while another app is in front (saves CPU/RAM).
 * - `onDestroy`-> [releaseViews]: drop the host's references to views that hold the Activity.
 *
 * The host itself only keeps the application context, so it can live for the whole process.
 * The [owner] token makes a late `onStop`/`onDestroy` from an old Activity instance harmless
 * when a new instance has already started listening.
 */
class MochibunWidgetHost private constructor(context: Context) : AppWidgetHost(context, HOST_ID) {

    private val _providersVersion = MutableStateFlow(0)

    /** Bumped when widget providers are installed, updated or removed. */
    val providersVersion: StateFlow<Int> = _providersVersion.asStateFlow()

    private var listeningOwner: Any? = null

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView = MochibunWidgetHostView(context)

    override fun onProvidersChanged() {
        _providersVersion.value++
    }

    fun startListeningFor(owner: Any) {
        val wasListening = listeningOwner != null
        listeningOwner = owner
        if (wasListening) return
        try {
            startListening()
        } catch (e: Exception) {
            // DeadSystemException / TransactionTooLargeException: widgets just stay frozen
            Log.w(TAG, "startListening failed", e)
        }
    }

    fun stopListeningFor(owner: Any) {
        if (listeningOwner !== owner) return
        listeningOwner = null
        try {
            stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening failed", e)
        }
    }

    /** Forgets every AppWidgetHostView this host created, so a destroyed Activity can be collected. */
    fun releaseViews(owner: Any) {
        if (listeningOwner != null && listeningOwner !== owner) return
        clearViews()
    }

    /** IDs the system has allocated to this host, or null if AppWidgetService couldn't be reached. */
    fun hostIdsOrNull(): IntArray? = try {
        appWidgetIds
    } catch (e: Exception) {
        Log.w(TAG, "Could not read host widget IDs", e)
        null
    }

    fun deleteAppWidgetIdSafely(appWidgetId: Int) {
        try {
            deleteAppWidgetId(appWidgetId)
        } catch (e: Exception) {
            Log.w(TAG, "deleteAppWidgetId($appWidgetId) failed", e)
        }
    }

    companion object {
        private const val TAG = "MochibunWidgetHost"
        const val HOST_ID = 1024

        @Volatile
        private var instance: MochibunWidgetHost? = null

        fun get(context: Context): MochibunWidgetHost =
            instance ?: synchronized(this) {
                instance ?: MochibunWidgetHost(context.applicationContext).also { instance = it }
            }
    }
}

/** Host view with a Mochibun-styled error state for providers that crash or fail to inflate. */
class MochibunWidgetHostView(context: Context) : AppWidgetHostView(context) {
    override fun getErrorView(): View = TextView(context).apply {
        text = "♡ This widget is napping ♡\nIts app couldn't draw it right now."
        gravity = Gravity.CENTER
        textSize = 12f
        setTextColor(0xFF703350.toInt())
    }
}
