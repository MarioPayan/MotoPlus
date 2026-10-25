package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WorkOrderTest {
    private val today = LocalDate.parse("2026-10-25")
    private val bike = Bike("b", "Y", "M", null, Category.naked, Drive.chain, Cooling.liquid, defaultParts(Category.naked, Drive.chain, Cooling.liquid),
        listOf(Reading("2026-10-25", 12000))).markDone("chain_lube", 11500, today).markDone("front_pads", 1000, today)

    @Test fun picksOverdueAndComingDue() {
        assertEquals(listOf("front_pads", "chain_lube"), bike.dueForWorkshop(12000.0, today))
    }

    @Test fun closingRecordsEachJobWithItsCost() {
        var n = 0
        val b = bike.copy(orders = listOf(WorkOrder("o", 1, "2026-10-25", 12000, listOf("front_pads", "chain_lube", "brake_fluid"))))
        assertEquals(2, b.nextOrderNumber())
        val done = b.closeOrder("o", mapOf("front_pads" to 95000.0, "chain_lube" to null), "2026-10-26", 12010, "/f.jpg") { "r${n++}" }
        assertNull(done.openOrder())
        assertEquals(12010, done.parts.first { it.id == "front_pads" }.lastKm)
        assertEquals(95000.0, done.recordsFor("front_pads").first().cost)
        assertNull(done.parts.first { it.id == "brake_fluid" }.lastKm)
        assertEquals("/f.jpg", done.recordsFor("chain_lube").first().photo)
    }
}
