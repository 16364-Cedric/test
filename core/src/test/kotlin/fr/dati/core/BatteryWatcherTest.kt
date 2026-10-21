package fr.dati.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatteryWatcherTest {
    @Test
    fun `warns once when dropping below the threshold`() {
        val watcher = BatteryWatcher(thresholdPercent = 20)
        assertFalse(watcher.onReading(21, charging = false))
        assertFalse(watcher.onReading(20, charging = false))
        assertTrue(watcher.onReading(19, charging = false))
        assertFalse(watcher.onReading(18, charging = false))
        assertFalse(watcher.onReading(5, charging = false))
    }

    @Test
    fun `no warning while charging, re-armed after charging`() {
        val watcher = BatteryWatcher(thresholdPercent = 20)
        assertTrue(watcher.onReading(15, charging = false))
        assertFalse(watcher.onReading(15, charging = true))
        assertTrue(watcher.onReading(15, charging = false))
    }

    @Test
    fun `small fluctuations around the threshold do not re-warn`() {
        val watcher = BatteryWatcher(thresholdPercent = 20)
        assertTrue(watcher.onReading(19, charging = false))
        assertFalse(watcher.onReading(21, charging = false))
        assertFalse(watcher.onReading(19, charging = false))
        assertFalse(watcher.onReading(25, charging = false))
        assertTrue(watcher.onReading(19, charging = false))
    }
}
