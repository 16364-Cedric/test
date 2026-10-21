package fr.dati.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.dati.core.DatiState
import java.util.Date

object Notifications {
    const val MONITORING_ID = 1
    private const val ALARM_ID = 2
    const val BATTERY_INFO_ID = 3
    const val NETWORK_INFO_ID = 4
    private const val CHANNEL_MONITORING = "monitoring"
    private const val CHANNEL_ALARM = "alarm"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITORING,
                context.getString(R.string.channel_monitoring),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                context.getString(R.string.channel_alarm),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                // Le son et la vibration sont gérés par AlarmSignal.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun monitoring(context: Context, state: DatiState): Notification {
        val text = (state as? DatiState.Monitoring)?.nextCheckInAtMs
            ?.let { context.getString(R.string.notif_next_check_in, formatWallTime(context, it)) }
            ?: context.getString(R.string.notif_monitoring)
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_monitoring_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(activityIntent(context, MainActivity::class.java))
            .addAction(0, context.getString(R.string.action_ok), serviceIntent(context, DatiService.ACTION_OK))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateMonitoring(context: Context, state: DatiState.Monitoring) {
        notify(context, MONITORING_ID, monitoring(context, state))
    }

    /** Affiche la pré-alarme ou l'alarme en plein écran, y compris sur l'écran de verrouillage. */
    fun showAlarm(context: Context, state: DatiState, ackedBy: String? = null) {
        val fullScreen = activityIntent(context, PreAlarmActivity::class.java)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, context.getString(R.string.action_ok), serviceIntent(context, DatiService.ACTION_OK))
        when (state) {
            is DatiState.PreAlarm -> builder
                .setContentTitle(context.getString(R.string.prealarm_title))
                .setContentText(state.reason.label(context))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(toWallTime(state.deadlineMs))
            is DatiState.Alarm -> builder
                .setContentTitle(context.getString(R.string.alarm_title))
                .setContentText(
                    ackedBy?.let { context.getString(R.string.acked_by, it) }
                        ?: context.getString(R.string.alarm_sent, state.reason.label(context))
                )
            else -> return
        }
        notify(context, ALARM_ID, builder.build())
    }

    /** Avertissement ponctuel (batterie faible…), sans plein écran. */
    fun showInfo(context: Context, id: Int, title: String, text: String) {
        notify(
            context,
            id,
            NotificationCompat.Builder(context, CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(activityIntent(context, MainActivity::class.java))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelInfo(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun cancelAlarm(context: Context) {
        NotificationManagerCompat.from(context).cancel(ALARM_ID)
    }

    /** Convertit un instant de [SystemClock.elapsedRealtime] en heure murale. */
    private fun toWallTime(elapsedMs: Long) = System.currentTimeMillis() + (elapsedMs - SystemClock.elapsedRealtime())

    fun formatWallTime(context: Context, elapsedMs: Long): String =
        DateFormat.getTimeFormat(context).format(Date(toWallTime(elapsedMs)))

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun serviceIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, DatiService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun activityIntent(context: Context, activity: Class<*>): PendingIntent = PendingIntent.getActivity(
        context,
        activity.name.hashCode(),
        Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
