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
        Part("aceite", "motor", "Aceite de motor", km = if (dirt) 1500 else if (small) 4000 else 6000, days = 365),
        Part("filtro_aceite", "motor", "Filtro de aceite", km = if (dirt) 3000 else if (small) 8000 else 12000, days = 730),
        Part("bujias", "motor", "Bujías", km = if (dirt) 6000 else 12000, days = 730),
        Part("valvulas", "motor", "Holgura de válvulas", km = if (dirt) 3000 else if (sport) 24000 else 18000),
        Part("filtro_aire", "admision", "Filtro de aire", km = if (dirt) 500 else 12000, days = if (dirt) null else 730),
        Part("pastillas_del", "freno_del", "Pastillas delanteras", km = if (sport) 7000 else 10000),
        Part("liquido_frenos", "freno_del", "Líquido de frenos", days = 730),
        Part("pastillas_tras", "freno_tras", "Pastillas traseras", km = 15000),
        Part("neumatico_del", "rueda_del", "Neumático delantero", km = if (sport) 8000 else 12000, days = 1825),
        Part("neumatico_tras", "rueda_tras", "Neumático trasero", km = if (sport) 5000 else 9000, days = 1825),
        Part("presion", "rueda_tras", "Presión de neumáticos", days = 14),
        Part("horquilla", "horquilla", "Aceite de horquilla", km = if (dirt) 5000 else 20000, days = 730),
        Part("amortiguador", "suspension_tras", "Revisión del amortiguador", km = if (dirt) 10000 else 20000, days = 730),
        Part("bateria", "bateria", "Revisión de batería", days = 180),
        Part("luces", "faro", "Luces e intermitentes", days = 30),
    )
    if (cooling == Cooling.liquid) list += Part("refrigerante", "radiador", "Refrigerante", days = 730)
    list += when (drive) {
        Drive.chain -> listOf(
            Part("cadena_engrase", "cadena", "Engrase de cadena", km = if (dirt) 300 else 600),
            Part("cadena_tension", "cadena", "Tensión de cadena", km = 1000),
            Part("kit_arrastre", "cadena", "Kit de arrastre", km = if (dirt) 8000 else 20000),
        )
        Drive.belt -> listOf(
            Part("correa_tension", "cadena", "Tensión de correa", km = 8000),
            Part("correa", "cadena", "Correa de transmisión", km = 50000, days = 1825),
        )
        Drive.shaft -> listOf(Part("aceite_cardan", "cadena", "Aceite del cardán", km = 20000, days = 730))
        Drive.cvt -> listOf(
            Part("correa_variador", "cadena", "Correa del variador", km = 15000),
            Part("rodillos", "cadena", "Rodillos del variador", km = 15000),
            Part("aceite_transmision", "cadena", "Aceite de transmisión", km = 10000, days = 730),
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
