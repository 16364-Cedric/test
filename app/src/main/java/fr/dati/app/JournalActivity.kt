package fr.dati.app

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fr.dati.app.databinding.ActivityJournalBinding

class JournalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityJournalBinding
    private lateinit var log: EventLog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJournalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        log = EventLog(this)
        binding.clearButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.journal_clear_confirm)
                .setPositiveButton(R.string.journal_clear) { _, _ ->
                    log.clear()
                    render()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun render() {
        val entries = log.read()
        binding.journalText.text =
            if (entries.isEmpty()) getString(R.string.journal_empty) else entries.asReversed().joinToString("\n")
    }
}
