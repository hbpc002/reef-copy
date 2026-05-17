package dev.pranav.reef.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import androidx.core.content.edit
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

private const val PREF_LIMITS = "app_limits"
private const val PREF_LOCK = "app_lock"

data class CyclicConfig(
    val usageMinutes: Int,
    val lockMinutes: Int
)

object AppLimits {

    private lateinit var prefs: SharedPreferences
    private lateinit var lockPrefs: SharedPreferences
    private val limits = mutableMapOf<String, Long>()
    private val lockDurations = mutableMapOf<String, Long>()
    private val lockUntil = mutableMapOf<String, Long>()
    private val reminderSent = mutableMapOf<String, Long>()
    private val cyclicConfigs = mutableMapOf<String, CyclicConfig>()
    private val cyclicCycleStartUsage = mutableMapOf<String, Long>()
    private val cyclicLockUntil = mutableMapOf<String, Long>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_LIMITS, Context.MODE_PRIVATE)
        lockPrefs = context.getSharedPreferences(PREF_LOCK, Context.MODE_PRIVATE)
        limits.clear()
        lockDurations.clear()
        lockUntil.clear()
        cyclicConfigs.clear()
        cyclicCycleStartUsage.clear()
        cyclicLockUntil.clear()

        prefs.all.forEach { (k, v) ->
            if (v is Long) limits[k] = v
        }
        lockPrefs.all.forEach { (k, v) ->
            when {
                k.startsWith("lock_until_") && v is Long -> {
                    lockUntil[k.removePrefix("lock_until_")] = v
                }
                k.startsWith("cyclic_config_") && v is String -> {
                    try {
                        val json = JSONObject(v)
                        cyclicConfigs[k.removePrefix("cyclic_config_")] = CyclicConfig(
                            usageMinutes = json.getInt("usage"),
                            lockMinutes = json.getInt("lock")
                        )
                    } catch (_: Exception) { }
                }
                k.startsWith("cyclic_start_") && v is Long -> {
                    cyclicCycleStartUsage[k.removePrefix("cyclic_start_")] = v
                }
                k.startsWith("cyclic_lock_until_") && v is Long -> {
                    cyclicLockUntil[k.removePrefix("cyclic_lock_until_")] = v
                }
                v is Long -> {
                    lockDurations[k] = v
                }
            }
        }
    }

    fun setLimit(pkg: String, minutes: Int) {
        limits[pkg] = minutes * 60_000L
    }

    fun getLimit(pkg: String): Long = limits[pkg] ?: 0L

    fun hasLimit(pkg: String): Boolean = limits.containsKey(pkg)

    fun setCyclicConfig(pkg: String, config: CyclicConfig?) {
        if (config != null) {
            cyclicConfigs[pkg] = config
            lockPrefs.edit().putString("cyclic_config_$pkg", JSONObject().apply {
                put("usage", config.usageMinutes)
                put("lock", config.lockMinutes)
            }.toString()).apply()
        } else {
            cyclicConfigs.remove(pkg)
            lockPrefs.edit().remove("cyclic_config_$pkg").apply()
        }
    }

    fun getCyclicConfig(pkg: String): CyclicConfig? = cyclicConfigs[pkg]

    fun setCyclicCycleStartUsage(pkg: String, usageMs: Long) {
        cyclicCycleStartUsage[pkg] = usageMs
        lockPrefs.edit().putLong("cyclic_start_$pkg", usageMs).apply()
    }

    fun getCyclicCycleStartUsage(pkg: String): Long = cyclicCycleStartUsage[pkg] ?: 0L

    fun setCyclicLockUntil(pkg: String, untilMs: Long) {
        cyclicLockUntil[pkg] = untilMs
        lockPrefs.edit().putLong("cyclic_lock_until_$pkg", untilMs).apply()
    }

    fun getCyclicLockUntilMs(pkg: String): Long = cyclicLockUntil[pkg] ?: 0L

    fun isInCyclicLockPhase(pkg: String): Boolean {
        return cyclicLockUntil[pkg]?.let { it > System.currentTimeMillis() } ?: false
    }

    fun removeLimit(pkg: String) {
        limits.remove(pkg)
        lockDurations.remove(pkg)
        lockUntil.remove(pkg)
        cyclicConfigs.remove(pkg)
        cyclicCycleStartUsage.remove(pkg)
        cyclicLockUntil.remove(pkg)
        removeCyclicPrefs(pkg)
    }

    fun setLockDuration(pkg: String, minutes: Int) {
        lockDurations[pkg] = minutes * 60_000L
    }

    fun getLockDurationMs(pkg: String): Long = lockDurations[pkg] ?: 0L

    fun setLockUntil(pkg: String, untilMs: Long) {
        lockUntil[pkg] = untilMs
        lockPrefs.edit().putLong("lock_until_$pkg", untilMs).apply()
    }

    fun getLockUntilMs(pkg: String): Long = lockUntil[pkg] ?: 0L

    fun clearExpiredLocks() {
        val now = System.currentTimeMillis()
        val expired = lockUntil.filter { it.value < now }.keys
        expired.forEach {
            lockUntil.remove(it)
            lockPrefs.edit().remove("lock_until_$it").apply()
        }
    }

    private fun removeCyclicPrefs(pkg: String) {
        lockPrefs.edit().remove("cyclic_config_$pkg")
            .remove("cyclic_start_$pkg")
            .remove("cyclic_lock_until_$pkg")
            .apply()
    }

    fun save() {
        check(::prefs.isInitialized)
        prefs.edit {
            clear()
            limits.forEach { putLong(it.key, it.value) }
        }
    }

    private fun startOfToday(): Long =
        LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    fun reminderSentToday(pkg: String): Boolean =
        reminderSent[pkg]?.let { it >= startOfToday() } ?: false

    fun markReminder(pkg: String) {
        reminderSent[pkg] = System.currentTimeMillis()
    }

    fun isWhitelisted(pkg: String): Boolean = Whitelist.isWhitelisted(pkg)
}


