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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.random.Random

private val Night = Color(0xFF171526)
private val Gold = Color(0xFFFFD27A)
private val Panel = Color(0xFF30263B)
private val GemColors = listOf(Color(0xFF49BFFF), Color(0xFFFF625F), Color(0xFFB77BFF), Color(0xFF55D68A), Color(0xFFFFC84E))

private data class Enemy(var progress: Float, var hp: Int, val speed: Float, val kind: Int)
private data class Tower(val lane: Int, val damage: Int, val range: Float, val cooldown: Float, val kind: Int, var timer: Float = 0f)
private class GameState {
    var baseHp by mutableIntStateOf(20)
    var coins by mutableIntStateOf(180)
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
        if (spawnTimer >= max(1.25f, 3.1f - wave * .10f)) {
            spawnTimer = 0f
            enemies.add(Enemy(0f, 32 + wave * 7, .028f + wave * .0014f, (wave + enemies.size) % 4))
            if ((score / 120 + 1) > wave) wave++
        }
        val leaked = mutableListOf<Enemy>()
        enemies.forEach { it.progress += it.speed * dt; if (it.progress >= 1f) leaked.add(it) }
        enemies.removeAll(leaked.toSet())
        if (leaked.isNotEmpty()) baseHp = max(0, baseHp - leaked.size * 2)
        towers.forEach { t ->
            t.timer -= dt
            if (t.timer <= 0f) {
                val target = enemies.filter { kotlin.math.abs(it.progress - t.lane / 10f) < t.range }.maxByOrNull { it.progress }
                if (target != null) { target.hp -= t.damage; t.timer = t.cooldown }
            }
        }
        val killed = enemies.filter { it.hp <= 0 }
        if (killed.isNotEmpty()) { score += killed.size * 10; coins += killed.size * 4 }
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
            val h = (max(0, col - 2)..minOf(6, col + 2)).filter { gems[row * 7 + it] == gems[i] }
            val v = (max(0, row - 2)..minOf(4, row + 2)).filter { gems[it * 7 + col] == gems[i] }
            if (h.size >= 3) h.forEach { marked.add(row * 7 + it) }
            if (v.size >= 3) v.forEach { marked.add(it * 7 + col) }
        }
        if (marked.size < 3) { gems[a] = gems[b]; gems[b] = temp; return }
        coins += marked.size * 12; score += marked.size * 10
        marked.forEach { gems[it] = -1 }
        for (col in 0..6) {
            val values = (0..4).map { gems[it * 7 + col] }.filter { it >= 0 }
            val fill = List(5 - values.size) { Random.nextInt(0, 5) }
            (0..4).forEach { row -> gems[row * 7 + col] = (fill + values)[row] }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(23, 21, 38)
        window.navigationBarColor = android.graphics.Color.rgb(23, 21, 38)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { FantasyDefense() } }
    }
}

