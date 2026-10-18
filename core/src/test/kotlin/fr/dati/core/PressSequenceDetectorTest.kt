package fr.dati.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PressSequenceDetectorTest {
    @Test
    fun `three quick presses complete the sequence`() {
        val detector = PressSequenceDetector()
        assertFalse(detector.onPress(0))
        assertFalse(detector.onPress(500))
        assertTrue(detector.onPress(1_000))
    }

    @Test
    fun `slow presses are ignored`() {
        val detector = PressSequenceDetector()
        assertFalse(detector.onPress(0))
        assertFalse(detector.onPress(1_500))
        assertFalse(detector.onPress(3_000))
        assertFalse(detector.onPress(4_600))
    }

    @Test
    fun `sequence restarts after firing`() {
        val detector = PressSequenceDetector()
        detector.onPress(0)
        detector.onPress(100)
        assertTrue(detector.onPress(200))
        assertFalse(detector.onPress(300))
        assertFalse(detector.onPress(400))
        assertTrue(detector.onPress(500))
    }
}
