package fr.dati.core

sealed interface DatiState {
    /** Surveillance arrêtée. */
    data object Idle : DatiState

    /** Surveillance active ; [nextCheckInAtMs] vaut `null` si la confirmation périodique est désactivée. */
    data class Monitoring(val nextCheckInAtMs: Long?) : DatiState

    /** Alarme imminente : l'utilisateur peut encore l'annuler jusqu'à [deadlineMs]. */
    data class PreAlarm(val reason: AlarmReason, val deadlineMs: Long) : DatiState

    /** Alarme déclenchée : les secours ont été prévenus. */
    data class Alarm(val reason: AlarmReason, val triggeredAtMs: Long) : DatiState
}
