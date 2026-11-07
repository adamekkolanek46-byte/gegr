package pl.lapto.app.v2

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

@Composable internal fun SettingsScreen(catches:List<VehicleCatch>, onImported:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) };var status by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ArchivePreview?>(null) }
    fun export(uri:Uri?,share:Boolean) {
        scope.launch {
            busy=true;status="Pakuję album i zdjęcia…"
            try {
                val file=withContext(Dispatchers.IO) {
                    val target=File(context.cacheDir,"shared/album-lap-to.zip");target.parentFile?.mkdirs()
                    val prefs=context.getSharedPreferences("album_v2",0)
                    val data=JSONArray(prefs.getString("items","[]"))
                    val settings=JSONObject().put("warsaw_api_key",defaultApiKey(context))
                    target.outputStream().use { out -> AlbumArchive.write(out,data,settings) { path ->
                        path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.canonicalPath.startsWith(context.filesDir.canonicalPath+File.separator) }
                    } }
                    if(uri!=null) requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use { out->target.inputStream().use { it.copyTo(out) } }
                    target
                }
                if(share) {
                    val fileUri=FileProvider.getUriForFile(context,context.packageName+".files",file)
                    val intent=Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,fileUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(intent,"Prześlij album na drugi telefon"))
                }
                status=if(share)"Kopia przygotowana do przesłania." else "Kopia zapisana. Na drugim telefonie wybierz Wczytaj kopię."
            } catch(e:Exception){status="Nie udało się zapisać kopii. Sprawdź wolne miejsce i spróbuj ponownie."}
            finally {busy=false}
        }
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri->if(uri!=null)export(uri,false) }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null) scope.launch {
        busy=true;status="Sprawdzam kopię…"
        try { preview=withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openInputStream(uri)).use { AlbumArchive.read(it,File(context.cacheDir,"import-${UUID.randomUUID()}")) } };status="Kopia gotowa do wczytania." }
        catch(e:Exception){status="Nieprawidłowa lub zbyt duża kopia. Twój album nie został zmieniony."}
        finally {busy=false}
    } }
    DisposableEffect(Unit) { onDispose { preview?.root?.deleteRecursively() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("USTAWIENIA",color=Color(0xFFE51C46),letterSpacing=3.sp)
        Text("Twój album,\nna każdym telefonie.",fontSize=30.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
        Text("Prześlij kopię przez Quick Share lub zapisz plik. Zawiera pojazdy, zdjęcia i ustawienia mapy. Na drugim telefonie zainstaluj Łap To i wczytaj ten plik.",color=Color(0xFFAAAAAF))
        Button(enabled=!busy,onClick={export(null,true)},modifier=Modifier.fillMaxWidth()){Text("PRZEŚLIJ ALBUM")}
        OutlinedButton(enabled=!busy,onClick={save.launch("album-lap-to.zip")},modifier=Modifier.fillMaxWidth()){Text("ZAPISZ KOPIĘ DO PLIKU")}
        OutlinedButton(enabled=!busy,onClick={open.launch(arrayOf("application/zip","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Text("WCZYTAJ KOPIĘ")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank())Text(status,color=Color(0xFFC9B972))
        HorizontalDivider()
        Text("Mapa",fontSize=22.sp,fontWeight=FontWeight.Bold)
        Text(if(defaultApiKey(context).isNotBlank())"API ustawione — mapa połączy się automatycznie." else "Brak klucza API. Ustaw go przyciskiem klucza na mapie.",color=Color(0xFFAAAAAF))
        Text("Twoja kolekcja: ${catches.size} pojazdów",color=Color(0xFFAAAAAF))
        Text("Łap To · 0.5.1",color=Color.Gray,fontSize=12.sp)
    }
    preview?.let { data ->
        AlertDialog(onDismissRequest={if(!busy){data.root.deleteRecursively();preview=null}},title={Text("Wczytać ${data.items.length()} pojazdów?")},text={Text("Połączę tę kopię z Twoim albumem. Pojazdy, które już masz, zachowają swoje zdjęcia.")},
            confirmButton={TextButton(enabled=!busy,onClick={scope.launch {
                busy=true
                try {val count=withContext(Dispatchers.IO){mergeArchive(context,data)};onImported();status="Dodano $count pojazdów. Album jest gotowy.";preview=null}
                catch(e:Exception){status="Nie udało się wczytać kopii. Dotychczasowy album pozostał bez zmian.";preview=null}
                finally {data.root.deleteRecursively();busy=false}
            }}){Text("POŁĄCZ ALBUMY")}},dismissButton={TextButton(enabled=!busy,onClick={data.root.deleteRecursively();preview=null}){Text("ANULUJ")}})
    }
}

internal fun mergeArchive(context:Context,archive:ArchivePreview):Int {
    val prefs=context.getSharedPreferences("album_v2",0)
    val original=prefs.getString("items","[]") ?: "[]"
    val merged=JSONArray(original)
    val known=(0 until merged.length()).map { merged.getJSONObject(it).let { o->o.getString("t")+":"+o.getString("n") } }.toHashSet()
    val dir=File(context.filesDir,"import-${UUID.randomUUID()}");dir.mkdirs();var count=0
    try {
        for(i in 0 until archive.items.length()) {
            val row=JSONObject(archive.items.getJSONObject(i).toString())
            if(!known.add(row.getString("t")+":"+row.getString("n")))continue
            val photo=row.getString("s")
            if(photo.isNotBlank()) {
                val from=File(archive.root,photo);val target=File(dir,"$i.png")
                from.copyTo(target);row.put("s",target.absolutePath)
            }
            merged.put(row);count++
        }
        if(!prefs.edit().putString("items",merged.toString()).commit()) {
            prefs.edit().putString("items",original).commit()
            error("Nie udało się zapisać albumu")
        }
    } catch(e:Exception) {dir.deleteRecursively();throw e}
    if(count==0)dir.deleteRecursively()
    archive.settings.optString("warsaw_api_key").takeIf { it.isNotBlank() && it.length<4096 }?.let { key->
        if(context.getSharedPreferences("settings",0).getString("warsaw_api_key","").isNullOrBlank())context.getSharedPreferences("settings",0).edit().putString("warsaw_api_key",key).apply()
    }
    return count
}
