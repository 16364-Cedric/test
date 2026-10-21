package fr.dati.app

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import fr.dati.app.databinding.ActivitySettingsBinding
import fr.dati.core.DatiState
import fr.dati.core.DeploymentConfig
import fr.dati.core.FallSensitivity
import fr.dati.core.PhoneNumbers

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: DatiSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = DatiSettings(this)
        load()
        binding.saveButton.setOnClickListener { save() }
        binding.applyLinkButton.setOnClickListener { applyLink() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        binding.nameInput.setText(settings.userName)
        binding.contactsInput.setText(settings.contacts.joinToString("\n"))
        binding.checkInInput.setText(settings.checkInMinutes.toString())
        binding.preAlarmInput.setText(settings.preAlarmSeconds.toString())
        binding.immobilityInput.setText(settings.immobilitySeconds.toString())
        binding.verticalityInput.setText(settings.verticalitySeconds.toString())
        binding.batteryInput.setText(settings.batteryThresholdPercent.toString())
        binding.fallSwitch.isChecked = settings.fallDetection
        binding.sensitivityGroup.check(
            when (settings.fallSensitivity) {
                FallSensitivity.LOW -> R.id.sensitivityLow
                FallSensitivity.NORMAL -> R.id.sensitivityNormal
                FallSensitivity.HIGH -> R.id.sensitivityHigh
            }
        )
        binding.callSwitch.isChecked = settings.callFirstContact
        binding.powerSosSwitch.isChecked = settings.powerButtonSos
        binding.serverInput.setText(settings.serverUrl)
        binding.tokenInput.setText(settings.deviceToken)
    }

    private fun save() {
        val raw = binding.contactsInput.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val invalid = raw.firstOrNull { PhoneNumbers.normalize(it) == null }
        if (invalid != null) {
            binding.contactsLayout.error = getString(R.string.error_invalid_number, invalid)
            return
        }
        binding.contactsLayout.error = null

        val server = binding.serverInput.text.toString().trim()
        if (server.isNotEmpty() && !server.startsWith("https://")) {
            binding.serverLayout.error = getString(R.string.error_server_https)
            return
        }
        binding.serverLayout.error = null

        settings.userName = binding.nameInput.text.toString()
        settings.contacts = raw.mapNotNull(PhoneNumbers::normalize)
        settings.checkInMinutes = binding.checkInInput.intOr(settings.checkInMinutes)
        settings.preAlarmSeconds = binding.preAlarmInput.intOr(settings.preAlarmSeconds)
        settings.immobilitySeconds = binding.immobilityInput.intOr(settings.immobilitySeconds)
        settings.verticalitySeconds = binding.verticalityInput.intOr(settings.verticalitySeconds)
        settings.batteryThresholdPercent = binding.batteryInput.intOr(settings.batteryThresholdPercent)
        settings.fallDetection = binding.fallSwitch.isChecked
        settings.fallSensitivity = when (binding.sensitivityGroup.checkedRadioButtonId) {
            R.id.sensitivityLow -> FallSensitivity.LOW
            R.id.sensitivityHigh -> FallSensitivity.HIGH
            else -> FallSensitivity.NORMAL
        }
        settings.callFirstContact = binding.callSwitch.isChecked
        settings.powerButtonSos = binding.powerSosSwitch.isChecked
        settings.serverUrl = server
        settings.deviceToken = binding.tokenInput.text.toString()

        reloadService()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun applyLink() {
        val config = try {
            DeploymentConfig.fromLink(binding.linkInput.text.toString())
        } catch (e: IllegalArgumentException) {
            binding.linkLayout.error = e.message
            return
        }
        binding.linkLayout.error = null
        settings.apply(config)
        reloadService()
        load()
        binding.linkInput.setText("")
        Toast.makeText(this, R.string.config_applied, Toast.LENGTH_SHORT).show()
    }

    private fun reloadService() {
        if (DatiStatus.state.value != DatiState.Idle) DatiService.send(this, DatiService.ACTION_RELOAD)
    }

    private fun EditText.intOr(default: Int) = text.toString().trim().toIntOrNull() ?: default
}
