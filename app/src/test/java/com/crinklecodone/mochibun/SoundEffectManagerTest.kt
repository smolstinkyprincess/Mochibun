package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SoundEffectManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSynthesizePcmThemesGenerateValidSamples() {
        val themes = listOf("kawaii", "retro")
        for (theme in themes) {
            for (effect in SoundEffect.entries) {
                val pcm = SoundEffectManager.synthesizePcm(theme, effect)
                assertTrue("PCM for $theme / $effect should not be empty", pcm.isNotEmpty())
                assertTrue("PCM for $theme / $effect should have at least 100 samples", pcm.size >= 100)
                // Ensure no values exceed 16-bit PCM bounds
                for (sample in pcm) {
                    assertTrue("Sample out of bounds: $sample", sample in -32768..32767)
                }
            }
        }
    }

    @Test
    fun testWriteWavFileCreatesCanonicalRiffHeader() {
        val pcm = ShortArray(441) { (it * 50).toShort() }
        val testFile = File(tempFolder.root, "test_audio.wav")

        SoundEffectManager.writeWavFile(testFile, pcm, 22050)

        assertTrue(testFile.exists())
        assertEquals(44 + (pcm.size * 2).toLong(), testFile.length())

        val bytes = FileInputStream(testFile).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val riff = ByteArray(4)
        buffer.get(riff)
        assertEquals("RIFF", String(riff))

        val chunkSize = buffer.int
        assertEquals(36 + (pcm.size * 2), chunkSize)

        val wave = ByteArray(4)
        buffer.get(wave)
        assertEquals("WAVE", String(wave))

        val fmt = ByteArray(4)
        buffer.get(fmt)
        assertEquals("fmt ", String(fmt))

        val subchunk1Size = buffer.int
        assertEquals(16, subchunk1Size)

        val audioFormat = buffer.short
        assertEquals(1.toShort(), audioFormat) // PCM

        val numChannels = buffer.short
        assertEquals(1.toShort(), numChannels) // Mono

        val sampleRate = buffer.int
        assertEquals(22050, sampleRate)

        val byteRate = buffer.int
        assertEquals(22050 * 2, byteRate)

        val blockAlign = buffer.short
        assertEquals(2.toShort(), blockAlign)

        val bitsPerSample = buffer.short
        assertEquals(16.toShort(), bitsPerSample)

        val dataTag = ByteArray(4)
        buffer.get(dataTag)
        assertEquals("data", String(dataTag))

        val dataSize = buffer.int
        assertEquals(pcm.size * 2, dataSize)
    }
}
