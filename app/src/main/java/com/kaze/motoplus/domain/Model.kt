package com.kaze.motoplus.domain

import kotlinx.serialization.Serializable

/** Zones of the bike drawing. Ids are the contract with assets/bike_*.json. */
val ZONES: Map<String, String> = linkedMapOf(
    "rueda_tras" to "Rueda trasera",
    "freno_tras" to "Freno trasero",
    "cadena" to "Transmisión",
    "suspension_tras" to "Amortiguador",
    "bateria" to "Batería",
    "admision" to "Admisión",
    "motor" to "Motor",
    "radiador" to "Refrigeración",
    "horquilla" to "Horquilla",
    "freno_del" to "Freno delantero",
    "rueda_del" to "Rueda delantera",
    "faro" to "Luces",
)

@Serializable
enum class Category(val label: String) {
    naked("Naked"), sport("Deportiva"), trail("Trail"), scooter("Scooter"),
    custom("Custom"), touring("Turismo"), offroad("Enduro / cross")
}

@Serializable
enum class Drive(val label: String) { chain("Cadena"), belt("Correa"), shaft("Cardán"), cvt("Variador (CVT)") }

@Serializable
enum class Cooling(val label: String) { liquid("Líquida"), air("Aire") }

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
)

@Serializable
data class Reading(val date: String, val km: Int, val at: Long = 0)

@Serializable
data class Ride(val date: String, val km: Double, val source: String, val wet: Boolean = false, val at: Long = 0) {
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
) {
    val title get() = listOfNotNull(brand, model, year?.toString()).joinToString(" ")
}

/** Automatic ride detection settings for this phone. */
@Serializable
data class Tracking(
    val enabled: Boolean = false,
    /** Bluetooth devices (helmet intercom, bike) whose connection means "I'm riding". */
    val devices: Map<String, String> = emptyMap(),
)

@Serializable
data class Garage(val bikes: List<Bike> = emptyList(), val selectedId: String? = null, val tracking: Tracking = Tracking()) {
    val selected get() = bikes.firstOrNull { it.id == selectedId } ?: bikes.firstOrNull()
}
