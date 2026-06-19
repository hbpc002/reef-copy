package dev.pranav.reef

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.pranav.reef.accessibility.UsageTracker
import dev.pranav.reef.services.AppLockService
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.CyclicConfig
import dev.pranav.reef.util.prefs
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class AppLockServiceTest {

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
        assertTrue(Service::class.java.isAssignableFrom(AppLockService::class.java))
    }

    @Test
    fun `onCreate creates notification channel`() {
        Robolectric.buildService(AppLockService::class.java).create().get()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel("app_lock_service")
        assertNotNull("Notification channel should be created", channel)
        assertEquals("app_lock_service", channel?.id)
    }

    @Test
    fun `onCreate starts foreground notification`() {
        val controller = Robolectric.buildService(AppLockService::class.java)
        controller.create()

        val shadowService = Shadows.shadowOf(controller.get())
        assertNotNull("Service should call startForeground", shadowService.lastForegroundNotification)
    }

    @Test
    fun `onDestroy cancels check job`() {
        val controller = Robolectric.buildService(AppLockService::class.java)
        controller.create().destroy()
    }

    @Test
    fun `companion start starts foreground service`() {
        AppLockService.start(context)

        val shadowApp = Shadows.shadowOf(context.applicationContext)
        val intents = shadowApp.foregroundServiceIntents
        val match = intents.any { it.component?.className == AppLockService::class.java.name }
        assertTrue("startForegroundService for AppLockService", match)
    }

    @Test
    fun `stop stops the service without exception`() {
        val controller = Robolectric.buildService(AppLockService::class.java)
        controller.create().startCommand(0, 0)

        AppLockService.stop(context)

        val shadowService = Shadows.shadowOf(controller.get())
        assertNotNull("Service should stop without exception", shadowService)
    }

    @Test
    fun `companion constants are correct`() {
        assertEquals("app_lock_service", getPrivateConst<String>("CHANNEL_ID"))
        assertEquals(1001, getPrivateConst<Int>("NOTIFICATION_ID"))
        assertEquals(30_000L, getPrivateConst<Long>("CHECK_INTERVAL_MS"))
    }

    @Test
    fun `checkCyclicLock activates lock when cycle usage exceeds limit`() {
        val pkg = "com.test.app"
        val cyclic = CyclicConfig(usageMinutes = 5, lockMinutes = 2)

        mockkObject(AppLimits)
        mockkObject(UsageTracker)
        every { UsageTracker.getTodayUsage(any(), pkg) } returns 6 * 60_000L
        every { AppLimits.isInCyclicLockPhase(pkg) } returns false
        every { AppLimits.getCyclicCycleStartUsage(pkg) } returns 0L
        every { AppLimits.getCyclicConfig(pkg) } returns cyclic
        every { AppLimits.setCyclicLockUntil(eq(pkg), any()) } just Runs

        val service = Robolectric.buildService(AppLockService::class.java).create().get()

        val checkMethod = AppLockService::class.java.getDeclaredMethod("checkCyclicLock", String::class.java, CyclicConfig::class.java)
        checkMethod.isAccessible = true
        checkMethod.invoke(service, pkg, cyclic)

        verify { AppLimits.setCyclicLockUntil(eq(pkg), any()) }
    }

    @Test
    fun `checkCyclicLock does not activate when cycle usage is under limit`() {
        val pkg = "com.test.app"
        val cyclic = CyclicConfig(usageMinutes = 10, lockMinutes = 2)

        mockkObject(AppLimits)
        mockkObject(UsageTracker)
        every { UsageTracker.getTodayUsage(any(), pkg) } returns 5 * 60_000L
        every { AppLimits.isInCyclicLockPhase(pkg) } returns false
        every { AppLimits.getCyclicCycleStartUsage(pkg) } returns 0L
        every { AppLimits.getCyclicConfig(pkg) } returns cyclic

        val service = Robolectric.buildService(AppLockService::class.java).create().get()

        val checkMethod = AppLockService::class.java.getDeclaredMethod("checkCyclicLock", String::class.java, CyclicConfig::class.java)
        checkMethod.isAccessible = true
        checkMethod.invoke(service, pkg, cyclic)

        verify(exactly = 0) { AppLimits.setCyclicLockUntil(any(), any()) }
    }

    @Test
    fun `checkCyclicLock resets cycle when lock phase expires`() {
        val pkg = "com.test.app"
        val cyclic = CyclicConfig(usageMinutes = 5, lockMinutes = 2)
        val now = System.currentTimeMillis()

        mockkObject(AppLimits)
        mockkObject(UsageTracker)
        every { AppLimits.isInCyclicLockPhase(pkg) } returns true
        every { AppLimits.getCyclicLockUntilMs(pkg) } returns now - 1000
        every { UsageTracker.getTodayUsage(any(), pkg) } returns 10 * 60_000L
        every { AppLimits.setCyclicCycleStartUsage(eq(pkg), any()) } just Runs

        val service = Robolectric.buildService(AppLockService::class.java).create().get()

        val checkMethod = AppLockService::class.java.getDeclaredMethod("checkCyclicLock", String::class.java, CyclicConfig::class.java)
        checkMethod.isAccessible = true
        checkMethod.invoke(service, pkg, cyclic)

        verify { AppLimits.setCyclicCycleStartUsage(eq(pkg), 10 * 60_000L) }
    }

    private inline fun <reified T> getPrivateConst(name: String): T {
        val field = AppLockService::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(null) as T
    }
}
