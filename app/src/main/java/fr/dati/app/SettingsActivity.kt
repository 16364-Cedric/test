package fr.dati.app

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import fr.dati.app.databinding.ActivitySettingsBinding
import fr.dati.core.DatiState

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: DatiSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = DatiSettings(this)

        binding.nameInput.setText(settings.userName)
        binding.contactsInput.setText(settings.contacts.joinToString("\n"))
        binding.checkInInput.setText(settings.checkInMinutes.toString())
        binding.preAlarmInput.setText(settings.preAlarmSeconds.toString())
        binding.immobilityInput.setText(settings.immobilitySeconds.toString())
        binding.fallSwitch.isChecked = settings.fallDetection
        binding.callSwitch.isChecked = settings.callFirstContact
        binding.saveButton.setOnClickListener { save() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun save() {
        val contacts = binding.contactsInput.text.toString().lines()
            .map { line -> line.filterNot { it.isWhitespace() || it == '.' || it == '-' } }
            .filter { it.isNotEmpty() }
        val invalid = contacts.firstOrNull { !PHONE_NUMBER.matches(it) }
        if (invalid != null) {
            binding.contactsLayout.error = getString(R.string.error_invalid_number, invalid)
            return
        }
        binding.contactsLayout.error = null

        settings.userName = binding.nameInput.text.toString()
        settings.contacts = contacts
        settings.checkInMinutes = binding.checkInInput.intOr(settings.checkInMinutes)
        settings.preAlarmSeconds = binding.preAlarmInput.intOr(settings.preAlarmSeconds)
        settings.immobilitySeconds = binding.immobilityInput.intOr(settings.immobilitySeconds)
        settings.fallDetection = binding.fallSwitch.isChecked
        settings.callFirstContact = binding.callSwitch.isChecked

        if (DatiStatus.state.value != DatiState.Idle) DatiService.send(this, DatiService.ACTION_RELOAD)
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun EditText.intOr(default: Int) = text.toString().trim().toIntOrNull() ?: default

    private companion object {
        val PHONE_NUMBER = Regex("""^\+?[0-9]{3,15}$""")
    }
}
