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
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.dati.app.databinding.ActivityMainBinding
import fr.dati.core.DatiState
import fr.dati.core.DeploymentConfig
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

        binding.startButton.setOnClickListener { requestStart() }
        binding.stopButton.setOnClickListener { confirmStop() }
        binding.okButton.setOnClickListener { DatiService.send(this, DatiService.ACTION_OK) }
        binding.sosButton.setOnClickListener { Toast.makeText(this, R.string.sos_hint, Toast.LENGTH_SHORT).show() }
        binding.sosButton.setOnLongClickListener {
            triggerSos()
            true
        }
        binding.testButton.setOnClickListener { confirmTest() }
        binding.mapButton.setOnClickListener { startActivity(Intent(this, MapActivity::class.java)) }
        binding.checksText.setOnClickListener { showChecks() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { DatiStatus.state.collect { render(it) } }
                launch { DatiStatus.ackedBy.collect { render(DatiStatus.state.value) } }
            }
        }
        handleConfigLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleConfigLink(intent)
    }

    override fun onResume() {
        super.onResume()
        if (ManagedConfig.apply(this, settings) && DatiStatus.state.value != DatiState.Idle) {
            DatiService.send(this, DatiService.ACTION_RELOAD)
        }
        renderChecks()
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_settings).title =
            getString(if (AdminGate.isProtected(this)) R.string.settings_locked else R.string.settings)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_settings -> {
            openSettings()
            true
        }
        R.id.menu_journal -> {
            startActivity(Intent(this, JournalActivity::class.java))
            true
        }
        R.id.menu_about -> {
            showAbout()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openSettings() {
        AdminGate.require(this) { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun showAbout() {
        val info = packageManager.getPackageInfo(packageName, 0)
        val version = getString(R.string.version, info.versionName, PackageInfoCompat.getLongVersionCode(info))
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setIcon(R.drawable.brand_logo)
            .setMessage("$version\n\n${getString(R.string.disclaimer)}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Lien `dati://config?…` ouvert depuis le QR code d'enrôlement. */
    private fun handleConfigLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "dati" || uri.host != "config") return
        intent.data = null
        val config = try {
            DeploymentConfig.fromQuery(uri.encodedQuery.orEmpty())
        } catch (e: IllegalArgumentException) {
            AlertDialog.Builder(this)
                .setTitle(R.string.config_invalid_title)
                .setMessage(e.message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        if (config.isEmpty) return

        val summary = buildList {
            config.userName?.let { add(getString(R.string.config_summary_name, it)) }
            config.serverUrl?.let { add(getString(R.string.config_summary_server, Uri.parse(it).host ?: it)) }
            config.contacts?.let { add(getString(R.string.config_summary_contacts, it.joinToString(", "))) }
            config.checkInMinutes?.let { add(getString(R.string.config_summary_check_in, it)) }
            config.immobilitySeconds?.let { add(getString(R.string.config_summary_immobility, it)) }
            config.verticalitySeconds?.let { add(getString(R.string.config_summary_verticality, it)) }
            config.batteryThresholdPercent?.let { add(getString(R.string.config_summary_battery, it)) }
            config.adminPassword?.let { add(getString(R.string.config_summary_admin)) }
        }.joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle(R.string.config_confirm_title)
            .setMessage(getString(R.string.config_confirm_message, summary))
            .setPositiveButton(R.string.config_apply) { _, _ ->
                // Téléphone verrouillé : seul l'administrateur peut appliquer une nouvelle configuration.
                AdminGate.require(this) {
                    settings.apply(config)
                    EventLog(this).add("Configuration d'entreprise appliquée")
                    if (DatiStatus.state.value != DatiState.Idle) DatiService.send(this, DatiService.ACTION_RELOAD)
                    Toast.makeText(this, R.string.config_applied, Toast.LENGTH_SHORT).show()
                    render(DatiStatus.state.value)
                    renderChecks()
                    invalidateOptionsMenu()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        binding.statusCard.setCardBackgroundColor(ContextCompat.getColor(this, colorRes))
        binding.detailText.text = when (state) {
            DatiState.Idle -> getString(R.string.detail_idle)
            is DatiState.Monitoring -> state.nextCheckInAtMs
                ?.let { getString(R.string.detail_next_check_in, formatDuration(it - now)) }
                ?: getString(R.string.detail_no_check_in)
            is DatiState.PreAlarm ->
                getString(R.string.detail_prealarm, causeLabel(state.reason), formatDuration(state.deadlineMs - now))
            is DatiState.Alarm -> DatiStatus.ackedBy.value?.let { getString(R.string.acked_by, it) }
                ?: getString(R.string.alarm_sent, state.reason.label(this))
        }

        val zone = DatiStatus.zonePresence?.takeIf { state != DatiState.Idle }
        binding.zoneText.isVisible = zone != null
        if (zone != null) {
            binding.zoneText.text = getString(
                R.string.zone_status,
                zone.name,
                formatDuration(now - zone.sinceMs),
                formatDuration(zone.maxDwellMs),
            )
        }

        // Seules les actions utiles dans l'état courant sont affichées.
        val active = state != DatiState.Idle
        binding.startButton.isVisible = !active
        binding.activeActions.isVisible = active
        binding.testButton.isEnabled = state is DatiState.Monitoring
    }

    private fun causeLabel(reason: fr.dati.core.AlarmReason) =
        DatiStatus.alarmDetail?.let { "${reason.label(this)} ($it)" } ?: reason.label(this)

    /** Vérifications résumées en une ligne ; le détail s'affiche au toucher. */
    private fun renderChecks() {
        val issues = SelfCheck.run(this, settings)
        val errors = issues.count { it.severity == SelfCheck.Severity.ERROR }
        binding.checksText.text = when {
            issues.isEmpty() -> getString(R.string.check_all_good)
            errors > 0 -> resources.getQuantityString(R.plurals.checks_errors, issues.size, issues.size)
            else -> resources.getQuantityString(R.plurals.checks_warnings, issues.size, issues.size)
        }
        binding.checksText.setTextColor(
            ContextCompat.getColor(this, if (errors > 0) R.color.dati_red else if (issues.isEmpty()) R.color.dati_green else R.color.dati_orange)
        )
    }

    private fun showChecks() {
        val issues = SelfCheck.run(this, settings)
        val message = if (issues.isEmpty()) {
            getString(R.string.check_all_good)
        } else {
            issues.joinToString("\n\n") { issue ->
                (if (issue.severity == SelfCheck.Severity.ERROR) "⛔ " else "⚠️ ") + issue.message
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.checks_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun requestStart() {
        if (settings.contacts.isEmpty() && !settings.serverConfigured) {
            Toast.makeText(this, R.string.error_no_contacts, Toast.LENGTH_LONG).show()
            openSettings()
            return
        }
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (settings.contacts.isNotEmpty()) add(Manifest.permission.SEND_SMS)
            if (settings.callFirstContact) add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            // Balises Bluetooth des zones à risque intérieures, transmises par le serveur.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.serverConfigured) {
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
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
        val sms = settings.contacts.isEmpty() || isGranted(Manifest.permission.SEND_SMS)
        if (!location || !notifications || !sms) {
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
        renderChecks()
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

    private fun confirmTest() {
        AlertDialog.Builder(this)
            .setTitle(R.string.test_title)
            .setMessage(R.string.test_message)
            .setPositiveButton(R.string.test_start) { _, _ -> DatiService.send(this, DatiService.ACTION_TEST) }
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
