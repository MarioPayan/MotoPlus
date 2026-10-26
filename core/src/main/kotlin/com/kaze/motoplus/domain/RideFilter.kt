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
    androidAutoConnected && settings.androidAuto -> DiscardReason.androidAuto
    else -> null
}
