package fr.dati.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import fr.dati.core.AlarmReason
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Prévient les contacts par SMS, avec la position GPS. */
class AlertSender(
    private val context: Context,
    private val settings: DatiSettings,
    private val log: EventLog,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var pendingListener: LocationListener? = null

    /**
     * Envoie immédiatement l'alerte avec la dernière position connue, puis un second SMS
     * dès qu'une position fraîche est obtenue.
     */
    fun sendAlert(reason: AlarmReason) {
        val last = lastKnownLocation()
        val time = DateFormat.getTimeFormat(context).format(Date())
        val template = if (reason == AlarmReason.TEST) R.string.sms_test else R.string.sms_alert
        sendSms(context.getString(template, name(), reason.label(context), time, where(last)))

        requestFreshLocation { fresh ->
            if (last == null || fresh.time > last.time) {
                sendSms(context.getString(R.string.sms_position_update, name(), describe(fresh)))
            }
        }
    }

    /** Rappel envoyé régulièrement tant que l'alarme n'est pas arrêtée. */
    fun sendReminder() {
        sendSms(context.getString(R.string.sms_reminder, name(), where(lastKnownLocation())))
    }

    fun sendAllClear(wasTest: Boolean) {
        cancel()
        sendSms(context.getString(if (wasTest) R.string.sms_test_end else R.string.sms_all_clear, name()))
    }

    fun sendLowBattery(percent: Int) {
        sendSms(context.getString(R.string.sms_low_battery, name(), percent, where(lastKnownLocation())))
    }

    fun sendShutdown() {
        sendSms(context.getString(R.string.sms_shutdown, name(), where(lastKnownLocation())))
    }

    fun cancel() {
        pendingListener?.let { locationManager.removeUpdates(it) }
        pendingListener = null
        handler.removeCallbacksAndMessages(null)
    }

    @SuppressLint("MissingPermission")
    fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return locationManager.getProviders(true)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun name() = settings.displayName(context)

    private fun where(location: Location?) = location?.let(::describe) ?: context.getString(R.string.sms_position_unknown)

    private fun describe(location: Location): String {
        val coordinates = String.format(Locale.US, "%.6f,%.6f", location.latitude, location.longitude)
        val ageMinutes = ((System.currentTimeMillis() - location.time) / 60_000).coerceAtLeast(0)
        return context.getString(R.string.sms_position, coordinates, location.accuracy.roundToInt(), ageMinutes)
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission() =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(onLocation: (Location) -> Unit) {
        if (!hasLocationPermission()) return
        cancel()
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { locationManager.isProviderEnabled(it) } ?: return

        val listener = locationListener { location ->
            cancel()
            onLocation(location)
        }
        pendingListener = listener
        locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        handler.postDelayed({ cancel() }, FRESH_LOCATION_TIMEOUT_MS)
    }

    private fun sendSms(text: String) {
        val contacts = settings.contacts
        if (contacts.isEmpty()) return
        if (!isGranted(Manifest.permission.SEND_SMS)) {
            log.add("SMS non envoyé : autorisation refusée")
            return
        }
        val smsManager: SmsManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        val parts = smsManager.divideMessage(text)
        var sent = 0
        for (number in contacts) {
            try {
                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                sent++
            } catch (e: Exception) {
                Log.e(TAG, "Échec de l'envoi du SMS à $number", e)
                log.add("Échec du SMS vers $number : ${e.message}")
            }
        }
        log.add("SMS envoyé à $sent/${contacts.size} contact(s) : ${text.take(60)}…")
    }

    private companion object {
        const val TAG = "AlertSender"
        const val FRESH_LOCATION_TIMEOUT_MS = 2 * 60_000L
    }
}
