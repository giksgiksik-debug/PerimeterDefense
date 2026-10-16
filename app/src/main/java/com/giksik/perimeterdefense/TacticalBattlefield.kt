package com.giksik.perimeterdefense

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.giksik.perimeterdefense.domain.CombatEvent
import com.giksik.perimeterdefense.domain.CombatEventType
import com.giksik.perimeterdefense.domain.CombatEngine
import com.giksik.perimeterdefense.domain.EnemyType
import com.giksik.perimeterdefense.domain.EnemyUnit
import kotlinx.coroutines.flow.collect
import kotlin.math.max

private val SceneCyan = Color(0xFF39E8FF)
private val SceneRed = Color(0xFFFF526E)
private val SceneAmber = Color(0xFFFFC857)
private val SceneGreen = Color(0xFF5CFFB0)

/**
 * Animated tactical battlefield. Enemy positions are read from the actual combat
 * state; laser and impact FX are presentation-only and never change combat values.
 */
@Composable
internal fun TacticalBattlefield(
    enemies: List<EnemyUnit>,
    baseHp: Int,
    turn: Int,
    effectEvents: List<CombatEvent>,
    effectTargets: List<EnemyUnit>,
    effectKey: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "battlefield-scanner")
    val scanFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan-sweep"
    )
    val impact = remember { Animatable(1f) }
    LaunchedEffect(effectKey) {
        val hasCombatFx = effectEvents.any {
            it.type == CombatEventType.ENEMY_DAMAGED ||
                it.type == CombatEventType.ENEMY_DESTROYED ||
                it.type == CombatEventType.BASE_DAMAGED ||
                it.type == CombatEventType.BASE_REPAIRED ||
                it.type == CombatEventType.TURRETS_STUNNED
        }
        if (hasCombatFx) {
            impact.snapTo(0f)
            impact.animateTo(1f, animationSpec = tween(durationMillis = 680, easing = LinearEasing))
        }
    }
    val impactFraction = impact.value

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF09202D), Color(0xFF07111D))))
            .border(1.dp, Color(0xFF167D93).copy(alpha = 0.8f), RoundedCornerShape(8.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val padX = 8.dp.toPx()
            val top = 9.dp.toPx()
            val baseY = h - 16.dp.toPx()
            val fieldH = max(1f, baseY - top)
            val laneWidth = (w - padX * 2f) / GridSize
            val thin = 1.dp.toPx()

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0D3542).copy(alpha = 0.64f), Color(0xFF07111D)),
                    center = Offset(w * 0.5f, h * 0.42f),
                    radius = max(w, h) * 0.72f
                )
            )

            // Lane geometry and crosshair ticks.
            for (lane in 0 until GridSize) {
                val x = padX + laneWidth * (lane + 0.5f)
                drawLine(Color(0xFF1F7184).copy(alpha = 0.25f), Offset(x, top), Offset(x, baseY), strokeWidth = thin)
                drawLine(Color(0xFF25859A).copy(alpha = 0.44f), Offset(x - 3.dp.toPx(), top), Offset(x + 3.dp.toPx(), top), strokeWidth = thin)
            }
            for (row in 0..CombatEngine.PATH_LENGTH) {
                val y = top + fieldH * row / (CombatEngine.PATH_LENGTH + 1f)
                drawLine(Color(0xFF1C5D70).copy(alpha = 0.22f), Offset(padX, y), Offset(w - padX, y), strokeWidth = thin * 0.65f)
            }

            // Scanner sweep plus leading glow.
            val scanY = top + fieldH * scanFraction
            drawLine(SceneCyan.copy(alpha = 0.08f), Offset(padX, scanY - 7.dp.toPx()), Offset(w - padX, scanY - 7.dp.toPx()), strokeWidth = 10.dp.toPx())
            drawLine(SceneCyan.copy(alpha = 0.78f), Offset(padX, scanY), Offset(w - padX, scanY), strokeWidth = 1.dp.toPx())

            // Turret platforms at the base of the six lanes.
            for (lane in 0 until GridSize step 2) {
                val x = padX + laneWidth * (lane + 0.5f)
                val turretY = h - 7.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF123747),
                    topLeft = Offset(x - laneWidth * 0.23f, turretY - 6.dp.toPx()),
                    size = Size(laneWidth * 0.46f, 8.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawLine(SceneCyan.copy(alpha = 0.7f), Offset(x, turretY - 5.dp.toPx()), Offset(x, turretY - 12.dp.toPx()), strokeWidth = 2.dp.toPx())
                drawCircle(SceneCyan.copy(alpha = 0.8f), radius = 1.5.dp.toPx(), center = Offset(x, turretY - 12.dp.toPx()))
            }

            // Laser bolts are emitted only for real combat events supplied by the engine.
            val damageEvents = effectEvents.filter { it.type == CombatEventType.ENEMY_DAMAGED }
            if (impactFraction < 0.82f && damageEvents.isNotEmpty()) {
                val alpha = (1f - impactFraction / 0.82f).coerceIn(0f, 1f)
                damageEvents.take(5).forEachIndexed { index, event ->
                    val target = effectTargets.firstOrNull { it.id == event.enemyId } ?: return@forEachIndexed
                    val targetX = padX + laneWidth * (target.lane.coerceIn(0, GridSize - 1) + 0.5f)
                    val targetY = top + fieldH * (target.progress.coerceIn(0, CombatEngine.PATH_LENGTH - 1).toFloat() / CombatEngine.PATH_LENGTH)
                    val color = when (index % 3) {
                        0 -> SceneCyan
                        1 -> Color(0xFFB56BFF)
                        else -> SceneAmber
                    }
                    val sourceX = padX + laneWidth * ((target.lane / 2) * 2 + 0.5f).coerceIn(0f, GridSize - 1f)
                    val source = Offset(sourceX, h - 20.dp.toPx())
                    val destination = Offset(targetX, targetY)
                    drawLine(color.copy(alpha = 0.16f * alpha), source, destination, strokeWidth = 7.dp.toPx())
                    drawLine(color.copy(alpha = alpha), source, destination, strokeWidth = 1.7.dp.toPx())
                    drawCircle(Color.White.copy(alpha = 0.8f * alpha), radius = 2.dp.toPx(), center = destination)
                }
            }

            // Render enemy drones as small vector robots with type-specific armor.
            enemies.forEach { enemy ->
                drawEnemyRobot(
                    lane = enemy.lane,
                    progress = enemy.progress,
                    health = enemy.health,
                    maxHealth = enemy.maxHealth,
                    type = enemy.type,
                    slowed = enemy.slowTurns > 0,
                    padX = padX,
                    laneWidth = laneWidth,
                    top = top,
                    fieldH = fieldH
                )
            }

            // Impact rings mark targets which took damage; destroyed units get a larger burst.
            if (impactFraction < 1f) {
                val alpha = (1f - impactFraction).coerceIn(0f, 1f)
                effectEvents.filter { it.type == CombatEventType.ENEMY_DESTROYED }.forEach { event ->
                    val target = effectTargets.firstOrNull { it.id == event.enemyId }
                    if (target != null) {
                        val center = Offset(
                            padX + laneWidth * (target.lane.coerceIn(0, GridSize - 1) + 0.5f),
                            top + fieldH * (target.progress.coerceIn(0, CombatEngine.PATH_LENGTH - 1).toFloat() / CombatEngine.PATH_LENGTH)
                        )
                        val radius = (5.dp.toPx() + 23.dp.toPx() * impactFraction)
                        drawCircle(SceneAmber.copy(alpha = alpha * 0.8f), radius = radius, center = center, style = Stroke(width = 1.5.dp.toPx()))
                        drawCircle(SceneRed.copy(alpha = alpha * 0.65f), radius = radius * 0.55f, center = center, style = Stroke(width = 1.dp.toPx()))
                        for (ray in 0 until 8) {
                            val angle = Math.PI * ray / 4.0
                            val inner = radius * 0.65f
                            val outer = radius * 1.05f
                            drawLine(
                                SceneAmber.copy(alpha = alpha),
                                Offset(center.x + (kotlin.math.cos(angle) * inner).toFloat(), center.y + (kotlin.math.sin(angle) * inner).toFloat()),
                                Offset(center.x + (kotlin.math.cos(angle) * outer).toFloat(), center.y + (kotlin.math.sin(angle) * outer).toFloat()),
                                strokeWidth = 1.3.dp.toPx()
                            )
                        }
                    }
                }

                if (effectEvents.any { it.type == CombatEventType.BASE_DAMAGED || it.type == CombatEventType.TURRETS_STUNNED }) {
                    val center = Offset(w * 0.5f, h - 9.dp.toPx())
                    val radius = 4.dp.toPx() + 17.dp.toPx() * impactFraction
                    drawCircle(SceneRed.copy(alpha = alpha * 0.7f), radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
                    drawCircle(SceneRed.copy(alpha = alpha * 0.3f), radius = radius * 1.5f, center = center, style = Stroke(width = 1.dp.toPx()))
                }
                if (effectEvents.any { it.type == CombatEventType.BASE_REPAIRED }) {
                    val center = Offset(w * 0.5f, h - 9.dp.toPx())
                    drawCircle(SceneGreen.copy(alpha = alpha * 0.65f), radius = 4.dp.toPx() + 17.dp.toPx() * impactFraction, center = center, style = Stroke(width = 2.dp.toPx()))
                }
            }

            // Base shield baseline.
            val shieldWidth = (w - padX * 2f) * (baseHp.coerceIn(0, 100) / 100f)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(SceneGreen, SceneCyan)),
                topLeft = Offset(padX, h - 2.dp.toPx()),
                size = Size(shieldWidth, 1.5.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
            drawLine(Color(0xFF315568), Offset(padX, h - 2.dp.toPx()), Offset(w - padX, h - 2.dp.toPx()), strokeWidth = 1.dp.toPx())
            // Small wave/turn readout markers.
            val markerX = padX + (turn % 12) * ((w - padX * 2f) / 12f)
            drawCircle(SceneCyan.copy(alpha = 0.75f), radius = 1.7.dp.toPx(), center = Offset(markerX, 4.dp.toPx()))
        }
    }
}

