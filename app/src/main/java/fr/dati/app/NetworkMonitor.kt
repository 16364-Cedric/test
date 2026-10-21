package fr.dati.app

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Surveille les deux voies par lesquelles une alerte peut partir : le réseau mobile (SMS) et
 * Internet (serveur de supervision). Le réseau est considéré disponible si l'une des deux l'est.
 * Les changements sont rendus sur le fil principal.
 */
class NetworkMonitor(context: Context, private val onChange: (available: Boolean) -> Unit) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val telephony = appContext.getSystemService(TelephonyManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hasTelephony = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private var cellularInService = false
    private var internet = false
    private var telephonyListener: Any? = null
    private var started = false

    val isAvailable: Boolean get() = cellularInService || internet

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            internet = capabilities.isValidatedInternet()
            onChange(isAvailable)
        }

        override fun onLost(network: Network) {
            internet = false
            onChange(isAvailable)
        }
    }

    fun start() {
        if (started) return
        started = true
        internet = connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.isValidatedInternet() == true
        connectivity.registerDefaultNetworkCallback(networkCallback, mainHandler)
        if (hasTelephony) registerTelephony()
        onChange(isAvailable)
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        val listener = telephonyListener
        // Test de version d'abord : TelephonyCallback n'existe pas avant Android 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && listener is TelephonyCallback) {
            telephony.unregisterTelephonyCallback(listener)
        } else if (listener is PhoneStateListener) {
            @Suppress("DEPRECATION")
            telephony.listen(listener, PhoneStateListener.LISTEN_NONE)
        }
        telephonyListener = null
    }

    private fun onServiceState(state: ServiceState) {
        cellularInService = state.state == ServiceState.STATE_IN_SERVICE
        onChange(isAvailable)
    }

    private fun registerTelephony() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                    override fun onServiceStateChanged(serviceState: ServiceState) = onServiceState(serviceState)
                }
                telephony.registerTelephonyCallback(appContext.mainExecutor, callback)
                telephonyListener = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onServiceStateChanged(serviceState: ServiceState) = onServiceState(serviceState)
                }
                @Suppress("DEPRECATION")
                telephony.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE)
                telephonyListener = listener
            }
        } catch (e: SecurityException) {
            // Sans accès à l'état du réseau mobile, on le suppose présent : mieux vaut ne rien
            // signaler que de fausses coupures. La perte d'Internet reste détectée.
            Log.w(TAG, "État du réseau mobile inaccessible", e)
            cellularInService = true
        }
    }

    private fun NetworkCapabilities.isValidatedInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private companion object {
        const val TAG = "NetworkMonitor"
    }
}
