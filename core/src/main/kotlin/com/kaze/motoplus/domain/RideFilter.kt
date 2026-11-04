package com.kaze.motoplus.domain

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
