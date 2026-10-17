package fr.dati.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImmobilityDetectorTest {
    @Test
    fun `fires once after the configured duration without movement`() {
        val detector = ImmobilityDetector(immobilityDurationMs = 10_000)
        val fired = (0..20_000L step 100).count { t -> detector.onSample(t, 1.0f + if (t % 200 == 0L) 0.01f else 0f) }
        assertEquals(1, fired)
    }

    @Test
    fun `movement restarts the countdown`() {
        val detector = ImmobilityDetector(immobilityDurationMs = 10_000)
        assertFalse(detector.onSample(0, 1f))
        assertFalse(detector.onSample(9_000, 1f))
        assertFalse(detector.onSample(9_500, 1.5f)) // mouvement
        assertFalse(detector.onSample(18_000, 1.5f))
        assertTrue(detector.onSample(19_500, 1.5f))
    }

    @Test
    fun `fires again after a new movement`() {
        val detector = ImmobilityDetector(immobilityDurationMs = 1_000)
        detector.onSample(0, 1f)
        assertTrue(detector.onSample(1_000, 1f))
        assertFalse(detector.onSample(2_000, 1f))
        detector.onSample(2_100, 2f)
        assertTrue(detector.onSample(3_100, 2f))
    }
}
