package com.example.rainbowdrop.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUri: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember {
        Room.databaseBuilder(context, AppDatabase::class.java, "rainbow_drop_db").build()
    }
    val undoRedoManager = remember { UndoRedoManager() }
    val exporter = remember { TimeLapseExporter(context) }

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentFilter by remember { mutableStateOf(FilterType.INK_SKETCH) }
    var currentTool by remember { mutableStateOf(Tool.BUCKET) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var isMysteryMode by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                if (imageUri.isEmpty()) return@withContext
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, android.net.Uri.parse(imageUri))
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, android.net.Uri.parse(imageUri))
                }
                originalBitmap = bitmap
                processedBitmap = ImageProcessor.applyFilter(bitmap, currentFilter)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(currentFilter, originalBitmap) {
        val original = originalBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            processedBitmap = ImageProcessor.applyFilter(original, currentFilter)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                    IconButton(onClick = { isMysteryMode = !isMysteryMode }) {
                        Icon(
                            if (isMysteryMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle Mystery Mode"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    if (isExporting) {
                        LinearProgressIndicator(
                            progress = { exportProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    FilterCarousel(
                        selectedFilter = currentFilter,
                        onFilterSelected = { currentFilter = it }
                    )
                    ToolBar(
                        currentTool = currentTool,
                        onToolSelected = { currentTool = it },
                        currentColor = currentColor,
                        onColorSelected = { currentColor = it },
                        onUndo = { undoRedoManager.undo() },
                        onRedo = { undoRedoManager.redo() }
                    )
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
            processedBitmap?.let { bitmap ->
                ColoringCanvas(
                    baseBitmap = bitmap,
                    currentColor = currentColor.toArgb(),
                    currentTool = currentTool,
                    isMysteryMode = isMysteryMode,
                    undoRedoManager = undoRedoManager
                )
            } ?: CircularProgressIndicator()
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
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta, Color.Cyan, Color.Black)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        EditorScreen(imageUri = "", onBack = {})
    }
}
