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
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Simple Edge Detection / Threshold for Ink Sketch
        val canvas = Canvas(dest)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f) // Grayscale
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        
        // Thresholding for a "sketch" look
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = dest.getPixel(x, y)
                val gray = Color.red(pixel)
                if (gray < 128) {
                    dest.setPixel(x, y, Color.BLACK)
                } else {
                    dest.setPixel(x, y, Color.WHITE)
                }
            }
        }
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
        // High saturation + Black outlines (simplified)
        return applyPopArt(src) // Placeholder for now, can be improved with edge detection
    }

    private fun applyWatercolor(src: Bitmap): Bitmap {
        // Blur + Saturation
        return src.copy(Bitmap.Config.ARGB_8888, true) // Placeholder
    }

    private fun applyTattooFlash(src: Bitmap): Bitmap {
        // High contrast + Limited palette
        return applyInkSketch(src) // Placeholder
    }
}
