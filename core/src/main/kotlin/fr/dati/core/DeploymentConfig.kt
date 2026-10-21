package fr.dati.core

import java.net.URLDecoder

/**
 * Configuration poussée par l'entreprise, via un QR code (`dati://config?...`) ou un outil de
 * gestion de flotte (MDM). Chaque champ absent laisse le réglage existant inchangé.
 */
data class DeploymentConfig(
    val serverUrl: String? = null,
    val token: String? = null,
    val userName: String? = null,
    val contacts: List<String>? = null,
    val checkInMinutes: Int? = null,
    val preAlarmSeconds: Int? = null,
    val immobilitySeconds: Int? = null,
    val verticalitySeconds: Int? = null,
    val batteryThresholdPercent: Int? = null,
    val fallDetection: Boolean? = null,
    val fallSensitivity: FallSensitivity? = null,
    val callFirstContact: Boolean? = null,
    val powerButtonSos: Boolean? = null,
) {
    val isEmpty: Boolean get() = this == DeploymentConfig()

    companion object {
        const val KEY_SERVER = "server"
        const val KEY_TOKEN = "token"
        const val KEY_NAME = "name"
        const val KEY_CONTACTS = "contacts"
        const val KEY_CHECK_IN = "checkin"
        const val KEY_PRE_ALARM = "prealarm"
        const val KEY_IMMOBILITY = "immobility"
        const val KEY_VERTICALITY = "verticality"
        const val KEY_BATTERY = "battery"
        const val KEY_FALL = "fall"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_CALL = "call"
        const val KEY_POWER_SOS = "powersos"

        /**
         * Lit un lien de configuration : `dati://config?…` ou la page d'enrôlement `https://…/c#…`
         * (les paramètres sont dans le fragment, qui n'est jamais envoyé au serveur).
         */
        fun fromLink(link: String): DeploymentConfig {
            val trimmed = link.trim()
            val query = when {
                '#' in trimmed -> trimmed.substringAfter('#')
                '?' in trimmed -> trimmed.substringAfter('?')
                else -> throw IllegalArgumentException("Lien de configuration invalide")
            }
            return fromQuery(query)
        }

        /** Lit la partie requête d'un lien `dati://config?...`. */
        fun fromQuery(query: String): DeploymentConfig = fromMap(
            query.split('&').filter { it.isNotEmpty() }.associate { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                decode(key) to decode(value)
            }
        )

        /**
         * @throws IllegalArgumentException si une valeur est invalide : une configuration douteuse
         * (par exemple un serveur non chiffré) est refusée en bloc plutôt qu'appliquée à moitié.
         */
        fun fromMap(values: Map<String, String>): DeploymentConfig {
            fun text(key: String) = values[key]?.trim()?.takeIf { it.isNotEmpty() }

            fun int(key: String, min: Int, max: Int = Int.MAX_VALUE) = text(key)?.let {
                val n = requireNotNull(it.toIntOrNull()) { "$key : nombre attendu" }
                require(n in min..max) { "$key : doit être compris entre $min et $max" }
                n
            }

            fun bool(key: String) = text(key)?.lowercase()?.let {
                when (it) {
                    "1", "true", "oui", "yes" -> true
                    "0", "false", "non", "no" -> false
                    else -> throw IllegalArgumentException("$key : oui/non attendu")
                }
            }

            val server = text(KEY_SERVER)?.trimEnd('/')
            require(server == null || server.startsWith("https://")) { "Le serveur doit utiliser https://" }

            return DeploymentConfig(
                serverUrl = server,
                token = text(KEY_TOKEN),
                userName = text(KEY_NAME),
                contacts = text(KEY_CONTACTS)?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.map {
                    requireNotNull(PhoneNumbers.normalize(it)) { "Numéro invalide : $it" }
                },
                checkInMinutes = int(KEY_CHECK_IN, 0),
                preAlarmSeconds = int(KEY_PRE_ALARM, 10),
                immobilitySeconds = int(KEY_IMMOBILITY, 0),
                verticalitySeconds = int(KEY_VERTICALITY, 0),
                batteryThresholdPercent = int(KEY_BATTERY, 0, 90),
                fallDetection = bool(KEY_FALL),
                fallSensitivity = text(KEY_SENSITIVITY)?.let {
                    requireNotNull(FallSensitivity.entries.find { s -> s.name.equals(it, ignoreCase = true) }) {
                        "Sensibilité inconnue : $it"
                    }
                },
                callFirstContact = bool(KEY_CALL),
                powerButtonSos = bool(KEY_POWER_SOS),
            )
        }

        // Surcharge prenant le nom du jeu de caractères : celle prenant un Charset n'existe qu'à partir d'Android 13.
        private fun decode(s: String): String = URLDecoder.decode(s, "UTF-8")
    }
}
