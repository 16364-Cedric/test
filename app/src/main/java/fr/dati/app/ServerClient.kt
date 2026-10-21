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
 *
 * Les événements qui n'ont pas pu partir (pas de réseau) sont gardés en file et renvoyés dans
 * l'ordre dès que le serveur répond de nouveau, pour que l'historique reste exact.
 */
class ServerClient(private val settings: DatiSettings) {
    /** Réponse du serveur : [ackedBy] est le superviseur qui a pris en charge l'alarme en cours. */
    data class Reply(val ackedBy: String?)

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Événements en attente (chemin, corps), manipulés uniquement sur le fil [executor]. */
    private val pending = ArrayDeque<Pair<String, String>>()

    fun heartbeat(payload: JSONObject, onReply: (Reply?) -> Unit) =
        post(PATH_HEARTBEAT, payload, queueOnFailure = false, onReply)

    fun event(type: String, payload: JSONObject, onReply: (Reply?) -> Unit) =
        post(PATH_EVENTS, payload.put("type", type), queueOnFailure = true, onReply)

    fun shutdown() {
        executor.shutdown()
    }

    private fun post(path: String, payload: JSONObject, queueOnFailure: Boolean, onReply: (Reply?) -> Unit) {
        if (!settings.serverConfigured || executor.isShutdown) return
        val baseUrl = settings.serverUrl
        val token = settings.deviceToken
        val body = payload.toString()
        executor.execute {
            val reply = if (queueOnFailure) {
                pending.addLast(path to body)
                while (pending.size > MAX_PENDING) pending.removeFirst()
                flush(baseUrl, token)
            } else {
                trySend(baseUrl + path, token, body)?.also { flush(baseUrl, token) }
            }
            mainHandler.post { onReply(reply) }
        }
    }

    /** Envoie la file dans l'ordre ; renvoie la réponse au dernier événement si tout est parti. */
    private fun flush(baseUrl: String, token: String): Reply? {
        var last: Reply? = null
        while (pending.isNotEmpty()) {
            val (path, body) = pending.first()
            last = trySend(baseUrl + path, token, body) ?: return null
            pending.removeFirst()
        }
        return last
    }

    private fun trySend(url: String, token: String, body: String): Reply? = try {
        send(url, token, body)
    } catch (e: Exception) {
        Log.w(TAG, "Serveur injoignable ($url)", e)
        null
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
        const val MAX_PENDING = 50
        const val PATH_HEARTBEAT = "/api/v1/heartbeat"
        const val PATH_EVENTS = "/api/v1/events"
    }
}
