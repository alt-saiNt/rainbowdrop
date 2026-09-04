package com.example.rainbowdrop.engine

import android.graphics.Bitmap

fun OriginalColoringEngine.prepare(bitmap: Bitmap): ColoringDocument {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return prepare(bitmap.width, bitmap.height, pixels)
}

fun ColoringDocument.createFullOutlineBitmap(): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.setPixels(fullOutlinePixels, 0, width, 0, 0, width, height)
    }
