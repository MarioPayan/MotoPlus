package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** A fill-up. Its odometer is a real reading, so every fill-up also corrects the km estimate. */
@Serializable
data class FuelEntry(
    val id: String,
    val date: String,
    val km: Int,
    val liters: Double,
    val total: Double? = null,
    /** Tank filled to the top: consumption is measured between two full fill-ups. */
    val full: Boolean = true,
    val at: Long = 0,
)

/** Records a fill-up and its odometer reading. */
fun Bike.withFuel(f: FuelEntry, today: LocalDate): Bike = withReading(f.km, today, f.at).copy(fuel = fuel + f)

data class FuelStats(val kmPerLiter: Double?, val costPerKm: Double?, val pricePerLiter: Double?)

/**
 * Consumption over every full-to-full span: distance between two full fill-ups divided by the liters
 * put in after the first one. Cost per km uses the same spans, only where every fill-up has a price.
 */
fun fuelStats(entries: List<FuelEntry>): FuelStats {
    val sorted = entries.sortedWith(compareBy({ it.km }, { it.date }))
    var km = 0.0; var liters = 0.0; var costKm = 0.0; var cost = 0.0
    var start: FuelEntry? = null
    var spanLiters = 0.0; var spanCost: Double? = 0.0
    for (e in sorted) {
        if (start != null) {
            spanLiters += e.liters
            spanCost = if (spanCost != null && e.total != null) spanCost + e.total else null
        }
        if (e.full) {
            if (start != null && e.km > start.km && spanLiters > 0) {
                km += e.km - start.km; liters += spanLiters
                spanCost?.let { cost += it; costKm += e.km - start.km }
            }
            start = e; spanLiters = 0.0; spanCost = 0.0
        }
    }
    val priced = sorted.filter { it.total != null && it.liters > 0 }
    return FuelStats(
        kmPerLiter = if (liters > 0) km / liters else null,
        costPerKm = if (costKm > 0) cost / costKm else null,
        pricePerLiter = priced.lastOrNull()?.let { it.total!! / it.liters },
    )
}

/** Km ridden since the first day of [today]'s month, from the odometer estimate and the readings. */
fun kmThisMonth(bike: Bike, today: LocalDate): Int {
    val start = today.withDayOfMonth(1).toString()
    val before = bike.readings.filter { it.date < start }.maxByOrNull { it.date }
    val base = before?.km ?: bike.readings.minByOrNull { it.date }?.km ?: return 0
    return (estimate(bike, today).km - base).toInt().coerceAtLeast(0)
}

/** GPS km per day for the 7 days ending today, oldest first. */
fun weekRides(bike: Bike, today: LocalDate): List<Double> = (6 downTo 0).map { back ->
    val d = today.minusDays(back.toLong()).toString()
    bike.rides.filter { it.date == d }.sumOf { it.km }
}

/** Removes a fill-up and the odometer reading it added. */
fun Bike.deleteFuel(id: String): Bike {
    val f = fuel.firstOrNull { it.id == id } ?: return this
    val r = readings.firstOrNull { it.km == f.km && it.at == f.at && it.date == f.date }
    return copy(fuel = fuel - f).let { if (r != null && readings.size > 1) it.copy(readings = readings - r) else it }
}

/** Removes a mistyped reading; the first one stays so the bike always has an odometer. */
fun Bike.deleteReading(r: Reading): Bike = if (readings.size > 1) copy(readings = readings - r) else this

/** A typed odometer this far above the estimate is probably a typo worth questioning. */
fun suspiciousJump(km: Int, estimate: Double) = km - estimate > 5000

/** Monthly distance from the rider's readings, used as the instrument's progress scale. */
fun monthlyAverage(bike: Bike, today: LocalDate): Int {
    val first = bike.readings.minBy { it.date }
    val span = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(first.date), today).coerceAtLeast(1)
    return ((estimate(bike, today).km - first.km).coerceAtLeast(0.0) / span * 30).toInt().coerceAtLeast(1)
}
