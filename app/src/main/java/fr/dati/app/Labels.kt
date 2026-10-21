package fr.dati.app

import android.content.Context
import fr.dati.core.AlarmReason

fun AlarmReason.label(context: Context): String = context.getString(
    when (this) {
        AlarmReason.FALL -> R.string.reason_fall
        AlarmReason.IMMOBILITY -> R.string.reason_immobility
        AlarmReason.LOSS_OF_VERTICALITY -> R.string.reason_loss_of_verticality
        AlarmReason.MISSED_CHECK_IN -> R.string.reason_missed_check_in
        AlarmReason.SOS -> R.string.reason_sos
        AlarmReason.TEST -> R.string.reason_test
    }
)

/** Formate une durée en minutes:secondes. */
fun formatDuration(ms: Long): String {
    val seconds = (ms / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
