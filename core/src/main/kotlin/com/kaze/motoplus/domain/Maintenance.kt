package com.kaze.motoplus.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

@kotlinx.serialization.Serializable
enum class Status { ok, warn, bad, none }

enum class Measure { km, days }

/** How far a part is through its interval. [left] counts in [measure] and goes negative once overdue; null measure = never recorded. */
data class PartState(val status: Status, val used: Double, val measure: Measure?, val left: Int, val problem: PartProblem? = null)

/** [parts] are the zone's watched parts, which set its status; [all] adds the unwatched ones, shown dimmed. No watched part leaves the zone ok and [parts] empty. */
data class ZoneState(val status: Status, val worst: Double, val parts: List<Pair<Part, PartState>>, val all: List<Pair<Part, PartState>> = parts)

enum class EstimateBasis { rides, todayReading, lastReading, average }

/**
 * [rides] counts GPS rides since the last reading; [kmPerDay] is the rider's average when [basis] is average.
 * [lastReadingKm] is the latest real reading the estimate starts from.
 */
data class OdoEstimate(val km: Double, val margin: Int, val basis: EstimateBasis, val gpsSinceReading: Double, val rides: Int = 0, val kmPerDay: Double = 0.0, val estimatedSinceReading: Double = 0.0, val lastReadingKm: Int = 0)

/**
 * Whole km of the shown odometer ([OdoEstimate.km] truncated, as every screen shows it) that come from projecting the
 * rider's daily average, not from a reading or a recorded ride; 0 when nothing is projected.
 */
val OdoEstimate.projectedKm: Int get() = if (basis == EstimateBasis.average) km.toInt() - lastReadingKm else 0

/** Part of the shown odometer is the daily-average projection, so screens mark it as estimated. */
val OdoEstimate.isProjected: Boolean get() = projectedKm != 0

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
        // The GPS factor corrects GPS error only; estimated km come from a speed the rider set and are far less sure.
        val gps = after.filter { !it.estimated }.sumOf { it.km }
        val guessed = after.filter { it.estimated }.sumOf { it.km }
        return OdoEstimate(
            km = last.km + gps * bike.gpsFactor + guessed,
            margin = max(5, (gps * 0.02 + guessed * 0.3).roundToInt()),
            basis = EstimateBasis.rides,
            gpsSinceReading = gps,
            rides = after.size,
            estimatedSinceReading = guessed,
            lastReadingKm = last.km,
        )
    }
    val first = bike.readings.minBy { it.date }
    val span = daysBetween(first.date, LocalDate.parse(last.date))
    val d = daysBetween(last.date, today)
    if (span < 1) return OdoEstimate(last.km.toDouble(), 0, if (d < 1) EstimateBasis.todayReading else EstimateBasis.lastReading, 0.0, lastReadingKm = last.km)
    val rate = (last.km - first.km) / span
    return OdoEstimate(
        km = last.km + rate * d,
        margin = (rate * d * 0.3).roundToInt(),
        basis = if (d < 1) EstimateBasis.todayReading else EstimateBasis.average,
        gpsSinceReading = 0.0,
        kmPerDay = rate,
        lastReadingKm = last.km,
    )
}

/**
 * When a part is coming due: at most [kmPct] of its km interval or [timePct] of its time interval left, whichever
 * comes first. One setting for every part; it lives in [Preferences].
 */
data class SoonThreshold(val kmPct: Int = 10, val timePct: Int = 10) {
    init { require(kmPct in RANGE && timePct in RANGE) }

    companion object { val RANGE = 1..50 }
}

/** Above any real interval use, so reported parts sort first wherever parts are ranked by urgency. */
private const val REPORTED_USED = 1e6

fun partState(p: Part, odo: Double, today: LocalDate, soon: SoonThreshold = SoonThreshold()): PartState {
    val state = intervalState(p, odo, today, soon)
    return if (p.problem != null) state.copy(status = Status.bad, used = REPORTED_USED, problem = p.problem) else state
}

