package fr.dati.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkLossTrackerTest {
    @Test
    fun `short outages are ignored`() {
        val tracker = NetworkLossTracker(graceMs = 60_000)
        assertNull(tracker.onStatus(0, available = false))
        assertNull(tracker.tick(59_000))
        assertNull(tracker.onStatus(59_500, available = true))
    }

    @Test
    fun `long outage is reported once, then its end`() {
        val tracker = NetworkLossTracker(graceMs = 60_000)
        tracker.onStatus(1_000, available = false)
        assertEquals(NetworkLossTracker.Event.Lost(1_000), tracker.tick(61_000))
        assertNull(tracker.tick(120_000))
        assertNull(tracker.onStatus(130_000, available = false))
        assertTrue(tracker.isLost)
        assertEquals(NetworkLossTracker.Event.Restored(1_000, 299_000), tracker.onStatus(300_000, available = true))
        assertNull(tracker.onStatus(301_000, available = true))
    }
}
