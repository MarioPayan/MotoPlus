package com.kaze.motoplus

import com.kaze.motoplus.domain.*
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
        val e = estimate(bike(listOf(Ride("2026-10-09", 99.0, "x"), Ride("2026-10-12", 34.0, "x"), Ride("2026-10-18", 142.0, "x"))), today)
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
        val b = bike(listOf(Ride("2026-10-12", 100.0, "x"))).withReading(12315, today)
        assertEquals(1.05, b.gpsFactor, 1e-9)
        assertEquals(12315.0, estimate(b, today).km, 0.0)
    }

    @Test fun absurdReadingKeepsFactor() {
        val b = bike(listOf(Ride("2026-10-12", 100.0, "x"))).withReading(13000, today)
        assertEquals(1.03, b.gpsFactor, 1e-9)
    }

    @Test fun partStateTakesWorstDimension() {
        val p = Part("a", "motor", "Aceite", km = 6000, days = 365, lastKm = 9000, lastDate = "2025-10-01")
        val s = partState(p, 10000.0, today)
        assertEquals(Status.bad, s.status)
        assertTrue(s.text.startsWith("Pasado por"))
        assertEquals(Status.none, partState(Part("b", "motor", "Bujías", km = 1), 0.0, today).status)
        assertEquals(Status.warn, partState(Part("c", "cadena", "Engrase", km = 600, lastKm = 11500), 11980.0, today).status)
    }

    @Test fun zoneTakesWorstPart() {
        val b = bike(parts = listOf(
            Part("x", "cadena", "Engrase", km = 600, lastKm = 12100),
            Part("y", "cadena", "Tensión", km = 1000, lastKm = 11000),
        ))
        val z = zoneStates(b, 12300.0, today)
        assertEquals(Status.bad, z.getValue("cadena").status)
        assertEquals(Status.ok, z.getValue("motor").status)
        assertEquals("y", z.getValue("cadena").parts.first().first.id)
    }

    @Test fun templatesOnlyUseDrawnZones() {
        for (c in Category.entries) for (d in Drive.entries) for (k in Cooling.entries) {
            val drawn = File("src/main/assets/bike_${silhouette(c)}.json").readText()
            for (p in defaultParts(c, d, k)) {
                assertTrue(p.zone in ZONES)
                assertTrue("zone ${p.zone} missing in drawing", drawn.contains("\"z\":\"${p.zone}\""))
                assertTrue(p.km != null || p.days != null)
            }
        }
        assertTrue(defaultParts(Category.naked, Drive.chain, Cooling.air).none { it.zone == "radiador" })
    }

    private val sample = """{"v":1,"titulo":"Chirrido","urgencia":"altísima","resumen":"Pastillas","causas":[{"causa":"Pastillas","probabilidad":"alta","zona":"freno_del"},{"causa":"Otra","zona":"inventada"}],"extra":1}"""

    @Test fun parsesFencedBlock() {
        val r = parseReport("Texto…\n```motoplus\n$sample\n```\nfin") as ParseResult.Ok
        assertEquals("media", r.report.urgencia)
        assertEquals("freno_del", r.report.causas[0].zona)
        assertNull(r.report.causas[1].zona)
    }

    @Test fun parsesLink() {
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sample.toByteArray())
        assertTrue(parseReport("mira https://motoplus.app/i#r=$b64") is ParseResult.Ok)
    }

    @Test fun rejectsGarbage() {
        assertTrue(parseReport("hola") is ParseResult.Error)
        assertTrue(parseReport("```motoplus\n{\"v\":1,\n```") is ParseResult.Error)
    }

    @Test fun promptListsPartsAndZones() {
        val b = bike(parts = defaultParts(Category.naked, Drive.chain, Cooling.liquid))
        val p = buildPrompt(b, "chirría", today)
        assertTrue(p.contains("Engrase de cadena [cadena]"))
        assertTrue(p.contains("SÍNTOMA: chirría"))
        assertTrue(p.contains("freno_del"))
    }

    @Test fun picksOdometerClosestToEstimate() {
        assertEquals(12473, pickOdometer("ODO 12473 km TRIP 234.5 12:41", 12450.0))
        assertEquals(12473, pickOdometer("ODO 12.473", 12450.0))
        assertNull(pickOdometer("sin números", 100.0))
    }

    @Test fun customIntervalAndSuggestion() {
        val b = bike(parts = defaultParts(Category.naked, Drive.chain, Cooling.liquid)).withInterval("aceite", 5000, null)
        val p = b.parts.first { it.id == "aceite" }
        assertEquals(5000, p.km); assertNull(p.days)
        assertEquals(6000, b.suggested("aceite")!!.km)
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
        var b = base.addRecord(ServiceRecord("1", "aceite", "2026-01-10", 8000, cost = 60.0))
            .addRecord(ServiceRecord("2", "aceite", "2026-06-01", 10500, cost = 55.5))
        assertEquals(10500, b.parts.first { it.id == "aceite" }.lastKm)
        assertEquals(115.5, b.totalCost, 1e-9)
        b = b.deleteRecord("2")
        assertEquals("2026-01-10", b.parts.first { it.id == "aceite" }.lastDate)
        b = b.updateRecord(ServiceRecord("1", "aceite", "2026-02-10", 8100))
        assertEquals(8100, b.parts.first { it.id == "aceite" }.lastKm)
        b = b.deleteRecord("1")
        assertNull(b.parts.first { it.id == "aceite" }.lastKm)
        assertEquals(Status.none, partState(b.parts.first { it.id == "aceite" }, 12000.0, today).status)
    }

    @Test fun alertsFireOncePerWorsening() {
        val b = base.markDone("cadena_engrase", 11500, today)
        val (first, baseline) = dueAlerts(b, 12000.0, today)
        assertEquals(listOf("cadena_engrase"), first.map { it.first.id })
        val again = dueAlerts(b.copy(notified = baseline), 12000.0, today).first
        assertTrue(again.isEmpty())
        val worse = dueAlerts(b.copy(notified = baseline), 12200.0, today).first
        assertEquals(Status.bad, worse.single().second.status)
    }

    @Test fun markDoneAddsRecord() {
        val b = base.markDone("cadena_engrase", 12000, today)
        assertEquals(1, b.recordsFor("cadena_engrase").size)
        assertEquals(12000, b.parts.first { it.id == "cadena_engrase" }.lastKm)
    }
}
