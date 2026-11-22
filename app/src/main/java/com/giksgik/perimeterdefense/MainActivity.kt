package com.giksgik.perimeterdefense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giksgik.perimeterdefense.domain.AmmoColor
import com.giksgik.perimeterdefense.domain.BoosterCosts
import com.giksgik.perimeterdefense.domain.CellState
import com.giksgik.perimeterdefense.domain.CombatEvent
import com.giksgik.perimeterdefense.domain.CombatEventType
import com.giksgik.perimeterdefense.domain.CombatState
import com.giksgik.perimeterdefense.domain.EnemyUnit
import com.giksgik.perimeterdefense.domain.GridEngine
import com.giksgik.perimeterdefense.domain.HangarUpgradeType
import com.giksgik.perimeterdefense.domain.HangarUpgrades
import com.giksgik.perimeterdefense.domain.InteractionMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val Background = Color(0xFF070B13)
private val SurfaceDark = Color(0xE6101B2A)
private val DialogBg = Color(0xFF0C1624)
private val BorderDark = Color(0xFF244454)
private val Neon = Color(0xFF00E5FF)
private val AmberAccent = Color(0xFFFFC857)
private val GreenAccent = Color(0xFF3FB950)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NeonBackdrop()
                        PerimeterGame()
                    }
                }
            }
        }
    }
}

