package fr.dati.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DatiStateMachineTest {
    private val config = DatiConfig(checkInIntervalMs = 60_000, preAlarmDurationMs = 10_000)

    @Test
    fun `start schedules the next check-in`() {
        val machine = DatiStateMachine(config)
        machine.start(1_000)
        assertEquals(DatiState.Monitoring(61_000), machine.state)
    }

    @Test
    fun `missed check-in leads to pre-alarm then alarm`() {
        val machine = DatiStateMachine(config)
        machine.start(0)
        machine.tick(59_999)
        assertIs<DatiState.Monitoring>(machine.state)

        machine.tick(60_000)
        assertEquals(DatiState.PreAlarm(AlarmReason.MISSED_CHECK_IN, 70_000), machine.state)

        machine.tick(70_000)
        assertEquals(DatiState.Alarm(AlarmReason.MISSED_CHECK_IN, 70_000), machine.state)
    }

    @Test
    fun `confirming during pre-alarm cancels it`() {
        val machine = DatiStateMachine(config)
        machine.start(0)
        machine.onDetection(AlarmReason.FALL, 5_000)
        assertEquals(DatiState.PreAlarm(AlarmReason.FALL, 15_000), machine.state)

        machine.confirmOk(8_000)
        assertEquals(DatiState.Monitoring(68_000), machine.state)
        machine.tick(15_000)
        assertIs<DatiState.Monitoring>(machine.state)
    }

    @Test
    fun `sos triggers the alarm immediately`() {
        val machine = DatiStateMachine(config)
        machine.start(0)
        machine.onDetection(AlarmReason.SOS, 3_000)
        assertEquals(DatiState.Alarm(AlarmReason.SOS, 3_000), machine.state)
    }

    @Test
    fun `sos during pre-alarm escalates`() {
        val machine = DatiStateMachine(config)
        machine.start(0)
        machine.onDetection(AlarmReason.IMMOBILITY, 1_000)
        machine.onDetection(AlarmReason.SOS, 2_000)
        assertEquals(DatiState.Alarm(AlarmReason.SOS, 2_000), machine.state)
    }

    @Test
    fun `detections are ignored when idle`() {
        val machine = DatiStateMachine(config)
        machine.onDetection(AlarmReason.SOS, 0)
        machine.tick(1_000_000)
        assertEquals(DatiState.Idle, machine.state)
    }

    @Test
    fun `disabled check-in never expires`() {
        val machine = DatiStateMachine(DatiConfig(checkInIntervalMs = null))
        machine.start(0)
        machine.tick(Long.MAX_VALUE)
        assertEquals(DatiState.Monitoring(null), machine.state)
    }

    @Test
    fun `listener is notified of each transition`() {
        val transitions = mutableListOf<Pair<DatiState, DatiState>>()
        val machine = DatiStateMachine(config) { old, new -> transitions += old to new }
        machine.start(0)
        machine.tick(10) // pas de changement
        machine.stop()
        assertEquals(
            listOf(
                DatiState.Idle to DatiState.Monitoring(60_000),
                DatiState.Monitoring(60_000) to DatiState.Idle,
            ),
            transitions,
        )
    }

    @Test
    fun `a test goes through pre-alarm like a real detection`() {
        val machine = DatiStateMachine(DatiConfig(checkInIntervalMs = null, preAlarmDurationMs = 10_000))
        machine.start(0)
        machine.onDetection(AlarmReason.TEST, 1_000)
        assertEquals(DatiState.PreAlarm(AlarmReason.TEST, 11_000), machine.state)
        machine.tick(11_000)
        assertEquals(DatiState.Alarm(AlarmReason.TEST, 11_000), machine.state)
    }
}
