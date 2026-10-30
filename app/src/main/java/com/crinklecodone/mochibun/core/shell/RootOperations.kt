package com.crinklecodone.mochibun.core.shell

/**
 * Superuser authorization and daemon state for privileged platform operations.
 */
sealed interface RootStatus {
    data object Unknown : RootStatus
    data object NotAvailable : RootStatus
    data object Requesting : RootStatus
    data class Granted(val identity: String) : RootStatus
    data class Denied(val message: String) : RootStatus
    data class Error(val message: String, val cause: Throwable? = null) : RootStatus
}

/**
 * Structured result model representing the outcome of a privileged shell execution.
 */
data class RootResult(
    val exitCode: Int,
    val stdout: List<String> = emptyList(),
    val stderr: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = exitCode == 0
    val outputString: String get() = stdout.joinToString("\n")
    val errorString: String get() = stderr.joinToString("\n")

    companion object {
        fun failure(message: String, exitCode: Int = -1): RootResult =
            RootResult(exitCode = exitCode, stderr = listOf(message))
    }
}

/**
 * Clean interface isolating privileged shell execution away from UI components.
 */
interface RootOperations {
    fun isSuBinaryPresent(): Boolean

    /** Cached grant state without blocking: true/false once known, null while undetermined. */
    fun isGranted(): Boolean?

    /** Starts acquiring the privileged shell in the background so later calls are fast. */
    fun warmUp()

    /** Blocks until the privileged shell is up; true when it is running as root. Off the main thread only. */
    fun acquireBlocking(): Boolean

    suspend fun requestRoot(): RootStatus
    suspend fun execute(command: String, timeoutMs: Long = 10_000L): RootResult

    /** Runs [commands] in order in the same shell session. Off the main thread only. */
    fun executeBlocking(vararg commands: String): RootResult
    fun submit(command: String, onResult: ((RootResult) -> Unit)? = null)
}
