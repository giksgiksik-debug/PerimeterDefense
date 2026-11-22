package com.giksgik.perimeterdefense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.random.Random

private data class Enemy(var progress: Float, var hp: Int, val speed: Float)
private data class Tower(val lane: Int, val damage: Int, val range: Float, val cooldown: Float, var timer: Float = 0f)
private class GameState {
    var baseHp by mutableIntStateOf(100)
    var coins by mutableIntStateOf(80)
    var wave by mutableIntStateOf(1)
    var score by mutableIntStateOf(0)
    var gems = mutableStateListOf<Int>().apply { repeat(35) { add(Random.nextInt(0, 5)) } }
    val enemies = mutableStateListOf<Enemy>()
    val towers = mutableStateListOf<Tower>()
    var selected by mutableIntStateOf(-1)
    var spawnTimer = 0f
    var gameOver by mutableStateOf(false)

    fun tick(dt: Float) {
        if (gameOver) return
        spawnTimer += dt
        if (spawnTimer >= max(1.4f, 3.5f - wave * .12f)) {
            spawnTimer = 0f
            enemies.add(Enemy(0f, 24 + wave * 5, .035f + wave * .0015f))
            if (enemies.size % 8 == 0) wave++
        }
        val leaked = mutableListOf<Enemy>()
        enemies.forEach { e ->
            e.progress += e.speed * dt
            if (e.progress >= 1f) leaked.add(e)
        }
        enemies.removeAll(leaked.toSet())
        if (leaked.isNotEmpty()) baseHp = max(0, baseHp - leaked.size * 10)
        towers.forEach { t ->
            t.timer -= dt
            if (t.timer <= 0f) {
                val target = enemies.filter { kotlin.math.abs(it.progress - t.lane / 10f) < t.range }.maxByOrNull { it.progress }
                if (target != null) {
                    target.hp -= t.damage
                    t.timer = t.cooldown
                }
            }
        }
        enemies.removeAll { it.hp <= 0 }
        if (baseHp <= 0) gameOver = true
    }

    fun matchAt(index: Int) {
        if (selected < 0) { selected = index; return }
        val a = selected; val b = index; selected = -1
        if (a == b || kotlin.math.abs(a / 7 - b / 7) + kotlin.math.abs(a % 7 - b % 7) != 1) return
        val temp = gems[a]; gems[a] = gems[b]; gems[b] = temp
        val marked = linkedSetOf<Int>()
        for (i in gems.indices) {
            val row = i / 7; val col = i % 7
            val horizontal = (max(0, col - 2)..minOf(6, col + 2)).filter { gems[row * 7 + it] == gems[i] }
            val vertical = (max(0, row - 2)..minOf(4, row + 2)).filter { gems[it * 7 + col] == gems[i] }
            if (horizontal.size >= 3) horizontal.forEach { marked.add(row * 7 + it) }
            if (vertical.size >= 3) vertical.forEach { marked.add(it * 7 + col) }
        }
        if (marked.size < 3) { gems[a] = gems[b]; gems[b] = temp; return }
        coins += marked.size * 5
        score += marked.size * 10
        marked.forEach { gems[it] = -1 }
        for (col in 0..6) {
            val values = (0..4).map { gems[it * 7 + col] }.filter { it >= 0 }
            val fill = List(5 - values.size) { Random.nextInt(0, 5) }
            (0..4).forEach { r -> gems[r * 7 + col] = (fill + values)[r] }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { DefenseGame() } }
    }
}

