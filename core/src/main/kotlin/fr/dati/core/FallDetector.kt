package fr.dati.core

/**
 * Détecte une chute à partir de la norme de l'accélération (en g).
 *
 * Une chute se caractérise par une phase de chute libre (norme nettement inférieure à 1 g)
 * suivie, peu après, d'un choc (norme nettement supérieure à 1 g).
 */
class FallDetector(
    private val freeFallThresholdG: Float = 0.5f,
    private val impactThresholdG: Float = 2.5f,
    private val minFreeFallMs: Long = 80,
    private val maxFreeFallToImpactMs: Long = 1_000,
) {
    private var freeFallStartMs: Long? = null
    private var freeFallEndMs: Long? = null

    /** Fournit un échantillon ; renvoie `true` si une chute vient d'être détectée. */
    fun onSample(timestampMs: Long, magnitudeG: Float): Boolean {
        val start = freeFallStartMs
        if (magnitudeG < freeFallThresholdG) {
            if (start == null) freeFallStartMs = timestampMs
            return false
        }

        if (start != null) {
            freeFallStartMs = null
            if (timestampMs - start >= minFreeFallMs) freeFallEndMs = timestampMs
        }

        val end = freeFallEndMs ?: return false
        if (timestampMs - end > maxFreeFallToImpactMs) {
            freeFallEndMs = null
            return false
        }
        if (magnitudeG >= impactThresholdG) {
            reset()
            return true
        }
        return false
    }

    fun reset() {
        freeFallStartMs = null
        freeFallEndMs = null
    }
}
