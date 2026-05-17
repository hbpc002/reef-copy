package dev.pranav.reef.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.pranav.reef.R
import dev.pranav.reef.accessibility.FocusModeService
import dev.pranav.reef.services.routines.RoutineSessionManager

class ReefWorker(context: Context, params: WorkerParameters): Worker(context, params) {

    override fun doWork(): Result {
        val safeContext = applicationContext.createDeviceProtectedStorageContext()

        val prefs = safeContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val isFocusModeActive = prefs.getBoolean("focus_mode", false)

        if (!safeContext.isAccessibilityServiceEnabledForBlocker()) {
            sendAccessibilityAlert(safeContext)
            return Result.success()
        }

        if (isFocusModeActive) {
            val intent = Intent(safeContext, FocusModeService::class.java)
            safeContext.startForegroundService(intent)
        }

        if (!isPrefsInitialized) {
            dev.pranav.reef.util.prefs =
                safeContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        }
        RoutineSessionManager.evaluateAndSync(safeContext)
        NotificationHelper.syncRoutineNotification(safeContext)

        return Result.success()
    }

    private fun sendAccessibilityAlert(context: Context) {
        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        sendInstantNotification(
            context = context,
            channelId = "reef_alerts",
            channelName = "Reef Alerts",
            notificationId = ACCESSIBILITY_ALERT_ID,
            title = "Reef 无障碍服务已关闭",
            message = "系统可能已自动关闭 Reef 的无障碍权限，点击重新开启",
            action = NotificationCompat.Action.Builder(
                0, "重新开启", pendingIntent
            ).build()
        )
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun sendInstantNotification(
        context: Context,
        channelId: String,
        channelName: String,
        title: String,
        message: String,
        notificationId: Int = System.currentTimeMillis().toInt(),
        action: NotificationCompat.Action? = null
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Channel for Reef alerts"
            }
        manager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (action != null) {
            builder.addAction(action)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val ACCESSIBILITY_ALERT_ID = 10001
    }
}
