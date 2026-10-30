package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MaintenanceLogTest {
    private val today = LocalDate.parse("2026-10-25")
    private val bike = Bike("b", "Y", "M", null, Category.naked, Drive.chain, Cooling.liquid, defaultParts(Category.naked, Drive.chain, Cooling.liquid),
        listOf(Reading("2026-10-25", 12000, 1))).markDone("chain_lube", 11500, today).markDone("front_pads", 1000, today).markDone("engine_oil", 11000, today)
    private var n = 0
    private val ids = { "id${n++}" }

    private fun log(b: Bike = bike, date: String = today.toString(), km: Int = 12000, jobs: List<LoggedJob> = listOf(LoggedJob("engine_oil", cost = 50000.0), LoggedJob("oil_filter"), LoggedJob("wash", "Wash"))) =
        b.logMaintenance(jobs, date, km, today, Venue.shop, " Taller de la 80 ", "/f.jpg", null, ids, now = 99)

    @Test fun everyJobSharesOneGroupAndUpdatesItsPart() {
        val b = log()
        val added = b.history.filter { r -> bike.history.none { it.id == r.id } }
        assertEquals(3, added.size)
        assertEquals(1, added.map { it.group }.distinct().size)
        assertNotNull(added.first().group)
        assertTrue(added.all { it.venue == Venue.shop && it.place == "Taller de la 80" && it.photo == "/f.jpg" && it.km == 12000 })
        assertEquals("Wash", added.last().partName)
        assertNull(added.first().partName)
        assertEquals(12000, b.parts.first { it.id == "engine_oil" }.lastKm)
        assertEquals(12000, b.parts.first { it.id == "oil_filter" }.lastKm)
        assertEquals(today.toString(), b.parts.first { it.id == "oil_filter" }.lastDate)
        assertEquals(50000.0, b.totalCost, 0.0)
        assertEquals(b, GarageCodec.decode(GarageCodec.encode(Garage(bikes = listOf(b)))).bikes.single())
    }

    @Test fun addsAReadingOnlyWhenTodayAndAboveTheEstimate() {
        assertEquals(bike.readings, log(km = 12000).readings)
        assertEquals(bike.readings, log(km = 11900).readings)
        assertEquals(bike.readings, log(date = "2026-10-20", km = 12500).readings)
        val above = log(km = 12500)
        assertEquals(bike.readings + Reading(today.toString(), 12500, 99), above.readings)
        assertEquals(12500.0, estimate(above, today).km, 0.0)
    }

    @Test fun rejectsFutureDatesAndEmptyLogs() {
        assertThrows(IllegalArgumentException::class.java) { log(date = "2026-10-26") }
        assertThrows(IllegalArgumentException::class.java) { log(jobs = emptyList()) }
    }

    @Test fun undoRestoresTheExactPreviousGarage() {
        val other = bike.copy(id = "other")
        val before = Garage(bikes = listOf(bike, other), selectedId = "b")
        val after = before.copy(bikes = listOf(log(km = 12500), other))
        assertNotEquals(before, after)
        val undone = after.copy(bikes = after.bikes.map { if (it.id == "b") it.undoMaintenance(bike, after.bikes.first()) else it })
        assertEquals(before, undone)
        assertEquals(GarageCodec.encode(before), GarageCodec.encode(undone))
    }

    @Test fun undoAfterARideOnlyTakesBackTheLog() {
        val after = log(km = 12500)
        val ride = Ride(today.toString(), 12.0, at = 200)
        val undone = after.copy(rides = after.rides + ride).undoMaintenance(bike, after)
        assertEquals(bike.copy(rides = bike.rides + ride), undone)
    }

    @Test fun undoReopensTheTallerList() {
        val order = WorkOrder("o", 1, today.toString(), 12000, listOf("engine_oil"))
        val start = bike.copy(orders = listOf(order))
        val after = start.logMaintenance(listOf(LoggedJob("engine_oil")), today.toString(), 12000, today, null, "", null, "o", ids)
        assertNull(after.openOrder())
        assertEquals(start, after.undoMaintenance(start, after))
        assertEquals(start.copy(rides = listOf(Ride(today.toString(), 1.0))), after.copy(rides = listOf(Ride(today.toString(), 1.0))).undoMaintenance(start, after))
    }

    @Test fun quickPicksFollowStatusAndTheBikesParts() {
        val odo = 12000.0
        // engine_oil 11000 of 6000 km: ok; front_pads 1000 of 10000: overdue; chain_lube 500 of 600 km: coming due.
        assertEquals(listOf("front_pads"), bike.quickPick(QuickPick.overdue, odo, today))
        assertEquals(listOf("chain_lube"), bike.quickPick(QuickPick.soon, odo, today))
        assertEquals(listOf("engine_oil", "oil_filter"), bike.quickPick(QuickPick.oil, odo, today))
        assertEquals(listOf("engine_oil", "oil_filter", "air_filter", "spark_plugs", "chain_lube"), bike.quickPick(QuickPick.full, odo, today))
        val belt = bike.copy(parts = bike.parts.filterNot { it.id == "chain_lube" || it.id == "oil_filter" })
        assertEquals(listOf("engine_oil"), belt.quickPick(QuickPick.oil, odo, today))
        assertEquals(listOf("engine_oil", "air_filter", "spark_plugs"), belt.quickPick(QuickPick.full, odo, today))
    }

    @Test fun pendingIsTheTallerListWithCustomJobs() {
        val odo = 12000.0
        assertEquals(bike.dueForWorkshop(odo, today), bike.quickPick(QuickPick.pending, odo, today))
        val order = WorkOrder("o", 1, today.toString(), 12000, listOf("oil_filter", "gone"), customItems = listOf(PendingJob("c", "Wash")))
        assertEquals(listOf("oil_filter", "c"), bike.copy(orders = listOf(order)).quickPick(QuickPick.pending, odo, today))
    }
}
