package pl.lapto.app.v2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

private val Navy = Color(0xFF061A35)
private val ZtmBlue = Color(0xFF005AA9)
private val WarsawRed = Color(0xFFE31937)
private val WarsawYellow = Color(0xFFFFD500)
private val Surface = Color(0xFF102A49)
private val Muted = Color(0xFF9CB1C9)

enum class Rarity(val label: String, val color: Color) {
    COMMON("Pospolity", Color(0xFF63B3ED)), RARE("Rzadki", Color(0xFF8B5CF6)),
    VERY_RARE("Bardzo rzadki", WarsawYellow), TECHNICAL("Techniczny", Color(0xFFFF7A00)),
    VINTAGE("Zabytkowy", Color(0xFF2DD4A8)), TEST("Testowy", WarsawRed)
}

data class VehicleCatch(
    val number: String, val type: String, val date: String, val sticker: String,
    val rarity: Rarity, val model: String, val technical: String
)

data class LiveVehicle(val lat: Double, val lon: Double, val line: String, val brigade: String, val type: String)
data class FleetModel(val id: String, val type: String, val name: String, val numbers: List<String>, val years: List<Int>, val operator: String, val depot: String, val length: Int, val power: String, val seats: Int, val capacity: Int, val floor: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContent { LapToTheme { LapToApp() } }
    }
}

@Composable private fun LapToTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = WarsawYellow, secondary = WarsawRed, background = Navy, surface = Surface),
    content = content
)

@Composable private fun LapToApp() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var catches by remember { mutableStateOf(loadCatches(context)) }
    val catalog = remember { loadFleetCatalog(context) }
    var deleteTarget by remember { mutableStateOf<VehicleCatch?>(null) }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Usunąć pojazd #${item.number}?") },
            text = { Text("Naklejka i wpis w albumie zostaną trwale usunięte.") },
            confirmButton = { TextButton(onClick = {
                if (item.sticker.isNotBlank()) File(item.sticker).delete()
                catches = catches.filterNot { it.number == item.number }; saveCatches(context, catches); deleteTarget = null
            }) { Text("USUŃ", color = WarsawRed) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("ANULUJ") } }
        )
    }

    Scaffold(containerColor = Navy, bottomBar = {
        NavigationBar(containerColor = Color(0xFF071F3D)) {
            listOf("Start" to Icons.Default.Home, "Galeria" to Icons.Default.PhotoLibrary, "Łap" to Icons.Default.PhotoCamera, "Mapa" to Icons.Default.Map, "Ja" to Icons.Default.Person).forEachIndexed { i, item ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Home(catches) { tab = 2 }
                1 -> Gallery(catches, catalog, onDelete = { deleteTarget = it })
                2 -> CameraScreen(catalog) { item -> catches = listOf(item) + catches.filterNot { it.number == item.number }; saveCatches(context, catches); tab = 1 }
                3 -> LiveMapScreen()
                else -> Profile(catches)
            }
        }
    }
}

@Composable private fun Home(catches: List<VehicleCatch>, onCamera: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(4.dp)); Text("ŁAP TO · WARSZAWA", color = WarsawYellow, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Złap miasto\nw ruchu.", color = Color.White, fontSize = 40.sp, lineHeight = 43.sp, fontWeight = FontWeight.Black)
        Text("Fotografuj autobusy i tramwaje. Każdy pojazd zmieni się w naklejkę bez tła.", color = Muted, fontSize = 17.sp)
        Button(onClick = onCamera, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = WarsawYellow, contentColor = Navy)) {
            Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(10.dp)); Text("ZŁAP POJAZD", fontWeight = FontWeight.Black)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("POJAZDY", catches.size.toString(), Modifier.weight(1f)); Stat("MODELE", catches.map { it.model }.distinct().size.toString(), Modifier.weight(1f))
        }
        catches.firstOrNull()?.let { Text("OSTATNIA NAKLEJKA", color = Muted, fontWeight = FontWeight.Bold); VehicleCard(it, {}) }
    }
}

