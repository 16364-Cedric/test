package fr.dati.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.dati.app.databinding.ActivityPreAlarmBinding
import fr.dati.core.AlarmReason
import fr.dati.core.DatiState
import kotlinx.coroutines.launch

/** Écran plein, affiché même téléphone verrouillé, pour annuler une pré-alarme ou arrêter une alarme. */
class PreAlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPreAlarmBinding

    private val handler = Handler(Looper.getMainLooper())
    private val countdown = object : Runnable {
        override fun run() {
            render(DatiStatus.state.value)
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityPreAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.okButton.setOnClickListener { DatiService.send(this, DatiService.ACTION_OK) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { DatiStatus.state.collect { onState(it) } }
                launch {
                    DatiStatus.ackedBy.collect { by ->
                        binding.ackText.isVisible = by != null
                        binding.ackText.text = by?.let { getString(R.string.acked_by, it) }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        handler.post(countdown)
    }

    override fun onStop() {
        handler.removeCallbacks(countdown)
        super.onStop()
    }

    private fun onState(state: DatiState) {
        when (state) {
            DatiState.Idle, is DatiState.Monitoring -> finish()
            is DatiState.PreAlarm -> render(state)
            is DatiState.Alarm -> {
                render(state)
                maybeCallFirstContact(state)
            }
        }
    }

    private fun render(state: DatiState) {
        when (state) {
            is DatiState.PreAlarm -> {
                binding.titleText.setText(R.string.prealarm_title)
                binding.reasonText.text = state.reason.label(this)
                binding.countdownText.text = formatDuration(state.deadlineMs - DatiService.now())
                binding.okButton.setText(R.string.im_ok)
            }
            is DatiState.Alarm -> {
                binding.titleText.setText(R.string.alarm_title)
                binding.reasonText.text = state.reason.label(this)
                binding.countdownText.setText(R.string.alarm_contacts_notified)
                binding.okButton.setText(R.string.stop_alarm)
            }
            DatiState.Idle, is DatiState.Monitoring -> Unit
        }
    }

    /** Passe un appel au premier contact (une seule fois par alarme) si l'option est activée. */
    private fun maybeCallFirstContact(state: DatiState.Alarm) {
        val settings = DatiSettings(this)
        val number = settings.contacts.firstOrNull() ?: return
        if (state.reason == AlarmReason.TEST) return
        if (!settings.callFirstContact || DatiStatus.lastCalledAlarmAt == state.triggeredAtMs) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return

        DatiStatus.lastCalledAlarmAt = state.triggeredAtMs
        DatiService.send(this, DatiService.ACTION_SILENCE)
        runCatching { startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))) }
    }
}
