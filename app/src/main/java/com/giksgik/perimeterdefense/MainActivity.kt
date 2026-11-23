package com.giksgik.perimeterdefense

import android.content.pm.ActivityInfo
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.random.Random

private val Gold = Color(0xFFFFD27A)
private val Panel = Color(0xFF191D2B)
private val Gems = listOf(Color(0xFF36BDF4), Color(0xFFF04F54), Color(0xFFB56AF2), Color(0xFF38C982), Color(0xFFF5B938))

private data class Enemy(var progress: Float, var hp: Int, val speed: Float, val kind: Int)
private data class Tower(val damage: Int, val range: Float, val cooldown: Float, val kind: Int, var timer: Float = 0f)
private class GameState {
    var hp by mutableIntStateOf(20)
    var coins by mutableIntStateOf(180)
    var wave by mutableIntStateOf(1)
    var score by mutableIntStateOf(0)
    var selectedTower by mutableIntStateOf(0)
    var selectedGem by mutableIntStateOf(-1)
    var paused by mutableStateOf(false)
    var over by mutableStateOf(false)
    var gems = mutableStateListOf<Int>().apply { repeat(35) { add(Random.nextInt(0,5)) } }
    val enemies = mutableStateListOf<Enemy>()
    val towers = mutableStateListOf<Tower>()
    var spawn = 0f
    fun tick(dt: Float) {
        if (paused || over) return
        spawn += dt
        if (spawn > max(1.3f, 3.2f-wave*.09f)) {
            spawn=0f
            enemies.add(Enemy(0f, 35+wave*8, .055f+wave*.001f, (wave+enemies.size)%5))
            if (score/150+1>wave) wave++
        }
        val leaked= mutableListOf<Enemy>()
        enemies.forEach { e -> e.progress += e.speed*dt; if(e.progress>=1f) leaked.add(e) }
        enemies.removeAll(leaked.toSet())
        if(leaked.isNotEmpty()) hp=max(0,hp-leaked.size*2)
        towers.forEach { t ->
            t.timer-=dt
            if(t.timer<=0f) {
                val target=enemies.filter { kotlin.math.abs(it.progress-.5f)<t.range }.maxByOrNull { it.progress }
                if(target!=null) { target.hp-=t.damage; t.timer=t.cooldown }
            }
        }
        val dead=enemies.filter { it.hp<=0 }
        if(dead.isNotEmpty()) { score+=dead.size*10; coins+=dead.size*5 }
        enemies.removeAll { it.hp<=0 }
        if(hp<=0) over=true
    }
    fun match(index:Int) {
        if(selectedGem<0) { selectedGem=index; return }
        val a=selectedGem; val b=index; selectedGem=-1
        if(a==b || kotlin.math.abs(a/7-b/7)+kotlin.math.abs(a%7-b%7)!=1) return
        val t=gems[a]; gems[a]=gems[b]; gems[b]=t
        val marked=linkedSetOf<Int>()
        for(i in gems.indices) {
            val r=i/7; val c=i%7; val v=gems[i]
            var left=c; while(left>0 && gems[r*7+left-1]==v) left--
            var right=c; while(right<6 && gems[r*7+right+1]==v) right++
            if(right-left>=2) for(x in left..right) marked.add(r*7+x)
            var top=r; while(top>0 && gems[(top-1)*7+c]==v) top--
            var bottom=r; while(bottom<4 && gems[(bottom+1)*7+c]==v) bottom++
            if(bottom-top>=2) for(y in top..bottom) marked.add(y*7+c)
        }
        if(marked.size<3) { gems[a]=gems[b]; gems[b]=t; return }
        coins+=marked.size*12; score+=marked.size*10
        marked.forEach { gems[it]=-1 }
        for(c in 0..6) {
            val keep=(0..4).map { gems[it*7+c] }.filter { it>=0 }
            val fill=List(5-keep.size) { Random.nextInt(0,5) }
            (0..4).forEach { r -> gems[r*7+c]=(fill+keep)[r] }
        }
    }
}

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.statusBarColor=android.graphics.Color.rgb(17,22,34)
        window.navigationBarColor=android.graphics.Color.rgb(17,22,34)
        setContent { MaterialTheme(colorScheme=darkColorScheme()) { GameScreen() } }
    }
}

