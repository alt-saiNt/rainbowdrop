package com.example.rainbowdrop.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.example.rainbowdrop.data.*
import com.example.rainbowdrop.engine.*
import com.example.rainbowdrop.ui.theme.RainbowDropTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUri: String,
    filterType: FilterType,
    tool: Tool,
    isMysteryMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val db = remember {
        Room.databaseBuilder(context, AppDatabase::class.java, "rainbow_drop_db")
            .fallbackToDestructiveMigration(true)
            .build()
    }
    val undoRedoManager = remember { UndoRedoManager() }
    val exporter = remember { TimeLapseExporter(context) }

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var outlineBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shadingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeFilter by remember { mutableStateOf(filterType) }
    var activeTool by remember { mutableStateOf(tool) }
    var activeMysteryMode by remember { mutableStateOf(isMysteryMode) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var extractedColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    var colorMap by remember { mutableStateOf<IntArray?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }

    var isPeeking by remember { mutableStateOf(false) }
    var recenterTrigger by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    val displayColors = remember(extractedColors) {
        extractedColors.ifEmpty { listOf(Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B)) }
    }

    var currentProjectId by remember { mutableStateOf<Long?>(null) }

    fun saveProgress() {
        scope.launch(Dispatchers.IO) {
            val image = originalBitmap ?: return@launch
            val filter = activeFilter
            val historyActions = undoRedoManager.currentHistory

            val projectId = currentProjectId
            val newId = if (projectId == null) {
                val project = ColoringProject(
                    originalImageUri = imageUri,
                    filterType = filter,
                    tool = activeTool,
                    isMysteryMode = activeMysteryMode
                )
                db.projectDao().insertProject(project)
            } else {
                val project = ColoringProject(
                    id = projectId,
                    originalImageUri = imageUri,
                    filterType = filter,
                    tool = activeTool,
                    isMysteryMode = activeMysteryMode
                )
                db.projectDao().updateProject(project)
                projectId
            }

            db.projectDao().deleteHistoryForProject(newId)
            historyActions.forEach { action ->
                val entry = ActionEntry(
                    projectId = newId,
                    actionType = action.type,
                    tool = action.tool,
                    color = action.color,
                    pathData = action.points?.serializePoints(),
                    x = action.x,
                    y = action.y
                )
                db.projectDao().insertAction(entry)
            }

            withContext(Dispatchers.Main) {
                currentProjectId = newId
                snackbarHostState.showSnackbar("✨ Alchemical progress sealed in Vault!")
            }
        }
    }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                if (imageUri.isEmpty()) return@withContext

                val project = db.projectDao().getProjectByUri(imageUri)
                var initialFilter = activeFilter

                if (project != null) {
                    val historyEntries = db.projectDao().getHistoryForProject(project.id).first()
                    val canvasActions = historyEntries.map { entry ->
                        val pointsList = entry.pathData?.deserializePoints()
                        CanvasAction(
                            type = entry.actionType,
                            tool = entry.tool,
                            color = entry.color,
                            path = pointsList?.toAndroidPath(),
                            x = entry.x,
                            y = entry.y,
                            points = pointsList
                        )
                    }
                    initialFilter = project.filterType
                    withContext(Dispatchers.Main) {
                        currentProjectId = project.id
                        activeFilter = project.filterType
                        activeTool = project.tool
                        activeMysteryMode = project.isMysteryMode
                        undoRedoManager.loadHistory(canvasActions)
                    }
                }

                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, android.net.Uri.parse(imageUri))
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, android.net.Uri.parse(imageUri))
                }

                // Scale down bitmap to prevent OutOfMemory and keep high 120fps response
                val maxDim = 1200
                val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val (w, h) = if (bitmap.width > bitmap.height) {
                        maxDim to (maxDim / aspect).toInt()
                    } else {
                        (maxDim * aspect).toInt() to maxDim
                    }
                    Bitmap.createScaledBitmap(bitmap, w, h, true)
                } else {
                    bitmap
                }

                val mutableBitmap = if (!scaled.isMutable) {
                    scaled.copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    scaled
                }

                originalBitmap = mutableBitmap
                val processed = ImageProcessor.applyFilter(mutableBitmap, initialFilter)
                processedBitmap = processed

                val outlines = ImageProcessor.getOutlines(mutableBitmap)
                val shading = if (initialFilter == FilterType.INK_SKETCH || initialFilter == FilterType.TATTOO_FLASH) {
                    processed
                } else {
                    null
                }

                val targetForPalette = processed
                val paletteInts = ColorExtractor.extractPalette(targetForPalette, targetColorCount = 18)
                val paletteColors = paletteInts.map { Color(it) }
                val map = ColorExtractor.generateColorMap(targetForPalette, paletteInts)

                withContext(Dispatchers.Main) {
                    outlineBitmap = outlines
                    shadingBitmap = shading
                    extractedColors = paletteColors
                    colorMap = map
                    if (paletteColors.isNotEmpty()) {
                        currentColor = paletteColors.first()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(activeFilter, originalBitmap) {
        val original = originalBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val processed = ImageProcessor.applyFilter(original, activeFilter)
            processedBitmap = processed
            val outlines = ImageProcessor.getOutlines(original)
            val shading = if (activeFilter == FilterType.INK_SKETCH || activeFilter == FilterType.TATTOO_FLASH) {
                processed
            } else {
                null
            }

            val targetForPalette = processed
            val paletteInts = ColorExtractor.extractPalette(targetForPalette, targetColorCount = 18)
            val paletteColors = paletteInts.map { Color(it) }
            val map = ColorExtractor.generateColorMap(targetForPalette, paletteInts)

            withContext(Dispatchers.Main) {
                outlineBitmap = outlines
                shadingBitmap = shading
                extractedColors = paletteColors
                colorMap = map
                if (paletteColors.isNotEmpty() && !paletteColors.contains(currentColor)) {
                    currentColor = paletteColors.first()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isMysteryMode) "Mystery Crucible" else "Transmutation Canvas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${activeFilter.displayName} • ${activeTool.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { saveProgress() }) {
                        Icon(
                            Icons.Rounded.Save,
                            contentDescription = "Save Progress",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            isExporting = true
                            processedBitmap?.let { base ->
                                val videoUri = exporter.export(base, undoRedoManager.currentHistory) { progress ->
                                    exportProgress = progress
                                }
                                if (videoUri != null) {
                                    snackbarHostState.showSnackbar("🎬 Time-lapse video saved to Gallery!")
                                } else {
                                    snackbarHostState.showSnackbar("⚠️ Export failed. Please try again.")
                                }
                            }
                            isExporting = false
                        }
                    }) {
                        Icon(
                            Icons.Rounded.IosShare,
                            contentDescription = "Export Time-Lapse",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save Progress", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(Icons.Rounded.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    saveProgress()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Finished PNG", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        processedBitmap?.let { base ->
                                            val imageUri = exporter.exportStaticImage(
                                                baseBitmap = base,
                                                coloringBitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888).apply {
                                                    val c = Canvas(this)
                                                    val p = Paint().apply {
                                                        isAntiAlias = true
                                                        style = Paint.Style.STROKE
                                                        strokeWidth = 24f
                                                        strokeCap = Paint.Cap.ROUND
                                                        strokeJoin = Paint.Join.ROUND
                                                    }
                                                    undoRedoManager.currentHistory.forEach { action ->
                                                        if (action.type == ActionType.DRAW) {
                                                            p.color = action.color
                                                            action.path?.let { c.drawPath(it, p) }
                                                        }
                                                    }
                                                },
                                                outlineBitmap = outlineBitmap,
                                                isMysteryMode = activeMysteryMode
                                            )
                                            if (imageUri != null) {
                                                snackbarHostState.showSnackbar("🖼️ High-Res PNG saved to Gallery!")
                                            }
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Start New Project", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                                onClick = {
                                    showMenu = false
                                    onBack()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Return to Menu", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                                onClick = {
                                    showMenu = false
                                    onBack()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 6.dp)
                ) {
                    if (isExporting) {
                        LinearProgressIndicator(
                            progress = { exportProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 1. Horizontally Scrolling Color Palette
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        ColorPickerRibbon(
                            colors = displayColors,
                            selectedColor = currentColor,
                            onColorSelected = { currentColor = it }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 1.dp)

                    // 2. Streamlined Primary Tool Ribbon Carousel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tool Toggle (Bucket vs Brush)
                        FilterChip(
                            selected = activeTool == Tool.BUCKET,
                            onClick = { activeTool = Tool.BUCKET },
                            label = { Text("Bucket", fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(Icons.Rounded.FormatColorFill, contentDescription = "Bucket Fill", modifier = Modifier.size(18.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        FilterChip(
                            selected = activeTool == Tool.BRUSH,
                            onClick = { activeTool = Tool.BRUSH },
                            label = { Text("Brush", fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Brush, contentDescription = "Freestyle Brush", modifier = Modifier.size(18.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        // Hold to Peek Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isPeeking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        isPeeking = true
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (!event.changes.any { it.pressed }) break
                                            }
                                        } finally {
                                            isPeeking = false
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Visibility,
                                    contentDescription = "Hold to Peek",
                                    tint = if (isPeeking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPeeking) "Peeking..." else "Hold to Peek",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPeeking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Recenter / Zoom to Fit
                        OutlinedButton(
                            onClick = { recenterTrigger++ },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(
                                Icons.Rounded.CenterFocusStrong,
                                contentDescription = "Recenter",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Recenter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }

                        // Undo & Redo Levers
                        IconButton(
                            onClick = { undoRedoManager.undo() },
                            enabled = undoRedoManager.currentHistory.isNotEmpty()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = "Undo",
                                tint = if (undoRedoManager.currentHistory.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(
                            onClick = { undoRedoManager.redo() }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Redo,
                                contentDescription = "Redo",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            val outlines = outlineBitmap
            if (outlines != null && processedBitmap != null) {
                val isFreeform = (activeTool == Tool.BRUSH && !activeMysteryMode)
                ColoringCanvas(
                    baseBitmap = processedBitmap!!,
                    outlineBitmap = outlines,
                    coloredBitmap = originalBitmap,
                    currentColor = currentColor.toArgb(),
                    currentTool = activeTool,
                    isMysteryMode = activeMysteryMode,
                    undoRedoManager = undoRedoManager,
                    shadingBitmap = shadingBitmap,
                    paletteColors = displayColors.map { it.toArgb() },
                    colorMap = colorMap,
                    isFreeformMode = isFreeform,
                    isPeeking = isPeeking,
                    recenterTrigger = recenterTrigger
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Aligning Canvas Pigments...", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
fun ColorPickerRibbon(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { color ->
            val isSelected = color == selectedColor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditorScreenPreview() {
    RainbowDropTheme {
        EditorScreen(
            imageUri = "",
            filterType = FilterType.INK_SKETCH,
            tool = Tool.BUCKET,
            isMysteryMode = false,
            onBack = {}
        )
    }
}
