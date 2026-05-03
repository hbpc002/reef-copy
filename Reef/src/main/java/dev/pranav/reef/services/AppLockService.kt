package dev.pranav.reef.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.pranav.reef.MainActivity
import dev.pranav.reef.R
import dev.pranav.reef.accessibility.UsageTracker
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.NotificationHelper

class AppLockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            checkAppUsageAndLock()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
    }

    private fun checkAppUsageAndLock() {
        val currentApp = UsageTracker.getCurrentForegroundApp(this)
        if (currentApp == null || currentApp == packageName) return

        if (AppLimits.isWhitelisted(currentApp)) return

        val blockReason = UsageTracker.checkBlockReason(this, currentApp)
        if (blockReason != UsageTracker.BlockReason.NONE) return

        val limitMs = AppLimits.getLockDurationMs(currentApp)
        if (limitMs <= 0) return

        val todayUsage = UsageTracker.getTodayUsage(this, currentApp)
        if (todayUsage >= limitMs) {
            val lockUntilMs = AppLimits.getLockUntilMs(currentApp)
            if (lockUntilMs > System.currentTimeMillis()) {
                Log.d(TAG, "App $currentApp is locked until $lockUntilMs")
                return
            }

            val lockDurationMs = AppLimits.getLockDurationMs(currentApp)
            if (lockDurationMs > 0) {
                AppLimits.setLockUntil(currentApp, System.currentTimeMillis() + lockDurationMs)
                Log.d(TAG, "Locked $currentApp for ${lockDurationMs / 60000} minutes")
                showLockedNotification(currentApp)
            }
        }
    }

    private fun showLockedNotification(pkg: String) {
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
        } catch (_: Exception) {
            pkg
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_locked))
            .setContentText(getString(R.string.app_locked_message, appName))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(pkg.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "App Lock Service", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Monitors app usage time"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reef")
            .setContentText("Monitoring app usage")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AppLockService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "app_lock_service"
        private const val CHECK_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, AppLockService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppLockService::class.java)
            context.stopService(intent)
        }
    }
}