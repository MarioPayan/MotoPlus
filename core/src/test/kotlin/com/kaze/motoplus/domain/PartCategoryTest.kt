package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PartCategoryTest {
    private val today = LocalDate.parse("2026-10-24")
    private fun bike(parts: List<Part> = defaultParts(Category.naked, Drive.chain, Cooling.liquid)) = Bike(
        id = "b", brand = "Yamaha", model = "MT-07", category = Category.naked, drive = Drive.chain, cooling = Cooling.liquid,
        parts = parts, readings = listOf(Reading("2026-01-01", 10000), Reading("2026-10-01", 12000)),
    )

    @Test fun everyTemplatePartHasAFixedCategory() {
        for (c in Category.entries) for (d in Drive.entries) for (k in Cooling.entries) {
            val unmapped = defaultParts(c, d, k).filter { it.partCategory == PartCategory.other }
            assertEquals("$c $d $k", emptyList<Part>(), unmapped)
        }
    }

    @Test fun categoriesComeInFixedOrderAndFrontBeforeRear() {
        val sorted = defaultParts(Category.naked, Drive.chain, Cooling.liquid).shuffled(java.util.Random(7)).sortedByCategory()
        assertEquals(
            listOf("front_tire", "rear_tire", "tire_pressure", "front_pads", "rear_pads", "brake_fluid",
                "engine_oil", "oil_filter", "spark_plugs", "valve_clearance", "air_filter", "coolant",
                "chain_lube", "chain_tension", "chain_kit", "fork_oil", "shock_service", "battery_check", "lights_check"),
            sorted.map { it.id },
        )
        val groups = sorted.byCategory().map { it.first }
        assertEquals(groups.sortedBy { it.ordinal }, groups)
    }

    @Test fun customPartsDefaultToOtrosOrKeepTheirCategoryAfterTemplateOnes() {
        val b = bike().copy(parts = listOf(Part("custom_3", "", "Sin categoría", km = 100)) + bike().parts)
            .addCustomPart("custom_1", "Escape", PartCategory.other, 5000, null, IntervalUnit.days, null)
            .addCustomPart("custom_2", "Manillar", PartCategory.brakes, null, 365, IntervalUnit.years, "1")
        val groups = b.parts.byCategory().toMap()
        assertEquals(listOf("front_pads", "rear_pads", "brake_fluid", "custom_2"), groups.getValue(PartCategory.brakes).map { it.id })
        assertEquals(listOf("custom_3", "custom_1"), groups.getValue(PartCategory.other).map { it.id })
        assertEquals("front_brake", b.parts.first { it.id == "custom_2" }.zone)
        assertTrue(b.parts.first { it.id == "custom_2" }.isCustom)
        assertFalse(b.parts.first { it.id == "engine_oil" }.isCustom)
    }

    @Test fun unwatchedPartsLeaveHealthZonesAlertsAndPicksButKeepHistory() {
        val odo = 12000.0
        val overdue = bike().parts.map { if (it.id == "engine_oil") it.copy(lastKm = 1000, lastDate = "2020-01-01") else it.copy(lastKm = 12000, lastDate = "2026-10-20") }
        val watched = bike(overdue).addRecord(ServiceRecord("r1", "engine_oil", "2020-01-01", 1000))
        val off = watched.watch("engine_oil", false)

        assertTrue(health(watched, odo, today) < 100)
        assertEquals(100, health(off, odo, today))
        assertEquals(Status.bad, zoneStates(watched, odo, today).getValue("engine").status)
        val zone = zoneStates(off, odo, today).getValue("engine")
        assertEquals(Status.ok, zone.status)
        assertTrue(zone.parts.none { it.first.id == "engine_oil" })
        assertTrue(zone.all.any { it.first.id == "engine_oil" })
        assertEquals(listOf("engine_oil"), upNext(watched, odo, today).map { it.first.id })
        assertTrue(upNext(off, odo, today).isEmpty())
        assertEquals(listOf("engine_oil"), dueAlerts(watched, odo, today).first.map { it.first.id })
        assertTrue(dueAlerts(off, odo, today).first.isEmpty())
        assertEquals(Status.ok, dueAlerts(off, odo, today).second["engine_oil"])
        assertEquals(listOf("engine_oil"), watched.dueForWorkshop(odo, today))
        assertTrue(off.dueForWorkshop(odo, today).isEmpty())
        assertEquals(listOf("engine_oil"), watched.quickPick(QuickPick.overdue, odo, today))
        assertTrue(off.quickPick(QuickPick.overdue, odo, today).isEmpty())
        assertEquals(1, off.history.size)
        assertEquals(1000, off.parts.first { it.id == "engine_oil" }.lastKm)
    }

    @Test fun categorySwitchAndPresets() {
        val b = bike().watchCategory(PartCategory.engine, false)
        assertTrue(b.parts.filter { it.partCategory == PartCategory.engine }.none { it.watched })
        assertTrue(b.parts.filter { it.partCategory != PartCategory.engine }.all { it.watched })
        assertNull(b.preset)
        val essential = b.withPreset(WatchPreset.essential)
        assertEquals(WatchPreset.essential, essential.preset)
        assertEquals(setOf("front_tire", "rear_tire", "tire_pressure", "front_pads", "rear_pads", "brake_fluid", "engine_oil", "oil_filter",
            "chain_lube", "chain_tension", "chain_kit"), essential.watchedParts.map { it.id }.toSet())
        assertEquals(WatchPreset.all, essential.withPreset(WatchPreset.all).preset)
        assertTrue(essential.withPreset(WatchPreset.none).watchedParts.isEmpty())
    }

    @Test fun drivetrainSwapAddsRemovesAndKeepsHistoryUnderItsName() {
        val b = bike().addRecord(ServiceRecord("r1", "chain_lube", "2026-10-01", 12000))
            .addCustomPart("custom_1", "Escape", PartCategory.other, 5000, null, IntervalUnit.days, null)
            .copy(orders = listOf(WorkOrder("o", 1, "2026-10-01", 12000, listOf("chain_kit", "engine_oil"), itemNotes = mapOf("chain_kit" to "x"))))
        val swap = b.withDrivetrain(Drive.cvt, Cooling.air) { "Label ${it.id}" }
        assertEquals(setOf("cvt_belt", "cvt_rollers", "gear_oil"), swap.added.map { it.id }.toSet())
        assertEquals(setOf("chain_lube", "chain_tension", "chain_kit", "coolant"), swap.removed.map { it.id }.toSet())
        assertTrue(swap.bike.parts.any { it.id == "custom_1" })
        assertEquals("Label chain_lube", swap.bike.history.single().partName)
        assertEquals(listOf("engine_oil"), swap.bike.orders.single().partIds)
        assertTrue(swap.bike.orders.single().itemNotes.isEmpty())
        assertEquals(Drive.cvt, swap.bike.drive)
        assertEquals(Cooling.air, swap.bike.cooling)
        assertSame(b, b.withDrivetrain(Drive.chain, Cooling.liquid) { "" }.bike)
    }

    @Test fun oldGaragesDecodeAsWatchedWithoutCategory() {
        val g = GarageCodec.decode("""{"schema":3,"bikes":[{"id":"b","brand":"Y","model":"M","category":"naked","drive":"chain","cooling":"liquid",
            "parts":[{"id":"engine_oil","zone":"engine","name":"Aceite","km":6000}],"readings":[{"date":"2026-10-01","km":1}]}]}""")
        val p = g.bikes.single().parts.single()
        assertTrue(p.watched)
        assertNull(p.category)
        assertEquals(PartCategory.engine, p.partCategory)
        val back = GarageCodec.decode(GarageCodec.encode(g.copy(bikes = g.bikes.map { it.watch("engine_oil", false).addCustomPart("custom_x", "Escape", PartCategory.other, 1, null, IntervalUnit.days, null) })))
        assertFalse(back.bikes.single().parts.first().watched)
        assertEquals(PartCategory.other, back.bikes.single().parts.last().category)
    }

    @Test fun preferencesCountHiddenAndRestore() {
        val p = Preferences()
        assertEquals(0, p.hidden)
        val minimal = p.minimal()
        assertEquals(Feature.entries.size, minimal.hidden)
        assertEquals(Feature.entries.size + 1, minimal.copy(drawing = false).hidden)
        assertEquals(0, minimal.copy(drawing = false, reminders = false, checks = emptySet()).showAll().hidden)
        assertEquals(CHECK_STEPS.toSet(), minimal.copy(checks = emptySet()).showAll().checks)
        assertEquals(listOf("tires", "papers"), p.copy(checks = setOf("tires", "papers")).checkSteps(CHECK_STEPS))
        assertEquals(listOf("tires"), p.copy(checks = setOf("tires", "papers"), features = Feature.entries.toSet() - Feature.papers).checkSteps(CHECK_STEPS))
        val g = Garage(bikes = listOf(bike())).watchAll(WatchPreset.essential)
        assertEquals(WatchPreset.essential, g.bikes.single().preset)
    }
}
