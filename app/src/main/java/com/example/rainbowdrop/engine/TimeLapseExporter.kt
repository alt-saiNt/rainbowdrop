package com.example.rainbowdrop.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class TimeLapseExporter(private val context: Context) {

    /**
     * Renders and exports the full painting progression as an MP4 time-lapse directly to MediaStore.
     */
    suspend fun export(
        baseBitmap: Bitmap,
        history: List<CanvasAction>,
        onProgress: (Float) -> Unit
    ): Uri? = withContext(Dispatchers.IO) {
        val framesDir = File(context.cacheDir, "frames")
        framesDir.deleteRecursively()
        framesDir.mkdirs()

        val canvasBitmap = Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        val brushPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 24f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // 1. Generate progression frames
        history.forEachIndexed { index, action ->
            when (action.type) {
                com.example.rainbowdrop.data.ActionType.DRAW -> {
                    brushPaint.color = action.color
                    action.path?.let { canvas.drawPath(it, brushPaint) }
                }
                com.example.rainbowdrop.data.ActionType.FILL -> {
                    if (action.x != null && action.y != null) {
                        fastFloodFill(canvasBitmap, action.x, action.y, action.color)
                    }
                }
            }

            val frameFile = File(framesDir, String.format("frame_%04d.jpg", index))
            FileOutputStream(frameFile).use { out ->
                canvasBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            onProgress((index + 1).toFloat() / history.size * 0.8f)
        }

        if (history.isEmpty()) {
            val singleFrame = File(framesDir, "frame_0000.jpg")
            FileOutputStream(singleFrame).use { out ->
                canvasBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        }

        // 2. Encode to MP4 using FFmpeg
        val outputVideo = File(context.externalCacheDir, "timelapse_${System.currentTimeMillis()}.mp4")
        val command = "-y -framerate 12 -i ${framesDir.absolutePath}/frame_%04d.jpg -c:v mpeg4 -pix_fmt yuv420p ${outputVideo.absolutePath}"

        val session = FFmpegKit.execute(command)
        onProgress(0.95f)

        val galleryUri = if (ReturnCode.isSuccess(session.returnCode) && outputVideo.exists()) {
            saveVideoToGallery(outputVideo)
        } else {
            null
        }

        onProgress(1.0f)
        framesDir.deleteRecursively()
        outputVideo.delete()
        galleryUri
    }

    /**
     * Exports a high-resolution flattened static PNG of the artwork directly to the photo gallery.
     */
    suspend fun exportStaticImage(
        baseBitmap: Bitmap,
        coloringBitmap: Bitmap,
        outlineBitmap: Bitmap?,
        isMysteryMode: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        val mergedBitmap = Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mergedBitmap)

        // 1. Paper background
        val paperPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, baseBitmap.width.toFloat(), baseBitmap.height.toFloat(), paperPaint)

        // 2. User pigment layer
        canvas.drawBitmap(coloringBitmap, 0f, 0f, null)

        // 3. Outlines
        if (outlineBitmap != null) {
            if (!isMysteryMode) {
                canvas.drawBitmap(outlineBitmap, 0f, 0f, null)
            } else {
                val paint = Paint().apply { alpha = 24 }
                canvas.drawBitmap(outlineBitmap, 0f, 0f, paint)
            }
        }

        // 4. Save to MediaStore
        val filename = "RainbowDrop_Art_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RainbowDrop")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                mergedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }
        mergedBitmap.recycle()
        uri
    }

    private fun saveVideoToGallery(videoFile: File): Uri? {
        val filename = "RainbowDrop_TimeLapse_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RainbowDrop")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { outStream ->
                FileInputStream(videoFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }
        return uri
    }

    private fun fastFloodFill(bitmap: Bitmap, startX: Int, startY: Int, targetColor: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val srcColor = bitmap.getPixel(startX, startY)
        if (srcColor == targetColor) return

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val queue = java.util.ArrayDeque<Int>(width * 2)
        queue.add(startY * width + startX)

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            val cy = pos / width
            var cx = pos % width

            if (pixels[pos] != srcColor) continue

            var leftX = cx
            while (leftX > 0 && pixels[cy * width + (leftX - 1)] == srcColor) {
                leftX--
            }
            var rightX = cx
            while (rightX < width - 1 && pixels[cy * width + (rightX + 1)] == srcColor) {
                rightX++
            }

            for (x in leftX..rightX) {
                val idx = cy * width + x
                pixels[idx] = targetColor

                if (cy > 0 && pixels[(cy - 1) * width + x] == srcColor) {
                    queue.add((cy - 1) * width + x)
                }
                if (cy < height - 1 && pixels[(cy + 1) * width + x] == srcColor) {
                    queue.add((cy + 1) * width + x)
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
