package fr.dati.core

/**
 * Machine à états du DATI.
 *
 * ```
 * Idle --start--> Monitoring --détection / échéance--> PreAlarm --délai écoulé--> Alarm
 *                     ^                                   |                         |
 *                     +------------- confirmOk -----------+-------------------------+
 * ```
 * Un SOS passe directement en [DatiState.Alarm]. La machine est pilotée par le temps
 * fourni par l'appelant, ce qui la rend déterministe et testable.
 */
class DatiStateMachine(
    config: DatiConfig,
    private val listener: (old: DatiState, new: DatiState) -> Unit = { _, _ -> },
) {
    var config: DatiConfig = config
        private set

    var state: DatiState = DatiState.Idle
        private set

    fun start(nowMs: Long) {
        if (state == DatiState.Idle) moveTo(monitoringFrom(nowMs))
    }

    fun stop() {
        moveTo(DatiState.Idle)
    }

    /** Applique une nouvelle configuration ; l'échéance de confirmation repart de [nowMs]. */
    fun updateConfig(newConfig: DatiConfig, nowMs: Long) {
        config = newConfig
        if (state is DatiState.Monitoring) moveTo(monitoringFrom(nowMs))
    }

    /** L'utilisateur confirme qu'il va bien : annule une pré-alarme ou une alarme et relance le compteur. */
    fun confirmOk(nowMs: Long) {
        if (state != DatiState.Idle) moveTo(monitoringFrom(nowMs))
    }

    /** Signale un événement détecté par les capteurs ou l'utilisateur. */
    fun onDetection(reason: AlarmReason, nowMs: Long) {
        when (state) {
            DatiState.Idle -> Unit
            is DatiState.Alarm -> Unit
            is DatiState.PreAlarm ->
                if (reason == AlarmReason.SOS) moveTo(DatiState.Alarm(reason, nowMs))
            is DatiState.Monitoring ->
                if (reason == AlarmReason.SOS) {
                    moveTo(DatiState.Alarm(reason, nowMs))
                } else {
                    moveTo(DatiState.PreAlarm(reason, nowMs + config.preAlarmDurationMs))
                }
        }
    }

    /** À appeler régulièrement (ex. chaque seconde) pour faire avancer les échéances. */
    fun tick(nowMs: Long) {
        when (val current = state) {
            is DatiState.Monitoring -> {
                val due = current.nextCheckInAtMs
                if (due != null && nowMs >= due) {
                    moveTo(DatiState.PreAlarm(AlarmReason.MISSED_CHECK_IN, nowMs + config.preAlarmDurationMs))
                }
            }
            is DatiState.PreAlarm ->
                if (nowMs >= current.deadlineMs) moveTo(DatiState.Alarm(current.reason, nowMs))
            DatiState.Idle, is DatiState.Alarm -> Unit
        }
    }

    private fun monitoringFrom(nowMs: Long) =
        DatiState.Monitoring(config.checkInIntervalMs?.let { nowMs + it })

    private fun moveTo(newState: DatiState) {
        val old = state
        if (old == newState) return
        state = newState
        listener(old, newState)
    }
}
