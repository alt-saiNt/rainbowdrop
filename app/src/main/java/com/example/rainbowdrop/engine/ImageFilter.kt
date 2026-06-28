package com.example.rainbowdrop.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

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

    private fun applyInkSketch(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        
        // 1. Grayscale
        val gray = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        
        // 2. Invert Grayscale
        val inverted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val invCanvas = Canvas(inverted)
        val invPaint = Paint()
        val invMatrix = ColorMatrix(floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f
        ))
        invPaint.colorFilter = ColorMatrixColorFilter(invMatrix)
        invCanvas.drawBitmap(gray, 0f, 0f, invPaint)
        
        // 3. Blur inverted
        val blurred = boxBlur(inverted, 4)
        
        // 4. Color Dodge blend & output transparent outlines
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val grayPixels = IntArray(width * height)
        val blurPixels = IntArray(width * height)
        val destPixels = IntArray(width * height)
        
        gray.getPixels(grayPixels, 0, width, 0, 0, width, height)
        blurred.getPixels(blurPixels, 0, width, 0, 0, width, height)
        
        for (i in grayPixels.indices) {
            val gColor = grayPixels[i]
            val bColor = blurPixels[i]
            
            val gVal = (gColor shr 16) and 0xFF
            val bVal = (bColor shr 16) and 0xFF
            
            val denom = 255 - bVal
            val dodge = if (denom == 0) 255 else minOf(255, (gVal * 255) / denom)
            
            val alpha = 255 - dodge
            if (alpha > 30) {
                val boostedAlpha = minOf(255, (alpha * 1.5).toInt())
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

    private fun applyCharcoal(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(dest)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        
        // Charcoal effect: High contrast grayscale
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = dest.getPixel(x, y)
                val gray = Color.red(pixel)
                val newGray = if (gray < 80) 0 else if (gray > 200) 255 else gray
                dest.setPixel(x, y, Color.rgb(newGray, newGray, newGray))
            }
        }
        return dest
    }

    private fun applyPopArt(src: Bitmap): Bitmap {
        // Posterize + Vibrant Colors
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = src.getPixel(x, y)
                val r = (Color.red(pixel) / 64) * 64
                val g = (Color.green(pixel) / 64) * 64
                val b = (Color.blue(pixel) / 64) * 64
                dest.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return dest
    }

    private fun applyVectorPoster(src: Bitmap): Bitmap {
        // High posterization
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = src.getPixel(x, y)
                val r = (Color.red(pixel) / 128) * 128
                val g = (Color.green(pixel) / 128) * 128
                val b = (Color.blue(pixel) / 128) * 128
                dest.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return dest
    }

    private fun applyComicBook(src: Bitmap): Bitmap {
        return applyPopArt(src) // Placeholder for now, can be improved with edge detection
    }

    private fun applyWatercolor(src: Bitmap): Bitmap {
        return src.copy(Bitmap.Config.ARGB_8888, true) // Placeholder
    }

    private fun applyTattooFlash(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val gray = IntArray(width * height)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        
        val destPixels = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                val val00 = gray[(y - 1) * width + (x - 1)]
                val val01 = gray[(y - 1) * width + x]
                val val02 = gray[(y - 1) * width + (x + 1)]
                
                val val10 = gray[y * width + (x - 1)]
                val val12 = gray[y * width + (x + 1)]
                
                val val20 = gray[(y + 1) * width + (x - 1)]
                val val21 = gray[(y + 1) * width + x]
                val val22 = gray[(y + 1) * width + (x + 1)]
                
                val gx = (
                    -1 * val00 + 1 * val02
                    -2 * val10 + 2 * val12
                    -1 * val20 + 1 * val22
                )
                
                val gy = (
                    -1 * val00 - 2 * val01 - 1 * val02
                    +1 * val20 + 2 * val21 + 1 * val22
                )
                
                val g = Math.sqrt((gx * gx + gy * gy).toDouble())
                
                val edgeAlpha = minOf(255, (g * 1.8).toInt())
                if (edgeAlpha > 50) {
                    val boostedAlpha = minOf(255, (edgeAlpha * 1.3).toInt())
                    destPixels[idx] = (boostedAlpha shl 24) or 0x000000
                } else {
                    destPixels[idx] = Color.TRANSPARENT
                }
            }
        }
        
        dest.setPixels(destPixels, 0, width, 0, 0, width, height)
        return dest
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        val outPixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                for (dx in -radius..radius) {
                    val nx = x + dx
                    if (nx in 0 until width) {
                        val pixel = pixels[y * width + nx]
                        rSum += (pixel shr 16) and 0xFF
                        gSum += (pixel shr 8) and 0xFF
                        bSum += pixel and 0xFF
                        count++
                    }
                }
                val idx = y * width + x
                outPixels[idx] = (0xFF shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny in 0 until height) {
                        val pixel = outPixels[ny * width + x]
                        rSum += (pixel shr 16) and 0xFF
                        gSum += (pixel shr 8) and 0xFF
                        bSum += pixel and 0xFF
                        count++
                    }
                }
                val idx = y * width + x
                pixels[idx] = (0xFF shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }
        
        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }
}
