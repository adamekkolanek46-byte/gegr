package pl.lapto.app.v2

import java.io.File
import org.junit.Test
import org.junit.Assert.*

class ClusterEngineTest {
    private fun districts()=District.parse(File("src/main/assets/warsaw_districts.json").readText())
    private fun vehicle(id:Int,lat:Double,lon:Double)=LiveVehicle(lat,lon,"19","1","Tramwaj",id.toString(),"2026-11-07 10:00:00")
    private fun city(zoom:Double)=MapWindow(zoom,52.5,21.4,52.0,20.7)
    @Test fun realDistrictsHaveCorrectAnchorsAndKnownLocations() {
        val d=districts();assertEquals(18,d.size)
        d.forEach { assertTrue(it.name,it.contains(it.center.lat,it.center.lon)) }
        assertEquals("Śródmieście",d.single { it.contains(52.2319,21.0067) }.name)
        assertEquals("Włochy",d.single { it.contains(52.1657,20.9671) }.name)
    }
    @Test fun thousandsOfVehiclesBecomeExactlyEighteenDistrictBubbles() {
        val d=districts();val vehicles=(0 until 3000).map { i->val c=d[i%d.size].center;vehicle(i,c.lat,c.lon) }
        val engine=ClusterEngine(d);val bubbles=engine.clusters(vehicles,city(11.5))
        assertEquals(18,bubbles.size);assertEquals(3000,bubbles.sumOf { it.count })
        assertTrue(bubbles.all { it.district && it.vehicle==null && it.label.isNotBlank() })
        assertEquals(18,engine.clusters(vehicles,city(13.49)).size)
    }
    @Test fun zoomRevealsIndividualsAndExcludesOffscreenVehicles() {
        val engine=ClusterEngine(districts())
        val a=vehicle(1,52.23,21.01);val b=vehicle(2,52.231,21.011);val far=vehicle(3,52.4,21.3)
        val window=MapWindow(17.0,52.24,21.02,52.22,21.0)
        val bubbles=engine.clusters(listOf(a,b,far),window)
        assertEquals(listOf("1","2"),bubbles.map { it.vehicle?.number })
        assertTrue(bubbles.none { it.district })
    }
    @Test fun crowdedViewportStaysBoundedWithoutLosingVehicles() {
        val vehicles=(0 until 5000).map { i->vehicle(i,52.10+(i%100)*.003,20.85+(i/100)*.006) }
        val bubbles=ClusterEngine(districts()).clusters(vehicles,city(17.0))
        assertTrue(bubbles.size<=160);assertEquals(5000,bubbles.sumOf { it.count })
    }
    @Test fun filtersAndOutsideCityCountsDoNotLeakOldVehicles() {
        val engine=ClusterEngine(districts());val far=vehicle(1,52.7,21.4)
        assertEquals(1,engine.clusters(listOf(far),city(11.0)).single { it.id=="outside" }.count)
        assertEquals(0,engine.clusters(emptyList(),city(11.0)).sumOf { it.count })
    }
}
