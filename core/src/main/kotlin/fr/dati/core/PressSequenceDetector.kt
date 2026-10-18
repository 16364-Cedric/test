package fr.dati.core

/**
 * Reconnaît une série d'appuis rapides (par exemple sur le bouton marche/arrêt),
 * utilisée pour déclencher un SOS sans regarder l'écran.
 */
class PressSequenceDetector(
    private val requiredPresses: Int = 3,
    private val windowMs: Long = 2_000,
) {
    init {
        require(requiredPresses >= 2) { "requiredPresses doit être >= 2" }
    }

    private val presses = ArrayDeque<Long>()

    /** Enregistre un appui ; renvoie `true` quand la série est complète. */
    fun onPress(timestampMs: Long): Boolean {
        presses.addLast(timestampMs)
        while (presses.isNotEmpty() && timestampMs - presses.first() > windowMs) presses.removeFirst()
        if (presses.size >= requiredPresses) {
            presses.clear()
            return true
        }
        return false
    }
}
