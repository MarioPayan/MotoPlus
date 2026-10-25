package com.kaze.motoplus.domain

/**
 * Generic maintenance plan for a new bike. These are typical manual values, not a specific model's:
 * the rider can adjust each interval later.
 */
// ponytail: one generic plan per category/drive/cooling; per-model plans when the catalog carries intervals
fun defaultParts(category: Category, drive: Drive, cooling: Cooling): List<Part> {
    val small = category == Category.scooter
    val dirt = category == Category.offroad
    val sport = category == Category.sport
    val list = mutableListOf(
        Part("engine_oil", "engine", "Aceite de motor", km = if (dirt) 1500 else if (small) 4000 else 6000, days = 365),
        Part("oil_filter", "engine", "Filtro de aceite", km = if (dirt) 3000 else if (small) 8000 else 12000, days = 730),
        Part("spark_plugs", "engine", "Bujías", km = if (dirt) 6000 else 12000, days = 730),
        Part("valve_clearance", "engine", "Holgura de válvulas", km = if (dirt) 3000 else if (sport) 24000 else 18000),
        Part("air_filter", "intake", "Filtro de aire", km = if (dirt) 500 else 12000, days = if (dirt) null else 730),
        Part("front_pads", "front_brake", "Pastillas delanteras", km = if (sport) 7000 else 10000),
        Part("brake_fluid", "front_brake", "Líquido de frenos", days = 730),
        Part("rear_pads", "rear_brake", "Pastillas traseras", km = 15000),
        Part("front_tire", "front_wheel", "Neumático delantero", km = if (sport) 8000 else 12000, days = 1825),
        Part("rear_tire", "rear_wheel", "Neumático trasero", km = if (sport) 5000 else 9000, days = 1825),
        Part("tire_pressure", "rear_wheel", "Presión de neumáticos", days = 14),
        Part("fork_oil", "fork", "Aceite de horquilla", km = if (dirt) 5000 else 20000, days = 730),
        Part("shock_service", "rear_shock", "Revisión del amortiguador", km = if (dirt) 10000 else 20000, days = 730),
        Part("battery_check", "battery", "Revisión de batería", days = 180),
        Part("lights_check", "lights", "Luces e intermitentes", days = 30),
    )
    if (cooling == Cooling.liquid) list += Part("coolant", "cooling", "Refrigerante", days = 730)
    list += when (drive) {
        Drive.chain -> listOf(
            Part("chain_lube", "drivetrain", "Engrase de cadena", km = if (dirt) 300 else 600),
            Part("chain_tension", "drivetrain", "Tensión de cadena", km = 1000),
            Part("chain_kit", "drivetrain", "Kit de arrastre", km = if (dirt) 8000 else 20000),
        )
        Drive.belt -> listOf(
            Part("belt_tension", "drivetrain", "Tensión de correa", km = 8000),
            Part("drive_belt", "drivetrain", "Correa de transmisión", km = 50000, days = 1825),
        )
        Drive.shaft -> listOf(Part("shaft_oil", "drivetrain", "Aceite del cardán", km = 20000, days = 730))
        Drive.cvt -> listOf(
            Part("cvt_belt", "drivetrain", "Correa del variador", km = 15000),
            Part("cvt_rollers", "drivetrain", "Rodillos del variador", km = 15000),
            Part("gear_oil", "drivetrain", "Aceite de transmisión", km = 10000, days = 730),
        )
    }
    return list
}

fun defaultDrive(category: Category) = if (category == Category.scooter) Drive.cvt else Drive.chain
fun defaultCooling(category: Category) = if (category == Category.offroad || category == Category.custom) Cooling.air else Cooling.liquid

/** Which drawing a category uses. Ids must match assets/bike_<name>.json. */
fun silhouette(c: Category) = when (c) {
    Category.naked -> "naked"
    Category.sport, Category.touring -> "sport"
    Category.trail, Category.offroad -> "trail"
    Category.custom -> "custom"
    Category.scooter -> "scooter"
}
