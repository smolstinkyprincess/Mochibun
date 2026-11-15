package one.crinklecod.mochibun

import one.crinklecod.mochibun.pet.PetClock

// Shared helpers for the pet and mascot atlas tests.

/** Controllable time for the pet. */
class FakePetClock(var now: Long, var day: Long, var hour: Int = 12, var boot: Long = now - 3_600_000L) : PetClock {
    override fun nowMs(): Long = now
    override fun epochDay(nowMs: Long): Long = day
    override fun localHour(nowMs: Long): Int = hour
    override fun bootEpochMs(nowMs: Long): Long = boot
}

/** A PNG signature + IHDR header for a [w] x [h] image, padded to [totalBytes]. */
fun fakePng(w: Int, h: Int, totalBytes: Int = 64): ByteArray {
    val bytes = ByteArray(maxOf(totalBytes, 33))
    val header = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte())
    header.copyInto(bytes)
    fun put(at: Int, v: Int) {
        bytes[at] = (v ushr 24).toByte(); bytes[at + 1] = (v ushr 16).toByte()
        bytes[at + 2] = (v ushr 8).toByte(); bytes[at + 3] = v.toByte()
    }
    put(16, w)
    put(20, h)
    return bytes
}