@Composable
private fun PerimeterGame(gameViewModel: PerimeterGameViewModel = viewModel()) {
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current.density
    var shakeOffsetX by remember { mutableFloatStateOf(0f) }
    var shakeOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uiState.visualEffectKey) {
        val hasImpact = uiState.visualEffectKey > 0 && uiState.visualEvents.any {
            it.type == CombatEventType.ENEMY_DAMAGED ||
                it.type == CombatEventType.ENEMY_DESTROYED ||
                it.type == CombatEventType.BASE_DAMAGED ||
                it.type == CombatEventType.BASE_REPAIRED ||
                it.type == CombatEventType.TURRETS_STUNNED
        }
        if (hasImpact) {
            val start = withFrameNanos { it }
            var now = start
            val durationNanos = 300_000_000L
            while (now - start < durationNanos) {
                val progress = ((now - start).toFloat() / durationNanos).coerceIn(0f, 1f)
                val amplitude = 8.5f * density * (1f - progress) * (1f - progress)
                val elapsed = (now - start).toDouble()
                shakeOffsetX = sin(elapsed / 38_000_000.0 * 2.0 * Math.PI).toFloat() * amplitude
                shakeOffsetY = cos(elapsed / 31_000_000.0 * 2.0 * Math.PI).toFloat() * amplitude * 0.58f
                now = withFrameNanos { it }
            }
        }
        shakeOffsetX = 0f
        shakeOffsetY = 0f
    }

    if (!uiState.isLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Neon)
        }
        return
    }

    val grid = uiState.grid
    val combat = uiState.combat
    val mode = uiState.mode
    val canUpgrade = combat.canAffordAnyUpgrade()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 820.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("PERIMETER // DEFENSE", color = Neon, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    Text(
                        "WAVE %02d (BEST %02d)  •  CR %04d".format(combat.wave, uiState.bestWave, combat.chips),
                        color = Color(0xFF9CB8C8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (canUpgrade) AmberAccent.copy(alpha = 0.22f) else SurfaceDark,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (canUpgrade) AmberAccent else BorderDark,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { gameViewModel.openHangar() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (canUpgrade) "★ АНГАР" else "АНГАР",
                            color = if (canUpgrade) AmberAccent else Color(0xFFB8D4E4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        if (uiState.soundEnabled) "♫" else "♪",
                        color = if (uiState.soundEnabled) Neon else Color(0xFF8293A2),
                        fontSize = 22.sp,
                        modifier = Modifier.clickable { gameViewModel.toggleSound() }
                    )
                    Text(
                        "⏸",
                        color = Color.White,
                        fontSize = 22.sp,
                        modifier = Modifier.clickable { gameViewModel.openPause() }
                    )
                }
            }

            RadarPanel(
                baseHp = combat.baseHp,
                maxBaseHp = combat.maxBaseHp,
                stun = combat.turretStunTurns,
                turns = combat.turn,
                enemies = combat.enemies,
                effectEvents = uiState.visualEvents,
                effectTargets = uiState.visualTargets,
                effectKey = uiState.visualEffectKey,
                compact = compact,
                shakeOffsetX = shakeOffsetX,
                shakeOffsetY = shakeOffsetY
            )

            Text(
                "ENERGY MATRIX // ${GridEngine.GRID_SIZE} × ${GridEngine.GRID_SIZE}  •  СВАЙП 3+  •  УДЕРЖАНИЕ = ФЛАГ",
                color = Neon,
                fontSize = if (compact) 9.sp else 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )

            // Board takes only the space left between radar and the fixed bottom controls.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        translationX = shakeOffsetX
                        translationY = shakeOffsetY
                    },
                contentAlignment = Alignment.Center
            ) {
                val matrixSize = minOf(maxWidth - 4.dp, maxHeight - 2.dp).coerceAtLeast(160.dp)
                Box(
                    modifier = Modifier
                        .size(matrixSize)
                        .pointerInput(Unit) {
                            val boardWidthPx = size.width.toFloat()
                            val boardHeightPx = size.height.toFloat()
                            val insetPx = 6.dp.toPx()
                            val gapPx = 5.dp.toPx()
                            val gridCount = GridEngine.GRID_SIZE
                            val tileWidthPx = ((boardWidthPx - insetPx * 2f - gapPx * (gridCount - 1)) / gridCount).coerceAtLeast(1f)
                            val tileHeightPx = ((boardHeightPx - insetPx * 2f - gapPx * (gridCount - 1)) / gridCount).coerceAtLeast(1f)
                            val longPressMs = 420L

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downTimeMs = down.uptimeMillis
                                var endTimeMs = downTimeMs
                                var endPosition = down.position
                                var stillPressed = true
                                var cancelled = false
                                while (stillPressed) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        cancelled = true
                                        break
                                    }
                                    endPosition = change.position
                                    endTimeMs = change.uptimeMillis
                                    stillPressed = change.pressed
                                    change.consume()
                                }

                                if (!cancelled) {
                                    fun columnAt(x: Float): Int =
                                        floor((x - insetPx) / (tileWidthPx + gapPx)).toInt().coerceIn(0, gridCount - 1)
                                    fun rowAt(y: Float): Int =
                                        floor((y - insetPx) / (tileHeightPx + gapPx)).toInt().coerceIn(0, gridCount - 1)

                                    val startRow = rowAt(down.position.y)
                                    val startCol = columnAt(down.position.x)
                                    val dx = endPosition.x - down.position.x
                                    val dy = endPosition.y - down.position.y
                                    val threshold = 14.dp.toPx()

                                    if (maxOf(abs(dx), abs(dy)) >= threshold) {
                                        if (abs(dx) >= abs(dy)) {
                                            gameViewModel.onSwipe(startRow, startCol, startRow, startCol + if (dx > 0) 1 else -1)
                                        } else {
                                            gameViewModel.onSwipe(startRow, startCol, startRow + if (dy > 0) 1 else -1, startCol)
                                        }
                                    } else if (endTimeMs - downTimeMs >= longPressMs) {
                                        gameViewModel.onCellLongPress(startRow, startCol)
                                    } else {
                                        gameViewModel.onCellTap(startRow, startCol)
                                    }
                                }
                            }
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceDark, RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF17647A), RoundedCornerShape(10.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        grid.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                row.forEach { cell ->
                                    val tileColor = when {
                                        cell.state == CellState.HIDDEN -> Color(0xFF252D38)
                                        cell.state == CellState.FLAGGED -> Color(0xFF3A2D1A)
                                        cell.hasMine -> Color(0xFF6E2630)
                                        else -> when (cell.ammoColor) {
                                            AmmoColor.RED -> Color(0xFFE5534B)
                                            AmmoColor.BLUE -> Color(0xFF388BFD)
                                            AmmoColor.YELLOW -> Color(0xFFD29922)
                                            AmmoColor.GREEN_HEAL -> Color(0xFF3FB950)
                                            AmmoColor.NONE -> Color(0xFF30363D)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        tileColor.copy(alpha = 0.98f),
                                                        tileColor.copy(alpha = 0.52f)
                                                    )
                                                ),
                                                RoundedCornerShape(5.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (cell.state == CellState.HIDDEN) Color(0xFF1C718A)
                                                else tileColor.copy(alpha = 0.92f),
                                                RoundedCornerShape(5.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NeonTileGlyph(cell = cell, modifier = Modifier.fillMaxSize().padding(3.dp))
                                        if (cell.state == CellState.REVEALED && !cell.hasMine && cell.adjacentMines > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(3.dp)
                                                    .background(Color(0xDD07111C), RoundedCornerShape(4.dp))
                                                    .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 3.dp, vertical = 0.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = cell.adjacentMines.toString(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 8.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(uiState.status, color = Color(0xFFB1BAC4), fontSize = if (compact) 9.sp else 11.sp, maxLines = 2)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { gameViewModel.setMode(InteractionMode.DRILL_REVEAL) },
                    modifier = Modifier.weight(1f).height(if (compact) 36.dp else 44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == InteractionMode.DRILL_REVEAL) Neon else SurfaceDark
                    )
                ) {
                    Text(
                        "◉ БУР",
                        color = if (mode == InteractionMode.DRILL_REVEAL) Color(0xFF06131D) else Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
                Button(
                    onClick = { gameViewModel.setMode(InteractionMode.FLAG_CAPTURE) },
                    modifier = Modifier.weight(1f).height(if (compact) 36.dp else 44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == InteractionMode.FLAG_CAPTURE) Color(0xFF238636) else SurfaceDark
                    )
                ) {
                    Text("◎ ЗАХВАТ", color = Color.White, fontWeight = FontWeight.Black)
                }
            }

            val repairAmount = combat.upgrades.toCombatRules().repairAmount
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Booster(
                    title = "СКАНЕР",
                    badge = "FREE",
                    affordable = true,
                    modifier = Modifier.weight(1f).height(if (compact) 40.dp else 50.dp),
                    compact = compact
                ) {
                    gameViewModel.useScanner()
                }
                Booster(
                    title = "АВИАУДАР",
                    badge = "35 DMG • ${BoosterCosts.AIRSTRIKE} CR",
                    affordable = combat.chips >= BoosterCosts.AIRSTRIKE && combat.enemies.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(if (compact) 40.dp else 50.dp),
                    compact = compact
                ) {
                    gameViewModel.launchAirstrike()
                }
                Booster(
                    title = "РЕМОНТ",
                    badge = "+$repairAmount HP • ${BoosterCosts.REPAIR} CR",
                    affordable = combat.chips >= BoosterCosts.REPAIR && combat.baseHp < combat.maxBaseHp,
                    modifier = Modifier.weight(1f).height(if (compact) 40.dp else 50.dp),
                    compact = compact
                ) {
                    gameViewModel.repairBase()
                }
            }
        }
    }

    if (uiState.showPauseDialog) {
        PauseDialog(
            soundEnabled = uiState.soundEnabled,
            wave = combat.wave,
            bestWave = uiState.bestWave,
            onResume = { gameViewModel.closePause() },
            onToggleSound = { gameViewModel.toggleSound() },
            onOpenHangar = { gameViewModel.openHangar() },
            onRestart = { gameViewModel.restartGame() }
        )
    }

    if (uiState.showHangarDialog) {
        HangarDialog(
            combat = combat,
            onUpgrade = { type -> gameViewModel.buyUpgrade(type) },
            onDismiss = { gameViewModel.closeHangar() }
        )
    }

    if (combat.isGameOver) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = DialogBg,
            titleContentColor = Color(0xFFFF667A),
            textContentColor = Color(0xFFD0DCE6),
            title = { Text("БАЗА УНИЧТОЖЕНА", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Волна: ${combat.wave} (рекорд: ${uiState.bestWave}).\n" +
                        "Накоплено чипов: ${combat.chips} CR.\nПопробуйте другую тактику и улучшения в Ангаре."
                )
            },
            confirmButton = {
                TextButton(onClick = { gameViewModel.restartGame() }) {
                    Text("ПОВТОРИТЬ", color = Neon, fontWeight = FontWeight.Black)
                }
            }
        )
    }
}

