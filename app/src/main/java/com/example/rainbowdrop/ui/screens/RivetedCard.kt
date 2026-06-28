package com.example.rainbowdrop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A beautiful, arcanepunk themed Card with brushed metal background,
 * glowing accent borders, and decorative silver rivets in each corner.
 */
@Composable
fun RivetedCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.secondary,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1B1B1F), Color(0xFF111113))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Draw rivets in the 4 corners
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rColor = Color(0xFF8A939E) // Silver rivet color
            val rRadius = 3.dp.toPx()
            val offset = 6.dp.toPx()
            
            // Top Left
            drawCircle(color = rColor, radius = rRadius, center = Offset(offset, offset))
            // Top Right
            drawCircle(color = rColor, radius = rRadius, center = Offset(size.width - offset, offset))
            // Bottom Left
            drawCircle(color = rColor, radius = rRadius, center = Offset(offset, size.height - offset))
            // Bottom Right
            drawCircle(color = rColor, radius = rRadius, center = Offset(size.width - offset, size.height - offset))
        }
        content()
    }
}
