package one.crinklecod.mochibun

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import one.crinklecod.mochibun.core.keypad.HOME_FOCUS_MASCOT
import one.crinklecod.mochibun.core.keypad.HomeTransitionTracker
import one.crinklecod.mochibun.core.keypad.LauncherKeypadActions
import one.crinklecod.mochibun.core.keypad.LauncherKeypadState
import one.crinklecod.mochibun.core.keypad.PET_PILL_CHAT
import one.crinklecod.mochibun.core.keypad.PET_PILL_FEED
import one.crinklecod.mochibun.core.keypad.PET_PILL_PET
import one.crinklecod.mochibun.core.keypad.T9InputState
import one.crinklecod.mochibun.core.keypad.petPillTargetIndex
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.LauncherKeyHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/** Records the pet pill calls the key handler makes. */
private class RecordingActions : LauncherKeypadActions {
    val selections = mutableListOf<Int>()
    val activated = mutableListOf<Int>()
    var opened = 0
    var closed = 0
    var interactions = 0
    var dismissed = 0

    override fun setDrawerOpen(open: Boolean) {}
    override fun setSelectedDrawerIndex(index: Int) {}
    override fun setHomeFocusArea(area: Int) {}
    override fun setSelectedFavoriteIndex(index: Int) {}
    override fun setSavedFavoriteIndexOnHome(index: Int) {}
    override fun setPendingTargetPage(page: Int?) {}
    override fun setCenterKeyState(state: Int) {}
    override fun openContextActionForCurrentFocus() {}
    override fun openFolder(folderId: String) {}
    override fun openLauncherMenu() {}
    override fun openSpeedDial(digit: Int) {}
    override fun dismissModal() { dismissed++ }
    override fun openPetPill() { opened++ }
    override fun setPetPillSelection(index: Int) { selections += index }
    override fun activatePetPillOption(index: Int) { activated += index }
    override fun closePetPill() { closed++ }
    override fun onPetPillInteraction() { interactions++ }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LauncherKeyHandlerTest {

    private lateinit var context: Context
    private lateinit var actions: RecordingActions
    private lateinit var handler: LauncherKeyHandler

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        PreferencesManager.setDpadWrapAround(context, false)
        actions = RecordingActions()
        handler = LauncherKeyHandler(
            context = context,
            // Already cancelled: page-scroll launches from the Home key are dropped instead of run
            scope = CoroutineScope(Job().apply { cancel() }),
            pagerState = PagerState(currentPage = 1) { 3 },
            listState = LazyListState(),
            homeTracker = HomeTransitionTracker().apply { wasActive = true },
            t9State = T9InputState(),
            actions = actions
        )
    }

    private fun state(pillOpen: Boolean = true, selected: Int = PET_PILL_FEED, centerKeyState: Int = 0) =
        LauncherKeypadState(
            anyModalOpen = pillOpen,
            drawerOpen = false,
            isHomeScreenActive = !pillOpen,
            isPageTransitioning = false,
            pendingTargetPage = null,
            favoriteItemCount = 0,
            selectedFavoriteIndex = 0,
            savedFavoriteIndexOnHome = 0,
            homeFocusArea = HOME_FOCUS_MASCOT,
            centerKeyState = centerKeyState,
            selectedDrawerIndex = 0,
            visibleApps = emptyList(),
            letterStarts = emptyMap(),
            isPetPillOpen = pillOpen,
            petPillSelectedIndex = selected
        )

    private fun send(keyCode: Int, action: Int, state: LauncherKeypadState, repeat: Int = 0): Boolean =
        handler.handleKeyEvent(ComposeKeyEvent(KeyEvent(0L, 0L, action, keyCode, repeat)), state)

    private fun press(keyCode: Int, state: LauncherKeypadState): Pair<Boolean, Boolean> =
        send(keyCode, KeyEvent.ACTION_DOWN, state) to send(keyCode, KeyEvent.ACTION_UP, state)

    // ── Opening ─────────────────────────────────────────────────────────────

    @Test
    fun centerOnFocusedMascot_opensThePill() {
        val home = state(pillOpen = false)
        assertTrue(send(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, home))
        assertTrue(send(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, home.copy(centerKeyState = 1)))
        assertEquals(1, actions.opened)
        assertTrue(actions.activated.isEmpty())
    }

    // ── Navigation ──────────────────────────────────────────────────────────

