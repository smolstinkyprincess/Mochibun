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

    // Compatibility aliases for libsu Shell.Result consumers
    val code: Int get() = exitCode
    val out: List<String> get() = stdout
    val err: List<String> get() = stderr

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
    suspend fun isRootAvailable(): Boolean
    suspend fun requestRoot(): RootStatus
    suspend fun execute(command: String, timeoutMs: Long = 10_000L): RootResult
    fun executeBlocking(command: String): RootResult
    suspend fun executeSafe(command: String, timeoutMs: Long = 10_000L): Result<RootResult>
    suspend fun executeWithOutput(command: String, timeoutMs: Long = 10_000L): List<String>
    fun submit(command: String)
}
