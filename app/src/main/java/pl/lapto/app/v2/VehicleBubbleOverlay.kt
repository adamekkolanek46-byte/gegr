package pl.lapto.app.v2

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** One canvas overlay, shared paints, no bitmap/Marker allocation during gestures. */
internal class VehicleBubbleOverlay(private val density:Float,private val select:(LiveVehicle)->Unit):Overlay() {
    var bubbles:List<MapBubble> = emptyList(); private set
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG)
    private val border=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFFFFFFFF.toInt();style=Paint.Style.STROKE;strokeWidth=2*density }
    private val text=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFFFFFFFF.toInt();textAlign=Paint.Align.CENTER;typeface=Typeface.DEFAULT_BOLD }
    private val point=Point()
    private var points:List<GeoPoint> = emptyList()
    fun submit(value:List<MapBubble>){bubbles=value;points=value.map { GeoPoint(it.lat,it.lon) }}
    override fun draw(canvas:Canvas,mapView:MapView,shadow:Boolean) {
        if(shadow)return
        val projection=mapView.projection
        for(i in bubbles.indices) {
            val bubble=bubbles[i];projection.toPixels(points[i],point)
            val radius=(if(bubble.district)23 else if(bubble.vehicle==null)20 else 17)*density
            val x=point.x.toFloat();val y=point.y.toFloat()
            if(x< -120*density || y< -60*density || x>canvas.width+120*density || y>canvas.height+60*density)continue
            fill.color=if(bubble.count==0)0xFF343438.toInt() else if(bubble.vehicle!=null)0xFFB21436.toInt() else 0xFFE51C46.toInt()
            canvas.drawCircle(x,y,radius,fill);canvas.drawCircle(x,y,radius,border)
            text.textSize=(if(bubble.vehicle==null)14 else 12)*density
            val title=if(bubble.vehicle!=null)bubble.label else if(bubble.count>99)"99+" else bubble.count.toString()
            canvas.drawText(title,x,y-(text.ascent()+text.descent())/2,text)
            if(bubble.district) {
                text.textSize=10*density
                val width=text.measureText(bubble.label)/2+6*density;val top=y+radius+3*density
                fill.color=0xE6000000.toInt()
                canvas.drawRoundRect(x-width,top,x+width,top+17*density,5*density,5*density,fill)
                canvas.drawText(bubble.label,x,top+12*density,text)
            }
        }
    }
    override fun onSingleTapConfirmed(event:MotionEvent,mapView:MapView):Boolean {
        for(i in bubbles.indices.reversed()) {
            mapView.projection.toPixels(points[i],point)
            val dx=point.x-event.x;val dy=point.y-event.y
            if(dx*dx+dy*dy>(32*density)*(32*density))continue
            val b=bubbles[i]
            if(b.vehicle!=null)select(b.vehicle) else {
                mapView.controller.setCenter(points[i])
                mapView.controller.setZoom(if(b.district)14.0 else (mapView.zoomLevelDouble+2).coerceAtMost(19.0))
            };return true
        };return false
    }
    override fun onDetach(mapView:MapView){submit(emptyList());super.onDetach(mapView)}
}
