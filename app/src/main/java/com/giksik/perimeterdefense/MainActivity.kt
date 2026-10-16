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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giksik.perimeterdefense.domain.AmmoColor
import com.giksik.perimeterdefense.domain.CombatEngine
import com.giksik.perimeterdefense.domain.CombatEventType
import com.giksik.perimeterdefense.domain.CombatState
import com.giksik.perimeterdefense.domain.EnemyType
import com.giksik.perimeterdefense.domain.EnemyUnit
import com.giksik.perimeterdefense.domain.CellState
import com.giksik.perimeterdefense.domain.GridEngine
import com.giksik.perimeterdefense.domain.InteractionMode

private val Background = Color(0xFF0D1117)
private val SurfaceDark = Color(0xFF161B22)
private val BorderDark = Color(0xFF30363D)
private val Neon = Color(0xFF58A6FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    PerimeterGame()
                }
            }
        }
    }
}

@Composable
private fun PerimeterGame() {
    var grid by remember { mutableStateOf(GridEngine.newGrid()) }
    var mode by remember { mutableStateOf(InteractionMode.DRILL_REVEAL) }
    var combat by remember { mutableStateOf(CombatEngine.newGame()) }
    var status by remember { mutableStateOf("Периметр стабилен. Выберите плитку.") }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("PERIMETER DEFENSE", color = Neon, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("WAVE %02d  •  ЧИПЫ %04d".format(combat.wave, combat.chips), color = Color.LightGray, fontSize = 11.sp)
            }
            Text("Ⅱ", color = Color.White, fontSize = 24.sp)
        }

        RadarPanel(baseHp = combat.baseHp, stun = combat.turretStunTurns, turns = combat.turn, enemies = combat.enemies)

        Text("ЭНЕРГОМАТРИЦА  //  6 × 6", color = Neon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(10.dp)).border(1.dp, BorderDark, RoundedCornerShape(10.dp)).padding(6.dp),
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
                                .background(tileColor, RoundedCornerShape(5.dp))
                                .border(1.dp, if (cell.state == CellState.HIDDEN) BorderDark else Color.White.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
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
                            Text(
                                text = when {
                                    cell.state == CellState.HIDDEN -> "⬡"
                                    cell.hasMine -> "✹"
                                    cell.adjacentMines > 0 -> cell.adjacentMines.toString()
                                    cell.ammoColor == AmmoColor.GREEN_HEAL -> "+"
                                    else -> when (cell.ammoColor) {
                                        AmmoColor.RED -> "R"
                                        AmmoColor.BLUE -> "B"
                                        AmmoColor.YELLOW -> "Y"
                                        else -> "·"
                                    }
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
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
            ) { Text("БУР", color = Color.White, fontWeight = FontWeight.Bold) }
            Button(
                onClick = { mode = InteractionMode.FLAG_CAPTURE },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (mode == InteractionMode.FLAG_CAPTURE) Color(0xFF238636) else SurfaceDark)
            ) { Text("ЗАХВАТ", color = Color.White, fontWeight = FontWeight.Bold) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Booster("СКАНЕР", "AD", Modifier.weight(1f))
            Booster("АВИАУДАР", "AD", Modifier.weight(1f))
            Booster("РЕМОНТ", "+15", Modifier.weight(1f))
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
        modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(10.dp)).border(1.dp, BorderDark, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("ТАКТИЧЕСКИЙ РАДАР", color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("ХОД $turns", color = Color.LightGray, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).fillMaxWidth().height(70.dp)
                .background(Color(0xFF0B171D), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF1F6B55), RoundedCornerShape(6.dp)),
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
            Text("ЩИТ ПЕРИМЕТРА", color = Color.LightGray, fontSize = 10.sp)
            Text("$baseHp / 100 HP", color = if (baseHp > 30) Color(0xFF3FB950) else Color(0xFFF85149), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().fillMaxWidth().height(6.dp).background(BorderDark, RoundedCornerShape(4.dp))) {
            Box(modifier = Modifier.fillMaxWidth(baseHp / 100f).size(height = 6.dp, width = 1.dp).background(Color(0xFF3FB950), RoundedCornerShape(4.dp)))
        }
        Text("ВРАГОВ: ${enemies.size}", color = Color.LightGray, fontSize = 9.sp)
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Text("🔴 МОРТИРА", color = Color(0xFFE5534B), fontSize = 9.sp)
            Text("🔵 КРИО", color = Color(0xFF58A6FF), fontSize = 9.sp)
            Text("🟡 ТЕСЛА", color = Color(0xFFD29922), fontSize = 9.sp)
        }
        if (stun > 0) Text("⚠ ПЕРЕГРУЗКА: $stun хода", color = Color(0xFFF85149), fontSize = 10.sp)
    }
}

@Composable
private fun Booster(title: String, badge: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(SurfaceDark, RoundedCornerShape(8.dp)).border(1.dp, BorderDark, RoundedCornerShape(8.dp)).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(badge, color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}