@Composable private fun GameScreen() {
    val g= remember { GameState() }
    LaunchedEffect(g) {
        var last=withFrameNanos { it }
        while(isActive) withFrameNanos { now ->
            val dt=((now-last)/1_000_000_000f).coerceIn(0f,.05f); last=now; g.tick(dt)
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF111624)).padding(horizontal=7.dp, vertical=5.dp)) {
        Header(g)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().weight(1.12f).border(2.dp,Color(0xFF9A774A),RoundedCornerShape(12.dp)).background(Color(0xFF253D36),RoundedCornerShape(12.dp))) {
            Canvas(Modifier.fillMaxSize()) { drawBattlefield(g) }
            Row(Modifier.align(Alignment.TopStart).padding(7.dp), verticalAlignment=Alignment.CenterVertically) {
                SmallTag("ЛЕСНОЙ ПЕРЕВАЛ")
                Spacer(Modifier.width(6.dp)); SmallTag("ВОЛНА ${g.wave}/15")
            }
            if(g.paused) Box(Modifier.fillMaxSize().background(Color(0x990B1020)),contentAlignment=Alignment.Center) { Text("ПАУЗА",color=Gold,fontSize=30.sp,fontWeight=FontWeight.Black) }
            if(g.over) Box(Modifier.fillMaxSize().background(Color(0xBB0B1020)),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("ЗАМОК ПАЛ",color=Color(0xFFFF7777),fontSize=28.sp,fontWeight=FontWeight.Black)
                    Button(onClick={g.hp=20;g.coins=180;g.wave=1;g.score=0;g.enemies.clear();g.towers.clear();g.over=false;g.spawn=0f}) { Text("СНОВА В БОЙ") }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().weight(.92f), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            TowerShop(g,Modifier.weight(.235f).fillMaxHeight())
            GemBoard(g,Modifier.weight(.53f).fillMaxHeight())
            BattleInfo(g,Modifier.weight(.235f).fillMaxHeight())
        }
    }
}

