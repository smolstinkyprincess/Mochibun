package one.crinklecod.mochibun

import one.crinklecod.mochibun.ui.screens.SETTINGS_HUB_DESTINATIONS
import one.crinklecod.mochibun.ui.screens.settings.APPEARANCE_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.BACKUP_RESTORE_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.CURSOR_HARDWARE_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.CURSOR_MOTION_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.KEYBOARD_PREDICTIVE_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.KEYBOARD_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.MAGISK_ROOT_LSPOSED_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.MAX_JUMP_ROWS
import one.crinklecod.mochibun.ui.screens.settings.SOUND_FEEDBACK_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.appearanceGreetingTickerRowIds
import one.crinklecod.mochibun.ui.screens.settings.appearanceTypographyBordersRowIds
import one.crinklecod.mochibun.ui.screens.settings.appearanceWallpaperDecoRowIds
import one.crinklecod.mochibun.ui.screens.settings.appearanceWidgetsCardsRowIds
import one.crinklecod.mochibun.ui.screens.settings.behaviorRowIds
import one.crinklecod.mochibun.ui.screens.settings.clamshellLidRowIds
import one.crinklecod.mochibun.ui.screens.settings.permissionsRowIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the 1-9 numeric keypad jump-key limit ([MAX_JUMP_ROWS] = 9) across all static
 * settings configuration screens using their worst-case (all conditional rows visible) counts.
 *
 * Excludes non-focusable static info screens (`AboutMochibunScreen`) and dynamic scroll lists
 * (`LidLedLibrary` and `LidAppLedRulesScreen`, whose length depends on user animations/apps).
 */
class SettingsRowLimitsTest {

    /**
     * Temporary allow-list of legacy screens pending Phase 3 splits. Entries must be removed as
     * each Phase 3 step completes; keeping a screen under limit here fails so the list shrinks.
     */
    private val temporaryAllowList = emptySet<String>()

    private fun staticConfigurationRowIds(): Map<String, List<String>> = linkedMapOf(
        "SettingsHubRootScreen" to SETTINGS_HUB_DESTINATIONS.map { it.javaClass.simpleName },
        "SoundFeedbackSettingsScreen" to SOUND_FEEDBACK_ROW_IDS,
        "BehaviorSettingsScreen" to behaviorRowIds(hasCreateFolder = true),
        "ClamshellLidSettingsScreen" to clamshellLidRowIds(
            showRootOptions = true,
            showScreenOffReset = true,
            showLidLedRows = true
        ),
        "BackupRestoreSettingsScreen" to BACKUP_RESTORE_ROW_IDS,
        "KeyboardSettingsScreen" to KEYBOARD_ROW_IDS,
        "KeyboardPredictiveTextSettingsScreen" to KEYBOARD_PREDICTIVE_ROW_IDS,
        "CursorHardwareSettingsScreen" to CURSOR_HARDWARE_ROW_IDS,
        "CursorMotionSettingsScreen" to CURSOR_MOTION_ROW_IDS,
        "AppearanceSettingsScreen" to APPEARANCE_ROW_IDS,
        "AppearanceTypographyBordersSettingsScreen" to appearanceTypographyBordersRowIds(
            hasBundleFont = true,
            showRootOptions = true,
            isSystemFontInstalled = true,
            hasCustomBorder = true
        ),
        "AppearanceGreetingTickerSettingsScreen" to appearanceGreetingTickerRowIds(
            isSubDisplayTickerEnabled = true
        ),
        "AppearanceWallpaperDecoSettingsScreen" to appearanceWallpaperDecoRowIds(
            hasCustomWallpaper = true,
            hasCustomMascot = true
        ),
        "AppearanceWidgetsCardsSettingsScreen" to appearanceWidgetsCardsRowIds(
            isSystemMonitorEnabled = true
        ),
        "PermissionsRootSettingsScreen" to permissionsRowIds(showRootOptions = true),
        "MagiskRootLsposedSettingsScreen" to MAGISK_ROOT_LSPOSED_ROW_IDS
    )

    @Test
    fun maxJumpRowsIsNine() {
        assertEquals(9, MAX_JUMP_ROWS)
    }

    @Test
    fun staticSettingsScreensStayWithinNineRows() {
        val screens = staticConfigurationRowIds()
        for ((name, rowIds) in screens) {
            assertEquals("$name row IDs must be unique", rowIds.size, rowIds.toSet().size)
            if (name in temporaryAllowList) {
                assertTrue(
                    "$name is now <= $MAX_JUMP_ROWS rows (${rowIds.size}); remove it from temporaryAllowList",
                    rowIds.size > MAX_JUMP_ROWS
                )
            } else {
                assertTrue(
                    "$name has ${rowIds.size} rows, exceeding MAX_JUMP_ROWS ($MAX_JUMP_ROWS)",
                    rowIds.size in 1..MAX_JUMP_ROWS
                )
            }
        }
        assertTrue(
            "temporaryAllowList contains unknown screen(s): ${temporaryAllowList - screens.keys}",
            screens.keys.containsAll(temporaryAllowList)
        )
    }
}
