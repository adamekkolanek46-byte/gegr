package pl.metromistrz.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import kotlin.math.*
import kotlin.random.Random
import java.io.File
import java.util.Locale

data class Station(val p: GeoPoint, var name: String, val cost: Double, var waiting: Int = 0)
data class Stock(val name: String, val price: Double, val capacity: Int, val speed: Double, val upkeep: Double)

class MainActivity : Activity() {
    private lateinit var map: MapView
    private lateinit var moneyText: TextView
    private lateinit var info: TextView
    private lateinit var hint: TextView
    private lateinit var play: Button
    private val stations = mutableListOf<Station>()
    private val stocks = listOf(Stock("81 · używany / stary", 8.0, 500, 65.0, 110.0), Stock("Metropolis · odnowiony", 20.0, 700, 75.0, 85.0), Stock("Inspiro · nowoczesny", 38.0, 900, 80.0, 65.0))
    private var cash = 180.0
    private var owned = -1
    private var onboard = 0
    private var transported = 0
    private var running = false
    private var build = false
    private var speed = 1
    private var target = 0
    private var direction = 1
    private var elapsed = 0.0
    private var travel = 1.0
    private var dwell = 0.0
    private var clock = 21600.0
    private var demandClock = 0.0
    private var train: GeoPoint? = null
    private var lastEvent = "Kup skład, zbuduj minimum dwie stacje."
    private val handler = Handler(Looper.getMainLooper())
    private val ink = Color.rgb(15,22,34)
    private val red = Color.rgb(240,77,83)
    private val tick = object : Runnable {
        override fun run() { if (running) simulate(.25 * speed); handler.postDelayed(this, 250) }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(s: String, size: Float = 13f, color: Int = Color.WHITE) = TextView(this).apply { text=s; textSize=size; setTextColor(color); setPadding(dp(12),dp(6),dp(12),dp(6)) }
    private fun button(s: String, action: () -> Unit) = Button(this).apply { text=s; textSize=11f; isAllCaps=false; setOnClickListener { action() } }
    private fun row() = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun note(s: String) { hint.text=s }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val config=Configuration.getInstance()
        config.userAgentValue="MetroMistrzWorld/2.0 (Android)"
        config.osmdroidBasePath=File(filesDir,"maps")
        config.osmdroidTileCache=File(cacheDir,"tiles")
        restore()
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(ink) }
        root.fitsSystemWindows=true
        setContentView(root)
        root.addView(label("M  /  METRO MISTRZ ŚWIAT",20f))
        moneyText=label("");root.addView(moneyText)
        val searchRow=row()
        val search=EditText(this).apply { hint="Miasto lub lat, lon"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); textSize=14f; setSingleLine() }
        searchRow.addView(search,LinearLayout.LayoutParams(0,dp(46),1f))
        searchRow.addView(button("Szukaj") { locate(search.text.toString()) })
        searchRow.addView(button("⚙") { menu() });root.addView(searchRow)
        val frame=FrameLayout(this)
        map=MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK);setMultiTouchControls(true)
            minZoomLevel=3.0;maxZoomLevel=19.0
            controller.setZoom(14.0);controller.setCenter(stations.firstOrNull()?.p ?: GeoPoint(52.2297,21.0122))
        }
        frame.addView(map)
        val attribution=label("© OpenStreetMap contributors",10f,ink).apply { setBackgroundColor(Color.WHITE);setOnClickListener { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright"))) } }
        frame.addView(attribution,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.LEFT))
        root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        map.overlays.add(MapEventsOverlay(object:MapEventsReceiver {
            override fun singleTapConfirmedHelper(p:GeoPoint):Boolean {
                val s=stations.minByOrNull { it.p.distanceToAsDouble(p) }
                if(s!=null && s.p.distanceToAsDouble(p)<120 && map.zoomLevelDouble>=14) stationDialog(s)
                return false
            }
            override fun longPressHelper(p:GeoPoint):Boolean { if(build && !running) propose(p);else note("Włącz „Buduj”, potem przytrzymaj miejsce na mapie.");return true }
        }))
        map.overlays.add(object:Overlay(){
            override fun draw(canvas:Canvas,m:MapView,shadow:Boolean) {
                if(shadow)return
                val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                val pts=stations.map { m.projection.toPixels(it.p,null) }
                if(pts.size>1) {
                    val path=Path();path.moveTo(pts[0].x.toFloat(),pts[0].y.toFloat())
                    for(i in 1 until pts.size-1){
                        val a=pts[i-1];val b=pts[i];val c=pts[i+1]
                        path.lineTo(b.x+(a.x-b.x)*.22f,b.y+(a.y-b.y)*.22f)
                        path.quadTo(b.x.toFloat(),b.y.toFloat(),b.x+(c.x-b.x)*.22f,b.y+(c.y-b.y)*.22f)
                    }
                    path.lineTo(pts.last().x.toFloat(),pts.last().y.toFloat())
                    paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.color=Color.WHITE;paint.strokeWidth=dp(9).toFloat();canvas.drawPath(path,paint)
                    paint.color=red;paint.strokeWidth=dp(5).toFloat();canvas.drawPath(path,paint)
                }
                paint.style=Paint.Style.FILL
                pts.forEachIndexed { i,p ->
                    paint.color=red;canvas.drawCircle(p.x.toFloat(),p.y.toFloat(),dp(8).toFloat(),paint)
                    paint.color=Color.WHITE;canvas.drawCircle(p.x.toFloat(),p.y.toFloat(),dp(4).toFloat(),paint)
                    if(m.zoomLevelDouble>=13){ paint.textSize=dp(11).toFloat();paint.typeface=Typeface.DEFAULT_BOLD;paint.setShadowLayer(3f,0f,0f,Color.BLACK);canvas.drawText(stations[i].name+" · "+stations[i].waiting,p.x+dp(12).toFloat(),p.y-dp(8).toFloat(),paint);paint.clearShadowLayer() }
                }
                train?.let { val p=m.projection.toPixels(it,null);paint.color=ink;canvas.drawCircle(p.x.toFloat(),p.y.toFloat(),dp(11).toFloat(),paint);paint.color=Color.WHITE;paint.textSize=dp(12).toFloat();canvas.drawText("M",p.x-dp(5).toFloat(),p.y+dp(4).toFloat(),paint) }
            }
        })
        hint=label("Przesuwaj mapę palcem, przybliżaj dwoma. Mapa wymaga internetu.",11f,Color.LTGRAY);root.addView(hint)
        info=label("",12f);root.addView(info)
        val actions=row()
        val buildButton=button("Buduj"){}
        buildButton.setOnClickListener { if(running)note("Zatrzymaj ruch przed budową.") else {build=!build;buildButton.text=if(build)"Buduj ✓" else "Buduj";note(if(build)"Przytrzymaj mapę: wycena stacji i tunelu." else "Przesuwanie mapy. Dotknij stacji, aby zobaczyć pasażerów.")} }
        actions.addView(buildButton,LinearLayout.LayoutParams(0,dp(48),1f))
        actions.addView(button("Tabor"){fleet()},LinearLayout.LayoutParams(0,dp(48),1f))
        play=button("▶ Start"){toggle()};actions.addView(play,LinearLayout.LayoutParams(0,dp(48),1f))
        val fast=button("1×"){}
        fast.setOnClickListener {speed=when(speed){1->5;5->20;else->1};fast.text="${speed}×"}
        actions.addView(fast,LinearLayout.LayoutParams(0,dp(48),.7f));root.addView(actions)
        update();handler.post(tick)
    }
    private fun locate(q:String) {
        val coord=q.split(",").map { it.trim().toDoubleOrNull() }
        if(coord.size==2 && coord.all { it!=null } && coord[0]!! in -85.0..85.0 && coord[1]!! in -180.0..180.0){map.controller.setCenter(GeoPoint(coord[0]!!,coord[1]!!));map.controller.setZoom(14.0);return}
        if(q.isBlank())return
        note("Szukam: $q…")
        Thread {
            try {
                @Suppress("DEPRECATION") val result=Geocoder(this,Locale.getDefault()).getFromLocationName(q,1)
                runOnUiThread { if(isDestroyed)return@runOnUiThread;val a=result?.firstOrNull();if(a!=null){map.controller.setCenter(GeoPoint(a.latitude,a.longitude));map.controller.setZoom(14.0);note("Obszar: $q")}else note("Brak wyniku. Przesuń mapę albo wpisz lat, lon.") }
            }catch(e:Exception){runOnUiThread {note("Wyszukiwanie niedostępne. Możesz przesuwać mapę lub wpisać współrzędne.")}}
        }.start()
    }
    private fun propose(p:GeoPoint){
        if(map.zoomLevelDouble<13){note("Przybliż mapę do ulic przed budową.");return}
        if(stations.any { it.p.distanceToAsDouble(p)<650 }){note("Za blisko innej stacji: minimum 650 m.");return}
        val km=stations.lastOrNull()?.p?.distanceToAsDouble(p)?.div(1000) ?: 0.0
        if(km>3.2){note("Za długi odcinek: maksymalnie 3,2 km między stacjami.");return}
        if(stations.size>=2){
            val a=stations[stations.size-2].p;val b=stations.last().p
            val turn=abs(((b.bearingTo(p)-a.bearingTo(b)+540)%360)-180)
            if(turn>60){note("Za ostry skręt: zmiana kierunku maks. 60°.");return}
        }
        val cost=28+km*42
        if(cash<cost){note("Brak środków: potrzeba ${fmt(cost)} mln zł.");return}
        val name=EditText(this).apply {setText("Stacja ${stations.size+1}");setSingleLine()}
        AlertDialog.Builder(this).setTitle("Budowa · ${fmt(cost)} mln zł").setMessage("Stacja 28 mln + tunel 42 mln/km.\nOdcinek: ${fmt(km)} km.\nPo budowie: ${fmt(cash-cost)} mln zł.\nKoszty są zbalansowane dla gry.").setView(name).setNegativeButton("Anuluj",null).setPositiveButton("Buduj"){_,_->
            if(cash>=cost){cash-=cost;stations.add(Station(p,name.text.toString().take(32).ifBlank{"Stacja ${stations.size+1}"},cost,Random.nextInt(8,30)));resetTrain();save();update();note("Stacja gotowa. Przytrzymaj mapę, żeby przedłużyć linię.")}
        }.show()
    }
    private fun stationDialog(s:Station){
        val input=EditText(this).apply {setText(s.name)}
        AlertDialog.Builder(this).setTitle(s.name).setMessage("Oczekujących: ${s.waiting}\nPopyt jest symulacją gry, nie rzeczywistymi danymi.").setView(input).setPositiveButton("Zmień nazwę"){_,_->s.name=input.text.toString().take(32).ifBlank{s.name};save();map.invalidate()}.setNegativeButton("Zamknij",null).show()
    }
    private fun fleet(){
        val items=stocks.mapIndexed { i,s -> "${if(i==owned)"✓ " else ""}${s.name}\n${s.price.toInt()} mln zł · ${s.capacity} osób · ${s.speed.toInt()} km/h\nUtrzymanie ${s.upkeep.toInt()} zł/min" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Tabor · jeden skład na linię").setItems(items){_,i->
            if(running){note("Zatrzymaj ruch, aby wymienić skład.");return@setItems}
            if(i==owned)return@setItems
            val refund=if(owned>=0)stocks[owned].price*.5 else 0.0
            val cost=stocks[i].price-refund
            AlertDialog.Builder(this).setTitle(stocks[i].name).setMessage("Koszt po odsprzedaży obecnego składu: ${fmt(cost)} mln zł.").setNegativeButton("Anuluj",null).setPositiveButton("Kup"){_,_->
                if(cash>=cost){cash-=cost;owned=i;resetTrain();save();update()}else note("Nie stać Cię na ten skład.")
            }.show()
        }.setNegativeButton("Zamknij",null).show()
    }
    private fun toggle(){
        if(!running && (owned<0 || stations.size<2)){note("Potrzebujesz dwóch stacji i kupionego składu.");return}
        running=!running;build=false;play.text=if(running)"Ⅱ Pauza" else "▶ Start";save();update()
    }
    private fun resetTrain(){target=0;direction=1;elapsed=0.0;travel=1.0;dwell=0.0;onboard=0;train=stations.firstOrNull()?.p}
    private fun simulate(dt:Double){
        clock+=dt;demandClock+=dt
        val stock=stocks[owned]
        cash-=stock.upkeep*dt/60/1000000
        if(cash<=0){cash=0.0;running=false;play.text="▶ Start";note("Brak pieniędzy na ruch. Odbierz dotację w menu.");save();update();return}
        if(demandClock>=60){demandClock%=60;stations.forEach {it.waiting=(it.waiting+Random.nextInt(3,14)).coerceAtMost(2000)}}
        if(dwell>0){dwell-=dt;if(dwell<=0){if(target==stations.lastIndex)direction=-1;if(target==0)direction=1;target+=direction;elapsed=0.0;travel=stations[target-direction].p.distanceToAsDouble(stations[target].p)/(stock.speed/3.6)*1.35};update();return}
        elapsed+=dt
        if(elapsed>=travel){
            train=stations[target].p
            val leave=if(target==0 || target==stations.lastIndex)onboard else Random.nextInt(0,onboard+1)/2
            onboard-=leave;transported+=leave
            val enter=min(stations[target].waiting,stock.capacity-onboard);stations[target].waiting-=enter;onboard+=enter;cash+=enter*4.5/1000000
            lastEvent="${stations[target].name}  ↑ $enter wsiadło  ↓ $leave wysiadło"
            dwell=25.0;save()
        }else if(target-direction in stations.indices){
            val a=stations[target-direction].p;val b=stations[target].p;val t=(elapsed/travel).coerceIn(0.0,1.0)
            train=GeoPoint(a.latitude+(b.latitude-a.latitude)*t,a.longitude+(b.longitude-a.longitude)*t)
        }
        update()
    }
    private fun update(){
        val km=stations.zipWithNext {a,b->a.p.distanceToAsDouble(b.p)/1000}.sum()
        val h=(clock/3600).toInt()%24;val m=(clock/60).toInt()%60
        moneyText.text="BUDŻET  ${fmt(cash)} mln zł     %02d:%02d".format(h,m)
        val stock=stocks.getOrNull(owned)
        info.text="M1 · ${stations.size} stacji · ${fmt(km)} km\n${stock?.name ?: "Brak składu"} · $onboard/${stock?.capacity ?: 0} osób\n$lastEvent\nDowiezieni: $transported · bilet 4,50 zł${if(dwell>0)" · postój ${dwell.toInt()} s" else ""}"
        map.invalidate()
    }
    private fun menu(){
        AlertDialog.Builder(this).setTitle("Zarządzanie").setItems(arrayOf("Wróć do swojej linii","Odbierz dotację za 1000 dowiezionych","Usuń ostatnią stację · zwrot 50%","Zasady i zapis","Nowa gra")){_,i->when(i){
            0->stations.firstOrNull()?.let {map.controller.setCenter(it.p);map.controller.setZoom(14.0)}
            1->{val prefs=getPreferences(0);val grants=prefs.getInt("grants",0);if(transported/1000>grants){cash+=35;prefs.edit().putInt("grants",grants+1).apply();save();update();note("Dotacja +35 mln zł.")}else note("Kolejna dotacja po ${(grants+1)*1000} dowiezionych pasażerach.")}
            2->if(!running && stations.isNotEmpty())AlertDialog.Builder(this).setMessage("Usunąć ${stations.last().name}? Zwrot połowy kosztu.").setNegativeButton("Anuluj",null).setPositiveButton("Usuń"){_,_->cash+=stations.removeAt(stations.lastIndex).cost*.5;resetTrain();save();update()}.show() else note("Zatrzymaj ruch przed rozbiórką.")
            3->AlertDialog.Builder(this).setTitle("Jak grać").setMessage("Start: 180 mln zł. Kup używany skład, zbuduj krótką linię i zarabiaj na biletach. Dotacja 35 mln co 1000 dowiezionych.\n\nMapa świata: przesuwanie i zoom dwoma palcami. Buduj → przytrzymaj mapę → zatwierdź cenę. Stacje min. 650 m, odcinki do 3,2 km, skręt do 60°.\n\nZapis automatyczny. Symulacja pauzuje po zamknięciu. Pasażerowie, osiągi i ekonomia są uproszczonym modelem gry. Mapa ulic jest prawdziwa i wymaga internetu.").setPositiveButton("OK",null).show()
            4->AlertDialog.Builder(this).setMessage("Usunąć aktualną grę i zacząć z 180 mln zł?").setNegativeButton("Anuluj",null).setPositiveButton("Nowa gra"){_,_->running=false;stations.clear();cash=180.0;owned=-1;transported=0;clock=21600.0;getPreferences(0).edit().remove("grants").apply();resetTrain();play.text="▶ Start";save();update()}.show()
        }}.setNegativeButton("Zamknij",null).show()
    }
    private fun save(){
        val arr=JSONArray();stations.forEach {s->arr.put(JSONObject().put("lat",s.p.latitude).put("lon",s.p.longitude).put("name",s.name).put("cost",s.cost).put("waiting",s.waiting))}
        getPreferences(0).edit().putString("stations",arr.toString()).putFloat("cash",cash.toFloat()).putInt("owned",owned).putInt("transported",transported).putFloat("clock",clock.toFloat()).putInt("onboard",onboard).putInt("target",target).putInt("direction",direction).putFloat("elapsed",elapsed.toFloat()).putFloat("travel",travel.toFloat()).putFloat("dwell",dwell.toFloat()).apply()
    }
    private fun restore(){
        val p=getPreferences(0);cash=p.getFloat("cash",180f).toDouble();owned=p.getInt("owned",-1);transported=p.getInt("transported",0);clock=p.getFloat("clock",21600f).toDouble()
        try{val a=JSONArray(p.getString("stations","[]"));for(i in 0 until a.length()){val s=a.getJSONObject(i);stations.add(Station(GeoPoint(s.getDouble("lat"),s.getDouble("lon")),s.getString("name"),s.getDouble("cost"),s.optInt("waiting")))}}catch(e:Exception){stations.clear()}
        onboard=p.getInt("onboard",0);target=p.getInt("target",0);direction=p.getInt("direction",1);elapsed=p.getFloat("elapsed",0f).toDouble();travel=p.getFloat("travel",1f).toDouble();dwell=p.getFloat("dwell",0f).toDouble()
        train=stations.getOrNull(target)?.p
    }
    override fun onResume(){super.onResume();map.onResume()}
    override fun onPause(){running=false;play.text="▶ Start";save();map.onPause();super.onPause()}
    override fun onDestroy(){handler.removeCallbacks(tick);map.onDetach();super.onDestroy()}
    private fun fmt(n:Double)=String.format(Locale.US,"%.2f",n)
}