@Composable private fun Stat(title: String, value: String, modifier: Modifier) = Column(modifier.background(Surface, RoundedCornerShape(20.dp)).padding(18.dp)) {
    Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(value, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
}

@Composable private fun Gallery(catches: List<VehicleCatch>, catalog: List<FleetModel>, onDelete: (VehicleCatch) -> Unit) {
    var filter by remember { mutableStateOf("Wszystkie") }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    selectedModel?.let { id ->
        catalog.firstOrNull { it.id == id }?.let { model -> ModelDetail(model, catches.filter { it.number in model.numbers }, onBack = { selectedModel = null }, onDelete = onDelete) }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp)) {
            Row { Column(Modifier.weight(1f)) { Text("TWÓJ ALBUM", color = Muted, letterSpacing = 3.sp, fontSize = 12.sp); Text("Warszawski tabor", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black) }; Text("${catches.size} / ${catalog.sumOf { it.numbers.size }}", color = Muted, fontWeight = FontWeight.Bold) }
        }
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Wszystkie", "Autobus", "Tramwaj", "Metro", "SKM", "Braki")) { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name.uppercase()) }) }
        }
        Spacer(Modifier.height(10.dp))
        val groups = catalog.filter { model -> filter == "Wszystkie" || filter == "Braki" && catches.count { it.number in model.numbers } < model.numbers.size || model.type == filter }.sortedBy { it.type + it.name }
        if (groups.isEmpty()) Empty("Brak modeli w tej kategorii") else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups, key = { it.id }) { model -> ModelCard(model, catches.filter { it.number in model.numbers }, onClick = { selectedModel = model.id }) }
        }
    }
}

@Composable private fun ModelCard(model: FleetModel, caught: List<VehicleCatch>, onClick: () -> Unit) {
    val total = model.numbers.size; val progress = (caught.size.toFloat()/total.coerceAtLeast(1)).coerceIn(0f,1f); val rarity = rarityFor(total)
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Surface(color = Color(0xFF233D59), shape = RoundedCornerShape(6.dp)) { Text(model.type.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) }; Spacer(Modifier.width(10.dp)); Text(model.name.ifBlank { "Nieznany model" }, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${caught.size}/$total", color = Color.White, fontWeight = FontWeight.Black) }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = rarity.color, trackColor = Color(0xFF263B52))
                Row { Text(model.years.let { if(it.isEmpty()) "ROK —" else "${it.first()}–${it.last()}" }, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(rarity.label.uppercase(), color = rarity.color, fontSize = 11.sp, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.width(12.dp)); val first = caught.firstOrNull(); val bitmap = remember(first?.sticker) { first?.sticker?.takeIf { it.isNotBlank() }?.let(BitmapFactory::decodeFile) }
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) { if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else Icon(Icons.Default.DirectionsTransit, null, tint = rarity.color, modifier = Modifier.size(55.dp)); Surface(color = Color.White, shape = RoundedCornerShape(5.dp), modifier = Modifier.align(Alignment.BottomStart)) { Text(first?.number ?: "?", color = Color.Black, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) } }
        }
    }
}

@Composable private fun ModelDetail(model: FleetModel, items: List<VehicleCatch>, onBack: () -> Unit, onDelete: (VehicleCatch) -> Unit) {
    val total = model.numbers.size; val rarity = rarityFor(total)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Wróć") }; Text("WSZYSTKIE MODELE", color = Muted, letterSpacing = 2.sp, fontSize = 12.sp) } }
        item { Text(model.name.ifBlank { "Nieznany model" }, color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black); Text("${model.type.uppercase()} · ${model.operator.uppercase()}", color = Muted, letterSpacing = 1.sp); Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { (items.size.toFloat()/total.coerceAtLeast(1)).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = rarity.color); Text("${items.size}/$total", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth()) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { TechTile("DŁUGOŚĆ", if(model.length>0) "${model.length/1000.0} m" else "—", Modifier.weight(1f)); TechTile("NAPĘD", model.power, Modifier.weight(1f)); TechTile("MIEJSCA", model.capacity.takeIf { it>0 }?.toString() ?: "—", Modifier.weight(1f)) } }
        item { Text("${model.depot} · ${model.years.let { if(it.isEmpty()) "rok —" else "${it.first()}–${it.last()}" }}", color = Muted) }
        item { Text("TWOJE NAKLEJKI", color = Muted, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
        items(model.numbers.chunked(3)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) { row.forEach { number -> val caught = items.firstOrNull { it.number == number }; Box(Modifier.weight(1f)) { if(caught != null) MiniSticker(caught, onDelete) else MissingSticker(number) } }; repeat(3-row.size) { Spacer(Modifier.weight(1f)) } } }
    }
}

