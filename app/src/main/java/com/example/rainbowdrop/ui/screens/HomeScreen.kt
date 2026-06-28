package com.example.rainbowdrop.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rainbowdrop.ui.theme.RainbowDropTheme

@Composable
fun HomeScreen(
    onImageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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
            // Blueprint gears background
            DecorativeGearsBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RivetedCard(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    borderColor = MaterialTheme.colorScheme.primary
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "RainbowDrop",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "TRANS-ALCHEMICAL COLORING ENGINE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                pickMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "PICK PHOTO TO START",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
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
    // Draw center hole
    drawCircle(
        color = color,
        radius = innerRadius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
    )
    // Draw outer rim
    drawCircle(
        color = color,
        radius = outerRadius - 10.dp.toPx(),
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
    )
    
    // Draw teeth
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
