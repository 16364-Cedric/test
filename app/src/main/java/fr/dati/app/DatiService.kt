package fr.dati.app

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
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
import fr.dati.core.PressSequenceDetector
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Service au premier plan qui surveille l'utilisateur en continu, même écran éteint :
 * accéléromètre et gyroscope (chute, immobilité), suivi GPS, échéances de confirmation,
 * bouton marche/arrêt (SOS), batterie, liaison avec le serveur de supervision, alertes.
 */
class DatiService : Service(), SensorEventListener {
    private lateinit var settings: DatiSettings
    private lateinit var machine: DatiStateMachine
    private lateinit var signal: AlarmSignal
    private lateinit var log: EventLog
    private lateinit var alertSender: AlertSender
    private lateinit var server: ServerClient
    private lateinit var sensorManager: SensorManager

    private val handler = Handler(Looper.getMainLooper())
    private var fallDetector = FallDetector()
    private var fallDetectionEnabled = true
    private var immobilityDetector: ImmobilityDetector? = null
    private val powerButton = PressSequenceDetector(requiredPresses = 4, windowMs = 3_000)
    private var powerButtonSosEnabled = true
    private var wakeLock: PowerManager.WakeLock? = null
    private var receiversRegistered = false

    private var batteryPercent: Int? = null
    private var charging: Boolean? = null
    private var lowBatteryWarned = false
    private var lastReminderAt = 0L

    // Maintient une position récente pour les SMS d'alerte (lue via getLastKnownLocation).
    private val locationTracker = locationListener { }

