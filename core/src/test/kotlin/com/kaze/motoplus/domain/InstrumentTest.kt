package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class InstrumentTest {
    @Test fun healthBandsIncludeTheirLowerBoundary() {
        listOf(0, 49).forEach { assertEquals(Status.bad, healthBand(it)) }
        listOf(50, 79).forEach { assertEquals(Status.warn, healthBand(it)) }
        listOf(80, 100).forEach { assertEquals(Status.ok, healthBand(it)) }
    }

    @Test fun currencyUsesExplicitCurrencyAndLocale() {
        val co = Locale.forLanguageTag("es-CO")
        val en = Locale.forLanguageTag("en")
        assertEquals("$ 474.000", formatMoney(474000.0, "COP", co))
        assertEquals("$ 474,000", formatMoney(474000.0, "COP", en))
        assertEquals("US$ 1.234,50", formatMoney(1234.5, "USD", co))
        assertEquals("US$ 1,234.50", formatMoney(1234.5, "USD", en))
        assertEquals("$ 475", formatMoney(474.8, "COP", co))
    }

    @Test fun amountInputUsesTheSelectedLocale() {
        assertEquals(1234.5, parseAmount("1.234,50", Locale.forLanguageTag("es-CO"))!!, 0.0)
        assertEquals(12.5, parseAmount("12.50", Locale.ENGLISH)!!, 0.0)
        assertNull(parseAmount("12oops", Locale.ENGLISH))
        assertNull(parseAmount("-5", Locale.ENGLISH))
    }

    @Test fun removingAnIntervalRetainsPastWork() {
        val part = Part("oil", "engine", "Saved name", km = 100, lastKm = 0)
        val bike = Bike("b", "KTM", "Duke", category = Category.naked, drive = Drive.chain, cooling = Cooling.liquid,
            parts = listOf(part), readings = listOf(Reading("2026-10-01", 200)),
            history = listOf(ServiceRecord("r", part.id, "2026-10-01", 200, 50.0, photo = "invoice.jpg")),
            orders = listOf(WorkOrder("o", 1, "2026-10-01", 200, listOf(part.id))))
        val updated = bike.withoutPart(part.id)
        assertTrue(updated.parts.isEmpty())
        assertTrue(updated.orders.single().partIds.isEmpty())
        assertEquals("Saved name", updated.history.single().partName)
        assertEquals(50.0, updated.totalCost, 0.0)
        assertEquals("invoice.jpg", updated.history.single().photo)
    }

    @Test fun snoozedAlertsReturnAfterTheDate() {
        val today = java.time.LocalDate.of(2026, 10, 29)
        val part = Part("oil", "engine", "Oil", km = 100, lastKm = 0, snoozedUntil = "2026-10-30")
        val bike = Bike("b", "KTM", "Duke", category = Category.naked, drive = Drive.chain, cooling = Cooling.liquid,
            parts = listOf(part), readings = listOf(Reading("2026-10-01", 200)))
        val (alerts, baseline) = dueAlerts(bike, 200.0, today)
        assertTrue(alerts.isEmpty())
        val after = dueAlerts(bike.copy(notified = baseline), 200.0, today.plusDays(1)).first
        assertEquals(part.id, after.single().first.id)
    }

    @Test fun schema3WithoutNewFieldsKeepsTheRidersData() {
        val old = """
            {"schema":3,"selectedId":"mario","bikes":[{
              "id":"mario","brand":"KTM","model":"390 Duke","year":2021,"category":"naked","drive":"chain","cooling":"liquid",
              "parts":[{"id":"engine_oil","zone":"engine","name":"Aceite de motor","km":6000,"days":365,"lastKm":12000,"lastDate":"2026-10-01"}],
              "readings":[{"date":"2026-10-01","km":12000,"at":1}],
              "rides":[{"date":"2026-10-02","km":12.5,"trigger":"bluetooth","device":"Cardo","wet":true,"at":2}],
              "gpsFactor":1.02,"accent":"violet","notified":{"engine_oil":"warn"},
              "history":[{"id":"service","partId":"engine_oil","date":"2026-10-01","km":12000,"cost":474000.0,"notes":"Saved note","photo":"/photos/invoice.jpg"}],
              "fuel":[{"id":"fuel","date":"2026-10-01","km":12000,"liters":10.5,"total":150000.0,"full":true,"at":1}],
              "orders":[{"id":"order","number":2,"date":"2026-10-02","km":12012,"partIds":["engine_oil"],"notes":"Saved order","closed":false}],
              "checks":[{"date":"2026-10-02","at":3,"ok":["lights"],"failed":["tires"],"notes":"Saved check"}],
              "reports":[{"at":"2026-10-02","km":12012,"data":{"v":2,"title":"Saved report","summary":"Keep this report","urgency":"medium","causes":[],"checks":[],"advice":""}}]
            }],"tracking":{"motion":true,"bluetooth":true,"devices":{"AA:BB":"Cardo"},"carDevices":{"CC:DD":"Car"},"androidAuto":true},"documents":[{"id":"paper","type":"soat","bikeId":"mario","number":"ABC123","expires":"2027-01-01","photos":["/photos/paper.jpg"],"notes":"Saved paper","alerted":30}]}
        """.trimIndent()
        val garage = GarageCodec.decode(old)
        val bike = garage.selected!!
        assertEquals(3, garage.schema)
        assertEquals("COP", garage.currency)
        assertNull(bike.parts.single().snoozedUntil)
        assertNull(bike.history.single().partName)
        assertEquals("mario", bike.id)
        assertEquals(Accent.violet, bike.accent)
        assertEquals(12000, bike.readings.single().km)
        assertEquals("Cardo", bike.rides.single().device)
        assertTrue(bike.rides.single().wet)
        assertEquals(1.02, bike.gpsFactor, 0.0)
        assertEquals(Status.warn, bike.notified["engine_oil"])
        assertEquals("/photos/invoice.jpg", bike.history.single().photo)
        assertEquals(474000.0, bike.history.single().cost!!, 0.0)
        assertEquals(10.5, bike.fuel.single().liters, 0.0)
        assertEquals("Saved order", bike.orders.single().notes)
        assertEquals(listOf("tires"), bike.checks.single().failed)
        assertEquals("Keep this report", bike.reports.single().data.summary)
        assertTrue(garage.tracking.androidAuto)
        assertEquals(listOf("/photos/paper.jpg"), garage.documents.single().photos)
        assertEquals(30, garage.documents.single().alerted)
        assertEquals(garage, GarageCodec.decode(GarageCodec.encode(garage)))
        val updated = garage.copy(currency = "USD", bikes = listOf(bike.copy(parts = bike.parts.map { it.copy(snoozedUntil = "2026-11-01") })))
        assertEquals(updated, GarageCodec.decode(GarageCodec.encode(updated)))
    }
}