@Composable
private fun PauseDialog(
    soundEnabled: Boolean,
    wave: Int,
    bestWave: Int,
    onResume: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenHangar: () -> Unit,
    onRestart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onResume,
        containerColor = DialogBg,
        titleContentColor = Neon,
        textContentColor = Color(0xFFD0DCE6),
        title = { Text("ПАУЗА // ТАКТИЧЕСКАЯ СВОДКА", fontSize = 15.sp, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Текущая волна: $wave  •  Рекорд: $bestWave", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("• Красный (Мортира): сильный удар по ближнему врагу.", fontSize = 11.sp)
                Text("• Синий (Крио): урон + замедление цели.", fontSize = 11.sp)
                Text("• Жёлтый (Тесла): урон по группе врагов на одной линии.", fontSize = 11.sp)
                Text("• Зелёный (Ремонт): чинит щит даже при перегрузке.", fontSize = 11.sp)
                Text("• Удержание закрытой клетки: поставить/снять флаг мины.", fontSize = 11.sp)
                Text("• Режим «Захват» по мине: +25 CR и запуск мины во врага.", fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text("ПРОДОЛЖИТЬ", color = Neon, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onToggleSound) {
                    Text(if (soundEnabled) "ЗВУК: ВКЛ" else "ЗВУК: ВЫКЛ", color = Color(0xFF9CB8C8), fontSize = 11.sp)
                }
                TextButton(onClick = onOpenHangar) {
                    Text("АНГАР", color = AmberAccent, fontSize = 11.sp)
                }
                TextButton(onClick = onRestart) {
                    Text("ЗАНОВО", color = Color(0xFFFF667A), fontSize = 11.sp)
                }
            }
        }
    )
}

