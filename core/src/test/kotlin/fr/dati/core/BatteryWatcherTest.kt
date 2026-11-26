package fr.dati.core

import fr.dati.core.BatteryWatcher.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BatteryWatcherTest {
    @Test
    fun `warns once below 20 percent, then once below 10 percent`() {
        val watcher = BatteryWatcher(thresholdPercent = 20, criticalPercent = 10)
        assertNull(watcher.onReading(21, charging = false))
        assertNull(watcher.onReading(20, charging = false))
        assertEquals(Level.LOW, watcher.onReading(19, charging = false))
        assertNull(watcher.onReading(15, charging = false))
        assertNull(watcher.onReading(10, charging = false))
        assertEquals(Level.CRITICAL, watcher.onReading(9, charging = false))
        assertNull(watcher.onReading(5, charging = false))
    }

    @Test
    fun `a sudden drop announces only the critical level`() {
        val watcher = BatteryWatcher(thresholdPercent = 20, criticalPercent = 10)
        assertEquals(Level.CRITICAL, watcher.onReading(8, charging = false))
        assertNull(watcher.onReading(7, charging = false))
    }

    @Test
    fun `no warning while charging, re-armed after charging`() {
        val watcher = BatteryWatcher(thresholdPercent = 20, criticalPercent = 10)
        assertEquals(Level.LOW, watcher.onReading(15, charging = false))
        assertNull(watcher.onReading(15, charging = true))
        assertEquals(Level.LOW, watcher.onReading(15, charging = false))
    }

    @Test
    fun `small fluctuations around the threshold do not re-warn`() {
        val watcher = BatteryWatcher(thresholdPercent = 20, criticalPercent = 10)
        assertEquals(Level.LOW, watcher.onReading(19, charging = false))
        assertNull(watcher.onReading(21, charging = false))
        assertNull(watcher.onReading(19, charging = false))
        assertNull(watcher.onReading(25, charging = false))
        assertEquals(Level.LOW, watcher.onReading(19, charging = false))
    }

    @Test
    fun `a level set to zero is disabled`() {
        val noLow = BatteryWatcher(thresholdPercent = 0, criticalPercent = 10)
        assertNull(noLow.onReading(15, charging = false))
        assertEquals(Level.CRITICAL, noLow.onReading(9, charging = false))

        val noCritical = BatteryWatcher(thresholdPercent = 20, criticalPercent = 0)
        assertEquals(Level.LOW, noCritical.onReading(19, charging = false))
        assertNull(noCritical.onReading(3, charging = false))
    }
}
