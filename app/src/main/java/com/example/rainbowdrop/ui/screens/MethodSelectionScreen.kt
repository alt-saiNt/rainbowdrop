package com.example.rainbowdrop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rainbowdrop.engine.FilterType
import com.example.rainbowdrop.engine.Tool

sealed class ColoringMethod(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tool: Tool,
    val isMystery: Boolean,
    val accentColor: Color
) {
    data object Freestyle : ColoringMethod(
        title = "Freestyle Brush",
        description = "Paint freely with organic strokes. The outlines remain visible to guide your creativity.",
        icon = Icons.Rounded.Brush,
        tool = Tool.BRUSH,
        isMystery = false,
        accentColor = Color(0xFF3B82F6) // Sapphire Blue
    )

    data object OutlineFill : ColoringMethod(
        title = "Outline Bucket Fill",
        description = "Tap to flood-fill outlined regions instantly. Perfect for neat and structured coloring.",
        icon = Icons.Rounded.FormatColorFill,
        tool = Tool.BUCKET,
        isMystery = false,
        accentColor = Color(0xFF10B981) // Emerald Green
    )

    data object MysteryFill : ColoringMethod(
        title = "Mystery Image Fill",
        description = "Outlines are completely hidden! Revel in the surprise as the picture is unraveled color by color.",
        icon = Icons.Rounded.AutoFixHigh,
        tool = Tool.BUCKET,
        isMystery = true,
        accentColor = Color(0xFFF59E0B) // Amber Gold
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodSelectionScreen(
    imageUri: String,
    filterType: FilterType,
    onMethodSelected: (Tool, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val methods = listOf(
        ColoringMethod.OutlineFill,
        ColoringMethod.Freestyle,
        ColoringMethod.MysteryFill
    )
    var selectedMethod by remember { mutableStateOf<ColoringMethod>(ColoringMethod.OutlineFill) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "CHOOSE METHOD",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "SELECT COLORING METHOD",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Configure how your alchemical canvas responds to your touches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    methods.forEach { method ->
                        val isSelected = selectedMethod == method
                        
                        RivetedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMethod = method },
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icon Box
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(method.accentColor.copy(alpha = 0.2f))
                                        .border(1.dp, method.accentColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = method.icon,
                                        contentDescription = method.title,
                                        tint = method.accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Text details
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = method.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = method.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Confirm Button
            Button(
                onClick = { onMethodSelected(selectedMethod.tool, selectedMethod.isMystery) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "LOCK METHOD & START COLORING",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Start")
                }
            }
        }
    }
}
