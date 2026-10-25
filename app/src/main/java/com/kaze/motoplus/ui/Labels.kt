package com.kaze.motoplus.ui

import com.kaze.motoplus.domain.*

// Every word the rider reads about the model lives here, so the domain stays language-free.

fun zoneLabel(id: String) = when (Zone.of(id)) {
    Zone.RearWheel -> "Rueda trasera"
    Zone.RearBrake -> "Freno trasero"
    Zone.Drivetrain -> "Transmisión"
    Zone.RearShock -> "Amortiguador"
    Zone.Battery -> "Batería"
    Zone.Intake -> "Admisión"
    Zone.Engine -> "Motor"
    Zone.Cooling -> "Refrigeración"
    Zone.Fork -> "Horquilla"
    Zone.FrontBrake -> "Freno delantero"
    Zone.FrontWheel -> "Rueda delantera"
    Zone.Lights -> "Luces"
    null -> id
}

/** Short caps name for callouts on the drawing. */
fun zoneShort(id: String, drive: Drive) = when (Zone.of(id)) {
    Zone.RearWheel -> "RUEDA TRAS."; Zone.RearBrake -> "FRENO TRAS."; Zone.RearShock -> "AMORT."; Zone.Battery -> "BATERÍA"
    Zone.Drivetrain -> when (drive) { Drive.chain -> "CADENA"; Drive.belt -> "CORREA"; Drive.shaft -> "CARDÁN"; Drive.cvt -> "VARIADOR" }
    Zone.Intake -> "ADMISIÓN"; Zone.Engine -> "MOTOR"; Zone.Cooling -> "REFRIG."; Zone.Fork -> "HORQUILLA"
    Zone.FrontBrake -> "FRENO DEL."; Zone.FrontWheel -> "RUEDA DEL."; Zone.Lights -> "LUCES"; null -> id.uppercase()
}

val Category.label get() = when (this) {
    Category.naked -> "Naked"; Category.sport -> "Deportiva"; Category.trail -> "Trail"; Category.scooter -> "Scooter"
    Category.custom -> "Custom"; Category.touring -> "Turismo"; Category.offroad -> "Enduro / cross"
}

val Drive.label get() = when (this) { Drive.chain -> "Cadena"; Drive.belt -> "Correa"; Drive.shaft -> "Cardán"; Drive.cvt -> "Variador (CVT)" }

val Cooling.label get() = when (this) { Cooling.liquid -> "Líquida"; Cooling.air -> "Aire" }

val Level.label get() = when (this) { Level.low -> "baja"; Level.medium -> "media"; Level.high -> "alta" }

/** "Quedan 400 km", "Pasado por 3 días", "Sin registro". */
val PartState.text get() = when (measure) {
    null -> "Sin registro"
    Measure.km -> if (left >= 0) "Quedan ${fmtKm(left)} km" else "Pasado por ${fmtKm(-left)} km"
    Measure.days -> if (left >= 0) "Quedan $left días" else "Pasado por ${-left} días"
}

val OdoEstimate.source get() = when (basis) {
    EstimateBasis.rides -> if (rides == 1) "1 salida desde la última lectura" else "$rides salidas desde la última lectura"
    EstimateBasis.todayReading -> "Lectura de hoy"
    EstimateBasis.lastReading -> "Última lectura"
    EstimateBasis.average -> "Estimado con tu media de ${"%.1f".format(kmPerDay)} km/día"
}

val Ride.sourceLabel get() = when (trigger) {
    RideTrigger.manual -> "Manual"
    RideTrigger.motion -> "Sensor de movimiento"
    RideTrigger.bluetooth -> "Bluetooth" + (device?.let { ": $it" } ?: "")
}

val ReportError.message get() = when (this) {
    ReportError.notFound -> "No encuentro el bloque motoplus ni el enlace. Copia la respuesta completa de la IA."
    ReportError.invalid -> "El bloque está incompleto o no es JSON válido. Pide a la IA que repita solo el bloque motoplus."
    ReportError.incomplete -> "El bloque está incompleto: falta el resumen."
}
