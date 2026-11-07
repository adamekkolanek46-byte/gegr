package pl.lapto.app.v2

import java.text.Normalizer
import java.util.Locale

data class FleetModel(val id: String, val type: String, val name: String, val numbers: List<String>, val years: List<Int>, val operator: String, val depot: String, val length: Int, val power: String, val seats: Int, val capacity: Int, val floor: String, val brand: String = "Inne")

internal fun groupFleet(input: List<FleetModel>): List<FleetModel> {
    fun normalized(s:String)=Normalizer.normalize(s,Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    fun common(values:List<Int>)=values.distinct().singleOrNull() ?: 0
    fun labels(values:List<String>)=values.filter { it.isNotBlank() }.distinct().joinToString(" / ")
    return input.groupBy { listOf(normalized(it.type),normalized(it.brand),normalized(it.name)) }.values.map { variants ->
        val first=variants.first()
        first.copy(id=variants.map { it.id }.sorted().joinToString("+"),
            numbers=variants.flatMap { it.numbers }.distinct().sortedWith(compareBy<String> { it.takeWhile(Char::isDigit).toLongOrNull() ?: Long.MAX_VALUE }.thenBy { it }),
            years=variants.flatMap { it.years }.distinct().sorted(),
            operator=labels(variants.map { it.operator }),depot=labels(variants.map { it.depot }),
            length=common(variants.map { it.length }),seats=common(variants.map { it.seats }),capacity=common(variants.map { it.capacity }),
            power=labels(variants.map { it.power }),floor=labels(variants.map { it.floor }))
    }
}