@Composable
private fun DefenseGame() {
    val game = remember { GameState() }
    LaunchedEffect(game) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, .05f)
                last = now
                game.tick(dt)
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF101923)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PERIMETER DEFENSE", color = Color(0xFF80D8FF), fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("БАЗА", "${game.baseHp}%", Color(0xFFFF7777))
            Stat("РЕСУРСЫ", "${game.coins}", Color(0xFFFFD166))
            Stat("ВОЛНА", "${game.wave}", Color(0xFF80D8FF))
            Stat("ОЧКИ", "${game.score}", Color.White)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF182B31), RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF31535B), RoundedCornerShape(14.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                val pathY = size.height * .52f
                drawLine(Color(0xFF52656A), Offset(0f, pathY), Offset(size.width, pathY), 22f)
                drawLine(Color(0xFF9AA7A7), Offset(0f, pathY), Offset(size.width, pathY), 2f)
                drawCircle(Color(0xFF57D6A1), 22f, Offset(size.width - 28f, pathY))
                game.towers.forEach { t ->
                    val x = size.width * (t.lane / 10f)
                    drawCircle(Color(0xFF55C8FF), 15f, Offset(x, pathY - 42f))
                    drawCircle(Color(0xFFBDEBFF), 5f, Offset(x, pathY - 42f))
                }
                game.enemies.forEach { e ->
                    drawCircle(if (e.hp > 30) Color(0xFFFF6262) else Color(0xFFFFB454), 11f, Offset(size.width * e.progress, pathY))
                    drawRect(Color(0xFF101010), Offset(size.width * e.progress - 12f, pathY - 19f), androidx.compose.ui.geometry.Size(24f, 3f))
                    drawRect(Color(0xFF7CFF9B), Offset(size.width * e.progress - 12f, pathY - 19f), androidx.compose.ui.geometry.Size(24f * (e.hp.toFloat() / (24 + game.wave * 5)).coerceIn(0f,1f), 3f))
                }
            }
            Text("МАРШРУТ ВРАГОВ  →  БАЗА", Modifier.align(Alignment.TopCenter).padding(12.dp), color = Color.LightGray, fontSize = 11.sp)
            Text("Башни автоматически атакуют врагов", Modifier.align(Alignment.BottomCenter).padding(10.dp), color = Color(0xFF9CB6C0), fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (game.coins >= 50 && game.towers.size < 5) { game.coins -= 50; game.towers.add(Tower(lane = 2 + game.towers.size * 1, damage = 18, range = .22f, cooldown = .65f)) } }, modifier = Modifier.weight(1f)) { Text("Пулемёт · 50") }
            Button(onClick = { if (game.coins >= 90 && game.towers.size < 5) { game.coins -= 90; game.towers.add(Tower(lane = 3 + game.towers.size, damage = 55, range = .32f, cooldown = 1.8f)) } }, modifier = Modifier.weight(1f)) { Text("Ракета · 90") }
        }
        Spacer(Modifier.height(8.dp))
        Text("СОБИРАЙ 3+ ОДИНАКОВЫХ КРИСТАЛЛА — БОЙ НЕ ОСТАНАВЛИВАЕТСЯ", color = Color(0xFFB6C9D2), fontSize = 10.sp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0..4) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0..6) {
                    val i = r * 7 + c
                    val colors = listOf(Color(0xFF45B8FF), Color(0xFFFFC857), Color(0xFFB18CFF), Color(0xFF59D6A5), Color(0xFFFF718A))
                    Box(Modifier.weight(1f).aspectRatio(1f).background(colors[game.gems[i]], RoundedCornerShape(7.dp)).border(if (game.selected == i) 3.dp else 1.dp, if (game.selected == i) Color.White else Color(0xFF53616B), RoundedCornerShape(7.dp)).clickable { game.matchAt(i) }, contentAlignment = Alignment.Center) {
                        Text("◆", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        if (game.gameOver) {
            Text("БАЗА УНИЧТОЖЕНА", color = Color(0xFFFF6666), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { game.baseHp = 100; game.coins = 80; game.wave = 1; game.score = 0; game.enemies.clear(); game.towers.clear(); game.spawnTimer = 0f; game.gameOver = false }) { Text("Начать заново") }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF8DA5B0), fontSize = 9.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