@Composable private fun TechTile(label: String, value: String, modifier: Modifier) = Column(modifier.background(Surface, RoundedCornerShape(16.dp)).padding(13.dp)) { Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp); Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black) }
@Composable private fun MiniSticker(item: VehicleCatch, onDelete: (VehicleCatch) -> Unit) { val bitmap = remember(item.sticker) { item.sticker.takeIf { it.isNotBlank() }?.let(BitmapFactory::decodeFile) }; Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A213D)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(170.dp).clickable { }) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit); Surface(color = Color.White, shape = RoundedCornerShape(6.dp), modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) { Text(item.number, color = Color.Black, fontWeight = FontWeight.Black, modifier = Modifier.padding(7.dp,4.dp)) }; IconButton(onClick = { onDelete(item) }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.DeleteOutline, "Usuń", tint = Muted) } } } }
@Composable private fun MissingSticker(number: String) = Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFF0A213D), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text(number, color = Color(0xFF40556D), fontSize = 18.sp, fontWeight = FontWeight.Bold) }

@Composable private fun VehicleCard(item: VehicleCatch, onLongPressDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(210.dp).background(Color(0xFF0A213D)), contentAlignment = Alignment.Center) {
                val bitmap = remember(item.sticker) { item.sticker.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) } }
                if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(14.dp), contentScale = ContentScale.Fit)
                else Icon(if (item.type == "Tramwaj") Icons.Default.Tram else Icons.Default.DirectionsBus, null, tint = item.rarity.color, modifier = Modifier.size(90.dp))
                IconButton(onClick = { menu = true }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.MoreVert, "Opcje") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { DropdownMenuItem(text = { Text("Usuń z galerii") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onLongPressDelete() }) }
            }
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${item.number}", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Surface(color = item.rarity.color, shape = RoundedCornerShape(50)) { Text(item.rarity.label.uppercase(), color = Navy, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
                }
                Text(item.model, color = WarsawYellow, fontWeight = FontWeight.Bold); Text("${item.type} · ${item.technical}", color = Muted, fontSize = 13.sp); Text(item.date, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun CameraScreen(catalog: List<FleetModel>, onCaught: (VehicleCatch) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var status by remember { mutableStateOf("Ustaw numer taborowy w ramce") }
    var selectedType by remember { mutableStateOf("Auto") }
    if (!granted) { Empty("Aplikacja potrzebuje aparatu") { permission.launch(Manifest.permission.CAMERA) }; return }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { ctx -> PreviewView(ctx).also { view ->
            val future = ProcessCameraProvider.getInstance(ctx); future.addListener({
                val provider = future.get(); val preview = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build(); provider.unbindAll()
                provider.bindToLifecycle(context as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }, ContextCompat.getMainExecutor(ctx))
        } }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Navy.copy(alpha = .92f)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) { items(listOf("Auto", "Autobus", "Tramwaj", "Metro", "SKM")) { type -> FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(type.uppercase(), fontSize = 10.sp) }) } }
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
            FloatingActionButton(containerColor = WarsawYellow, contentColor = Navy, modifier = Modifier.size(72.dp), onClick = {
                val shot = capture ?: return@FloatingActionButton; val photo = File(context.cacheDir, "capture-${System.currentTimeMillis()}.jpg"); status = "Czytam numer…"
                shot.takePicture(ImageCapture.OutputFileOptions.Builder(photo).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        val input = InputImage.fromFilePath(context, android.net.Uri.fromFile(photo))
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(input).addOnSuccessListener { text ->
                            val number = findFleetNumber(text.text, catalog)
                            if (number == null) { status = "Nie znaleziono numeru — podejdź bliżej"; photo.delete(); return@addOnSuccessListener }
                            status = "Wycinam pojazd z tła…"
                            val opts = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
                            SubjectSegmentation.getClient(opts).process(input).addOnSuccessListener { segmented ->
                                val foreground = segmented.foregroundBitmap
                                if (foreground == null) { status = "Nie wykryto pojazdu na zdjęciu"; photo.delete(); return@addOnSuccessListener }
                                val stickerFile = File(context.filesDir, "sticker-$number-${System.currentTimeMillis()}.png")
                                stickerFile.outputStream().use { createSticker(foreground).compress(Bitmap.CompressFormat.PNG, 100, it) }
                                val info = vehicleInfo(number, selectedType, catalog)
                                onCaught(VehicleCatch(number, info.first, now(), stickerFile.absolutePath, info.second, info.third, info.fourth)); photo.delete()
                            }.addOnFailureListener { e -> status = if (e.message?.contains("download", true) == true) "Pobieram model wycinania — spróbuj ponownie za chwilę" else "Nie udało się wyciąć tła" }
                        }.addOnFailureListener { status = "Nie udało się odczytać zdjęcia" }
                    }
                    override fun onError(exception: ImageCaptureException) { status = "Błąd aparatu" }
                })
            }) { Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(34.dp)) }
        }
    }
}

