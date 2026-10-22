package fr.dati.core

/**
 * Mesure le temps passé dans chaque zone à risque à partir des positions GPS.
 *
 * Les positions trop imprécises (> [maxAccuracyM]) sont ignorées : la présence reste celle de la
 * dernière position fiable, pour ne pas sortir ni entrer à tort à cause d'un écart du GPS.
 * Le temps continue de courir entre deux positions grâce à [tick].
 */
class ZoneDwellTracker(
    zones: List<RiskZone> = emptyList(),
    private val warnBeforeMs: Long = 60_000,
    private val maxAccuracyM: Float = 50f,
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

    var zones: List<RiskZone> = zones
        set(value) {
            field = value
            val ids = value.map { it.id }.toSet()
            presences.keys.retainAll(ids)
        }

    /** Zones où la personne se trouve, avec l'heure d'entrée. */
    fun currentZones(): List<Pair<RiskZone, Long>> =
        zones.mapNotNull { zone -> presences[zone.id]?.let { zone to it.sinceMs } }

    fun onLocation(timestampMs: Long, point: GeoPoint, accuracyM: Float): List<Event> {
        if (accuracyM > maxAccuracyM) return tick(timestampMs)
        val events = mutableListOf<Event>()
        for (zone in zones) {
            val inside = zone.contains(point)
            val presence = presences[zone.id]
            if (inside && presence == null) {
                presences[zone.id] = Presence(timestampMs)
                events += Event.Entered(zone)
            } else if (!inside && presence != null) {
                presences.remove(zone.id)
                events += Event.Left(zone, timestampMs - presence.sinceMs)
            }
        }
        return events + tick(timestampMs)
    }

    fun tick(timestampMs: Long): List<Event> {
        val events = mutableListOf<Event>()
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
    }
}
