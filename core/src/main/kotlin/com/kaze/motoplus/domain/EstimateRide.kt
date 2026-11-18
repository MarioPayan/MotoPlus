package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

/** A city guess, not a measurement: the rider can edit it, and measured rides can suggest a better one. */
const val DEFAULT_ESTIMATE_KMH = 25
val ESTIMATE_KMH_RANGE = 5..120

/** Fewer measured rides than this say too little about the rider's usual speed. */
private const val MEASURED_SPEED_MIN_RIDES = 5

/** Past this the end event was almost certainly lost, so time × speed would invent a huge ride. */
private const val ESTIMATE_CAP_MS = 3 * 3_600_000L
private const val MS_PER_HOUR = 3_600_000.0

/**
 * An open Estimación ride. It lives on disk from start to end because the process can die in between; with no GPS,
 * the start time is all that km are computed from. [carBluetooth] records that a car connected during it.
 */
@Serializable
data class EstimateSession(
    val bikeId: String,
    val trigger: RideTrigger,
    val device: String? = null,
    val startedAt: Long,
    val carBluetooth: Boolean = false,
) {
    fun encode(): String = sessionJson.encodeToString(this)

    companion object {
        /** Null for a file a kill left half written. */
        fun decode(text: String): EstimateSession? = runCatching { sessionJson.decodeFromString<EstimateSession>(text) }.getOrNull()
    }
}

private val sessionJson = Json { ignoreUnknownKeys = true }

fun estimatedKm(startedAt: Long, now: Long, kmh: Int): Double =
    (now - startedAt).coerceAtLeast(0) / MS_PER_HOUR * kmh.coerceIn(ESTIMATE_KMH_RANGE.first, ESTIMATE_KMH_RANGE.last)

fun isPastEstimateCap(startedAt: Long, now: Long) = now - startedAt > ESTIMATE_CAP_MS

enum class EstimateDrop { noBike, alreadySaved, tooLong, carBluetooth, tooShort }

/** How a session ended: [ride] to save, or why there is none. [km] is what time × speed gave either way. */
data class EstimateEnd(val km: Double, val ride: Ride?, val dropped: EstimateDrop?)

/**
 * Ends a session at [now]. Android Auto isn't checked: its state can't be followed without a live process.
 * "Already saved" means a kill landed between saving the ride and deleting the session.
 */
fun Garage.endEstimate(s: EstimateSession, now: Long, zone: ZoneId): EstimateEnd {
    val bike = bikes.firstOrNull { it.id == s.bikeId }
    // A reading typed during the session is the odometer then: only the time after it adds km.
    val km = estimatedKm(bike?.readingDuring(s.startedAt)?.at ?: s.startedAt, now, tracking.estimateKmh)
    fun dropped(why: EstimateDrop) = EstimateEnd(km, null, why)
    if (bike == null) return dropped(EstimateDrop.noBike)
    if (bike.rides.any { it.at >= s.startedAt }) return dropped(EstimateDrop.alreadySaved)
    if (isPastEstimateCap(s.startedAt, now)) return dropped(EstimateDrop.tooLong)
    if (discardReason(s.trigger, s.carBluetooth, false, tracking) != null) return dropped(EstimateDrop.carBluetooth)
    if (km < MIN_RIDE_KM) return dropped(EstimateDrop.tooShort)
    val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
    return EstimateEnd(km, Ride(date, km, s.trigger, s.device, at = now, estimated = true), null)
}

/** A session found at process start that no longer can end in a ride, so it must not stay open. */
fun Garage.estimateIsOver(s: EstimateSession, now: Long, zone: ZoneId) =
    endEstimate(s, now, zone).dropped in setOf(EstimateDrop.noBike, EstimateDrop.alreadySaved, EstimateDrop.tooLong)

/** The rider's real average from GPS rides, stops included, or null until there are enough of them. Never applied on its own. */
fun Bike.measuredKmh(): Int? {
    val measured = rides.filter { !it.estimated && it.durationMs > 0 }
    if (measured.size < MEASURED_SPEED_MIN_RIDES) return null
    val kmh = measured.sumOf { it.km } / (measured.sumOf { it.durationMs } / MS_PER_HOUR)
    return Math.round(kmh).toInt().takeIf { it in ESTIMATE_KMH_RANGE }
}
