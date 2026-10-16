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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.giksik.perimeterdefense.domain.CombatEvent
import com.giksik.perimeterdefense.domain.CombatEventType
import com.giksik.perimeterdefense.domain.CombatEngine
import com.giksik.perimeterdefense.domain.AmmoColor
import com.giksik.perimeterdefense.domain.EnemyType
import com.giksik.perimeterdefense.domain.EnemyUnit
import kotlinx.coroutines.flow.collect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos

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
    turretStunTurns: Int,
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
    var vfxStartNanos by remember { mutableLongStateOf(0L) }
    var vfxFrameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(effectKey) {
        val hasCombatFx = effectEvents.any {
            it.type == CombatEventType.ENEMY_DAMAGED ||
                it.type == CombatEventType.ENEMY_DESTROYED ||
                it.type == CombatEventType.BASE_DAMAGED ||
                it.type == CombatEventType.BASE_REPAIRED ||
                it.type == CombatEventType.TURRETS_STUNNED
        }
        if (!hasCombatFx) return@LaunchedEffect

        val start = withFrameNanos { it }
        vfxStartNanos = start
        vfxFrameNanos = start
        var now = start
        val durationNanos = 680_000_000L
        while (now - start < durationNanos) {
            vfxFrameNanos = now
            now = withFrameNanos { it }
        }
        vfxFrameNanos = start + durationNanos
    }
    val elapsedMs = if (vfxStartNanos > 0L && vfxFrameNanos >= vfxStartNanos) {
        ((vfxFrameNanos - vfxStartNanos) / 1_000_000f).coerceAtLeast(0f)
    } else {
        680f
    }
    val impactFraction = (elapsedMs / 680f).coerceIn(0f, 1f)

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

            // Three weapon-specific turrets: mortar left, cryo center, Tesla right.
            val turretSpecs = listOf(
                Triple(0.18f, SceneRed, AmmoColor.RED),
                Triple(0.50f, SceneCyan, AmmoColor.BLUE),
                Triple(0.82f, SceneAmber, AmmoColor.YELLOW)
            )
            turretSpecs.forEach { (fractionX, turretColor, _) ->
                val x = padX + (w - padX * 2f) * fractionX
                val turretY = h - 7.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF123747),
                    topLeft = Offset(x - laneWidth * 0.23f, turretY - 6.dp.toPx()),
                    size = Size(laneWidth * 0.46f, 8.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawLine(
                    turretColor.copy(alpha = if (turretStunTurns > 0) 0.25f else 0.88f),
                    Offset(x, turretY - 5.dp.toPx()),
                    Offset(x, turretY - 15.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawCircle(
                    turretColor.copy(alpha = if (turretStunTurns > 0) 0.3f else 0.95f),
                    radius = 3.dp.toPx(),
                    center = Offset(x, turretY - 15.dp.toPx())
                )
                drawCircle(
                    Color.White.copy(alpha = if (turretStunTurns > 0) 0.1f else 0.7f),
                    radius = 1.dp.toPx(),
                    center = Offset(x, turretY - 15.dp.toPx())
                )
            }

            // Real combat damage events create a travelling, double-layer laser.
            val damageEvents = effectEvents.filter { it.type == CombatEventType.ENEMY_DAMAGED }
            if (elapsedMs < 390f) {
                damageEvents.take(8).forEachIndexed { index, event ->
                    val target = effectTargets.firstOrNull { it.id == event.enemyId } ?: return@forEachIndexed
                    val weapon = event.weaponColor ?: when (index % 3) {
                        0 -> AmmoColor.RED
                        1 -> AmmoColor.BLUE
                        else -> AmmoColor.YELLOW
                    }
                    val weaponColor = when (weapon) {
                        AmmoColor.RED -> SceneRed
                        AmmoColor.BLUE -> SceneCyan
                        AmmoColor.YELLOW -> SceneAmber
                        AmmoColor.GREEN_HEAL -> SceneGreen
                        AmmoColor.NONE -> SceneCyan
                    }
                    val sourceFraction = when (weapon) {
                        AmmoColor.RED -> 0.18f
                        AmmoColor.BLUE -> 0.50f
                        AmmoColor.YELLOW -> 0.82f
                        AmmoColor.GREEN_HEAL, AmmoColor.NONE -> 0.50f
                    }
                    val source = Offset(
                        padX + (w - padX * 2f) * sourceFraction,
                        h - 18.dp.toPx()
                    )
                    val destination = Offset(
                        padX + laneWidth * (target.lane.coerceIn(0, GridSize - 1) + 0.5f),
                        top + fieldH * (target.progress.coerceIn(0, CombatEngine.PATH_LENGTH - 1).toFloat() / CombatEngine.PATH_LENGTH)
                    )
                    val beamProgress = (elapsedMs / 245f).coerceIn(0f, 1f)
                    val endpoint = Offset(
                        source.x + (destination.x - source.x) * beamProgress,
                        source.y + (destination.y - source.y) * beamProgress
                    )
                    val alpha = (1f - elapsedMs / 390f).coerceIn(0f, 1f)

                    if (weapon == AmmoColor.YELLOW) {
                        // Tesla discharge zig-zags rather than behaving like a straight laser.
                        val path = Path().apply {
                            moveTo(source.x, source.y)
                            val dx = endpoint.x - source.x
                            val dy = endpoint.y - source.y
                            val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                            val perpX = -dy / length
                            val perpY = dx / length
                            for (step in 1..5) {
                                val t = step / 6f
                                val offset = if (step % 2 == 0) -1f else 1f
                                lineTo(
                                    source.x + dx * t + perpX * 5.dp.toPx() * offset,
                                    source.y + dy * t + perpY * 5.dp.toPx() * offset
                                )
                            }
                            lineTo(endpoint.x, endpoint.y)
                        }
                        drawPath(path, weaponColor.copy(alpha = 0.22f * alpha), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(path, weaponColor.copy(alpha = 0.92f * alpha), style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(path, Color.White.copy(alpha = 0.95f * alpha), style = Stroke(width = 0.85.dp.toPx(), cap = StrokeCap.Round))
                    } else {
                        drawLine(weaponColor.copy(alpha = 0.20f * alpha), source, endpoint, strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(weaponColor.copy(alpha = 0.95f * alpha), source, endpoint, strokeWidth = 2.8.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = 0.95f * alpha), source, endpoint, strokeWidth = 0.85.dp.toPx(), cap = StrokeCap.Round)
                    }
                    drawCircle(Color.White.copy(alpha = alpha), radius = 2.2.dp.toPx(), center = endpoint)
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

            // Expanding impact rings and exactly eight sparks at each hit point.
            if (elapsedMs < 560f) {
                val lifetime = (elapsedMs / 560f).coerceIn(0f, 1f)
                val alpha = (1f - lifetime).coerceIn(0f, 1f)
                val destroyedIds = effectEvents
                    .filter { it.type == CombatEventType.ENEMY_DESTROYED }
                    .mapNotNull { it.enemyId }
                    .toSet()
                val hitEvents = effectEvents.filter {
                    it.type == CombatEventType.ENEMY_DESTROYED ||
                        (it.type == CombatEventType.ENEMY_DAMAGED && it.enemyId !in destroyedIds)
                }
                hitEvents.take(8).forEachIndexed { eventIndex, event ->
                    val target = effectTargets.firstOrNull { it.id == event.enemyId }
                    if (target != null) {
                        val center = Offset(
                            padX + laneWidth * (target.lane.coerceIn(0, GridSize - 1) + 0.5f),
                            top + fieldH * (target.progress.coerceIn(0, CombatEngine.PATH_LENGTH - 1).toFloat() / CombatEngine.PATH_LENGTH)
                        )
                        val color = when (event.weaponColor) {
                            AmmoColor.RED -> SceneRed
                            AmmoColor.BLUE -> SceneCyan
                            AmmoColor.YELLOW -> SceneAmber
                            AmmoColor.GREEN_HEAL -> SceneGreen
                            else -> if (event.type == CombatEventType.ENEMY_DESTROYED) SceneAmber else SceneRed
                        }
                        val maxRadius = if (event.type == CombatEventType.ENEMY_DESTROYED) 27.dp.toPx() else 17.dp.toPx()
                        val radius = maxRadius * (0.15f + lifetime * 0.85f)
                        drawCircle(color.copy(alpha = alpha * 0.78f), radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
                        drawCircle(color.copy(alpha = alpha * 0.22f), radius = radius * 0.62f, center = center)
                        drawCircle(Color.White.copy(alpha = alpha * 0.88f), radius = maxRadius * 0.17f * (1f - lifetime), center = center)
                        for (spark in 0 until 8) {
                            val angle = (PI * 2.0 * spark / 8.0) + eventIndex * 0.31
                            val inner = radius * 0.42f
                            val outer = radius * (0.88f + 0.2f * sin(lifetime * 5f + spark))
                            val start = Offset(
                                center.x + (cos(angle) * inner).toFloat(),
                                center.y + (sin(angle) * inner).toFloat()
                            )
                            val finish = Offset(
                                center.x + (cos(angle) * outer).toFloat(),
                                center.y + (sin(angle) * outer).toFloat()
                            )
                            drawLine(color.copy(alpha = alpha), start, finish, strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                }

                if (effectEvents.any { it.type == CombatEventType.BASE_DAMAGED || it.type == CombatEventType.TURRETS_STUNNED }) {
                    val center = Offset(w * 0.5f, h - 13.dp.toPx())
                    val radius = 4.dp.toPx() + 19.dp.toPx() * lifetime
                    drawCircle(SceneRed.copy(alpha = alpha * 0.8f), radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
                    drawCircle(SceneRed.copy(alpha = alpha * 0.3f), radius = radius * 1.5f, center = center, style = Stroke(width = 1.dp.toPx()))
                    for (spark in 0 until 8) {
                        val angle = PI * 2.0 * spark / 8.0
                        drawLine(
                            SceneAmber.copy(alpha = alpha),
                            Offset(center.x + (cos(angle) * radius * 0.4f).toFloat(), center.y + (sin(angle) * radius * 0.4f).toFloat()),
                            Offset(center.x + (cos(angle) * radius * 1.1f).toFloat(), center.y + (sin(angle) * radius * 1.1f).toFloat()),
                            strokeWidth = 1.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                if (effectEvents.any { it.type == CombatEventType.BASE_REPAIRED }) {
                    val center = Offset(w * 0.5f, h - 13.dp.toPx())
                    drawCircle(SceneGreen.copy(alpha = alpha * 0.7f), radius = 4.dp.toPx() + 17.dp.toPx() * lifetime, center = center, style = Stroke(width = 2.dp.toPx()))
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
