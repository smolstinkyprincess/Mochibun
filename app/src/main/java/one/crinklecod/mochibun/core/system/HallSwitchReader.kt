package one.crinklecod.mochibun.core.system

import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * Streams the AT-M120 hall switch as root.
 *
 * The keypad driver reports the flip as KEY_SLEEP / KEY_WAKEUP on /dev/input/event0. A `getevent`
 * runs as a background job inside libsu's existing root shell (no second su request, so no extra
 * Magisk prompt or toast) and writes into an anonymous pipe this process created. SELinux blocks
 * app-created FIFOs here (priv_app has no fifo_file create on its data dir), so root opens the
 * pipe's write end through /proc/<pid>/fd. One thread blocks on the read end and only wakes for
 * lid events. Closing the flip while the display has already timed out still arrives here,
 * even though no screen broadcast fires for it.
 *
 * getevent's output is filtered through `grep` in the root shell so ordinary keypad presses (same
 * device node) never reach this process. If the app dies, grep gets SIGPIPE on its next lid line
 * and exits, then getevent on its next write. Each launch also kills any leftover getevent reading
 * event0 first, which ends its grep via EOF.
 */
internal class HallSwitchReader(
    private val onHallChanged: (closed: Boolean) -> Unit
) {
    @Volatile
    var isStreaming = false
        private set

    @Volatile
    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread({
            try {
                run()
            } finally {
                isRunning = false
            }
        }, "mochibun-hall").apply { isDaemon = true }.start()
    }

    private fun run() {
        if (!RootShell.isSuBinaryPresent()) return
        if (!RootShell.acquireBlocking()) {
            Log.i(TAG, "No root; lid state falls back to open")
            return
        }

        readHallProperty(expectClosed = null)?.let(onHallChanged)

        var restarts = 0
        while (restarts <= MAX_RESTARTS) {
            val readEnd = launchGetevent() ?: return
            try {
                ParcelFileDescriptor.AutoCloseInputStream(readEnd).bufferedReader().use { reader ->
                    isStreaming = true
                    while (true) {
                        val line = reader.readLine() ?: break
                        parseHallLine(line)?.let(onHallChanged)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hall stream failed", e)
            } finally {
                isStreaming = false
            }
            restarts++
            Log.w(TAG, "Hall stream ended (restart $restarts/$MAX_RESTARTS)")
            Thread.sleep(RESTART_DELAY_MS)
            // Re-seed in case a transition was missed while the stream was down
            readHallProperty(expectClosed = null)?.let(onHallChanged)
        }
    }

    /**
     * Starts getevent writing into a new pipe and returns the read end, or null on failure.
     * The background job opens the write end itself (a failed redirect on `exec` could take down
     * the shared root shell); the foreground waits until the job's stdout is our pipe, and only
     * then is this process's copy of the write end closed, so EOF means getevent has exited.
     */
    private fun launchGetevent(): ParcelFileDescriptor? {
        val (readEnd, writeEnd) = try {
            ParcelFileDescriptor.createPipe()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create hall pipe", e)
            return null
        }
        try {
            val target = "/proc/${Process.myPid()}/fd/${writeEnd.fd}"
            val result = RootShell.executeBlocking(
                "for p in \$(pidof getevent); do " +
                    "grep -q '$INPUT_DEVICE' /proc/\$p/cmdline 2>/dev/null && kill \$p; done",
                // event0 is shared with mtk-kpd, so unfiltered getevent would wake this
                // process on every keypress system-wide. Filter in the root shell so only lid
                // lines cross the pipe. $! is grep's pid (last stage of the background pipeline).
                "getevent -ql $INPUT_DEVICE < /dev/null 2>/dev/null | " +
                    "grep --line-buffered -E '$HALL_KEY_PATTERN' > $target 2>/dev/null &",
                "g=\$!; want=\$(readlink $target); i=0",
                "while [ \$i -lt 50 ] && [ \"\$(readlink /proc/\$g/fd/1 2>/dev/null)\" != \"\$want\" ]; " +
                    "do sleep 0.02; i=\$((i+1)); done",
                "[ \"\$(readlink /proc/\$g/fd/1 2>/dev/null)\" = \"\$want\" ] && echo hall-stream-ok"
            )
            if (result.stdout.lastOrNull() == "hall-stream-ok") return readEnd
            Log.w(TAG, "getevent did not attach to the hall pipe: ${result.errorString}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start hall stream", e)
        } finally {
            writeEnd.close()
        }
        readEnd.close()
        return null
    }

    companion object {
        private const val TAG = "HallSwitchReader"
        private const val INPUT_DEVICE = "/dev/input/event0" // mtk-kpd
        private const val HALL_KEY_PATTERN = "KEY_SLEEP|KEY_WAKEUP"
        private const val MAX_RESTARTS = 3
        private const val RESTART_DELAY_MS = 2_000L

        /**
         * Maps one `getevent -ql` line to a hall transition: true = closed, false = opened.
         * Releases and every other key are ignored.
         */
        @VisibleForTesting
        internal fun parseHallLine(line: String): Boolean? {
            if (!line.contains("DOWN")) return null
            return when {
                line.contains("KEY_SLEEP") -> true
                line.contains("KEY_WAKEUP") -> false
                else -> null
            }
        }

        /**
         * Reads [ClamshellManager.PROP_HALL_STATE] through the root shell. Must run off the main
         * thread. When [expectClosed] is set, polls briefly for that value, because the vendor
         * property can trail the screen broadcast by a few hundred ms.
         */
        fun readHallProperty(expectClosed: Boolean?): Boolean? {
            val want = when (expectClosed) {
                true -> "1"
                false -> "0"
                null -> ""
            }
            val prop = ClamshellManager.PROP_HALL_STATE
            val cmd = if (want.isEmpty()) {
                "getprop $prop"
            } else {
                "i=0; v=; while [ \$i -lt 4 ]; do v=\$(getprop $prop); " +
                    "[ \"\$v\" = \"$want\" ] && break; sleep 0.15; i=\$((i+1)); done; echo \$v"
            }
            return try {
                when (RootShell.executeBlocking(cmd).stdout.lastOrNull()?.trim()) {
                    "1" -> true
                    "0" -> false
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
