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
            compose.runOnIdle {
                val map=requireNotNull(mapIn(compose.activity.window.decorView))
                assertNotNull("The displayed map must not have been destroyed",map.tileRequestCompleteHandler)
                map.setUseDataConnection(false)
                val vehicles=(1..120).map { n -> LiveVehicle(52.2297,21.0122,"19","1","Tramwaj",n.toString(),"2026-11-07 09:00:00") }
                renderClusters(compose.activity,map,vehicles){}
                renderClusters(compose.activity,map,vehicles){}
                assertEquals(1,map.overlays.count { it is org.osmdroid.views.overlay.Marker })
                map.controller.setZoom(14.0)
                assertNotNull(map.tileRequestCompleteHandler)
                renderClusters(compose.activity,map,emptyList()){}
                assertEquals(0,map.overlays.count { it is org.osmdroid.views.overlay.Marker })
            }
            compose.onNodeWithText("Start",useUnmergedTree=true).performClick()
        }
    }
}
