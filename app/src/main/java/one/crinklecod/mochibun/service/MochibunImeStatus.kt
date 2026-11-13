package one.crinklecod.mochibun.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import one.crinklecod.mochibun.core.shell.RootResult
import one.crinklecod.mochibun.core.system.RootShell

/** Enabled / default checks and setup shortcuts for [MochibunImeService]. Settings UI only. */
object MochibunImeStatus {

    enum class State { NOT_ENABLED, ENABLED, DEFAULT }

    fun imeId(context: Context): String =
        ComponentName(context, MochibunImeService::class.java).flattenToShortString()

    fun state(context: Context): State {
        val id = imeId(context)
        val current = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        } catch (_: Exception) { null }
        if (current != null && ComponentName.unflattenFromString(current) == ComponentName.unflattenFromString(id)) {
            return State.DEFAULT
        }
        val imm = context.getSystemService(InputMethodManager::class.java)
        val enabled = try {
            imm?.enabledInputMethodList?.any { it.packageName == context.packageName } == true
        } catch (_: Exception) { false }
        return if (enabled) State.ENABLED else State.NOT_ENABLED
    }

    /** Android's on-screen keyboard list, where the user can turn the keyboard on. */
    fun openKeyboardSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }

    /** The system "Choose input method" picker. Needs a focused window of ours to show. */
    fun showPicker(context: Context) {
        try {
            context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        } catch (_: Exception) {}
    }

    /** Enables and selects the keyboard in one step. Root only. */
    suspend fun enableAndSelectViaRoot(context: Context): RootResult {
        val id = imeId(context)
        return RootShell.execute("ime enable $id && ime set $id", ROOT_TIMEOUT_MS)
    }

    private const val ROOT_TIMEOUT_MS = 5_000L
}