    @Test
    fun leftRight_moveBetweenOptions() {
        assertEquals(true to true, press(KeyEvent.KEYCODE_DPAD_RIGHT, state(selected = PET_PILL_FEED)))
        press(KeyEvent.KEYCODE_DPAD_RIGHT, state(selected = PET_PILL_PET))
        press(KeyEvent.KEYCODE_DPAD_LEFT, state(selected = PET_PILL_CHAT))
        assertEquals(listOf(PET_PILL_PET, PET_PILL_CHAT, PET_PILL_PET), actions.selections)
        assertTrue(actions.activated.isEmpty())
    }

    @Test
    fun edges_stopWithoutWrap_andWrapWhenEnabled() {
        press(KeyEvent.KEYCODE_DPAD_LEFT, state(selected = PET_PILL_FEED))
        press(KeyEvent.KEYCODE_DPAD_RIGHT, state(selected = PET_PILL_CHAT))
        assertTrue(actions.selections.isEmpty())

        PreferencesManager.setDpadWrapAround(context, true)
        press(KeyEvent.KEYCODE_DPAD_LEFT, state(selected = PET_PILL_FEED))
        press(KeyEvent.KEYCODE_DPAD_RIGHT, state(selected = PET_PILL_CHAT))
        assertEquals(listOf(PET_PILL_CHAT, PET_PILL_FEED), actions.selections)
    }

    @Test
    fun petPillTargetIndex_math() {
        assertEquals(1, petPillTargetIndex(0, 1, wrapAround = false))
        assertNull(petPillTargetIndex(0, -1, wrapAround = false))
        assertNull(petPillTargetIndex(2, 1, wrapAround = false))
        assertEquals(2, petPillTargetIndex(0, -1, wrapAround = true))
        assertEquals(0, petPillTargetIndex(2, 1, wrapAround = true))
    }

    // ── Activation ──────────────────────────────────────────────────────────

    @Test
    fun centerRelease_activatesTheSelectedOption() {
        val s = state(selected = PET_PILL_PET)
        assertTrue(send(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN, s))
        assertTrue(actions.activated.isEmpty()) // runs on release, not press
        assertTrue(send(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, s))
        assertEquals(listOf(PET_PILL_PET), actions.activated)

        press(KeyEvent.KEYCODE_ENTER, state(selected = PET_PILL_CHAT))
        assertEquals(listOf(PET_PILL_PET, PET_PILL_CHAT), actions.activated)
    }

    @Test
    fun centerReleaseWithoutPress_doesNothing() {
        // e.g. the release of the press that opened the pill
        assertTrue(send(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, state()))
        assertTrue(actions.activated.isEmpty())
    }

    @Test
    fun keypad123_runOptionsDirectly() {
        send(KeyEvent.KEYCODE_1, KeyEvent.ACTION_DOWN, state())
        send(KeyEvent.KEYCODE_3, KeyEvent.ACTION_DOWN, state())
        send(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN, state())
        send(KeyEvent.KEYCODE_2, KeyEvent.ACTION_DOWN, state(), repeat = 1) // held: no repeat feeding
        assertEquals(listOf(PET_PILL_FEED, PET_PILL_CHAT, PET_PILL_PET), actions.activated)
        assertEquals(listOf(PET_PILL_FEED, PET_PILL_CHAT, PET_PILL_PET), actions.selections)
    }

    // ── Dismissal & isolation ───────────────────────────────────────────────

    @Test
    fun back_closesThePillOnRelease() {
        assertTrue(send(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, state()))
        assertEquals(0, actions.closed)
        assertTrue(send(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, state()))
        assertEquals(1, actions.closed)

        press(KeyEvent.KEYCODE_ESCAPE, state())
        assertEquals(2, actions.closed)
    }

    @Test
    fun homeKey_dismissesThePill() {
        assertTrue(send(KeyEvent.KEYCODE_HOME, KeyEvent.ACTION_DOWN, state()))
        assertEquals(1, actions.dismissed)
    }

    @Test
    fun otherHomeKeys_areSwallowed_volumePassesThrough() {
        for (key in listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_MENU
        )) {
            assertEquals("key $key", true to true, press(key, state()))
        }
        assertTrue(actions.selections.isEmpty())
        assertTrue(actions.activated.isEmpty())
        assertEquals(0, actions.closed)
        assertEquals(0, actions.opened)

        assertFalse(send(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, state()))
    }

    @Test
    fun everyHandledKey_restartsTheAutoCloseTimer() {
        press(KeyEvent.KEYCODE_DPAD_RIGHT, state())
        press(KeyEvent.KEYCODE_DPAD_DOWN, state())
        press(KeyEvent.KEYCODE_DPAD_CENTER, state())
        send(KeyEvent.KEYCODE_1, KeyEvent.ACTION_DOWN, state())
        assertEquals(4, actions.interactions)
    }
}
