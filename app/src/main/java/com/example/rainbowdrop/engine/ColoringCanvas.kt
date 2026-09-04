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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.rainbowdrop.data.ActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.ceil

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
    paletteColors: List<Int> = emptyList(),
    colorMap: IntArray? = null,
    coloringDocument: ColoringDocument? = null,
    isFreeformMode: Boolean = false,
    isPeeking: Boolean = false,
    recenterTrigger: Int = 0
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val guidedTapRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 10f)
        offset += offsetChange
    }

    // High-performance canvas bitmap for user pigment layers
    val coloringBitmap = remember(baseBitmap) {
        Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888)
    }
    val coloringCanvas = remember(coloringBitmap) { Canvas(coloringBitmap) }
    
    val brushPaint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 24f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    var redrawTrigger by remember { mutableIntStateOf(0) }

    // Reconstruct canvas from history non-destructively
    LaunchedEffect(undoRedoManager.changeCount, baseBitmap, coloredBitmap, coloringDocument) {
        coloringBitmap.eraseColor(Color.TRANSPARENT)
        val colorSource = coloredBitmap ?: baseBitmap
        undoRedoManager.currentHistory.forEach { action ->
            when (action.type) {
                ActionType.DRAW -> {
                    if (isMysteryMode || !isFreeformMode) {
                        brushPaint.shader = BitmapShader(
                            colorSource,
                            Shader.TileMode.CLAMP,
                            Shader.TileMode.CLAMP
                        )
                    } else {
                        brushPaint.shader = null
                        brushPaint.color = action.color
                    }
                    action.path?.let { coloringCanvas.drawPath(it, brushPaint) }
                }
                ActionType.FILL -> {
                    if (action.x != null && action.y != null) {
                        val document = coloringDocument
                        if (document != null && currentTool == Tool.BUCKET &&
                            !isMysteryMode && !isFreeformMode
                        ) {
                            fillGuidedRegion(
                                coloring = coloringBitmap,
                                document = document,
                                x = action.x,
                                y = action.y,
                                selectedColor = action.color
                            )
                        } else {
                            fastFloodFill(
                                coloring = coloringBitmap,
                                outline = outlineBitmap,
                                base = colorSource,
                                startX = action.x,
                                startY = action.y,
                                targetColor = action.color,
                                paletteColors = paletteColors,
                                colorMap = colorMap,
                                isMysteryMode = isMysteryMode,
                                isFreeform = isFreeformMode
                            )
                        }
                    }
                }
            }
        }
        redrawTrigger++
    }

    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Highlight the still-unfilled regions that belong to the selected color.
    LaunchedEffect(currentColor, baseBitmap, redrawTrigger, currentTool, coloredBitmap, paletteColors, colorMap, coloringDocument, isFreeformMode) {
        if (isFreeformMode || (currentTool != Tool.BUCKET && !isMysteryMode)) {
            maskBitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            val mask = generateHighlightMask(
                base = coloredBitmap ?: baseBitmap,
                coloring = coloringBitmap,
                targetColor = currentColor,
                paletteColors = paletteColors,
                colorMap = colorMap,
                coloringDocument = coloringDocument
            )
            withContext(Dispatchers.Main) {
                maskBitmap = mask
            }
        }
    }

    // A translucent gold hatch reads as guidance without looking like applied pigment.
    val checkerboardPaint = remember {
        Paint().apply {
            shader = BitmapShader(
                Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888).apply {
                    val c = Canvas(this)
                    c.drawColor(Color.argb(26, 255, 196, 54))
                    val stripe = Paint().apply {
                        color = Color.argb(105, 255, 196, 54)
                        strokeWidth = 4f
                        isAntiAlias = true
                    }
                    c.drawLine(-7f, 7f, 7f, -7f, stripe)
                    c.drawLine(0f, 28f, 28f, 0f, stripe)
                    c.drawLine(21f, 35f, 35f, 21f, stripe)
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
            .pointerInput(currentTool, currentColor, isMysteryMode, baseBitmap, coloringDocument, guidedTapRadiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isSingleTouch = true
                    val startPos = down.position
                    var lastPos = startPos
                    var totalDragDistance = 0f

                    val colorSource = coloredBitmap ?: baseBitmap

                    // Setup brush shader or solid color
                    if (isMysteryMode) {
                        brushPaint.shader = BitmapShader(
                            colorSource,
                            Shader.TileMode.CLAMP,
                            Shader.TileMode.CLAMP
                        )
                    } else {
                        brushPaint.shader = null
                        brushPaint.color = currentColor
                    }

                    var activePath = Path()
                    val activePoints = mutableListOf<Pair<Float, Float>>()

                    val startBmpOffset = screenToBitmap(
                        startPos, scale, offset,
                        size.width.toFloat(), size.height.toFloat(),
                        baseBitmap.width.toFloat(), baseBitmap.height.toFloat()
                    )

                    if (currentTool == Tool.BRUSH) {
                        activePath.moveTo(startBmpOffset.x, startBmpOffset.y)
                        activePoints.add(startBmpOffset.x to startBmpOffset.y)
                        // Draw initial point/dab
                        coloringCanvas.drawCircle(startBmpOffset.x, startBmpOffset.y, 12f, brushPaint.apply { style = Paint.Style.FILL })
                        brushPaint.style = Paint.Style.STROKE
                        redrawTrigger++
                    }

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }

                        if (pressedCount > 1) {
                            // Multi-touch detected: yield to transformable for smooth zoom/pan
                            isSingleTouch = false
                        }

                        if (isSingleTouch && pressedCount == 1) {
                            val change = event.changes.firstOrNull { it.pressed }
                            if (change != null) {
                                val dragDelta = change.positionChange()
                                totalDragDistance += dragDelta.getDistance()

                                if (currentTool == Tool.BRUSH) {
                                    val currentBmpOffset = screenToBitmap(
                                        change.position, scale, offset,
                                        size.width.toFloat(), size.height.toFloat(),
                                        baseBitmap.width.toFloat(), baseBitmap.height.toFloat()
                                    )
                                    val lastBmpOffset = screenToBitmap(
                                        lastPos, scale, offset,
                                        size.width.toFloat(), size.height.toFloat(),
                                        baseBitmap.width.toFloat(), baseBitmap.height.toFloat()
                                    )

                                    // Incremental bezier-smoothed stroke
                                    val midX = (lastBmpOffset.x + currentBmpOffset.x) / 2f
                                    val midY = (lastBmpOffset.y + currentBmpOffset.y) / 2f
                                    activePath.quadTo(lastBmpOffset.x, lastBmpOffset.y, midX, midY)
                                    activePoints.add(currentBmpOffset.x to currentBmpOffset.y)

                                    // Direct incremental hardware canvas draw for 120fps response
                                    coloringCanvas.drawLine(
                                        lastBmpOffset.x, lastBmpOffset.y,
                                        currentBmpOffset.x, currentBmpOffset.y,
                                        brushPaint
                                    )
                                    redrawTrigger++
                                    change.consume()
                                }
                                lastPos = change.position
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Gesture completion handling
                    if (isSingleTouch) {
                        if (currentTool == Tool.BRUSH && activePoints.size > 1) {
                            undoRedoManager.addAction(
                                CanvasAction(
                                    type = ActionType.DRAW,
                                    tool = Tool.BRUSH,
                                    color = currentColor,
                                    path = activePath,
                                    points = activePoints
                                )
                            )
                        } else if (currentTool == Tool.BUCKET && totalDragDistance < 20f) {
                            val tapBmpOffset = screenToBitmap(
                                startPos, scale, offset,
                                size.width.toFloat(), size.height.toFloat(),
                                baseBitmap.width.toFloat(), baseBitmap.height.toFloat()
                            )
                            val x = tapBmpOffset.x.toInt()
                            val y = tapBmpOffset.y.toInt()

                            if (x in 0 until baseBitmap.width && y in 0 until baseBitmap.height) {
                                val document = coloringDocument
                                if (document != null && !isMysteryMode && !isFreeformMode) {
                                    val fitScale = minOf(
                                        size.width.toFloat() / baseBitmap.width,
                                        size.height.toFloat() / baseBitmap.height
                                    )
                                    val bitmapTapRadius = ceil(
                                        guidedTapRadiusPx / (scale * fitScale).coerceAtLeast(0.01f)
                                    ).toInt().coerceIn(1, 96)
                                    val result = fillGuidedRegion(
                                        coloring = coloringBitmap,
                                        document = document,
                                        x = x,
                                        y = y,
                                        selectedColor = currentColor,
                                        maxSnapDistance = bitmapTapRadius
                                    )
                                    if (result is GuidedFillResult.Filled) {
                                        val filledRegion = document.regions[result.regionId]
                                        val historyAnchor = filledRegion.runs.first()
                                        undoRedoManager.addAction(
                                            CanvasAction(
                                                type = ActionType.FILL,
                                                tool = Tool.BUCKET,
                                                color = currentColor,
                                                x = historyAnchor.startX,
                                                y = historyAnchor.y
                                            )
                                        )
                                        redrawTrigger++
                                    }
                                } else {
                                    val targetIdx = paletteColors.indexOf(currentColor)
                                    val pixelCluster = if (colorMap != null && colorMap.size == baseBitmap.width * baseBitmap.height) {
                                        colorMap[y * baseBitmap.width + x]
                                    } else {
                                        ColorExtractor.closestPaletteIndex(colorSource.getPixel(x, y), paletteColors)
                                    }

                                    // Legacy path retained for Creative, Challenge, and filtered styles.
                                    if (isFreeformMode || pixelCluster == targetIdx || targetIdx == -1) {
                                        val currentPixelColor = coloringBitmap.getPixel(x, y)
                                        if (((currentPixelColor shr 24) and 0xFF) == 0 || isFreeformMode) {
                                            fastFloodFill(
                                                coloring = coloringBitmap,
                                                outline = outlineBitmap,
                                                base = colorSource,
                                                startX = x,
                                                startY = y,
                                                targetColor = currentColor,
                                                paletteColors = paletteColors,
                                                colorMap = colorMap,
                                                isMysteryMode = isMysteryMode,
                                                isFreeform = isFreeformMode
                                            )
                                            undoRedoManager.addAction(
                                                CanvasAction(
                                                    type = ActionType.FILL,
                                                    tool = Tool.BUCKET,
                                                    color = currentColor,
                                                    x = x,
                                                    y = y
                                                )
                                            )
                                            redrawTrigger++
                                        }
                                    }
                                }
                            }
                        }
                    }
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
            redrawTrigger // Reactive dependency

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

                if (isPeeking) {
                    // Peek Mode: Display full original image cleanly
                    drawBitmap(coloredBitmap ?: baseBitmap, 0f, 0f, null)
                } else {
                    // 1. Parchment/Canvas paper foundation
                    val paperPaint = Paint().apply { color = Color.WHITE }
                    drawRect(0f, 0f, bitmapWidth, bitmapHeight, paperPaint)

                    // 2. Active color checkerboard highlight mask for guided coloring
                    if ((currentTool == Tool.BUCKET || isMysteryMode) && maskBitmap != null && !isFreeformMode) {
                        val saveCount = saveLayer(0f, 0f, bitmapWidth, bitmapHeight, null)
                        drawBitmap(maskBitmap!!, 0f, 0f, null)
                        val maskedCheckerPaint = Paint(checkerboardPaint).apply {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                        }
                        drawRect(0f, 0f, bitmapWidth, bitmapHeight, maskedCheckerPaint)
                        restoreToCount(saveCount)
                    }

                    // 3. User pigment coloring layer
                    drawBitmap(coloringBitmap, 0f, 0f, null)

                    // 4. Shading & texture overlay
                    if (shadingBitmap != null) {
                        val saveCount = saveLayer(0f, 0f, bitmapWidth, bitmapHeight, null)
                        drawBitmap(coloringBitmap, 0f, 0f, null)
                        val shadingPaint = Paint().apply {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                        }
                        drawBitmap(shadingBitmap, 0f, 0f, shadingPaint)
                        restoreToCount(saveCount)
                    }

                    // 5. Outline ink boundary layer
                    if (!isMysteryMode) {
                        drawBitmap(outlineBitmap, 0f, 0f, null)
                    } else {
                        // Subtle, delicate arcanepunk whisper outline (hint)
                        val hintPaint = Paint().apply { alpha = 45 }
                        drawBitmap(outlineBitmap, 0f, 0f, hintPaint)
                    }
                }

                restore()
            }
        }
    }
}

private fun fillGuidedRegion(
    coloring: Bitmap,
    document: ColoringDocument,
    x: Int,
    y: Int,
    selectedColor: Int,
    maxSnapDistance: Int = 0
): GuidedFillResult {
    val pixels = IntArray(document.width * document.height)
    coloring.getPixels(pixels, 0, document.width, 0, 0, document.width, document.height)
    val result = GuidedRegionPainter.fillAt(
        document = document,
        coloringPixels = pixels,
        x = x,
        y = y,
        selectedColor = selectedColor,
        maxSnapDistance = maxSnapDistance
    )
    if (result is GuidedFillResult.Filled) {
        coloring.setPixels(pixels, 0, document.width, 0, 0, document.width, document.height)
    }
    return result
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

/**
 * Ultra-fast Span-based Flood Fill algorithm.
 * In guided modes (Bucket / Mystery), fills with authentic image pixels (basePixels).
 * In Freeform mode, fills with selected pigment.
 */
private fun fastFloodFill(
    coloring: Bitmap,
    outline: Bitmap,
    base: Bitmap,
    startX: Int,
    startY: Int,
    targetColor: Int,
    paletteColors: List<Int>,
    colorMap: IntArray?,
    isMysteryMode: Boolean = false,
    isFreeform: Boolean = false
) {
    val width = coloring.width
    val height = coloring.height
    val srcColor = coloring.getPixel(startX, startY)

    if (!isFreeform && ((srcColor shr 24) and 0xFF) > 0) return

    val coloringPixels = IntArray(width * height)
    val outlinePixels = IntArray(width * height)
    val basePixels = IntArray(width * height)

    coloring.getPixels(coloringPixels, 0, width, 0, 0, width, height)
    outline.getPixels(outlinePixels, 0, width, 0, 0, width, height)
    base.getPixels(basePixels, 0, width, 0, 0, width, height)

    val targetIdx = paletteColors.indexOf(targetColor)

    fun isBoundary(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return true
        val p = outlinePixels[y * width + x]
        return ((p shr 24) and 0xFF) > 100
    }

    if (isBoundary(startX, startY) && !isMysteryMode) return

    fun matches(idx: Int): Boolean {
        if (coloringPixels[idx] != srcColor) return false
        if (!isFreeform && colorMap != null && colorMap.size == width * height && targetIdx != -1) {
            return colorMap[idx] == targetIdx
        }
        return true
    }

    val queue = ArrayDeque<Int>(width * 2)
    queue.add(startY * width + startX)

    while (queue.isNotEmpty()) {
        val pos = queue.removeFirst()
        val cy = pos / width
        val cx = pos % width

        if (isBoundary(cx, cy) && !isMysteryMode) continue
        if (!matches(pos)) continue

        var leftX = cx
        while (leftX > 0 && (isMysteryMode || !isBoundary(leftX - 1, cy)) && matches(cy * width + (leftX - 1))) {
            leftX--
        }

        var rightX = cx
        while (rightX < width - 1 && (isMysteryMode || !isBoundary(rightX + 1, cy)) && matches(cy * width + (rightX + 1))) {
            rightX++
        }

        for (x in leftX..rightX) {
            val idx = cy * width + x
            coloringPixels[idx] = if (isFreeform) targetColor else basePixels[idx]

            if (cy > 0) {
                val upIdx = (cy - 1) * width + x
                if ((isMysteryMode || !isBoundary(x, cy - 1)) && matches(upIdx)) {
                    queue.add(upIdx)
                }
            }
            if (cy < height - 1) {
                val downIdx = (cy + 1) * width + x
                if ((isMysteryMode || !isBoundary(x, cy + 1)) && matches(downIdx)) {
                    queue.add(downIdx)
                }
            }
        }
    }

    coloring.setPixels(coloringPixels, 0, width, 0, 0, width, height)
}

/**
 * Generates active color highlighting mask with zero overhead.
 * Highlights exactly the regions where the selected palette color belongs.
 */
private fun generateHighlightMask(
    base: Bitmap,
    coloring: Bitmap,
    targetColor: Int,
    paletteColors: List<Int>,
    colorMap: IntArray?,
    coloringDocument: ColoringDocument?
): Bitmap {
    val width = base.width
    val height = base.height
    val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val coloringPixels = IntArray(width * height)
    val maskPixels = IntArray(width * height)

    coloring.getPixels(coloringPixels, 0, width, 0, 0, width, height)

    val targetIdx = paletteColors.indexOf(targetColor)
    if (targetIdx == -1) return mask

    if (coloringDocument != null && coloringDocument.regionIdByPixel.size == width * height) {
        for (i in coloringPixels.indices) {
            val regionId = coloringDocument.regionIdByPixel[i]
            val requiredColorId = coloringDocument.regions.getOrNull(regionId)?.requiredColorId
            if ((coloringPixels[i] ushr 24) == 0 && requiredColorId == targetIdx) {
                maskPixels[i] = 0xFFFFFFFF.toInt()
            }
        }
    } else if (colorMap != null && colorMap.size == width * height) {
        for (i in coloringPixels.indices) {
            if ((coloringPixels[i] ushr 24) == 0 && colorMap[i] == targetIdx) {
                maskPixels[i] = 0xFFFFFFFF.toInt()
            }
        }
    } else {
        val basePixels = IntArray(width * height)
        base.getPixels(basePixels, 0, width, 0, 0, width, height)
        for (i in coloringPixels.indices) {
            if ((coloringPixels[i] ushr 24) == 0 && ColorExtractor.closestPaletteIndex(basePixels[i], paletteColors) == targetIdx) {
                maskPixels[i] = 0xFFFFFFFF.toInt()
            }
        }
    }

    mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
    return mask
}
