package fr.dati.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Détecte une perte de verticalité : le téléphone, porté debout (poche de poitrine, étui de
 * ceinture), reste incliné de plus de [maxTiltDegrees] pendant [delayMs], comme lorsque la
 * personne est à terre.
 *
 * L'inclinaison est mesurée entre la gravité et l'axe long du téléphone, sur une moyenne glissante
 * de l'accélération pour ne pas réagir aux secousses de la marche.
 */
class VerticalityDetector(
    var delayMs: Long,
    maxTiltDegrees: Double = 60.0,
    private val smoothing: Float = 0.1f,
) {
    private val minVerticalCosine = cos(Math.toRadians(maxTiltDegrees))
    private var gravity: FloatArray? = null
    private var tiltedSinceMs: Long? = null
    private var fired = false

    /** Accélération en g ; renvoie `true` quand l'inclinaison vient de dépasser le délai. */
    fun onAcceleration(timestampMs: Long, x: Float, y: Float, z: Float): Boolean {
        val g = gravity?.also {
            it[0] += smoothing * (x - it[0])
            it[1] += smoothing * (y - it[1])
            it[2] += smoothing * (z - it[2])
        } ?: floatArrayOf(x, y, z).also { gravity = it }

        val norm = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        if (norm < MIN_GRAVITY_G) return false // chute libre ou mesure aberrante : pas d'orientation fiable

        if (abs(g[1]) / norm >= minVerticalCosine) {
            tiltedSinceMs = null
            fired = false
            return false
        }
        val since = tiltedSinceMs ?: timestampMs.also { tiltedSinceMs = it }
        if (!fired && timestampMs - since >= delayMs) {
            fired = true
            return true
        }
        return false
    }

    fun reset() {
        gravity = null
        tiltedSinceMs = null
        fired = false
    }

    private companion object {
        const val MIN_GRAVITY_G = 0.5f
    }
}