@Composable private fun Header(g:GameState) {
    Row(Modifier.fillMaxWidth().height(42.dp).background(Color(0xFF1B2232),RoundedCornerShape(9.dp)).border(1.dp,Color(0xFF8E704B),RoundedCornerShape(9.dp)).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("⚜",color=Gold,fontSize=26.sp)
        Column(Modifier.weight(1f).padding(start=7.dp)) {
            Text("PERIMETER DEFENSE",color=Gold,fontSize=16.sp,fontWeight=FontWeight.Black,letterSpacing=1.sp)
            Text("ЗАЩИТА КОРОЛЕВСТВА  •  ЛЕСНОЙ ПЕРЕВАЛ",color=Color(0xFFB9C1D0),fontSize=8.sp,fontWeight=FontWeight.Bold)
        }
        Stat("⚔","ВОЛНА ${g.wave}/15")
        Spacer(Modifier.width(5.dp)); Stat("☠","${g.enemies.size} ВРАГОВ")
        Spacer(Modifier.width(5.dp)); Stat("♥","${g.hp}/20")
        Spacer(Modifier.width(5.dp)); Stat("✦","${g.coins}")
        Spacer(Modifier.width(5.dp))
        Box(Modifier.size(34.dp).background(Color(0xFF5C4229),RoundedCornerShape(8.dp)).border(1.dp,Gold,RoundedCornerShape(8.dp)).clickable { g.paused=!g.paused },contentAlignment=Alignment.Center) {
            Text(if(g.paused) "▶" else "Ⅱ",color=Gold,fontSize=18.sp,fontWeight=FontWeight.Black)
        }
    }
}
@Composable private fun Stat(icon:String,value:String) {
    Row(Modifier.background(Color(0xFF101827),RoundedCornerShape(7.dp)).border(1.dp,Color(0xFF526078),RoundedCornerShape(7.dp)).padding(horizontal=8.dp, vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(icon,color=Gold,fontSize=14.sp); Spacer(Modifier.width(5.dp)); Text(value,color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Bold)
    }
}
@Composable private fun SmallTag(s:String) {
    Text(s,Modifier.background(Color(0xD9161C2B),RoundedCornerShape(5.dp)).border(1.dp,Gold.copy(alpha=.65f),RoundedCornerShape(5.dp)).padding(horizontal=7.dp,vertical=4.dp),color=Gold,fontSize=8.sp,fontWeight=FontWeight.Black)
}
@Composable private fun TowerShop(g:GameState,modifier:Modifier) {
    Column(modifier.background(Color(0xE7192030),RoundedCornerShape(10.dp)).border(1.dp,Color(0xFF8B704F),RoundedCornerShape(10.dp)).padding(5.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text("БАШНИ ОБОРОНЫ",color=Gold,fontSize=10.sp,fontWeight=FontWeight.Black)
        TowerChoice(g,0,"🏹","Лучник","80",Color(0xFF63BA70))
        TowerChoice(g,1,"🔥","Маг огня","130",Color(0xFFFF874C))
        TowerChoice(g,2,"❄","Ледяная","110",Color(0xFF66D8F5))
        TowerChoice(g,3,"⚡","Молния","160",Color(0xFFB98AFF))
    }
}
@Composable private fun TowerChoice(g:GameState,id:Int,icon:String,name:String,cost:String,tint:Color) {
    val chosen=g.selectedTower==id
    Column(Modifier.weight(1f).fillMaxWidth().clickable { g.selectedTower=id }.background(if(chosen) tint.copy(alpha=.20f) else Color(0xFF252D40),RoundedCornerShape(7.dp)).border(if(chosen) 2.dp else 1.dp,if(chosen) tint else Color(0xFF536078),RoundedCornerShape(7.dp)).padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Text(icon,fontSize=19.sp); Text(name,color=Color.White,fontSize=9.sp,fontWeight=FontWeight.Bold); Text("◉ $cost",color=Gold,fontSize=9.sp,fontWeight=FontWeight.Black)
    }
}
@Composable private fun GemBoard(g:GameState,modifier:Modifier) {
    Column(modifier.background(Color(0xF0171D2C),RoundedCornerShape(10.dp)).border(1.dp,Color(0xFF96744C),RoundedCornerShape(10.dp)).padding(5.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("КРИСТАЛЛЫ МАНЫ",color=Gold,fontSize=10.sp,fontWeight=FontWeight.Black)
            Spacer(Modifier.weight(1f)); Text("СОЕДИНЯЙ 3+",color=Color(0xFFB8C4D7),fontSize=8.sp)
        }
        Spacer(Modifier.height(4.dp))
        for(r in 0..4) Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            for(c in 0..6) {
                val i=r*7+c
                Gem(g.gems[i],g.selectedGem==i,Modifier.weight(1f).fillMaxHeight()) { g.match(i) }
            }
        }
    }
}
@Composable private fun Gem(type:Int,selected:Boolean,modifier:Modifier,onClick:()->Unit) {
    val safe=type.coerceIn(0,4); val color=Gems[safe]
    Box(modifier.clickable { onClick() }.background(Brush.verticalGradient(listOf(Color(0xFF46516A),Color(0xFF171D2C))),RoundedCornerShape(7.dp)).border(if(selected) 2.dp else 1.dp,if(selected) Color.White else Color(0xFF66738D),RoundedCornerShape(7.dp)).padding(2.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w=size.width; val h=size.height
            val p=Path().apply { moveTo(w*.5f,h*.06f);lineTo(w*.91f,h*.35f);lineTo(w*.78f,h*.86f);lineTo(w*.5f,h*.97f);lineTo(w*.22f,h*.86f);lineTo(w*.09f,h*.35f);close() }
            drawPath(p,Brush.linearGradient(listOf(color.copy(alpha=.72f),color,color.copy(alpha=.65f))))
            drawPath(Path().apply { moveTo(w*.5f,h*.06f);lineTo(w*.5f,h*.49f);lineTo(w*.09f,h*.35f);close() },Color.White.copy(alpha=.3f))
            drawPath(Path().apply { moveTo(w*.5f,h*.49f);lineTo(w*.91f,h*.35f);lineTo(w*.78f,h*.86f);close() },Color.Black.copy(alpha=.18f))
            drawLine(Color.White.copy(alpha=.7f),Offset(w*.22f,h*.29f),Offset(w*.42f,h*.16f),2f)
            drawCircle(color.copy(alpha=.3f),w*.12f,Offset(w*.5f,h*.52f))
        }
    }
}
@Composable private fun BattleInfo(g:GameState,modifier:Modifier) {
    Column(modifier.background(Color(0xE7192030),RoundedCornerShape(10.dp)).border(1.dp,Color(0xFF8B704F),RoundedCornerShape(10.dp)).padding(7.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("БОЕВАЯ СВОДКА",color=Gold,fontSize=10.sp,fontWeight=FontWeight.Black)
        Text("✦  ${g.score}",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Black)
        Text("ОЧКИ",color=Color(0xFFABB6C8),fontSize=8.sp)
        Divider(color=Color(0xFF46516A))
        Text("ЗДОРОВЬЕ ЗАМКА",color=Color(0xFFB8C4D7),fontSize=8.sp,fontWeight=FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF4A2631),RoundedCornerShape(8.dp))) {
            Box(Modifier.fillMaxWidth(g.hp/20f).fillMaxHeight().background(Color(0xFFE84D58),RoundedCornerShape(8.dp)))
        }
        Text("${g.hp}/20",color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Black)
        Spacer(Modifier.weight(1f))
        Text("ЦЕЛЬ",color=Gold,fontSize=9.sp,fontWeight=FontWeight.Black)
        Text("Отразить 15 волн и не дать врагам пройти к замку.",color=Color.White,fontSize=10.sp,lineHeight=13.sp)
        Text("Выбрана: ${listOf("Лучник","Маг огня","Лёд","Молния")[g.selectedTower]}",color=Color(0xFFB7C6D9),fontSize=9.sp)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBattlefield(g:GameState) {
    val w=size.width; val h=size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF9CCB8A),Color(0xFF4E8555),Color(0xFF234C43))),size=Size(w,h))
    // distant cliffs, mist and forest canopy
    drawOval(Color(0xFFB5D4A0).copy(alpha=.35f),Offset(w*.22f,-h*.18f),Size(w*.58f,h*.62f))
    for(i in 0..15) {
        val x=w*(i/15f); val y=h*(.05f+(i%4)*.055f)
        drawCircle(Color(0xFF173E37),h*.105f,Offset(x,y))
        drawCircle(Color(0xFF286348),h*.083f,Offset(x-h*.018f,y-h*.02f))
        drawCircle(Color(0xFF477E4A),h*.055f,Offset(x-h*.03f,y-h*.035f))
        drawCircle(Color(0xFF8EBB5C).copy(alpha=.65f),h*.018f,Offset(x-h*.04f,y-h*.05f))
    }
    // river and falls
    val river=Path().apply { moveTo(0f,h*.84f);cubicTo(w*.18f,h*.68f,w*.23f,h*.98f,w*.38f,h*.82f);cubicTo(w*.56f,h*.64f,w*.68f,h*.97f,w*.79f,h*.80f);lineTo(w,h*.72f) }
    drawPath(river,Color(0xFF176F86),style=Stroke(h*.13f,cap=StrokeCap.Round))
    drawPath(river,Color(0xFF59C9D8),style=Stroke(h*.075f,cap=StrokeCap.Round))
    drawPath(river,Color(0xFFB0F4E9).copy(alpha=.65f),style=Stroke(h*.012f,cap=StrokeCap.Round))
    // winding stone road with dark edge, cobble seams
    val road=Path().apply { moveTo(-w*.02f,h*.50f);cubicTo(w*.13f,h*.30f,w*.27f,h*.30f,w*.36f,h*.49f);cubicTo(w*.47f,h*.74f,w*.58f,h*.65f,w*.61f,h*.42f);cubicTo(w*.64f,h*.18f,w*.77f,h*.22f,w*.84f,h*.43f);cubicTo(w*.9f,h*.62f,w*.94f,h*.56f,w*1.02f,h*.38f) }
    drawPath(road,Color(0xFF423C3B),style=Stroke(h*.205f,cap=StrokeCap.Round,join=PathEffect.cornerPathEffect(h*.05f)))
    drawPath(road,Brush.linearGradient(listOf(Color(0xFFD9C39A),Color(0xFF9F896A),Color(0xFFE0CDA7))),style=Stroke(h*.17f,cap=StrokeCap.Round))
    drawPath(road,Color(0xFF75644F).copy(alpha=.6f),style=Stroke(h*.006f))
    // stone bridge
    drawRoundRect(Color(0xFF665A4A),Offset(w*.34f,h*.69f),Size(w*.25f,h*.14f),androidx.compose.ui.geometry.CornerRadius(h*.04f))
    drawRoundRect(Color(0xFFC3AC83),Offset(w*.35f,h*.68f),Size(w*.23f,h*.10f),androidx.compose.ui.geometry.CornerRadius(h*.035f))
    for(i in 0..7) drawLine(Color(0xFF806F59),Offset(w*(.36f+i*.027f),h*.70f),Offset(w*(.36f+i*.027f),h*.77f),h*.004f)
    // build pads
    val pads=listOf(.16f to .33f,.29f to .60f,.43f to .23f,.53f to .57f,.70f to .30f,.79f to .62f)
    pads.forEachIndexed { i,p ->
        val x=w*p.first; val y=h*p.second
        drawOval(Color(0xFF102B25).copy(alpha=.45f),Offset(x-w*.045f,y-h*.018f),Size(w*.09f,h*.045f))
        val pad=Path().apply { moveTo(x,y-h*.055f);lineTo(x+w*.052f,y);lineTo(x,y+h*.055f);lineTo(x-w*.052f,y);close() }
        drawPath(pad,Color(0xFF8E7959));drawPath(pad,Color(0xFFE5C78A),style=Stroke(h*.008f))
        drawCircle(Color(0xFFFFD77D).copy(alpha=.75f),h*.009f,Offset(x,y))
        if(i<g.towers.size) {
            val col=when(g.towers[i].kind){0->Color(0xFF5FAE58);1->Color(0xFFFF8143);2->Color(0xFF5DD5F4);else->Color(0xFFB58AFB)}
            drawRoundRect(Color(0xFF5C4938),Offset(x-w*.025f,y-h*.12f),Size(w*.05f,h*.12f),androidx.compose.ui.geometry.CornerRadius(h*.015f))
            drawCircle(col,h*.042f,Offset(x,y-h*.12f));drawCircle(Color.White.copy(alpha=.7f),h*.012f,Offset(x-h*.01f,y-h*.13f))
        } else {
            drawCircle(Color(0xFFFFD77D).copy(alpha=.45f),h*.018f,Offset(x,y))
        }
    }
    // enemy gate at left
    val gx=w*.055f; val gy=h*.47f
    drawRoundRect(Color(0xFF3E3336),Offset(gx-w*.05f,gy-h*.20f),Size(w*.10f,h*.35f),androidx.compose.ui.geometry.CornerRadius(h*.04f))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFF6A2D),Color(0xFF6E1027),Color(0xFF260D22))),Offset(gx-w*.035f,gy-h*.15f),Size(w*.07f,h*.27f),androidx.compose.ui.geometry.CornerRadius(h*.06f))
    drawRoundRect(Color(0xFFFF9B43).copy(alpha=.7f),Offset(gx-w*.035f,gy-h*.15f),Size(w*.07f,h*.27f),androidx.compose.ui.geometry.CornerRadius(h*.06f),style=Stroke(h*.009f))
    for(i in 0..2) drawCircle(Color(0xFFFFD36B),h*(.012f-i*.002f),Offset(gx-w*.026f+i*w*.026f,gy-h*.18f))
    // destination castle right
    val cx=w*.93f; val cy=h*.39f
    drawRoundRect(Color(0xFF655D59),Offset(cx-w*.075f,cy-h*.20f),Size(w*.15f,h*.31f),androidx.compose.ui.geometry.CornerRadius(h*.025f))
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFFE6D4B4),Color(0xFF9B8A78))),Offset(cx-w*.065f,cy-h*.18f),Size(w*.13f,h*.27f),androidx.compose.ui.geometry.CornerRadius(h*.02f))
    for(dx in listOf(-.06f,0f,.06f)) {
        val tx=cx+w*dx
        drawRect(Color(0xFFD9C7A7),Offset(tx-w*.021f,cy-h*.31f),Size(w*.042f,h*.18f))
        drawRect(Color(0xFF3472B8),Offset(tx-w*.023f,cy-h*.32f),Size(w*.046f,h*.055f))
        val roof=Path().apply { moveTo(tx-w*.03f,cy-h*.32f);lineTo(tx,cy-h*.43f);lineTo(tx+w*.03f,cy-h*.32f);close() }
        drawPath(roof,Color(0xFF1F5EA5))
        drawLine(Gold,Offset(tx,cy-h*.43f),Offset(tx,cy-h*.48f),h*.005f)
    }
    drawRoundRect(Color(0xFF352B2C),Offset(cx-w*.015f,cy-h*.04f),Size(w*.03f,h*.13f),androidx.compose.ui.geometry.CornerRadius(h*.02f))
    // monsters move along the route, with shadow and health
    g.enemies.forEach { e ->
        val t=e.progress.coerceIn(0f,1f)
        val x=w*(.06f+.86f*t)
        val y=when {t<.22f->h*(.50f-.20f*t);t<.42f->h*(.456f+.60f*(t-.22f));t<.65f->h*(.576f-.95f*(t-.42f));t<.83f->h*(.357f+.15f*(t-.65f));else->h*(.384f-.15f*(t-.83f))}
        val c=when(e.kind){0->Color(0xFF77B84F);1->Color(0xFF9D6749);2->Color(0xFFCBD2D5);3->Color(0xFF7553A8);else->Color(0xFF8E3945)}
        drawOval(Color.Black.copy(alpha=.5f),Offset(x-w*.017f,y+h*.018f),Size(w*.034f,h*.025f))
        drawRoundRect(c,Offset(x-w*.013f,y-h*.025f),Size(w*.026f,h*.055f),androidx.compose.ui.geometry.CornerRadius(h*.012f))
        drawCircle(c,h*.021f,Offset(x,y-h*.03f))
        drawCircle(Color(0xFFFFE9A4),h*.004f,Offset(x-w*.007f,y-h*.034f));drawCircle(Color(0xFFFFE9A4),h*.004f,Offset(x+w*.007f,y-h*.034f))
        drawRoundRect(Color(0xFF351E28),Offset(x-w*.021f,y-h*.07f),Size(w*.042f,h*.009f),androidx.compose.ui.geometry.CornerRadius(h*.005f))
        drawRoundRect(Color(0xFFE34F57),Offset(x-w*.021f,y-h*.07f),Size(w*.042f*(e.hp.toFloat()/(35+g.wave*8)).coerceIn(0f,1f),h*.009f),androidx.compose.ui.geometry.CornerRadius(h*.005f))
    }
    // foreground grass sparkles
    for(i in 0..28) {
        val x=w*((i*31%97)/100f); val y=h*(.72f+(i*17%24)/100f)
        drawCircle(if(i%3==0) Color(0xFFFFE9A3) else Color(0xFFB4E5A2).copy(alpha=.7f),h*.003f,Offset(x,y))
    }
}
