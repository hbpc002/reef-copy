package dev.pranav.reef.accessibility

import android.app.usage.UsageStatsManager
import android.content.Context
import dev.pranav.reef.services.routines.RoutineSessionManager
import dev.pranav.reef.util.*
import java.util.Calendar

object UsageTracker {

    private val systemAppCache = mutableMapOf<String, Boolean>()

    enum class BlockReason {
        NONE,
        DAILY_LIMIT,
        ROUTINE_LIMIT,
        AUTO_LOCK,
        CYCLIC_LOCK
    }

    fun shouldBlock(context: Context, packageName: String): Boolean {
        return checkBlockReason(context, packageName) != BlockReason.NONE
    }

    fun checkBlockReason(context: Context, packageName: String): BlockReason {
        if (shouldSkipPackage(context, packageName)) return BlockReason.NONE

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val limitWarningsEnabled = prefs.getBoolean("limit_warnings", true)

        // Routine limits are always enforced — whitelist does not bypass them
        RoutineSessionManager.getLimitMs(packageName)?.let { limitMs ->
            val usageMs = RoutineSessionManager.getUsageMs(context, packageName)

            android.util.Log.d(
                "UsageTracker",
                "Routine check for $packageName: usage=$usageMs, limit=$limitMs"
            )

            if (limitWarningsEnabled && usageMs >= (limitMs * 0.85) && usageMs < limitMs) {
                val timeRemaining = limitMs - usageMs
                NotificationHelper.showReminderNotification(context, packageName, timeRemaining)
            }

            if (usageMs >= limitMs) {
                android.util.Log.d("UsageTracker", "BLOCKING $packageName due to routine limit")
                return BlockReason.ROUTINE_LIMIT
            }
        }

        // Whitelist bypasses daily limits only
        if (Whitelist.isWhitelisted(packageName)) return BlockReason.NONE

        // Check daily limits
        if (AppLimits.hasLimit(packageName)) {
            val dailyUsage = getDailyUsage(packageName, usm)
            val limit = AppLimits.getLimit(packageName)

            if (limitWarningsEnabled && dailyUsage >= (limit * 0.85) && dailyUsage < limit) {
                if (!AppLimits.reminderSentToday(packageName)) {
                    val timeRemaining = limit - dailyUsage
                    NotificationHelper.showReminderNotification(context, packageName, timeRemaining)
                    AppLimits.markReminder(packageName)
                }
            }

            if (dailyUsage >= limit) {
                return BlockReason.DAILY_LIMIT
            }
        }

        return BlockReason.NONE
    }


    private fun getDailyUsage(packageName: String, usm: UsageStatsManager): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        return ScreenUsageHelper.fetchUsageInMs(
            usm,
            startOfDay,
            now,
            packageName
        )[packageName] ?: 0L
    }

    private fun shouldSkipPackage(context: Context, packageName: String): Boolean {
        return systemAppCache.getOrPut(packageName) {
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                val isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

                if (isSystem) {
                    pm.getLaunchIntentForPackage(packageName) == null
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    fun checkAutoLockReason(context: Context, packageName: String): BlockReason {
        if (shouldSkipPackage(context, packageName)) return BlockReason.NONE
        if (Whitelist.isWhitelisted(packageName)) return BlockReason.NONE

        val cyclic = AppLimits.getCyclicConfig(packageName)
        if (cyclic != null) {
            val now = System.currentTimeMillis()
            if (AppLimits.isInCyclicLockPhase(packageName)) {
                return BlockReason.CYCLIC_LOCK
            }
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val dailyUsage = getDailyUsage(packageName, usm)
            val cycleStartUsage = AppLimits.getCyclicCycleStartUsage(packageName)
            val cycleUsage = dailyUsage - cycleStartUsage
            if (cycleUsage >= cyclic.usageMinutes * 60_000L) {
                return BlockReason.DAILY_LIMIT
            }
            return BlockReason.NONE
        }

        if (!AppLimits.hasLimit(packageName)) return BlockReason.NONE

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dailyUsage = getDailyUsage(packageName, usm)
        val limit = AppLimits.getLimit(packageName)

        val lockUntilMs = AppLimits.getLockUntilMs(packageName)
        if (lockUntilMs > System.currentTimeMillis()) {
            return BlockReason.AUTO_LOCK
        }

        if (dailyUsage >= limit) {
            return BlockReason.DAILY_LIMIT
        }

        return BlockReason.NONE
    }

    fun getTodayUsage(context: Context, packageName: String): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return getDailyUsage(packageName, usm)
    }

    fun getCurrentForegroundApp(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        var mostRecent: String? = null
        var mostRecentTime = 0L

        for (stat in stats) {
            if (stat.lastTimeUsed > mostRecentTime && stat.totalTimeInForeground > 0) {
                mostRecent = stat.packageName
                mostRecentTime = stat.lastTimeUsed
            }
        }

        return mostRecent
    }
}