private fun intervalState(p: Part, odo: Double, today: LocalDate, soon: SoonThreshold): PartState {
    if (p.lastKm == null && p.lastDate == null) return PartState(Status.none, 1.0, null, 0)
    val byKm = if (p.km != null && p.lastKm != null) (odo - p.lastKm) / p.km else 0.0
    val byTime = if (p.days != null && p.lastDate != null) daysBetween(p.lastDate, today) / p.days else 0.0
    val used = max(byKm, byTime)
    // Measured on each interval the part has: what is left against that share of the interval.
    val soonByKm = p.km != null && p.lastKm != null && p.km - (odo - p.lastKm) <= p.km * soon.kmPct / 100.0
    val soonByTime = p.days != null && p.lastDate != null && p.days - daysBetween(p.lastDate, today) <= p.days * soon.timePct / 100.0
    val status = when {
        used >= 1 -> Status.bad
        soonByKm || soonByTime -> Status.warn
        else -> Status.ok
    }
    return if (byKm >= byTime && p.km != null && p.lastKm != null) {
        PartState(status, used, Measure.km, (p.km - (odo - p.lastKm)).roundToInt())
    } else {
        PartState(status, used, Measure.days, (p.days!! - daysBetween(p.lastDate!!, today)).roundToInt())
    }
}

private val rank = mapOf(Status.ok to 0, Status.warn to 1, Status.bad to 2, Status.none to 3)

fun zoneStates(bike: Bike, odo: Double, today: LocalDate, soon: SoonThreshold = SoonThreshold()): Map<String, ZoneState> =
    Zone.entries.map { it.id }.associateWith { z ->
        val all = bike.parts.filter { it.zone == z }.map { it to partState(it, odo, today, soon) }
            .sortedByDescending { it.second.used }
        val parts = all.filter { it.first.watched }
        ZoneState(
            status = if (parts.any { it.second.problem != null }) Status.bad else parts.maxByOrNull { rank.getValue(it.second.status) }?.second?.status ?: Status.ok,
            worst = parts.maxOfOrNull { it.second.used } ?: 0.0,
            parts = parts,
            all = all,
        )
    }

/** 0–100 over the watched parts: an ok part counts 1, a part coming due counts half, overdue or unknown counts 0. */
fun health(bike: Bike, odo: Double, today: LocalDate, soon: SoonThreshold = SoonThreshold()): Int {
    val watched = bike.watchedParts
    if (watched.isEmpty()) return 100
    val score = watched.sumOf {
        when (partState(it, odo, today, soon).status) { Status.ok -> 1.0; Status.warn -> 0.5; else -> 0.0 }
    }
    return (100 * score / watched.size).roundToInt()
}

/** Lo próximo: watched parts that need attention and are not snoozed, most used first. */
fun upNext(bike: Bike, odo: Double, today: LocalDate, soon: SoonThreshold = SoonThreshold()): List<Pair<Part, PartState>> =
    bike.watchedParts.map { it to partState(it, odo, today, soon) }.filter { it.second.status != Status.ok && !it.first.snoozed(today) }
        .sortedByDescending { it.second.used }

/** What to look at soon, for the wide widget: watched, unsnoozed parts overdue (first) or coming due, most used first. */
fun Bike.dueSoon(odo: Double, today: LocalDate, soon: SoonThreshold = SoonThreshold()): List<Pair<Part, PartState>> =
    watchedParts.filterNot { it.snoozed(today) }.map { it to partState(it, odo, today, soon) }
        .filter { it.second.status == Status.bad || it.second.status == Status.warn }
        .sortedWith(compareBy<Pair<Part, PartState>> { if (it.second.status == Status.bad) 0 else 1 }.thenByDescending { it.second.used })

