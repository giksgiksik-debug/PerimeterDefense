package com.giksgik.perimeterdefense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.giksgik.perimeterdefense.domain.AmmoColor
import com.giksgik.perimeterdefense.domain.CellState
import com.giksgik.perimeterdefense.domain.GridCell
import kotlin.math.cos
import kotlin.math.sin

private val BackdropGradientColors = listOf(Color(0xFF10283A), Color(0xFF090E18), Color(0xFF060A11))
private val BackdropGridColor = Color(0xFF1C7890).copy(alpha = 0.12f)
private val BackdropCornerCyan = Color(0xFF00E5FF).copy(alpha = 0.42f)
private val BackdropNodeCyan = Color(0xFF00E5FF).copy(alpha = 0.26f)
private val BackdropNodeAmber = Color(0xFFFF9F43).copy(alpha = 0.22f)
private val ScanNodeFractions = floatArrayOf(0.18f, 0.50f, 0.82f)

private val GlyphHiddenCyan = Color(0xFF67F4FF)
private val GlyphFlagAmber = Color(0xFFFFB74D)
private val GlyphMineRed = Color(0xFFFF536B)
private val GlyphAmmoRed = Color(0xFFFF647C)
private val GlyphAmmoBlue = Color(0xFF4DE3FF)
private val GlyphAmmoYellow = Color(0xFFFFC857)
private val GlyphAmmoGreen = Color(0xFF5CFFB0)
private val GlyphNeutral = Color(0xFF90A8BA)
private val GlyphJammedAmber = Color(0xFFFFA34D)

/**
 * Lightweight procedural sci-fi backdrop cached via drawWithCache so brushes
 * and layout metrics are only rebuilt when the viewport size changes.
 */
