package com.example.rainbowdrop.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalColoringEngineTest {
    private val engine = OriginalColoringEngine()

    @Test
    fun scatteredSpecksAreAbsorbedIntoTheMajorColor() {
        val width = 60
        val height = 60
        val background = argb(38, 108, 184)
        val speck = argb(224, 116, 42)
        val pixels = IntArray(width * height) { background }

        var placed = 0
        for (y in 2 until height step 6) {
            for (x in 2 until width step 6) {
                pixels[y * width + x] = speck
                placed++
            }
        }
        assertTrue(placed > 50)

        val document = engine.prepare(width, height, pixels)

        assertEquals(1, document.palette.size)
        assertEquals(1, document.regions.size)
        assertTrue(document.colorIdByPixel.all { it == 0 })
    }

    @Test
    fun smallCoherentFeatureSurvivesPaletteReduction() {
        val width = 40
        val height = 40
        val skin = argb(206, 151, 118)
        val eye = argb(24, 72, 132)
        val pixels = IntArray(width * height) { skin }
        for (y in 18..21) {
            for (x in 18..21) pixels[y * width + x] = eye
        }

        val document = engine.prepare(width, height, pixels)
        val eyeRegionId = document.regionIdByPixel[19 * width + 19]
        val skinRegionId = document.regionIdByPixel[0]

        assertEquals(2, document.palette.size)
        assertNotEquals(skinRegionId, eyeRegionId)
        assertEquals(16, document.regions[eyeRegionId].area)
    }

    @Test
    fun guidedFillUsesRegionMembershipEvenWhenVisibleOutlineHasAGap() {
        val width = 12
        val height = 6
        val red = argb(190, 42, 52)
        val blue = argb(38, 84, 190)
        val source = IntArray(width * height) { index ->
            if (index % width < width / 2) red else blue
        }
        val prepared = engine.prepare(width, height, source)
        val outlineWithGap = prepared.fullOutlinePixels.clone().also {
            it[3 * width + width / 2] = 0
        }
        val document = prepared.copy(fullOutlinePixels = outlineWithGap)
        val coloring = IntArray(width * height)
        val leftRegion = document.regions[document.regionIdByPixel[2 * width + 2]]
        val selectedColor = document.palette[leftRegion.requiredColorId].argb

        val result = GuidedRegionPainter.fillAt(document, coloring, 2, 2, selectedColor)

        assertTrue(result is GuidedFillResult.Filled)
        assertTrue((coloring[2 * width + 2] ushr 24) != 0)
        assertEquals(0, coloring[2 * width + 9])
    }

    @Test
    fun guidedFillRejectsTheWrongPaletteFamily() {
        val width = 10
        val height = 4
        val left = argb(210, 55, 45)
        val right = argb(35, 95, 205)
        val source = IntArray(width * height) { index ->
            if (index % width < width / 2) left else right
        }
        val document = engine.prepare(width, height, source)
        val coloring = IntArray(width * height)
        val leftRegion = document.regions[document.regionIdByPixel[1 * width + 1]]
        val wrongColor = document.palette.first { it.id != leftRegion.requiredColorId }.argb

        val result = GuidedRegionPainter.fillAt(document, coloring, 1, 1, wrongColor)

        assertTrue(result is GuidedFillResult.WrongColor)
        assertFalse(coloring.any { (it ushr 24) != 0 })
    }

    @Test
    fun disconnectedAreasOfTheSameColorReceiveDifferentRegionIds() {
        val width = 20
        val height = 20
        val red = argb(205, 45, 50)
        val blue = argb(35, 85, 190)
        val source = IntArray(width * height) { index ->
            val x = index % width
            if (x == 9 || x == 10) blue else red
        }

        val document = engine.prepare(width, height, source)
        val leftRegion = document.regions[document.regionIdByPixel[5 * width + 3]]
        val rightRegion = document.regions[document.regionIdByPixel[5 * width + 16]]

        assertNotEquals(leftRegion.id, rightRegion.id)
        assertEquals(leftRegion.requiredColorId, rightRegion.requiredColorId)
    }

    @Test
    fun guidedFillSnapsToANearbySmallRegionOfTheSelectedColor() {
        val width = 24
        val height = 24
        val background = argb(174, 126, 86)
        val detail = argb(45, 94, 156)
        val source = IntArray(width * height) { background }
        for (y in 10..12) {
            for (x in 10..12) source[y * width + x] = detail
        }
        val document = engine.prepare(width, height, source)
        val coloring = IntArray(width * height)
        val detailRegion = document.regions[document.regionIdByPixel[11 * width + 11]]
        val selectedColor = document.palette[detailRegion.requiredColorId].argb

        val result = GuidedRegionPainter.fillAt(
            document = document,
            coloringPixels = coloring,
            x = 7,
            y = 11,
            selectedColor = selectedColor,
            maxSnapDistance = 4
        )

        assertEquals(GuidedFillResult.Filled(detailRegion.id), result)
        assertTrue((coloring[11 * width + 11] ushr 24) != 0)
        assertEquals(0, coloring[11 * width + 5])
    }

    @Test
    fun guidedFillDoesNotSnapToADistantRegion() {
        val width = 24
        val height = 24
        val background = argb(174, 126, 86)
        val detail = argb(45, 94, 156)
        val source = IntArray(width * height) { background }
        for (y in 10..12) {
            for (x in 10..12) source[y * width + x] = detail
        }
        val document = engine.prepare(width, height, source)
        val coloring = IntArray(width * height)
        val detailRegion = document.regions[document.regionIdByPixel[11 * width + 11]]
        val selectedColor = document.palette[detailRegion.requiredColorId].argb

        val result = GuidedRegionPainter.fillAt(
            document = document,
            coloringPixels = coloring,
            x = 2,
            y = 11,
            selectedColor = selectedColor,
            maxSnapDistance = 4
        )

        assertTrue(result is GuidedFillResult.WrongColor)
        assertFalse(coloring.any { (it ushr 24) != 0 })
    }

    @Test
    fun coherentMutedBackgroundSurfacesKeepDistinctPaletteFamilies() {
        val width = 80
        val height = 24
        val browns = intArrayOf(
            argb(139, 105, 77),
            argb(123, 96, 75),
            argb(108, 84, 66),
            argb(91, 72, 59)
        )
        val source = IntArray(width * height) { index ->
            browns[(index % width) / 20]
        }

        val document = engine.prepare(width, height, source)
        val stripeColorIds = (0 until 4).map { stripe ->
            document.colorIdByPixel[12 * width + stripe * 20 + 10]
        }.toSet()

        assertTrue(stripeColorIds.size >= 3)
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
