package dev.pranav.reef

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.pranav.reef.services.KeepAliveService
import dev.pranav.reef.util.prefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class KeepAliveServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        }
    }

    @Test
    fun `service extends Service`() {
        assertTrue(Service::class.java.isAssignableFrom(KeepAliveService::class.java))
    }

    @Test
    fun `onCreate creates notification channel`() {
        Robolectric.buildService(KeepAliveService::class.java).create().get()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel("keep_alive_service")
        assertNotNull("Notification channel should be created", channel)
        assertEquals("keep_alive_service", channel?.id)
    }

    @Test
    fun `onCreate starts foreground notification`() {
        val controller = Robolectric.buildService(KeepAliveService::class.java)
        controller.create()

        val shadowService = Shadows.shadowOf(controller.get())
        assertTrue("Service should have called startForeground", shadowService.lastForegroundNotification != null)
    }

    @Test
    fun `companion start creates intent to start service`() {
        KeepAliveService.start(context)

        val shadowApp = Shadows.shadowOf(context.applicationContext)
        val intents = shadowApp.foregroundServiceIntents
        val match = intents.any { it.component?.className == KeepAliveService::class.java.name }
        assertTrue("startForegroundService intent for KeepAliveService", match)
    }

    @Test
    fun `stop calls stopService without exception`() {
        val controller = Robolectric.buildService(KeepAliveService::class.java)
        controller.create().startCommand(0, 0)

        KeepAliveService.stop(context)

        val shadowService = Shadows.shadowOf(controller.get())
        assertNotNull("Service should stop without exception", shadowService)
    }

    @Test
    fun `onTaskRemoved schedules alarm for restart`() {
        val service = Robolectric.buildService(KeepAliveService::class.java).create().get()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)

        service.onTaskRemoved(null)

        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertTrue("Alarm should be scheduled for restart", scheduledAlarms.isNotEmpty())
    }

    @Test
    fun `onTaskRemoved alarm uses RTC_WAKEUP type`() {
        val service = Robolectric.buildService(KeepAliveService::class.java).create().get()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)

        service.onTaskRemoved(null)

        val alarm = shadowAlarmManager.scheduledAlarms.first()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
    }

    @Test
    fun `companion notification constants are correct`() {
        val channelId = getPrivateConst<String>("CHANNEL_ID")
        assertEquals("keep_alive_service", channelId)

        val alertChannelId = getPrivateConst<String>("ALERT_CHANNEL_ID")
        assertEquals("reef_alerts", alertChannelId)

        val notificationId = getPrivateConst<Int>("NOTIFICATION_ID")
        assertEquals(9001, notificationId)

        val alertNotifId = getPrivateConst<Int>("ALERT_NOTIFICATION_ID")
        assertEquals(10001, alertNotifId)
    }

    private inline fun <reified T> getPrivateConst(name: String): T {
        val field = KeepAliveService::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(null) as T
    }
}
