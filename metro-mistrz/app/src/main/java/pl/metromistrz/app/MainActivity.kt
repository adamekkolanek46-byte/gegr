package pl.metromistrz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.*

private val Ink = Color(0xFF101824)
private val Navy = Color(0xFF18263B)
private val Slate = Color(0xFF617186)
private val Map = Color(0xFFE5EDF1)
private val Water = Color(0xFF8BCBE2)
private val Green = Color(0xFFB5D5B1)
private val Track = Color(0xFF25374B)
private val MetroRed = Color(0xFFE84C52)
private val MetroBlue = Color(0xFF296EE8)
private val MetroYellow = Color(0xFFF2B93B)
private val Good = Color(0xFF1FA16C)
private val Bad = Color(0xFFD83C45)

data class Point(val x: Float, val y: Float)
data class City(val name: String, val subtitle: String, val demand: Int, val scaleKm: Float)

private val cities = listOf(
    City("Warszawa", "gęsta zabudowa · duży popyt", 94, .083f),
    City("Kraków", "stare miasto · duży ruch", 83, .091f),
    City("Wrocław", "rzeka i mosty · średni popyt", 76, .096f),
    City("Gdańsk", "układ liniowy · umiarkowany popyt", 67, .105f),
    City("Własne miasto", "zaprojektuj od zera", 60, .10f)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MetroMistrzTheme { MetroGame() } }
    }
}

@Composable private fun MetroMistrzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = MetroRed, background = Map, surface = Color.White), content = content)
}

