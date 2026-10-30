package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Who did a logged maintenance: a workshop or the rider. */
@Serializable
enum class Venue { shop, self }

/** One job in a maintenance log: a part's id, or a free job with its [name]. */
data class LoggedJob(val id: String, val name: String? = null, val cost: Double? = null, val note: String = "")

/** Sets of jobs the maintenance screen checks with one tap. */
enum class QuickPick { pending, overdue, soon, oil, full }

private val OIL_CHANGE = listOf("engine_oil", "oil_filter")
private val FULL_SERVICE = listOf("engine_oil", "oil_filter", "air_filter", "spark_plugs", "chain_lube")

/** Ids a quick pick checks; parts the bike lacks are skipped. Pendientes are the Taller list, custom jobs included. */
fun Bike.quickPick(pick: QuickPick, odo: Double, today: LocalDate): List<String> {
    fun present(ids: List<String>) = ids.filter { id -> parts.any { it.id == id } }
    fun status(s: Status) = parts.filter { partState(it, odo, today).status == s }.map { it.id }
    return when (pick) {
        QuickPick.pending -> workshopItems(odo, today).let { it.partIds + it.customItems.map(PendingJob::id) }
        QuickPick.overdue -> status(Status.bad)
        QuickPick.soon -> status(Status.warn)
        QuickPick.oil -> present(OIL_CHANGE)
        QuickPick.full -> present(FULL_SERVICE)
    }
}

/**
 * Many jobs at one date and odometer reading: one record per job, all sharing a new group id.
 * A reading taken today above the estimate becomes an odometer reading. With [orderId], the done jobs leave that
 * Taller list; it closes, keeping its last jobs for the history, only when none remain.
 */
fun Bike.logMaintenance(
    jobs: List<LoggedJob>, date: String, km: Int, today: LocalDate, venue: Venue?, place: String, photo: String?,
    orderId: String?, newId: () -> String, now: Long = 0,
): Bike {
    require(jobs.isNotEmpty() && km in 0..9_999_999 && !LocalDate.parse(date).isAfter(today))
    require(jobs.all { j -> j.cost == null || (j.cost.isFinite() && j.cost >= 0) })
    val order = orders.firstOrNull { it.id == orderId && !it.closed }
    val group = newId()
    var b = if (date == today.toString() && km > estimate(this, today).km.toInt()) withReading(km, today, now) else this
    for (j in jobs) {
        val part = parts.any { it.id == j.id }
        if (!part && j.name.isNullOrBlank()) continue
        val notes = listOfNotNull(order?.notes, j.note).map(String::trim).filter(String::isNotEmpty).joinToString("\n")
        b = b.addRecord(ServiceRecord(newId(), j.id, date, km, j.cost, notes, photo, if (part) null else j.name!!.trim(),
            group = group, venue = venue, place = place.trim()))
    }
    if (order != null) {
        val done = jobs.map { it.id }.toSet()
        val left = order.copy(partIds = order.partIds.filterNot(done::contains), customItems = order.customItems.filterNot { it.id in done },
            itemNotes = order.itemNotes.filterKeys { it !in done })
        val next = if (left.partIds.isEmpty() && left.customItems.isEmpty()) order.copy(closed = true) else left
        b = b.copy(orders = b.orders.map { if (it.id == order.id) next else it })
    }
    return b
}

/**
 * Deshacer: the bike goes back to [before] exactly when nothing else touched it since [after];
 * otherwise only what the log added is taken back, so a ride recorded meanwhile survives.
 */
fun Bike.undoMaintenance(before: Bike, after: Bike): Bike {
    if (this == after) return before
    val added = after.history.filter { r -> before.history.none { it.id == r.id } }.map { it.id }.toSet()
    val readings = after.readings.filterNot(before.readings::contains).toSet()
    return copy(
        history = history.filterNot { it.id in added },
        parts = parts.map { p -> before.parts.firstOrNull { it.id == p.id }?.let { p.copy(lastKm = it.lastKm, lastDate = it.lastDate) } ?: p },
        readings = this.readings.filterNot(readings::contains),
        gpsFactor = if (readings.isEmpty()) gpsFactor else before.gpsFactor,
        orders = orders.map { o -> before.orders.firstOrNull { it.id == o.id } ?: o },
    )
}
