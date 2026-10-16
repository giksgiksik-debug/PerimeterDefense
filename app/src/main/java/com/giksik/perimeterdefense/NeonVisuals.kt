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
import androidx.compose.ui.graphics.Path
import com.giksik.perimeterdefense.domain.AmmoColor
import com.giksik.perimeterdefense.domain.CellState
import com.giksik.perimeterdefense.domain.GridCell
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


/**
 * Vector-rendered tile emblem: crisp at every phone density and lightweight
 * enough to render the full 6×6 board without shipping sprite sheets.
 */
@Composable
fun NeonTileGlyph(cell: GridCell, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val unit = size.minDimension
        val thin = (unit * 0.055f).coerceAtLeast(1.dp.toPx())
        val brightCyan = Color(0xFF67F4FF)
        val color = when {
            cell.hasMine -> Color(0xFFFF536B)
            cell.state == CellState.HIDDEN -> brightCyan
            cell.ammoColor == AmmoColor.RED -> Color(0xFFFF647C)
            cell.ammoColor == AmmoColor.BLUE -> Color(0xFF4DE3FF)
            cell.ammoColor == AmmoColor.YELLOW -> Color(0xFFFFC857)
            cell.ammoColor == AmmoColor.GREEN_HEAL -> Color(0xFF5CFFB0)
            else -> Color(0xFF90A8BA)
        }

        if (cell.state == CellState.HIDDEN) {
            val r = unit * 0.27f
            val hex = Path().apply {
                for (i in 0..5) {
                    val angle = Math.PI / 3.0 * i - Math.PI / 6.0
                    val p = Offset(cx + (kotlin.math.cos(angle) * r).toFloat(), cy + (kotlin.math.sin(angle) * r).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(hex, color.copy(alpha = 0.9f), style = Stroke(width = thin))
            drawCircle(color.copy(alpha = 0.12f), radius = unit * 0.13f, center = center)
            drawCircle(color, radius = unit * 0.035f, center = center)
            drawLine(color.copy(alpha = 0.65f), Offset(cx - r * 0.45f, cy), Offset(cx + r * 0.45f, cy), strokeWidth = thin * 0.7f)
        } else if (cell.hasMine) {
            val r = unit * 0.22f
            drawCircle(color.copy(alpha = 0.16f), radius = unit * 0.34f, center = center)
            drawCircle(color, radius = r, center = center, style = Stroke(width = thin * 1.5f))
            for (i in 0 until 8) {
                val angle = Math.PI / 4.0 * i
                val inner = r * 1.05f
                val outer = r * 1.55f
                drawLine(
                    color,
                    Offset(cx + (kotlin.math.cos(angle) * inner).toFloat(), cy + (kotlin.math.sin(angle) * inner).toFloat()),
                    Offset(cx + (kotlin.math.cos(angle) * outer).toFloat(), cy + (kotlin.math.sin(angle) * outer).toFloat()),
                    strokeWidth = thin
                )
            }
            drawCircle(color, radius = unit * 0.055f, center = center)
        } else when (cell.ammoColor) {
            AmmoColor.RED -> {
                drawCircle(color.copy(alpha = 0.14f), radius = unit * 0.34f, center = center)
                drawCircle(color, radius = unit * 0.23f, center = center, style = Stroke(width = thin * 1.4f))
                drawCircle(color, radius = unit * 0.07f, center = center)
                val r = unit * 0.37f
                drawLine(color, Offset(cx - r, cy), Offset(cx - r * 0.58f, cy), strokeWidth = thin)
                drawLine(color, Offset(cx + r * 0.58f, cy), Offset(cx + r, cy), strokeWidth = thin)
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy - r * 0.58f), strokeWidth = thin)
                drawLine(color, Offset(cx, cy + r * 0.58f), Offset(cx, cy + r), strokeWidth = thin)
            }
            AmmoColor.BLUE -> {
                drawCircle(color.copy(alpha = 0.14f), radius = unit * 0.34f, center = center)
                drawCircle(color, radius = unit * 0.25f, center = center, style = Stroke(width = thin))
                for (i in 0 until 6) {
                    val angle = Math.PI / 3.0 * i
                    val end = Offset(cx + (kotlin.math.cos(angle) * unit * 0.29f).toFloat(), cy + (kotlin.math.sin(angle) * unit * 0.29f).toFloat())
                    drawLine(color, center, end, strokeWidth = thin)
                }
                drawCircle(color, radius = unit * 0.055f, center = center)
            }
            AmmoColor.YELLOW -> {
                drawCircle(color.copy(alpha = 0.14f), radius = unit * 0.34f, center = center)
                val bolt = Path().apply {
                    moveTo(cx + unit * 0.06f, cy - unit * 0.32f)
                    lineTo(cx - unit * 0.19f, cy + unit * 0.02f)
                    lineTo(cx - unit * 0.015f, cy + unit * 0.02f)
                    lineTo(cx - unit * 0.07f, cy + unit * 0.30f)
                    lineTo(cx + unit * 0.20f, cy - unit * 0.08f)
                    lineTo(cx + unit * 0.035f, cy - unit * 0.08f)
                    close()
                }
                drawPath(bolt, color)
            }
            AmmoColor.GREEN_HEAL -> {
                drawCircle(color.copy(alpha = 0.14f), radius = unit * 0.34f, center = center)
                drawCircle(color.copy(alpha = 0.9f), radius = unit * 0.27f, center = center, style = Stroke(width = thin))
                drawLine(color, Offset(cx - unit * 0.17f, cy), Offset(cx + unit * 0.17f, cy), strokeWidth = thin * 2.2f)
                drawLine(color, Offset(cx, cy - unit * 0.17f), Offset(cx, cy + unit * 0.17f), strokeWidth = thin * 2.2f)
            }
            AmmoColor.NONE -> drawCircle(color.copy(alpha = 0.7f), radius = unit * 0.06f, center = center)
        }

        if (cell.isJammed) {
            drawCircle(Color(0xFFFFA34D), radius = unit * 0.39f, center = center, style = Stroke(width = thin))
            drawLine(Color(0xFFFFA34D), Offset(cx - unit * 0.12f, cy - unit * 0.12f), Offset(cx + unit * 0.12f, cy + unit * 0.12f), strokeWidth = thin)
            drawLine(Color(0xFFFFA34D), Offset(cx + unit * 0.12f, cy - unit * 0.12f), Offset(cx - unit * 0.12f, cy + unit * 0.12f), strokeWidth = thin)
        }
    }
}
