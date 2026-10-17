package fr.dati.core

/**
 * Paramètres de surveillance.
 *
 * @property checkInIntervalMs intervalle entre deux confirmations de présence, `null` pour désactiver.
 * @property preAlarmDurationMs délai laissé à l'utilisateur pour annuler une pré-alarme.
 */
data class DatiConfig(
    val checkInIntervalMs: Long? = 30 * 60_000L,
    val preAlarmDurationMs: Long = 30_000L,
) {
    init {
        require(checkInIntervalMs == null || checkInIntervalMs > 0) { "checkInIntervalMs doit être > 0" }
        require(preAlarmDurationMs > 0) { "preAlarmDurationMs doit être > 0" }
    }
}
