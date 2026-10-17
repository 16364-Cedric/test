package fr.dati.app

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fr.dati.core.AlarmReason
import fr.dati.core.DatiState
import fr.dati.core.DatiStateMachine
import fr.dati.core.FallDetector
import fr.dati.core.ImmobilityDetector
import kotlin.math.sqrt

/**
 * Service au premier plan qui surveille l'utilisateur en continu, même écran éteint :
 * accéléromètre et gyroscope (chute, immobilité), suivi GPS, échéances de confirmation,
 * déclenchement des alertes.
 */
class DatiService : Service(), SensorEventListener {
    private lateinit var settings: DatiSettings
    private lateinit var machine: DatiStateMachine
    private lateinit var signal: AlarmSignal
    private lateinit var alertSender: AlertSender
    private lateinit var sensorManager: SensorManager

    private val handler = Handler(Looper.getMainLooper())
    private val fallDetector = FallDetector()
    private var fallDetectionEnabled = true
    private var immobilityDetector: ImmobilityDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Maintient une position récente pour les SMS d'alerte (lue via getLastKnownLocation).
    private val locationTracker = locationListener { }

    private val ticker = object : Runnable {
        override fun run() {
            machine.tick(now())
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = DatiSettings(this)
        machine = DatiStateMachine(settings.toConfig(), ::onStateChanged)
        signal = AlarmSignal(this)
        alertSender = AlertSender(this, settings)
        sensorManager = getSystemService(SensorManager::class.java)
        Notifications.createChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            // Service relancé par le système après avoir été tué.
            null -> if (settings.monitoringActive) startMonitoring()
            ACTION_STOP -> stopMonitoring()
            ACTION_OK -> machine.confirmOk(now())
            ACTION_SOS -> machine.onDetection(AlarmReason.SOS, now())
            ACTION_SILENCE -> signal.stop()
            ACTION_RELOAD -> reloadSettings()
        }
        if (machine.state == DatiState.Idle) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        releaseResources()
        machine.stop()
        super.onDestroy()
    }

    private fun startMonitoring() {
        if (machine.state != DatiState.Idle) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, Notifications.MONITORING_ID, Notifications.monitoring(this, DatiState.Idle), type)
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de démarrer la surveillance", e)
            settings.monitoringActive = false
            return
        }
        settings.monitoringActive = true

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dati:monitoring")
            .apply { acquire() }
        configureDetectors()
        for (type in listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE)) {
            sensorManager.getDefaultSensor(type)?.let { sensorManager.registerListener(this, it, SAMPLING_PERIOD_US) }
        }
        startLocationTracking()
        machine.updateConfig(settings.toConfig(), now())
        machine.start(now())
        handler.post(ticker)
    }

    private fun stopMonitoring() {
        settings.monitoringActive = false
        releaseResources()
        machine.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun releaseResources() {
        handler.removeCallbacks(ticker)
        sensorManager.unregisterListener(this)
        getSystemService(LocationManager::class.java).removeUpdates(locationTracker)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        alertSender.cancel()
        signal.stop()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        val locationManager = getSystemService(LocationManager::class.java)
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider, LOCATION_INTERVAL_MS, LOCATION_MIN_DISTANCE_M, locationTracker, Looper.getMainLooper(),
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Localisation non autorisée", e)
            }
        }
    }

    private fun reloadSettings() {
        configureDetectors()
        machine.updateConfig(settings.toConfig(), now())
    }

    private fun configureDetectors() {
        fallDetectionEnabled = settings.fallDetection
        fallDetector.reset()
        immobilityDetector = settings.immobilitySeconds.takeIf { it > 0 }?.let { ImmobilityDetector(it * 1_000L) }
    }

    private fun onStateChanged(old: DatiState, new: DatiState) {
        DatiStatus.publish(new)
        when (new) {
            DatiState.Idle -> {
                signal.stop()
                Notifications.cancelAlarm(this)
            }
            is DatiState.Monitoring -> {
                signal.stop()
                Notifications.cancelAlarm(this)
                Notifications.updateMonitoring(this, new)
                fallDetector.reset()
                immobilityDetector?.reset()
                if (old is DatiState.Alarm) alertSender.sendAllClear()
            }
            is DatiState.PreAlarm -> {
                signal.start()
                Notifications.showAlarm(this, new)
            }
            is DatiState.Alarm -> {
                signal.start()
                Notifications.showAlarm(this, new)
                alertSender.sendAlert(new.reason)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (machine.state !is DatiState.Monitoring) return
        val (x, y, z) = event.values
        val timestampMs = event.timestamp / 1_000_000
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val g = SensorManager.GRAVITY_EARTH
                val magnitudeG = sqrt(x * x + y * y + z * z) / g
                when {
                    fallDetectionEnabled && fallDetector.onSample(timestampMs, magnitudeG) ->
                        machine.onDetection(AlarmReason.FALL, now())
                    immobilityDetector?.onAcceleration(timestampMs, x / g, y / g, z / g) == true ->
                        machine.onDetection(AlarmReason.IMMOBILITY, now())
                }
            }
            Sensor.TYPE_GYROSCOPE ->
                if (immobilityDetector?.onRotation(timestampMs, x, y, z) == true) {
                    machine.onDetection(AlarmReason.IMMOBILITY, now())
                }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val ACTION_START = "fr.dati.app.action.START"
        const val ACTION_STOP = "fr.dati.app.action.STOP"
        const val ACTION_OK = "fr.dati.app.action.OK"
        const val ACTION_SOS = "fr.dati.app.action.SOS"
        const val ACTION_SILENCE = "fr.dati.app.action.SILENCE"
        const val ACTION_RELOAD = "fr.dati.app.action.RELOAD"

        private const val TAG = "DatiService"
        private const val TICK_MS = 1_000L
        private const val SAMPLING_PERIOD_US = 20_000 // 50 Hz
        private const val LOCATION_INTERVAL_MS = 30_000L
        private const val LOCATION_MIN_DISTANCE_M = 10f

        /** Horloge monotone utilisée par la machine à états (insensible aux changements d'heure). */
        fun now(): Long = SystemClock.elapsedRealtime()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DatiService::class.java).setAction(ACTION_START))
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, DatiService::class.java).setAction(action))
        }
    }
}
