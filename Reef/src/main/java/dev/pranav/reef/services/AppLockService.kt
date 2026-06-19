package dev.pranav.reef.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.pranav.reef.MainActivity
import dev.pranav.reef.R
import dev.pranav.reef.accessibility.UsageTracker
import dev.pranav.reef.util.AndroidUtilities.getAppName
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.CyclicConfig
import dev.pranav.reef.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppLockService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var checkJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted, startForeground skipped")
        }
        startPeriodicCheck()
    }

    private fun startPeriodicCheck() {
        checkJob = serviceScope.launch {
            try {
                while (true) {
                    checkAppUsageAndLock()
                    delay(CHECK_INTERVAL_MS)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // 协程被取消
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        checkJob?.cancel()
    }

    private fun checkAppUsageAndLock() {
        val currentApp = UsageTracker.getCurrentForegroundApp(this)
        if (currentApp == null || currentApp == packageName) return

        if (AppLimits.isWhitelisted(currentApp)) return

        val cyclic = AppLimits.getCyclicConfig(currentApp)
        if (cyclic != null) {
            checkCyclicLock(currentApp, cyclic)
            return
        }

        val blockReason = UsageTracker.checkBlockReason(this, currentApp)
        if (blockReason != UsageTracker.BlockReason.NONE) return

        val limitMs = AppLimits.getLimit(currentApp)
        if (limitMs <= 0) return

        val lockUntilMs = AppLimits.getLockUntilMs(currentApp)
        val now = System.currentTimeMillis()

        if (lockUntilMs > now) {
            return
        }

        val todayUsage = UsageTracker.getTodayUsage(this, currentApp)
        if (todayUsage >= limitMs) {
            val lockDurationMs = AppLimits.getLockDurationMs(currentApp)
            if (lockDurationMs > 0) {
                AppLimits.setLockUntil(currentApp, now + lockDurationMs)
                Log.d(TAG, "Locked $currentApp for ${lockDurationMs / 60000} minutes")
                showLockedNotification(currentApp)
            }
        }
    }

    private fun checkCyclicLock(pkg: String, cyclic: CyclicConfig) {
        val now = System.currentTimeMillis()

        if (AppLimits.isInCyclicLockPhase(pkg)) {
            if (AppLimits.getCyclicLockUntilMs(pkg) <= now) {
                val todayUsage = UsageTracker.getTodayUsage(this, pkg)
                AppLimits.setCyclicCycleStartUsage(pkg, todayUsage)
                Log.d(TAG, "Cyclic lock expired for $pkg, restarting use cycle")
            }
            return
        }

        val todayUsage = UsageTracker.getTodayUsage(this, pkg)
        val cycleStartUsage = AppLimits.getCyclicCycleStartUsage(pkg)
        val cycleUsage = todayUsage - cycleStartUsage
        val cycleUsageLimit = cyclic.usageMinutes * 60_000L

        if (cycleUsage >= cycleUsageLimit) {
            val lockDuration = cyclic.lockMinutes * 60_000L
            AppLimits.setCyclicLockUntil(pkg, now + lockDuration)
            Log.d(TAG, "Cyclic lock activated for $pkg: ${cyclic.lockMinutes}min lock after ${cyclic.usageMinutes}min use")
            showCyclicLockedNotification(pkg, cyclic.lockMinutes)
        }
    }

    private fun showCyclicLockedNotification(pkg: String, lockMinutes: Int) {
        val appName = getAppName(this, pkg)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_blocked))
            .setContentText(getString(R.string.app_locked_for_minutes, appName, lockMinutes))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(pkg.hashCode(), notification)
    }

    private fun showLockedNotification(pkg: String) {
        val appName = getAppName(this, pkg)

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