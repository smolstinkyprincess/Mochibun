package one.crinklecod.mochibun

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import android.view.KeyEvent
import one.crinklecod.mochibun.core.keypad.WheelMode
import one.crinklecod.mochibun.core.keypad.isWheelModeToggleKey
import one.crinklecod.mochibun.core.keypad.wheelPetalForDigit
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.BackupRestoreManager
import one.crinklecod.mochibun.data.repository.ClipRingEntry
import one.crinklecod.mochibun.data.repository.ClipboardRing
import one.crinklecod.mochibun.data.repository.ClipboardRingRepository
import one.crinklecod.mochibun.ui.dialogs.KaomojiWheelState
import one.crinklecod.mochibun.ui.dialogs.WheelStage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** Clipboard ring rules, pinned Quick Snippets persistence / backup, and wheel mode keys. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ClipboardRingTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        PreferencesManager.getPrefs(context).edit().clear().commit()
        PreferencesManager.reload(context)
        ClipboardRingRepository.clearForTest()
    }

    @Test
    fun recopiedTextMovesToFrontWithoutDuplicates() {
        val ring = ClipboardRing()
        ring.offer("a")
        ring.offer("b")
        ring.offer("c")
        assertTrue(ring.offer("  a \n"))
        assertEquals(listOf("a", "c", "b"), ring.items)
        // Copying the newest entry again changes nothing
        assertFalse(ring.offer("a"))
        assertEquals(listOf("a", "c", "b"), ring.items)
    }

    @Test
    fun ringKeepsOnlyTheEightNewest() {
        val ring = ClipboardRing()
        for (i in 1..10) ring.offer("clip $i")
        assertEquals(ClipboardRingRepository.MAX_RECENT_CLIPS, ring.items.size)
        assertEquals("clip 10", ring.items.first())
        assertEquals("clip 3", ring.items.last())
    }

    @Test
    fun blankAndSensitiveClipsAreSkipped_longClipsCapped() {
        val ring = ClipboardRing()
        assertFalse(ring.offer(null))
        assertFalse(ring.offer(""))
        assertFalse(ring.offer(" \n\t "))
        assertFalse(ring.offer("hunter2", sensitive = true))
        assertTrue(ring.items.isEmpty())

        ring.offer("x".repeat(500))
        assertEquals(ClipboardRingRepository.MAX_CLIP_CHARS, ring.items.single().length)
    }

    @Test
    fun sensitiveFlagOnClipDescriptionIsHonoured() {
        val plain = ClipDescription("text", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN))
        assertFalse(ClipboardRingRepository.isSensitive(plain))
        assertFalse(ClipboardRingRepository.isSensitive(null))

        val secret = ClipDescription("otp", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)).apply {
            extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        assertTrue(ClipboardRingRepository.isSensitive(secret))
    }

    @Test
    fun clipboardSyncCapturesPlainTextButNeverSensitiveClips() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)

        val otp = ClipData.newPlainText("otp", "123456").apply {
            description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(otp)
        ClipboardRingRepository.syncFromClipboard(context)
        assertTrue(ClipboardRingRepository.recentClips.value.isEmpty())

        // Robolectric's clock is frozen; real copies are always milliseconds apart
        ShadowSystemClock.advanceBy(Duration.ofMillis(5))
        clipboard.setPrimaryClip(ClipData.newPlainText("note", "  see you at 7 ♡  "))
        ClipboardRingRepository.syncFromClipboard(context)
        assertEquals(listOf("see you at 7 ♡"), ClipboardRingRepository.recentClips.value)

        // An unchanged clipboard is not read again on the next resume
        ClipboardRingRepository.clearForTestKeepingStamp()
        ClipboardRingRepository.syncFromClipboard(context)
        assertTrue(ClipboardRingRepository.recentClips.value.isEmpty())
    }

    @Test
    fun mergedRingPutsPinnedFirstAndCapsAtEightPetals() {
        val pinned = listOf("me@example.com", "1-2-3 Mochi St")
        val recent = (1..10).map { "clip $it" } + "me@example.com"
        val ring = ClipboardRingRepository.mergeRing(pinned, recent)

        assertEquals(ClipboardRingRepository.MAX_RING_PETALS, ring.size)
        assertEquals(ClipRingEntry("me@example.com", pinned = true), ring[0])
        assertEquals(ClipRingEntry("1-2-3 Mochi St", pinned = true), ring[1])
        assertEquals(ClipRingEntry("clip 1", pinned = false), ring[2])
        // A recent copy of a pinned snippet doesn't take a second petal
        assertEquals(1, ring.count { it.text == "me@example.com" })
    }

    @Test
    fun snippetsAreSanitizedPersistedAndCapped() {
        PreferencesManager.setQuickSnippets(
            context,
            listOf("  a  ", "", "a", "b".repeat(400)) + (1..10).map { "s$it" }
        )
        val saved = PreferencesManager.quickSnippets.value
        assertEquals(PreferencesManager.QUICK_SNIPPET_MAX_COUNT, saved.size)
        assertEquals("a", saved[0])
        assertEquals(PreferencesManager.QUICK_SNIPPET_MAX_CHARS, saved[1].length)

        PreferencesManager.reload(context)
        assertEquals(saved, PreferencesManager.quickSnippets.value)
    }

    @Test
    fun backupRoundTripsSnippetsButNeverRecentClips() {
        PreferencesManager.setQuickSnippets(context, listOf("me@example.com", "xoxo, emi ♡"))
        ClipboardRingRepository.offer("private copied text")

        val json = BackupRestoreManager.createBackupJson(context)
        assertFalse(json.contains("private copied text"))
        assertEquals(2, JSONObject(json).getJSONArray(BackupRestoreManager.KEY_QUICK_SNIPPETS).length())

        PreferencesManager.getPrefs(context).edit().clear().commit()
        PreferencesManager.reload(context)
        assertTrue(PreferencesManager.quickSnippets.value.isEmpty())

        BackupRestoreManager.restoreBackupJson(context, json).getOrThrow()
        assertEquals(listOf("me@example.com", "xoxo, emi ♡"), PreferencesManager.quickSnippets.value)
    }

    @Test
    fun backupWithoutSnippetsKeepsCurrent_tooManyIsRejected() {
        PreferencesManager.setQuickSnippets(context, listOf("keep me"))
        val old = JSONObject(BackupRestoreManager.createBackupJson(context)).apply {
            remove(BackupRestoreManager.KEY_QUICK_SNIPPETS)
        }
        BackupRestoreManager.restoreBackupJson(context, old.toString()).getOrThrow()
        assertEquals(listOf("keep me"), PreferencesManager.quickSnippets.value)

        val bad = JSONObject(BackupRestoreManager.createBackupJson(context)).apply {
            put(BackupRestoreManager.KEY_QUICK_SNIPPETS, JSONArray((1..7).map { "s$it" }))
        }
        assertTrue(BackupRestoreManager.restoreBackupJson(context, bad.toString()).isFailure)
        assertEquals(listOf("keep me"), PreferencesManager.quickSnippets.value)
    }

    @Test
    fun wheelTogglesModeAndPicksClipPetals() {
        val entries = listOf(ClipRingEntry("pinned", true), ClipRingEntry("recent", false))
        val state = KaomojiWheelState(clipEntriesProvider = { entries })
        assertEquals(WheelMode.KAOMOJI, state.mode)
        assertEquals(state.currentKaomoji, state.currentText)

        state.toggleMode()
        assertEquals(WheelMode.CLIPBOARD_SNIPPETS, state.mode)
        assertEquals(WheelStage.CATEGORY, state.stage)
        assertEquals("pinned", state.currentText)
        assertTrue(state.lockClip())
        assertEquals(WheelStage.ITEM, state.stage)
        assertTrue(state.stepClip(1))
        assertEquals("recent", state.currentText)
        assertTrue(state.stepClip(1)) // wraps
        assertEquals("pinned", state.currentText)
        assertFalse(state.selectClip(5))
        assertTrue(state.selectClip(1))
        assertEquals("recent", state.currentText)
        state.returnToCategories()
        assertEquals(WheelStage.CATEGORY, state.stage)
        assertEquals(1, state.clipWedgeIndex)

        state.toggleMode()
        assertEquals(WheelMode.KAOMOJI, state.mode)
        assertEquals(WheelStage.CATEGORY, state.stage)
    }

    @Test
    fun wheelKeyHelpers() {
        assertTrue(isWheelModeToggleKey(KeyEvent.KEYCODE_STAR))
        assertTrue(isWheelModeToggleKey(KeyEvent.KEYCODE_0))
        assertFalse(isWheelModeToggleKey(KeyEvent.KEYCODE_POUND))
        assertEquals(0, wheelPetalForDigit(KeyEvent.KEYCODE_1))
        assertEquals(7, wheelPetalForDigit(KeyEvent.KEYCODE_8))
        assertEquals(3, wheelPetalForDigit(KeyEvent.KEYCODE_NUMPAD_4))
        assertEquals(null, wheelPetalForDigit(KeyEvent.KEYCODE_9))
        assertEquals(null, wheelPetalForDigit(KeyEvent.KEYCODE_0))
    }
}
