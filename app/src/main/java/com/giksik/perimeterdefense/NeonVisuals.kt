package com.giksik.perimeterdefense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Lightweight procedural sci-fi backdrop. Everything is drawn on-device:
 * no bitmap assets, network access, or gameplay-state changes are required.
 */
@Composable
fun NeonBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF10283A), Color(0xFF090E18), Color(0xFF060A11)),
                center = Offset(size.width * 0.5f, size.height * 0.28f),
                radius = size.maxDimension * 0.82f
            )
        )

        val step = 28.dp.toPx()
        val gridColor = Color(0xFF1C7890).copy(alpha = 0.12f)
        var x = 0f
        while (x <= size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.6.dp.toPx())
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.6.dp.toPx())
            y += step
        }

        // Corner HUD brackets
        val cyan = Color(0xFF00E5FF).copy(alpha = 0.42f)
        val length = 24.dp.toPx()
        val inset = 10.dp.toPx()
        val stroke = 1.5.dp.toPx()
        drawHudCorner(Offset(inset, inset), length, stroke, cyan, topLeft = true)
        drawHudCorner(Offset(size.width - inset, inset), length, stroke, cyan, topLeft = false)
        drawHudCorner(Offset(inset, size.height - inset), length, stroke, cyan, topLeft = false, bottom = true)
        drawHudCorner(Offset(size.width - inset, size.height - inset), length, stroke, cyan, topLeft = true, bottom = true)

        // Decorative scan nodes along the outer edge
        listOf(0.18f, 0.50f, 0.82f).forEach { fraction ->
            val cy = size.height * fraction
            drawCircle(Color(0xFF00E5FF).copy(alpha = 0.26f), radius = 2.2.dp.toPx(), center = Offset(inset, cy))
            drawCircle(Color(0xFFFF9F43).copy(alpha = 0.22f), radius = 2.2.dp.toPx(), center = Offset(size.width - inset, cy))
        }
    }
}

private fun DrawScope.drawHudCorner(
    anchor: Offset,
    length: Float,
    stroke: Float,
    color: Color,
    topLeft: Boolean,
    bottom: Boolean = false
) {
    val xDirection = if (topLeft) 1f else -1f
    val yDirection = if (bottom) -1f else 1f
    drawLine(color, anchor, Offset(anchor.x + length * xDirection, anchor.y), strokeWidth = stroke)
    drawLine(color, anchor, Offset(anchor.x, anchor.y + length * yDirection), strokeWidth = stroke)
}
