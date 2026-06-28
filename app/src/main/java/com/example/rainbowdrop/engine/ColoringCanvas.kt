package com.example.rainbowdrop.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.Queue

enum class Tool {
    BUCKET, BRUSH
}

@Composable
fun ColoringCanvas(
    baseBitmap: Bitmap,
    outlineBitmap: Bitmap,
    currentColor: Int,
    currentTool: Tool,
    isMysteryMode: Boolean,
    undoRedoManager: UndoRedoManager,
    modifier: Modifier = Modifier,
    coloredBitmap: Bitmap? = null
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale *= zoomChange
        offset += offsetChange
    }

    // This bitmap will hold the user's coloring
    val coloringBitmap = remember(baseBitmap) {
        Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
    }
    val coloringCanvas = remember(coloringBitmap) { Canvas(coloringBitmap) }
    val brushPaint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 20f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    var redrawTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(undoRedoManager.changeCount, baseBitmap) {
        coloringBitmap.eraseColor(Color.TRANSPARENT)
        undoRedoManager.currentHistory.forEach { action ->
            when (action.type) {
                com.example.rainbowdrop.data.ActionType.DRAW -> {
                    if (isMysteryMode) {
                        brushPaint.shader = android.graphics.BitmapShader(
                            baseBitmap,
                            android.graphics.Shader.TileMode.CLAMP,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    } else {
                        brushPaint.shader = null
                        brushPaint.color = action.color
                    }
                    action.path?.let { coloringCanvas.drawPath(it, brushPaint) }
                }
                com.example.rainbowdrop.data.ActionType.FILL -> {
                    if (action.x != null && action.y != null) {
                        floodFill(coloringBitmap, baseBitmap, action.x, action.y, action.color, isMysteryMode)
                    }
                }
            }
        }
        redrawTrigger++
    }

    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(currentColor, baseBitmap, redrawTrigger, currentTool, coloredBitmap) {
        if (currentTool != Tool.BUCKET) {
            maskBitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            val mask = generateHighlightMask(coloredBitmap ?: baseBitmap, coloringBitmap, currentColor)
            withContext(Dispatchers.Main) {
                maskBitmap = mask
            }
        }
    }
    
    // Checkered background for active color highlighting
    val checkerboardPaint = remember {
        Paint().apply {
            shader = android.graphics.BitmapShader(
                Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
                    val c = Canvas(this)
                    val p = Paint()
                    p.color = Color.LTGRAY
                    c.drawRect(0f, 0f, 10f, 10f, p)
                    c.drawRect(10f, 10f, 20f, 20f, p)
                    p.color = Color.WHITE
                    c.drawRect(10f, 0f, 20f, 10f, p)
                    c.drawRect(0f, 10f, 10f, 20f, p)
                },
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .transformable(state = transformState)
            .pointerInput(currentTool, currentColor) {
                if (currentTool == Tool.BRUSH) {
                    var currentPath = Path()
                    var currentPoints = mutableListOf<Pair<Float, Float>>()
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            currentPath = Path()
                            currentPoints = mutableListOf()
                            val bitmapOffset = screenToBitmap(startOffset, scale, offset, size.width.toFloat(), size.height.toFloat(), baseBitmap.width.toFloat(), baseBitmap.height.toFloat())
                            currentPath.moveTo(bitmapOffset.x, bitmapOffset.y)
                            currentPoints.add(bitmapOffset.x to bitmapOffset.y)
                        },
                        onDrag = { change, _ ->
                            val bitmapOffset = screenToBitmap(change.position, scale, offset, size.width.toFloat(), size.height.toFloat(), baseBitmap.width.toFloat(), baseBitmap.height.toFloat())
                            currentPath.lineTo(bitmapOffset.x, bitmapOffset.y)
                            currentPoints.add(bitmapOffset.x to bitmapOffset.y)
                            
                            if (isMysteryMode) {
                                brushPaint.shader = android.graphics.BitmapShader(
                                    baseBitmap,
                                    android.graphics.Shader.TileMode.CLAMP,
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                            } else {
                                brushPaint.shader = null
                                brushPaint.color = currentColor
                            }
                            coloringCanvas.drawPath(currentPath, brushPaint)
                            redrawTrigger++
                        },
                        onDragEnd = {
                            undoRedoManager.addAction(CanvasAction(com.example.rainbowdrop.data.ActionType.DRAW, Tool.BRUSH, currentColor, currentPath, points = currentPoints))
                        }
                    )
                } else if (currentTool == Tool.BUCKET) {
                    detectTapGestures(
                        onTap = { pressOffset ->
                            val bitmapOffset = screenToBitmap(pressOffset, scale, offset, size.width.toFloat(), size.height.toFloat(), baseBitmap.width.toFloat(), baseBitmap.height.toFloat())
                            val x = bitmapOffset.x.toInt()
                            val y = bitmapOffset.y.toInt()
                            if (x in 0 until baseBitmap.width && y in 0 until baseBitmap.height) {
                                floodFill(coloringBitmap, baseBitmap, x, y, currentColor, isMysteryMode)
                                undoRedoManager.addAction(CanvasAction(com.example.rainbowdrop.data.ActionType.FILL, Tool.BUCKET, currentColor, x = x, y = y))
                                redrawTrigger++
                            }
                        }
                    )
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            val dummy = redrawTrigger // Dependency to trigger redraw
            
            drawContext.canvas.nativeCanvas.apply {
                val screenWidth = size.width
                val screenHeight = size.height
                val bitmapWidth = baseBitmap.width.toFloat()
                val bitmapHeight = baseBitmap.height.toFloat()
                
                val scaleFactor = minOf(screenWidth / bitmapWidth, screenHeight / bitmapHeight)
                val actualBitmapWidth = bitmapWidth * scaleFactor
                val actualBitmapHeight = bitmapHeight * scaleFactor
                
                val left = (screenWidth - actualBitmapWidth) / 2f
                val top = (screenHeight - actualBitmapHeight) / 2f
                
                save()
                translate(left, top)
                scale(scaleFactor, scaleFactor)
                
                // Draw solid white paper background matching the image boundaries
                val paperPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
                drawRect(0f, 0f, bitmapWidth, bitmapHeight, paperPaint)

                // Highlight active areas with checkerboard if using bucket and mask is ready
                if (currentTool == Tool.BUCKET && maskBitmap != null) {
                    val saveCount = saveLayer(0f, 0f, bitmapWidth, bitmapHeight, null)
                    drawBitmap(maskBitmap!!, 0f, 0f, null)
                    val maskedCheckerPaint = android.graphics.Paint(checkerboardPaint).apply {
                        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
                    }
                    drawRect(0f, 0f, bitmapWidth, bitmapHeight, maskedCheckerPaint)
                    restoreToCount(saveCount)
                }

                // Draw user coloring
                drawBitmap(coloringBitmap, 0f, 0f, null)
                
                // Draw outline bitmap on top
                if (!isMysteryMode) {
                    drawBitmap(outlineBitmap, 0f, 0f, null)
                } else {
                    val paint = android.graphics.Paint().apply { alpha = 20 }
                    drawBitmap(outlineBitmap, 0f, 0f, paint)
                }
                
                restore()
            }
        }
    }
}

private fun screenToBitmap(
    screenOffset: Offset,
    scale: Float,
    translation: Offset,
    screenWidth: Float,
    screenHeight: Float,
    bitmapWidth: Float,
    bitmapHeight: Float
): Offset {
    val centerX = screenWidth / 2f
    val centerY = screenHeight / 2f
    
    val normalizedX = (screenOffset.x - translation.x - centerX) / scale + centerX
    val normalizedY = (screenOffset.y - translation.y - centerY) / scale + centerY
    
    val scaleFactor = minOf(screenWidth / bitmapWidth, screenHeight / bitmapHeight)
    val actualBitmapWidth = bitmapWidth * scaleFactor
    val actualBitmapHeight = bitmapHeight * scaleFactor
    
    val left = (screenWidth - actualBitmapWidth) / 2f
    val top = (screenHeight - actualBitmapHeight) / 2f
    
    val bitmapX = (normalizedX - left) / scaleFactor
    val bitmapY = (normalizedY - top) / scaleFactor
    
    return Offset(bitmapX, bitmapY)
}

private fun floodFill(
    coloring: Bitmap,
    base: Bitmap,
    x: Int,
    y: Int,
    targetColor: Int,
    isMysteryMode: Boolean = false
) {
    val srcColor = coloring.getPixel(x, y)
    if (!isMysteryMode && srcColor == targetColor) return
    if (isMysteryMode && srcColor != Color.TRANSPARENT) return

    val basePixel = base.getPixel(x, y)
    val baseAlpha = (basePixel shr 24) and 0xFF
    if (baseAlpha > 120) return // Tap directly on an outline boundary

    val width = coloring.width
    val height = coloring.height
    val coloringPixels = IntArray(width * height)
    val basePixels = IntArray(width * height)
    coloring.getPixels(coloringPixels, 0, width, 0, 0, width, height)
    base.getPixels(basePixels, 0, width, 0, 0, width, height)

    val queue: Queue<Int> = LinkedList()
    queue.add(y * width + x)

    while (queue.isNotEmpty()) {
        val pos = queue.poll()!!
        val cx = pos % width
        val cy = pos / width
        
        if (coloringPixels[pos] == srcColor) {
            val baseA = (basePixels[pos] shr 24) and 0xFF
            if (baseA <= 120) { // Not an outline boundary
                coloringPixels[pos] = if (isMysteryMode) basePixels[pos] else targetColor
                if (cx > 0) queue.add(pos - 1)
                if (cx < width - 1) queue.add(pos + 1)
                if (cy > 0) queue.add(pos - width)
                if (cy < height - 1) queue.add(pos + width)
            }
        }
    }
    coloring.setPixels(coloringPixels, 0, width, 0, 0, width, height)
}

private fun generateHighlightMask(
    base: Bitmap,
    coloring: Bitmap,
    targetColor: Int
): Bitmap {
    val width = base.width
    val height = base.height
    val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    val basePixels = IntArray(width * height)
    val coloringPixels = IntArray(width * height)
    base.getPixels(basePixels, 0, width, 0, 0, width, height)
    coloring.getPixels(coloringPixels, 0, width, 0, 0, width, height)
    
    val maskPixels = IntArray(width * height)
    
    val targetR = (targetColor shr 16) and 0xFF
    val targetG = (targetColor shr 8) and 0xFF
    val targetB = targetColor and 0xFF
    
    for (i in basePixels.indices) {
        val cColor = coloringPixels[i]
        if (((cColor shr 24) and 0xFF) > 0) continue
        
        val bColor = basePixels[i]
        val bR = (bColor shr 16) and 0xFF
        val bG = (bColor shr 8) and 0xFF
        val bB = bColor and 0xFF
        
        val dist = Math.abs(bR - targetR) + Math.abs(bG - targetG) + Math.abs(bB - targetB)
        if (dist < 45) {
            maskPixels[i] = 0xFFFFFFFF.toInt()
        }
    }
    
    mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
    return mask
}
