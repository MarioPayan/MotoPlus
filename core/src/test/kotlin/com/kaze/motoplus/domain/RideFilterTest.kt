package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test

class RideFilterTest {
    private val both = Tracking(motion = true, bluetooth = true, carDevices = mapOf("CC:DD" to "Coche"), androidAutoInCar = true)
    private val none = Tracking(motion = true, bluetooth = true)

    @Test fun motionRideInACarIsDiscarded() {
        assertEquals(DiscardReason.carBluetooth, discardReason(RideTrigger.motion, true, false, both))
        assertEquals(DiscardReason.androidAuto, discardReason(RideTrigger.motion, false, true, both))
        assertEquals(DiscardReason.carBluetooth, discardReason(RideTrigger.motion, true, true, both))
        assertNull(discardReason(RideTrigger.motion, false, false, both))
    }

    @Test fun rulesSwitchedOffKeepTheRide() {
        assertNull(discardReason(RideTrigger.motion, true, true, none))
        assertEquals(DiscardReason.androidAuto, discardReason(RideTrigger.motion, true, true, none.copy(androidAutoInCar = true)))
        assertEquals(DiscardReason.carBluetooth, discardReason(RideTrigger.motion, true, true, none.copy(carDevices = both.carDevices)))
    }

    @Test fun androidAutoAloneIsNotACarTripUnlessTheRiderSaysSo() {
        assertNull(discardReason(RideTrigger.motion, false, true, Tracking(motion = true)))
        assertEquals(DiscardReason.androidAuto, discardReason(RideTrigger.motion, false, true, Tracking(motion = true, androidAutoInCar = true)))
    }

    @Test fun intercomAndManualRidesAlwaysCount() {
        for (t in listOf(RideTrigger.bluetooth, RideTrigger.manual)) {
            assertNull(discardReason(t, true, true, both))
        }
    }

    @Test fun parkedAfterSixMinutesWithoutMoving() {
        val min = 60_000L
        assertFalse(isParked(now = 6 * min, lastMovingAt = 0, startedAt = 0))
        assertTrue(isParked(now = 6 * min + 1, lastMovingAt = 0, startedAt = 0))
        assertFalse("movement restarts the clock", isParked(now = 20 * min, lastMovingAt = 15 * min, startedAt = 0))
        assertTrue(isParked(now = 22 * min, lastMovingAt = 15 * min, startedAt = 0))
        assertFalse("no fix yet counts from the start", isParked(now = 13 * min, lastMovingAt = 0, startedAt = 10 * min))
        assertTrue(isParked(now = 17 * min, lastMovingAt = 0, startedAt = 10 * min))
    }

    private val bogota = java.time.ZoneId.of("America/Bogota")
    private val startedAt = java.time.Instant.parse("2026-11-03T16:22:00Z").toEpochMilli()
    private val checkpoint = RideCheckpoint("b", RideTrigger.motion, null, 12.4, startedAt, startedAt + 30 * 60_000)
    private fun bike(rides: List<Ride> = emptyList()) = Bike("b", "Yamaha", "MT-07", null, Category.naked, Drive.chain, Cooling.liquid, emptyList(), listOf(Reading("2026-10-01", 1000)), rides)
    private fun garage(vararg rides: Ride, tracking: Tracking = Tracking(motion = true)) = Garage(bikes = listOf(bike(rides.toList())), tracking = tracking)

    @Test fun orphanBecomesARideDatedWhenItWasLastAlive() {
        val ride = garage().orphanRide(checkpoint, bogota)!!
        assertEquals("2026-11-03", ride.date)
        assertEquals(12.4, ride.km, 0.0)
        assertEquals(RideTrigger.motion, ride.trigger)
        assertEquals(checkpoint.lastAt, ride.at)
    }

    @Test fun orphanNearMidnightTakesTheLocalDay() {
        val late = java.time.Instant.parse("2026-11-04T03:00:00Z").toEpochMilli()
        assertEquals("2026-11-03", garage().orphanRide(checkpoint.copy(lastAt = late), bogota)!!.date)
    }

    @Test fun shortOrphanIsDropped() {
        assertNull(garage().orphanRide(checkpoint.copy(km = 0.29), bogota))
        assertNotNull(garage().orphanRide(checkpoint.copy(km = MIN_RIDE_KM), bogota))
    }

    @Test fun orphanOfADeletedBikeIsDropped() {
        assertNull(garage().orphanRide(checkpoint.copy(bikeId = "gone"), bogota))
    }

    @Test fun discardRulesStillApplyToAnOrphan() {
        val rules = Tracking(motion = true, carDevices = mapOf("CC:DD" to "Carro"), androidAutoInCar = true)
        assertNull(garage(tracking = rules).orphanRide(checkpoint.copy(carBluetooth = true), bogota))
        assertNull(garage(tracking = rules).orphanRide(checkpoint.copy(androidAuto = true), bogota))
        assertNotNull(garage(tracking = rules).orphanRide(checkpoint.copy(trigger = RideTrigger.manual, androidAuto = true), bogota))
        assertNotNull(garage(tracking = Tracking()).orphanRide(checkpoint.copy(androidAuto = true), bogota))
    }

    @Test fun orphanAlreadySavedIsNotCountedTwice() {
        val saved = Ride("2026-11-03", 12.4, RideTrigger.motion, at = checkpoint.lastAt + 1)
        assertNull(garage(saved).orphanRide(checkpoint, bogota))
        val earlier = Ride("2026-11-02", 5.0, RideTrigger.motion, at = startedAt - 1)
        assertNotNull(garage(earlier).orphanRide(checkpoint, bogota))
    }

    @Test fun checkpointRoundTripsAndABrokenFileReadsAsNothing() {
        val c = checkpoint.copy(device = "Cardo", carBluetooth = true)
        assertEquals(c, RideCheckpoint.decode(c.encode()))
        assertNull(RideCheckpoint.decode(""))
        assertNull(RideCheckpoint.decode(c.encode().take(20)))
    }
}
