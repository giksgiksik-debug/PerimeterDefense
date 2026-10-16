package com.giksik.perimeterdefense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giksik.perimeterdefense.data.GamePersistence
import com.giksik.perimeterdefense.data.SavedGame
import com.giksik.perimeterdefense.domain.AmmoColor
import com.giksik.perimeterdefense.domain.CombatEngine
import com.giksik.perimeterdefense.domain.CombatEventType
import com.giksik.perimeterdefense.domain.CombatState
import com.giksik.perimeterdefense.domain.EnemyType
import com.giksik.perimeterdefense.domain.EnemyUnit
import com.giksik.perimeterdefense.domain.CellState
import com.giksik.perimeterdefense.domain.GridEngine
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

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("PERIMETER // DEFENSE", color = Neon, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("TACTICAL NETWORK ONLINE  •  WAVE %02d  •  CR %04d".format(combat.wave, combat.chips), color = Color(0xFF9CB8C8), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Ⅱ", color = Color.White, fontSize = 24.sp)
        }

        RadarPanel(baseHp = combat.baseHp, stun = combat.turretStunTurns, turns = combat.turn, enemies = combat.enemies)

        Text("ENERGY MATRIX  //  6 × 6", color = Neon, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
                                .clickable {
                                    val result = if (mode == InteractionMode.DRILL_REVEAL) {
                                        GridEngine.reveal(grid, rowIndex, colIndex)
                                    } else {
                                        GridEngine.capture(grid, rowIndex, colIndex)
                                    }
                                    grid = result.grid
                                    status = result.message
                                    if (result.turnConsumed) {
                                        var nextCombat = combat
                                        if (result.detonatedMines > 0) {
                                            nextCombat = CombatEngine.registerMineDetonation(nextCombat).state
                                        }
                                        if (result.capturedMines > 0) {
                                            nextCombat = nextCombat.copy(chips = nextCombat.chips + result.capturedMines * 25)
                                            repeat(result.capturedMines) {
                                                nextCombat = CombatEngine.launchCapturedMine(nextCombat).state
                                            }
                                        }

                                        val matchResolution = GridEngine.resolveMatches(result.grid)
                                        grid = matchResolution.grid
                                        val attackResult = CombatEngine.resolveMatches(nextCombat, matchResolution.groups)
                                        nextCombat = CombatEngine.advanceTurn(attackResult.state).state
                                        combat = nextCombat

                                        if (matchResolution.groups.isNotEmpty()) {
                                            val matchedCells = matchResolution.groups.sumOf { it.size }
                                            status += " Серии: ${matchResolution.groups.size}, срабатываний клеток: $matchedCells."
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
                                        if (combat.isGameOver) status += " База разрушена."
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (cell.state == CellState.REVEALED && !cell.hasMine && cell.adjacentMines > 0) {
                                Text(
                                    text = cell.adjacentMines.toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            } else {
                                NeonTileGlyph(cell = cell, modifier = Modifier.fillMaxSize().padding(3.dp))
                            }
                        }
                    }
                }
            }
        }

        Text(status, color = Color(0xFFB1BAC4), fontSize = 11.sp, maxLines = 2)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { mode = InteractionMode.DRILL_REVEAL },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (mode == InteractionMode.DRILL_REVEAL) Neon else SurfaceDark)
            ) { Text("⌖  БУР", color = Color(0xFF06131D), fontWeight = FontWeight.Black) }
            Button(
                onClick = { mode = InteractionMode.FLAG_CAPTURE },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (mode == InteractionMode.FLAG_CAPTURE) Color(0xFF238636) else SurfaceDark)
            ) { Text("⬡  ЗАХВАТ", color = Color.White, fontWeight = FontWeight.Black) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Booster("СКАНЕР", "SCAN", Modifier.weight(1f)) {
                val mines = grid.flatten().count { it.hasMine }
                status = "СКАНЕР: мин на поле — $mines; целей на радаре — ${combat.enemies.size}."
            }
            Booster("АВИАУДАР", "18 DMG", Modifier.weight(1f)) {
                val result = CombatEngine.launchAirstrike(combat)
                combat = CombatEngine.advanceTurn(result.state).state
                val damaged = result.events.count { it.type == CombatEventType.ENEMY_DAMAGED }
                val destroyed = result.events.count { it.type == CombatEventType.ENEMY_DESTROYED }
                status = if (damaged == 0) "Авиаудар: целей нет." else "Авиаудар: поражено целей — $damaged, уничтожено — $destroyed."
            }
            Booster("РЕМОНТ", "+15 HP", Modifier.weight(1f)) {
                val result = CombatEngine.repairBase(combat)
                combat = CombatEngine.advanceTurn(result.state).state
                val repaired = result.events.filter { it.type == CombatEventType.BASE_REPAIRED }.sumOf { it.amount }
                status = if (repaired == 0) "Ремонт: щит уже полон." else "Ремонт: восстановлено $repaired HP."
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
private fun RadarPanel(baseHp: Int, stun: Int, turns: Int, enemies: List<EnemyUnit>) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF17647A), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("◉  TACTICAL SENSOR ARRAY", color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("ХОД $turns", color = Color.LightGray, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).fillMaxWidth().height(70.dp)
                .background(Brush.radialGradient(colors = listOf(Color(0xFF0C2A35), Color(0xFF07151F))), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF00AFC6).copy(alpha = 0.62f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (progress in (CombatEngine.PATH_LENGTH - 1) downTo 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (0 until GridEngine.GRID_SIZE).forEach { lane ->
                            val here = enemies.filter { it.lane == lane && it.progress == progress }
                            val cellColor = when {
                                here.isEmpty() -> Color(0xFF10262B)
                                here.any { it.type == EnemyType.BOSS } -> Color(0xFFD29922)
                                else -> Color(0xFF8B2635)
                            }
                            Box(
                                modifier = Modifier.weight(1f).height(8.dp)
                                    .background(cellColor, RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (here.isNotEmpty()) {
                                    Text(
                                        if (here.size > 1) here.size.toString() else "◆",
                                        color = Color.White,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.background(Brush.verticalGradient(listOf(Color(0xFF172A3A), Color(0xFF0B131F))), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF1B667C), RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = Color(0xFFB8D4E4), fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(badge, color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}
