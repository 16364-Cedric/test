package fr.dati.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.util.concurrent.TimeUnit

/** Vérifie tout ce dont dépend la fiabilité de l'alarme, pour l'afficher sur l'écran principal. */
object SelfCheck {
    enum class Severity { ERROR, WARNING }

    data class Issue(val severity: Severity, val message: String)

    private const val TEST_REMINDER_DAYS = 7

    fun run(context: Context, settings: DatiSettings): List<Issue> {
        val issues = mutableListOf<Issue>()
        fun error(res: Int, vararg args: Any) = issues.add(Issue(Severity.ERROR, context.getString(res, *args)))
        fun warning(res: Int, vararg args: Any) = issues.add(Issue(Severity.WARNING, context.getString(res, *args)))
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        if (settings.contacts.isEmpty() && !settings.serverConfigured) error(R.string.check_no_recipient)
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            error(R.string.check_location_permission)
        }
        if (settings.contacts.isNotEmpty() && !granted(Manifest.permission.SEND_SMS)) error(R.string.check_sms_permission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            error(R.string.check_notification_permission)
        }
        if (settings.callFirstContact && !granted(Manifest.permission.CALL_PHONE)) warning(R.string.check_call_permission)

        val sensors = context.getSystemService(SensorManager::class.java)
        if (sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) error(R.string.check_no_accelerometer)
        if (sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null) warning(R.string.check_no_gyroscope)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            warning(R.string.check_full_screen)
        }
        if (!context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) {
            warning(R.string.check_battery_optimization)
        }
        if (!LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))) {
            warning(R.string.check_location_disabled)
        }
        if (settings.serverConfigured && DatiStatus.serverReachable == false) warning(R.string.check_server_unreachable)

        val lastTest = settings.lastTestAt
        if (lastTest == 0L) {
            warning(R.string.check_never_tested)
        } else {
            val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastTest)
            if (days >= TEST_REMINDER_DAYS) warning(R.string.check_test_old, days)
        }
        return issues.sortedBy { it.severity }
    }
}