@Composable
fun NeonBackdrop(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val radialBrush = Brush.radialGradient(
                    colors = BackdropGradientColors,
                    center = Offset(size.width * 0.5f, size.height * 0.28f),
                    radius = size.maxDimension * 0.82f
                )
                val step = 28.dp.toPx()
                val gridStroke = 0.6.dp.toPx()
                val length = 24.dp.toPx()
                val inset = 10.dp.toPx()
                val cornerStroke = 1.5.dp.toPx()
                val nodeRadius = 2.2.dp.toPx()

                onDrawBehind {
                    drawRect(brush = radialBrush)

                    var x = 0f
                    while (x <= size.width) {
                        drawLine(BackdropGridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = gridStroke)
                        x += step
                    }
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(BackdropGridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = gridStroke)
                        y += step
                    }

                    // Corner HUD brackets
                    drawHudCorner(Offset(inset, inset), length, cornerStroke, BackdropCornerCyan, topLeft = true)
                    drawHudCorner(Offset(size.width - inset, inset), length, cornerStroke, BackdropCornerCyan, topLeft = false)
                    drawHudCorner(Offset(inset, size.height - inset), length, cornerStroke, BackdropCornerCyan, topLeft = false, bottom = true)
                    drawHudCorner(Offset(size.width - inset, size.height - inset), length, cornerStroke, BackdropCornerCyan, topLeft = true, bottom = true)

                    // Decorative scan nodes along the outer edge
                    for (fraction in ScanNodeFractions) {
                        val cy = size.height * fraction
                        drawCircle(BackdropNodeCyan, radius = nodeRadius, center = Offset(inset, cy))
                        drawCircle(BackdropNodeAmber, radius = nodeRadius, center = Offset(size.width - inset, cy))
                    }
                }
            }
    )
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
    val reusablePath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val unit = size.minDimension
        val thin = (unit * 0.055f).coerceAtLeast(1.dp.toPx())
        val thinStroke = Stroke(width = thin)

        // Check HIDDEN and FLAGGED before hasMine so hidden mines are never leaked visually.
        val color = when {
            cell.state == CellState.HIDDEN -> GlyphHiddenCyan
            cell.state == CellState.FLAGGED -> GlyphFlagAmber
            cell.hasMine -> GlyphMineRed
            cell.ammoColor == AmmoColor.RED -> GlyphAmmoRed
            cell.ammoColor == AmmoColor.BLUE -> GlyphAmmoBlue
            cell.ammoColor == AmmoColor.YELLOW -> GlyphAmmoYellow
            cell.ammoColor == AmmoColor.GREEN_HEAL -> GlyphAmmoGreen
            else -> GlyphNeutral
        }

        when {
            cell.state == CellState.HIDDEN -> {
                val r = unit * 0.27f
                reusablePath.reset()
                for (i in 0..5) {
                    val angle = Math.PI / 3.0 * i - Math.PI / 6.0
                    val px = cx + (cos(angle) * r).toFloat()
                    val py = cy + (sin(angle) * r).toFloat()
                    if (i == 0) reusablePath.moveTo(px, py) else reusablePath.lineTo(px, py)
                }
                reusablePath.close()
                drawPath(reusablePath, color.copy(alpha = 0.9f), style = thinStroke)
                drawCircle(color.copy(alpha = 0.12f), radius = unit * 0.13f, center = center)
                drawCircle(color, radius = unit * 0.035f, center = center)
                drawLine(color.copy(alpha = 0.65f), Offset(cx - r * 0.45f, cy), Offset(cx + r * 0.45f, cy), strokeWidth = thin * 0.7f)
            }

            cell.state == CellState.FLAGGED -> {
                // Tactical warning pennant for flagged tiles.
                val poleX = cx - unit * 0.12f
                val topY = cy - unit * 0.28f
                val bottomY = cy + unit * 0.28f
                drawCircle(color.copy(alpha = 0.14f), radius = unit * 0.34f, center = center)
                drawLine(color, Offset(poleX, topY), Offset(poleX, bottomY), strokeWidth = thin * 1.4f)
                reusablePath.reset()
                reusablePath.moveTo(poleX, topY)
                reusablePath.lineTo(cx + unit * 0.24f, cy - unit * 0.11f)
                reusablePath.lineTo(poleX, cy + unit * 0.05f)
                reusablePath.close()
                drawPath(reusablePath, color)
                drawLine(color, Offset(cx - unit * 0.22f, bottomY), Offset(cx + unit * 0.12f, bottomY), strokeWidth = thin * 1.2f)
            }

            cell.hasMine -> {
                val r = unit * 0.22f
                drawCircle(color.copy(alpha = 0.16f), radius = unit * 0.34f, center = center)
                drawCircle(color, radius = r, center = center, style = Stroke(width = thin * 1.5f))
                for (i in 0 until 8) {
                    val angle = Math.PI / 4.0 * i
                    val inner = r * 1.05f
                    val outer = r * 1.55f
                    drawLine(
                        color,
                        Offset(cx + (cos(angle) * inner).toFloat(), cy + (sin(angle) * inner).toFloat()),
                        Offset(cx + (cos(angle) * outer).toFloat(), cy + (sin(angle) * outer).toFloat()),
                        strokeWidth = thin
                    )
                }
                drawCircle(color, radius = unit * 0.055f, center = center)
            }

            else -> when (cell.ammoColor) {
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
                    drawCircle(color, radius = unit * 0.25f, center = center, style = thinStroke)
                    for (i in 0 until 6) {
                        val angle = Math.PI / 3.0 * i
                        val end = Offset(cx + (cos(angle) * unit * 0.29f).toFloat(), cy + (sin(angle) * unit * 0.29f).toFloat())
                        drawLine(color, center, end, strokeWidth = thin)
                    }
                    drawCircle(color, radius = unit * 0.055f, center = center)
                }
                AmmoColor.YELLOW -> {
                    drawCircle(color.copy(alpha = 0.14f), radius = unit * 0.34f, center = center)
                    reusablePath.reset()
                    reusablePath.moveTo(cx + unit * 0.06f, cy - unit * 0.32f)
                    reusablePath.lineTo(cx - unit * 0.19f, cy + unit * 0.02f)
                    reusablePath.lineTo(cx - unit * 0.015f, cy + unit * 0.02f)
                    reusablePath.lineTo(cx - unit * 0.07f, cy + unit * 0.30f)
                    reusablePath.lineTo(cx + unit * 0.20f, cy - unit * 0.08f)
                    reusablePath.lineTo(cx + unit * 0.035f, cy - unit * 0.08f)
                    reusablePath.close()
                    drawPath(reusablePath, color)
                }
                AmmoColor.GREEN_HEAL -> {
                    drawCircle(color.copy(alpha = 0.14f), radius = unit * 0.34f, center = center)
                    drawCircle(color.copy(alpha = 0.9f), radius = unit * 0.27f, center = center, style = thinStroke)
                    drawLine(color, Offset(cx - unit * 0.17f, cy), Offset(cx + unit * 0.17f, cy), strokeWidth = thin * 2.2f)
                    drawLine(color, Offset(cx, cy - unit * 0.17f), Offset(cx, cy + unit * 0.17f), strokeWidth = thin * 2.2f)
                }
                AmmoColor.NONE -> drawCircle(color.copy(alpha = 0.7f), radius = unit * 0.06f, center = center)
            }
        }

        if (cell.isJammed) {
            drawCircle(GlyphJammedAmber, radius = unit * 0.39f, center = center, style = thinStroke)
            drawLine(GlyphJammedAmber, Offset(cx - unit * 0.12f, cy - unit * 0.12f), Offset(cx + unit * 0.12f, cy + unit * 0.12f), strokeWidth = thin)
            drawLine(GlyphJammedAmber, Offset(cx + unit * 0.12f, cy - unit * 0.12f), Offset(cx - unit * 0.12f, cy + unit * 0.12f), strokeWidth = thin)
        }
    }
}
