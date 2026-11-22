package com.giksgik.perimeterdefense

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.giksgik.perimeterdefense.domain.AmmoColor
import com.giksgik.perimeterdefense.domain.CombatEngine
import com.giksgik.perimeterdefense.domain.CombatEvent
import com.giksgik.perimeterdefense.domain.CombatEventType
import com.giksgik.perimeterdefense.domain.EnemyType
import com.giksgik.perimeterdefense.domain.EnemyUnit
import com.giksgik.perimeterdefense.domain.GridEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private val SceneCyan = Color(0xFF39E8FF)
private val SceneRed = Color(0xFFFF526E)
private val SceneAmber = Color(0xFFFFC857)
private val SceneGreen = Color(0xFF5CFFB0)

private val BattlefieldShape = RoundedCornerShape(8.dp)
private val BattlefieldBgBrush = Brush.verticalGradient(listOf(Color(0xFF09202D), Color(0xFF07111D)))
private val BattlefieldBorderColor = Color(0xFF167D93).copy(alpha = 0.8f)
private val ShieldGradientColors = listOf(SceneGreen, SceneCyan)

private data class TurretSpec(val fractionX: Float, val color: Color, val ammo: AmmoColor)

private val TurretSpecs = listOf(
    TurretSpec(0.18f, SceneRed, AmmoColor.RED),
    TurretSpec(0.50f, SceneCyan, AmmoColor.BLUE),
    TurretSpec(0.82f, SceneAmber, AmmoColor.YELLOW)
)

private const val VFX_DURATION_MS = 680f
private const val VFX_DURATION_NANOS = 680_000_000L

/**
 * Animated tactical battlefield. Enemy positions are read from the actual combat
 * state; laser and impact FX are presentation-only and never change combat values.
 * Frame-driven state is read strictly inside Canvas so animations never trigger
 * per-frame recomposition of the composable tree.
 */