@Composable private fun MetroGame() {
    var city by remember { mutableStateOf(cities.first()) }
    var stations by remember { mutableStateOf(listOf<Point>()) }
    var lineName by remember { mutableStateOf("M1") }
    var lineColor by remember { mutableStateOf(MetroRed) }
    var building by remember { mutableStateOf(true) }
    var simulation by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Dotknij mapy, aby postawić pierwszą stację.") }
    var showRules by remember { mutableStateOf(false) }

    val report = routeReport(stations, city.scaleKm)
    val canSimulate = stations.size >= 2 && report.blockers.isEmpty()

    fun changeCity(newCity: City) {
        city = newCity; stations = emptyList(); simulation = false
        message = "Nowy obszar: ${newCity.name}. Zacznij od pierwszej stacji."
    }

    fun addStation(point: Point) {
        if (!building) return
        val proposal = stations + point
        val test = routeReport(proposal, city.scaleKm)
        if (test.lastError != null) {
            message = test.lastError
        } else {
            stations = proposal
            message = if (proposal.size == 1) "Stacja 01 gotowa. Rysuj dalej." else "Stacja ${proposal.size.toString().padStart(2, '0')} dodana · ${test.totalKm.format()} km linii"
        }
    }

    Scaffold(containerColor = Map, topBar = {
        Column(Modifier.background(Ink).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = lineColor, shape = RoundedCornerShape(12.dp)) {
                    Text(lineName, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("METRO MISTRZ", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 15.sp)
                    Text("Polska · projektowanie realistycznej sieci", color = Color(0xFFAEC0D4), fontSize = 10.sp)
                }
                IconButton(onClick = { showRules = true }) { Icon(Icons.Default.Info, "Zasady", tint = Color.White) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                cities.forEach { item -> CityChip(item, item == city) { changeCity(item) } }
            }
        }
    }, bottomBar = {
        Column(Modifier.background(Color.White).navigationBarsPadding().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatPill(Icons.Default.Train, "${stations.size} st.", Navy, Modifier.weight(1f))
                StatPill(Icons.Default.Straighten, "${report.totalKm.format()} km", Navy, Modifier.weight(1f))
                StatPill(if (report.blockers.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning, if (report.blockers.isEmpty()) "realistyczna" else "popraw trasę", if (report.blockers.isEmpty()) Good else Bad, Modifier.weight(1.32f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = {
                    if (stations.isNotEmpty()) { stations = stations.dropLast(1); simulation = false; message = "Cofnięto ostatnią stację." }
                }, enabled = stations.isNotEmpty(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Undo, null); Spacer(Modifier.width(4.dp)); Text("COFNIJ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { simulation = !simulation; building = !simulation }, enabled = canSimulate, modifier = Modifier.weight(1.45f), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = if (simulation) Ink else lineColor)) {
                    Icon(if (simulation) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(if (simulation) "ZATRZYMAJ" else "URUCHOM METRO", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().background(Color(0xFFF7FAFB)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(if (simulation) Good else lineColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(if (simulation) "Pociągi kursują · frekwencja ${routeDemand(stations, city.demand)}%" else city.subtitle, color = Slate, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(if (building) "TRYB BUDOWY" else "SYMULACJA", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
            }
            Box(Modifier.fillMaxSize()) {
                MetroMap(stations, lineColor, simulation, onTap = ::addStation)
                Surface(color = Color.White.copy(.94f), shadowElevation = 7.dp, shape = RoundedCornerShape(18.dp), modifier = Modifier.align(Alignment.TopCenter).padding(14.dp).fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(message, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (stations.size > 1) {
                            Spacer(Modifier.height(3.dp))
                            Text("Odstęp ostatnich stacji: ${report.lastDistance.format()} km · zakręt: ${report.lastTurn.format(0)}°", color = Slate, fontSize = 10.sp)
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = report.blockers.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()) {
                    Surface(color = Color(0xFFFFF3F3), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Bad.copy(.25f), RoundedCornerShape(16.dp))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Bad); Spacer(Modifier.width(9.dp))
                            Text(report.blockers.first(), color = Bad, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    if (showRules) RulesSheet { showRules = false }
}

@Composable private fun CityChip(city: City, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Color.White else Color(0xFF26354A), shape = RoundedCornerShape(50.dp)) {
        Text(city.name, Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = if (selected) Ink else Color(0xFFE1EAF2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun StatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, modifier: Modifier) {
    Row(modifier.height(36.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = .08f)).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable private fun MetroMap(stations: List<Point>, lineColor: Color, simulation: Boolean, onTap: (Point) -> Unit) {
    val animation = rememberInfiniteTransition(label = "train")
    val progress by animation.animateFloat(0f, 1f, infiniteRepeatable(tween(8500, easing = LinearEasing)), label = "trainProgress")
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { tap ->
        val point = Point((tap.x / size.width * 100f).coerceIn(4f, 96f), (tap.y / size.height * 100f).coerceIn(7f, 95f))
        onTap(point)
    } }) {
        drawRect(Map)
        // city blocks
        for (x in 5..95 step 10) for (y in 8..94 step 9) {
            val shift = ((x * 7 + y * 3) % 5).toFloat()
            drawRoundRect(Color(0xFFD3DEE2), Offset(x / 100f * size.width + shift, y / 100f * size.height), Size(size.width * .055f, size.height * .032f), CornerRadius(5f, 5f))
        }
        // parks
        drawOval(Green, Offset(size.width * .09f, size.height * .64f), Size(size.width * .26f, size.height * .20f))
        drawOval(Green, Offset(size.width * .68f, size.height * .12f), Size(size.width * .22f, size.height * .16f))
        // river
        val river = Path().apply { moveTo(size.width * .63f, 0f); cubicTo(size.width*.45f,size.height*.22f,size.width*.75f,size.height*.44f,size.width*.52f,size.height*.69f); cubicTo(size.width*.43f,size.height*.82f,size.width*.56f,size.height*.94f,size.width*.51f,size.height) }
        drawPath(river, Water, style = Stroke(width = size.width * .045f, cap = StrokeCap.Round))
        // roads
        val road = Color(0xFFB9C7CE)
        listOf(.17f,.37f,.78f).forEach { y -> drawLine(road, Offset(0f,size.height*y), Offset(size.width,size.height*(y+.08f)), 5f) }
        listOf(.23f,.48f,.84f).forEach { x -> drawLine(road, Offset(size.width*x,0f), Offset(size.width*(x-.12f),size.height), 4f) }

        fun screen(p: Point) = Offset(p.x / 100f * size.width, p.y / 100f * size.height)
        if (stations.size > 1) {
            val path = Path().apply { moveTo(screen(stations.first()).x, screen(stations.first()).y); stations.drop(1).forEach { lineTo(screen(it).x, screen(it).y) } }
            drawPath(path, Color.White, style = Stroke(width = 15f, cap = StrokeCap.Round))
            drawPath(path, lineColor, style = Stroke(width = 9f, cap = StrokeCap.Round))
        }
        stations.forEachIndexed { i, p ->
            val at = screen(p)
            drawCircle(Color.White, 13f, at)
            drawCircle(lineColor, 9f, at)
            drawCircle(Color.White, 4f, at)
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(16,24,36); textSize = 24f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setShadowLayer(3f,1f,1f,android.graphics.Color.WHITE) }
                drawText("${(i + 1).toString().padStart(2,'0')}", at.x + 15f, at.y - 13f, paint)
            }
        }
        if (simulation && stations.size > 1) {
            val train = pointAlong(stations, progress)
            val at = screen(train)
            drawCircle(Ink, 15f, at); drawCircle(Color.White, 10f, at); drawCircle(lineColor, 6f, at)
        }
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(74,98,115); textSize = 22f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            drawText("DOTKNIJ MAPY · BUDUJ POD ZIEMIĄ", 22f, size.height - 24f, paint)
        }
    }
}

private data class RouteReport(val totalKm: Float, val lastDistance: Float, val lastTurn: Float, val blockers: List<String>, val lastError: String?)

private fun routeReport(points: List<Point>, scale: Float): RouteReport {
    if (points.size < 2) return RouteReport(0f, 0f, 180f, emptyList(), null)
    val dists = points.zipWithNext { a, b -> distance(a, b) * scale }
    val turns = points.windowed(3).map { angle(it[0], it[1], it[2]) }
    val blocker = when {
        dists.last() < .65f -> "Za blisko: zachowaj co najmniej 650 m między stacjami."
        dists.last() > 3.2f -> "Za daleko: pojedynczy odcinek może mieć maks. 3,2 km."
        turns.isNotEmpty() && turns.last() < 115f -> "Za ostry zakręt: metro potrzebuje łagodniejszego łuku (min. 115°)."
        else -> null
    }
    val all = mutableListOf<String>()
    dists.forEach { if (it < .65f) all += "stacje są zbyt blisko"; if (it > 3.2f) all += "odcinek jest zbyt długi" }
    turns.forEach { if (it < 115f) all += "jeden z zakrętów jest za ostry" }
    return RouteReport(dists.sum(), dists.last(), turns.lastOrNull() ?: 180f, all.distinct(), blocker)
}

private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
private fun angle(a: Point, b: Point, c: Point): Float {
    val uX = a.x - b.x; val uY = a.y - b.y; val vX = c.x - b.x; val vY = c.y - b.y
    val denom = hypot(uX, uY) * hypot(vX, vY)
    return if (denom == 0f) 0f else Math.toDegrees(acos(((uX*vX + uY*vY) / denom).coerceIn(-1f,1f).toDouble())).toFloat()
}
private fun pointAlong(points: List<Point>, progress: Float): Point {
    val parts = points.zipWithNext { a,b -> distance(a,b) }; val total = parts.sum().coerceAtLeast(.01f); var left = progress * total
    parts.forEachIndexed { index, len -> if (left <= len) { val t=left/len; val a=points[index]; val b=points[index+1]; return Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t) }; left -= len }
    return points.last()
}
private fun routeDemand(points: List<Point>, base: Int) = (base - max(0, points.size - 9) * 2).coerceAtLeast(42)
private fun Float.format(decimals: Int = 1) = "%1.${decimals}f".format(java.util.Locale.US, this)

@Composable private fun RulesSheet(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("GOTOWE", color = MetroRed, fontWeight = FontWeight.Black) } }, title = { Text("Zasady realistycznej trasy", color = Ink, fontWeight = FontWeight.Black) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Rule(Icons.Default.SocialDistance, "Min. 650 m", "Stacje postawione bliżej nie zostaną dodane.")
            Rule(Icons.Default.Straighten, "Maks. 3,2 km", "Dłuższy odcinek nie ma sensu dla miejskiego metra.")
            Rule(Icons.Default.TurnRight, "Zakręt min. 115°", "Linia nie może nagle zawracać ani być zbyt kręta.")
            Rule(Icons.Default.PlayArrow, "Uruchomienie", "Gdy projekt jest poprawny, możesz włączyć kursowanie.")
        }
    })
}
@Composable private fun Rule(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MetroRed); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.Bold, color = Ink); Text(desc, color = Slate, fontSize = 11.sp) } }
}