@Composable
private fun FantasyDefense() {
    val game = remember { GameState() }
    LaunchedEffect(game) {
        var previous = withFrameNanos { it }
        while (isActive) withFrameNanos { now ->
            val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, .05f)
            previous = now
            game.tick(dt)
        }
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171526), Color(0xFF241C2D), Color(0xFF151B25)))).padding(horizontal = 9.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Crest("⚔")
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("ЗАЩИТА КОРОЛЕВСТВА", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("ВОЛНА ${game.wave}  •  ВРАГОВ УНИЧТОЖЕНО: ${game.score / 10}", color = Color(0xFFB7AFC2), fontSize = 9.sp)
            }
            Crest("Ⅱ")
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ResourcePill("♥", "${game.baseHp}/20", Color(0xFFFF777D), Modifier.weight(1f))
            ResourcePill("◉", "${game.coins}", Gold, Modifier.weight(1f))
            ResourcePill("✦", "${game.score}", Color(0xFF78D7FF), Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().weight(1f).border(2.dp, Color(0xFF9A7549), RoundedCornerShape(15.dp)).background(Color(0xFF263C32), RoundedCornerShape(15.dp)).padding(3.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawFantasyMap(game) }
            Text("ЛЕСНОЙ ПЕРЕВАЛ", Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xB51A1725), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 4.dp), color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("ЗАЩИТИТЕ ЗАМОК", Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color(0xB51A1725), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ОБОРОНА", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("Выбери башню и место", color = Color(0xFFB7AFC2), fontSize = 9.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TowerCard("🏹", "Лучник", "80", Color(0xFF6DBF75), Modifier.weight(1f)) { buyTower(game, 80, 14, .65f, 0) }
            TowerCard("🔥", "Маг огня", "130", Color(0xFFFF8A45), Modifier.weight(1f)) { buyTower(game, 130, 36, 1.25f, 1) }
            TowerCard("❄", "Лёд", "110", Color(0xFF67D9F5), Modifier.weight(1f)) { buyTower(game, 110, 12, .48f, 2) }
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("КРИСТАЛЛЫ МАНЫ", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text("Совмещай 3 и более", color = Color(0xFFB7AFC2), fontSize = 9.sp)
        }
        Column(Modifier.fillMaxWidth().border(2.dp, Color(0xFF876642), RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Color(0xFF403148), Color(0xFF211E31))), RoundedCornerShape(12.dp)).padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0..4) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0..6) {
                    val i = r * 7 + c
                    GemTile(game.gems[i], game.selected == i, Modifier.weight(1f).aspectRatio(1f)) { game.matchAt(i) }
                }
            }
        }
        if (game.gameOver) {
            Text("ЗАМОК ПАЛ", color = Color(0xFFFF777D), fontWeight = FontWeight.Black, fontSize = 20.sp)
            Button(onClick = { game.baseHp=20; game.coins=180; game.wave=1; game.score=0; game.enemies.clear(); game.towers.clear(); game.spawnTimer=0f; game.gameOver=false }) { Text("Начать заново") }
        }
    }
}

private fun buyTower(g: GameState, cost: Int, damage: Int, cooldown: Float, kind: Int) {
    if (g.coins >= cost && g.towers.size < 7) {
        g.coins -= cost
        g.towers.add(Tower(lane = 2 + g.towers.size, damage = damage, range = .26f, cooldown = cooldown, kind = kind))
    }
}

