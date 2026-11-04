package com.crinklecodone.mochibun.ui.dialogs

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Call inside a Dialog's content: the dialog window shrinks above the keyboard strip
 * (MochibunImeService) instead of being covered by it.
 */
@Composable
fun ResizeWithKeyboard() {
    val view = LocalView.current
    DisposableEffect(view) {
        @Suppress("DEPRECATION") // ADJUST_RESIZE is still how a dialog window resizes on API 31
        (view.parent as? DialogWindowProvider)?.window
            ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { }
    }
}
