package fr.dati.core

/**
 * Suit la disponibilité du réseau (mobile ou Internet). Une coupure n'est signalée qu'après
 * [graceMs], pour ignorer les micro-coupures (tunnel, ascenseur, changement d'antenne).
 */
class NetworkLossTracker(private val graceMs: Long = 60_000) {
    sealed interface Event {
        data class Lost(val sinceMs: Long) : Event

        data class Restored(val lostAtMs: Long, val durationMs: Long) : Event
    }

    private var unavailableSinceMs: Long? = null

    /** Vrai tant qu'une coupure signalée n'est pas terminée. */
    var isLost = false
        private set

    fun onStatus(timestampMs: Long, available: Boolean): Event? {
        if (available) {
            val since = unavailableSinceMs
            val wasLost = isLost
            unavailableSinceMs = null
            isLost = false
            return if (wasLost && since != null) Event.Restored(since, timestampMs - since) else null
        }
        if (unavailableSinceMs == null) unavailableSinceMs = timestampMs
        return tick(timestampMs)
    }

    /** À appeler régulièrement : signale la coupure une fois le délai de grâce écoulé. */
    fun tick(timestampMs: Long): Event? {
        val since = unavailableSinceMs ?: return null
        if (!isLost && timestampMs - since >= graceMs) {
            isLost = true
            return Event.Lost(since)
        }
        return null
    }
}
