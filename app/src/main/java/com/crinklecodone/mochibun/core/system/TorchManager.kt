package com.crinklecodone.mochibun.core.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Lightweight hardware flashlight coordinator for the Chinoe AT-M120 physical keypad shortcuts.
 */
object TorchManager {

    private const val TAG = "TorchManager"

    @Volatile
    var isTorchOn: Boolean = false
        private set

    /**
     * Toggles the physical rear flashlight/torch state.
     * Returns true if torch is now active, false if turned off, or null if no flash is available.
     */
    fun toggleTorch(context: Context): Boolean? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return null

        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return null

            val targetState = !isTorchOn
            cameraManager.setTorchMode(cameraId, targetState)
            isTorchOn = targetState
            Log.d(TAG, "Toggled torch on camera $cameraId: $targetState")
            targetState
        } catch (e: Exception) {
            Log.e(TAG, "Failed toggling flashlight/torch", e)
            null
        }
    }
}
