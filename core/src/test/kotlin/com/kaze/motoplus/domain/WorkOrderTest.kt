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

    @Test fun tabCountsMatchBothScreenListsWithNineAutomaticJobs() {
        val overdue = bike.copy(parts = bike.parts.take(9).map { it.copy(km = 1000, lastKm = 0) }, orders = emptyList())
        val pending = overdue.workshopItems(12000.0, today)
        val visit = overdue.workshopItems(12000.0, today)
        assertEquals(9, pending.count)
        assertEquals(overdue.dueForWorkshop(12000.0, today), pending.partIds)
        assertEquals(pending.partIds.size + pending.customItems.size, pending.count)
        assertEquals(visit.partIds.size + visit.customItems.size, pending.count)
    }

    @Test fun savedJobsCustomJobsAndMissingPartsUseTheRenderedCount() {
        val order = WorkOrder("o", 1, today.toString(), 12000,
            listOf("front_pads", "removed_part", "front_pads"), customItems = listOf(PendingJob("job", "Wash")))
        val items = bike.copy(orders = listOf(order)).workshopItems(12000.0, today)
        assertEquals(listOf("front_pads"), items.partIds)
        assertEquals(order.customItems, items.customItems)
        assertEquals(2, items.count)
        assertEquals(0, bike.copy(orders = listOf(order.copy(partIds = emptyList(), customItems = emptyList())))
            .workshopItems(12000.0, today).count)
        assertEquals(bike.dueForWorkshop(12000.0, today).size,
            bike.copy(orders = listOf(order.copy(closed = true))).workshopItems(12000.0, today).count)
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
