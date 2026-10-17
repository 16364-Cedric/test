package fr.dati.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FallDetectorTest {
    /** Rejoue une série d'échantillons à 50 Hz et renvoie `true` si une chute a été détectée. */
    private fun FallDetector.replay(vararg samples: Float): Boolean =
        samples.withIndex().any { (i, g) -> onSample(i * 20L, g) }

    @Test
    fun `free fall followed by impact is a fall`() {
        val detector = FallDetector()
        assertTrue(detector.replay(1f, 1f, 0.2f, 0.1f, 0.1f, 0.2f, 0.1f, 1.5f, 3.4f, 1f))
    }

    @Test
    fun `walking is not a fall`() {
        val detector = FallDetector()
        val walking = FloatArray(200) { if (it % 2 == 0) 1.3f else 0.7f }
        assertFalse(detector.replay(*walking))
    }

    @Test
    fun `impact without free fall is not a fall`() {
        val detector = FallDetector()
        assertFalse(detector.replay(1f, 1f, 3.5f, 1f))
    }

    @Test
    fun `too short free fall is ignored`() {
        val detector = FallDetector()
        assertFalse(detector.replay(1f, 0.2f, 0.2f, 3.5f, 1f))
    }

    @Test
    fun `impact long after free fall is ignored`() {
        val detector = FallDetector()
        val samples = floatArrayOf(1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f) + FloatArray(60) { 1f } + floatArrayOf(3.5f)
        assertFalse(detector.replay(*samples))
    }
}