    private val ticker = object : Runnable {
        override fun run() {
            val now = now()
            machine.tick(now)
            val state = machine.state
            if (state is DatiState.Alarm && state.reason != AlarmReason.TEST && now - lastReminderAt >= REMINDER_INTERVAL_MS) {
                lastReminderAt = now
                alertSender.sendReminder()
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            server.heartbeat(statePayload(), ::onServerReply)
            scheduleHeartbeat()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val percent = level * 100 / scale
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            batteryPercent = percent
            charging = isCharging
            if (isCharging || percent > LOW_BATTERY_PERCENT + 5) {
                lowBatteryWarned = false
            } else if (percent <= LOW_BATTERY_PERCENT && !lowBatteryWarned) {
                lowBatteryWarned = true
                onLowBattery(percent)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Pendant un appel, le capteur de proximité allume et éteint l'écran : ce ne sont pas des appuis.
            val mode = getSystemService(AudioManager::class.java).mode
            if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) return
            if (powerButtonSosEnabled && powerButton.onPress(now())) {
                log.add("SOS déclenché par le bouton marche/arrêt")
                machine.onDetection(AlarmReason.SOS, now())
            }
        }
    }

    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            log.add("Extinction du téléphone pendant la surveillance")
            alertSender.sendShutdown()
            server.event("shutdown", statePayload()) { }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = DatiSettings(this)
        log = EventLog(this)
        machine = DatiStateMachine(settings.toConfig(), ::onStateChanged)
        signal = AlarmSignal(this)
        alertSender = AlertSender(this, settings, log)
        server = ServerClient(settings)
        sensorManager = getSystemService(SensorManager::class.java)
        Notifications.createChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            // Service relancé par le système après avoir été tué.
            null -> if (settings.monitoringActive) {
                log.add("Surveillance relancée par le système")
                startMonitoring()
            }
            ACTION_STOP -> stopMonitoring()
            ACTION_OK -> machine.confirmOk(now())
            ACTION_SOS -> machine.onDetection(AlarmReason.SOS, now())
            ACTION_TEST -> machine.onDetection(AlarmReason.TEST, now())
            ACTION_SILENCE -> signal.stop()
            ACTION_RELOAD -> reloadSettings()
        }
        if (machine.state == DatiState.Idle) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        releaseResources()
        machine.stop()
        server.shutdown()
        super.onDestroy()
    }

    private fun startMonitoring() {
        if (machine.state != DatiState.Idle) return
        ManagedConfig.apply(this, settings)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, Notifications.MONITORING_ID, Notifications.monitoring(this, DatiState.Idle), type)
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de démarrer la surveillance", e)
            log.add("Impossible de démarrer la surveillance : ${e.message}")
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
        registerReceivers()
        machine.updateConfig(settings.toConfig(), now())
        machine.start(now())
        handler.post(ticker)
        scheduleHeartbeat()
    }

    private fun stopMonitoring() {
        settings.monitoringActive = false
        releaseResources()
        machine.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun releaseResources() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(heartbeat)
        sensorManager.unregisterListener(this)
        getSystemService(LocationManager::class.java).removeUpdates(locationTracker)
        if (receiversRegistered) {
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(screenReceiver)
            unregisterReceiver(shutdownReceiver)
            receiversRegistered = false
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        alertSender.cancel()
        signal.stop()
    }

    private fun registerReceivers() {
        if (receiversRegistered) return
        val flags = ContextCompat.RECEIVER_NOT_EXPORTED
        ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), flags)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            flags,
        )
        ContextCompat.registerReceiver(this, shutdownReceiver, IntentFilter(Intent.ACTION_SHUTDOWN), flags)
        receiversRegistered = true
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
        log.add("Réglages rechargés")
    }

    private fun configureDetectors() {
        fallDetectionEnabled = settings.fallDetection
        fallDetector = FallDetector.withSensitivity(settings.fallSensitivity)
        immobilityDetector = settings.immobilitySeconds.takeIf { it > 0 }?.let { ImmobilityDetector(it * 1_000L) }
        powerButtonSosEnabled = settings.powerButtonSos
    }

    private fun onStateChanged(old: DatiState, new: DatiState) {
        DatiStatus.publish(new)
        log.add(describe(new))
        when (new) {
            DatiState.Idle -> {
                signal.stop()
                Notifications.cancelAlarm(this)
                DatiStatus.publishAck(null)
            }
            is DatiState.Monitoring -> {
                signal.stop()
                Notifications.cancelAlarm(this)
                Notifications.updateMonitoring(this, new)
                fallDetector.reset()
                immobilityDetector?.reset()
                DatiStatus.publishAck(null)
                if (old is DatiState.Alarm) alertSender.sendAllClear(wasTest = old.reason == AlarmReason.TEST)
            }
            is DatiState.PreAlarm -> {
                signal.start()
                Notifications.showAlarm(this, new)
            }
            is DatiState.Alarm -> {
                signal.start()
                Notifications.showAlarm(this, new)
                lastReminderAt = now()
                if (new.reason == AlarmReason.TEST) settings.lastTestAt = System.currentTimeMillis()
                alertSender.sendAlert(new.reason)
            }
        }

        val event = when {
            new == DatiState.Idle -> "stop"
            new is DatiState.Monitoring && old == DatiState.Idle -> "start"
            new is DatiState.Monitoring && (old is DatiState.PreAlarm || old is DatiState.Alarm) -> "ok"
            new is DatiState.PreAlarm -> "prealarm"
            new is DatiState.Alarm -> "alarm"
            else -> null
        }
        event?.let { server.event(it, statePayload(), ::onServerReply) }
        if (new != DatiState.Idle) scheduleHeartbeat()
    }

    private fun describe(state: DatiState) = when (state) {
        DatiState.Idle -> "Surveillance arrêtée"
        is DatiState.Monitoring -> "Surveillance active"
        is DatiState.PreAlarm -> "Pré-alarme : ${state.reason.label(this)}"
        is DatiState.Alarm -> "ALARME : ${state.reason.label(this)}"
    }

    private fun scheduleHeartbeat() {
        handler.removeCallbacks(heartbeat)
        if (!settings.serverConfigured) return
        val urgent = machine.state is DatiState.PreAlarm || machine.state is DatiState.Alarm
        handler.postDelayed(heartbeat, if (urgent) URGENT_HEARTBEAT_MS else HEARTBEAT_MS)
    }

    private fun onServerReply(reply: ServerClient.Reply?) {
        val reachable = reply != null
        if (DatiStatus.serverReachable != reachable) {
            log.add(if (reachable) "Serveur de supervision joignable" else "Serveur de supervision injoignable")
        }
        DatiStatus.serverReachable = reachable
        val ackedBy = reply?.ackedBy ?: return
        val state = machine.state
        if (state is DatiState.Alarm && DatiStatus.ackedBy.value != ackedBy) {
            log.add("Alarme prise en charge par $ackedBy")
            DatiStatus.publishAck(ackedBy)
            Notifications.showAlarm(this, state, ackedBy)
        }
    }

    private fun onLowBattery(percent: Int) {
        log.add("Batterie faible : $percent %")
        Notifications.showInfo(this, getString(R.string.low_battery_title), getString(R.string.low_battery_text, percent))
        alertSender.sendLowBattery(percent)
        server.event("low_battery", statePayload()) { }
    }

    /** État envoyé au serveur ; la position n'y est affichée qu'en cas d'alerte. */
    private fun statePayload(): JSONObject {
        val state = machine.state
        return JSONObject().apply {
            put(
                "state",
                when (state) {
                    DatiState.Idle -> "idle"
                    is DatiState.Monitoring -> "monitoring"
                    is DatiState.PreAlarm -> "prealarm"
                    is DatiState.Alarm -> "alarm"
                },
            )
            when (state) {
                is DatiState.PreAlarm -> put("reason", state.reason.name)
                is DatiState.Alarm -> put("reason", state.reason.name)
                else -> put("reason", JSONObject.NULL)
            }
            batteryPercent?.let { put("battery", it) }
            charging?.let { put("charging", it) }
            alertSender.lastKnownLocation()?.let {
                put(
                    "location",
                    JSONObject()
                        .put("lat", it.latitude)
                        .put("lon", it.longitude)
                        .put("accuracy", it.accuracy.toDouble())
                        .put("time", it.time),
                )
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
        const val ACTION_TEST = "fr.dati.app.action.TEST"
        const val ACTION_SILENCE = "fr.dati.app.action.SILENCE"
        const val ACTION_RELOAD = "fr.dati.app.action.RELOAD"

        private const val TAG = "DatiService"
        private const val TICK_MS = 1_000L
        private const val SAMPLING_PERIOD_US = 20_000 // 50 Hz
        private const val LOCATION_INTERVAL_MS = 30_000L
        private const val LOCATION_MIN_DISTANCE_M = 10f
        private const val HEARTBEAT_MS = 5 * 60_000L
        private const val URGENT_HEARTBEAT_MS = 30_000L
        private const val REMINDER_INTERVAL_MS = 5 * 60_000L
        private const val LOW_BATTERY_PERCENT = 15

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
