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

/** Parts worth taking to the workshop now: overdue or coming due, most urgent first. */
fun Bike.dueForWorkshop(odo: Double, today: java.time.LocalDate): List<String> =
    parts.map { it to partState(it, odo, today) }.filter { it.second.status == Status.bad || it.second.status == Status.warn }
        .sortedByDescending { it.second.used }.map { it.first.id }

fun Bike.openOrder() = orders.lastOrNull { !it.closed }

/** The jobs rendered by both workshop screens and counted on the Taller tab. */
data class WorkshopItems(val partIds: List<String>, val customItems: List<PendingJob>) {
    val count: Int get() = partIds.size + customItems.size
}

/** A saved list (including an empty one) wins over automatically suggested due parts. */
fun Bike.workshopItems(odo: Double, today: java.time.LocalDate): WorkshopItems {
    val open = openOrder()
    val ids = (open?.partIds ?: dueForWorkshop(odo, today)).distinct().filter { id -> parts.any { it.id == id } }
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

/** Saves a reviewed visit atomically; unfinished jobs stay in Pendientes. */
fun Bike.completeVisit(order: WorkOrder?, done: Map<String, Double?>, extras: List<PendingJob>, date: String, km: Int, photo: String?, newId: () -> String): Bike {
    require(km >= 0 && !java.time.LocalDate.parse(date).isAfter(java.time.LocalDate.now()))
    val custom = (order?.customItems.orEmpty() + extras).associateBy { it.id }
    var b = this
    for ((id, cost) in done) {
        require(cost == null || (cost.isFinite() && cost >= 0))
        val job = custom[id]
        if (parts.none { it.id == id } && job == null) continue
        b = b.addRecord(ServiceRecord(newId(), id, date, km, cost,
            notes = listOfNotNull(order?.notes?.takeIf { it.isNotBlank() }, order?.itemNotes?.get(id)?.takeIf { it.isNotBlank() }, job?.note?.takeIf { it.isNotBlank() }).joinToString("\n"), photo = photo, partName = job?.name))
    }
    if (order != null) {
        val remaining = order.copy(id = newId(), number = nextOrderNumber(), date = date, km = km,
            partIds = order.partIds.filterNot(done::containsKey),
            customItems = order.customItems.filterNot { it.id in done },
            itemNotes = order.itemNotes.filterKeys { it !in done })
        b = b.copy(orders = b.orders.map { if (it.id == order.id) it.copy(closed = true) else it } +
            if (remaining.partIds.isNotEmpty() || remaining.customItems.isNotEmpty()) listOf(remaining) else emptyList())
    }
    return b
}