@Composable private fun LiveMapScreen() {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(context.getSharedPreferences("settings", 0).getString("warsaw_api_key", "").orEmpty()) }
    var vehicles by remember { mutableStateOf(emptyList<LiveVehicle>()) }
    var status by remember { mutableStateOf("Wpisz klucz API Warszawy") }
    var map by remember { mutableStateOf<MapView?>(null) }
    var editKey by remember { mutableStateOf(apiKey.isBlank()) }
    val scope = rememberCoroutineScope()
    fun refresh() { if (apiKey.isBlank()) return; status = "Pobieram pojazdy…"; scope.launch {
        runCatching { fetchAllVehicles(apiKey) }.onSuccess { vehicles = it; status = "${it.size} pojazdów · aktualizacja co 10 s"; map?.let { m -> renderClusters(context, m, it) } }.onFailure { status = "Błąd API: ${it.message ?: "spróbuj ponownie"}" }
    } }
    DisposableEffect(apiKey) { if (apiKey.isNotBlank()) { val job = scope.launch { while (isActive) { refresh(); delay(10_000) } }; onDispose { job.cancel() } } else onDispose { } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Mapa pojazdów", color = Color.White, fontWeight = FontWeight.Bold); Text(status, color = Muted, fontSize = 12.sp) }
            IconButton(onClick = { editKey = true }) { Icon(Icons.Default.Key, "Klucz API") }; IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Odśwież") }
        }
        AndroidView(factory = { ctx -> MapView(ctx).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(12.0); controller.setCenter(GeoPoint(52.2297, 21.0122)); addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean { renderClusters(ctx, this@apply, vehicles); return false }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean { renderClusters(ctx, this@apply, vehicles); return false }
        }); map = this } }, modifier = Modifier.weight(1f).fillMaxWidth())
    }
    if (editKey) AlertDialog(onDismissRequest = { editKey = false }, title = { Text("Klucz API Warszawy") }, text = { OutlinedTextField(value = apiKey, onValueChange = { apiKey = it.trim() }, label = { Text("apikey") }, supportingText = { Text("Darmowy klucz z api.um.warszawa.pl") }) }, confirmButton = { TextButton(onClick = { context.getSharedPreferences("settings", 0).edit().putString("warsaw_api_key", apiKey).apply(); editKey = false; refresh() }) { Text("ZAPISZ") } }, dismissButton = { TextButton(onClick = { editKey = false }) { Text("ANULUJ") } })
}

@Composable private fun Profile(catches: List<VehicleCatch>) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { Text("Twoja kolekcja", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black) }
    items(Rarity.entries) { r -> Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(17.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(r.color, RoundedCornerShape(50))); Spacer(Modifier.width(12.dp)); Text(r.label, color = Color.White, modifier = Modifier.weight(1f)); Text(catches.count { it.rarity == r }.toString(), color = WarsawYellow, fontWeight = FontWeight.Black) } }
}

@Composable private fun Empty(text: String, action: (() -> Unit)? = null) = Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(Icons.Default.DirectionsBus, null, tint = WarsawYellow, modifier = Modifier.size(70.dp)); Spacer(Modifier.height(12.dp)); Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    if (action != null) { Spacer(Modifier.height(16.dp)); Button(onClick = action) { Text("KONTYNUUJ") } }
}

