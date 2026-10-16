package com.tnyemii.mochibun

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

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
        "/sbin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    /**
     * Quickly checks whether a su binary exists in PATH or standard system directories.
     * Does NOT execute su or trigger a Magisk authorization prompt.
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
     * Explicitly requests root privileges through Magisk by executing `su` with `id`.
     * Magisk will display its normal authorization dialog if not previously granted or denied.
     * Runs off the main thread with a 25-second timeout to allow the user time to respond.
     */
    suspend fun requestRoot(): RootStatus = withContext(Dispatchers.IO) {
        if (!isSuBinaryPresent()) {
            return@withContext RootStatus.NotAvailable
        }

        try {
            val result = withTimeoutOrNull(25_000L) {
                runSuInternal("id")
            } ?: return@withContext RootStatus.Error("Root authorization request timed out")

            if (result.isSuccess) {
                val idOutput = result.stdout.firstOrNull { it.contains("uid=") }
                    ?: result.stdout.joinToString(" ")
                if (idOutput.contains("uid=0")) {
                    RootStatus.Granted(idOutput)
                } else {
                    RootStatus.Denied("Command ran with non-root UID: $idOutput")
                }
            } else {
                val err = result.stderr.joinToString(" ").ifBlank {
                    result.stdout.joinToString(" ").ifBlank { "Exit code ${result.exitCode}" }
                }
                RootStatus.Denied(err)
            }
        } catch (e: Exception) {
            RootStatus.Error(e.message ?: "Failed to execute su")
        }
    }

    /**
     * Executes a single command as root.
     * Never executes on the main thread.
     */
    suspend fun execute(command: String, timeoutMs: Long = 10_000L): RootResult = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(timeoutMs) {
                runSuInternal(command)
            } ?: RootResult(-1, emptyList(), listOf("Command timed out after ${timeoutMs}ms"))
        } catch (e: Exception) {
            RootResult(-1, emptyList(), listOf(e.message ?: "Execution error"))
        }
    }

    private fun runSuInternal(command: String): RootResult {
        var process: Process? = null
        val stdoutList = mutableListOf<String>()
        val stderrList = mutableListOf<String>()

        try {
            process = ProcessBuilder("su")
                .redirectErrorStream(false)
                .start()

            // Write command to stdin and exit cleanly
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(command)
                writer.newLine()
                writer.write("exit")
                writer.newLine()
                writer.flush()
            }

            // Read stdout and stderr concurrently to prevent pipe buffer deadlock
            val outThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                        lines.forEach { synchronized(stdoutList) { stdoutList.add(it) } }
                    }
                } catch (_: Exception) {}
            }

            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                        lines.forEach { synchronized(stderrList) { stderrList.add(it) } }
                    }
                } catch (_: Exception) {}
            }

            outThread.start()
            errThread.start()

            // Wait for process to complete
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return RootResult(-1, stdoutList, listOf("Process timed out"))
            }

            outThread.join(2000)
            errThread.join(2000)

            return RootResult(process.exitValue(), stdoutList, stderrList)
        } catch (e: Exception) {
            return RootResult(-1, stdoutList, listOf(e.message ?: "Process failure"))
        } finally {
            try {
                process?.inputStream?.close()
                process?.outputStream?.close()
                process?.errorStream?.close()
                process?.destroy()
            } catch (_: Exception) {}
        }
    }
}
