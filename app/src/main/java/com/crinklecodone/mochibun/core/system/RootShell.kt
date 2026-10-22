/**
 * Privileged root execution bridge for the Chinoe AT-M120.
 * Backed by libsu's persistent daemon to eliminate process-fork latency,
 * thread-allocation GC pressure, and pipe buffer deadlocks.
 */
package com.crinklecodone.mochibun.core.system

import com.topjohnwu.superuser.NoShellException
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class RootStatus {
    object Unknown : RootStatus()
    object NotAvailable : RootStatus()
    object Requesting : RootStatus()
    data class Granted(val identity: String) : RootStatus()
    data class Denied(val message: String) : RootStatus()
    data class Error(val message: String) : RootStatus()
}

data class RootResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>
) {
    val isSuccess: Boolean = exitCode == 0
}

object RootShell {

    // Common standard paths where su binary may reside
    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/system_ext/bin/su",
        "/sbin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    /**
     * Quickly checks whether a su binary exists in PATH or standard system directories.
     * Does NOT execute su or trigger a Magisk/KernelSU authorization prompt.
     */
    fun isSuBinaryPresent(): Boolean {
        for (path in SU_PATHS) {
            if (File(path).exists()) return true
        }
        val pathEnv = System.getenv("PATH") ?: return false
        for (dir in pathEnv.split(":")) {
            val file = File(dir, "su")
            if (file.exists() && file.canExecute()) return true
        }
        return false
    }

    /**
     * Checks whether root access has already been granted and the shell daemon is root.
     * Non-blocking and does NOT prompt the user if ungranted.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (!isSuBinaryPresent()) return@withContext false
        val cachedStatus = Shell.isAppGrantedRoot()
        if (cachedStatus != null) return@withContext cachedStatus

        try {
            Shell.getShell().isRoot
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Explicitly requests root privileges. If not yet granted or denied, Magisk/KernelSU
     * will display its superuser permission prompt.
     * Dispatched off the main thread (Dispatchers.IO) to prevent UI stalls.
     */
    suspend fun requestRoot(): RootStatus = withContext(Dispatchers.IO) {
        if (!isSuBinaryPresent()) {
            return@withContext RootStatus.NotAvailable
        }

        try {
            // Shell.getShell() lazily connects or spawns the root daemon
            val shell = Shell.getShell()
            if (!shell.isRoot) {
                return@withContext RootStatus.Denied("Shell spawned without root privileges (UID != 0)")
            }

            val result = Shell.cmd("id").exec()
            if (result.isSuccess) {
                val idOutput = result.out.firstOrNull { it.contains("uid=") }
                    ?: result.out.joinToString(" ")
                if (idOutput.contains("uid=0")) {
                    RootStatus.Granted(idOutput)
                } else {
                    RootStatus.Denied("Command ran with non-root UID: $idOutput")
                }
            } else {
                val err = result.err.joinToString(" ").ifBlank {
                    result.out.joinToString(" ").ifBlank { "Exit code ${result.code}" }
                }
                RootStatus.Denied(err)
            }
        } catch (e: NoShellException) {
            RootStatus.Denied(e.message ?: "Superuser access denied")
        } catch (e: Exception) {
            RootStatus.Error(e.message ?: "Failed to acquire root shell")
        }
    }

    /**
     * Executes a command synchronously on Dispatchers.IO via the persistent root daemon.
     * Returns a [RootResult] containing exitCode, stdout, and stderr.
     */
    suspend fun execute(command: String): RootResult = withContext(Dispatchers.IO) {
        if (!isSuBinaryPresent()) {
            return@withContext RootResult(-1, emptyList(), listOf("su binary not found"))
        }

        try {
            val result = Shell.cmd(command).exec()
            RootResult(
                exitCode = result.code,
                stdout = result.out,
                stderr = result.err
            )
        } catch (e: Exception) {
            RootResult(-1, emptyList(), listOf(e.message ?: "Execution error"))
        }
    }

    /**
     * Convenience helper to execute a command and collect standard output lines.
     * Returns an empty list if execution fails or root is unavailable.
     */
    suspend fun executeWithOutput(command: String): List<String> = withContext(Dispatchers.IO) {
        if (!isSuBinaryPresent()) return@withContext emptyList()
        try {
            Shell.cmd(command).exec().out
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Submits a command asynchronously to the persistent daemon without waiting for its output.
     * Ideal for fire-and-forget operations off the main thread.
     */
    fun submit(command: String) {
        try {
            Shell.cmd(command).submit { /* no-op */ }
        } catch (_: Exception) {
            // Silently ignore if root shell cannot be acquired
        }
    }
}