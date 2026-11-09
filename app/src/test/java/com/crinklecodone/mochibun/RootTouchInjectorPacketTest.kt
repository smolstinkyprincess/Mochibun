// SPDX-License-Identifier: Apache-2.0
package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.system.RootTouchInjector
import org.junit.Assert.assertEquals
import org.junit.Test

class RootTouchInjectorPacketTest {
    @Test
    fun layoutMatchesNativeSide() {
        val buf = ByteArray(24)
        RootTouchInjector.encodePacket(buf, 5, 1, -2, 3, 4, 258)
        assertEquals(0xB7.toByte(), buf[0])
        assertEquals(5, buf[1].toInt())
        assertEquals(1, buf[4].toInt())                  // a = 1, little-endian
        assertEquals(0xFE.toByte(), buf[8])              // b = -2 -> FE FF FF FF
        assertEquals(0xFF.toByte(), buf[11])
        assertEquals(2, buf[20].toInt())                 // e = 258 -> 02 01 00 00
        assertEquals(1, buf[21].toInt())
    }
}