object Whitelist {
    private const val USER_EXCLUSIONS_KEY = "user_excluded_packages"
    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences("whitelist", Context.MODE_PRIVATE)

        val exclusions = getUserExclusions()

        // Whitelist all system apps by default (every init, respects exclusions)
        context.packageManager.getInstalledPackages(Process.myUserHandle().hashCode())
            .forEach { pkgInfo ->
                val pkg = pkgInfo.applicationInfo?.packageName ?: return@forEach
                if ((pkgInfo.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    && pkg !in exclusions
                ) {
                    whitelistInternal(pkg)
                }
            }

        // Whitelist all installed input methods (keyboards)
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val inputMethods = inputMethodManager.enabledInputMethodList
        inputMethods.forEach { imi ->
            if (imi.packageName !in exclusions) whitelistInternal(imi.packageName)
        }

        // Whitelist the default SMS app
        val defaultSmsPackage = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        if (defaultSmsPackage != null && defaultSmsPackage !in exclusions) {
            whitelistInternal(defaultSmsPackage)
        }

        // Whitelist the default Phone app
        val telecomManager =
            context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
        val defaultPhonePackage = telecomManager.defaultDialerPackage
        if (defaultPhonePackage != null && defaultPhonePackage !in exclusions) {
            whitelistInternal(defaultPhonePackage)
        }

        // Whitelist the default assistant app
        val intentAssist = android.content.Intent(android.content.Intent.ACTION_ASSIST).apply {
            addCategory(android.content.Intent.CATEGORY_DEFAULT)
        }
        val resolveInfoAssist = context.packageManager.resolveActivity(intentAssist, 0)
        val defaultAssistPackage = resolveInfoAssist?.activityInfo?.packageName
        if (defaultAssistPackage != null && defaultAssistPackage !in exclusions) {
            whitelistInternal(defaultAssistPackage)
        }

        // Whitelist the default launcher
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            addCategory(android.content.Intent.CATEGORY_DEFAULT)
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        val defaultLauncherPackage = resolveInfo?.activityInfo?.packageName
        if (defaultLauncherPackage != null && defaultLauncherPackage !in exclusions) {
            whitelistInternal(defaultLauncherPackage)
        }

        // Whitelist apps with SYSTEM_ALERT_WINDOW permission
        context.packageManager.getPackagesHoldingPermissions(
            arrayOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW),
            0
        ).forEach { pkg ->
            if (pkg.packageName !in exclusions) whitelistInternal(pkg.packageName)
        }

        // Whitelist all enabled accessibility services
        val enabledAccessibilityServices = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (enabledAccessibilityServices != null) {
            enabledAccessibilityServices.split(':').forEach { component ->
                val idx = component.indexOf('/')
                if (idx > 0) {
                    val accPkg = component.substring(0, idx)
                    if (accPkg !in exclusions) whitelistInternal(accPkg)
                }
            }
        }

        // Whitelist all enabled notification listener services
        val enabledNotificationListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        if (enabledNotificationListeners != null) {
            enabledNotificationListeners.split(':').forEach { component ->
                val idx = component.indexOf('/')
                if (idx > 0) {
                    val nlPkg = component.substring(0, idx)
                    if (nlPkg !in exclusions) whitelistInternal(nlPkg)
                }
            }
        }
    }

    private fun getUserExclusions(): Set<String> {
        val raw = sharedPreferences.getString(USER_EXCLUSIONS_KEY, null) ?: return emptySet()
        return try {
            org.json.JSONArray(raw).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveUserExclusions(exclusions: Set<String>) {
        val json = org.json.JSONArray(exclusions.toList()).toString()
        sharedPreferences.edit { putString(USER_EXCLUSIONS_KEY, json) }
    }

    fun isWhitelisted(packageName: String): Boolean {
        return sharedPreferences.getBoolean(packageName, false)
    }

    fun whitelist(packageName: String) {
        whitelistInternal(packageName)
        val exclusions = getUserExclusions().toMutableSet()
        if (exclusions.remove(packageName)) {
            saveUserExclusions(exclusions)
        }
    }

    private fun whitelistInternal(packageName: String) {
        sharedPreferences.edit { putBoolean(packageName, true) }
    }

    fun unwhitelist(packageName: String) {
        sharedPreferences.edit { putBoolean(packageName, false) }
        val exclusions = getUserExclusions().toMutableSet()
        exclusions.add(packageName)
        saveUserExclusions(exclusions)
    }

    fun getWhitelistedLaunchableCount(launcherApps: android.content.pm.LauncherApps): Int {
        val launchablePackages =
            launcherApps.getActivityList(null, android.os.Process.myUserHandle())
                .map { it.applicationInfo.packageName }
                .toSet()
        return sharedPreferences.all.count { (pkg, isWhitelisted) ->
            isWhitelisted == true && launchablePackages.contains(pkg)
        }
    }
}
