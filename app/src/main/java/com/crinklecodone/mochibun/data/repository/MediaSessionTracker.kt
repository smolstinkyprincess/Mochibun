package com.crinklecodone.mochibun.data.repository

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.crinklecodone.mochibun.service.MochibunNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MochibunMediaState(
    val isPlaying: Boolean = false,
    val title: String = "No Track",
    val artist: String = "Unknown Artist",
    val artwork: Bitmap? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val lastPositionUpdateTime: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val packageName: String? = null,
    val sessionActivity: PendingIntent? = null
) {
    val progressFraction: Float
        get() {
            if (durationMs <= 0L) return 0f
            val currentPos = if (isPlaying && lastPositionUpdateTime > 0L) {
                val elapsed = SystemClock.elapsedRealtime() - lastPositionUpdateTime
                (positionMs + (elapsed * playbackSpeed)).toLong()
            } else {
                positionMs
            }
            return (currentPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * Singleton media session tracker that monitors active media sessions via Android's
 * MediaSessionManager and NotificationListenerService on Android 12 (API 31).
 */
object MediaSessionTracker {
    private const val TAG = "MediaSessionTracker"

    private val _mediaState = MutableStateFlow(MochibunMediaState())
    val mediaState: StateFlow<MochibunMediaState> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null
    private var sessionManager: MediaSessionManager? = null
    private var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var notificationListenerComponent: ComponentName? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            mainHandler.post {
                updateFromController(activeController)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            mainHandler.post {
                updateFromController(activeController)
            }
        }

        override fun onSessionDestroyed() {
            mainHandler.post {
                activeController = null
                _mediaState.value = MochibunMediaState()
            }
        }
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val myComponent = ComponentName(context, MochibunNotificationListenerService::class.java).flattenToString()
        return enabledListeners.contains(myComponent) || enabledListeners.contains(context.packageName)
    }

    fun startTracking(context: Context) {
        appContext = context.applicationContext
        val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        sessionManager = mgr
        val comp = ComponentName(context, MochibunNotificationListenerService::class.java)
        notificationListenerComponent = comp

        if (activeSessionsListener == null) {
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                mainHandler.post {
                    handleActiveSessions(controllers)
                }
            }
            activeSessionsListener = listener
            tryRegisterSessions(context, mgr, listener, comp)
        } else {
            // Re-query current active sessions
            tryRegisterSessions(context, mgr, activeSessionsListener!!, comp)
        }
    }

    private fun tryRegisterSessions(
        context: Context,
        mgr: MediaSessionManager,
        listener: MediaSessionManager.OnActiveSessionsChangedListener,
        comp: ComponentName
    ) {
        var registered = false

        try {
            mgr.removeOnActiveSessionsChangedListener(listener)
        } catch (_: Exception) {}

        try {
            mgr.addOnActiveSessionsChangedListener(listener, comp)
            val initialSessions = mgr.getActiveSessions(comp)
            handleActiveSessions(initialSessions)
            registered = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification listener permission not active: ${e.message}")
        }

        if (!registered) {
            try {
                mgr.addOnActiveSessionsChangedListener(listener, null)
                val initialSessions = mgr.getActiveSessions(null)
                handleActiveSessions(initialSessions)
                registered = true
            } catch (e: SecurityException) {
                Log.w(TAG, "MEDIA_CONTENT_CONTROL permission not active: ${e.message}")
            }
        }
    }

    private fun handleActiveSessions(controllers: List<MediaController>?) {
        val list = controllers ?: emptyList()
        // Prioritize a controller that is actively playing
        val playingController = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()

        if (activeController?.sessionToken != playingController?.sessionToken) {
            try {
                activeController?.unregisterCallback(controllerCallback)
            } catch (_: Exception) {}

            activeController = playingController
            try {
                activeController?.registerCallback(controllerCallback)
            } catch (_: Exception) {}
        }

        updateFromController(activeController)
    }

    fun updateFromController(controller: MediaController?) {
        if (controller == null) {
            _mediaState.value = MochibunMediaState()
            return
        }

        val pbState = controller.playbackState
        val isPlaying = pbState?.state == PlaybackState.STATE_PLAYING
        val position = pbState?.position ?: 0L
        val speed = pbState?.playbackSpeed ?: 1.0f
        val updateTime = pbState?.lastPositionUpdateTime ?: 0L

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString()
            ?: "No Track"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: "Unknown Artist"
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        var artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        if (artwork == null && appContext != null) {
            val artUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            if (artUri != null) {
                try {
                    val uri = Uri.parse(artUri)
                    val stream = appContext?.contentResolver?.openInputStream(uri)
                    artwork = BitmapFactory.decodeStream(stream)
                    stream?.close()
                } catch (_: Exception) {}
            }
        }

        _mediaState.value = MochibunMediaState(
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            artwork = artwork,
            durationMs = duration,
            positionMs = position,
            lastPositionUpdateTime = updateTime,
            playbackSpeed = speed,
            packageName = controller.packageName,
            sessionActivity = controller.sessionActivity
        )
    }

    fun skipToPrevious() {
        try {
            activeController?.transportControls?.skipToPrevious()
        } catch (e: Exception) {
            Log.e(TAG, "Error skipToPrevious", e)
        }
    }

    fun togglePlayPause() {
        try {
            val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                activeController?.transportControls?.pause()
            } else {
                activeController?.transportControls?.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error togglePlayPause", e)
        }
    }

    fun skipToNext() {
        try {
            activeController?.transportControls?.skipToNext()
        } catch (e: Exception) {
            Log.e(TAG, "Error skipToNext", e)
        }
    }

    fun openCurrentMediaApp(context: Context) {
        val state = _mediaState.value
        try {
            if (state.sessionActivity != null) {
                state.sessionActivity.send()
            } else if (state.packageName != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(state.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch media app", e)
        }
    }

    fun stopTracking() {
        try {
            activeController?.unregisterCallback(controllerCallback)
        } catch (_: Exception) {}
        activeController = null

        activeSessionsListener?.let { listener ->
            try {
                sessionManager?.removeOnActiveSessionsChangedListener(listener)
            } catch (_: Exception) {}
        }
        activeSessionsListener = null
        sessionManager = null
    }

    fun onNotificationListenerServiceConnected(service: MochibunNotificationListenerService) {
        mainHandler.post {
            val mgr = sessionManager ?: (service.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager)
            if (mgr != null) {
                sessionManager = mgr
                val listener = activeSessionsListener ?: MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                    mainHandler.post { handleActiveSessions(controllers) }
                }.also { activeSessionsListener = it }
                val comp = ComponentName(service, MochibunNotificationListenerService::class.java)
                notificationListenerComponent = comp
                tryRegisterSessions(service, mgr, listener, comp)
            }
        }
    }

    fun onNotificationListenerServiceDisconnected() {
        // Handled cleanly
    }
}
