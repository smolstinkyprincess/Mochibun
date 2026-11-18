package one.crinklecod.mochibun

import one.crinklecod.mochibun.data.prefs.HomeMascotSize
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.BackupRestoreManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HomeMascotSizeTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetPrefs() {
        PreferencesManager.getPrefs(context).edit().clear().commit()
        PreferencesManager.reload(context)
    }

    @Test
    fun presetsMatchExpectedSizesAndLabels() {
        assertEquals(listOf(HomeMascotSize.SMALL, HomeMascotSize.MEDIUM, HomeMascotSize.LARGE), HomeMascotSize.entries)
        assertEquals(56, HomeMascotSize.SMALL.sizeDp)
        assertEquals(72, HomeMascotSize.MEDIUM.sizeDp)
        assertEquals(88, HomeMascotSize.LARGE.sizeDp)
        assertEquals("Small", HomeMascotSize.SMALL.label)
        assertEquals("Medium", HomeMascotSize.MEDIUM.label)
        assertEquals("Large", HomeMascotSize.LARGE.label)
    }

    @Test
    fun cyclingWrapsInBothDirectionsAndUnknownFallsBackToSmall() {
        assertEquals(HomeMascotSize.MEDIUM, HomeMascotSize.SMALL.next())
        assertEquals(HomeMascotSize.LARGE, HomeMascotSize.MEDIUM.next())
        assertEquals(HomeMascotSize.SMALL, HomeMascotSize.LARGE.next())

        assertEquals(HomeMascotSize.LARGE, HomeMascotSize.SMALL.previous())
        assertEquals(HomeMascotSize.SMALL, HomeMascotSize.MEDIUM.previous())
        assertEquals(HomeMascotSize.MEDIUM, HomeMascotSize.LARGE.previous())

        assertEquals(HomeMascotSize.SMALL, HomeMascotSize.fromId(null))
        assertEquals(HomeMascotSize.SMALL, HomeMascotSize.fromId("giant"))
        assertEquals(HomeMascotSize.LARGE, HomeMascotSize.fromId("large"))
    }

    @Test
    fun preferencePersistsAndReloads() {
        PreferencesManager.init(context)
        assertEquals(HomeMascotSize.SMALL, PreferencesManager.getHomeMascotSize(context))
        assertEquals(HomeMascotSize.SMALL, PreferencesManager.homeMascotSize.value)

        PreferencesManager.setHomeMascotSize(context, HomeMascotSize.LARGE)
        assertEquals(HomeMascotSize.LARGE, PreferencesManager.getHomeMascotSize(context))
        assertEquals(HomeMascotSize.LARGE, PreferencesManager.homeMascotSize.value)

        PreferencesManager.getPrefs(context).edit()
            .putString(PreferencesManager.KEY_HOME_MASCOT_SIZE, HomeMascotSize.MEDIUM.id)
            .commit()
        PreferencesManager.reload(context)
        assertEquals(HomeMascotSize.MEDIUM, PreferencesManager.homeMascotSize.value)
    }

    @Test
    fun backupAndRestoreRoundTripsHomeMascotSize() {
        PreferencesManager.init(context)
        PreferencesManager.setHomeMascotSize(context, HomeMascotSize.LARGE)

        val backupJson = BackupRestoreManager.createBackupJson(context)
        val themeObj = JSONObject(backupJson).getJSONObject("theme")
        assertEquals("large", themeObj.getString("home_mascot_size"))

        PreferencesManager.setHomeMascotSize(context, HomeMascotSize.SMALL)
        BackupRestoreManager.restoreBackupJson(context, backupJson).getOrThrow()

        assertEquals(HomeMascotSize.LARGE, PreferencesManager.getHomeMascotSize(context))
        assertEquals(HomeMascotSize.LARGE, PreferencesManager.homeMascotSize.value)
    }
}
