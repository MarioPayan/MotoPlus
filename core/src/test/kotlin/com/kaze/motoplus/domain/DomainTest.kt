package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Base64

class DomainTest {
    private val today = LocalDate.parse("2026-10-24")
    private fun bike(rides: List<Ride> = emptyList(), parts: List<Part> = emptyList()) = Bike(
        id = "b", brand = "Yamaha", model = "MT-07", year = 2021,
        category = Category.naked, drive = Drive.chain, cooling = Cooling.liquid, parts = parts,
        readings = listOf(Reading("2026-08-02", 11020), Reading("2026-09-14", 11750), Reading("2026-10-10", 12210)),
        rides = rides, gpsFactor = 1.03,
    )

    @Test fun estimateUsesRidesSinceLastReading() {
        val e = estimate(bike(listOf(Ride("2026-10-09", 99.0), Ride("2026-10-12", 34.0), Ride("2026-10-18", 142.0))), today)
        assertEquals(12210 + 176 * 1.03, e.km, 0.01)
        assertEquals(176.0, e.gpsSinceReading, 0.0)
    }

    @Test fun estimateProjectsAverageWithoutRides() {
        val e = estimate(bike(), today)
        val rate = (12210 - 11020) / 69.0
        assertEquals(12210 + rate * 14, e.km, 0.01)
        assertTrue(e.margin > 0)
    }

    @Test fun singleReadingIsExact() {
        val b = bike().copy(readings = listOf(Reading("2026-10-24", 500)))
        assertEquals(500.0, estimate(b, today).km, 0.0)
    }

    @Test fun readingRecalibratesGps() {
        val b = bike(listOf(Ride("2026-10-12", 100.0))).withReading(12315, today)
        assertEquals(1.05, b.gpsFactor, 1e-9)
        assertEquals(12315.0, estimate(b, today).km, 0.0)
    }

    @Test fun sameDayRideCountsAfterReading() {
        val b = bike().copy(readings = listOf(Reading("2026-10-24", 12000, at = 1_000)),
            rides = listOf(Ride("2026-10-24", 10.0, at = 500), Ride("2026-10-24", 20.0, at = 2_000)))
        assertEquals(12000 + 20 * 1.03, estimate(b, today).km, 1e-9)
    }

    @Test fun absurdReadingKeepsFactor() {
        val b = bike(listOf(Ride("2026-10-12", 100.0))).withReading(13000, today)
        assertEquals(1.03, b.gpsFactor, 1e-9)
    }

    @Test fun partStateTakesWorstDimension() {
        val p = Part("a", "engine", "Aceite", km = 6000, days = 365, lastKm = 9000, lastDate = "2025-10-01")
        val s = partState(p, 10000.0, today)
        assertEquals(Status.bad, s.status)
        assertEquals(Measure.days, s.measure); assertEquals(-23, s.left)
        assertEquals(Status.none, partState(Part("b", "engine", "Bujías", km = 1), 0.0, today).status)
        assertEquals(Status.warn, partState(Part("c", "drivetrain", "Engrase", km = 600, lastKm = 11500), 12050.0, today).status)
    }

    @Test fun soonIsAShareOfTheIntervalLeftByKmOrTime() {
        val oil = Part("a", "engine", "Aceite", km = 6000, days = 365, lastKm = 10000, lastDate = "2026-10-01")
        // 10 % by default: 600 km of 6.000 or 36,5 days of 365, whichever comes first.
        assertEquals(Status.warn, partState(oil, 15400.0, today).status)
        assertEquals(Status.ok, partState(oil, 15399.0, today).status)
        assertEquals(Status.warn, partState(oil.copy(lastDate = today.minusDays(329).toString()), 10000.0, today).status)
        assertEquals(Status.ok, partState(oil.copy(lastDate = today.minusDays(328).toString()), 10000.0, today).status)
        // The rider's setting replaces it, km and time on their own.
        assertEquals(Status.ok, partState(oil, 14600.0, today).status)
        assertEquals(Status.warn, partState(oil, 14600.0, today, SoonThreshold(kmPct = 25, timePct = 10)).status)
        assertEquals(Status.ok, partState(oil.copy(lastDate = today.minusDays(300).toString()), 10000.0, today).status)
        assertEquals(Status.warn, partState(oil.copy(lastDate = today.minusDays(300).toString()), 10000.0, today, SoonThreshold(kmPct = 10, timePct = 20)).status)
        // Overdue stays overdue whatever the threshold.
        assertEquals(Status.bad, partState(oil, 16000.0, today, SoonThreshold(50, 50)).status)
    }

