package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PartProblemTest {
    private val today = LocalDate.parse("2026-11-03")
    private val problem = PartProblem(ProblemKind.broken, "2026-11-03", "Hace ruido")
    private val fresh = Part("engine_oil", "engine", "Aceite", km = 6000, lastKm = 12000, lastDate = "2026-11-01")
    private val otherInZone = Part("spark_plugs", "engine", "Bujías", km = 6000, lastKm = 12000, lastDate = "2026-11-01")
    private val neverDone = Part("oil_filter", "engine", "Filtro", km = 6000)
    private val overdue = Part("chain_lube", "drivetrain", "Engrase", km = 100, lastKm = 1000, lastDate = "2026-01-01")
    private fun bike(vararg parts: Part) = Bike("b", "Yamaha", "MT-07", null, Category.naked, Drive.chain, Cooling.liquid, parts.toList(), listOf(Reading("2026-11-01", 12000)))

    @Test fun reportedPartIsOverdueWhateverItsKmSay() {
        val b = bike(fresh).reportProblem("engine_oil", problem)
        val s = partState(b.parts.single(), 12001.0, today)
        assertEquals(Status.bad, s.status)
        assertEquals(problem, s.problem)
        assertEquals(Status.ok, partState(fresh, 12001.0, today).status)
    }

    @Test fun reportedPartLeadsEveryUrgencyList() {
        val b = bike(overdue, fresh).reportProblem("engine_oil", problem)
        assertEquals("engine_oil", upNext(b, 12001.0, today).first().first.id)
        assertEquals("engine_oil", b.dueForWorkshop(12001.0, today).first())
        assertTrue("engine_oil" in b.quickPick(QuickPick.overdue, 12001.0, today))
    }

    @Test fun zoneShowsTheReportEvenBesideANeverRecordedPart() {
        val b = bike(fresh, otherInZone, neverDone).reportProblem("engine_oil", problem)
        val zone = zoneStates(b, 12001.0, today).getValue("engine")
        assertEquals(Status.bad, zone.status)
        assertEquals("engine_oil", zone.parts.first().first.id)
        assertEquals(Status.none, zoneStates(bike(fresh, neverDone), 12001.0, today).getValue("engine").status)
    }

    @Test fun healthCountsAReportedPartAsOverdue() {
        val b = bike(fresh, otherInZone)
        assertEquals(100, health(b, 12001.0, today))
        assertEquals(50, health(b.reportProblem("engine_oil", problem), 12001.0, today))
    }

    @Test fun reportingBringsAnUnwatchedOrSnoozedPartBack() {
        val hidden = fresh.copy(watched = false, snoozedUntil = "2026-12-01")
        val p = bike(hidden).reportProblem("engine_oil", problem).parts.single()
        assertTrue(p.watched)
        assertFalse(p.snoozed(today))
    }

    @Test fun reportDoesNotNotifyTheRiderWhoJustMadeIt() {
        val b = bike(fresh).reportProblem("engine_oil", problem)
        assertTrue(dueAlerts(b, 12001.0, today).first.isEmpty())
    }

    @Test fun reportingAnUnknownPartChangesNothing() {
        val b = bike(fresh)
        assertEquals(b, b.reportProblem("nope", problem))
    }

    @Test fun clearingByHandGoesBackToItsKm() {
        val b = bike(fresh).reportProblem("engine_oil", problem).clearProblem("engine_oil")
        assertNull(b.parts.single().problem)
        assertEquals(Status.ok, partState(b.parts.single(), 12001.0, today).status)
    }

    @Test fun loggingMaintenanceClearsOnlyThatPart() {
        val b = bike(fresh, otherInZone).reportProblem("engine_oil", problem).reportProblem("spark_plugs", problem)
        val done = b.markDone("engine_oil", 12002, today)
        assertNull(done.parts.first { it.id == "engine_oil" }.problem)
        assertEquals(problem, done.parts.first { it.id == "spark_plugs" }.problem)
        val logged = b.logMaintenance(listOf(LoggedJob("spark_plugs")), "2026-11-03", 12002, today, null, "", null, null, { java.util.UUID.randomUUID().toString() })
        assertNull(logged.parts.first { it.id == "spark_plugs" }.problem)
    }

    @Test fun undoingMaintenanceBringsTheReportBack() {
        val before = bike(fresh).reportProblem("engine_oil", problem)
        val after = before.logMaintenance(listOf(LoggedJob("engine_oil")), "2026-11-03", 12002, today, null, "", null, null, { java.util.UUID.randomUUID().toString() })
        assertEquals(problem, after.undoMaintenance(before, after).parts.single().problem)
        val rideSinceThen = after.copy(rides = after.rides + Ride("2026-11-03", 3.0))
        assertEquals(problem, rideSinceThen.undoMaintenance(before, after).parts.single().problem)
    }

    @Test fun oldGarageDecodesWithoutReportsAndReportsRoundTrip() {
        val old = """{"schema":3,"bikes":[{"id":"b","brand":"Yamaha","model":"MT","category":"naked","drive":"chain","cooling":"liquid",
            "parts":[{"id":"engine_oil","zone":"engine","name":"Aceite","km":6000}],"readings":[{"date":"2026-10-01","km":12000}]}]}"""
        val g = GarageCodec.decode(old)
        assertNull(g.bikes.single().parts.single().problem)
        val reported = g.copy(bikes = g.bikes.map { it.reportProblem("engine_oil", problem) })
        assertEquals(reported, GarageCodec.decode(GarageCodec.encode(reported)))
    }
}
