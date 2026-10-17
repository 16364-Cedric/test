package fr.dati.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
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
class AlertSender(private val context: Context, private val settings: DatiSettings) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var pendingListener: LocationListener? = null

    /**
     * Envoie immédiatement l'alerte avec la dernière position connue, puis un second SMS
     * dès qu'une position fraîche est obtenue.
     */
    fun sendAlert(reason: AlarmReason) {
        val last = lastKnownLocation()
        val where = last?.let(::describe) ?: context.getString(R.string.sms_position_unknown)
        val time = DateFormat.getTimeFormat(context).format(Date())
        sendSms(context.getString(R.string.sms_alert, name(), reason.label(context), time, where))

        requestFreshLocation { fresh ->
            if (last == null || fresh.time > last.time) {
                sendSms(context.getString(R.string.sms_position_update, name(), describe(fresh)))
            }
        }
    }

    fun sendAllClear() {
        cancel()
        sendSms(context.getString(R.string.sms_all_clear, name()))
    }

    fun cancel() {
        pendingListener?.let { locationManager.removeUpdates(it) }
        pendingListener = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun name() = settings.displayName(context)

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
    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return locationManager.getProviders(true)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(onLocation: (Location) -> Unit) {
        if (!hasLocationPermission()) return
        cancel()
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { locationManager.isProviderEnabled(it) } ?: return

        // Toutes les méthodes sont implémentées : avant Android 11 elles n'ont pas d'implémentation par défaut.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                cancel()
                onLocation(location)
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        pendingListener = listener
        locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        handler.postDelayed({ cancel() }, FRESH_LOCATION_TIMEOUT_MS)
    }

    private fun sendSms(text: String) {
        if (!isGranted(Manifest.permission.SEND_SMS)) {
            Log.w(TAG, "Permission SMS refusée, alerte non envoyée")
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
        for (number in settings.contacts) {
            try {
                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Échec de l'envoi du SMS à $number", e)
            }
        }
    }

    private companion object {
        const val TAG = "AlertSender"
        const val FRESH_LOCATION_TIMEOUT_MS = 2 * 60_000L
    }
}