/** Records a real odometer reading and, when GPS rides covered the gap, recalibrates the GPS factor. */
fun Bike.withReading(km: Int, today: LocalDate, now: Long = 0): Bike {
    val est = estimate(this, today)
    val last = readings.maxWith(compareBy({ it.date }, { it.at }))
    // With estimated km in the gap the difference says nothing about the GPS.
    val factor = if (est.gpsSinceReading > 0 && est.estimatedSinceReading == 0.0) {
        val f = (km - last.km) / est.gpsSinceReading
        if (f in 0.85..1.15) f else gpsFactor
    } else gpsFactor
    return copy(readings = readings + Reading(today.toString(), km, now), gpsFactor = factor)
}

/**
 * A job logged for today at [km] is also the odometer right now. Any km other than the estimate's own becomes a
 * reading, below the estimate too: otherwise an overshooting estimate keeps eating the interval the job just reset.
 */
fun Bike.withServiceReading(date: String, km: Int, today: LocalDate, now: Long = 0): Bike =
    if (date == today.toString() && km != estimate(this, today).km.toInt()) withReading(km, today, now) else this

/** Fixes a mistyped latest reading (and the fill-up that brought it): it is replaced, never added on top of. */
fun Bike.replaceLastReading(km: Int, today: LocalDate, now: Long = 0): Bike {
    val last = readings.maxWith(compareBy({ it.date }, { it.at }))
    return copy(readings = readings - last + Reading(today.toString(), km, now), fuel = fuel.map { if (it.km == last.km && it.at == last.at) it.copy(km = km, at = now) else it })
}

fun Bike.markDone(partId: String, km: Int, today: LocalDate) =
    addRecord(ServiceRecord(java.util.UUID.randomUUID().toString(), partId, today.toString(), km))

/** A rider-set interval; at least one of [km] or [days] must be set. */
fun Bike.withInterval(partId: String, km: Int?, days: Int?, timeUnit: IntervalUnit = IntervalUnit.days, timeValue: String? = null): Bike {
    require(km != null || days != null)
    return copy(parts = parts.map { if (it.id == partId) it.copy(km = km, days = days, timeUnit = timeUnit, timeValue = timeValue) else it })
}

fun Bike.suggested(partId: String): Part? = defaultParts(category, drive, cooling).firstOrNull { it.id == partId }

fun fmtKm(n: Int): String = "%,d".format(java.util.Locale.ROOT, n).replace(',', '.')

/**
 * Parts that got worse since we last told the rider (ok → coming due → overdue), plus the new baseline.
 * Never-recorded parts are not news: the rider sees them in red already. Unwatched and snoozed parts keep their old baseline.
 */
fun dueAlerts(bike: Bike, odo: Double, today: LocalDate, soon: SoonThreshold = SoonThreshold()): Pair<List<Pair<Part, PartState>>, Map<String, Status>> {
    val now = bike.parts.associate { it.id to partState(it, odo, today, soon) }
    val sev = mapOf(Status.ok to 0, Status.warn to 1, Status.bad to 2)
    val worse = bike.parts.mapNotNull { p ->
        val s = now.getValue(p.id)
        if (p.snoozed(today) || !p.watched) return@mapNotNull null
        val before = sev[bike.notified[p.id] ?: Status.ok] ?: 0
        val after = sev[s.status] ?: return@mapNotNull null
        if (after > before) p to s else null
    }.sortedByDescending { it.second.used }
    return worse to now.mapValues { (id, state) -> bike.parts.first { it.id == id }.let { p -> if (p.snoozed(today) || !p.watched) bike.notified[id] ?: Status.ok else state.status } }
}

/** Shared health meter bands. */
fun healthBand(score: Int): Status = when { score >= 80 -> Status.ok; score >= 50 -> Status.warn; else -> Status.bad }

fun Part.snoozed(today: LocalDate) = snoozedUntil?.let { runCatching { LocalDate.parse(it).isAfter(today) }.getOrDefault(false) } ?: false

fun formatKm(n: Int, locale: java.util.Locale): String = java.text.NumberFormat.getIntegerInstance(locale).format(n)
