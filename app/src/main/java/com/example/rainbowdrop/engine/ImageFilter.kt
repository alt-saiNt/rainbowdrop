package com.example.rainbowdrop.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt

enum class FilterType(val displayName: String) {
    ORIGINAL("Original"),
    POP_ART("Pop Art"),
    COMIC_BOOK("Comic Book"),
    WATERCOLOR("Watercolor"),
    VECTOR_POSTER("Vector Poster"),
    INK_SKETCH("Ink Sketch"),
    CHARCOAL("Charcoal"),
    TATTOO_FLASH("Tattoo Flash")
}

object ImageProcessor {

    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        return when (filterType) {
            FilterType.ORIGINAL -> bitmap
            FilterType.INK_SKETCH -> applyInkSketch(bitmap)
            FilterType.CHARCOAL -> applyCharcoal(bitmap)
            FilterType.POP_ART -> applyPopArt(bitmap)
            FilterType.VECTOR_POSTER -> applyVectorPoster(bitmap)
            FilterType.COMIC_BOOK -> applyComicBook(bitmap)
            FilterType.WATERCOLOR -> applyWatercolor(bitmap)
            FilterType.TATTOO_FLASH -> applyTattooFlash(bitmap)
        }
    }

    fun getOutlines(bitmap: Bitmap): Bitmap {
        val blurred = boxBlur(bitmap, 6)
        val outlines = extractSobelOutlines(blurred, threshold = 40, edgeBoost = 1.4f)
        blurred.recycle()
        return outlines
    }

    /**
     * 1. INK SKETCH: Inverted Color Dodge Pencil/Ink Line Filter
     */
    private fun applyInkSketch(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height

        val gray = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(src, 0f, 0f, paint)

        val inverted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val invCanvas = Canvas(inverted)
        val invMatrix = ColorMatrix(floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f
        ))
        val invPaint = Paint().apply { colorFilter = ColorMatrixColorFilter(invMatrix) }
        invCanvas.drawBitmap(gray, 0f, 0f, invPaint)

        val blurred = boxBlur(inverted, 4)
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val grayPixels = IntArray(width * height)
        val blurPixels = IntArray(width * height)
        val destPixels = IntArray(width * height)

        gray.getPixels(grayPixels, 0, width, 0, 0, width, height)
        blurred.getPixels(blurPixels, 0, width, 0, 0, width, height)

        for (i in grayPixels.indices) {
            val gVal = (grayPixels[i] shr 16) and 0xFF
            val bVal = (blurPixels[i] shr 16) and 0xFF

            val denom = 255 - bVal
            val dodge = if (denom == 0) 255 else min(255, (gVal * 255) / denom)
            val alpha = 255 - dodge

            if (alpha > 28) {
                val boostedAlpha = min(255, (alpha * 1.6f).toInt())
                destPixels[i] = (boostedAlpha shl 24) or 0x000000
            } else {
                destPixels[i] = Color.TRANSPARENT
            }
        }

        dest.setPixels(destPixels, 0, width, 0, 0, width, height)
        gray.recycle()
        inverted.recycle()
        blurred.recycle()
        return dest
    }

    /**
     * 2. CHARCOAL: High-contrast rich smudged monochrome
     */
    private fun applyCharcoal(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

            // S-Curve charcoal transfer
            val charcoal = when {
                lum < 65 -> 0
                lum > 215 -> 255
                else -> ((lum - 65) * 255) / 150
            }
            pixels[i] = (0xFF shl 24) or (charcoal shl 16) or (charcoal shl 8) or charcoal
        }

        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * 3. POP ART: Saturated 4-level chromatic posterization
     */
    private fun applyPopArt(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF

            // Boost saturation
            val avg = (r + g + b) / 3
            r = min(255, max(0, avg + ((r - avg) * 1.35f).toInt()))
            g = min(255, max(0, avg + ((g - avg) * 1.35f).toInt()))
            b = min(255, max(0, avg + ((b - avg) * 1.35f).toInt()))

            // Quantize to 4 discrete vibrance tiers
            val qr = min(255, (r / 64) * 64 + 32)
            val qg = min(255, (g / 64) * 64 + 32)
            val qb = min(255, (b / 64) * 64 + 32)

            pixels[i] = (0xFF shl 24) or (qr shl 16) or (qg shl 8) or qb
        }

        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * 4. VECTOR POSTER: Crisp graphic band flattening
     */
    private fun applyVectorPoster(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            // 3-tier sharp band quantization
            val vr = min(255, (r / 85) * 85 + 42)
            val vg = min(255, (g / 85) * 85 + 42)
            val vb = min(255, (b / 85) * 85 + 42)

            pixels[i] = (0xFF shl 24) or (vr shl 16) or (vg shl 8) or vb
        }

        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * 5. COMIC BOOK: Halftone dot pattern + bold ink contouring
     */
    private fun applyComicBook(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pop = applyPopArt(src)
        val pixels = IntArray(width * height)
        pop.getPixels(pixels, 0, width, 0, 0, width, height)
        pop.recycle()

        // Apply comic halftone screening
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val p = pixels[idx]
                var r = (p shr 16) and 0xFF
                var g = (p shr 8) and 0xFF
                var b = p and 0xFF

                // Halftone dot modulation in 4x4 matrix
                val isDot = (x % 4 == 0 && y % 4 == 0) || ((x + 2) % 4 == 0 && (y + 2) % 4 == 0)
                if (isDot) {
                    r = max(0, (r * 0.75f).toInt())
                    g = max(0, (g * 0.75f).toInt())
                    b = max(0, (b * 0.75f).toInt())
                }

                pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * 6. WATERCOLOR: Pigment diffusion with edge pooling & translucent wash
     */
    private fun applyWatercolor(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        
        // 1. Soft pigment diffusion blur
        val blurred = boxBlur(src, 6)
        val pixels = IntArray(width * height)
        blurred.getPixels(pixels, 0, width, 0, 0, width, height)
        blurred.recycle()

        // 2. Extract edge gradient for wet-edge pooling
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val p = pixels[idx]
                var r = (p shr 16) and 0xFF
                var g = (p shr 8) and 0xFF
                var b = p and 0xFF

                // Check contrast delta with neighbors (pigment pooling at contours)
                val left = (pixels[idx - 1] shr 16) and 0xFF
                val right = (pixels[idx + 1] shr 16) and 0xFF
                val delta = abs(left - right)

                if (delta > 20) {
                    // Darken pigment edges
                    r = max(0, r - (delta * 0.4f).toInt())
                    g = max(0, g - (delta * 0.4f).toInt())
                    b = max(0, b - (delta * 0.4f).toInt())
                } else {
                    // Soft translucent wash luminance boost
                    r = min(255, (r * 1.05f).toInt())
                    g = min(255, (g * 1.05f).toInt())
                    b = min(255, (b * 1.05f).toInt())
                }

                pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * 7. TATTOO FLASH: American traditional bold outlines & rich solid colors
     */
    private fun applyTattooFlash(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            // Warm vintage shift + rich saturation
            val tr = min(255, (r * 1.1f).toInt())
            val tg = min(255, (g * 1.0f).toInt())
            val tb = max(0, (b * 0.9f).toInt())

            val qr = min(255, (tr / 48) * 48 + 24)
            val qg = min(255, (tg / 48) * 48 + 24)
            val qb = min(255, (tb / 48) * 48 + 24)

            pixels[i] = (0xFF shl 24) or (qr shl 16) or (qg shl 8) or qb
        }

        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * High-speed Sobel Edge Extraction for clean coloring boundaries
     */
    private fun extractSobelOutlines(src: Bitmap, threshold: Int = 40, edgeBoost: Float = 1.4f): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        val gray = IntArray(width * height)
        val destPixels = IntArray(width * height)

        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        }

        for (y in 1 until height - 1) {
            val rowAbove = (y - 1) * width
            val rowCurr = y * width
            val rowBelow = (y + 1) * width

            for (x in 1 until width - 1) {
                val val00 = gray[rowAbove + x - 1]
                val val01 = gray[rowAbove + x]
                val val02 = gray[rowAbove + x + 1]

                val val10 = gray[rowCurr + x - 1]
                val val12 = gray[rowCurr + x + 1]

                val val20 = gray[rowBelow + x - 1]
                val val21 = gray[rowBelow + x]
                val val22 = gray[rowBelow + x + 1]

                val gx = (-val00 + val02 - 2 * val10 + 2 * val12 - val20 + val22)
                val gy = (-val00 - 2 * val01 - val02 + val20 + 2 * val21 + val22)

                val g = sqrt((gx * gx + gy * gy).toDouble()).toInt()

                if (g > threshold) {
                    val alpha = min(255, (g * edgeBoost).toInt())
                    destPixels[rowCurr + x] = (alpha shl 24) or 0x000000
                } else {
                    destPixels[rowCurr + x] = Color.TRANSPARENT
                }
            }
        }

        dest.setPixels(destPixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Fast Separable 2-Pass Horizontal + Vertical Box Blur
     */
    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        val temp = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // Pass 1: Horizontal Blur
        for (y in 0 until height) {
            val yOffset = y * width
            for (x in 0 until width) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0

                for (dx in -radius..radius) {
                    val nx = x + dx
                    if (nx in 0 until width) {
                        val p = pixels[yOffset + nx]
                        rSum += (p shr 16) and 0xFF
                        gSum += (p shr 8) and 0xFF
                        bSum += p and 0xFF
                        count++
                    }
                }
                temp[yOffset + x] = (0xFF shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }

        // Pass 2: Vertical Blur
        for (x in 0 until width) {
            for (y in 0 until height) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0

                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny in 0 until height) {
                        val p = temp[ny * width + x]
                        rSum += (p shr 16) and 0xFF
                        gSum += (p shr 8) and 0xFF
                        bSum += p and 0xFF
                        count++
                    }
                }
                pixels[y * width + x] = (0xFF shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }

        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }
}
