package com.crinklecodone.mochibun.core.system

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter

/**
 * High-performance, non-blocking touch injector for the Chinoe AT-M120.
 *
 * Architecture & Fallback Role:
 * While AccessibilityService.dispatchGesture() is the primary unprivileged tap handler,
 * certain system dialogs, overlay windows, and secured surfaces reject accessibility gestures.
 * This injector maintains a persistent background interactive root shell ('su') that streams
 * 'input tap x y' commands over stdin. Keeping a single root process alive drops tap latency
 * from ~180 ms (spawning a new su binary per tap) down to <15 ms, completely isolated on Dispatchers.IO.
 */
object RootTouchInjector {
    private const val TAG = "RootTouchInjector"

    private val injectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tapQueue = Channel<Pair<Int, Int>>(capacity = Channel.BUFFERED)

    @Volatile
    private var suProcess: Process? = null
    @Volatile
    private var suWriter: BufferedWriter? = null
    @Volatile
    private var isQueueWorkerRunning = false

    private val lock = Any()

    init {
        startWorkerIfNeeded()
    }

    private fun startWorkerIfNeeded() {
        synchronized(lock) {
            if (isQueueWorkerRunning) return
            isQueueWorkerRunning = true
            injectorScope.launch {
                for ((x, y) in tapQueue) {
                    performTapInternal(x, y)
                }
            }
        }
    }

    /**
     * Queues a tap asynchronously without waiting or blocking the caller.
     */
    fun tapAsync(x: Int, y: Int) {
        startWorkerIfNeeded()
        tapQueue.trySend(Pair(x, y))
    }

    /**
     * Executes tap as a suspending call on IO thread.
     */
    suspend fun injectTap(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        performTapInternal(x, y)
    }

    private fun ensureShell(): Boolean {
        if (!RootShell.isSuBinaryPresent()) return false

        val currentWriter = suWriter
        val currentProcess = suProcess
        if (currentProcess != null && currentWriter != null) {
            try {
                // If exitValue doesn't throw IllegalThreadStateException, process is dead
                currentProcess.exitValue()
            } catch (_: IllegalThreadStateException) {
                // Still running
                return true
            }
        }

        // Needs initialization or restart
        synchronized(lock) {
            try {
                suWriter?.close()
                suProcess?.destroy()
            } catch (_: java.io.IOException) {}

            return try {
                val process = ProcessBuilder("su")
                    .redirectErrorStream(true)
                    .start()
                val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
                suProcess = process
                suWriter = writer
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start interactive su process: ${e.message}")
                suProcess = null
                suWriter = null
                false
            }
        }
    }

    private fun performTapInternal(x: Int, y: Int): Boolean {
        // First try interactive shell for minimal latency (<15ms)
        if (ensureShell()) {
            try {
                val writer = suWriter
                if (writer != null) {
                    writer.write("input tap $x $y\n")
                    writer.flush()
                    return true
                }
            } catch (e: java.io.IOException) {
                Log.w(TAG, "Interactive shell write failed, falling back to direct exec: ${e.message}")
                synchronized(lock) {
                    try {
                        suWriter?.close()
                        suProcess?.destroy()
                    } catch (_: java.io.IOException) { }
                    suWriter = null
                    suProcess = null
                }
            }
        }

        // Direct process fallback
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
            proc.waitFor()
            proc.exitValue() == 0
        } catch (e: Exception) {
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            Log.e(TAG, "Fallback root tap failed: ${e.message}")
            false
        }
    }

    /**
     * Cleans up open root processes and worker scopes on service teardown.
     */
    fun shutdown() {
        synchronized(lock) {
            tapQueue.close()
            try {
                suWriter?.write("exit\n")
                suWriter?.flush()
                suWriter?.close()
            } catch (_: java.io.IOException) {}
            try {
                suProcess?.destroy()
            } catch (_: Exception) {}
            suWriter = null
            suProcess = null
            isQueueWorkerRunning = false
        }
        injectorScope.cancel()
    }
}
