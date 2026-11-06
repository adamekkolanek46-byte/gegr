package pl.lapto.app.v2

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray
import pl.lapto.app.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext

data class MonthStats(val month:String,val count:Int,val models:Int,val streak:Int,val days:List<Int>)
fun monthly(catches:List<VehicleCatch>):MonthStats {
    val fmt=DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
    val month=YearMonth.now()
    val dated=catches.mapNotNull { c -> runCatching { LocalDate.parse(c.date,fmt) to c }.getOrNull() }
    val current=dated.filter { YearMonth.from(it.first)==month }
    var run=0; var best=0; var prev:LocalDate?=null
    current.map { it.first }.distinct().sorted().forEach { d -> run=if(prev?.plusDays(1)==d) run+1 else 1;best=maxOf(best,run);prev=d }
    return MonthStats(month.format(DateTimeFormatter.ofPattern("LLLL",Locale("pl"))).uppercase(),current.size,current.map { it.second.type+it.second.model }.distinct().size,best,(1..month.lengthOfMonth()).map { day->current.count { it.first.dayOfMonth==day } })
}
@Composable fun StatsPanel(catches:List<VehicleCatch>) {
    val context=LocalContext.current
    val s=monthly(catches)
    Column(Modifier.fillMaxWidth().background(Color(0xFF171719),RoundedCornerShape(26.dp)).padding(22.dp)) {
        Text(s.month,color=Color.Gray,letterSpacing=3.sp,fontSize=12.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)){Text(s.count.toString(),fontSize=58.sp,color=Color.White,fontWeight=FontWeight.Bold);Text("POJAZDY",color=Color.Gray,fontSize=10.sp)}
            Column(Modifier.weight(1f)){Text("${s.models}",fontSize=26.sp,color=Color.White);Text("MODELE",color=Color.Gray,fontSize=10.sp);Text("${s.streak} dni",fontSize=24.sp,color=Color(0xFFC9B972));Text("NAJLEPSZA SERIA",color=Color.Gray,fontSize=10.sp)}
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().height(34.dp),verticalAlignment=androidx.compose.ui.Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            s.days.forEach { n -> Box(Modifier.weight(1f).height((3+31f*n/(s.days.maxOrNull()?.coerceAtLeast(1) ?: 1)).dp).background(if(n>0)Color(0xFFE51C46)else Color(0xFF303034),RoundedCornerShape(2.dp))) }
        }
        TextButton(onClick={Widgets.pin(context,CollectionWidget::class.java)}) { Text("DODAJ WIDŻET NA PULPIT",fontSize=11.sp) }
    }
}
class CollectionWidget:AppWidgetProvider(){ override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){Widgets.refresh(c)} }
class TrackingWidget:AppWidgetProvider(){ override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){Widgets.refresh(c)} }
object Widgets {
    fun pin(c:Context,cls:Class<*>) { val m=AppWidgetManager.getInstance(c);if(m.isRequestPinAppWidgetSupported)m.requestPinAppWidget(ComponentName(c,cls),null,null) else android.widget.Toast.makeText(c,"Przytrzymaj pulpit → Widżety → Łap To",android.widget.Toast.LENGTH_LONG).show() }
    fun track(c:Context,v:LiveVehicle,catalog:List<FleetModel>) {
        c.getSharedPreferences("tracking",0).edit().putString("title","#${v.number} · LINIA ${v.line}").putString("model",catalog.firstOrNull{it.type==v.type && v.number in it.numbers}?.name ?: v.type).putString("time",v.time).apply()
        refresh(c)
    }
    fun refresh(c:Context) {
        val manager=AppWidgetManager.getInstance(c)
        for(cls in listOf(CollectionWidget::class.java,TrackingWidget::class.java)) {
            val ids=manager.getAppWidgetIds(ComponentName(c,cls));if(ids.isEmpty())continue
            val collection=cls==CollectionWidget::class.java
            val rv=RemoteViews(c.packageName,R.layout.lap_widget)
            val intent=Intent(c,MainActivity::class.java).putExtra("tab",if(collection)0 else 3)
            rv.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,if(collection)10 else 11,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            if(collection) {
                val a=runCatching{JSONArray(c.getSharedPreferences("album_v2",0).getString("items","[]"))}.getOrDefault(JSONArray())
                val cs=(0 until a.length()).mapNotNull { i->runCatching { val o=a.getJSONObject(i); VehicleCatch(o.getString("n"),o.getString("t"),o.getString("d"),"",Rarity.COMMON,o.optString("m"),"") }.getOrNull() }
                val s=monthly(cs)
                rv.setTextViewText(R.id.widget_label,s.month+" · KOLEKCJA")
                rv.setTextViewText(R.id.widget_value,"${s.count} pojazdów")
                rv.setTextViewText(R.id.widget_detail,"${s.models} modeli    ·    Seria: ${s.streak} dni")
                rv.setTextViewText(R.id.widget_footer,"Otwórz album Łap To")
                rv.setProgressBar(R.id.widget_progress,100,(cs.size*100/3095).coerceIn(0,100),false)
            } else {
                val p=c.getSharedPreferences("tracking",0)
                rv.setTextViewText(R.id.widget_label,p.getString("title","WYBIERZ POJAZD NA MAPIE"))
                rv.setTextViewText(R.id.widget_value,p.getString("model","Łap To"))
                rv.setTextViewText(R.id.widget_detail,"Sygnał GPS: "+p.getString("time","—"))
                rv.setTextViewText(R.id.widget_footer,"Ostatnia pozycja · dotknij, aby odświeżyć mapę")
                rv.setViewVisibility(R.id.widget_progress,android.view.View.GONE)
            }
            manager.updateAppWidget(ids,rv)
        }
    }
}
