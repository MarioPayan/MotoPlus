package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable

/** A list of jobs to take to the workshop. Open until the rider comes back and marks what was done. */
@Serializable
data class WorkOrder(
    val id: String,
    val number: Int,
    val date: String,
    val km: Int,
    val partIds: List<String>,
    val notes: String = "",
    val closed: Boolean = false,
    val customItems: List<PendingJob> = emptyList(),
    val itemNotes: Map<String, String> = emptyMap(),
)

@Serializable
data class PendingJob(val id: String, val name: String, val note: String = "")

/** Watched parts worth taking to the workshop now: overdue or coming due, most urgent first. */
fun Bike.dueForWorkshop(odo: Double, today: java.time.LocalDate, soon: SoonThreshold = SoonThreshold()): List<String> =
    watchedParts.map { it to partState(it, odo, today, soon) }.filter { it.second.status == Status.bad || it.second.status == Status.warn }
        .sortedByDescending { it.second.used }.map { it.first.id }

fun Bike.openOrder() = orders.lastOrNull { !it.closed }

/** The jobs rendered by both workshop screens and counted on the Taller tab. */
data class WorkshopItems(val partIds: List<String>, val customItems: List<PendingJob>) {
    val count: Int get() = partIds.size + customItems.size
}

/** A saved list (including an empty one) wins over automatically suggested due parts. */
fun Bike.workshopItems(odo: Double, today: java.time.LocalDate, soon: SoonThreshold = SoonThreshold()): WorkshopItems {
    val open = openOrder()
    val ids = (open?.partIds ?: dueForWorkshop(odo, today, soon)).distinct().filter { id -> parts.any { it.id == id } }
    return WorkshopItems(ids, open?.customItems.orEmpty())
}

fun Bike.nextOrderNumber() = (orders.maxOfOrNull { it.number } ?: 0) + 1

/** The rider is back: each done job becomes a service record with its cost; the order closes. */
fun Bike.closeOrder(orderId: String, done: Map<String, Double?>, date: String, km: Int, photo: String?, newId: () -> String): Bike {
    val o = orders.firstOrNull { it.id == orderId } ?: return this
    var b = copy(orders = orders.map { if (it.id == orderId) it.copy(closed = true) else it })
    for ((partId, cost) in done) b = b.addRecord(ServiceRecord(newId(), partId, date, km, cost, notes = o.notes, photo = photo))
    return b
}
