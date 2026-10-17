package fr.dati.core

import kotlin.math.sqrt

/**
 * Détecte une absence de mouvement prolongée (perte de verticalité / malaise).
 *
 * Deux sources comptent comme mouvement :
 * - l'accéléromètre, dès que le vecteur d'accélération s'écarte de plus de
 *   [accelerationThresholdG] de la référence (changement d'orientation ou secousse) ;
 * - le gyroscope, dès que la vitesse de rotation dépasse [rotationThresholdRadS].
 *
 * Le détecteur ne se déclenche qu'une fois par période d'immobilité.
 */
class ImmobilityDetector(
    var immobilityDurationMs: Long,
    private val accelerationThresholdG: Float = 0.08f,
    private val rotationThresholdRadS: Float = 0.15f,
) {
    private var reference: FloatArray? = null
    private var lastMovementMs: Long? = null
    private var fired = false

    /** Accélération en g sur les trois axes ; renvoie `true` si l'immobilité vient d'atteindre la durée configurée. */
    fun onAcceleration(timestampMs: Long, x: Float, y: Float, z: Float): Boolean {
        val ref = reference
        if (ref == null || norm(x - ref[0], y - ref[1], z - ref[2]) > accelerationThresholdG) {
            reference = floatArrayOf(x, y, z)
            markMovement(timestampMs)
            return false
        }
        return checkElapsed(timestampMs)
    }

    /** Vitesse de rotation en rad/s sur les trois axes ; renvoie `true` si l'immobilité vient d'atteindre la durée configurée. */
    fun onRotation(timestampMs: Long, x: Float, y: Float, z: Float): Boolean {
        if (norm(x, y, z) > rotationThresholdRadS) {
            markMovement(timestampMs)
            return false
        }
        return checkElapsed(timestampMs)
    }

    /** Repart de zéro, par exemple après une confirmation de l'utilisateur. */
    fun reset() {
        reference = null
        lastMovementMs = null
        fired = false
    }

    private fun markMovement(timestampMs: Long) {
        lastMovementMs = timestampMs
        fired = false
    }

    private fun checkElapsed(timestampMs: Long): Boolean {
        val since = lastMovementMs ?: timestampMs.also { lastMovementMs = it }
        if (!fired && timestampMs - since >= immobilityDurationMs) {
            fired = true
            return true
        }
        return false
    }

    private fun norm(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)
}
