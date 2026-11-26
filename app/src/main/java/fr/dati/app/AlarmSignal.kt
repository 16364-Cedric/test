package fr.dati.app

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Sonnerie et vibration locales, pour réveiller l'utilisateur et attirer l'attention des personnes proches. */
class AlarmSignal(private val context: Context) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val handler = Handler(Looper.getMainLooper())
    private val endWarning = Runnable { stop() }
    private var ringtone: Ringtone? = null
    private var alarmActive = false

    /** Alarme continue (pré-alarme, alarme), jusqu'à [stop]. */
    fun start() {
        stop()
        alarmActive = true
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
        ring(looping = true)
    }

    /**
     * Avertissement : sonnerie et vibration pendant quelques secondes (batterie faible, perte de
     * réseau). Ignoré si une alarme sonne déjà, pour ne jamais l'interrompre.
     */
    fun warn(durationMs: Long = WARNING_DURATION_MS) {
        if (alarmActive) return
        stop()
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 300, 500, 300, 500), -1))
        ring(looping = true)
        handler.postDelayed(endWarning, durationMs)
    }

    /** Courte vibration d'avertissement, sans sonnerie. */
    fun buzz() {
        if (alarmActive) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 300), -1))
    }

    fun stop() {
        handler.removeCallbacks(endWarning)
        alarmActive = false
        ringtone?.stop()
        ringtone = null
        vibrator.cancel()
    }

    private fun ring(looping: Boolean) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
            // Flux « alarme » : audible même si le téléphone est en mode silencieux ou vibreur.
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = looping
            play()
        }
    }

    private companion object {
        const val WARNING_DURATION_MS = 5_000L
    }
}
