package fr.dati.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.dati.app.databinding.ActivityMainBinding
import fr.dati.core.DatiState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: DatiSettings

    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            render(DatiStatus.state.value)
            handler.postDelayed(this, 1_000)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPermissionsResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = DatiSettings(this)

        binding.toggleButton.setOnClickListener {
            if (DatiStatus.state.value == DatiState.Idle) requestStart() else confirmStop()
        }
        binding.okButton.setOnClickListener { DatiService.send(this, DatiService.ACTION_OK) }
        binding.sosButton.setOnClickListener { Toast.makeText(this, R.string.sos_hint, Toast.LENGTH_SHORT).show() }
        binding.sosButton.setOnLongClickListener {
            triggerSos()
            true
        }
        binding.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DatiStatus.state.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        handler.post(refresher)
    }

    override fun onStop() {
        handler.removeCallbacks(refresher)
        super.onStop()
    }

    private fun render(state: DatiState) {
        val now = DatiService.now()
        val (statusRes, colorRes) = when (state) {
            DatiState.Idle -> R.string.status_idle to R.color.dati_grey
            is DatiState.Monitoring -> R.string.status_monitoring to R.color.dati_green
            is DatiState.PreAlarm -> R.string.status_prealarm to R.color.dati_orange
            is DatiState.Alarm -> R.string.status_alarm to R.color.dati_red
        }
        binding.statusText.setText(statusRes)
        binding.statusText.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.detailText.text = when (state) {
            DatiState.Idle -> getString(R.string.detail_idle)
            is DatiState.Monitoring -> state.nextCheckInAtMs
                ?.let { getString(R.string.detail_next_check_in, formatDuration(it - now)) }
                ?: getString(R.string.detail_no_check_in)
            is DatiState.PreAlarm ->
                getString(R.string.detail_prealarm, state.reason.label(this), formatDuration(state.deadlineMs - now))
            is DatiState.Alarm -> getString(R.string.alarm_sent, state.reason.label(this))
        }

        val active = state != DatiState.Idle
        binding.toggleButton.setText(if (active) R.string.stop_monitoring else R.string.start_monitoring)
        binding.okButton.isEnabled = active
        binding.contactsWarning.isVisible = settings.contacts.isEmpty()
    }

    private fun requestStart() {
        if (settings.contacts.isEmpty()) {
            Toast.makeText(this, R.string.error_no_contacts, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.SEND_SMS)
            if (settings.callFirstContact) add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun onPermissionsResult() {
        val location = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        if (!location || !notifications || !isGranted(Manifest.permission.SEND_SMS)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permissions_title)
                .setMessage(R.string.permissions_message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        DatiService.start(this)
        promptSystemSettings()
    }

    /** Propose les réglages système dont dépend la fiabilité de l'alarme. */
    private fun promptSystemSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.full_screen_title)
                .setMessage(R.string.full_screen_message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
    }

    private fun confirmStop() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_stop)
            .setPositiveButton(R.string.stop_monitoring) { _, _ -> DatiService.send(this, DatiService.ACTION_STOP) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun triggerSos() {
        if (DatiStatus.state.value == DatiState.Idle) {
            Toast.makeText(this, R.string.sos_requires_monitoring, Toast.LENGTH_LONG).show()
        } else {
            DatiService.send(this, DatiService.ACTION_SOS)
        }
    }
}
