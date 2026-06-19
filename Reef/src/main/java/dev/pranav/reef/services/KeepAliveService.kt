package dev.pranav.reef.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import dev.pranav.reef.MainActivity
import dev.pranav.reef.R
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.util.isAccessibilityServiceEnabledForBlocker
import dev.pranav.reef.util.prefs

class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted, startForeground skipped")
        }
        recoverServices()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_RECOVER, false) == true) {
            recoverServices()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(this, KeepAliveService::class.java).apply {
            putExtra(EXTRA_RECOVER, true)
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                pendingIntent
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun recoverServices() {
        if (!isAccessibilityServiceEnabledForBlocker()) {
            showAccessibilityAlert()
            return
        }

        if (prefs.getBoolean("focus_mode", false)) {
            try {
                startForegroundService(Intent(this, FocusModeService::class.java))
            } catch (_: Exception) { }
        }

        if (prefs.getBoolean("auto_lock_enabled", false)) {
            try {
                AppLockService.start(this)
            } catch (_: Exception) { }
        }
    }

    private fun showAccessibilityAlert() {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID, "Reef 服务提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "无障碍服务状态提醒" }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Reef 无障碍服务已关闭")
            .setContentText("系统可能已自动关闭无障碍权限，点击重新开启")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(NotificationCompat.Action.Builder(0, "重新开启", pendingIntent).build())
            .build()

        try {
            nm.notify(ALERT_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) { }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Keep Alive",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps Reef running in background"
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reef")
            .setContentText("Running in background")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "keep_alive_service"
        private const val ALERT_CHANNEL_ID = "reef_alerts"
        private const val ALERT_NOTIFICATION_ID = 10001
        private const val EXTRA_RECOVER = "extra_recover"

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }
    }
}
