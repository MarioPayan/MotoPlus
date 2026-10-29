package com.kaze.motoplus.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

@kotlinx.serialization.Serializable
enum class Status { ok, warn, bad, none }

enum class Measure { km, days }

/** How far a part is through its interval. [left] counts in [measure] and goes negative once overdue; null measure = never recorded. */
data class PartState(val status: Status, val used: Double, val measure: Measure?, val left: Int)

data class ZoneState(val status: Status, val worst: Double, val parts: List<Pair<Part, PartState>>)

enum class EstimateBasis { rides, todayReading, lastReading, average }

/** [rides] counts GPS rides since the last reading; [kmPerDay] is the rider's average when [basis] is average. */
data class OdoEstimate(val km: Double, val margin: Int, val basis: EstimateBasis, val gpsSinceReading: Double, val rides: Int = 0, val kmPerDay: Double = 0.0)

private fun daysBetween(from: String, to: LocalDate) =
    max(0L, ChronoUnit.DAYS.between(LocalDate.parse(from), to)).toDouble()

/**
 * Odometer right now: last real reading plus GPS rides since then (times the calibration factor),
 * or, with no rides, the rider's average km/day projected from the readings.
 */
fun estimate(bike: Bike, today: LocalDate): OdoEstimate {
    val last = bike.readings.maxWith(compareBy({ it.date }, { it.at }))
    val after = bike.rides.filter { it.after(last) }
    if (after.isNotEmpty()) {
        val gps = after.sumOf { it.km }
        return OdoEstimate(
            km = last.km + gps * bike.gpsFactor,
            margin = max(5, (gps * 0.02).roundToInt()),
            basis = EstimateBasis.rides,
            gpsSinceReading = gps,
            rides = after.size,
        )
    }
    val first = bike.readings.minBy { it.date }
    val span = daysBetween(first.date, LocalDate.parse(last.date))
    val d = daysBetween(last.date, today)
    if (span < 1) return OdoEstimate(last.km.toDouble(), 0, if (d < 1) EstimateBasis.todayReading else EstimateBasis.lastReading, 0.0)
    val rate = (last.km - first.km) / span
    return OdoEstimate(
        km = last.km + rate * d,
        margin = (rate * d * 0.3).roundToInt(),
        basis = if (d < 1) EstimateBasis.todayReading else EstimateBasis.average,
        gpsSinceReading = 0.0,
        kmPerDay = rate,
    )
}

fun partState(p: Part, odo: Double, today: LocalDate): PartState {
    if (p.lastKm == null && p.lastDate == null) return PartState(Status.none, 1.0, null, 0)
    val byKm = if (p.km != null && p.lastKm != null) (odo - p.lastKm) / p.km else 0.0
    val byTime = if (p.days != null && p.lastDate != null) daysBetween(p.lastDate, today) / p.days else 0.0
    val used = max(byKm, byTime)
    val status = when {
        used >= 1 -> Status.bad
        used >= 0.75 -> Status.warn
        else -> Status.ok
    }
    return if (byKm >= byTime && p.km != null && p.lastKm != null) {
        PartState(status, used, Measure.km, (p.km - (odo - p.lastKm)).roundToInt())
    } else {
        PartState(status, used, Measure.days, (p.days!! - daysBetween(p.lastDate!!, today)).roundToInt())
    }
}

private val rank = mapOf(Status.ok to 0, Status.warn to 1, Status.bad to 2, Status.none to 3)

fun zoneStates(bike: Bike, odo: Double, today: LocalDate): Map<String, ZoneState> =
    Zone.entries.map { it.id }.associateWith { z ->
        val parts = bike.parts.filter { it.zone == z }.map { it to partState(it, odo, today) }
            .sortedByDescending { it.second.used }
        ZoneState(
            status = parts.maxByOrNull { rank.getValue(it.second.status) }?.second?.status ?: Status.ok,
            worst = parts.maxOfOrNull { it.second.used } ?: 0.0,
            parts = parts,
        )
    }

/** 0–100: an ok part counts 1, a part coming due counts half, overdue or unknown counts 0. */
fun health(bike: Bike, odo: Double, today: LocalDate): Int {
    if (bike.parts.isEmpty()) return 100
    val score = bike.parts.sumOf {
        when (partState(it, odo, today).status) { Status.ok -> 1.0; Status.warn -> 0.5; else -> 0.0 }
    }
    return (100 * score / bike.parts.size).roundToInt()
}

/** Records a real odometer reading and, when GPS rides covered the gap, recalibrates the GPS factor. */
fun Bike.withReading(km: Int, today: LocalDate, now: Long = 0): Bike {
    val est = estimate(this, today)
    val last = readings.maxWith(compareBy({ it.date }, { it.at }))
    val factor = if (est.gpsSinceReading > 0) {
        val f = (km - last.km) / est.gpsSinceReading
        if (f in 0.85..1.15) f else gpsFactor
    } else gpsFactor
    return copy(readings = readings + Reading(today.toString(), km, now), gpsFactor = factor)
}

/** Fixes a mistyped latest reading (and the fill-up that brought it): it is replaced, never added on top of. */
fun Bike.replaceLastReading(km: Int, today: LocalDate, now: Long = 0): Bike {
    val last = readings.maxWith(compareBy({ it.date }, { it.at }))
    return copy(readings = readings - last + Reading(today.toString(), km, now), fuel = fuel.map { if (it.km == last.km && it.at == last.at) it.copy(km = km, at = now) else it })
}

fun Bike.markDone(partId: String, km: Int, today: LocalDate) =
    addRecord(ServiceRecord(java.util.UUID.randomUUID().toString(), partId, today.toString(), km))

/** A rider-set interval; at least one of [km] or [days] must be set. */
fun Bike.withInterval(partId: String, km: Int?, days: Int?): Bike {
    require(km != null || days != null)
    return copy(parts = parts.map { if (it.id == partId) it.copy(km = km, days = days) else it })
}

fun Bike.suggested(partId: String): Part? = defaultParts(category, drive, cooling).firstOrNull { it.id == partId }

fun fmtKm(n: Int): String = "%,d".format(java.util.Locale.ROOT, n).replace(',', '.')

/**
 * Parts that got worse since we last told the rider (ok → coming due → overdue), plus the new baseline.
 * Never-recorded parts are not news: the rider sees them in red already.
 */
fun dueAlerts(bike: Bike, odo: Double, today: LocalDate): Pair<List<Pair<Part, PartState>>, Map<String, Status>> {
    val now = bike.parts.associate { it.id to partState(it, odo, today) }
    val sev = mapOf(Status.ok to 0, Status.warn to 1, Status.bad to 2)
    val worse = bike.parts.mapNotNull { p ->
        val s = now.getValue(p.id)
        if (p.snoozed(today)) return@mapNotNull null
        val before = sev[bike.notified[p.id] ?: Status.ok] ?: 0
        val after = sev[s.status] ?: return@mapNotNull null
        if (after > before) p to s else null
    }.sortedByDescending { it.second.used }
    return worse to now.mapValues { (id, state) -> if (bike.parts.first { it.id == id }.snoozed(today)) bike.notified[id] ?: Status.ok else state.status }
}

/** Shared health meter bands. */
fun healthBand(score: Int): Status = when { score >= 80 -> Status.ok; score >= 50 -> Status.warn; else -> Status.bad }

fun Part.snoozed(today: LocalDate) = snoozedUntil?.let { runCatching { LocalDate.parse(it).isAfter(today) }.getOrDefault(false) } ?: false

fun formatKm(n: Int, locale: java.util.Locale): String = java.text.NumberFormat.getIntegerInstance(locale).format(n)
