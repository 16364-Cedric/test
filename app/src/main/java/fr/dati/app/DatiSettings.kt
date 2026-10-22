package fr.dati.app

import android.content.Context
import androidx.core.content.edit
import fr.dati.core.DatiConfig
import fr.dati.core.DeploymentConfig
import fr.dati.core.FallSensitivity
import fr.dati.core.RiskZone

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

    /** Durée en secondes d'inclinaison avant pré-alarme de perte de verticalité, 0 pour désactiver. */
    var verticalitySeconds: Int
        get() = prefs.getInt(KEY_VERTICALITY, 0)
        set(value) = prefs.edit { putInt(KEY_VERTICALITY, value.coerceAtLeast(0)) }

    /** Seuil d'alerte batterie en pourcentage, 0 pour désactiver. */
    var batteryThresholdPercent: Int
        get() = prefs.getInt(KEY_BATTERY, 20)
        set(value) = prefs.edit { putInt(KEY_BATTERY, value.coerceIn(0, 90)) }

    var fallDetection: Boolean
        get() = prefs.getBoolean(KEY_FALL, true)
        set(value) = prefs.edit { putBoolean(KEY_FALL, value) }

    var callFirstContact: Boolean
        get() = prefs.getBoolean(KEY_CALL, false)
        set(value) = prefs.edit { putBoolean(KEY_CALL, value) }

    var fallSensitivity: FallSensitivity
        get() = FallSensitivity.entries.find { it.name == prefs.getString(KEY_SENSITIVITY, null) } ?: FallSensitivity.NORMAL
        set(value) = prefs.edit { putString(KEY_SENSITIVITY, value.name) }

    /** SOS par appuis rapides sur le bouton marche/arrêt. */
    var powerButtonSos: Boolean
        get() = prefs.getBoolean(KEY_POWER_SOS, true)
        set(value) = prefs.edit { putBoolean(KEY_POWER_SOS, value) }

    /** Adresse du serveur de supervision, vide si l'entreprise n'en utilise pas. */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_SERVER, value.trim().trimEnd('/')) }

    var deviceToken: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TOKEN, value.trim()) }

    val serverConfigured: Boolean get() = serverUrl.isNotBlank() && deviceToken.isNotBlank()

    /** Zones à risque reçues du serveur (JSON) et leur version ; -1 tant qu'aucune n'a été reçue. */
    var zonesJson: String
        get() = prefs.getString(KEY_ZONES, "[]").orEmpty()
        set(value) = prefs.edit { putString(KEY_ZONES, value) }

    var zonesVersion: Int
        get() = prefs.getInt(KEY_ZONES_VERSION, -1)
        set(value) = prefs.edit { putInt(KEY_ZONES_VERSION, value) }

    /** Zones à risque créées sur le téléphone (même format JSON que celles du serveur). */
    var localZonesJson: String
        get() = prefs.getString(KEY_LOCAL_ZONES, "[]").orEmpty()
        set(value) = prefs.edit { putString(KEY_LOCAL_ZONES, value) }

    /** Zones du serveur et zones locales. */
    fun allZones(): List<RiskZone> = ZoneCodec.parse(zonesJson) + ZoneCodec.parse(localZonesJson)

    /** Heure (horloge murale) du dernier essai complet de la chaîne d'alerte, 0 si jamais. */
    var lastTestAt: Long
        get() = prefs.getLong(KEY_LAST_TEST, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_TEST, value) }

    /** Vrai tant que l'utilisateur n'a pas arrêté la surveillance (permet la reprise après un redémarrage du service). */
    var monitoringActive: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE, false)
        set(value) = prefs.edit { putBoolean(KEY_ACTIVE, value) }

    fun displayName(context: Context): String = userName.ifBlank { context.getString(R.string.default_user_name) }

    /** Applique une configuration d'entreprise ; les champs absents ne changent pas. */
    fun apply(config: DeploymentConfig) {
        config.serverUrl?.let { serverUrl = it }
        config.token?.let { deviceToken = it }
        config.userName?.let { userName = it }
        config.contacts?.let { contacts = it }
        config.checkInMinutes?.let { checkInMinutes = it }
        config.preAlarmSeconds?.let { preAlarmSeconds = it }
        config.immobilitySeconds?.let { immobilitySeconds = it }
        config.verticalitySeconds?.let { verticalitySeconds = it }
        config.batteryThresholdPercent?.let { batteryThresholdPercent = it }
        config.fallDetection?.let { fallDetection = it }
        config.fallSensitivity?.let { fallSensitivity = it }
        config.callFirstContact?.let { callFirstContact = it }
        config.powerButtonSos?.let { powerButtonSos = it }
    }

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
        private const val KEY_VERTICALITY = "verticality_seconds"
        private const val KEY_BATTERY = "battery_threshold"
        private const val KEY_FALL = "fall_detection"
        private const val KEY_CALL = "call_first_contact"
        private const val KEY_ACTIVE = "monitoring_active"
        private const val KEY_SENSITIVITY = "fall_sensitivity"
        private const val KEY_POWER_SOS = "power_button_sos"
        private const val KEY_SERVER = "server_url"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_LAST_TEST = "last_test_at"
        private const val KEY_ZONES = "zones_json"
        private const val KEY_ZONES_VERSION = "zones_version"
        private const val KEY_LOCAL_ZONES = "local_zones_json"
    }
}