    @Test fun dueSoonPutsOverdueFirstAndSkipsTheRest() {
        val b = bike(parts = listOf(
            Part("soon", "drivetrain", "Engrase", km = 600, lastKm = 11760),            // 50 left: coming due, 92 % used
            Part("late", "front_brake", "Pastillas", km = 10000, lastKm = 2000),         // overdue, 103 % used
            Part("fine", "engine", "Aceite", km = 6000, lastKm = 12000),                 // ok
            Part("never", "battery", "Batería", days = 180),                             // never recorded
            Part("hidden", "fork", "Horquilla", km = 1000, lastKm = 11000, watched = false),
            Part("napping", "lights", "Luces", km = 1000, lastKm = 11000, snoozedUntil = "2026-11-01"),
        ))
        assertEquals(listOf("late", "soon"), b.dueSoon(12310.0, today).map { it.first.id })
        assertEquals(listOf("late"), b.dueSoon(12310.0, today, SoonThreshold(5, 5)).map { it.first.id })
        assertTrue(bike(parts = listOf(Part("fine", "engine", "Aceite", km = 6000, lastKm = 12000))).dueSoon(12310.0, today).isEmpty())
    }

    @Test fun zoneTakesWorstPart() {
        val b = bike(parts = listOf(
            Part("x", "drivetrain", "Engrase", km = 600, lastKm = 12100),
            Part("y", "drivetrain", "Tensión", km = 1000, lastKm = 11000),
        ))
        val z = zoneStates(b, 12300.0, today)
        assertEquals(Status.bad, z.getValue("drivetrain").status)
        assertEquals(Status.ok, z.getValue("engine").status)
        assertEquals("y", z.getValue("drivetrain").parts.first().first.id)
    }

    @Test fun templatesOnlyUseDrawnZones() {
        for (c in Category.entries) for (d in Drive.entries) for (k in Cooling.entries) {
            val drawn = File("../app/src/main/assets/bike_${silhouette(c)}.json").readText()
            for (p in defaultParts(c, d, k)) {
                assertTrue(p.zone in Zone.ids)
                assertTrue("zone ${p.zone} missing in drawing", drawn.contains("\"z\":\"${p.zone}\""))
                assertTrue(p.km != null || p.days != null)
            }
        }
        assertTrue(defaultParts(Category.naked, Drive.chain, Cooling.air).none { it.zone == "cooling" })
    }

    private val sample = """{"v":2,"title":"Chirrido","urgency":"altísima","summary":"Pastillas","causes":[{"cause":"Pastillas","likelihood":"high","zone":"front_brake"},{"cause":"Otra","zone":"inventada"}],"extra":1}"""

    @Test fun parsesFencedBlock() {
        val r = parseReport("Texto…\n```motoplus\n$sample\n```\nfin") as ParseResult.Ok
        assertEquals(Level.medium, r.report.urgency)
        assertEquals("front_brake", r.report.causes[0].zone)
        assertNull(r.report.causes[1].zone)
    }

    @Test fun parsesLink() {
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sample.toByteArray())
        assertTrue(parseReport("mira https://motoplus.app/i#r=$b64") is ParseResult.Ok)
    }

    @Test fun rejectsGarbage() {
        assertEquals(ParseResult.Error(ReportError.notFound), parseReport("hola"))
        assertEquals(ParseResult.Error(ReportError.invalid), parseReport("```motoplus\n{\"v\":2,\n```"))
    }

    @Test fun picksOdometerClosestToEstimate() {
        assertEquals(12473, pickOdometer("ODO 12473 km TRIP 234.5 12:41", 12450.0))
        assertEquals(12473, pickOdometer("ODO 12.473", 12450.0))
        assertNull(pickOdometer("sin números", 100.0))
    }

    @Test fun customIntervalAndSuggestion() {
        val b = bike(parts = defaultParts(Category.naked, Drive.chain, Cooling.liquid)).withInterval("engine_oil", 5000, null)
        val p = b.parts.first { it.id == "engine_oil" }
        assertEquals(5000, p.km); assertNull(p.days)
        assertEquals(6000, b.suggested("engine_oil")!!.km)
        assertNull(b.suggested("inventada"))
    }

    @Test fun formatsKm() = assertEquals("12.473", fmtKm(12473))
}

class HistoryTest {
    private val today = LocalDate.parse("2026-10-24")
    private val base = Bike(
        id = "b", brand = "Yamaha", model = "MT-07", category = Category.naked, drive = Drive.chain, cooling = Cooling.liquid,
        parts = defaultParts(Category.naked, Drive.chain, Cooling.liquid), readings = listOf(Reading("2026-10-24", 12000)),
    )

