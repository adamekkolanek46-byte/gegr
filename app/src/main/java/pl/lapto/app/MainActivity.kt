package pl.lapto.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val Ink = Color(0xFF090A0C)
private val Card = Color(0xFF15171B)
private val Yellow = Color(0xFFFFD400)
private val Muted = Color(0xFF9699A2)

data class Catch(val number: String, val type: String, val date: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LapToTheme { LapToApp() } }
    }
}

@Composable
private fun LapToTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Yellow, background = Ink, surface = Card),
        typography = Typography(), content = content
    )
}

@Composable
fun LapToApp() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var catches by remember { mutableStateOf(loadCatches(context)) }
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Card) {
                listOf("Start" to Icons.Default.Home, "Album" to Icons.Default.CollectionsBookmark, "Łap" to Icons.Default.PhotoCamera, "Mapa" to Icons.Default.Map, "Ja" to Icons.Default.Person).forEachIndexed { i, item ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(catches) { tab = 2 }
                1 -> AlbumScreen(catches)
                2 -> CameraScreen { number ->
                    val entry = Catch(number, vehicleType(number), SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("pl")).format(Date()))
                    catches = listOf(entry) + catches.filterNot { it.number == number }
                    saveCatches(context, catches)
                    tab = 1
                }
                3 -> ComingSoon("Mapa na żywo", "W następnym kroku podłączymy lokalizacje autobusów i tramwajów Warszawy.")
                else -> ProfileScreen(catches)
            }
        }
    }
}

@Composable
private fun HomeScreen(catches: List<Catch>, onCatch: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("ŁAP TO", color = Yellow, fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 16.sp)
        Text("Warszawa jeździ.\nTy zbierasz.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 42.sp)
        Text("Wypatrz numer taborowy, zrób zdjęcie i dodaj pojazd do swojej kolekcji.", color = Muted, fontSize = 17.sp, lineHeight = 25.sp)
        Button(onClick = onCatch, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(10.dp)); Text("ZŁAP POJAZD", fontWeight = FontWeight.Black)
        }
        StatCard("TWOJA KOLEKCJA", "${catches.size}", "złapanych pojazdów")
        catches.firstOrNull()?.let { Text("OSTATNIO", color = Muted, fontWeight = FontWeight.Bold); VehicleCard(it) }
    }
}

@Composable private fun StatCard(label: String, value: String, caption: String) {
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(22.dp)) {
        Text(label, color = Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(value, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black)
        Text(caption, color = Muted)
    }
}

@Composable
private fun AlbumScreen(catches: List<Catch>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp)); Text("Album", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("${catches.size} unikalnych pojazdów", color = Muted)
        Spacer(Modifier.height(18.dp))
        if (catches.isEmpty()) ComingSoon("Jeszcze tu pusto", "Złap pierwszy autobus albo tramwaj.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(catches) { VehicleCard(it) } }
    }
}

@Composable
private fun VehicleCard(item: Catch) {
    Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(20.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(62.dp).background(Yellow, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            Icon(if (item.type == "Tramwaj") Icons.Default.Tram else Icons.Default.DirectionsBus, null, tint = Ink, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) {
            Text("#${item.number}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(item.type, color = Yellow, fontWeight = FontWeight.Bold); Text(item.date, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CameraScreen(onCaught: (String) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var status by remember { mutableStateOf("Ustaw numer taborowy w ramce") }
    if (!granted) {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.PhotoCamera, null, tint = Yellow, modifier = Modifier.size(72.dp)); Spacer(Modifier.height(20.dp))
            Text("Potrzebny jest aparat", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Zdjęcie analizujemy bezpośrednio na telefonie.", color = Muted); Spacer(Modifier.height(20.dp))
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Włącz aparat") }
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { ctx ->
            PreviewView(ctx).also { view ->
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get(); val preview = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    capture = ImageCapture.Builder().build(); provider.unbindAll()
                    provider.bindToLifecycle(context as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                }, ContextCompat.getMainExecutor(ctx))
            }
        }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Ink.copy(alpha = .86f)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status, color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.height(14.dp))
            FloatingActionButton(onClick = {
                val shot = capture ?: return@FloatingActionButton
                val file = File(context.cacheDir, "catch-${System.currentTimeMillis()}.jpg")
                status = "Czytam numer…"
                shot.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(file))
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image).addOnSuccessListener { text ->
                            val number = findFleetNumber(text.text)
                            if (number != null) onCaught(number) else status = "Nie znaleziono numeru. Spróbuj bliżej."
                            file.delete()
                        }.addOnFailureListener { status = "Nie udało się odczytać zdjęcia" }
                    }
                    override fun onError(exception: ImageCaptureException) { status = "Błąd aparatu" }
                })
            }, containerColor = Yellow, contentColor = Ink, modifier = Modifier.size(72.dp)) { Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(34.dp)) }
        }
    }
}

@Composable private fun ProfileScreen(catches: List<Catch>) = Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Spacer(Modifier.height(8.dp)); Text("Twoje łowy", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    StatCard("ZŁAPANE", catches.size.toString(), "unikalnych numerów")
    StatCard("TRAMWAJE", catches.count { it.type == "Tramwaj" }.toString(), "w albumie")
}

@Composable private fun ComingSoon(title: String, text: String) = Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(text, color = Muted)
}

private fun findFleetNumber(text: String): String? = Regex("(?<![A-Z0-9])\\d{3,5}(?![A-Z0-9])", RegexOption.IGNORE_CASE).findAll(text).map { it.value }.firstOrNull { it.length == 4 || it.length == 5 }
private fun vehicleType(number: String) = if (number.toIntOrNull() in 2000..4999) "Tramwaj" else "Autobus"

private fun loadCatches(context: Context): List<Catch> = context.getSharedPreferences("album", Context.MODE_PRIVATE).getStringSet("catches", emptySet()).orEmpty().mapNotNull {
    val p = it.split("|"); if (p.size == 3) Catch(p[0], p[1], p[2]) else null
}.sortedByDescending { it.date }
private fun saveCatches(context: Context, catches: List<Catch>) { context.getSharedPreferences("album", Context.MODE_PRIVATE).edit().putStringSet("catches", catches.map { "${it.number}|${it.type}|${it.date}" }.toSet()).apply() }
