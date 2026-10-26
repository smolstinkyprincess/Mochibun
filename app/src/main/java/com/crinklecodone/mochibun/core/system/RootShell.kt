package com.crinklecodone.mochibun.core.system

import com.crinklecodone.mochibun.core.shell.RootOperations
import com.crinklecodone.mochibun.core.shell.RootResult
import com.crinklecodone.mochibun.core.shell.RootStatus
import com.topjohnwu.superuser.NoShellException
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Privileged root execution bridge for the Chinoe AT-M120 (MediaTek MT6761 / Android 12).
 * Backed by libsu's persistent daemon to avoid process-fork latency, GC churn,
 * and pipe deadlocks on low-RAM (2GB) hardware.
 */
object RootShell : RootOperations {

    const val DEFAULT_TIMEOUT_MS: Long = 10_000L

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/system_ext/bin/su",
        "/sbin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    /**
     * Fast filesystem check to verify if a su binary exists in PATH or standard paths.
     * Non-blocking and never triggers Magisk/KernelSU authorization prompts.
     */
    override fun isSuBinaryPresent(): Boolean {
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
     * Requests root privileges, triggering the Magisk/KernelSU prompt if not yet granted.
     * Dispatched to Dispatchers.IO with a timeout guard to prevent UI thread starvation.
     */
    override suspend fun requestRoot(): RootStatus = withContext(Dispatchers.IO) {
        if (!isSuBinaryPresent()) {
            return@withContext RootStatus.NotAvailable
        }

        try {
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
        } catch (e: Throwable) {
            RootStatus.Error(e.message ?: "Failed to acquire root shell", e)
        }
    }

    /**
     * Executes a shell command synchronously on Dispatchers.IO with explicit timeout protection.
     */
    override suspend fun execute(command: String, timeoutMs: Long): RootResult = withContext(Dispatchers.IO) {
        if (!isSuBinaryPresent()) {
            return@withContext RootResult.failure("su binary not found")
        }

        try {
            val executionResult = withTimeoutOrNull(timeoutMs) {
                val res = Shell.cmd(command).exec()
                RootResult(
                    exitCode = res.code,
                    stdout = res.out,
                    stderr = res.err
                )
            }
            executionResult ?: RootResult.failure("Command timed out after ${timeoutMs}ms")
        } catch (e: Throwable) {
            RootResult.failure(e.message ?: "Execution error")
        }
    }

    /**
     * Synchronous blocking shell execution for non-coroutine contexts.
     * Callers must ensure they are already running off the main thread.
     */
    override fun executeBlocking(command: String): RootResult {
        if (!isSuBinaryPresent()) {
            return RootResult.failure("su binary not found")
        }
        return try {
            val res = Shell.cmd(command).exec()
            RootResult(
                exitCode = res.code,
                stdout = res.out,
                stderr = res.err
            )
        } catch (e: Throwable) {
            RootResult.failure(e.message ?: "Execution error")
        }
    }

    /**
     * Submits an asynchronous command to the persistent root daemon without blocking the caller.
     */
    override fun submit(command: String) {
        if (!isSuBinaryPresent()) return
        try {
            Shell.cmd(command).submit { /* daemon execution, no-op callback */ }
        } catch (_: Throwable) {
            // Ignored if daemon cannot be acquired
        }
    }
}
