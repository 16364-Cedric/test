package fr.dati.core

/**
 * Mesure le temps passé dans chaque zone à risque.
 *
 * - Zones sans balise : présence d'après le GPS. Les positions trop imprécises (> [maxAccuracyM])
 *   sont ignorées : la présence reste celle de la dernière position fiable.
 * - Zones avec balises Bluetooth : présence tant qu'une de leurs balises a été captée avec un
 *   signal suffisant depuis moins de [beaconTimeoutMs].
 *
 * Le temps continue de courir entre deux mesures grâce à [tick].
 */
class ZoneDwellTracker(
    zones: List<RiskZone> = emptyList(),
    private val warnBeforeMs: Long = 60_000,
    private val maxAccuracyM: Float = 50f,
    private val beaconTimeoutMs: Long = 30_000,
) {
    sealed interface Event {
        val zone: RiskZone

        data class Entered(override val zone: RiskZone) : Event

        data class Left(override val zone: RiskZone, val durationMs: Long) : Event

        data class Warning(override val zone: RiskZone, val remainingMs: Long) : Event

        data class Exceeded(override val zone: RiskZone) : Event
    }

    private class Presence(var sinceMs: Long, var warned: Boolean = false, var exceeded: Boolean = false)

    private val presences = mutableMapOf<String, Presence>()

    /** Dernière réception suffisamment forte de chaque balise. */
    private val beaconSeenAt = mutableMapOf<String, Long>()

    var zones: List<RiskZone> = zones
        set(value) {
            field = value
            val ids = value.map { it.id }.toSet()
            presences.keys.retainAll(ids)
        }

    /** Zones où la personne se trouve, avec l'heure d'entrée. */
    fun currentZones(): List<Pair<RiskZone, Long>> =
        zones.mapNotNull { zone -> presences[zone.id]?.let { zone to it.sinceMs } }

    /** Balises dont l'application doit écouter les signaux. */
    val beaconIds: Set<String> get() = zones.flatMapTo(mutableSetOf()) { it.beacons }

    fun onLocation(timestampMs: Long, point: GeoPoint, accuracyM: Float): List<Event> {
        if (accuracyM > maxAccuracyM) return tick(timestampMs)
        val events = mutableListOf<Event>()
        for (zone in zones) {
            if (!zone.usesBeacons) updatePresence(zone, zone.contains(point), timestampMs, events)
        }
        return events + tick(timestampMs)
    }

    fun onBeacon(timestampMs: Long, beaconId: String, rssi: Int): List<Event> {
        val id = Beacons.normalize(beaconId) ?: return tick(timestampMs)
        if (zones.any { id in it.beacons && rssi >= it.minRssi }) beaconSeenAt[id] = timestampMs
        return tick(timestampMs)
    }

    fun tick(timestampMs: Long): List<Event> {
        val events = mutableListOf<Event>()
        for (zone in zones) {
            if (!zone.usesBeacons) continue
            val inside = zone.beacons.any { id -> beaconSeenAt[id]?.let { timestampMs - it <= beaconTimeoutMs } == true }
            updatePresence(zone, inside, timestampMs, events)
        }
        for (zone in zones) {
            val presence = presences[zone.id] ?: continue
            val elapsed = timestampMs - presence.sinceMs
            if (!presence.exceeded && elapsed >= zone.maxDwellMs) {
                presence.exceeded = true
                presence.warned = true
                events += Event.Exceeded(zone)
            } else if (!presence.warned && warnBeforeMs < zone.maxDwellMs && elapsed >= zone.maxDwellMs - warnBeforeMs) {
                presence.warned = true
                events += Event.Warning(zone, zone.maxDwellMs - elapsed)
            }
        }
        return events
    }

    /** Après confirmation de l'utilisateur, relance le décompte des zones dépassées. */
    fun acknowledgeExceeded(timestampMs: Long) {
        for (presence in presences.values) {
            if (presence.exceeded) {
                presence.sinceMs = timestampMs
                presence.warned = false
                presence.exceeded = false
            }
        }
    }

    fun reset() {
        presences.clear()
        beaconSeenAt.clear()
    }

    private fun updatePresence(zone: RiskZone, inside: Boolean, timestampMs: Long, events: MutableList<Event>) {
        val presence = presences[zone.id]
        if (inside && presence == null) {
            presences[zone.id] = Presence(timestampMs)
            events += Event.Entered(zone)
        } else if (!inside && presence != null) {
            presences.remove(zone.id)
            events += Event.Left(zone, timestampMs - presence.sinceMs)
        }
    }
}
