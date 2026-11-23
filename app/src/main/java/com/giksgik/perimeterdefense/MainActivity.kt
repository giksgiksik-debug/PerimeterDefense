package com.giksgik.perimeterdefense

import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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

private fun loadArt(context: android.content.Context, path: String): ImageBitmap? = runCatching {
    context.assets.open("game_art/$path").use { BitmapFactory.decodeStream(it).asImageBitmap() }
}.getOrNull()

@Composable private fun GameScreen() {
    val g= remember { GameState() }
    val context = LocalContext.current
    val art = remember {
        mapOf(
            "background" to loadArt(context, "background/forest_pass_background.webp"),
            "road" to loadArt(context, "layers/road_overlay.png"),
            "bridge" to loadArt(context, "layers/stone_bridge.png"),
            "pad1" to loadArt(context, "layers/build_pad_1.png"),
            "pad2" to loadArt(context, "layers/build_pad_2.png"),
            "pad3" to loadArt(context, "layers/build_pad_3.png"),
            "pad4" to loadArt(context, "layers/build_pad_4.png"),
            "pad5" to loadArt(context, "layers/build_pad_5.png"),
            "pad6" to loadArt(context, "layers/build_pad_6.png"),
            "castle" to loadArt(context, "sprites/structures/castle_blue.png"),
            "archer" to loadArt(context, "sprites/structures/tower_archer.png"),
            "fire" to loadArt(context, "sprites/structures/tower_fire.png"),
            "ice" to loadArt(context, "sprites/structures/tower_ice.png"),
            "lightning" to loadArt(context, "sprites/structures/tower_lightning.png"),
            "goblin" to loadArt(context, "sprites/structures/enemy_goblin.png"),
            "brute" to loadArt(context, "sprites/structures/enemy_brute.png")
        )
    }
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
            Canvas(Modifier.fillMaxSize()) { drawBattlefield(g, art) }
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
    Column(Modifier.height(47.dp).fillMaxWidth().clickable { g.selectedTower=id }.background(if(chosen) tint.copy(alpha=.20f) else Color(0xFF252D40),RoundedCornerShape(7.dp)).border(if(chosen) 2.dp else 1.dp,if(chosen) tint else Color(0xFF536078),RoundedCornerShape(7.dp)).padding(3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
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
    val context=LocalContext.current
    val names=listOf("blue.png","red.png","purple.png","green.png","yellow.png")
    val image= remember(type) {
        runCatching { context.assets.open("game_art/sprites/gems/${names[type.coerceIn(0,4)]}").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
    }
    Box(modifier.clickable { onClick() }.background(Brush.verticalGradient(listOf(Color(0xFF46516A),Color(0xFF171D2C))),RoundedCornerShape(7.dp)).border(if(selected) 2.dp else 1.dp,if(selected) Color.White else Color(0xFF66738D),RoundedCornerShape(7.dp)).padding(2.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            image?.let { drawImage(it, dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt())) }
                ?: drawCircle(Gems[type.coerceIn(0,4)],size.minDimension*.34f)
            if(selected) drawCircle(Color.White.copy(alpha=.85f),size.minDimension*.46f,style=Stroke(width=3.dp.toPx()))
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBattlefield(g:GameState, art:Map<String,ImageBitmap?>) {
    val w=size.width; val h=size.height
    fun sprite(key:String, x:Float, y:Float, width:Float, height:Float) {
        art[key]?.let { drawImage(it, dstOffset=androidx.compose.ui.unit.IntOffset(x.toInt(),y.toInt()), dstSize=androidx.compose.ui.unit.IntSize(width.toInt(),height.toInt())) }
    }
    // 1. Painted forest background
    art["background"]?.let { drawImage(it, dstSize=androidx.compose.ui.unit.IntSize(w.toInt(),h.toInt())) }
        ?: drawRect(Color(0xFF315D45), size=Size(w,h))
    // 2. Separate road and bridge overlays
    sprite("road",0f,0f,w,h)
    sprite("bridge",0f,0f,w,h)
    // 3. Build pads and placed tower sprites
    val pads=listOf(.16f to .33f,.29f to .60f,.43f to .23f,.53f to .57f,.70f to .30f,.79f to .62f)
    pads.forEachIndexed { i,p ->
        val x=w*p.first; val y=h*p.second
        sprite("pad${i+1}",x-w*.055f,y-h*.075f,w*.11f,h*.15f)
        if(i<g.towers.size) {
            val key=when(g.towers[i].kind){0->"archer";1->"fire";2->"ice";else->"lightning"}
            sprite(key,x-w*.055f,y-h*.23f,w*.11f,h*.24f)
        }
    }
    // 4. Castle destination
    sprite("castle",w*.835f,h*.12f,w*.16f,h*.48f)
    // 5. Moving enemy sprites
    g.enemies.forEach { e ->
        val t=e.progress.coerceIn(0f,1f)
        val x=w*(.06f+.86f*t)
        val y=when {t<.22f->h*(.50f-.20f*t);t<.42f->h*(.456f+.60f*(t-.22f));t<.65f->h*(.576f-.95f*(t-.42f));t<.83f->h*(.357f+.15f*(t-.65f));else->h*(.384f-.15f*(t-.83f))}
        val key=if(e.kind%3==0) "brute" else "goblin"
        sprite(key,x-w*.035f,y-h*.13f,w*.07f,h*.15f)
        drawRoundRect(Color(0xFF351E28),Offset(x-w*.025f,y-h*.15f),Size(w*.05f,h*.009f),androidx.compose.ui.geometry.CornerRadius(h*.005f))
        drawRoundRect(Color(0xFFE34F57),Offset(x-w*.025f,y-h*.15f),Size(w*.05f*(e.hp.toFloat()/(35+g.wave*8)).coerceIn(0f,1f),h*.009f),androidx.compose.ui.geometry.CornerRadius(h*.005f))
    }
}