    @Test fun lastDoneFollowsNewestRecord() {
        var b = base.addRecord(ServiceRecord("1", "engine_oil", "2026-01-10", 8000, cost = 60.0))
            .addRecord(ServiceRecord("2", "engine_oil", "2026-06-01", 10500, cost = 55.5))
        assertEquals(10500, b.parts.first { it.id == "engine_oil" }.lastKm)
        assertEquals(115.5, b.totalCost, 1e-9)
        b = b.deleteRecord("2")
        assertEquals("2026-01-10", b.parts.first { it.id == "engine_oil" }.lastDate)
        b = b.updateRecord(ServiceRecord("1", "engine_oil", "2026-02-10", 8100))
        assertEquals(8100, b.parts.first { it.id == "engine_oil" }.lastKm)
        b = b.deleteRecord("1")
        assertNull(b.parts.first { it.id == "engine_oil" }.lastKm)
        assertEquals(Status.none, partState(b.parts.first { it.id == "engine_oil" }, 12000.0, today).status)
    }

    @Test fun alertsFireOncePerWorsening() {
        val b = base.markDone("chain_lube", 11450, today)
        val (first, baseline) = dueAlerts(b, 12000.0, today)
        assertEquals(listOf("chain_lube"), first.map { it.first.id })
        val again = dueAlerts(b.copy(notified = baseline), 12000.0, today).first
        assertTrue(again.isEmpty())
        val worse = dueAlerts(b.copy(notified = baseline), 12200.0, today).first
        assertEquals(Status.bad, worse.single().second.status)
    }

    @Test fun markDoneAddsRecord() {
        val b = base.markDone("chain_lube", 12000, today)
        assertEquals(1, b.recordsFor("chain_lube").size)
        assertEquals(12000, b.parts.first { it.id == "chain_lube" }.lastKm)
    }
}

class DistanceTest {
    @Test fun sumsMovementAndDropsNoise() {
        val m = DistanceMeter()
        var t = 0L
        // ~1.1 km north in 100 m steps every 5 s (72 km/h)
        for (i in 0..11) { m.add(40.0 + i * 0.0009, -3.7, 8f, t); t += 5000 }
        assertEquals(1.1, m.km, 0.02)
        val before = m.meters
        m.add(40.0 + 11 * 0.0009 + 0.00002, -3.7, 8f, t)   // 2 m of jitter at a light
        m.add(41.0, -3.7, 8f, t + 1000)                     // 100 km jump in 1 s
        m.add(40.5, -3.7, 120f, t + 2000)                   // useless fix
        assertEquals(before, m.meters, 0.0)
    }

    @Test fun parkedGpsJitterIsNotMovement() {
        val m = DistanceMeter()
        m.add(40.0, -3.7, 25f, 0)
        val parkedSince = m.lastMovingAt
        // 20 m of drift with a 25 m error, the way a phone indoors reports it
        for (i in 1..20) m.add(40.0 + (i % 2) * 0.00018, -3.7, 25f, i * 5000L)
        assertEquals(0.0, m.meters, 0.0)
        assertEquals(parkedSince, m.lastMovingAt)
        // Riding slowly with the same error still counts: 15 m steps add up against the anchor
        for (i in 1..20) m.add(40.0 + i * 0.000135, -3.7, 25f, 100_000 + i * 5000L)
        assertEquals(0.27, m.km, 0.04)
    }

    /** Tester note: a reading of 60.649 typed while parked at the end of a ride, then the ride ended and added its 2,36 km on top. */
    @Test fun aReadingTypedMidRideKeepsOnlyTheKmAfterIt() {
        val m = DistanceMeter()
        var t = 1_000L
        // ~2.3 km before the reading, ~0.6 km after it
        for (i in 0..23) { m.add(40.0 + i * 0.0009, -3.7, 8f, t); t += 5000 }
        val readingAt = t
        for (i in 24..29) { m.add(40.0 + i * 0.0009, -3.7, 8f, t + 1); t += 5000 }
        val bike = Bike("b", "Y", "M", null, Category.naked, Drive.chain, Cooling.liquid, emptyList(), listOf(Reading("2026-11-18", 60000, 10)))
        assertEquals(m.km, bike.rideKm(500, m), 0.0)
        val typed = bike.withReading(60649, LocalDate.parse("2026-11-18"), readingAt)
        val km = typed.rideKm(500, m)
        assertEquals(0.6, km, 0.02)
        assertEquals("the 23 steps before the reading are left out", m.km * 6 / 29, km, 1e-6)
        val ended = typed.copy(rides = listOf(Ride("2026-11-18", km, RideTrigger.motion, at = t)))
        assertEquals(60649 + km, estimate(ended, LocalDate.parse("2026-11-18")).km, 1e-9)
    }

    @Test fun haversineKnownDistance() = assertEquals(111_195.0, haversine(0.0, 0.0, 1.0, 0.0), 50.0)
}
