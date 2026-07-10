package com.example.rainbowdrop.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }

    val defaultPalette = remember {
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta, Color.Cyan, Color.Black)
    }
    val displayColors = remember(extractedColors) {
        extractedColors.ifEmpty { defaultPalette }
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

                // Scale down bitmap to prevent OutOfMemory and slow filter/fill rendering
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
                
                // Clean outlines always generated from original/processed blurred image
                val outlines = ImageProcessor.getOutlines(mutableBitmap)
                
                // Shading details only present for INK_SKETCH and TATTOO_FLASH
                val shading = if (initialFilter == FilterType.INK_SKETCH || initialFilter == FilterType.TATTOO_FLASH) {
                    processed
                } else {
                    null
                }

                // Dynamically extract colors from the original downscaled image
                val paletteInts = ColorExtractor.extractPalette(mutableBitmap)
                val paletteColors = paletteInts.map { Color(it) }

                withContext(Dispatchers.Main) {
                    outlineBitmap = outlines
                    shadingBitmap = shading
                    extractedColors = paletteColors
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
            withContext(Dispatchers.Main) {
                outlineBitmap = outlines
                shadingBitmap = shading
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Coloring Canvas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { saveProgress() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save Progress")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            isExporting = true
                            processedBitmap?.let { base ->
                                val video = exporter.export(base, undoRedoManager.currentHistory) { progress ->
                                    exportProgress = progress
                                }
                                if (video != null) {
                                    // Handle video export success
                                }
                            }
                            isExporting = false
                        }
                    }) {
                        Icon(Icons.Rounded.IosShare, contentDescription = "Export")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    if (isExporting) {
                        LinearProgressIndicator(
                            progress = { exportProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ColorPicker(
                                colors = displayColors,
                                selectedColor = currentColor,
                                onColorSelected = { currentColor = it }
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { undoRedoManager.undo() }) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
                            }
                            IconButton(onClick = { undoRedoManager.redo() }) {
                                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
                            }
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
                ColoringCanvas(
                    baseBitmap = processedBitmap!!,
                    outlineBitmap = outlines,
                    coloredBitmap = originalBitmap,
                    currentColor = currentColor.toArgb(),
                    currentTool = activeTool,
                    isMysteryMode = activeMysteryMode,
                    undoRedoManager = undoRedoManager,
                    shadingBitmap = shadingBitmap,
                    paletteColors = displayColors.map { it.toArgb() }
                )
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun FilterCarousel(
    selectedFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterType.entries.forEach { filter ->
            item {
                FilterItem(
                    filter = filter,
                    isSelected = filter == selectedFilter,
                    onClick = { onFilterSelected(filter) }
                )
            }
        }
    }
}

@Composable
fun FilterItem(
    filter: FilterType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    InputChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(filter.displayName) }
    )
}

@Composable
fun ToolBar(
    currentTool: Tool,
    onToolSelected: (Tool) -> Unit,
    colors: List<Color>,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToolButton(
                icon = Icons.Rounded.FormatColorFill,
                isSelected = currentTool == Tool.BUCKET,
                onClick = { onToolSelected(Tool.BUCKET) }
            )
            ToolButton(
                icon = Icons.Rounded.Brush,
                isSelected = currentTool == Tool.BRUSH,
                onClick = { onToolSelected(Tool.BRUSH) }
            )
        }

        ColorPicker(
            colors = colors,
            selectedColor = currentColor,
            onColorSelected = onColorSelected
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
            }
        }
    }
}

@Composable
fun ToolButton(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        )
    ) {
        Icon(icon, contentDescription = null)
    }
}

@Composable
fun ColorPicker(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onColorSelected(color) }
                    .padding(4.dp)
            ) {
                if (color == selectedColor) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(2.dp, Color.White, CircleShape)
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