@Composable private fun Crest(symbol: String) {
    Box(Modifier.size(38.dp).border(2.dp, Gold, RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFF57402F), Color(0xFF251D2B))), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Text(symbol, color = Gold, fontSize = 21.sp, fontWeight = FontWeight.Black)
    }
}
@Composable private fun ResourcePill(icon: String, value: String, color: Color, modifier: Modifier) {
    Row(modifier.border(1.dp, Color(0xFF806448), RoundedCornerShape(9.dp)).background(Panel, RoundedCornerShape(9.dp)).padding(vertical=6.dp, horizontal=7.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.Center) {
        Text(icon, color=color, fontSize=15.sp); Spacer(Modifier.width(5.dp)); Text(value, color=Color.White, fontWeight=FontWeight.Bold, fontSize=12.sp)
    }
}
@Composable private fun TowerCard(icon: String, name: String, cost: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick=onClick).border(1.dp, tint.copy(alpha=.8f), RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFF4A3B42), Color(0xFF292435))), RoundedCornerShape(10.dp)).padding(vertical=5.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Text(icon, fontSize=22.sp); Text(name, color=Color.White, fontSize=10.sp, fontWeight=FontWeight.Bold); Text("◉ $cost", color=Gold, fontSize=10.sp, fontWeight=FontWeight.Bold)
    }
}
@Composable private fun GemTile(type: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val safe = type.coerceIn(0,4)
    Box(modifier.clickable(onClick=onClick).background(if(selected) Color(0xFF8B6B42) else Color(0xFF1D1C2D), RoundedCornerShape(7.dp)).border(if(selected) 2.dp else 1.dp, if(selected) Color.White else Color(0xFF6B557A), RoundedCornerShape(7.dp)).padding(2.dp), contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w=size.width; val h=size.height
            val p=Path().apply { moveTo(w*.5f,h*.06f); lineTo(w*.88f,h*.36f); lineTo(w*.76f,h*.83f); lineTo(w*.5f,h*.96f); lineTo(w*.22f,h*.83f); lineTo(w*.1f,h*.36f); close() }
            drawPath(p, Brush.linearGradient(listOf(GemColors[safe].copy(alpha=.65f), GemColors[safe], GemColors[safe].copy(alpha=.55f))))
            drawPath(Path().apply { moveTo(w*.5f,h*.06f); lineTo(w*.5f,h*.48f); lineTo(w*.1f,h*.36f); close() }, Color.White.copy(alpha=.27f))
            drawPath(Path().apply { moveTo(w*.5f,h*.48f); lineTo(w*.88f,h*.36f); lineTo(w*.76f,h*.83f); close() }, Color.Black.copy(alpha=.17f))
            drawLine(Color.White.copy(alpha=.65f), Offset(w*.24f,h*.3f), Offset(w*.42f,h*.17f), 1.5f)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFantasyMap(game: GameState) {
    val w=size.width; val h=size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF53734B), Color(0xFF243E36))), size=Size(w,h))
    // layered isometric hills and forest canopy
    for (i in 0..8) {
        val x=w*(i/8f)
        drawOval(Color(0xFF71834B).copy(alpha=.45f), Offset(x-w*.10f,h*.05f+(i%3)*h*.07f), Size(w*.24f,h*.34f))
        drawCircle(Color(0xFF1D483A), w*.045f, Offset(x,h*.12f+(i%3)*h*.06f))
        drawCircle(Color(0xFF386748), w*.033f, Offset(x-w*.018f,h*.10f+(i%3)*h*.06f))
    }
    // river ribbon
    val river=Path().apply { moveTo(0f,h*.78f); cubicTo(w*.2f,h*.63f,w*.23f,h*.96f,w*.43f,h*.79f); cubicTo(w*.65f,h*.58f,w*.72f,h*.92f,w,h*.68f) }
    drawPath(river, Color(0xFF168C9C), style=androidx.compose.ui.graphics.drawscope.Stroke(width=h*.11f, cap=StrokeCap.Round))
    drawPath(river, Color(0xFF58D5D6), style=androidx.compose.ui.graphics.drawscope.Stroke(width=h*.055f, cap=StrokeCap.Round))
    // winding cobblestone road
    val road=Path().apply { moveTo(-w*.04f,h*.25f); cubicTo(w*.25f,h*.12f,w*.22f,h*.55f,w*.47f,h*.45f); cubicTo(w*.69f,h*.32f,w*.62f,h*.75f,w*1.03f,h*.55f) }
    drawPath(road, Color(0xFF493A32), style=androidx.compose.ui.graphics.drawscope.Stroke(width=h*.18f, cap=StrokeCap.Round))
    drawPath(road, Color(0xFFB49A72), style=androidx.compose.ui.graphics.drawscope.Stroke(width=h*.145f, cap=StrokeCap.Round))
    drawPath(road, Color(0xFFD4BC8E), style=androidx.compose.ui.graphics.drawscope.Stroke(width=h*.012f, cap=StrokeCap.Round))
    // tower pads, isometric diamonds
    val pads=listOf(.18f to .34f,.36f to .28f,.48f to .64f,.64f to .34f,.78f to .68f,.86f to .28f,.28f to .72f)
    pads.forEachIndexed { i,p ->
        val x=w*p.first; val y=h*p.second
        val pad=Path().apply { moveTo(x,y-h*.045f); lineTo(x+w*.055f,y); lineTo(x,y+h*.045f); lineTo(x-w*.055f,y); close() }
        drawOval(Color(0xFF18271F).copy(alpha=.5f), Offset(x-w*.06f,y-h*.02f), Size(w*.12f,h*.055f))
        drawPath(pad, Color(0xFF8A785A)); drawPath(pad, Color(0xFFC5A56D), style=androidx.compose.ui.graphics.drawscope.Stroke(width=2f))
        drawCircle(Color(0xFFEFCC7B).copy(alpha=.7f), 3.5f, Offset(x,y))
        game.towers.getOrNull(i)?.let { tower ->
            val color=when(tower.kind){0->Color(0xFF7DBD66);1->Color(0xFFFF8548);else->Color(0xFF69DDF4)}
            drawOval(Color.Black.copy(alpha=.45f), Offset(x-w*.035f,y-h*.015f), Size(w*.07f,h*.035f))
            drawRoundRect(Color(0xFF514438), Offset(x-w*.026f,y-h*.09f), Size(w*.052f,h*.09f), androidx.compose.ui.geometry.CornerRadius(8f))
            drawCircle(color, w*.027f, Offset(x,y-h*.09f)); drawCircle(Color.White.copy(alpha=.65f), w*.009f, Offset(x-w*.008f,y-h*.1f))
        }
    }
    // castle at the destination
    val cx=w*.91f; val cy=h*.26f
    drawRoundRect(Color(0xFF5C5A63), Offset(cx-w*.065f,cy-h*.12f), Size(w*.13f,h*.17f), androidx.compose.ui.geometry.CornerRadius(8f))
    drawRoundRect(Color(0xFFB6A28A), Offset(cx-w*.052f,cy-h*.10f), Size(w*.104f,h*.15f), androidx.compose.ui.geometry.CornerRadius(6f))
    for (dx in listOf(-.055f,0f,.055f)) {
        val tx=cx+w*dx
        drawRect(Color(0xFF6E8FC0), Offset(tx-w*.014f,cy-h*.19f), Size(w*.028f,h*.12f))
        val roof=Path().apply { moveTo(tx-w*.022f,cy-h*.19f); lineTo(tx,cy-h*.25f); lineTo(tx+w*.022f,cy-h*.19f); close() }
        drawPath(roof, Color(0xFF3475B8))
    }
    drawRoundRect(Color(0xFF342B2D), Offset(cx-w*.012f,cy-h*.035f), Size(w*.024f,h*.085f), androidx.compose.ui.geometry.CornerRadius(12f))
    drawRect(Color(0xFFFFD27A), Offset(cx-w*.004f,cy-h*.025f), Size(w*.008f,h*.018f))
    // monsters following the path
    game.enemies.forEach { e ->
        val t=e.progress.coerceIn(0f,1f)
        val x=w*(.02f+.96f*t)
        val y=when { t<.27f -> h*(.25f-.12f*t); t<.55f -> h*(.22f+.23f*(t-.27f)); t<.78f -> h*(.285f-.35f*(t-.55f)); else -> h*(.205f+.22f*(t-.78f)) }
        val body=when(e.kind){0->Color(0xFF75A94B);1->Color(0xFF9B6547);2->Color(0xFF697E8D);else->Color(0xFF8D4D9D)}
        drawOval(Color.Black.copy(alpha=.45f), Offset(x-w*.018f,y+h*.014f), Size(w*.036f,h*.022f))
        drawCircle(body,w*.019f,Offset(x,y))
        drawCircle(body.copy(red=(body.red*.72f).coerceIn(0f,1f)),w*.011f,Offset(x-w*.012f,y-h*.014f))
        drawCircle(Color(0xFFFFE5A0),w*.0035f,Offset(x-w*.017f,y-h*.019f))
        drawCircle(Color(0xFFFFE5A0),w*.0035f,Offset(x-w*.007f,y-h*.019f))
        drawRoundRect(Color(0xFF3B2028),Offset(x-w*.022f,y-h*.04f),Size(w*.044f,h*.008f),androidx.compose.ui.geometry.CornerRadius(4f))
        drawRoundRect(Color(0xFFE75D58),Offset(x-w*.022f,y-h*.04f),Size(w*.044f*(e.hp.toFloat()/(32+game.wave*7)).coerceIn(0f,1f),h*.008f),androidx.compose.ui.geometry.CornerRadius(4f))
    }
    // magical glows and firefly points
    for (i in 0..10) {
        val x=w*((i*37%97)/100f); val y=h*((i*23%83)/100f)
        drawCircle(Color(0xFFFFE8A0).copy(alpha=.55f),2.2f,Offset(x,y))
    }
}
