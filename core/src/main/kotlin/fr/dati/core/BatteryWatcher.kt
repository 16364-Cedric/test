package fr.dati.core

/**
 * Signale le passage sous deux paliers de batterie : faible, puis critique. Chaque palier n'est
 * signalé qu'une fois ; tout est réarmé après une recharge. Un seuil à 0 désactive le palier.
 */
class BatteryWatcher(
    var thresholdPercent: Int,
    var criticalPercent: Int = 10,
) {
    enum class Level { LOW, CRITICAL }

    private var warnedLow = false
    private var warnedCritical = false

    /** Renvoie le palier qui vient d'être franchi sans être en charge, sinon `null`. */
    fun onReading(percent: Int, charging: Boolean): Level? {
        if (charging || percent >= maxOf(thresholdPercent, criticalPercent) + HYSTERESIS_PERCENT) {
            warnedLow = false
            warnedCritical = false
            return null
        }
        if (criticalPercent > 0 && percent < criticalPercent && !warnedCritical) {
            warnedCritical = true
            warnedLow = true // inutile d'annoncer ensuite « faible » : « critique » est plus grave
            return Level.CRITICAL
        }
        if (thresholdPercent > 0 && percent < thresholdPercent && !warnedLow) {
            warnedLow = true
            return Level.LOW
        }
        return null
    }

    private companion object {
        const val HYSTERESIS_PERCENT = 5
    }
}
