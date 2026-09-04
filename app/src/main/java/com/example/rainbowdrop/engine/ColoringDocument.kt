package com.example.rainbowdrop.engine

/**
 * The nonvisual source of truth for one prepared coloring page.
 *
 * The UI may choose how to draw the outlines, but fills are always resolved through
 * [regionIdByPixel]. This prevents an antialiased or missing outline pixel from changing
 * which area a tap belongs to.
 */
data class ColoringDocument(
    val width: Int,
    val height: Int,
    val revealPixels: IntArray,
    val palette: List<PaletteEntry>,
    val colorIdByPixel: IntArray,
    val regionIdByPixel: IntArray,
    val regions: List<ColoringRegion>,
    val fullOutlinePixels: IntArray
) {
    init {
        val pixelCount = width * height
        require(width > 0 && height > 0) { "Document dimensions must be positive" }
        require(revealPixels.size == pixelCount) { "Reveal image size does not match document" }
        require(colorIdByPixel.size == pixelCount) { "Color map size does not match document" }
        require(regionIdByPixel.size == pixelCount) { "Region map size does not match document" }
        require(fullOutlinePixels.size == pixelCount) { "Outline size does not match document" }
    }
}

data class PaletteEntry(
    val id: Int,
    val argb: Int,
    val pixelCount: Int
)

data class PixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class PixelRun(
    val y: Int,
    val startX: Int,
    val endXInclusive: Int
)

data class ColoringRegion(
    val id: Int,
    val requiredColorId: Int,
    val area: Int,
    val bounds: PixelBounds,
    val runs: List<PixelRun>
)

/** Result of a guided tap, kept explicit so rejected taps cannot enter undo history. */
sealed interface GuidedFillResult {
    data class Filled(val regionId: Int) : GuidedFillResult
    data object WrongColor : GuidedFillResult
    data object AlreadyFilled : GuidedFillResult
    data object OutsideDocument : GuidedFillResult
}

object GuidedRegionPainter {
    fun fillAt(
        document: ColoringDocument,
        coloringPixels: IntArray,
        x: Int,
        y: Int,
        selectedColor: Int,
        maxSnapDistance: Int = 0
    ): GuidedFillResult {
        require(coloringPixels.size == document.width * document.height) {
            "Coloring layer size does not match document"
        }
        val selectedColorId = document.palette.indexOfFirst { it.argb == selectedColor }
        if (selectedColorId == -1) return GuidedFillResult.WrongColor

        val directResult = resultAt(document, coloringPixels, x, y, selectedColorId)
        if (directResult is GuidedFillResult.Filled) {
            fillRegion(document, coloringPixels, directResult.regionId)
            return directResult
        }

        val snappedRegionId = if (maxSnapDistance > 0) {
            findNearestUnfilledRegion(
                document = document,
                coloringPixels = coloringPixels,
                x = x,
                y = y,
                selectedColorId = selectedColorId,
                maxDistance = maxSnapDistance
            )
        } else {
            null
        }
        if (snappedRegionId == null) return directResult

        fillRegion(document, coloringPixels, snappedRegionId)
        return GuidedFillResult.Filled(snappedRegionId)
    }

    private fun resultAt(
        document: ColoringDocument,
        coloringPixels: IntArray,
        x: Int,
        y: Int,
        selectedColorId: Int
    ): GuidedFillResult {
        if (x !in 0 until document.width || y !in 0 until document.height) {
            return GuidedFillResult.OutsideDocument
        }
        val regionId = document.regionIdByPixel[y * document.width + x]
        val region = document.regions.getOrNull(regionId)
            ?: return GuidedFillResult.OutsideDocument
        if (selectedColorId != region.requiredColorId) return GuidedFillResult.WrongColor
        return if (isFilled(document, coloringPixels, region)) {
            GuidedFillResult.AlreadyFilled
        } else {
            GuidedFillResult.Filled(regionId)
        }
    }

    private fun findNearestUnfilledRegion(
        document: ColoringDocument,
        coloringPixels: IntArray,
        x: Int,
        y: Int,
        selectedColorId: Int,
        maxDistance: Int
    ): Int? {
        val radius = maxDistance.coerceAtLeast(0)
        val minimumX = (x - radius).coerceAtLeast(0)
        val maximumX = (x + radius).coerceAtMost(document.width - 1)
        val minimumY = (y - radius).coerceAtLeast(0)
        val maximumY = (y + radius).coerceAtMost(document.height - 1)
        val maximumDistanceSquared = radius * radius
        var nearestRegionId: Int? = null
        var nearestDistanceSquared = Int.MAX_VALUE

        for (candidateY in minimumY..maximumY) {
            for (candidateX in minimumX..maximumX) {
                val deltaX = candidateX - x
                val deltaY = candidateY - y
                val distanceSquared = deltaX * deltaX + deltaY * deltaY
                if (distanceSquared > maximumDistanceSquared || distanceSquared >= nearestDistanceSquared) continue
                val regionId = document.regionIdByPixel[candidateY * document.width + candidateX]
                if (regionId !in document.regions.indices) continue
                val region = document.regions[regionId]
                if (region.requiredColorId != selectedColorId || isFilled(document, coloringPixels, region)) continue
                nearestRegionId = regionId
                nearestDistanceSquared = distanceSquared
            }
        }
        return nearestRegionId
    }

    private fun isFilled(
        document: ColoringDocument,
        coloringPixels: IntArray,
        region: ColoringRegion
    ): Boolean {
        val firstRun = region.runs.firstOrNull() ?: return true
        val firstPixel = firstRun.y * document.width + firstRun.startX
        return (coloringPixels[firstPixel] ushr 24) != 0
    }

    private fun fillRegion(
        document: ColoringDocument,
        coloringPixels: IntArray,
        regionId: Int
    ) {
        val region = document.regions[regionId]
        for (run in region.runs) {
            var index = run.y * document.width + run.startX
            val endIndex = run.y * document.width + run.endXInclusive
            while (index <= endIndex) {
                coloringPixels[index] = document.revealPixels[index]
                index++
            }
        }
    }
}
