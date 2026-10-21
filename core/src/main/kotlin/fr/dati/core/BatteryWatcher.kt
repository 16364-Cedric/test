package fr.dati.core

/** Signale une seule fois le passage sous le seuil de batterie, puis se réarme après recharge. */
class BatteryWatcher(var thresholdPercent: Int) {
    private var warned = false

    /** Renvoie `true` quand la batterie vient de passer sous le seuil sans être en charge. */
    fun onReading(percent: Int, charging: Boolean): Boolean {
        if (charging || percent >= thresholdPercent + HYSTERESIS_PERCENT) {
            warned = false
            return false
        }
        if (percent < thresholdPercent && !warned) {
            warned = true
            return true
        }
        return false
    }

    private companion object {
        const val HYSTERESIS_PERCENT = 5
    }
}
