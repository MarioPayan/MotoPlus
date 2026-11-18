package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

/** Why a recorded ride was thrown away instead of counted. */
enum class DiscardReason { carBluetooth, androidAuto }

/**
 * Whether a ride counts, given what the phone was connected to while it was recorded.
 * Only motion rides can be car trips: the activity sensor can't tell a car from a bike, while an intercom
 * connection or the rider's own tap can. Returns null to keep the ride, or why to discard it.
 */
fun discardReason(trigger: RideTrigger, carBluetoothConnected: Boolean, androidAutoConnected: Boolean, settings: Tracking): DiscardReason? = when {
    trigger != RideTrigger.motion -> null
    carBluetoothConnected && settings.carDevices.isNotEmpty() -> DiscardReason.carBluetooth
    androidAutoConnected && settings.androidAutoInCar -> DiscardReason.androidAuto
    else -> null
}

private const val PARKED_AFTER_MS = 6 * 60_000L

/** The bike hasn't moved for 6 minutes. A ride that never moved counts from its start. */
fun isParked(now: Long, lastMovingAt: Long, startedAt: Long) = now - maxOf(lastMovingAt, startedAt) > PARKED_AFTER_MS

/** Below this a ride is GPS noise, not a trip. */
const val MIN_RIDE_KM = 0.3

/** The latest odometer reading the rider typed after [startedAt], while a ride was open. */
fun Bike.readingDuring(startedAt: Long): Reading? = readings.filter { it.at > startedAt }.maxByOrNull { it.at }

/**
 * A reading typed mid-ride is the odometer at that moment, so the ride only adds what it covered after it.
 * Counting it all would add the km before the reading twice.
 */
fun Bike.rideKm(startedAt: Long, meter: DistanceMeter): Double = readingDuring(startedAt)?.let { meter.kmSince(it.at) } ?: meter.km

/** What a live ride keeps on disk so that a system kill, which skips onDestroy, doesn't lose it. [lastAt] is when it was written. */
@Serializable
data class RideCheckpoint(
    val bikeId: String,
    val trigger: RideTrigger,
    val device: String? = null,
    val km: Double,
    val startedAt: Long,
    val lastAt: Long,
    val carBluetooth: Boolean = false,
    val androidAuto: Boolean = false,
) {
    fun encode(): String = checkpointJson.encodeToString(this)

    companion object {
        /** Null for a file the kill left half written. */
        fun decode(text: String): RideCheckpoint? = runCatching { checkpointJson.decodeFromString<RideCheckpoint>(text) }.getOrNull()
    }
}

private val checkpointJson = Json { ignoreUnknownKeys = true }

/**
 * The ride a dead process left behind, dated when it was last alive. Null when there is nothing to save: too short, a
 * discard rule applies, the bike is gone, or the ride already reached the list (only a kill between saving and deleting
 * the checkpoint leaves that, and no other ride can have ended since this one started).
 */
fun Garage.orphanRide(c: RideCheckpoint, zone: ZoneId): Ride? {
    val bike = bikes.firstOrNull { it.id == c.bikeId } ?: return null
    if (bike.rides.any { it.at >= c.startedAt }) return null
    if (c.km < MIN_RIDE_KM || discardReason(c.trigger, c.carBluetooth, c.androidAuto, tracking) != null) return null
    return Ride(Instant.ofEpochMilli(c.lastAt).atZone(zone).toLocalDate().toString(), c.km, c.trigger, c.device, at = c.lastAt)
}