@Composable
internal fun TacticalBattlefield(
    enemies: List<EnemyUnit>,
    baseHp: Int,
    maxBaseHp: Int = 100,
    turretStunTurns: Int,
    turn: Int,
    effectEvents: List<CombatEvent>,
    effectTargets: List<EnemyUnit>,
    effectKey: Int,
    modifier: Modifier = Modifier,
    pathLength: Int = CombatEngine.PATH_LENGTH,
    gridSize: Int = GridEngine.GRID_SIZE
) {
    val infiniteTransition = rememberInfiniteTransition(label = "battlefield-scanner")
    val scanFractionState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan-sweep"
    )

    val vfxStartNanosState = remember { mutableLongStateOf(0L) }
    val vfxFrameNanosState = remember { mutableLongStateOf(0L) }
    val teslaPath = remember { Path() }

    val damageEvents = remember(effectEvents) {
        effectEvents.filter { it.type == CombatEventType.ENEMY_DAMAGED }.take(8)
    }
    val hitEvents = remember(effectEvents) {
        val destroyedIds = effectEvents
            .filter { it.type == CombatEventType.ENEMY_DESTROYED }
            .mapNotNull { it.enemyId }
            .toSet()
        effectEvents.filter {
            it.type == CombatEventType.ENEMY_DESTROYED ||
                (it.type == CombatEventType.ENEMY_DAMAGED && it.enemyId !in destroyedIds)
        }.take(8)
    }
    val hasBaseHitFx = remember(effectEvents) {
        effectEvents.any { it.type == CombatEventType.BASE_DAMAGED || it.type == CombatEventType.TURRETS_STUNNED }
    }
    val hasBaseRepairFx = remember(effectEvents) {
        effectEvents.any { it.type == CombatEventType.BASE_REPAIRED }
    }
    val targetsById = remember(effectTargets) {
        effectTargets.associateBy { it.id }
    }

    LaunchedEffect(effectKey) {
        val hasCombatFx = effectKey > 0 && effectEvents.any {
            it.type == CombatEventType.ENEMY_DAMAGED ||
                it.type == CombatEventType.ENEMY_DESTROYED ||
                it.type == CombatEventType.BASE_DAMAGED ||
                it.type == CombatEventType.BASE_REPAIRED ||
                it.type == CombatEventType.TURRETS_STUNNED
        }
        if (!hasCombatFx) {
            vfxStartNanosState.longValue = 0L
            vfxFrameNanosState.longValue = 0L
            return@LaunchedEffect
        }

        val start = withFrameNanos { it }
        vfxStartNanosState.longValue = start
        vfxFrameNanosState.longValue = start
        var now = start
        while (now - start < VFX_DURATION_NANOS) {
            vfxFrameNanosState.longValue = now
            now = withFrameNanos { it }
        }
        vfxFrameNanosState.longValue = start + VFX_DURATION_NANOS
    }

    Box(
        modifier = modifier
            .clip(BattlefieldShape)
            .background(BattlefieldBgBrush)
            .border(1.dp, BattlefieldBorderColor, BattlefieldShape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Read animated frame state inside DrawScope only to avoid recomposition.
            val scanFraction = scanFractionState.value
            val startNanos = vfxStartNanosState.longValue
            val frameNanos = vfxFrameNanosState.longValue
            val elapsedMs = if (startNanos > 0L && frameNanos >= startNanos) {
                ((frameNanos - startNanos) / 1_000_000f).coerceAtLeast(0f)
            } else {
                VFX_DURATION_MS
            }

            val w = size.width
            val h = size.height
            val padX = 8.dp.toPx()
            val top = 9.dp.toPx()
            val baseY = h - 16.dp.toPx()
            val fieldH = max(1f, baseY - top)
            val safeGridSize = max(1, gridSize)
            val safePathLength = max(1, pathLength)
            val laneWidth = (w - padX * 2f) / safeGridSize
            val thin = 1.dp.toPx()

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0D3542).copy(alpha = 0.64f), Color(0xFF07111D)),
                    center = Offset(w * 0.5f, h * 0.42f),
                    radius = max(w, h) * 0.72f
                )
            )

            // Lane geometry and crosshair ticks.
            for (lane in 0 until safeGridSize) {
                val x = padX + laneWidth * (lane + 0.5f)
                drawLine(Color(0xFF1F7184).copy(alpha = 0.25f), Offset(x, top), Offset(x, baseY), strokeWidth = thin)
                drawLine(Color(0xFF25859A).copy(alpha = 0.44f), Offset(x - 3.dp.toPx(), top), Offset(x + 3.dp.toPx(), top), strokeWidth = thin)
            }
            for (row in 0..safePathLength) {
                val y = top + fieldH * row / (safePathLength + 1f)
                drawLine(Color(0xFF1C5D70).copy(alpha = 0.22f), Offset(padX, y), Offset(w - padX, y), strokeWidth = thin * 0.65f)
            }

            // Scanner sweep plus leading glow.
            val scanY = top + fieldH * scanFraction
            drawLine(SceneCyan.copy(alpha = 0.08f), Offset(padX, scanY - 7.dp.toPx()), Offset(w - padX, scanY - 7.dp.toPx()), strokeWidth = 10.dp.toPx())
            drawLine(SceneCyan.copy(alpha = 0.78f), Offset(padX, scanY), Offset(w - padX, scanY), strokeWidth = 1.dp.toPx())

            // Three weapon-specific turrets: mortar left, cryo center, Tesla right.
            val turretY = h - 7.dp.toPx()
            val turretCorner = CornerRadius(2.dp.toPx())
            TurretSpecs.forEach { spec ->
                val x = padX + (w - padX * 2f) * spec.fractionX
                drawRoundRect(
                    color = Color(0xFF123747),
                    topLeft = Offset(x - laneWidth * 0.23f, turretY - 6.dp.toPx()),
                    size = Size(laneWidth * 0.46f, 8.dp.toPx()),
                    cornerRadius = turretCorner
                )
                drawLine(
                    spec.color.copy(alpha = if (turretStunTurns > 0) 0.25f else 0.88f),
                    Offset(x, turretY - 5.dp.toPx()),
                    Offset(x, turretY - 15.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx()
                )
                drawCircle(
                    spec.color.copy(alpha = if (turretStunTurns > 0) 0.3f else 0.95f),
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
            if (elapsedMs < 390f && damageEvents.isNotEmpty()) {
                val outerStroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                val midStroke = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                val coreStroke = Stroke(width = 0.85.dp.toPx(), cap = StrokeCap.Round)
                val beamProgress = (elapsedMs / 245f).coerceIn(0f, 1f)
                val alpha = (1f - elapsedMs / 390f).coerceIn(0f, 1f)

                damageEvents.forEachIndexed { index, event ->
                    val target = event.enemyId?.let { targetsById[it] } ?: return@forEachIndexed
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
                        padX + laneWidth * (target.lane.coerceIn(0, safeGridSize - 1) + 0.5f),
                        top + fieldH * (target.progress.coerceIn(0, safePathLength - 1).toFloat() / safePathLength)
                    )
                    val endpoint = Offset(
                        source.x + (destination.x - source.x) * beamProgress,
                        source.y + (destination.y - source.y) * beamProgress
                    )

                    if (weapon == AmmoColor.YELLOW) {
                        // Tesla discharge zig-zags rather than behaving like a straight laser.
                        teslaPath.reset()
                        teslaPath.moveTo(source.x, source.y)
                        val dx = endpoint.x - source.x
                        val dy = endpoint.y - source.y
                        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                        val perpX = -dy / length
                        val perpY = dx / length
                        val zigzagAmp = 5.dp.toPx()
                        for (step in 1..5) {
                            val t = step / 6f
                            val offset = if (step % 2 == 0) -1f else 1f
                            teslaPath.lineTo(
                                source.x + dx * t + perpX * zigzagAmp * offset,
                                source.y + dy * t + perpY * zigzagAmp * offset
                            )
                        }
                        teslaPath.lineTo(endpoint.x, endpoint.y)
                        drawPath(teslaPath, weaponColor.copy(alpha = 0.22f * alpha), style = outerStroke)
                        drawPath(teslaPath, weaponColor.copy(alpha = 0.92f * alpha), style = midStroke)
                        drawPath(teslaPath, Color.White.copy(alpha = 0.95f * alpha), style = coreStroke)
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
                    fieldH = fieldH,
                    gridSize = safeGridSize,
                    pathLength = safePathLength
                )
            }

            // Expanding impact rings and eight sparks at each hit point.
            if (elapsedMs < 560f) {
                val lifetime = (elapsedMs / 560f).coerceIn(0f, 1f)
                val alpha = (1f - lifetime).coerceIn(0f, 1f)
                val ringStroke = Stroke(width = 2.dp.toPx())
                val thinRingStroke = Stroke(width = 1.dp.toPx())

                hitEvents.forEachIndexed { eventIndex, event ->
                    val target = event.enemyId?.let { targetsById[it] }
                    if (target != null) {
                        val center = Offset(
                            padX + laneWidth * (target.lane.coerceIn(0, safeGridSize - 1) + 0.5f),
                            top + fieldH * (target.progress.coerceIn(0, safePathLength - 1).toFloat() / safePathLength)
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
                        drawCircle(color.copy(alpha = alpha * 0.78f), radius = radius, center = center, style = ringStroke)
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

                if (hasBaseHitFx) {
                    val center = Offset(w * 0.5f, h - 13.dp.toPx())
                    val radius = 4.dp.toPx() + 19.dp.toPx() * lifetime
                    drawCircle(SceneRed.copy(alpha = alpha * 0.8f), radius = radius, center = center, style = ringStroke)
                    drawCircle(SceneRed.copy(alpha = alpha * 0.3f), radius = radius * 1.5f, center = center, style = thinRingStroke)
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

                if (hasBaseRepairFx) {
                    val center = Offset(w * 0.5f, h - 13.dp.toPx())
                    drawCircle(SceneGreen.copy(alpha = alpha * 0.7f), radius = 4.dp.toPx() + 17.dp.toPx() * lifetime, center = center, style = ringStroke)
                }
            }

            // Base shield baseline.
            val safeMaxHp = max(1, maxBaseHp)
            val shieldWidth = (w - padX * 2f) * (baseHp.coerceIn(0, safeMaxHp).toFloat() / safeMaxHp)
            drawRoundRect(
                brush = Brush.horizontalGradient(ShieldGradientColors),
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

private fun DrawScope.drawEnemyRobot(
    lane: Int,
    progress: Int,
    health: Int,
    maxHealth: Int,
    type: EnemyType,
    slowed: Boolean,
    padX: Float,
    laneWidth: Float,
    top: Float,
    fieldH: Float,
    gridSize: Int,
    pathLength: Int
) {
    val centerX = padX + laneWidth * (lane.coerceIn(0, gridSize - 1) + 0.5f)
    val centerY = top + fieldH * (progress.coerceIn(0, pathLength - 1).toFloat() / pathLength)
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

    // Armored units and bosses receive visible segmented chest plates and rivets.
    if (type == EnemyType.ARMORED || type == EnemyType.BOSS) {
        val plateWidth = bodySize.width * 0.28f
        val plateHeight = bodySize.height * 0.38f
        for (plate in 0..2) {
            val px = centerX - bodySize.width * 0.42f + plate * (plateWidth + 1.dp.toPx())
            drawRoundRect(
                color = Color(0xFFD9E5F0).copy(alpha = 0.62f),
                topLeft = Offset(px, centerY + bodySize.height * 0.08f),
                size = Size(plateWidth, plateHeight),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
            drawCircle(
                color = SceneCyan.copy(alpha = 0.8f),
                radius = 0.65.dp.toPx(),
                center = Offset(px + plateWidth * 0.5f, centerY + bodySize.height * 0.22f)
            )
        }
        drawLine(
            if (type == EnemyType.BOSS) SceneAmber else SceneCyan,
            Offset(centerX - bodySize.width * 0.45f, centerY + bodySize.height * 0.58f),
            Offset(centerX + bodySize.width * 0.45f, centerY + bodySize.height * 0.58f),
            strokeWidth = 1.dp.toPx()
        )
    }
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