private const val GridSize = 6

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEnemyRobot(
    lane: Int,
    progress: Int,
    health: Int,
    maxHealth: Int,
    type: EnemyType,
    slowed: Boolean,
    padX: Float,
    laneWidth: Float,
    top: Float,
    fieldH: Float
) {
    val centerX = padX + laneWidth * (lane.coerceIn(0, GridSize - 1) + 0.5f)
    val centerY = top + fieldH * (progress.coerceIn(0, CombatEngine.PATH_LENGTH - 1).toFloat() / CombatEngine.PATH_LENGTH)
    val radius = when (type) {
        EnemyType.BOSS -> 8.dp.toPx()
        EnemyType.ARMORED -> 7.dp.toPx()
        else -> 5.6.dp.toPx()
    }
    val hull = when (type) {
        EnemyType.SCOUT -> Color(0xFFFF5F75)
        EnemyType.RAIDER -> Color(0xFFFF9D55)
        EnemyType.ARMORED -> Color(0xFFB56BFF)
        EnemyType.BOSS -> SceneAmber
    }
    drawCircle(hull.copy(alpha = 0.12f), radius = radius * 1.9f, center = Offset(centerX, centerY))
    if (type == EnemyType.BOSS || slowed) {
        drawCircle(
            if (slowed) SceneCyan.copy(alpha = 0.9f) else hull.copy(alpha = 0.8f),
            radius = radius * 1.55f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.dp.toPx())
        )
    }

    val bodySize = Size(radius * 1.45f, radius * 1.2f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(hull.copy(alpha = 0.98f), hull.copy(alpha = 0.38f))),
        topLeft = Offset(centerX - bodySize.width / 2f, centerY - bodySize.height * 0.05f),
        size = bodySize,
        cornerRadius = CornerRadius(2.dp.toPx())
    )
    val headSize = if (type == EnemyType.BOSS) radius * 0.95f else radius * 0.72f
    drawRoundRect(
        color = Color(0xFF17232F),
        topLeft = Offset(centerX - headSize / 2f, centerY - radius * 0.85f),
        size = Size(headSize, headSize * 0.72f),
        cornerRadius = CornerRadius(1.dp.toPx())
    )
    drawLine(SceneCyan, Offset(centerX - headSize * 0.26f, centerY - radius * 0.55f), Offset(centerX + headSize * 0.26f, centerY - radius * 0.55f), strokeWidth = 1.4.dp.toPx())
    drawLine(hull, Offset(centerX - radius * 0.4f, centerY + radius * 0.5f), Offset(centerX - radius * 0.58f, centerY + radius * 0.9f), strokeWidth = 1.1.dp.toPx())
    drawLine(hull, Offset(centerX + radius * 0.4f, centerY + radius * 0.5f), Offset(centerX + radius * 0.58f, centerY + radius * 0.9f), strokeWidth = 1.1.dp.toPx())
    drawLine(hull, Offset(centerX - radius * 0.68f, centerY + radius * 0.02f), Offset(centerX - radius * 1.05f, centerY + radius * 0.25f), strokeWidth = 1.2.dp.toPx())
    drawLine(hull, Offset(centerX + radius * 0.68f, centerY + radius * 0.02f), Offset(centerX + radius * 1.05f, centerY + radius * 0.25f), strokeWidth = 1.2.dp.toPx())

    // Compact health strip floats over each unit.
    val healthBarWidth = radius * 2.35f
    val barY = centerY - radius * 1.22f
    drawRoundRect(
        color = Color(0xFF253544),
        topLeft = Offset(centerX - healthBarWidth / 2f, barY),
        size = Size(healthBarWidth, 1.8.dp.toPx()),
        cornerRadius = CornerRadius(1.dp.toPx())
    )
    val ratio = (health.toFloat() / max(1, maxHealth).toFloat()).coerceIn(0f, 1f)
    if (ratio > 0f) {
        drawRoundRect(
            color = if (ratio > 0.55f) SceneGreen else if (ratio > 0.25f) SceneAmber else SceneRed,
            topLeft = Offset(centerX - healthBarWidth / 2f, barY),
            size = Size(healthBarWidth * ratio, 1.8.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
    }
}
