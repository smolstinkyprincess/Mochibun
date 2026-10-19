package com.tnyemii.mochibun

import androidx.compose.ui.graphics.Color
import com.tnyemii.mochibun.PixelStickers.generatePixelSprayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PixelSprayTest {

    @Test
    fun count_generatesExactStickerCount() {
        val count = 25
        val items = generatePixelSprayLayout(
            count = count,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f,
            seed = 12345L
        )
        assertEquals(count, items.size)
    }

    @Test
    fun zeroCount_returnsEmptyList() {
        val items = generatePixelSprayLayout(
            count = 0,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun emptyStickers_returnsEmptyList() {
        val items = generatePixelSprayLayout(
            count = 10,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            stickers = emptyList(),
            minSizePx = 30f,
            maxSizePx = 60f
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun sizeBounds_respectedForEverySticker() {
        val minSize = 25f
        val maxSize = 70f
        val items = generatePixelSprayLayout(
            count = 40,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = minSize,
            maxSizePx = maxSize,
            seed = 999L
        )

        for (item in items) {
            val maxDimension = maxOf(item.width, item.height)
            assertTrue("Sticker dimension $maxDimension must be >= $minSize", maxDimension >= minSize - 0.01f)
            assertTrue("Sticker dimension $maxDimension must be <= $maxSize", maxDimension <= maxSize + 0.01f)
        }
    }

    @Test
    fun rotationBounds_respectedForEverySticker() {
        val minRot = -30f
        val maxRot = 25f
        val items = generatePixelSprayLayout(
            count = 50,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 50f,
            minRotation = minRot,
            maxRotation = maxRot,
            seed = 777L
        )

        for (item in items) {
            assertTrue("Rotation ${item.rotation} must be >= $minRot", item.rotation >= minRot)
            assertTrue("Rotation ${item.rotation} must be <= $maxRot", item.rotation <= maxRot)
        }
    }

    @Test
    fun aspectRatio_strictlyPreserved() {
        val items = generatePixelSprayLayout(
            count = 60,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f,
            seed = 42L
        )

        for (item in items) {
            val expectedAr = item.asset.aspectRatio
            val actualAr = item.width / item.height
            assertTrue(
                "Aspect ratio for ${item.asset.name} expected $expectedAr but was $actualAr",
                abs(expectedAr - actualAr) < 0.01f
            )
        }
    }

    @Test
    fun deterministicSeed_producesIdenticalArrangements() {
        val seed = 42069L
        val run1 = generatePixelSprayLayout(
            count = 20,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f,
            seed = seed
        )
        val run2 = generatePixelSprayLayout(
            count = 20,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f,
            seed = seed
        )

        assertEquals(run1.size, run2.size)
        for (i in run1.indices) {
            assertEquals(run1[i].asset.resId, run2[i].asset.resId)
            assertEquals(run1[i].x, run2[i].x, 0.001f)
            assertEquals(run1[i].y, run2[i].y, 0.001f)
            assertEquals(run1[i].width, run2[i].width, 0.001f)
            assertEquals(run1[i].height, run2[i].height, 0.001f)
            assertEquals(run1[i].rotation, run2[i].rotation, 0.001f)
            assertEquals(run1[i].tintColor, run2[i].tintColor)
        }
    }

    @Test
    fun differentSeeds_produceDifferentArrangements() {
        val run1 = generatePixelSprayLayout(
            count = 20,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f,
            seed = 100L
        )
        val run2 = generatePixelSprayLayout(
            count = 20,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f,
            seed = 200L
        )

        val anyDifferent = run1.indices.any { i ->
            run1[i].x != run2[i].x || run1[i].y != run2[i].y || run1[i].rotation != run2[i].rotation
        }
        assertTrue("Different seeds should produce different layouts", anyDifferent)
    }

    @Test
    fun originalOnly_neverAppliesTints() {
        val items = generatePixelSprayLayout(
            count = 30,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f,
            tintBehavior = PixelSprayTintBehavior.ORIGINAL_ONLY,
            seed = 111L
        )

        for (item in items) {
            assertNull("Item ${item.asset.name} should not be tinted in ORIGINAL_ONLY mode", item.tintColor)
            assertNull("Item ${item.asset.name} should have null colorFilter in ORIGINAL_ONLY mode", item.colorFilter)
        }
    }

    @Test
    fun tintEligibleOnly_neverTintsIneligibleAssets() {
        val items = generatePixelSprayLayout(
            count = 50,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f,
            tintBehavior = PixelSprayTintBehavior.TINT_ELIGIBLE_ONLY,
            seed = 222L
        )

        for (item in items) {
            if (!item.asset.canTint) {
                assertNull("Ineligible asset ${item.asset.name} must never be tinted", item.tintColor)
                assertNull("Ineligible asset ${item.asset.name} must have null colorFilter", item.colorFilter)
            } else {
                assertNotNull("Eligible asset ${item.asset.name} should be tinted in TINT_ELIGIBLE_ONLY mode", item.tintColor)
                assertNotNull("Eligible asset ${item.asset.name} should have colorFilter in TINT_ELIGIBLE_ONLY mode", item.colorFilter)
            }
        }
    }

    @Test
    fun customPalette_isRespected() {
        val customColor = Color(0xFF123456)
        val items = generatePixelSprayLayout(
            count = 30,
            areaWidthPx = 480f,
            areaHeightPx = 800f,
            minSizePx = 30f,
            maxSizePx = 60f,
            tintBehavior = PixelSprayTintBehavior.ALWAYS_TINT_WHERE_POSSIBLE,
            palette = listOf(customColor),
            seed = 333L
        )

        for (item in items) {
            if (item.asset.canTint) {
                assertEquals(customColor, item.tintColor)
            }
        }
    }

    @Test
    fun smallArea_handledGracefullyWithoutCrash() {
        val items = generatePixelSprayLayout(
            count = 15,
            areaWidthPx = 50f,
            areaHeightPx = 50f,
            minSizePx = 40f,
            maxSizePx = 60f,
            seed = 444L
        )
        assertEquals(15, items.size)
        for (item in items) {
            assertTrue("X should be finite", item.x.isFinite())
            assertTrue("Y should be finite", item.y.isFinite())
        }
    }
}
