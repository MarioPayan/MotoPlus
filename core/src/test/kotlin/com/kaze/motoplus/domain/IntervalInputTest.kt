package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test

class IntervalInputTest {
    @Test fun switchingBackRestoresTheExactTypedDaysAndKeepsCanonicalDays() {
        val months = IntervalInput("45").switchTo(IntervalUnit.months)
        assertEquals("1.48", months.text)
        assertEquals(45, months.toDays())
        assertEquals("45", months.switchTo(IntervalUnit.years).switchTo(IntervalUnit.days).text)
    }

    @Test fun typingResetsEarlierUnitMemory() {
        val edited = IntervalInput("45").switchTo(IntervalUnit.months).type("2")
        assertEquals("60.8", edited.switchTo(IntervalUnit.days).text)
        assertEquals("2", edited.switchTo(IntervalUnit.days).switchTo(IntervalUnit.months).text)
        assertEquals(61, edited.toDays())
    }

    @Test fun reopeningAnEstimatedUnitKeepsTheStoredDayLimit() {
        val part = Part("p", "engine", "Oil", days = 45, timeUnit = IntervalUnit.years, timeValue = "0.12")
        val reopened = IntervalInput.from(part)
        assertEquals("0.12", reopened.text)
        assertEquals(45, reopened.toDays())
        assertEquals("45", reopened.switchTo(IntervalUnit.days).text)
    }

    @Test fun legacyIntervalsUseTheLargestWholeUnitWithoutChangingDays() {
        for ((days, expected) in listOf(1825 to ("5" to IntervalUnit.years),
            365 to ("1" to IntervalUnit.years), 30 to ("1" to IntervalUnit.months),
            180 to ("6" to IntervalUnit.months), 14 to ("14" to IntervalUnit.days),
            31 to ("31" to IntervalUnit.days))) {
            val input = IntervalInput.from(Part("p", "engine", "Oil", days = days))
            assertEquals(expected.first, input.text)
            assertEquals(expected.second, input.unit)
            assertEquals(days, input.toDays())
            assertEquals(days.toString(), input.switchTo(IntervalUnit.days).text)
        }
    }

    @Test fun explicitlyChosenUnitsWinOverWholeUnitInference() {
        val days = Part("p", "engine", "Oil", days = 1825, timeUnit = IntervalUnit.days, timeValue = "1825")
        assertEquals(IntervalUnit.days, IntervalInput.from(days).unit)
        assertEquals("1825", IntervalInput.from(days).text)
        val months = days.copy(timeUnit = IntervalUnit.months, timeValue = "60")
        assertEquals(IntervalUnit.months, IntervalInput.from(months).unit)
        assertEquals("60", IntervalInput.from(months).text)
        assertEquals(1825, IntervalInput.from(months).toDays())
    }

    @Test fun emptyAndInvalidValuesDoNotCreateALimit() {
        assertNull(IntervalInput().switchTo(IntervalUnit.months).toDays())
        assertNull(IntervalInput("0").toDays())
        assertNull(IntervalInput("-1").toDays())
    }

    @Test fun oldPartsDefaultToDaysAndChosenUnitSurvivesSerialization() {
        val old = Part("p", "engine", "Oil", days = 45)
        assertEquals("45", IntervalInput.from(old).text)
        val garage = Garage(bikes = listOf(Bike("b", "B", "M", category = Category.naked, drive = Drive.chain, cooling = Cooling.air,
            parts = listOf(old.copy(timeUnit = IntervalUnit.months, timeValue = "1.48")), readings = emptyList())))
        val restored = GarageCodec.decode(GarageCodec.encode(garage)).bikes.single().parts.single()
        assertEquals(IntervalUnit.months, restored.timeUnit)
        assertEquals("1.48", IntervalInput.from(restored).text)
        assertEquals(45, restored.days)
    }
}
