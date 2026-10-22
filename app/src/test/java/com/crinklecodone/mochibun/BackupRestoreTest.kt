package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.data.repository.BackupRestoreManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreTest {

    @Test
    fun testGenerateBackupFileNameFormat() {
        val fileName = BackupRestoreManager.generateBackupFileName()
        assertTrue("Filename should start with mochibun_backup_", fileName.startsWith("mochibun_backup_"))
        assertTrue("Filename should end with .json", fileName.endsWith(".json"))
        // Format: mochibun_backup_yyyyMMdd_HHmmss.json -> length is 16 + 15 + 5 = 36
        assertEquals(36, fileName.length)
    }

    @Test
    fun testCurrentBackupVersion() {
        assertEquals(1, BackupRestoreManager.CURRENT_BACKUP_VERSION)
    }
}