private suspend fun fetchAllVehicles(key: String): List<LiveVehicle> = withContext(Dispatchers.IO) {
    listOf("1" to "Autobus", "2" to "Tramwaj").flatMap { (type, label) ->
        val connection = URL("https://dane.um.warszawa.pl/api/action/get_ztm_lokalizacja_pojazdow").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"; connection.connectTimeout = 15_000; connection.readTimeout = 20_000; connection.doOutput = true
        connection.setRequestProperty("Authorization", key); connection.setRequestProperty("Content-Type", "application/json"); connection.setRequestProperty("Accept", "application/json")
        connection.outputStream.bufferedWriter().use { it.write("{\"type\":$type}") }
        if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
        val raw = connection.inputStream.bufferedReader().use { it.readText() }; connection.disconnect()
        val result = if (raw.trimStart().startsWith("[")) JSONArray(raw) else JSONObject(raw).optJSONArray("result") ?: JSONArray()
        (0 until result.length()).mapNotNull { i -> result.optJSONObject(i)?.let { o ->
            val lat = o.optDouble("Lat"); val lon = o.optDouble("Lon"); if (lat !in 52.0..52.5 || lon !in 20.5..21.5) null else LiveVehicle(lat, lon, o.optString("Lines", "?"), o.optString("Brigade", "?"), label)
        } }
    }
}

private fun renderClusters(context: Context, map: MapView, vehicles: List<LiveVehicle>) {
    map.overlays.removeAll { it is Marker }
    if (vehicles.isEmpty()) { map.invalidate(); return }
    val precision = when { map.zoomLevelDouble >= 16 -> .001; map.zoomLevelDouble >= 14 -> .004; map.zoomLevelDouble >= 12 -> .012; else -> .035 }
    vehicles.groupBy { Pair((it.lat / precision).roundToInt(), (it.lon / precision).roundToInt()) }.values.forEach { group ->
        val lat = group.map { it.lat }.average(); val lon = group.map { it.lon }.average(); val count = group.size
        Marker(map).apply { position = GeoPoint(lat, lon); setAnchor(.5f, .5f); title = if (count == 1) "Linia ${group[0].line} · ${group[0].type}" else "$count pojazdów"; snippet = if (count == 1) "Brygada ${group[0].brigade}" else "Powiększ mapę, aby rozdzielić"; icon = BitmapDrawable(context.resources, clusterBitmap(if (count > 99) "99+" else count.toString(), count == 1)); map.overlays.add(this) }
    }; map.invalidate()
}

private fun clusterBitmap(text: String, single: Boolean): Bitmap {
    val size = if (single) 72 else 86; val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val c = Canvas(b)
    c.drawCircle(size/2f, size/2f, size*.43f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (single) android.graphics.Color.rgb(0,90,169) else android.graphics.Color.rgb(227,25,55) })
    c.drawText(text, size/2f, size*.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = if (text.length > 2) 25f else 30f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }); return b
}

private fun createSticker(source: Bitmap): Bitmap {
    val max = 1200f; val scale = min(1f, max / max(source.width, source.height)); val fg = if (scale < 1) Bitmap.createScaledBitmap(source, (source.width*scale).toInt(), (source.height*scale).toInt(), true) else source
    val pad = 28; val out = Bitmap.createBitmap(fg.width+pad*2, fg.height+pad*2, Bitmap.Config.ARGB_8888); val canvas = Canvas(out); val alpha = fg.extractAlpha()
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN) }
    for (x in -10..10 step 4) for (y in -10..10 step 4) if (x*x+y*y <= 110) canvas.drawBitmap(alpha, (pad+x).toFloat(), (pad+y).toFloat(), white)
    canvas.drawBitmap(fg, pad.toFloat(), pad.toFloat(), null); return out
}

