package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FuelTest {
    private val today = LocalDate.parse("2026-10-25")

    @Test fun monthlyAverageRequiresThirtyDaysOfActualReadings() {
        fun bike(vararg readings: Reading) = Bike("b", "Y", "M", null, Category.naked,
            Drive.chain, Cooling.air, emptyList(), readings.toList())
        val first = Reading("2026-10-01", 1000)
        assertNull(monthlyAverage(bike(), today))
        assertNull(monthlyAverage(bike(first), today.plusDays(90)))
        assertNull(monthlyAverage(bike(first, Reading("2026-10-30", 39950)), today.plusDays(90)))
        assertEquals(38950, monthlyAverage(bike(first, Reading("2026-10-31", 39950)), LocalDate.parse("2026-10-31")))
        assertEquals(300, monthlyAverage(bike(first, Reading("2026-11-30", 1600)), LocalDate.parse("2026-11-30")))
        assertNull(monthlyAverage(bike(first, Reading("2026-11-30", 1600)), today))
    }

    @Test fun consumptionBetweenFullTanks() {
        val s = fuelStats(listOf(
            FuelEntry("1", "2026-10-01", 12000, 10.0, 160000.0),
            FuelEntry("2", "2026-10-08", 12150, 4.0, 64000.0, full = false),
            FuelEntry("3", "2026-10-15", 12310, 6.0, 96000.0),
        ))
        assertEquals(310 / 10.0, s.kmPerLiter!!, 1e-9)
        assertEquals(160000.0 / 310, s.costPerKm!!, 1e-9)
        assertEquals(16000.0, s.pricePerLiter!!, 1e-9)
    }

    @Test fun oneFillUpHasNoConsumption() {
        val s = fuelStats(listOf(FuelEntry("1", "2026-10-01", 12000, 10.0)))
        assertNull(s.kmPerLiter); assertNull(s.costPerKm); assertNull(s.pricePerLiter)
    }

    @Test fun fillUpIsAReading() {
        val b = Bike("b", "Yamaha", "MT-07", null, Category.naked, Drive.chain, Cooling.liquid, emptyList(),
            listOf(Reading("2026-10-01", 12000)), rides = listOf(Ride("2026-10-10", 200.0)))
            .withFuel(FuelEntry("1", "2026-10-25", 12210, 11.2, at = 5), today)
        assertEquals(12210.0, estimate(b, today).km, 0.0)
        assertEquals(1.05, b.gpsFactor, 1e-9)
        assertEquals(1, b.fuel.size)
    }

    @Test fun monthAndWeek() {
        val b = Bike("b", "Y", "M", null, Category.naked, Drive.chain, Cooling.liquid, emptyList(),
            listOf(Reading("2026-09-28", 11500), Reading("2026-10-20", 12000)), rides = listOf(Ride("2026-10-24", 18.0), Ride("2026-10-25", 6.0, at = 9)))
        assertEquals(500 + 24, kmThisMonth(b, today))
        assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 18.0, 6.0), weekRides(b, today))
    }
}

class FuelDeleteTest {
    @Test fun deletingFillUpRemovesItsReading() {
        val today = java.time.LocalDate.parse("2026-10-25")
        val b = Bike("b", "Y", "M", null, Category.naked, Drive.chain, Cooling.liquid, emptyList(), listOf(Reading("2026-10-01", 12000)))
            .withFuel(FuelEntry("1", "2026-10-25", 6012000, 4.5, at = 7), today)
        val fixed = b.deleteFuel("1")
        assertEquals(listOf(Reading("2026-10-01", 12000)), fixed.readings)
        assertTrue(fixed.fuel.isEmpty())
        assertEquals(fixed, fixed.deleteReading(fixed.readings.single()))
        assertTrue(suspiciousJump(6012000, 12000.0)); assertFalse(suspiciousJump(12300, 12000.0))
    }

    @Test fun typoInLastReadingCanBeReplaced() {
        val today = java.time.LocalDate.parse("2026-10-25")
        val b = Bike("b", "Y", "M", null, Category.naked, Drive.chain, Cooling.liquid, emptyList(), listOf(Reading("2026-10-25", 60000, at = 1)))
            .withFuel(FuelEntry("1", "2026-10-25", 6012000, 4.5, at = 7), today)
        assertEquals(2, b.readings.size)
        val fixed = b.replaceLastReading(60120, today, 9)
        assertEquals(listOf(60000, 60120), fixed.readings.map { it.km })
        assertEquals(60120, fixed.fuel.single().km)
        assertEquals(60120.0, estimate(fixed, today).km, 0.0)
    }
}
