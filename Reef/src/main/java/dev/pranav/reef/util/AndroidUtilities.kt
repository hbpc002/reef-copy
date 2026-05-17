package dev.pranav.reef.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Vibrator
import android.os.VibratorManager
import androidx.collection.LruCache

object AndroidUtilities {
    private val appNameCache = LruCache<String, String>(100)

    fun getAppName(context: Context, packageName: String): String {
        return appNameCache.get(packageName) ?: run {
            try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                val name = context.packageManager.getApplicationLabel(appInfo).toString()
                appNameCache.put(packageName, name)
                name
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }
        }
    }

    fun vibrate(context: Context, durationMs: Long) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(durationMs)
    }
}
