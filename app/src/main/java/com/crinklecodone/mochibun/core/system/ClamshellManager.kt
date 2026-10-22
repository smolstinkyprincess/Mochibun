/**
 * Centralized Clamshell / Flip Lid Lifecycle Manager for the ALT Mive Style Folder (AT-M120).
 * Detects physical folder open/close transitions and display power states, coordinating
 * immediate low-power suspension across services and tactile focus recovery on wake.
 */
package com.crinklecodone.mochibun.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

interface ClamshellListener {
    fun onLidClosed() {}
    fun onLidOpened() {}
}

object ClamshellManager {
    private const val TAG = "ClamshellManager"

    private val _isLidClosed = MutableStateFlow(false)
    val isLidClosed: StateFlow<Boolean> = _isLidClosed.asStateFlow()

    private val listeners = CopyOnWriteArrayList<ClamshellListener>()
    private var isReceiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen OFF event received (clamshell closed or display sleep)")
                    updateState(isClosed = true)
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen ON event received (clamshell opened or display wake)")
                    val isKeyboardHidden = context?.resources?.configuration?.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES
                    updateState(isClosed = isKeyboardHidden)
                }
            }
        }
    }

    /**
     * Initializes global broadcast receiver for screen on/off events.
     * Safe to call multiple times; idempotently registers once per process.
     */
    fun init(context: Context) {
        if (isReceiverRegistered) return
        val appContext = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            appContext.registerReceiver(screenStateReceiver, filter)
            isReceiverRegistered = true

            // Initialize baseline state
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isInteractive = powerManager?.isInteractive ?: true
            val isKeyboardHidden = appContext.resources.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES
            _isLidClosed.value = !isInteractive || isKeyboardHidden
            Log.d(TAG, "ClamshellManager initialized. Initial closed state: ${_isLidClosed.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screen state receiver: ${e.message}")
        }
    }

    /**
     * Called from Activity or Service onConfigurationChanged.
     */
    fun onConfigurationChanged(newConfig: Configuration) {
        if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
            updateState(isClosed = true)
        } else if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
            updateState(isClosed = false)
        }
    }

    /**
     * Inspects whether the clamshell is currently closed or the screen is non-interactive.
     */
    fun isLidClosed(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = powerManager?.isInteractive ?: true
        val isKeyboardHidden = context.resources.configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES
        return !isInteractive || isKeyboardHidden || _isLidClosed.value
    }

    fun addListener(listener: ClamshellListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: ClamshellListener) {
        listeners.remove(listener)
    }

    private fun updateState(isClosed: Boolean) {
        val previous = _isLidClosed.value
        _isLidClosed.value = isClosed
        if (previous != isClosed) {
            Log.d(TAG, "Clamshell transition: isClosed=$isClosed (previous=$previous)")
            if (isClosed) {
                listeners.forEach { it.onLidClosed() }
            } else {
                listeners.forEach { it.onLidOpened() }
            }
        }
    }
}