@Composable
private fun HangarDialog(
    combat: CombatState,
    onUpgrade: (HangarUpgradeType) -> Unit,
    onDismiss: () -> Unit
) {
    val rules = combat.upgrades.toCombatRules()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        titleContentColor = AmberAccent,
        textContentColor = Color(0xFFD0DCE6),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("АНГАР // АПГРЕЙДЫ", fontSize = 15.sp, fontWeight = FontWeight.Black)
                Text("${combat.chips} CR", color = Neon, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HangarUpgradeRow(
                    title = "Мортира (RED)",
                    detail = "Урон: ${rules.mortarDamage} (+${HangarUpgrades.MORTAR_DAMAGE_PER_LEVEL})",
                    level = combat.upgrades.mortarLevel,
                    cost = combat.upgrades.costOf(HangarUpgradeType.MORTAR),
                    canAfford = combat.chips >= combat.upgrades.costOf(HangarUpgradeType.MORTAR),
                    accent = Color(0xFFFF667A),
                    onBuy = { onUpgrade(HangarUpgradeType.MORTAR) }
                )
                HangarUpgradeRow(
                    title = "Крио-луч (BLUE)",
                    detail = "Урон: ${rules.cryoDamage}, замедл.: ${rules.cryoSlowTurns}х",
                    level = combat.upgrades.cryoLevel,
                    cost = combat.upgrades.costOf(HangarUpgradeType.CRYO),
                    canAfford = combat.chips >= combat.upgrades.costOf(HangarUpgradeType.CRYO),
                    accent = Color(0xFF45D9FF),
                    onBuy = { onUpgrade(HangarUpgradeType.CRYO) }
                )
                HangarUpgradeRow(
                    title = "Тесла (YELLOW)",
                    detail = "Урон по линии: ${rules.teslaDamage} (+${HangarUpgrades.TESLA_DAMAGE_PER_LEVEL})",
                    level = combat.upgrades.teslaLevel,
                    cost = combat.upgrades.costOf(HangarUpgradeType.TESLA),
                    canAfford = combat.chips >= combat.upgrades.costOf(HangarUpgradeType.TESLA),
                    accent = AmberAccent,
                    onBuy = { onUpgrade(HangarUpgradeType.TESLA) }
                )
                HangarUpgradeRow(
                    title = "Щит базы (SHIELD)",
                    detail = "Макс HP: ${combat.maxBaseHp} (+${HangarUpgrades.SHIELD_HP_PER_LEVEL} и ремонт)",
                    level = combat.upgrades.shieldLevel,
                    cost = combat.upgrades.costOf(HangarUpgradeType.SHIELD),
                    canAfford = combat.chips >= combat.upgrades.costOf(HangarUpgradeType.SHIELD),
                    accent = GreenAccent,
                    onBuy = { onUpgrade(HangarUpgradeType.SHIELD) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("В БОЙ", color = Neon, fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
private fun HangarUpgradeRow(
    title: String,
    detail: String,
    level: Int,
    cost: Int,
    canAfford: Boolean,
    accent: Color,
    onBuy: () -> Unit
) {
    val isMax = level >= HangarUpgrades.MAX_LEVEL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$title  •  Ур. $level/${HangarUpgrades.MAX_LEVEL}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text(detail, color = Color(0xFFB8D4E4), fontSize = 10.sp)
        }
        Button(
            onClick = onBuy,
            enabled = !isMax && canAfford,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.copy(alpha = 0.85f),
                disabledContainerColor = BorderDark
            ),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                if (isMax) "MAX" else "$cost CR",
                color = if (!isMax && canAfford) Color(0xFF06131D) else Color(0xFF9CB8C8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun RadarPanel(
    baseHp: Int,
    maxBaseHp: Int,
    stun: Int,
    turns: Int,
    enemies: List<EnemyUnit>,
    effectEvents: List<CombatEvent>,
    effectTargets: List<EnemyUnit>,
    effectKey: Int,
    compact: Boolean,
    shakeOffsetX: Float,
    shakeOffsetY: Float
) {
    val safeMaxHp = maxBaseHp.coerceAtLeast(1)
    val shieldFraction = (baseHp.toFloat() / safeMaxHp).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = shakeOffsetX
                translationY = shakeOffsetY
            }
            .background(SurfaceDark, RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF17647A), RoundedCornerShape(10.dp))
            .padding(if (compact) 7.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("◉  TACTICAL SENSOR ARRAY", color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("ХОД $turns", color = Color.LightGray, fontSize = 10.sp)
        }
        TacticalBattlefield(
            enemies = enemies,
            baseHp = baseHp,
            maxBaseHp = safeMaxHp,
            turretStunTurns = stun,
            turn = turns,
            effectEvents = effectEvents,
            effectTargets = effectTargets,
            effectKey = effectKey,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 58.dp else 122.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BASE SHIELD", color = Color(0xFF9CB8C8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                "$baseHp / $safeMaxHp HP",
                color = if (shieldFraction > 0.3f) GreenAccent else Color(0xFFF85149),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(BorderDark, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(shieldFraction)
                    .height(6.dp)
                    .background(GreenAccent, RoundedCornerShape(4.dp))
            )
        }
        Text(
            "HOSTILES DETECTED  /  ${enemies.size}",
            color = if (enemies.isEmpty()) Color(0xFF5CFFB0) else Color(0xFFFF667A),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Text("♦  MORTAR", color = Color(0xFFFF667A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("♦  CRYO", color = Color(0xFF45D9FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("♦  TESLA", color = AmberAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        if (stun > 0) Text("⚠ ПЕРЕГРУЗКА: $stun хода", color = Color(0xFFF85149), fontSize = 10.sp)
    }
}

@Composable
private fun Booster(
    title: String,
    badge: String,
    affordable: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = if (affordable) Color(0xFF1B667C) else BorderDark.copy(alpha = 0.6f)
    Column(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF172A3A), Color(0xFF0B131F))), RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 4.dp else 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp)
    ) {
        Text(
            title,
            color = if (affordable) Color(0xFFB8D4E4) else Color(0xFF6B7E8C),
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            badge,
            color = if (affordable) Neon else Color(0xFF5A6D7B),
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}
