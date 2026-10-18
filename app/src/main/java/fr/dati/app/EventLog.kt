package fr.dati.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Journal local des événements (démarrages, alertes, SMS, serveur), consultable dans l'application. */
class EventLog(context: Context) {
    private val file = File(context.applicationContext.filesDir, "journal.txt")

    @Synchronized
    fun add(message: String) {
        val line = "${SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date())}  $message"
        file.writeText((read() + line).takeLast(MAX_LINES).joinToString("\n"))
    }

    /** Les entrées, de la plus ancienne à la plus récente. */
    @Synchronized
    fun read(): List<String> = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    @Synchronized
    fun clear() {
        file.delete()
    }

    private companion object {
        const val MAX_LINES = 300
    }
}
