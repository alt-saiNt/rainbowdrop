package com.example.rainbowdrop.engine

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object ColorExtractor {

    /**
     * Extracts a dynamic, sorted color palette from the given Bitmap using Adaptive CIELAB K-Means.
     *
     * @param bitmap The source image.
     * @param deltaEThreshold The perceptual distance limit (Delta-E) to register a new color center.
     *                         Smaller values (~6-8) yield more detailed colors; larger values (~12-15) yield fewer.
     * @param maxColors The absolute maximum number of colors to extract.
     * @return A list of ARGB colors sorted chromatically.
     */
    fun extractPalette(
        bitmap: Bitmap,
        deltaEThreshold: Double = 8.0,
        maxColors: Int = 200
    ): List<Int> {
        // 1. Downsample the image to a standard processing grid (e.g., 64x64) for swift math
        val sampleSize = 64
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)
        val width = scaled.width
        val height = scaled.height
        
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // 2. Convert all pixels to LAB space
        val labPoints = ArrayList<DoubleArray>(pixels.size)
        for (color in pixels) {
            val a = (color shr 24) and 0xFF
            // Ignore transparent pixels if there are any
            if (a < 50) continue
            
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            
            labPoints.add(rgbToLab(r, g, b))
        }

        if (labPoints.isEmpty()) {
            return listOf(Color.BLACK, Color.WHITE)
        }

        // 3. Find Adaptive Initial Centers (modified K-Means++ initialization)
        val centers = ArrayList<DoubleArray>()
        
        // Use average color of the image as the first seed center
        var sumL = 0.0
        var sumA = 0.0
        var sumB = 0.0
        for (p in labPoints) {
            sumL += p[0]
            sumA += p[1]
            sumB += p[2]
        }
        val avgCenter = doubleArrayOf(
            sumL / labPoints.size,
            sumA / labPoints.size,
            sumB / labPoints.size
        )
        centers.add(avgCenter)

        // Pre-allocate distance array to track min distance of each point to any chosen center
        val minDists = DoubleArray(labPoints.size) { Double.MAX_VALUE }

        // Spawn centers until we can't find anything distinct enough or we hit maxColors
        while (centers.size < maxColors) {
            val latestCenter = centers.last()
            var maxDistVal = -1.0
            var candidateIdx = -1

            for (i in labPoints.indices) {
                val d = deltaE(labPoints[i], latestCenter)
                if (d < minDists[i]) {
                    minDists[i] = d
                }
                if (minDists[i] > maxDistVal) {
                    maxDistVal = minDists[i]
                    candidateIdx = i
                }
            }

            // If the furthest point is closer than our perceptual threshold, stop spawning centers
            if (maxDistVal < deltaEThreshold || candidateIdx == -1) {
                break
            }

            centers.add(labPoints[candidateIdx])
        }

        // 4. Run K-Means to settle cluster centers into their local peaks
        val finalLabCenters = runKMeans(labPoints, centers, maxIterations = 12)

        // 5. Convert centers back to RGB, filter out noise/duplicates, and sort
        val rgbColors = ArrayList<Int>()
        for (lab in finalLabCenters) {
            val rgb = labToRgb(lab[0], lab[1], lab[2])
            if (!rgbColors.contains(rgb)) {
                rgbColors.add(rgb)
            }
        }

        return sortColors(rgbColors)
    }

    // --- COLOR SPACE MATH (RGB -> XYZ -> CIELAB -> XYZ -> RGB) ---

    private fun rgbToLab(r: Int, g: Int, b: Int): DoubleArray {
        var rf = r / 255.0
        var gf = g / 255.0
        var bf = b / 255.0

        rf = if (rf > 0.04045) ((rf + 0.055) / 1.055).pow(2.4) else rf / 12.92
        gf = if (gf > 0.04045) ((gf + 0.055) / 1.055).pow(2.4) else gf / 12.92
        bf = if (bf > 0.04045) ((bf + 0.055) / 1.055).pow(2.4) else bf / 12.92

        rf *= 100.0
        gf *= 100.0
        bf *= 100.0

        // D65 Standard Illuminant values
        val x = rf * 0.4124 + gf * 0.3576 + bf * 0.1805
        val y = rf * 0.2126 + gf * 0.7152 + bf * 0.0722
        val z = rf * 0.0193 + gf * 0.1192 + bf * 0.9505

        // Convert XYZ to CIELAB
        val refX = 95.047
        val refY = 100.000
        val refZ = 108.883

        var xf = x / refX
        var yf = y / refY
        var zf = z / refZ

        xf = if (xf > 0.008856) xf.pow(1.0 / 3.0) else (7.787 * xf) + (16.0 / 116.0)
        yf = if (yf > 0.008856) yf.pow(1.0 / 3.0) else (7.787 * yf) + (16.0 / 116.0)
        zf = if (zf > 0.008856) zf.pow(1.0 / 3.0) else (7.787 * zf) + (16.0 / 116.0)

        val l = (116.0 * yf) - 16.0
        val cieA = 500.0 * (xf - yf)
        val cieB = 200.0 * (yf - zf)

        return doubleArrayOf(l, cieA, cieB)
    }

    private fun labToRgb(l: Double, a: Double, b: Double): Int {
        // LAB to XYZ
        var yf = (l + 16.0) / 116.0
        var xf = a / 500.0 + yf
        var zf = yf - b / 200.0

        val yf3 = yf.pow(3.0)
        val xf3 = xf.pow(3.0)
        val zf3 = zf.pow(3.0)

        yf = if (yf3 > 0.008856) yf3 else (yf - 16.0 / 116.0) / 7.787
        xf = if (xf3 > 0.008856) xf3 else (xf - 16.0 / 116.0) / 7.787
        zf = if (zf3 > 0.008856) zf3 else (zf - 16.0 / 116.0) / 7.787

        val refX = 95.047
        val refY = 100.000
        val refZ = 108.883

        val x = xf * refX
        val y = yf * refY
        val z = zf * refZ

        // XYZ to RGB
        val xn = x / 100.0
        val yn = y / 100.0
        val zn = z / 100.0

        var rf = xn *  3.2406 + yn * -1.5372 + zn * -0.4986
        var gf = xn * -0.9689 + yn *  1.8758 + zn *  0.0415
        var bf = xn *  0.0557 + yn * -0.2040 + zn *  1.0570

        rf = if (rf > 0.0031308) 1.055 * rf.pow(1.0 / 2.4) - 0.055 else 12.92 * rf
        gf = if (gf > 0.0031308) 1.055 * gf.pow(1.0 / 2.4) - 0.055 else 12.92 * gf
        bf = if (bf > 0.0031308) 1.055 * bf.pow(1.0 / 2.4) - 0.055 else 12.92 * bf

        val ri = (max(0.0, min(1.0, rf)) * 255.0).toInt()
        val gi = (max(0.0, min(1.0, gf)) * 255.0).toInt()
        val bi = (max(0.0, min(1.0, bf)) * 255.0).toInt()

        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun deltaE(lab1: DoubleArray, lab2: DoubleArray): Double {
        // Standard Euclidean distance in CIELAB is a very good approximation of perceptual color difference
        val dL = lab1[0] - lab2[0]
        val dA = lab1[1] - lab2[1]
        val dB = lab1[2] - lab2[2]
        return sqrt(dL * dL + dA * dA + dB * dB)
    }

    // --- K-MEANS ENGINE ---

    private fun runKMeans(
        points: List<DoubleArray>,
        initialCenters: List<DoubleArray>,
        maxIterations: Int
    ): List<DoubleArray> {
        var centers = initialCenters.map { it.clone() }
        val k = centers.size
        if (k == 0) return emptyList()

        for (iter in 0 until maxIterations) {
            val clusters = List(k) { ArrayList<DoubleArray>() }

            // Assign each point to its closest center
            for (p in points) {
                var minIdx = 0
                var minDist = Double.MAX_VALUE
                for (i in 0 until k) {
                    val d = deltaE(p, centers[i])
                    if (d < minDist) {
                        minDist = d
                        minIdx = i
                    }
                }
                clusters[minIdx].add(p)
            }

            // Recompute centers based on mean of cluster members
            var shifted = false
            val nextCenters = ArrayList<DoubleArray>(k)
            for (i in 0 until k) {
                val clusterPoints = clusters[i]
                if (clusterPoints.isEmpty()) {
                    nextCenters.add(centers[i])
                    continue
                }

                var sumL = 0.0
                var sumA = 0.0
                var sumB = 0.0
                for (p in clusterPoints) {
                    sumL += p[0]
                    sumA += p[1]
                    sumB += p[2]
                }
                val count = clusterPoints.size.toDouble()
                val meanCenter = doubleArrayOf(sumL / count, sumA / count, sumB / count)

                if (deltaE(meanCenter, centers[i]) > 0.05) {
                    shifted = true
                }
                nextCenters.add(meanCenter)
            }
            centers = nextCenters
            if (!shifted) break
        }
        return centers
    }

    // --- CHROMATIC GRADIANT SORTING ---

    /**
     * Sorts the colors chromatically (Hue -> Saturation -> Value) and pushes neutral colors
     * (whites, grays, blacks) cleanly to the end of the list.
     */
    private fun sortColors(colors: List<Int>): List<Int> {
        val hsv = FloatArray(3)
        return colors.sortedWith { c1, c2 ->
            Color.colorToHSV(c1, hsv)
            val h1 = hsv[0]
            val s1 = hsv[1]
            val v1 = hsv[2]

            Color.colorToHSV(c2, hsv)
            val h2 = hsv[0]
            val s2 = hsv[1]
            val v2 = hsv[2]

            // Define "neutral" colors (blacks, grays, whites)
            // Low saturation OR extremely low brightness
            val isNeutral1 = s1 < 0.10f || v1 < 0.10f
            val isNeutral2 = s2 < 0.10f || v2 < 0.10f

            if (isNeutral1 && !isNeutral2) {
                1  // Put neutral 1 after chromatic 2
            } else if (!isNeutral1 && isNeutral2) {
                -1 // Put chromatic 1 before neutral 2
            } else if (isNeutral1 && isNeutral2) {
                // Both neutral: sort from darkest to lightest
                v1.compareTo(v2)
            } else {
                // Both chromatic: sort by Hue, then Saturation, then Value
                val hueComp = h1.compareTo(h2)
                if (hueComp != 0) {
                    hueComp
                } else {
                    val satComp = s1.compareTo(s2)
                    if (satComp != 0) {
                        satComp
                    } else {
                        v1.compareTo(v2)
                    }
                }
            }
        }
    }
}
