package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test

class RideFilterTest {
    private val both = Tracking(motion = true, bluetooth = true, carDevices = mapOf("CC:DD" to "Coche"), androidAuto = true)
    private val none = Tracking(motion = true, bluetooth = true)

    @Test fun motionRideInACarIsDiscarded() {
        assertEquals(DiscardReason.carBluetooth, discardReason(RideTrigger.motion, true, false, both))
        assertEquals(DiscardReason.androidAuto, discardReason(RideTrigger.motion, false, true, both))
        assertEquals(DiscardReason.carBluetooth, discardReason(RideTrigger.motion, true, true, both))
        assertNull(discardReason(RideTrigger.motion, false, false, both))
    }

    @Test fun rulesSwitchedOffKeepTheRide() {
        assertNull(discardReason(RideTrigger.motion, true, true, none))
        assertEquals(DiscardReason.androidAuto, discardReason(RideTrigger.motion, true, true, none.copy(androidAuto = true)))
        assertEquals(DiscardReason.carBluetooth, discardReason(RideTrigger.motion, true, true, none.copy(carDevices = both.carDevices)))
    }

    @Test fun intercomAndManualRidesAlwaysCount() {
        for (t in listOf(RideTrigger.bluetooth, RideTrigger.manual)) {
            assertNull(discardReason(t, true, true, both))
        }
    }
}
