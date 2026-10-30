package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Completar visita is Registrar mantenimiento opened on an open Taller list. */
class VisitTest {
    private val today = LocalDate.parse("2026-01-05")
    private val bike = Bike("b", "Yamaha", "MT", category = Category.naked, drive = Drive.chain, cooling = Cooling.liquid,
        parts = defaultParts(Category.naked, Drive.chain, Cooling.liquid), readings = listOf(Reading("2026-01-01", 10000)))
    private val order = WorkOrder("o", 1, "2026-01-02", 10000, listOf("engine_oil", "oil_filter"), "Check noise",
        customItems = listOf(PendingJob("custom", "Adjust clutch", "Cable tension")), itemNotes = mapOf("engine_oil" to "10W-50"))

    @Test fun savesCostsNotesCustomJobsAndLastServiceAndRetainsUncheckedJobs() {
        var id = 0
        val jobs = listOf(LoggedJob("engine_oil", cost = 145000.0, note = "10W-50"), LoggedJob("custom", "Adjust clutch", 35000.0))
        val result = bike.copy(orders = listOf(order)).logMaintenance(jobs, "2026-01-03", 10025, today, Venue.shop, "", "/photo", "o", { "id${id++}" })
        val open = result.openOrder()!!
        assertEquals("o", open.id)
        assertEquals(listOf("oil_filter"), open.partIds)
        assertTrue(open.customItems.isEmpty())
        assertFalse("engine_oil" in open.itemNotes)
        assertEquals(1, result.orders.size)
        assertEquals(180000.0, result.totalCost, 0.0)
        val oil = result.parts.first { it.id == "engine_oil" }
        assertEquals(10025, oil.lastKm)
        assertEquals("2026-01-03", oil.lastDate)
        assertNull(result.parts.first { it.id == "oil_filter" }.lastKm)
        assertEquals("Check noise\n10W-50", result.recordsFor("engine_oil").single().notes)
        assertEquals("Adjust clutch", result.recordsFor("custom").single().partName)
        assertEquals("/photo", result.history.first().photo)
        assertNull(result.deleteRecord(result.history.first().id).parts.first { it.id == "engine_oil" }.lastKm)
        assertEquals(result, GarageCodec.decode(GarageCodec.encode(Garage(bikes = listOf(result)))).bikes.single())
    }

    @Test fun theListClosesOnlyWhenNoPendingJobRemainsAndKeepsItsLastJobs() {
        var id = 0
        val first = bike.copy(orders = listOf(order)).logMaintenance(listOf(LoggedJob("engine_oil"), LoggedJob("custom", "Adjust clutch")),
            "2026-01-03", 10025, today, null, "", null, "o", { "a${id++}" })
        assertFalse(first.orders.single().closed)
        val second = first.logMaintenance(listOf(LoggedJob("oil_filter")), "2026-01-04", 10030, today, null, "", null, "o", { "b${id++}" })
        assertNull(second.openOrder())
        assertTrue(second.orders.single().closed)
        assertEquals(listOf("oil_filter"), second.orders.single().partIds)
    }

    @Test fun invoiceOnlyVisitWithoutAnOrderCanRecordExistingAndExtraJobs() {
        var id = 0
        val b = bike.logMaintenance(listOf(LoggedJob("engine_oil"), LoggedJob("extra", "Wash", 9000.0)), "2026-01-03", 10030, today, null, "", null, null, { "r${id++}" })
        assertEquals(2, b.history.size)
        assertEquals(10030, b.parts.first { it.id == "engine_oil" }.lastKm)
        assertEquals("Wash", b.history.last().partName)
        assertEquals(9000.0, b.totalCost, 0.0)
    }

    @Test fun formatsMoneyWhileTypingWithTheChosenLocale() {
        val co = Locale.forLanguageTag("es-CO")
        assertEquals("145.000", formatAmountInput("145000", "COP", co))
        assertEquals(145000.0, parseAmount(formatAmountInput("145000", "COP", co), co)!!, 0.0)
        assertEquals("1.234,50", formatAmountInput("1234,50", "EUR", co))
        assertEquals("1,234.50", formatAmountInput("1234.50", "USD", Locale.US))
        assertEquals("", formatAmountInput("", "COP", co))
    }
}
