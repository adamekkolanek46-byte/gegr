package pl.lapto.app.v2

import org.json.JSONArray
import kotlin.math.*

internal data class MapPosition(val lat:Double,val lon:Double)
internal data class MapWindow(val zoom:Double,val north:Double,val east:Double,val south:Double,val west:Double) {
    fun contains(lat:Double,lon:Double):Boolean {
        val latMargin=(north-south)*.12;val lonMargin=(east-west)*.12
        return lat in south-latMargin..north+latMargin && lon in west-lonMargin..east+lonMargin
    }
}
internal data class District(val name:String,val center:MapPosition,val rings:List<List<MapPosition>>) {
    private val bounds=rings.flatten().let { points->doubleArrayOf(points.minOf{it.lat},points.maxOf{it.lat},points.minOf{it.lon},points.maxOf{it.lon}) }
    fun contains(lat:Double,lon:Double):Boolean {
        if(lat !in bounds[0]..bounds[1] || lon !in bounds[2]..bounds[3])return false
        fun inRing(ring:List<MapPosition>):Boolean {
            var inside=false;var previous=ring.last()
            for(p in ring) {
                if((p.lat>lat)!=(previous.lat>lat) && lon<(previous.lon-p.lon)*(lat-p.lat)/(previous.lat-p.lat)+p.lon)inside=!inside
                previous=p
            };return inside
        }
        return inRing(rings.first()) && rings.drop(1).none { inRing(it) }
    }
    companion object {
        fun parse(json:String):List<District> {
            val a=JSONArray(json)
            return (0 until a.length()).map { i->val o=a.getJSONObject(i);val c=o.getJSONArray("center");val rs=o.getJSONArray("rings")
                District(o.getString("name"),MapPosition(c.getDouble(1),c.getDouble(0)),(0 until rs.length()).map { r->val points=rs.getJSONArray(r)
                    (0 until points.length()).map { j->val p=points.getJSONArray(j);MapPosition(p.getDouble(1),p.getDouble(0)) }
                })
            }
        }
    }
}
internal data class MapBubble(val id:String,val lat:Double,val lon:Double,val count:Int,val label:String="",val vehicle:LiveVehicle?=null,val district:Boolean=false)

/** Background-only engine: district membership is reused while panning and zooming. */
internal class ClusterEngine(private val districts:List<District>) {
    private var previous:List<LiveVehicle>?=null
    private var assigned:List<Pair<LiveVehicle,Int>> = emptyList()
    fun clusters(input:List<LiveVehicle>,window:MapWindow):List<MapBubble> {
        if(previous!==input) {
            assigned=input.map { v->v to districts.indexOfFirst { it.contains(v.lat,v.lon) } }
            previous=input
        }
        if(window.zoom<13.5) {
            val counts=IntArray(districts.size)
            val outside=ArrayList<LiveVehicle>()
            for((v,d) in assigned)if(d>=0)counts[d]++ else outside.add(v)
            val result=districts.mapIndexed { i,d->MapBubble("district:$i",d.center.lat,d.center.lon,counts[i],d.name,district=true) }.toMutableList()
            if(outside.isNotEmpty())result.add(MapBubble("outside",outside.map{it.lat}.average(),outside.map{it.lon}.average(),outside.size,"Poza Warszawą",district=true))
            return result
        }
        val visible=input.filter { window.contains(it.lat,it.lon) }
        if(window.zoom>=16.5 && visible.size<=400)return visible.map { v->MapBubble("${v.type}:${v.number}",v.lat,v.lon,1,v.line,v) }
        // A bounded number of spatial buckets; no thousands of Android Marker views.
        var cell=80.0
        fun group():Collection<List<LiveVehicle>> {
            val world=256.0*2.0.pow(floor(window.zoom))
            return visible.groupBy { v ->
                val sinLat=sin(Math.toRadians(v.lat.coerceIn(-85.0,85.0)))
                val x=(v.lon+180)/360*world
                val y=(.5-ln((1+sinLat)/(1-sinLat))/(4*PI))*world
                floor(x/cell).toLong() to floor(y/cell).toLong()
            }.values
        }
        var groups=group()
        while(groups.size>160){cell*=1.4;groups=group()}
        return groups.mapIndexed { i,group->
            val v=group.singleOrNull()
            MapBubble("cell:$i",group.map{it.lat}.average(),group.map{it.lon}.average(),group.size,v?.line.orEmpty(),v)
        }
    }
}
