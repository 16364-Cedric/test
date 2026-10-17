package fr.dati.app

import android.content.Context
import androidx.core.content.edit
import fr.dati.core.DatiConfig

/** Préférences de l'utilisateur, stockées localement sur le téléphone. */
class DatiSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dati", Context.MODE_PRIVATE)

    var userName: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_NAME, value.trim()) }

    /** Numéros des personnes à prévenir, dans l'ordre de priorité. */
    var contacts: List<String>
        get() = prefs.getString(KEY_CONTACTS, "").orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        set(value) = prefs.edit { putString(KEY_CONTACTS, value.joinToString("\n")) }

    /** Intervalle de confirmation de présence en minutes, 0 pour désactiver. */
    var checkInMinutes: Int
        get() = prefs.getInt(KEY_CHECK_IN, 30)
        set(value) = prefs.edit { putInt(KEY_CHECK_IN, value.coerceAtLeast(0)) }

    var preAlarmSeconds: Int
        get() = prefs.getInt(KEY_PRE_ALARM, 30)
        set(value) = prefs.edit { putInt(KEY_PRE_ALARM, value.coerceAtLeast(MIN_PRE_ALARM_SECONDS)) }

    /** Durée d'immobilité avant pré-alarme en secondes, 0 pour désactiver. */
    var immobilitySeconds: Int
        get() = prefs.getInt(KEY_IMMOBILITY, 120)
        set(value) = prefs.edit { putInt(KEY_IMMOBILITY, value.coerceAtLeast(0)) }

    var fallDetection: Boolean
        get() = prefs.getBoolean(KEY_FALL, true)
        set(value) = prefs.edit { putBoolean(KEY_FALL, value) }

    var callFirstContact: Boolean
        get() = prefs.getBoolean(KEY_CALL, false)
        set(value) = prefs.edit { putBoolean(KEY_CALL, value) }

    /** Vrai tant que l'utilisateur n'a pas arrêté la surveillance (permet la reprise après un redémarrage du service). */
    var monitoringActive: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_ACTIVE, value) }

    fun displayName(context: Context): String = userName.ifBlank { context.getString(R.string.default_user_name) }

    fun toConfig() = DatiConfig(
        checkInIntervalMs = checkInMinutes.takeIf { it > 0 }?.let { it * 60_000L },
        preAlarmDurationMs = preAlarmSeconds.coerceAtLeast(MIN_PRE_ALARM_SECONDS) * 1_000L,
    )

    companion object {
        const val MIN_PRE_ALARM_SECONDS = 10

        private const val KEY_NAME = "user_name"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_CHECK_IN = "check_in_minutes"
        private const val KEY_PRE_ALARM = "pre_alarm_seconds"
        private const val KEY_IMMOBILITY = "immobility_seconds"
        private const val KEY_FALL = "fall_detection"
        private const val KEY_CALL = "call_first_contact"
        private const val KEY_ACTIVE = "monitoring_active"
    }
}
