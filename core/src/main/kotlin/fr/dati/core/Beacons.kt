package fr.dati.core

/**
 * Identifiants de balises Bluetooth : adresse matérielle (`aa:bb:cc:dd:ee:ff`) ou iBeacon
 * (`uuid:major:minor`), comparés en minuscules.
 */
object Beacons {
    private val MAC = Regex("^([0-9a-f]{2}:){5}[0-9a-f]{2}$")
    private val IBEACON = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}:(\\d{1,5}):(\\d{1,5})$")

    /** Forme canonique, ou `null` si l'identifiant n'est pas reconnu. */
    fun normalize(raw: String): String? {
        val id = raw.trim().lowercase()
        if (MAC.matches(id)) return id
        val match = IBEACON.matchEntire(id) ?: return null
        val (major, minor) = match.destructured
        if (major.toInt() > 65_535 || minor.toInt() > 65_535) return null
        return "${id.substringBefore(':')}:${major.toInt()}:${minor.toInt()}"
    }

    fun iBeaconId(uuid: String, major: Int, minor: Int) = "${uuid.lowercase()}:$major:$minor"
}
