package fr.dati.app

import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import fr.dati.app.databinding.DialogPasswordBinding
import fr.dati.core.AdminPassword
import fr.dati.core.AttemptLimiter

/**
 * Mode administrateur : quand un mot de passe est défini, les réglages et les zones locales
 * ne sont modifiables qu'après l'avoir saisi. Le déverrouillage dure quelques minutes.
 *
 * Restent toujours accessibles au salarié : démarrer et arrêter la protection, « Je vais bien »,
 * SOS, l'essai, la carte et le journal en lecture.
 */
object AdminGate {
    private const val UNLOCK_DURATION_MS = 5 * 60_000L

    private val limiter = AttemptLimiter()
    private var unlockedUntilMs = 0L

    fun isProtected(activity: AppCompatActivity) = DatiSettings(activity).adminPasswordHash.isNotEmpty()

    fun isUnlocked(activity: AppCompatActivity) =
        !isProtected(activity) || SystemClock.elapsedRealtime() < unlockedUntilMs

    fun lock() {
        unlockedUntilMs = 0L
    }

    /** Exécute [onGranted] tout de suite si rien n'est protégé ou déjà déverrouillé, sinon après saisie du mot de passe. */
    fun require(activity: AppCompatActivity, onGranted: () -> Unit) {
        if (isUnlocked(activity)) {
            onGranted()
            return
        }
        val waitMs = limiter.remainingLockoutMs(SystemClock.elapsedRealtime())
        if (waitMs > 0) {
            Toast.makeText(activity, activity.getString(R.string.admin_locked_out, (waitMs + 999) / 1_000), Toast.LENGTH_LONG).show()
            return
        }
        val form = DialogPasswordBinding.inflate(activity.layoutInflater)
        AlertDialog.Builder(activity)
            .setTitle(R.string.admin_title)
            .setMessage(R.string.admin_prompt)
            .setView(form.root)
            .setPositiveButton(R.string.admin_unlock) { _, _ ->
                val now = SystemClock.elapsedRealtime()
                val log = EventLog(activity)
                if (AdminPassword.verify(form.passwordInput.text.toString(), DatiSettings(activity).adminPasswordHash)) {
                    limiter.onSuccess()
                    unlockedUntilMs = now + UNLOCK_DURATION_MS
                    log.add("Mode administrateur ouvert")
                    onGranted()
                } else {
                    limiter.onFailure(now)
                    log.add("Mot de passe administrateur erroné")
                    Toast.makeText(activity, R.string.admin_wrong, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Saisie d'un nouveau mot de passe (deux fois) ; [onSet] reçoit le mot de passe validé. */
    fun askNewPassword(activity: AppCompatActivity, onSet: (String) -> Unit) {
        val form = DialogPasswordBinding.inflate(activity.layoutInflater)
        form.confirmLayout.isVisible = true
        AlertDialog.Builder(activity)
            .setTitle(R.string.admin_set_title)
            .setMessage(activity.getString(R.string.admin_set_message, AdminPassword.MIN_LENGTH))
            .setView(form.root)
            .setPositiveButton(R.string.settings_save) { _, _ ->
                val password = form.passwordInput.text.toString()
                when {
                    password.length < AdminPassword.MIN_LENGTH ->
                        Toast.makeText(activity, activity.getString(R.string.admin_too_short, AdminPassword.MIN_LENGTH), Toast.LENGTH_LONG).show()
                    password != form.confirmInput.text.toString() ->
                        Toast.makeText(activity, R.string.admin_mismatch, Toast.LENGTH_LONG).show()
                    else -> onSet(password)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
