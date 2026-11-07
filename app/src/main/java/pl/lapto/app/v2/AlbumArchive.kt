package pl.lapto.app.v2

import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.*

internal data class ArchivePreview(val root:File,val items:JSONArray,val settings:JSONObject)
internal object AlbumArchive {
    private const val MAX_TOTAL=512L*1024*1024
    private const val MAX_FILE=16L*1024*1024
    fun write(output:OutputStream, items:JSONArray, settings:JSONObject, photo:(String)->File?) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            val entries=JSONArray()
            for(i in 0 until items.length()) {
                val row=JSONObject(items.getJSONObject(i).toString())
                val file=photo(row.optString("s"))
                if(file!=null && file.isFile) {
                    require(file.length()<=MAX_FILE){"Zdjęcie jest zbyt duże"}
                    val name="photos/$i.png"
                    zip.putNextEntry(ZipEntry(name));file.inputStream().use { it.copyTo(zip) };zip.closeEntry()
                    row.put("s",name)
                } else row.put("s","")
                entries.put(row)
            }
            val manifest=JSONObject().put("format","lap-to-album").put("version",1).put("items",entries).put("settings",settings)
            zip.putNextEntry(ZipEntry("album.json"));zip.write(manifest.toString().toByteArray(Charsets.UTF_8));zip.closeEntry()
        }
    }
    fun read(input:InputStream, target:File):ArchivePreview {
        target.mkdirs();var total=0L;var count=0;val names=HashSet<String>()
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while(true) {
                    val entry=zip.nextEntry ?: break
                    require(++count<=10001 && names.add(entry.name)){"Nieprawidłowa kopia"}
                    require(!entry.isDirectory && (entry.name=="album.json" || Regex("photos/[0-9]+\\.png").matches(entry.name))){"Nieprawidłowa ścieżka"}
                    val file=File(target,entry.name);file.parentFile?.mkdirs();var size=0L
                    file.outputStream().use { out ->
                        val buffer=ByteArray(8192)
                        while(true){val n=zip.read(buffer);if(n<0)break;size+=n;total+=n
                            require(size<=MAX_FILE && total<=MAX_TOTAL){"Kopia przekracza limit 512 MB"};out.write(buffer,0,n)}
                    };zip.closeEntry()
                }
            }
            val root=JSONObject(File(target,"album.json").readText())
            require(root.getString("format")=="lap-to-album" && root.getInt("version")==1){"Nieobsługiwany format kopii"}
            val items=root.getJSONArray("items");require(items.length()<=10000)
            val ids=HashSet<String>()
            for(i in 0 until items.length()) {
                val item=items.getJSONObject(i);val n=item.getString("n");val t=item.getString("t");val photo=item.getString("s")
                require(n.isNotBlank() && n.length<=40 && t in listOf("Autobus","Tramwaj","Metro","SKM"))
                require(ids.add("$t:$n")){"Powtórzony pojazd w kopii"}
                require(item.getString("d").length<=100 && item.getString("m").length<=500)
                require(item.getString("r") in listOf("COMMON","RARE","VERY_RARE","TECHNICAL","VINTAGE","TEST"))
                require(photo.isBlank() || (Regex("photos/[0-9]+\\.png").matches(photo) && File(target,photo).isFile)){"Brak zdjęcia w kopii"}
            }
            return ArchivePreview(target,items,root.optJSONObject("settings") ?: JSONObject())
        } catch(e:Exception) { target.deleteRecursively();throw e }
    }
}
