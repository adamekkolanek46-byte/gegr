package pl.lapto.app.v2

import org.junit.Test
import org.junit.Assert.*
import org.json.*
import java.io.*
import java.nio.file.Files
import java.util.zip.*

class AlbumTests {
    private fun model(id:String,years:List<Int>,numbers:List<String>,type:String="Tramwaj")=FleetModel(id,type,"Alstom Konstal 105N",numbers,years,"TW","R1",13000,"elektryczny",20,100,"wysoka","Alstom Konstal")
    @Test fun combinesAllYearsWithoutDuplicatingNumbers() {
        val rows=groupFleet(listOf(model("1",listOf(1999,2000),listOf("101","102")),model("2",listOf(1997,1999),listOf("102","103"))))
        assertEquals(1,rows.size);assertEquals(listOf(1997,1999,2000),rows[0].years);assertEquals(listOf("101","102","103"),rows[0].numbers)
    }
    @Test fun doesNotMixDifferentTransportTypesOrModels() {
        assertEquals(3,groupFleet(listOf(model("1",emptyList(),listOf("1")),model("2",emptyList(),listOf("1"),"Metro"),model("3",emptyList(),listOf("2")).copy(name="105Na"))).size)
    }
    private fun item()=JSONObject().put("n","105").put("t","Tramwaj").put("d","07.11.2026, 09:30").put("m","105N").put("r","COMMON").put("s","local-photo").put("x","")
    @Test fun archiveRoundTripPreservesPhotosAndSettings() {
        val root=Files.createTempDirectory("album-test").toFile()
        try {
            val photo=File(root,"photo.png").apply { writeBytes(byteArrayOf(1,2,3,4)) }
            val bytes=ByteArrayOutputStream();AlbumArchive.write(bytes,JSONArray().put(item()),JSONObject().put("warsaw_api_key","test")){photo}
            val parsed=AlbumArchive.read(ByteArrayInputStream(bytes.toByteArray()),File(root,"read"))
            assertEquals("105",parsed.items.getJSONObject(0).getString("n"))
            assertArrayEquals(photo.readBytes(),File(parsed.root,parsed.items.getJSONObject(0).getString("s")).readBytes())
            assertEquals("test",parsed.settings.getString("warsaw_api_key"))
        } finally {root.deleteRecursively()}
    }
    @Test fun rejectsTraversalWithoutWritingOutsideStaging() {
        val root=Files.createTempDirectory("album-test").toFile();val bytes=ByteArrayOutputStream()
        ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry("../outside"));it.write(byteArrayOf(1));it.closeEntry() }
        try {
            assertThrows(IllegalArgumentException::class.java){AlbumArchive.read(ByteArrayInputStream(bytes.toByteArray()),File(root,"read"))}
            assertFalse(File(root,"outside").exists());assertFalse(File(root,"read").exists())
        } finally {root.deleteRecursively()}
    }
    @Test fun rejectsMissingPhotosBeforeImport() {
        val root=Files.createTempDirectory("album-test").toFile();val bytes=ByteArrayOutputStream()
        val manifest=JSONObject().put("format","lap-to-album").put("version",1).put("items",JSONArray().put(item().put("s","photos/0.png")))
        ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry("album.json"));it.write(manifest.toString().toByteArray());it.closeEntry() }
        try {assertThrows(IllegalArgumentException::class.java){AlbumArchive.read(ByteArrayInputStream(bytes.toByteArray()),File(root,"read"))}}
        finally {root.deleteRecursively()}
    }
}
