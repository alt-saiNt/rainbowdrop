package com.example.rainbowdrop.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TimeLapseExporter(private val context: Context) {

    suspend fun export(
        baseBitmap: Bitmap,
        history: List<CanvasAction>,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val framesDir = File(context.cacheDir, "frames")
        framesDir.deleteRecursively()
        framesDir.mkdirs()

        val canvasBitmap = Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        val brushPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 20f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Generate frames
        history.forEachIndexed { index, action ->
            when (action.type) {
                com.example.rainbowdrop.data.ActionType.DRAW -> {
                    brushPaint.color = action.color
                    action.path?.let { canvas.drawPath(it, brushPaint) }
                }
                com.example.rainbowdrop.data.ActionType.FILL -> {
                    if (action.x != null && action.y != null) {
                        floodFill(canvasBitmap, action.x, action.y, action.color)
                    }
                }
            }
            
            // Save frame every action or every few actions
            val frameFile = File(framesDir, String.format("frame_%04d.jpg", index))
            FileOutputStream(frameFile).use { out ->
                canvasBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            onProgress(index.toFloat() / history.size)
        }

        val outputVideo = File(context.externalCacheDir, "timelapse_${System.currentTimeMillis()}.mp4")
        val command = "-y -framerate 10 -i ${framesDir.absolutePath}/frame_%04d.jpg -c:v libx264 -pix_fmt yuv420p ${outputVideo.absolutePath}"
        
        val session = FFmpegKit.execute(command)
        if (ReturnCode.isSuccess(session.returnCode)) {
            outputVideo
        } else {
            null
        }
    }

    private fun floodFill(bitmap: Bitmap, x: Int, y: Int, targetColor: Int) {
        val srcColor = bitmap.getPixel(x, y)
        if (srcColor == targetColor) return

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val queue = java.util.LinkedList<Int>()
        queue.add(y * width + x)

        while (queue.isNotEmpty()) {
            val pos = queue.poll()!!
            val cx = pos % width
            val cy = pos / width
            
            if (pixels[pos] == srcColor) {
                pixels[pos] = targetColor
                if (cx > 0) queue.add(pos - 1)
                if (cx < width - 1) queue.add(pos + 1)
                if (cy > 0) queue.add(pos - width)
                if (cy < height - 1) queue.add(pos + width)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
