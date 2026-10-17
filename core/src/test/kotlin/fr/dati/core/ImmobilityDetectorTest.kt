package fr.dati.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImmobilityDetectorTest {
    @Test
    fun `fires once after the configured duration without movement`() {
        val detector = ImmobilityDetector(immobilityDurationMs = 10_000)
        val fired = (0..20_000L step 100).count { t ->
            detector.onAcceleration(t, 0f, 0f, 1f + if (t % 200 == 0L) 0.01f else 0f)
        }
        assertEquals(1, fired)
    }

    @Test
    fun `shaking restarts the countdown`() {
        val detector = ImmobilityDetector(immobilityDurationMs = 10_000)
        assertFalse(detector.onAcceleration(0, 0f, 0f, 1f))
        assertFalse(detector.onAcceleration(9_000, 0f, 0f, 1f))
        assertFalse(detector.onAcceleration(9_500, 0f, 0f, 1.5f))
        assertFalse(detector.onAcceleration(18_000, 0f, 0f, 1.5f))
        assertTrue(detector.onAcceleration(19_500, 0f, 0f, 1.5f))
    }

    @Test
    fun `slow tilt counts as movement even though the magnitude stays at 1 g`() {
        val detector = ImmobilityDetector(immobilityDurationMs = 10_000)
        // Le téléphone pivote de 90° en 15 s : la norme reste 1 g, seule l'orientation change.
        val fired = (0..15_000L step 100).any { t ->
            val angle = (t / 15_000.0 * Math.PI / 2)
            detector.onAcceleration(t, sin(angle).toFloat(), 0f, cos(angle).toFloat())
        }
        assertFalse(fired)
    }

    @Test
    fun `rotation detected by the gyroscope restarts the countdown`() {
        val detector = ImmobilityDetector(immobilityDurationMs = 10_000)
        detector.onAcceleration(0, 0f, 0f, 1f)
        assertFalse(detector.onRotation(9_000, 0f, 0.5f, 0f))
        assertFalse(detector.onAcceleration(18_000, 0f, 0f, 1f))
        assertTrue(detector.onAcceleration(19_000, 0f, 0f, 1f))
    }

    @Test
    fun `gyroscope noise is not movement`() {
        val detector = ImmobilityDetector(immobilityDurationMs = 10_000)
        detector.onAcceleration(0, 0f, 0f, 1f)
        assertFalse(detector.onRotation(5_000, 0.02f, -0.01f, 0.03f))
        assertTrue(detector.onRotation(10_000, 0.01f, 0.02f, -0.02f))
    }

    @Test
    fun `fires again after a new movement`() {
        val detector = ImmobilityDetector(immobilityDurationMs = 1_000)
        detector.onAcceleration(0, 0f, 0f, 1f)
        assertTrue(detector.onAcceleration(1_000, 0f, 0f, 1f))
        assertFalse(detector.onAcceleration(2_000, 0f, 0f, 1f))
        detector.onAcceleration(2_100, 0f, 1f, 0f)
        assertTrue(detector.onAcceleration(3_100, 0f, 1f, 0f))
    }
}
