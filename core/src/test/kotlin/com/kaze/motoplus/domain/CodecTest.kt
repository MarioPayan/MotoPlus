package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test

class CodecTest {
    // A garage.json as version 0.9.0 wrote it: Spanish ids, ride sources as text, Spanish report keys.
    private val v1 = """
        {"bikes":[{"id":"b1","brand":"Yamaha","model":"MT-07","year":2018,"category":"naked","drive":"chain","cooling":"liquid",
          "parts":[{"id":"aceite","zone":"motor","name":"Aceite de motor","km":6000,"days":365,"lastKm":12000,"lastDate":"2026-10-01"},
                   {"id":"cadena_engrase","zone":"cadena","name":"Engrase de cadena","km":600}],
          "readings":[{"date":"2026-10-01","km":12000,"at":1}],
          "rides":[{"date":"2026-10-02","km":12.5,"source":"Bluetooth: Cardo","wet":false,"at":2},
                   {"date":"2026-10-03","km":3.0,"source":"Sensor de movimiento","at":3},
                   {"date":"2026-10-04","km":1.0,"source":"Manual"}],
          "gpsFactor":1.0,
          "reports":[{"at":"2026-10-05","km":12020,"data":{"v":1,"titulo":"Chirrido","urgencia":"alta","resumen":"Pastillas",
             "causas":[{"causa":"Pastillas gastadas","probabilidad":"alta","zona":"freno_del"}],"comprobaciones":["Mirar grosor"],"recomendacion":"Taller"}}],
          "history":[{"id":"r1","partId":"aceite","date":"2026-10-01","km":12000,"cost":60.0}],
          "notified":{"aceite":"ok","cadena_engrase":"warn"}}],
         "selectedId":"b1","tracking":{"enabled":true,"devices":{"AA:BB":"Cardo"}}}
    """.trimIndent()

    @Test fun migratesV1() {
        val g = GarageCodec.decode(v1)
        val b = g.bikes.single()
        assertEquals(Garage.SCHEMA, g.schema)
        assertEquals(listOf("engine_oil" to "engine", "chain_lube" to "drivetrain"), b.parts.map { it.id to it.zone })
        assertEquals("engine_oil", b.history.single().partId)
        assertEquals(mapOf("engine_oil" to Status.ok, "chain_lube" to Status.warn), b.notified)
        assertEquals(listOf(RideTrigger.bluetooth, RideTrigger.motion, RideTrigger.manual), b.rides.map { it.trigger })
        assertEquals("Cardo", b.rides[0].device)
        val r = b.reports.single().data
        assertEquals(Level.high, r.urgency)
        assertEquals("Pastillas", r.summary)
        assertEquals(Cause("Pastillas gastadas", Level.high, "front_brake"), r.causes.single())
        assertEquals(listOf("Mirar grosor"), r.checks)
        assertEquals(Tracking(motion = true, bluetooth = true, devices = mapOf("AA:BB" to "Cardo")), g.tracking)
        assertEquals(12000, b.parts.first().lastKm)
    }

    // A garage.json as 0.10.0 wrote it: one switch for automatic rides.
    private fun v2(enabled: Boolean) = """
        {"schema":2,"bikes":[],"tracking":{"enabled":$enabled,"devices":{"AA:BB":"Cardo"}}}
    """.trimIndent()

    @Test fun migratesTrackingSwitchV2() {
        val on = GarageCodec.decode(v2(true))
        assertEquals(Garage.SCHEMA, on.schema)
        assertEquals(Tracking(motion = true, bluetooth = true, devices = mapOf("AA:BB" to "Cardo")), on.tracking)
        assertEquals(Tracking(devices = mapOf("AA:BB" to "Cardo")), GarageCodec.decode(v2(false)).tracking)
    }

    @Test fun keepsCurrentTracking() {
        val g = Garage(tracking = Tracking(motion = true, carDevices = mapOf("CC:DD" to "Coche"), androidAuto = true))
        assertEquals(g, GarageCodec.decode(GarageCodec.encode(g)))
    }

    @Test fun roundTrips() {
        val g = GarageCodec.decode(v1)
        assertEquals(g, GarageCodec.decode(GarageCodec.encode(g)))
    }
}
