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
)

/** Parts worth taking to the workshop now: overdue or coming due, most urgent first. */
fun Bike.dueForWorkshop(odo: Double, today: java.time.LocalDate): List<String> =
    parts.map { it to partState(it, odo, today) }.filter { it.second.status == Status.bad || it.second.status == Status.warn }
        .sortedByDescending { it.second.used }.map { it.first.id }

fun Bike.openOrder() = orders.lastOrNull { !it.closed }

fun Bike.nextOrderNumber() = (orders.maxOfOrNull { it.number } ?: 0) + 1

/** The rider is back: each done job becomes a service record with its cost; the order closes. */
fun Bike.closeOrder(orderId: String, done: Map<String, Double?>, date: String, km: Int, photo: String?, newId: () -> String): Bike {
    val o = orders.firstOrNull { it.id == orderId } ?: return this
    var b = copy(orders = orders.map { if (it.id == orderId) it.copy(closed = true) else it })
    for ((partId, cost) in done) b = b.addRecord(ServiceRecord(newId(), partId, date, km, cost, notes = "Orden de trabajo nº ${o.number}", photo = photo))
    return b
}
