package com.example.rainbowdrop.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    coloredBitmap: Bitmap? = null,
    shadingBitmap: Bitmap? = null,
    paletteColors: List<Int> = emptyList()
) {
    var scale by remember { mutableFloatStateOf(1f) }
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

    var redrawTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(undoRedoManager.changeCount, baseBitmap, coloredBitmap) {
        coloringBitmap.eraseColor(Color.TRANSPARENT)
        undoRedoManager.currentHistory.forEach { action ->
            when (action.type) {
                com.example.rainbowdrop.data.ActionType.DRAW -> {
                    if (isMysteryMode) {
                        brushPaint.shader = BitmapShader(
                            coloredBitmap ?: baseBitmap,
                            Shader.TileMode.CLAMP,
                            Shader.TileMode.CLAMP
                        )
                    } else {
                        brushPaint.shader = null
                        brushPaint.color = action.color
                    }
                    action.path?.let { coloringCanvas.drawPath(it, brushPaint) }
                }
                com.example.rainbowdrop.data.ActionType.FILL -> {
                    if (action.x != null && action.y != null) {
                        floodFill(coloringBitmap, outlineBitmap, coloredBitmap ?: baseBitmap, action.x, action.y, action.color, isMysteryMode)
                    }
                }
            }
        }
        redrawTrigger++
    }

    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(currentColor, baseBitmap, redrawTrigger, currentTool, coloredBitmap, paletteColors) {
        if (currentTool != Tool.BUCKET) {
            maskBitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            val mask = generateHighlightMask(coloredBitmap ?: baseBitmap, coloringBitmap, currentColor, paletteColors)
            withContext(Dispatchers.Main) {
                maskBitmap = mask
            }
        }
    }
    
    // Checkered background for active color highlighting
    val checkerboardPaint = remember {
        Paint().apply {
            shader = BitmapShader(
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
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
        }
    }

    Box(
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
                                brushPaint.shader = BitmapShader(
                                    coloredBitmap ?: baseBitmap,
                                    Shader.TileMode.CLAMP,
                                    Shader.TileMode.CLAMP
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
                                val colorSource = coloredBitmap ?: baseBitmap
                                val targetPixelColor = colorSource.getPixel(x, y)
                                
                                // Only allow bucket fill if selected color matches target pixel color (or mystery mode)
                                if (isMysteryMode || isColorMatch(currentColor, targetPixelColor)) {
                                    val currentPixelColor = coloringBitmap.getPixel(x, y)
                                    // Lock check: if already colored, don't overwrite (unless mystery mode)
                                    if (currentPixelColor == Color.TRANSPARENT || isMysteryMode) {
                                        floodFill(coloringBitmap, outlineBitmap, colorSource, x, y, currentColor, isMysteryMode)
                                        undoRedoManager.addAction(CanvasAction(com.example.rainbowdrop.data.ActionType.FILL, Tool.BUCKET, currentColor, x = x, y = y))
                                        redrawTrigger++
                                    }
                                }
                            }
                        }
                    )
                }
            }
    ) 
    {
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
            redrawTrigger // Dependency to trigger redraw
            
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
            val paperPaint = Paint().apply { color = Color.WHITE }
                drawRect(0f, 0f, bitmapWidth, bitmapHeight, paperPaint)

                // Highlight active areas with checkerboard if using bucket and mask is ready
                if (currentTool == Tool.BUCKET && maskBitmap != null) {
                    val saveCount = saveLayer(0f, 0f, bitmapWidth, bitmapHeight, null)
                    drawBitmap(maskBitmap!!, 0f, 0f, null)
                    val maskedCheckerPaint = Paint(checkerboardPaint).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    }
                    drawRect(0f, 0f, bitmapWidth, bitmapHeight, maskedCheckerPaint)
                    restoreToCount(saveCount)
                }

                // Draw user coloring
                drawBitmap(coloringBitmap, 0f, 0f, null)

                // Draw shading details on top of colored areas
                if (shadingBitmap != null) {
                    val saveCount = saveLayer(0f, 0f, bitmapWidth, bitmapHeight, null)
                    drawBitmap(coloringBitmap, 0f, 0f, null)
                    val shadingPaint = Paint().apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                    }
                    drawBitmap(shadingBitmap, 0f, 0f, shadingPaint)
                    restoreToCount(saveCount)
                }
                
                // Draw outline bitmap on top
                if (!isMysteryMode) {
                    drawBitmap(outlineBitmap, 0f, 0f, null)
                } else {
                    val paint = Paint().apply { alpha = 20 }
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
    outline: Bitmap,
    base: Bitmap,
    x: Int,
    y: Int,
    targetColor: Int,
    isMysteryMode: Boolean = false
) {
    val srcColor = coloring.getPixel(x, y)
    if (!isMysteryMode && srcColor == targetColor) return
    if (isMysteryMode && srcColor != Color.TRANSPARENT) return

    val width = coloring.width
    val height = coloring.height
    val coloringPixels = IntArray(width * height)
    val outlinePixels = IntArray(width * height)
    val basePixels = IntArray(width * height)
    coloring.getPixels(coloringPixels, 0, width, 0, 0, width, height)
    outline.getPixels(outlinePixels, 0, width, 0, 0, width, height)
    base.getPixels(basePixels, 0, width, 0, 0, width, height)

    val radius = 2 // Closes gaps up to 4 pixels wide

    // Helper to check if a pixel is near any outline boundary (on-the-fly dilation)
    fun isNearOutline(cx: Int, cy: Int): Boolean {
        for (dy in -radius..radius) {
            val ny = cy + dy
            if (ny in 0 until height) {
                for (dx in -radius..radius) {
                    val nx = cx + dx
                    if (nx in 0 until width) {
                        val pixel = outlinePixels[ny * width + nx]
                        val alpha = (pixel shr 24) and 0xFF
                        if (alpha > 120) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    if (isNearOutline(x, y)) return // Tap directly on or near an outline boundary

    val queue: Queue<Int> = LinkedList()
    queue.add(y * width + x)

    while (queue.isNotEmpty()) {
        val pos = queue.poll()!!
        val cx = pos % width
        val cy = pos / width
        
        if (coloringPixels[pos] == srcColor) {
            if (!isNearOutline(cx, cy)) { // On-the-fly dilation boundary check
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
    targetColor: Int,
    paletteColors: List<Int>
): Bitmap {
    val width = base.width
    val height = base.height
    val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    val basePixels = IntArray(width * height)
    val coloringPixels = IntArray(width * height)
    base.getPixels(basePixels, 0, width, 0, 0, width, height)
    coloring.getPixels(coloringPixels, 0, width, 0, 0, width, height)
    
    val maskPixels = IntArray(width * height)
    
    for (i in basePixels.indices) {
        val cColor = coloringPixels[i]
        if (((cColor shr 24) and 0xFF) > 0) continue
        
        val bColor = basePixels[i]
        if (isColorMatch(targetColor, bColor)) {
            maskPixels[i] = 0xFFFFFFFF.toInt()
        }
    }
    
    mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
    return mask
}

private fun getClosestPaletteColor(pixelColor: Int, palette: List<Int>): Int {
    if (palette.isEmpty()) return pixelColor
    var minIdx = 0
    var minDist = Double.MAX_VALUE
    
    val rP = (pixelColor shr 16) and 0xFF
    val gP = (pixelColor shr 8) and 0xFF
    val bP = pixelColor and 0xFF
    
    for (i in palette.indices) {
        val color = palette[i]
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        
        val dist = kotlin.math.abs(r - rP) + kotlin.math.abs(g - gP) + kotlin.math.abs(b - bP)
        if (dist < minDist) {
            minDist = dist.toDouble()
            minIdx = i
        }
    }
    return palette[minIdx]
}

private fun isColorMatch(colorA: Int, colorB: Int): Boolean {
    val rA = (colorA shr 16) and 0xFF
    val gA = (colorA shr 8) and 0xFF
    val bA = colorA and 0xFF
    
    val rB = (colorB shr 16) and 0xFF
    val gB = (colorB shr 8) and 0xFF
    val bB = colorB and 0xFF
    
    val dist = kotlin.math.abs(rA - rB) + kotlin.math.abs(gA - gB) + kotlin.math.abs(bA - bB)
    return dist < 120 // Forgiving matching threshold for color families
}
