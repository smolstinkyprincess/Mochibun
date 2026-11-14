package one.crinklecod.mochibun.core.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Lightweight hardware flashlight coordinator for the Chinoe AT-M120 physical keypad shortcuts.
 *
 * The torch state follows the system through a [CameraManager.TorchCallback], so toggling from the
 * Quick Settings tile (or any other app) never leaves the `0`-key toggle out of sync.
 */
object TorchManager {

    private const val TAG = "TorchManager"

    @Volatile
    var isTorchOn: Boolean = false
        private set

    @Volatile
    private var cachedCameraId: String? = null

    @Volatile
    private var callbackRegistered = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == cachedCameraId) isTorchOn = enabled
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            // Camera in use by another app: the torch is off as far as we're concerned
            if (cameraId == cachedCameraId) isTorchOn = false
        }
    }

    /** Resolves the flash camera and starts tracking the real torch state. Cheap; safe to repeat. */
    fun init(context: Context) {
        val cameraManager = context.applicationContext
            .getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        ensureTracking(cameraManager)
    }

    private fun ensureTracking(cameraManager: CameraManager) {
        if (cachedCameraId == null) cachedCameraId = findFlashCameraId(cameraManager)
        if (callbackRegistered || cachedCameraId == null) return
        synchronized(this) {
            if (callbackRegistered) return
            try {
                // Fires right away with the current state, then on every change
                cameraManager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
                callbackRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed registering torch callback", e)
            }
        }
    }

    /** Rear (LENS_FACING_BACK) camera with a flash unit, else any camera with a flash. */
    private fun findFlashCameraId(cameraManager: CameraManager): String? = try {
        val withFlash = cameraManager.cameraIdList.filter { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    } catch (e: Exception) {
        Log.e(TAG, "Failed looking up flash camera", e)
        null
    }

    /**
     * Toggles the physical rear flashlight/torch state.
     * Returns true if torch is now active, false if turned off, or null if no flash is available.
     */
    fun toggleTorch(context: Context): Boolean? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return null
        ensureTracking(cameraManager)
        val cameraId = cachedCameraId ?: return null

        return try {
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

    /** Toast text for a [toggleTorch] result. */
    fun toastText(result: Boolean?): String = when (result) {
        true -> "♡ Flashlight On ♡"
        false -> "♡ Flashlight Off ♡"
        null -> "♡ Flashlight unavailable ♡"
    }
}
