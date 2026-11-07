package pl.lapto.app.v2

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class MapScreenTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun mapIn(view:View):MapView? {
        if(view is MapView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount){mapIn(view.getChildAt(i))?.let {return it}}
        return null
    }
    @Test fun openMapZoomSwitchAwayAndReturn() {
        repeat(3) {
            compose.onNodeWithText("Mapa",useUnmergedTree=true).performClick()
            compose.onNodeWithText("ANULUJ").performClick()
            compose.waitUntil(10000) {
                mapIn(compose.activity.window.decorView)?.overlays?.filterIsInstance<VehicleBubbleOverlay>()?.singleOrNull()?.bubbles?.size==18
            }
            compose.runOnIdle {
                val map=requireNotNull(mapIn(compose.activity.window.decorView))
                assertNotNull("The displayed map must not have been destroyed",map.tileRequestCompleteHandler)
                map.setUseDataConnection(false)
                val overlay=map.overlays.filterIsInstance<VehicleBubbleOverlay>().single()
                assertEquals(18,overlay.bubbles.size)
                assertTrue(overlay.bubbles.all { it.district && it.vehicle==null })
                assertEquals(0,map.overlays.count { it is org.osmdroid.views.overlay.Marker })
                val district=overlay.bubbles.first { it.label=="Śródmieście" }
                val pixel=map.projection.toPixels(org.osmdroid.util.GeoPoint(district.lat,district.lon),null)
                val tap=android.view.MotionEvent.obtain(0L,0L,android.view.MotionEvent.ACTION_UP,pixel.x.toFloat(),pixel.y.toFloat(),0)
                assertTrue(overlay.onSingleTapConfirmed(tap,map));tap.recycle()
                assertEquals(14.0,map.zoomLevelDouble,.01)
                assertNotNull(map.tileRequestCompleteHandler)
            }
            compose.waitUntil(10000) {
                mapIn(compose.activity.window.decorView)?.overlays?.filterIsInstance<VehicleBubbleOverlay>()?.singleOrNull()?.bubbles?.isEmpty()==true
            }
            compose.onNodeWithText("Start",useUnmergedTree=true).performClick()
        }
    }
}
