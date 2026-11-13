package one.crinklecod.mochibun

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MochibunImeKeyRoutingTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var previousInputMethod: String? = null
    private var activity: ImeRoutingTestActivity? = null

    @Before
    fun selectMochibunIme() {
        previousInputMethod = runShellCommand("settings get secure default_input_method")
            .trim()
            .takeUnless { it.isBlank() || it == "null" }
        runShellCommand("ime enable $IME_ID")
        runShellCommand("ime set $IME_ID")

        val intent = Intent(instrumentation.context, ImeRoutingTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity = instrumentation.startActivitySync(intent) as ImeRoutingTestActivity
        instrumentation.runOnMainSync {
            activity?.let { testActivity ->
                testActivity.editor.requestFocus()
                (testActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(testActivity.editor, 0)
            }
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(250)
    }

    @After
    fun restoreInputMethod() {
        activity?.finish()
        previousInputMethod?.let { runShellCommand("ime set $it") }
    }

    @Test
    fun digitThenCenterCommitsFirstCandidateAndConsumesRelease() {
        val editor = requireNotNull(activity).editor

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_2)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals("a", editor.text.toString())
        assertTrue(editor.committedText.contains("a"))
        assertFalse(
            "Consumed key events must not be forwarded to the editor",
            editor.forwardedKeyEvents.any {
                it.keyCode == KeyEvent.KEYCODE_2 ||
                    it.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
            }
        )
    }

    @Test
    fun deleteWithActiveCandidateCancelsWithoutDeletingEditorText() {
        val editor = requireNotNull(activity).editor

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_2)
        assertTrue(editor.composingText.contains("a"))

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)

        assertEquals(0, editor.deleteSurroundingTextCalls)
        assertEquals(0, editor.deleteSurroundingTextInCodePointsCalls)
        assertEquals("", editor.composingText.last())
        assertEquals("", editor.text.toString())
    }

    @Test
    fun deleteWithoutCandidateDelegatesOneCharacterDeletion() {
        val editor = requireNotNull(activity).editor
        instrumentation.runOnMainSync { editor.setText("x") }

        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)

        assertEquals(1, editor.deleteSurroundingTextCalls)
        assertEquals(1 to 0, editor.lastDeleteSurroundingText)
        assertEquals(0, editor.deleteSurroundingTextInCodePointsCalls)
        assertEquals("", editor.text.toString())
    }

    private fun runShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }

    private companion object {
        const val IME_ID = "one.crinklecod.mochibun/.service.MochibunImeService"
    }
}

class ImeRoutingTestActivity : Activity() {
    lateinit var editor: RecordingEditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor = RecordingEditText(this)
        editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        editor.imeOptions = EditorInfo.IME_ACTION_NONE
        setContentView(editor)
        editor.requestFocus()
    }
}

class RecordingEditText(context: Context) : EditText(context) {
    val committedText = mutableListOf<String>()
    val composingText = mutableListOf<String>()
    val forwardedKeyEvents = mutableListOf<KeyEvent>()
    var deleteSurroundingTextCalls = 0
        private set
    var deleteSurroundingTextInCodePointsCalls = 0
        private set
    var lastDeleteSurroundingText: Pair<Int, Int>? = null
        private set

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                committedText += text?.toString().orEmpty()
                return super.commitText(text, newCursorPosition)
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                composingText += text?.toString().orEmpty()
                return super.setComposingText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                deleteSurroundingTextCalls++
                lastDeleteSurroundingText = beforeLength to afterLength
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(
                beforeLength: Int,
                afterLength: Int
            ): Boolean {
                deleteSurroundingTextInCodePointsCalls++
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                forwardedKeyEvents += KeyEvent(event)
                return super.sendKeyEvent(event)
            }
        }
    }
}
