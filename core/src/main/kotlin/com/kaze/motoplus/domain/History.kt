package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable

/** One maintenance job: what, when, at how many km, and optionally what it cost and the invoice photo. */
@Serializable
data class ServiceRecord(
    val id: String,
    val partId: String,
    val date: String,
    val km: Int,
    val cost: Double? = null,
    val notes: String = "",
    val photo: String? = null,
    val partName: String? = null,
    /** Records saved together by Registrar mantenimiento share this id. */
    val group: String? = null,
    val venue: Venue? = null,
    /** Free text for where it was done, e.g. the workshop's name. */
    val place: String = "",
)

/** Logging maintenance on a part also settles any problem reported on it. */
fun Bike.addRecord(r: ServiceRecord) = copy(history = history + r).recompute(r.partId).clearProblem(r.partId)

fun Bike.updateRecord(r: ServiceRecord) = copy(history = history.map { if (it.id == r.id) r else it }).recompute(r.partId)

fun Bike.deleteRecord(id: String): Bike {
    val r = history.firstOrNull { it.id == id } ?: return this
    return copy(history = history.filter { it.id != id }).recompute(r.partId)
}

/** The part's "last done" always mirrors its newest record; with no records left it goes back to unknown. */
private fun Bike.recompute(partId: String): Bike {
    val last = history.filter { it.partId == partId }.maxWithOrNull(compareBy({ it.date }, { it.km }))
    return copy(parts = parts.map { if (it.id == partId) it.copy(lastKm = last?.km, lastDate = last?.date) else it })
}

fun Bike.recordsFor(partId: String) = history.filter { it.partId == partId }.sortedWith(compareByDescending<ServiceRecord> { it.date }.thenByDescending { it.km })

val Bike.totalCost get() = history.sumOf { it.cost ?: 0.0 }

/** Removes an interval while retaining the names and costs of past work. */
fun Bike.withoutPart(partId: String): Bike {
    val part = parts.firstOrNull { it.id == partId } ?: return this
    return copy(
        parts = parts.filter { it.id != partId },
        history = history.map { if (it.partId == partId && it.partName == null) it.copy(partName = part.name) else it },
        orders = orders.map { it.copy(partIds = it.partIds - partId) },
        notified = notified - partId,
    )
}
