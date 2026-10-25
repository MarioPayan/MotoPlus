package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable

/** A quick check before riding. Step ids are the app's (tires, lights, chain…); [failed] ones need a look. */
@Serializable
data class PreRideCheck(val date: String, val at: Long, val ok: List<String>, val failed: List<String>, val notes: String = "")

/** Steps whose "fine" also counts as the matching periodic job done today (pressure every 14 days, lights every 30). */
val CHECK_PARTS = mapOf("tires" to "tire_pressure", "lights" to "lights_check")

/** Saves the check and marks the periodic jobs it covers as done. */
fun Bike.withCheck(c: PreRideCheck, km: Int, today: java.time.LocalDate): Bike {
    var b = copy(checks = checks + c)
    for (step in c.ok) CHECK_PARTS[step]?.takeIf { id -> b.parts.any { it.id == id } }?.let { b = b.markDone(it, km, today) }
    return b
}
