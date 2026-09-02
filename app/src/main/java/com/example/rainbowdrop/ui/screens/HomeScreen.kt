package com.example.rainbowdrop.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.example.rainbowdrop.data.AppDatabase
import com.example.rainbowdrop.data.ColoringProject
import com.example.rainbowdrop.engine.Tool
import com.example.rainbowdrop.ui.theme.RainbowDropTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onImageSelected: (String) -> Unit,
    onProjectSelected: (ColoringProject) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember {
        Room.databaseBuilder(context, AppDatabase::class.java, "rainbow_drop_db")
            .fallbackToDestructiveMigration(true)
            .build()
    }

    val savedProjects by db.projectDao().getAllProjects().collectAsState(initial = emptyList())

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImageSelected(uri.toString())
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Arcanepunk blueprint gears background
            DecorativeGearsBackground(modifier = Modifier.fillMaxSize())

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header & Primary Forge Action
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    RivetedCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = MaterialTheme.colorScheme.primary
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "RainbowDrop",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = "TRANS-ALCHEMICAL COLORING ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    pickMedia.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "FORGE NEW CANVAS",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // Vault Archives / Saved Projects
                if (savedProjects.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SAVED VAULT TRANSMUTATIONS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }

                    items(savedProjects, key = { it.id }) { project ->
                        SavedProjectCard(
                            project = project,
                            onOpen = { onProjectSelected(project) },
                            onDelete = {
                                scope.launch(Dispatchers.IO) {
                                    db.projectDao().deleteProject(project.id)
                                    db.projectDao().deleteHistoryForProject(project.id)
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun SavedProjectCard(
    project: ColoringProject,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(project.lastModified) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        sdf.format(Date(project.lastModified))
    }

    RivetedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tool Icon Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        project.isMysteryMode -> Icons.Rounded.AutoFixHigh
                        project.tool == Tool.BRUSH -> Icons.Rounded.Brush
                        else -> Icons.Rounded.FormatColorFill
                    },
                    contentDescription = null,
                    tint = if (project.isMysteryMode) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Project Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.filterType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (project.isMysteryMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MYSTERY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${project.tool.name} • $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Action Buttons
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onOpen,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Resume",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun DecorativeGearsBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val gearColor = Color(0xFF8A939E).copy(alpha = 0.05f) // Silver blueprint color with very low opacity

        // Gear top right
        drawGear(
            center = Offset(size.width - 50.dp.toPx(), 80.dp.toPx()),
            outerRadius = 90.dp.toPx(),
            innerRadius = 40.dp.toPx(),
            teethCount = 12,
            color = gearColor
        )

        // Small interlocking gear top right
        drawGear(
            center = Offset(size.width - 150.dp.toPx(), 40.dp.toPx()),
            outerRadius = 50.dp.toPx(),
            innerRadius = 22.dp.toPx(),
            teethCount = 8,
            color = gearColor
        )

        // Gear bottom left
        drawGear(
            center = Offset(50.dp.toPx(), size.height - 100.dp.toPx()),
            outerRadius = 110.dp.toPx(),
            innerRadius = 50.dp.toPx(),
            teethCount = 14,
            color = gearColor
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGear(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    teethCount: Int,
    color: Color
) {
    drawCircle(
        color = color,
        radius = innerRadius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
    )
    drawCircle(
        color = color,
        radius = outerRadius - 10.dp.toPx(),
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
    )

    val toothWidth = 8.dp.toPx()

    for (i in 0 until teethCount) {
        val angle = (i * 360f / teethCount) * (Math.PI / 180).toFloat()
        val cos = Math.cos(angle.toDouble()).toFloat()
        val sin = Math.sin(angle.toDouble()).toFloat()

        val startPoint = Offset(
            x = center.x + (outerRadius - 12.dp.toPx()) * cos,
            y = center.y + (outerRadius - 12.dp.toPx()) * sin
        )
        val endPoint = Offset(
            x = center.x + outerRadius * cos,
            y = center.y + outerRadius * sin
        )

        drawLine(
            color = color,
            start = startPoint,
            end = endPoint,
            strokeWidth = toothWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,navigationButtons=false")
@Composable
fun HomeScreenPreview() {
    RainbowDropTheme {
        HomeScreen(onImageSelected = {})
    }
}
