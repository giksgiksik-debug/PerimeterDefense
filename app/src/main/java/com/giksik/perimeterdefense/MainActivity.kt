package com.giksik.perimeterdefense

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.giksik.perimeterdefense.data.GamePersistence
import com.giksik.perimeterdefense.data.SavedGame
import com.giksik.perimeterdefense.domain.AmmoColor
import com.giksik.perimeterdefense.domain.CombatEngine
import com.giksik.perimeterdefense.domain.CombatEvent
import com.giksik.perimeterdefense.domain.CombatEventType
import com.giksik.perimeterdefense.domain.CombatState
import com.giksik.perimeterdefense.domain.EnemyType
import com.giksik.perimeterdefense.domain.EnemyUnit
import com.giksik.perimeterdefense.domain.CellState
import com.giksik.perimeterdefense.domain.GridEngine
import com.giksik.perimeterdefense.domain.GridResult
import com.giksik.perimeterdefense.domain.InteractionMode

private val Background = Color(0xFF070B13)
private val SurfaceDark = Color(0xE6101B2A)
private val BorderDark = Color(0xFF244454)
private val Neon = Color(0xFF00E5FF)

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
private fun PerimeterGame() {
    val appContext = LocalContext.current.applicationContext
    val persistence = remember { GamePersistence(appContext) }
    var saveReady by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(GridEngine.newGrid()) }
    var mode by remember { mutableStateOf(InteractionMode.DRILL_REVEAL) }
    var combat by remember { mutableStateOf(CombatEngine.newGame()) }
    var status by remember { mutableStateOf("Периметр стабилен. Выберите плитку.") }
    var visualEvents by remember { mutableStateOf<List<CombatEvent>>(emptyList()) }
    var visualTargets by remember { mutableStateOf<List<EnemyUnit>>(emptyList()) }
    var visualEffectKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val saved = persistence.load()
        if (saved != null) {
            grid = saved.grid
            combat = saved.combat
            mode = saved.mode
        }
        saveReady = true
    }

    LaunchedEffect(grid, combat, mode, saveReady) {
        if (saveReady) persistence.save(SavedGame(grid = grid, combat = combat, mode = mode))
    }

    fun applyGridResult(result: GridResult) {
        grid = result.grid
        status = result.message
        if (!result.turnConsumed) return

        var nextCombat = combat
        val turnVisualEvents = mutableListOf<CombatEvent>()
        val visualCandidates = LinkedHashMap<Long, EnemyUnit>().apply {
            putAll(combat.enemies.associateBy { it.id })
        }
        if (result.detonatedMines > 0) {
            val detonation = CombatEngine.registerMineDetonation(nextCombat)
            nextCombat = detonation.state
            turnVisualEvents += detonation.events
        }
        if (result.capturedMines > 0) {
            nextCombat = nextCombat.copy(chips = nextCombat.chips + result.capturedMines * 25)
            repeat(result.capturedMines) {
                visualCandidates.putAll(nextCombat.enemies.associateBy { it.id })
                val capturedShot = CombatEngine.launchCapturedMine(nextCombat)
                nextCombat = capturedShot.state
                turnVisualEvents += capturedShot.events
            }
        }

        val matchResolution = GridEngine.resolveMatches(result.grid)
        grid = matchResolution.grid
        visualCandidates.putAll(nextCombat.enemies.associateBy { it.id })
        val attackResult = CombatEngine.resolveMatches(nextCombat, matchResolution.groups)
        turnVisualEvents += attackResult.events
        visualCandidates.putAll(attackResult.state.enemies.associateBy { it.id })
        val advanceResult = CombatEngine.advanceTurn(attackResult.state)
        turnVisualEvents += advanceResult.events
        nextCombat = advanceResult.state
        combat = nextCombat

        val hasVisibleCombatFx = turnVisualEvents.any {
            it.type == CombatEventType.ENEMY_DAMAGED ||
                it.type == CombatEventType.ENEMY_DESTROYED ||
                it.type == CombatEventType.BASE_DAMAGED ||
                it.type == CombatEventType.BASE_REPAIRED ||
                it.type == CombatEventType.TURRETS_STUNNED
        }
        if (hasVisibleCombatFx) {
            visualEvents = turnVisualEvents.toList()
            val targetIds = turnVisualEvents.mapNotNull { it.enemyId }.toSet()
            visualTargets = visualCandidates.values.filter { it.id in targetIds }
            visualEffectKey += 1
        }

        if (matchResolution.groups.isNotEmpty()) {
            val matchedCells = matchResolution.groups.sumOf { it.size }
            status += " Серии: ${matchResolution.groups.size}, клеток: $matchedCells."
        }
        if (attackResult.events.any { it.type == CombatEventType.ENEMY_DESTROYED }) {
            status += " Враг уничтожен."
        }
        if (attackResult.events.any { it.type == CombatEventType.ENEMY_SLOWED }) {
            status += " Враг замедлен."
        }
        if (result.detonatedMines > 0) {
            status += " Щит повреждён, турели перегружены."
        }
        if (nextCombat.isGameOver) status += " База разрушена."
    }

    fun handleCellTap(row: Int, col: Int) {
        val result = if (mode == InteractionMode.DRILL_REVEAL) {
            GridEngine.reveal(grid, row, col)
        } else {
            GridEngine.capture(grid, row, col)
        }
        applyGridResult(result)
    }

    fun handleSwipe(row1: Int, col1: Int, row2: Int, col2: Int) {
        if (mode != InteractionMode.DRILL_REVEAL) {
            status = "РЕЖИМ ЗАХВАТА: коснись закрытой клетки."
            return
        }
        val swap = GridEngine.swapAdjacent(grid, row1, col1, row2, col2)
        status = swap.message
        if (swap.accepted) {
            applyGridResult(
                GridResult(
                    grid = swap.grid,
                    revealedCells = 2,
                    turnConsumed = true,
                    message = swap.message
                )
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 820.dp
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("PERIMETER // DEFENSE", color = Neon, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("TACTICAL NETWORK ONLINE  •  WAVE %02d  •  CR %04d".format(combat.wave, combat.chips), color = Color(0xFF9CB8C8), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Ⅱ", color = Color.White, fontSize = 24.sp)
        }

        RadarPanel(
            baseHp = combat.baseHp,
            stun = combat.turretStunTurns,
            turns = combat.turn,
            enemies = combat.enemies,
            effectEvents = visualEvents,
            effectTargets = visualTargets,
            effectKey = visualEffectKey,
            compact = compact
        )

        Text(
            "ENERGY MATRIX  //  6 × 6  •  СВАЙП 3+",
            color = Neon,
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Column(
            modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF17647A), RoundedCornerShape(10.dp)).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            grid.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEachIndexed { colIndex, cell ->
                        val tileColor = when {
                            cell.state == CellState.HIDDEN -> Color(0xFF252D38)
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
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                                .background(Brush.radialGradient(colors = listOf(tileColor.copy(alpha = 0.98f), tileColor.copy(alpha = 0.52f))), RoundedCornerShape(5.dp))
                                .border(1.dp, if (cell.state == CellState.HIDDEN) Color(0xFF1C718A) else tileColor.copy(alpha = 0.92f), RoundedCornerShape(5.dp))
                                .pointerInput(grid, mode, rowIndex, colIndex) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
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
                                            stillPressed = change.pressed
                                            change.consume()
                                        }
                                        if (!cancelled) {
                                            val dx = endPosition.x - down.position.x
                                            val dy = endPosition.y - down.position.y
                                            val swipeThreshold = 18.dp.toPx()
                                            if (maxOf(abs(dx), abs(dy)) >= swipeThreshold) {
                                                if (abs(dx) >= abs(dy)) {
                                                    handleSwipe(
                                                        rowIndex, colIndex,
                                                        rowIndex, colIndex + if (dx > 0) 1 else -1
                                                    )
                                                } else {
                                                    handleSwipe(
                                                        rowIndex, colIndex,
                                                        rowIndex + if (dy > 0) 1 else -1, colIndex
                                                    )
                                                }
                                            } else {
                                                handleCellTap(rowIndex, colIndex)
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            NeonTileGlyph(cell = cell, modifier = Modifier.fillMaxSize().padding(3.dp))
                            if (cell.state == CellState.REVEALED && !cell.hasMine && cell.adjacentMines > 0) {
                                Box(
                                    modifier = Modifier.align(Alignment.TopEnd)
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

        Text(status, color = Color(0xFFB1BAC4), fontSize = if (compact) 9.sp else 11.sp, maxLines = 2)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { mode = InteractionMode.DRILL_REVEAL },
                modifier = Modifier.weight(1f).height(if (compact) 36.dp else 44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (mode == InteractionMode.DRILL_REVEAL) Neon else SurfaceDark)
            ) { Text("⌖  БУР", color = Color(0xFF06131D), fontWeight = FontWeight.Black) }
            Button(
                onClick = { mode = InteractionMode.FLAG_CAPTURE },
                modifier = Modifier.weight(1f).height(if (compact) 36.dp else 44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (mode == InteractionMode.FLAG_CAPTURE) Color(0xFF238636) else SurfaceDark)
            ) { Text("⬡  ЗАХВАТ", color = Color.White, fontWeight = FontWeight.Black) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Booster("СКАНЕР", "SCAN", Modifier.weight(1f).height(if (compact) 40.dp else 50.dp), compact = compact) {
                val mines = grid.flatten().count { it.hasMine }
                status = "СКАНЕР: мин на поле — $mines; целей на радаре — ${combat.enemies.size}."
            }
            Booster("АВИАУДАР", "18 DMG", Modifier.weight(1f).height(if (compact) 40.dp else 50.dp), compact = compact) {
                val result = CombatEngine.launchAirstrike(combat)
                if (result.events.isNotEmpty()) {
                    visualEvents = result.events
                    val targetIds = result.events.mapNotNull { it.enemyId }.toSet()
                    visualTargets = combat.enemies.filter { it.id in targetIds }
                    visualEffectKey += 1
                }
                combat = CombatEngine.advanceTurn(result.state).state
                val damaged = result.events.count { it.type == CombatEventType.ENEMY_DAMAGED }
                val destroyed = result.events.count { it.type == CombatEventType.ENEMY_DESTROYED }
                status = if (damaged == 0) "Авиаудар: целей нет." else "Авиаудар: поражено целей — $damaged, уничтожено — $destroyed."
            }
            Booster("РЕМОНТ", "+15 HP", Modifier.weight(1f).height(if (compact) 40.dp else 50.dp), compact = compact) {
                val result = CombatEngine.repairBase(combat)
                if (result.events.isNotEmpty()) {
                    visualEvents = result.events
                    visualTargets = emptyList()
                    visualEffectKey += 1
                }
                combat = CombatEngine.advanceTurn(result.state).state
                val repaired = result.events.filter { it.type == CombatEventType.BASE_REPAIRED }.sumOf { it.amount }
                status = if (repaired == 0) "Ремонт: щит уже полон." else "Ремонт: восстановлено $repaired HP."
            }
        }
    }
    }

    if (combat.isGameOver) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("БАЗА УНИЧТОЖЕНА") },
            text = { Text("Волна: ${combat.wave}. Чипы: ${combat.chips}. Попробуйте другую тактику.") },
            confirmButton = {
                TextButton(onClick = {
                    grid = GridEngine.newGrid()
                    combat = CombatEngine.newGame()
                    mode = InteractionMode.DRILL_REVEAL
                    status = "Периметр стабилен. Выберите плитку."
                }) {
                    Text("ПОВТОРИТЬ")
                }
            }
        )
    }
}

@Composable
private fun RadarPanel(
    baseHp: Int,
    stun: Int,
    turns: Int,
    enemies: List<EnemyUnit>,
    effectEvents: List<CombatEvent>,
    effectTargets: List<EnemyUnit>,
    effectKey: Int,
    compact: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF17647A), RoundedCornerShape(10.dp)).padding(if (compact) 7.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("◉  TACTICAL SENSOR ARRAY", color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("ХОД $turns", color = Color.LightGray, fontSize = 10.sp)
        }
        TacticalBattlefield(
            enemies = enemies,
            baseHp = baseHp,
            turn = turns,
            effectEvents = effectEvents,
            effectTargets = effectTargets,
            effectKey = effectKey,
            modifier = Modifier.fillMaxWidth().height(if (compact) 58.dp else 122.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("BASE SHIELD", color = Color(0xFF9CB8C8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("$baseHp / 100 HP", color = if (baseHp > 30) Color(0xFF3FB950) else Color(0xFFF85149), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().fillMaxWidth().height(6.dp).background(BorderDark, RoundedCornerShape(4.dp))) {
            Box(modifier = Modifier.fillMaxWidth(baseHp / 100f).size(height = 6.dp, width = 1.dp).background(Color(0xFF3FB950), RoundedCornerShape(4.dp)))
        }
        Text("HOSTILES DETECTED  /  ${enemies.size}", color = if (enemies.isEmpty()) Color(0xFF5CFFB0) else Color(0xFFFF667A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Text("◆  MORTAR", color = Color(0xFFFF667A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("◆  CRYO", color = Color(0xFF45D9FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("◆  TESLA", color = Color(0xFFFFC857), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        if (stun > 0) Text("⚠ ПЕРЕГРУЗКА: $stun хода", color = Color(0xFFF85149), fontSize = 10.sp)
    }
}

@Composable
private fun Booster(
    title: String,
    badge: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.background(Brush.verticalGradient(listOf(Color(0xFF172A3A), Color(0xFF0B131F))), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF1B667C), RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = if (compact) 4.dp else 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 4.dp)
    ) {
        Text(title, color = Color(0xFFB8D4E4), fontSize = if (compact) 8.sp else 9.sp, fontWeight = FontWeight.Black)
        Text(badge, color = Neon, fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.Black)
    }
}
