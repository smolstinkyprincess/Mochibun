package com.crinklecodone.mochibun.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.crinklecodone.mochibun.core.util.MochibunSystemActions
import com.crinklecodone.mochibun.data.prefs.PreferencesManager

/**
 * Keeps MochibunKeyHook (running inside system_server) informed about the Media Volume Lock pref.
 *
 * system_server can't read our SharedPreferences, so the app pushes the value with a broadcast:
 * - once at app start-up,
 * - every time the pref changes (PreferencesManager.setMediaVolumeLockEnabled),
 * - whenever the hook asks for it (ACTION_REQUEST_MEDIA_VOLUME_LOCK), e.g. right after it registers
 *   its receiver.
 * Same channel and signature permission as the cursor-mode sync in VirtualCursorService.
 */
object MediaVolumeLockSync {
    private const val TAG = "MediaVolumeLockSync"

    @Volatile
    private var requestReceiverRegistered = false

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (!requestReceiverRegistered) {
            synchronized(this) {
                if (!requestReceiverRegistered) {
                    registerRequestReceiver(appContext)
                    requestReceiverRegistered = true
                }
            }
        }
        pushCurrent(appContext)
    }

    private fun registerRequestReceiver(appContext: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != MochibunSystemActions.ACTION_REQUEST_MEDIA_VOLUME_LOCK) return
                pushCurrent(appContext)
            }
        }
        // Only system_server (holds INTERNAL_SYSTEM_WINDOW) may ask.
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(MochibunSystemActions.ACTION_REQUEST_MEDIA_VOLUME_LOCK),
            MochibunSystemActions.SYSTEM_SERVER_BROADCAST_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun pushCurrent(context: Context) =
        push(context, PreferencesManager.isMediaVolumeLockEnabled(context))

    fun push(context: Context, enabled: Boolean) {
        val intent = Intent(MochibunSystemActions.ACTION_MEDIA_VOLUME_LOCK_CHANGED)
            .setPackage("android")
            .putExtra(MochibunSystemActions.EXTRA_MEDIA_VOLUME_LOCK_ENABLED, enabled)
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync Media Volume Lock with system_server", e)
        }
    }
}
