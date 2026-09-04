package com.example.rainbowdrop.engine

import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class OriginalEngineConfig(
    val maxPaletteColors: Int = 18,
    val minimumPaletteDistance: Double = 5.0,
    val substantialColorShare: Double = 0.02,
    val minimumCoherentArea: Int = 9,
    val coherentAreaDivisor: Int = 16_000,
    val minimumRegionArea: Int = 9,
    val regionAreaDivisor: Int = 10_000,
    val strongFeatureAreaDivisor: Int = 30_000,
    val strongBoundaryDelta: Double = 12.0
)

/**
 * Prepares Original photos for guided coloring without depending on Android classes.
 * Keeping this layer on primitive arrays makes segmentation deterministic and unit-testable.
 */
class OriginalColoringEngine(
    private val config: OriginalEngineConfig = OriginalEngineConfig()
) {
    fun prepare(width: Int, height: Int, revealPixels: IntArray): ColoringDocument {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(revealPixels.size == width * height) { "Pixel count does not match dimensions" }

        val histogram = buildHistogram(revealPixels)
        val initialCenters = chooseInitialCenters(histogram, config.maxPaletteColors)
        val centers = runWeightedKMeans(histogram, initialCenters)
        var colorMap = assignPixels(revealPixels, histogram, centers)

        colorMap = pruneScatteredColors(colorMap, width, height, centers)
        val compactedAfterPruning = compactColors(colorMap, centers)
        colorMap = compactedAfterPruning.labels
        var compactCenters = compactedAfterPruning.centers

        colorMap = mergeUnplayableRegions(colorMap, width, height, compactCenters)
        val finalCompaction = compactColors(colorMap, compactCenters)
        colorMap = finalCompaction.labels
        compactCenters = finalCompaction.centers

        val regionData = labelRegions(colorMap, width, height)
        val paletteCounts = IntArray(compactCenters.size)
        for (colorId in colorMap) paletteCounts[colorId]++
        val palette = compactCenters.mapIndexed { index, center ->
            PaletteEntry(index, labToArgb(center), paletteCounts[index])
        }
        val outline = generateOutline(regionData.regionIdByPixel, width, height)

        return ColoringDocument(
            width = width,
            height = height,
            revealPixels = revealPixels.clone(),
            palette = palette,
            colorIdByPixel = colorMap,
            regionIdByPixel = regionData.regionIdByPixel,
            regions = regionData.regions,
            fullOutlinePixels = outline
        )
    }

    private data class HistogramBin(
        val key: Int,
        val count: Int,
        val lab: DoubleArray
    )

    private data class MutableHistogramBin(
        var count: Int = 0,
        var red: Long = 0,
        var green: Long = 0,
        var blue: Long = 0
    )

    private fun buildHistogram(pixels: IntArray): List<HistogramBin> {
        val bins = HashMap<Int, MutableHistogramBin>()
        for (pixel in pixels) {
            val red = pixel ushr 16 and 0xFF
            val green = pixel ushr 8 and 0xFF
            val blue = pixel and 0xFF
            val key = quantizedKey(red, green, blue)
            val bin = bins.getOrPut(key) { MutableHistogramBin() }
            bin.count++
            bin.red += red
            bin.green += green
            bin.blue += blue
        }
        return bins.map { (key, bin) ->
            val count = bin.count.coerceAtLeast(1)
            HistogramBin(
                key = key,
                count = count,
                lab = rgbToLab(
                    (bin.red / count).toInt(),
                    (bin.green / count).toInt(),
                    (bin.blue / count).toInt()
                )
            )
        }
    }

    private fun chooseInitialCenters(bins: List<HistogramBin>, maxColors: Int): List<DoubleArray> {
        if (bins.isEmpty()) return listOf(doubleArrayOf(0.0, 0.0, 0.0))
        val centers = mutableListOf(bins.maxBy { it.count }.lab.clone())

        while (centers.size < maxColors && centers.size < bins.size) {
            var bestBin: HistogramBin? = null
            var bestDistance = 0.0
            var bestScore = Double.NEGATIVE_INFINITY
            for (bin in bins) {
                val distance = centers.minOf { deltaE(bin.lab, it) }
                val score = distance * sqrt(bin.count.toDouble())
                if (score > bestScore) {
                    bestScore = score
                    bestDistance = distance
                    bestBin = bin
                }
            }
            if (bestBin == null || bestDistance < config.minimumPaletteDistance) break
            centers += bestBin.lab.clone()
        }
        return centers
    }

    private fun runWeightedKMeans(
        bins: List<HistogramBin>,
        initialCenters: List<DoubleArray>
    ): List<DoubleArray> {
        var centers = initialCenters.map { it.clone() }
        repeat(10) {
            val sums = Array(centers.size) { DoubleArray(3) }
            val weights = LongArray(centers.size)
            for (bin in bins) {
                val cluster = closestCenter(bin.lab, centers)
                val weight = bin.count.toLong()
                sums[cluster][0] += bin.lab[0] * weight
                sums[cluster][1] += bin.lab[1] * weight
                sums[cluster][2] += bin.lab[2] * weight
                weights[cluster] += weight
            }

            var moved = false
            centers = centers.mapIndexed { index, oldCenter ->
                if (weights[index] == 0L) {
                    oldCenter
                } else {
                    val next = doubleArrayOf(
                        sums[index][0] / weights[index],
                        sums[index][1] / weights[index],
                        sums[index][2] / weights[index]
                    )
                    if (deltaE(oldCenter, next) > 0.05) moved = true
                    next
                }
            }
            if (!moved) return centers
        }
        return centers
    }

    private fun assignPixels(
        pixels: IntArray,
        histogram: List<HistogramBin>,
        centers: List<DoubleArray>
    ): IntArray {
        val clusterByKey = HashMap<Int, Int>(histogram.size)
        for (bin in histogram) clusterByKey[bin.key] = closestCenter(bin.lab, centers)
        return IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val key = quantizedKey(pixel ushr 16 and 0xFF, pixel ushr 8 and 0xFF, pixel and 0xFF)
            clusterByKey.getValue(key)
        }
    }

    private fun pruneScatteredColors(
        labels: IntArray,
        width: Int,
        height: Int,
        centers: List<DoubleArray>
    ): IntArray {
        val totalByColor = IntArray(centers.size)
        for (label in labels) totalByColor[label]++
        val largestComponent = largestComponents(labels, width, height, centers.size)
        val totalPixels = labels.size
        val coherentArea = max(config.minimumCoherentArea, totalPixels / config.coherentAreaDivisor)
        val retained = BooleanArray(centers.size) { colorId ->
            totalByColor[colorId] >= totalPixels * config.substantialColorShare ||
                largestComponent[colorId] >= coherentArea
        }

        if (retained.none { it }) {
            retained[totalByColor.indices.maxBy { totalByColor[it] }] = true
        }
        if (retained.all { it }) return labels

        val result = labels.clone()
        repeat(2) {
            val source = result.clone()
            for (index in source.indices) {
                val oldColor = source[index]
                if (retained[oldColor]) continue
                val x = index % width
                val y = index / width
                val neighborCounts = IntArray(centers.size)
                forEachNeighbor(x, y, width, height, includeDiagonals = true) { neighbor ->
                    val neighborColor = source[neighbor]
                    if (retained[neighborColor]) neighborCounts[neighborColor]++
                }
                val adjacentChoice = neighborCounts.indices
                    .filter { retained[it] && neighborCounts[it] > 0 }
                    .maxWithOrNull(
                        compareBy<Int> { neighborCounts[it] }
                            .thenBy { -deltaE(centers[oldColor], centers[it]) }
                    )
                result[index] = adjacentChoice ?: nearestRetained(oldColor, retained, centers)
            }
        }
        return result
    }

    private fun mergeUnplayableRegions(
        labels: IntArray,
        width: Int,
        height: Int,
        centers: List<DoubleArray>
    ): IntArray {
        val result = labels.clone()
        val minimumArea = max(config.minimumRegionArea, labels.size / config.regionAreaDivisor)
        val strongFeatureArea = max(config.minimumCoherentArea, labels.size / config.strongFeatureAreaDivisor)

        repeat(2) {
            val source = result.clone()
            val visited = BooleanArray(source.size)
            val queue = IntArray(source.size)
            for (start in source.indices) {
                if (visited[start]) continue
                val colorId = source[start]
                val pixels = IntAccumulator()
                val neighborCounts = IntArray(centers.size)
                var head = 0
                var tail = 0
                queue[tail++] = start
                visited[start] = true

                while (head < tail) {
                    val index = queue[head++]
                    pixels.add(index)
                    val x = index % width
                    val y = index / width
                    forEachNeighbor(x, y, width, height, includeDiagonals = false) { neighbor ->
                        val neighborColor = source[neighbor]
                        if (neighborColor == colorId) {
                            if (!visited[neighbor]) {
                                visited[neighbor] = true
                                queue[tail++] = neighbor
                            }
                        } else {
                            neighborCounts[neighborColor]++
                        }
                    }
                }

                if (pixels.size >= minimumArea) continue
                val adjacentColors = neighborCounts.indices.filter { neighborCounts[it] > 0 }
                if (adjacentColors.isEmpty()) continue
                val strongestBoundary = adjacentColors.minOf { deltaE(centers[colorId], centers[it]) }
                val preserveStrongFeature = pixels.size >= strongFeatureArea &&
                    strongestBoundary >= config.strongBoundaryDelta
                if (preserveStrongFeature) continue

                val replacement = adjacentColors.minBy { candidate ->
                    deltaE(centers[colorId], centers[candidate]) -
                        min(12.0, neighborCounts[candidate] * 0.15)
                }
                pixels.forEach { result[it] = replacement }
            }
        }
        return result
    }

    private data class CompactColors(val labels: IntArray, val centers: List<DoubleArray>)

    private fun compactColors(labels: IntArray, centers: List<DoubleArray>): CompactColors {
        val present = BooleanArray(centers.size)
        for (label in labels) present[label] = true
        val oldToNew = IntArray(centers.size) { -1 }
        val compactCenters = mutableListOf<DoubleArray>()
        for (oldId in centers.indices) {
            if (present[oldId]) {
                oldToNew[oldId] = compactCenters.size
                compactCenters += centers[oldId]
            }
        }
        return CompactColors(
            labels = IntArray(labels.size) { oldToNew[labels[it]] },
            centers = compactCenters
        )
    }

    private data class RegionData(
        val regionIdByPixel: IntArray,
        val regions: List<ColoringRegion>
    )

    private fun labelRegions(labels: IntArray, width: Int, height: Int): RegionData {
        val regionIds = IntArray(labels.size) { -1 }
        val queue = IntArray(labels.size)
        val regionColors = mutableListOf<Int>()
        val regionAreas = mutableListOf<Int>()
        val regionBounds = mutableListOf<PixelBounds>()

        for (start in labels.indices) {
            if (regionIds[start] != -1) continue
            val regionId = regionColors.size
            val colorId = labels[start]
            var head = 0
            var tail = 0
            var area = 0
            var left = width
            var top = height
            var right = 0
            var bottom = 0
            queue[tail++] = start
            regionIds[start] = regionId

            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                area++
                left = min(left, x)
                top = min(top, y)
                right = max(right, x)
                bottom = max(bottom, y)
                forEachNeighbor(x, y, width, height, includeDiagonals = false) { neighbor ->
                    if (regionIds[neighbor] == -1 && labels[neighbor] == colorId) {
                        regionIds[neighbor] = regionId
                        queue[tail++] = neighbor
                    }
                }
            }
            regionColors += colorId
            regionAreas += area
            regionBounds += PixelBounds(left, top, right, bottom)
        }

        val runsByRegion = Array(regionColors.size) { mutableListOf<PixelRun>() }
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                val regionId = regionIds[y * width + x]
                val startX = x
                while (x + 1 < width && regionIds[y * width + x + 1] == regionId) x++
                runsByRegion[regionId] += PixelRun(y, startX, x)
                x++
            }
        }

        val regions = regionColors.indices.map { regionId ->
            ColoringRegion(
                id = regionId,
                requiredColorId = regionColors[regionId],
                area = regionAreas[regionId],
                bounds = regionBounds[regionId],
                runs = runsByRegion[regionId]
            )
        }
        return RegionData(regionIds, regions)
    }

    private fun generateOutline(regionIds: IntArray, width: Int, height: Int): IntArray {
        val outline = IntArray(regionIds.size)
        val black = 0xFF000000.toInt()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val regionId = regionIds[index]
                val boundary = x == 0 || y == 0 || x == width - 1 || y == height - 1 ||
                    regionIds[index - 1] != regionId || regionIds[index + 1] != regionId ||
                    regionIds[index - width] != regionId || regionIds[index + width] != regionId
                if (boundary) outline[index] = black
            }
        }
        return outline
    }

    private fun largestComponents(
        labels: IntArray,
        width: Int,
        height: Int,
        colorCount: Int
    ): IntArray {
        val largest = IntArray(colorCount)
        val visited = BooleanArray(labels.size)
        val queue = IntArray(labels.size)
        for (start in labels.indices) {
            if (visited[start]) continue
            val color = labels[start]
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                forEachNeighbor(x, y, width, height, includeDiagonals = false) { neighbor ->
                    if (!visited[neighbor] && labels[neighbor] == color) {
                        visited[neighbor] = true
                        queue[tail++] = neighbor
                    }
                }
            }
            largest[color] = max(largest[color], tail)
        }
        return largest
    }

    private fun nearestRetained(
        sourceColor: Int,
        retained: BooleanArray,
        centers: List<DoubleArray>
    ): Int = retained.indices.filter { retained[it] }
        .minBy { deltaE(centers[sourceColor], centers[it]) }

    private fun closestCenter(lab: DoubleArray, centers: List<DoubleArray>): Int =
        centers.indices.minBy { deltaE(lab, centers[it]) }

    private inline fun forEachNeighbor(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        includeDiagonals: Boolean,
        action: (Int) -> Unit
    ) {
        if (x > 0) action(y * width + x - 1)
        if (x + 1 < width) action(y * width + x + 1)
        if (y > 0) action((y - 1) * width + x)
        if (y + 1 < height) action((y + 1) * width + x)
        if (includeDiagonals) {
            if (x > 0 && y > 0) action((y - 1) * width + x - 1)
            if (x + 1 < width && y > 0) action((y - 1) * width + x + 1)
            if (x > 0 && y + 1 < height) action((y + 1) * width + x - 1)
            if (x + 1 < width && y + 1 < height) action((y + 1) * width + x + 1)
        }
    }

    private fun quantizedKey(red: Int, green: Int, blue: Int): Int =
        ((red ushr 4) shl 8) or ((green ushr 4) shl 4) or (blue ushr 4)

    private class IntAccumulator(initialCapacity: Int = 16) {
        private var values = IntArray(initialCapacity)
        var size: Int = 0
            private set

        fun add(value: Int) {
            if (size == values.size) values = values.copyOf(values.size * 2)
            values[size++] = value
        }

        inline fun forEach(action: (Int) -> Unit) {
            for (index in 0 until size) action(values[index])
        }
    }

    private fun rgbToLab(red: Int, green: Int, blue: Int): DoubleArray {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value > 0.04045) ((value + 0.055) / 1.055).pow(2.4) else value / 12.92
        }
        val redLinear = linear(red)
        val greenLinear = linear(green)
        val blueLinear = linear(blue)
        val x = (redLinear * 0.4124 + greenLinear * 0.3576 + blueLinear * 0.1805) / 0.95047
        val y = redLinear * 0.2126 + greenLinear * 0.7152 + blueLinear * 0.0722
        val z = (redLinear * 0.0193 + greenLinear * 0.1192 + blueLinear * 0.9505) / 1.08883
        fun pivot(value: Double): Double =
            if (value > 0.008856) value.pow(1.0 / 3.0) else 7.787 * value + 16.0 / 116.0
        val fx = pivot(x)
        val fy = pivot(y)
        val fz = pivot(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    private fun labToArgb(lab: DoubleArray): Int {
        var y = (lab[0] + 16.0) / 116.0
        var x = lab[1] / 500.0 + y
        var z = y - lab[2] / 200.0
        fun inversePivot(value: Double): Double {
            val cube = value * value * value
            return if (cube > 0.008856) cube else (value - 16.0 / 116.0) / 7.787
        }
        x = 0.95047 * inversePivot(x)
        y = inversePivot(y)
        z = 1.08883 * inversePivot(z)
        var red = x * 3.2406 + y * -1.5372 + z * -0.4986
        var green = x * -0.9689 + y * 1.8758 + z * 0.0415
        var blue = x * 0.0557 + y * -0.2040 + z * 1.0570
        fun gamma(value: Double): Int {
            val corrected = if (value > 0.0031308) 1.055 * value.pow(1.0 / 2.4) - 0.055 else 12.92 * value
            return (corrected.coerceIn(0.0, 1.0) * 255.0).toInt()
        }
        red = gamma(red).toDouble()
        green = gamma(green).toDouble()
        blue = gamma(blue).toDouble()
        return (0xFF shl 24) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
    }

    private fun deltaE(first: DoubleArray, second: DoubleArray): Double {
        val deltaL = first[0] - second[0]
        val deltaA = first[1] - second[1]
        val deltaB = first[2] - second[2]
        return sqrt(deltaL * deltaL + deltaA * deltaA + deltaB * deltaB)
    }
}