private data class Info(val first: String, val second: Rarity, val third: String, val fourth: String)
private fun vehicleInfo(n: String, chosenType: String = "Auto", catalog: List<FleetModel> = emptyList()): Info {
    catalog.firstOrNull { n in it.numbers }?.let { m -> return Info(m.type, rarityFor(m.numbers.size), m.name.ifBlank { "Nieznany model" }, listOf(m.power, m.floor).filter { it.isNotBlank() }.joinToString(" · ")) }
    val v = n.toIntOrNull() ?: 0; val type = if (chosenType != "Auto") chosenType else if (v in 2000..4999) "Tramwaj" else "Autobus"
    val rarity = when { v in 90000..99999 -> Rarity.TEST; v in 100..999 -> Rarity.VINTAGE; v in 50000..89999 -> Rarity.TECHNICAL; v % 41 == 0 -> Rarity.VERY_RARE; v % 7 == 0 -> Rarity.RARE; else -> Rarity.COMMON }
    val model = when { type == "Metro" && v in 1..99 -> "Škoda Varsovia"; type == "Metro" -> "Pociąg metra"; type == "SKM" && v in 1..99 -> "Newag Impuls"; type == "SKM" -> "Elektryczny zespół trakcyjny"; type == "Tramwaj" && v in 4200..4299 -> "Hyundai Rotem 140N"; type == "Tramwaj" && v in 3000..3599 -> "Pesa Swing"; type == "Autobus" && v in 8300..8799 -> "Solaris Urbino 18"; type == "Autobus" && v in 9800..9899 -> "Mercedes-Benz Conecto G"; rarity == Rarity.TECHNICAL -> "Pojazd techniczny"; else -> "Model do potwierdzenia" }
    val specs = when(type) { "Tramwaj" -> "szynowy · niskopodłogowy"; "Metro" -> "metro · elektryczny"; "SKM" -> "kolej miejska · elektryczny"; else -> "miejski · niskopodłogowy" }; return Info(type, rarity, model, specs)
}

private fun modelCapacity(model: String) = when(model) { "Hyundai Rotem 140N" -> 85; "Pesa Swing" -> 186; "Solaris Urbino 18" -> 403; "Mercedes-Benz Conecto G" -> 99; "Škoda Varsovia" -> 37; "Newag Impuls" -> 35; else -> 25 }
private fun rarityFor(total: Int) = when { total <= 12 -> Rarity.VERY_RARE; total <= 48 -> Rarity.RARE; else -> Rarity.COMMON }

private fun findFleetNumber(text: String, catalog: List<FleetModel>): String? {
    val candidates = Regex("(?<![A-Z0-9])\\d{2,5}[A-Z]?(?![A-Z0-9])", RegexOption.IGNORE_CASE).findAll(text.uppercase()).map { it.value }.toList()
    val known = catalog.asSequence().flatMap { it.numbers.asSequence() }.toHashSet()
    return candidates.firstOrNull { it in known } ?: candidates.firstOrNull { it.count(Char::isDigit) >= 4 }
}
private fun now() = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("pl")).format(Date())
private fun saveCatches(context: Context, list: List<VehicleCatch>) { val a = JSONArray(); list.forEach { a.put(JSONObject().put("n",it.number).put("t",it.type).put("d",it.date).put("s",it.sticker).put("r",it.rarity.name).put("m",it.model).put("x",it.technical)) }; context.getSharedPreferences("album_v2",0).edit().putString("items",a.toString()).apply() }
private fun loadCatches(context: Context): List<VehicleCatch> = runCatching { val a = JSONArray(context.getSharedPreferences("album_v2",0).getString("items","[]")); (0 until a.length()).map { i -> a.getJSONObject(i).let { VehicleCatch(it.getString("n"),it.getString("t"),it.getString("d"),it.optString("s"),Rarity.valueOf(it.optString("r","COMMON")),it.optString("m","Model do potwierdzenia"),it.optString("x")) } } }.getOrDefault(emptyList())
private fun loadFleetCatalog(context: Context): List<FleetModel> = runCatching {
    val a = JSONArray(context.assets.open("fleet_catalog.json").bufferedReader().use { it.readText() })
    (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
        val ns=o.getJSONArray("numbers"); val ys=o.getJSONArray("years")
        FleetModel(o.getString("id"),o.getString("type"),o.optString("name"), (0 until ns.length()).map { ns.getString(it) }, (0 until ys.length()).map { ys.getInt(it) },o.optString("operator"),o.optString("depot"),o.optInt("length"),o.optString("power"),o.optInt("seats"),o.optInt("capacity"),o.optString("floor"))
    }
}.getOrDefault(emptyList())
