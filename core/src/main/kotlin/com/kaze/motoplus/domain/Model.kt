package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable

/** Zones of the bike drawing, in drawing order. Ids are the contract with assets/bike_*.json and with AI reports. */
enum class Zone(val id: String) {
    RearWheel("rear_wheel"), RearBrake("rear_brake"), Drivetrain("drivetrain"), RearShock("rear_shock"),
    Battery("battery"), Intake("intake"), Engine("engine"), Cooling("cooling"), Fork("fork"),
    FrontBrake("front_brake"), FrontWheel("front_wheel"), Lights("lights");

    companion object {
        val ids = entries.map { it.id }.toSet()
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

@Serializable
enum class Category { naked, sport, trail, scooter, custom, touring, offroad }

@Serializable
enum class Drive { chain, belt, shaft, cvt }

@Serializable
enum class Cooling { liquid, air }

/** The rider's colour for a bike. None of them is green, amber or red: those belong to part status. */
@Serializable
enum class Accent { cyan, violet, magenta, chalk }

/** A maintenance item. Interval by distance, by time, or both: whichever comes first. */
@Serializable
data class Part(
    val id: String,
    val zone: String,
    val name: String,
    val km: Int? = null,
    val days: Int? = null,
    val lastKm: Int? = null,
    val lastDate: String? = null,
    val snoozedUntil: String? = null,
)

@Serializable
data class Reading(val date: String, val km: Int, val at: Long = 0)

@Serializable
enum class RideTrigger { manual, motion, bluetooth }

@Serializable
data class Ride(
    val date: String,
    val km: Double,
    val trigger: RideTrigger = RideTrigger.manual,
    /** Name of the Bluetooth device that started the ride, if any. */
    val device: String? = null,
    val wet: Boolean = false,
    val at: Long = 0,
) {
    /** Rides on the reading's own day count only if they ended after it was taken. */
    fun after(r: Reading) = date > r.date || (date == r.date && at > 0 && r.at > 0 && at > r.at)
}

@Serializable
data class SavedReport(val at: String, val km: Int, val data: ReportData)

@Serializable
data class Bike(
    val id: String,
    val brand: String,
    val model: String,
    val year: Int? = null,
    val category: Category,
    val drive: Drive,
    val cooling: Cooling,
    val parts: List<Part>,
    val readings: List<Reading>,
    val rides: List<Ride> = emptyList(),
    val gpsFactor: Double = 1.0,
    val reports: List<SavedReport> = emptyList(),
    val history: List<ServiceRecord> = emptyList(),
    /** Last status we notified per part, so a reminder fires once per change. */
    val notified: Map<String, Status> = emptyMap(),
    val accent: Accent = Accent.cyan,
    val fuel: List<FuelEntry> = emptyList(),
    val orders: List<WorkOrder> = emptyList(),
    val checks: List<PreRideCheck> = emptyList(),
) {
    val title get() = listOfNotNull(brand, model, year?.toString()).joinToString(" ")
}

/** How this phone keeps the odometer estimate current: each ride detector and each car exclusion is switched on its own. */
@Serializable
data class Tracking(
    /** Start a ride when the activity sensor says the phone is in a vehicle. */
    val motion: Boolean = false,
    /** Start a ride when one of [devices] connects. */
    val bluetooth: Boolean = false,
    /** Bluetooth devices (helmet intercom, bike) whose connection means "I'm riding", address to name. */
    val devices: Map<String, String> = emptyMap(),
    /** The rider's cars, address to name: while one is connected, a motion ride is a car trip. Empty turns the rule off. */
    val carDevices: Map<String, String> = emptyMap(),
    /** While the phone projects to Android Auto, a motion ride is a car trip. */
    val androidAuto: Boolean = false,
) {
    /** Some detector can start a ride without the rider. */
    val detects get() = motion || bluetooth
}

@Serializable
data class Garage(
    val schema: Int = SCHEMA,
    val bikes: List<Bike> = emptyList(),
    val selectedId: String? = null,
    val tracking: Tracking = Tracking(),
    val documents: List<Document> = emptyList(),
    val currency: String = "COP",
) {
    val selected get() = bikes.firstOrNull { it.id == selectedId } ?: bikes.firstOrNull()

    companion object { const val SCHEMA = 3 }
}
