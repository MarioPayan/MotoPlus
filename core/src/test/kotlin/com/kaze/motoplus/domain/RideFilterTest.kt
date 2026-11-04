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
}
