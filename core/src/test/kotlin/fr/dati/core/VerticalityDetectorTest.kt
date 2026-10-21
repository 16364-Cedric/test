package fr.dati.core

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerticalityDetectorTest {
    private val upright = floatArrayOf(0f, 1f, 0f)
    private val lyingFlat = floatArrayOf(0f, 0f, 1f)

    private fun VerticalityDetector.feed(fromMs: Long, toMs: Long, v: FloatArray): Int =
        (fromMs..toMs step 20).count { t -> onAcceleration(t, v[0], v[1], v[2]) }

    @Test
    fun `phone lying down for the delay triggers once`() {
        val detector = VerticalityDetector(delayMs = 10_000)
        assertEquals(0, detector.feed(0, 5_000, upright))
        assertEquals(1, detector.feed(5_020, 30_000, lyingFlat))
    }

    @Test
    fun `standing up again before the delay cancels`() {
        val detector = VerticalityDetector(delayMs = 10_000)
        detector.feed(0, 1_000, upright)
        assertEquals(0, detector.feed(1_020, 8_000, lyingFlat))
        detector.feed(8_020, 10_000, upright)
        assertEquals(0, detector.feed(10_020, 18_000, lyingFlat))
    }

    @Test
    fun `upside down still counts as vertical`() {
        val detector = VerticalityDetector(delayMs = 1_000)
        assertEquals(0, detector.feed(0, 5_000, floatArrayOf(0f, -1f, 0f)))
    }

    @Test
    fun `moderate tilt below the threshold is tolerated`() {
        val detector = VerticalityDetector(delayMs = 1_000)
        val tilt45 = floatArrayOf(0f, 0.707f, 0.707f)
        assertEquals(0, detector.feed(0, 5_000, tilt45))
    }

    @Test
    fun `walking jolts do not reset an upright phone into an alarm`() {
        val detector = VerticalityDetector(delayMs = 3_000)
        val fired = (0..20_000L step 20).any { t ->
            val bounce = (0.6 * sin(t / 80.0)).toFloat()
            detector.onAcceleration(t, bounce * 0.5f, 1f + bounce, bounce * 0.5f)
        }
        assertFalse(fired)
    }

    @Test
    fun `fires again after the phone was upright in between`() {
        val detector = VerticalityDetector(delayMs = 1_000)
        assertEquals(1, detector.feed(0, 3_000, lyingFlat))
        detector.feed(3_020, 5_000, upright)
        assertTrue(detector.feed(5_020, 8_000, lyingFlat) == 1)
    }
}
