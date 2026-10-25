package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DocumentsTest {
    private val today = LocalDate.parse("2026-10-25")
    private fun doc(expires: String?, alerted: Int? = null) = Document("d", DocType.soat, "b", expires = expires, alerted = alerted)

    @Test fun statusByDaysLeft() {
        assertEquals(DocState(Status.ok, null), docState(doc(null), today))
        assertEquals(Status.ok, docState(doc("2026-12-01"), today).status)
        assertEquals(DocState(Status.warn, 12), docState(doc("2026-11-06"), today))
        assertEquals(DocState(Status.bad, -23), docState(doc("2026-10-02"), today))
    }

    @Test fun remindersFireOncePerStage() {
        assertNull(reminderStage(doc("2026-12-30"), today))
        assertEquals(30, reminderStage(doc("2026-11-06"), today))
        assertEquals(7, reminderStage(doc("2026-10-30"), today))
        assertEquals(0, reminderStage(doc("2026-10-02"), today))
        assertEquals(listOf(30), docAlerts(listOf(doc("2026-11-06")), today).map { it.second })
        assertTrue(docAlerts(listOf(doc("2026-11-06", alerted = 30)), today).isEmpty())
        assertEquals(listOf(7), docAlerts(listOf(doc("2026-10-30", alerted = 30)), today).map { it.second })
        assertTrue(docAlerts(listOf(doc(null)), today).isEmpty())
    }

    @Test fun readsLatestDateFromPhoto() {
        assertEquals(LocalDate.parse("2027-11-06"), pickExpiry("VIGENCIA DESDE 07/11/2026 HASTA 06/11/2027 POLIZA 1234"))
        assertEquals(LocalDate.parse("2031-03-14"), pickExpiry("Expedición 2021-03-14 Vence 2031-03-14"))
        assertNull(pickExpiry("sin fechas 31/02/2026"))
    }
}
