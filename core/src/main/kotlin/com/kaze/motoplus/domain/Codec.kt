package com.kaze.motoplus.domain

import kotlinx.serialization.json.*

/**
 * The garage as JSON: the file on the phone, exports and backups all use this one format.
 * Anything older than [Garage.SCHEMA] is migrated on read, so an old export still imports.
 */
object GarageCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun encode(g: Garage): String = json.encodeToString(g)

    fun decode(text: String): Garage = json.decodeFromJsonElement(migrate(json.parseToJsonElement(text).jsonObject))

    fun migrate(root: JsonObject): JsonObject {
        var o = root
        if ((o["schema"]?.jsonPrimitive?.intOrNull ?: 1) < 2) o = v1ToV2(o)
        return o
    }

    // v1 used Spanish ids and Spanish JSON keys in AI reports.
    private val zones = mapOf(
        "rueda_tras" to "rear_wheel", "freno_tras" to "rear_brake", "cadena" to "drivetrain", "suspension_tras" to "rear_shock",
        "bateria" to "battery", "admision" to "intake", "motor" to "engine", "radiador" to "cooling", "horquilla" to "fork",
        "freno_del" to "front_brake", "rueda_del" to "front_wheel", "faro" to "lights",
    )
    private val parts = mapOf(
        "aceite" to "engine_oil", "filtro_aceite" to "oil_filter", "bujias" to "spark_plugs", "valvulas" to "valve_clearance",
        "filtro_aire" to "air_filter", "pastillas_del" to "front_pads", "liquido_frenos" to "brake_fluid", "pastillas_tras" to "rear_pads",
        "neumatico_del" to "front_tire", "neumatico_tras" to "rear_tire", "presion" to "tire_pressure", "horquilla" to "fork_oil",
        "amortiguador" to "shock_service", "bateria" to "battery_check", "luces" to "lights_check", "refrigerante" to "coolant",
        "cadena_engrase" to "chain_lube", "cadena_tension" to "chain_tension", "kit_arrastre" to "chain_kit", "correa_tension" to "belt_tension",
        "correa" to "drive_belt", "aceite_cardan" to "shaft_oil", "correa_variador" to "cvt_belt", "rodillos" to "cvt_rollers",
        "aceite_transmision" to "gear_oil",
    )
    private val levels = mapOf("baja" to "low", "media" to "medium", "alta" to "high")

    private fun v1ToV2(o: JsonObject): JsonObject {
        val bikes = o["bikes"]?.jsonArray?.map { bike(it.jsonObject) } ?: emptyList()
        return JsonObject(o + ("schema" to JsonPrimitive(2)) + ("bikes" to JsonArray(bikes)))
    }

    private fun bike(b: JsonObject): JsonObject {
        val out = b.toMutableMap()
        b.arr("parts")?.let { a -> out["parts"] = JsonArray(a.map { p -> p.obj.with("id" to p.obj.str("id")?.let(::part), "zone" to p.obj.str("zone")?.let(::zone)) }) }
        b.arr("history")?.let { a -> out["history"] = JsonArray(a.map { r -> r.obj.with("partId" to r.obj.str("partId")?.let(::part)) }) }
        b["notified"]?.jsonObject?.let { n -> out["notified"] = JsonObject(n.mapKeys { part(it.key) }) }
        b.arr("rides")?.let { a -> out["rides"] = JsonArray(a.map { ride(it.obj) }) }
        b.arr("reports")?.let { a -> out["reports"] = JsonArray(a.map { r -> JsonObject(r.obj + ("data" to report(r.obj["data"]!!.jsonObject))) }) }
        return JsonObject(out)
    }

    private fun ride(r: JsonObject): JsonObject {
        val src = r.str("source") ?: ""
        val out = r.toMutableMap().apply { remove("source") }
        when {
            src.startsWith("Bluetooth") -> { out["trigger"] = JsonPrimitive("bluetooth"); out["device"] = JsonPrimitive(src.substringAfter(": ", "").ifBlank { null }) }
            src == "Sensor de movimiento" -> out["trigger"] = JsonPrimitive("motion")
            else -> out["trigger"] = JsonPrimitive("manual")
        }
        return JsonObject(out)
    }

    private fun report(d: JsonObject): JsonObject = buildJsonObject {
        put("v", REPORT_VERSION)
        put("title", d.str("titulo") ?: "")
        put("urgency", levels[d.str("urgencia")] ?: "medium")
        put("summary", d.str("resumen") ?: "")
        putJsonArray("causes") {
            d.arr("causas")?.forEach { c ->
                addJsonObject {
                    put("cause", c.obj.str("causa") ?: "")
                    put("likelihood", levels[c.obj.str("probabilidad")] ?: "medium")
                    c.obj.str("zona")?.let { put("zone", zone(it)) }
                }
            }
        }
        putJsonArray("checks") { d.arr("comprobaciones")?.forEach { add(it) } }
        put("advice", d.str("recomendacion") ?: "")
    }

    private fun zone(id: String) = zones[id] ?: id
    private fun part(id: String) = parts[id] ?: id
    private val JsonElement.obj get() = jsonObject
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray
    private fun JsonObject.with(vararg kv: Pair<String, String?>) =
        JsonObject(this + kv.filter { it.second != null }.associate { it.first to JsonPrimitive(it.second) })
}
