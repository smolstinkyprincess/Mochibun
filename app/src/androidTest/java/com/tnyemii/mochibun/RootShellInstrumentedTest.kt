package com.tnyemii.mochibun

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootShellInstrumentedTest {

    @Test
    fun testSuBinaryPresence() {
        val present = RootShell.isSuBinaryPresent()
        println("MOCHIBUN_TEST: su binary present = $present")
        assertTrue("su binary should be present on rooted device", present)
    }

    @Test
    fun testRootRequest() = runBlocking {
        println("MOCHIBUN_TEST: Requesting root access...")
        val status = RootShell.requestRoot()
        println("MOCHIBUN_TEST: Root status = $status")
        assertNotNull(status)
    }
}
