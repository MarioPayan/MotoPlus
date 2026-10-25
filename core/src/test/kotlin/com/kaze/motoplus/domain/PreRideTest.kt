package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PreRideTest {
    @Test fun fineTiresAndLightsCountAsDone() {
        val today = LocalDate.parse("2026-10-25")
        val b = Bike("b", "Y", "M", null, Category.naked, Drive.chain, Cooling.liquid, defaultParts(Category.naked, Drive.chain, Cooling.liquid), listOf(Reading("2026-10-25", 12000)))
            .withCheck(PreRideCheck("2026-10-25", 1, ok = listOf("tires", "chain"), failed = listOf("lights")), 12000, today)
        assertEquals(1, b.checks.size)
        assertEquals("2026-10-25", b.parts.first { it.id == "tire_pressure" }.lastDate)
        assertNull(b.parts.first { it.id == "lights_check" }.lastDate)
    }
}
