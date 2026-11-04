package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EstimateRideTest {
    private val bogota = ZoneId.of("America/Bogota")
    private val hour = 3_600_000L
    private val startedAt = Instant.parse("2026-11-03T16:00:00Z").toEpochMilli()
    private val session = EstimateSession("b", RideTrigger.motion, null, startedAt)
    private val today = LocalDate.parse("2026-11-04")
    private fun bike(rides: List<Ride> = emptyList(), factor: Double = 1.0) = Bike("b", "Yamaha", "MT-07", null, Category.naked, Drive.chain, Cooling.liquid, emptyList(),
        listOf(Reading("2026-10-01", 1000, 1)), rides, gpsFactor = factor)
    private fun garage(vararg rides: Ride, tracking: Tracking = Tracking(mode = TrackingMode.estimate)) = Garage(bikes = listOf(bike(rides.toList())), tracking = tracking)

    @Test fun kmAreTimeTimesSpeed() {
        assertEquals(25.0, estimatedKm(0, hour, DEFAULT_ESTIMATE_KMH), 1e-9)
        assertEquals(20.0, estimatedKm(0, hour / 2, 40), 1e-9)
        assertEquals("a clock moved backwards is no ride", 0.0, estimatedKm(hour, 0, 40), 0.0)
        assertEquals("a tampered speed stays in range", 120.0, estimatedKm(0, hour, 9999), 1e-9)
    }

    @Test fun endingASessionSavesAnEstimatedRide() {
        val end = garage().endEstimate(session, startedAt + hour, bogota)
        val ride = end.ride!!
        assertNull(end.dropped)
        assertTrue(ride.estimated)
        assertEquals(25.0, ride.km, 1e-9)
        assertEquals("2026-11-03", ride.date)
        assertEquals(startedAt + hour, ride.at)
        assertEquals(RideTrigger.motion, ride.trigger)
    }

    @Test fun tooShortIsDropped() {
        val end = garage().endEstimate(session, startedAt + 30_000, bogota)
        assertEquals(EstimateDrop.tooShort, end.dropped)
        assertNull(end.ride)
    }

    @Test fun pastTheCapSavesNothing() {
        assertNotNull(garage().endEstimate(session, startedAt + 3 * hour, bogota).ride)
        val end = garage().endEstimate(session, startedAt + 3 * hour + 1, bogota)
        assertEquals(EstimateDrop.tooLong, end.dropped)
        assertNull(end.ride)
    }

    @Test fun carBluetoothDiscardsAMotionSessionOnly() {
        val rules = Tracking(mode = TrackingMode.estimate, carDevices = mapOf("CC:DD" to "Carro"))
        val car = session.copy(carBluetooth = true)
        assertEquals(EstimateDrop.carBluetooth, garage(tracking = rules).endEstimate(car, startedAt + hour, bogota).dropped)
        assertNotNull(garage(tracking = rules).endEstimate(car.copy(trigger = RideTrigger.manual), startedAt + hour, bogota).ride)
        assertNotNull(garage(tracking = rules).endEstimate(session, startedAt + hour, bogota).ride)
    }

    @Test fun deletedBikeAndAlreadySavedSessionsSaveNothing() {
        assertEquals(EstimateDrop.noBike, garage().endEstimate(session.copy(bikeId = "gone"), startedAt + hour, bogota).dropped)
        val saved = Ride("2026-11-03", 25.0, RideTrigger.motion, at = startedAt + 1, estimated = true)
        assertEquals(EstimateDrop.alreadySaved, garage(saved).endEstimate(session, startedAt + hour, bogota).dropped)
        val earlier = Ride("2026-11-02", 5.0, at = startedAt - 1)
        assertNotNull(garage(earlier).endEstimate(session, startedAt + hour, bogota).ride)
    }

    @Test fun onlyDeadSessionsCloseAtProcessStart() {
        assertFalse("still running", garage().estimateIsOver(session, startedAt + hour, bogota))
        assertFalse("short so far, not over", garage().estimateIsOver(session, startedAt + 1, bogota))
        assertTrue(garage().estimateIsOver(session, startedAt + 4 * hour, bogota))
        assertTrue(garage().estimateIsOver(session.copy(bikeId = "gone"), startedAt + hour, bogota))
    }

    @Test fun sessionRoundTripsAndABrokenFileReadsAsNothing() {
        val s = session.copy(device = "Cardo", carBluetooth = true)
        assertEquals(s, EstimateSession.decode(s.encode()))
        assertNull(EstimateSession.decode(""))
        assertNull(EstimateSession.decode(s.encode().take(15)))
    }

    @Test fun gpsFactorSkipsEstimatedKm() {
        val b = bike(listOf(Ride("2026-10-10", 100.0), Ride("2026-10-11", 50.0, estimated = true)), factor = 1.1)
        val e = estimate(b, today)
        assertEquals(1000 + 100 * 1.1 + 50, e.km, 1e-9)
        assertEquals(100.0, e.gpsSinceReading, 0.0)
        assertEquals(50.0, e.estimatedSinceReading, 0.0)
        assertEquals(2, e.rides)
        assertEquals(EstimateBasis.rides, e.basis)
    }

    @Test fun estimatedKmWidenTheMargin() {
        assertEquals(5, estimate(bike(listOf(Ride("2026-10-10", 100.0))), today).margin)
        assertEquals(30, estimate(bike(listOf(Ride("2026-10-10", 100.0, estimated = true))), today).margin)
    }

    @Test fun estimatedRidesNeverFeedTheCalibration() {
        val mixed = bike(listOf(Ride("2026-10-10", 100.0), Ride("2026-10-11", 5.0, estimated = true)), factor = 1.02)
        assertEquals(1.02, mixed.withReading(1105, today).gpsFactor, 0.0)
        val onlyEstimated = bike(listOf(Ride("2026-10-11", 100.0, estimated = true)), factor = 1.02)
        assertEquals(1.02, onlyEstimated.withReading(1095, today).gpsFactor, 0.0)
        val onlyGps = bike(listOf(Ride("2026-10-10", 100.0)), factor = 1.02)
        assertEquals(1.05, onlyGps.withReading(1105, today).gpsFactor, 1e-9)
    }

    @Test fun measuredSpeedNeedsFiveGpsRidesWithDuration() {
        val ride = Ride("2026-10-10", 15.0, durationMs = hour / 2)
        assertNull(bike(List(4) { ride }).measuredKmh())
        assertEquals(30, bike(List(5) { ride }).measuredKmh())
        val noise = listOf(Ride("2026-10-11", 99.0), Ride("2026-10-11", 99.0, estimated = true, durationMs = hour))
        assertEquals("rides without duration and estimated rides don't count", 30, bike(List(5) { ride } + noise).measuredKmh())
        assertNull(bike(List(4) { ride } + noise).measuredKmh())
        assertNull("an impossible average isn't offered", bike(List(5) { Ride("2026-10-10", 500.0, durationMs = hour / 2) }).measuredKmh())
    }

    @Test fun oldDataDecodesAsPrecisionWithDefaults() {
        val old = """{"schema":3,"tracking":{"motion":true},"bikes":[{"id":"b","brand":"Y","model":"M","category":"naked","drive":"chain","cooling":"liquid",
            "parts":[],"readings":[{"date":"2026-10-01","km":1000}],"rides":[{"date":"2026-10-02","km":12.5,"trigger":"motion","at":2}]}]}"""
        val g = GarageCodec.decode(old)
        assertEquals(TrackingMode.precision, g.tracking.mode)
        assertEquals(DEFAULT_ESTIMATE_KMH, g.tracking.estimateKmh)
        assertEquals(0L, g.bikes.single().rides.single().durationMs)
        assertFalse(g.bikes.single().rides.single().estimated)
        val changed = g.copy(tracking = g.tracking.copy(mode = TrackingMode.estimate, estimateKmh = 40),
            bikes = g.bikes.map { b -> b.copy(rides = b.rides + Ride("2026-10-03", 5.0, estimated = true, durationMs = 9)) })
        assertEquals(changed, GarageCodec.decode(GarageCodec.encode(changed)))
    }
}
