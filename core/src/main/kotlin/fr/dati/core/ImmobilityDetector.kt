package fr.dati.core

import kotlin.math.abs

/**
 * Détecte une absence de mouvement prolongée (perte de verticalité / malaise).
 *
 * Un mouvement est compté dès que la norme de l'accélération varie de plus de
 * [movementThresholdG] par rapport à la valeur de référence. Le détecteur ne se
 * déclenche qu'une fois par période d'immobilité.
 */
class ImmobilityDetector(
    var immobilityDurationMs: Long,
    private val movementThresholdG: Float = 0.08f,
) {
    private var referenceG: Float? = null
    private var lastMovementMs: Long? = null
    private var fired = false

    /** Fournit un échantillon ; renvoie `true` si l'immobilité vient d'atteindre la durée configurée. */
    fun onSample(timestampMs: Long, magnitudeG: Float): Boolean {
        val reference = referenceG
        if (reference == null || abs(magnitudeG - reference) > movementThresholdG) {
            referenceG = magnitudeG
            lastMovementMs = timestampMs
            fired = false
            return false
        }

        val since = lastMovementMs ?: timestampMs.also { lastMovementMs = it }
        if (!fired && timestampMs - since >= immobilityDurationMs) {
            fired = true
            return true
        }
        return false
    }

    /** Repart de zéro, par exemple après une confirmation de l'utilisateur. */
    fun reset() {
        referenceG = null
        lastMovementMs = null
        fired = false
    }
}
