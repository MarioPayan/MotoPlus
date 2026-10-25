package com.kaze.motoplus.ui

import com.kaze.motoplus.domain.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PromptTest {
    @Test fun promptListsPartsAndZones() {
        val b = Bike("b", "Yamaha", "MT-07", 2021, Category.naked, Drive.chain, Cooling.liquid,
            defaultParts(Category.naked, Drive.chain, Cooling.liquid), listOf(Reading("2026-10-10", 12210)))
        val p = buildPrompt(b, "chirría", LocalDate.parse("2026-10-24"))
        assertTrue(p.contains("Engrase de cadena [drivetrain]"))
        assertTrue(p.contains("SÍNTOMA: chirría"))
        assertTrue(p.contains("front_brake"))
        assertTrue(p.contains("\"v\":$REPORT_VERSION"))
    }
}
