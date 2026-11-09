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

    @Test
    fun keyPacketEncoding() {
        val buf = ByteArray(24)
        // OP_KEY = 10, a = 523 (KEY_NUMERIC_POUND), b = 25 (holdMs)
        RootTouchInjector.encodePacket(buf, 10, 523, 25, 0, 0, 0)
        assertEquals(0xB7.toByte(), buf[0])
        assertEquals(10, buf[1].toInt())
        assertEquals((523 and 0xFF).toByte(), buf[4])
        assertEquals(((523 shr 8) and 0xFF).toByte(), buf[5])
        assertEquals(25.toByte(), buf[8])
    }

    @Test
    fun pastePacketEncoding() {
        val buf = ByteArray(24)
        // OP_PASTE = 11
        RootTouchInjector.encodePacket(buf, 11, 0, 0, 0, 0, 0)
        assertEquals(0xB7.toByte(), buf[0])
        assertEquals(11, buf[1].toInt())
    }
}
