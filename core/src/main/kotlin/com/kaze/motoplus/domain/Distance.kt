package com.kaze.motoplus.domain

import kotlin.math.*

/**
 * Adds up GPS fixes into ride distance, dropping what a real phone produces on a bike:
 * imprecise fixes, jitter while stopped and teleport jumps after a tunnel.
 */
class DistanceMeter(
    private val maxAccuracyM: Float = 35f,
    private val minStepM: Double = 8.0,
    private val maxSpeedMs: Double = 75.0,
) {
    var meters = 0.0; private set
    var lastMovingAt = 0L; private set
    private var lat = 0.0
    private var lon = 0.0
    private var at = 0L
    private var has = false
    /** Fix time and total meters after each counted step, so the km since any moment can be told. */
    private val steps = ArrayList<Pair<Long, Double>>()

    /** Returns true when the fix counted as movement. */
    fun add(latitude: Double, longitude: Double, accuracyM: Float, timeMs: Long): Boolean {
        if (accuracyM > maxAccuracyM) return false
        if (!has) { lat = latitude; lon = longitude; at = timeMs; has = true; lastMovingAt = timeMs; return false }
        val d = haversine(lat, lon, latitude, longitude)
        val dt = (timeMs - at) / 1000.0
        // A step smaller than the fix's own error is jitter: keep the anchor, so slow real movement still adds up.
        if (d < maxOf(minStepM, accuracyM.toDouble())) return false
        if (dt > 0 && d / dt > maxSpeedMs) { lat = latitude; lon = longitude; at = timeMs; return false }
        meters += d; lat = latitude; lon = longitude; at = timeMs; lastMovingAt = timeMs
        steps += timeMs to meters
        return true
    }

    val km get() = meters / 1000.0

    /** Km from fixes taken after [timeMs]. */
    fun kmSince(timeMs: Long): Double = (meters - (steps.lastOrNull { it.first <= timeMs }?.second ?: 0.0)) / 1000.0
}

fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}
