package fr.dati.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ZoneDwellTrackerTest {
    // Un atelier d'environ 100 m × 100 m.
    private val workshop = RiskZone(
        id = "z1",
        name = "Atelier peinture",
        shape = ZoneShape.Polygon(
            listOf(GeoPoint(45.0000, 5.0000), GeoPoint(45.0000, 5.0013), GeoPoint(45.0009, 5.0013), GeoPoint(45.0009, 5.0000))
        ),
        maxDwellMs = 5 * 60_000,
    )
    private val tank = RiskZone("z2", "Cuve", ZoneShape.Circle(GeoPoint(45.0100, 5.0100), 30.0), maxDwellMs = 2 * 60_000)

    private val inside = GeoPoint(45.0004, 5.0006)
    private val outside = GeoPoint(45.0030, 5.0006)

    @Test
    fun `geometry`() {
        assertTrue(workshop.contains(inside))
        assertFalse(workshop.contains(outside))
        assertTrue(tank.contains(GeoPoint(45.0101, 5.0101)))
        assertFalse(tank.contains(GeoPoint(45.0110, 5.0100)))
        assertEquals(111_195.0, Geo.distanceM(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 1.0)
    }

    @Test
    fun `entering, warning one minute before, then exceeding`() {
        val tracker = ZoneDwellTracker(listOf(workshop, tank))
        assertIs<ZoneDwellTracker.Event.Entered>(tracker.onLocation(0, inside, 10f).single())
        assertTrue(tracker.tick(3 * 60_000).isEmpty())
        val warning = tracker.tick(4 * 60_000).single()
        assertIs<ZoneDwellTracker.Event.Warning>(warning)
        assertEquals(60_000, warning.remainingMs)
        assertIs<ZoneDwellTracker.Event.Exceeded>(tracker.tick(5 * 60_000).single())
        assertTrue(tracker.tick(6 * 60_000).isEmpty(), "une seule alerte par dépassement")
    }

    @Test
    fun `leaving in time stops the countdown`() {
        val tracker = ZoneDwellTracker(listOf(workshop))
        tracker.onLocation(0, inside, 10f)
        val left = tracker.onLocation(3 * 60_000, outside, 10f).single()
        assertIs<ZoneDwellTracker.Event.Left>(left)
        assertEquals(3 * 60_000L, left.durationMs)
        assertTrue(tracker.tick(10 * 60_000).isEmpty())
    }

    @Test
    fun `imprecise fixes do not make the worker leave`() {
        val tracker = ZoneDwellTracker(listOf(workshop))
        tracker.onLocation(0, inside, 10f)
        assertTrue(tracker.onLocation(60_000, outside, 120f).isEmpty())
        assertEquals(1, tracker.currentZones().size)
        assertIs<ZoneDwellTracker.Event.Exceeded>(tracker.tick(5 * 60_000).last())
    }

    @Test
    fun `acknowledging restarts the countdown while still inside`() {
        val tracker = ZoneDwellTracker(listOf(workshop))
        tracker.onLocation(0, inside, 10f)
        tracker.tick(5 * 60_000)
        tracker.acknowledgeExceeded(6 * 60_000)
        assertTrue(tracker.tick(9 * 60_000).isEmpty())
        assertIs<ZoneDwellTracker.Event.Warning>(tracker.tick(10 * 60_000).single())
        assertIs<ZoneDwellTracker.Event.Exceeded>(tracker.tick(11 * 60_000).single())
    }

    @Test
    fun `removing a zone forgets the presence`() {
        val tracker = ZoneDwellTracker(listOf(workshop))
        tracker.onLocation(0, inside, 10f)
        tracker.zones = emptyList()
        assertTrue(tracker.currentZones().isEmpty())
        assertTrue(tracker.tick(10 * 60_000).isEmpty())
    }
}

class BeaconZoneTest {
    private val beacon = "e2c56db5-dffb-48d2-b060-d0f5a71096e0:1:7"
    private val zone = RiskZone(
        id = "z3",
        name = "Local batteries",
        shape = ZoneShape.Circle(GeoPoint(45.0, 5.0), 10.0),
        maxDwellMs = 5 * 60_000,
        beacons = setOf(beacon),
        minRssi = -75,
    )

    @Test
    fun `identifiers are normalised`() {
        assertEquals(beacon, Beacons.normalize(" E2C56DB5-DFFB-48D2-B060-D0F5A71096E0:01:007 "))
        assertEquals("aa:bb:cc:dd:ee:ff", Beacons.normalize("AA:BB:CC:DD:EE:FF"))
        assertEquals(null, Beacons.normalize("e2c56db5-dffb-48d2-b060-d0f5a71096e0:70000:1"))
        assertEquals(null, Beacons.normalize("balise 1"))
    }

    @Test
    fun `presence follows the beacon, not the GPS`() {
        val tracker = ZoneDwellTracker(listOf(zone))
        assertTrue(tracker.onLocation(0, GeoPoint(45.0, 5.0), 5f).isEmpty(), "le GPS est ignoré pour cette zone")
        assertTrue(tracker.onBeacon(1_000, beacon.uppercase(), -90).isEmpty(), "signal trop faible")
        assertIs<ZoneDwellTracker.Event.Entered>(tracker.onBeacon(2_000, beacon, -60).single())
        tracker.onBeacon(20_000, beacon, -65)
        assertTrue(tracker.tick(45_000).isEmpty(), "absence de signal de moins de 30 s tolérée")
        val left = tracker.tick(51_000).single()
        assertIs<ZoneDwellTracker.Event.Left>(left)
    }

    @Test
    fun `staying near the beacon exceeds the limit`() {
        val tracker = ZoneDwellTracker(listOf(zone))
        var exceeded = false
        for (t in 0..6 * 60_000L step 10_000) {
            if (tracker.onBeacon(t, beacon, -70).any { it is ZoneDwellTracker.Event.Exceeded }) exceeded = true
        }
        assertTrue(exceeded)
        assertEquals(setOf(beacon), tracker.beaconIds)
    }
}
