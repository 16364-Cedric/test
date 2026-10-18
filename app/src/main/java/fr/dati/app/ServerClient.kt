package fr.dati.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Client du serveur de supervision. Les requêtes partent sur un fil dédié ; la réponse
 * (ou `null` en cas d'échec) est rendue sur le fil principal.
 */
class ServerClient(private val settings: DatiSettings) {
    /** Réponse du serveur : [ackedBy] est le superviseur qui a pris en charge l'alarme en cours. */
    data class Reply(val ackedBy: String?)

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun heartbeat(payload: JSONObject, onReply: (Reply?) -> Unit) = post("/api/v1/heartbeat", payload, onReply)

    fun event(type: String, payload: JSONObject, onReply: (Reply?) -> Unit) =
        post("/api/v1/events", payload.put("type", type), onReply)

    fun shutdown() {
        executor.shutdown()
    }

    private fun post(path: String, payload: JSONObject, onReply: (Reply?) -> Unit) {
        if (!settings.serverConfigured || executor.isShutdown) return
        val url = settings.serverUrl + path
        val token = settings.deviceToken
        val body = payload.toString()
        executor.execute {
            val reply = try {
                send(url, token, body)
            } catch (e: Exception) {
                Log.w(TAG, "Serveur injoignable ($path)", e)
                null
            }
            mainHandler.post { onReply(reply) }
        }
    }

    private fun send(url: String, token: String, body: String): Reply? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.use { it.write(body.toByteArray()) }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Réponse HTTP ${connection.responseCode} de $url")
                return null
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val ack = json.optJSONObject("ack")
            return Reply(ackedBy = ack?.optString("by")?.takeIf { it.isNotBlank() })
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "ServerClient"
        const val TIMEOUT_MS = 10_000
    }
}
