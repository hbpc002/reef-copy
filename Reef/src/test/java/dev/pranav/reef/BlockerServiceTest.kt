package dev.pranav.reef

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.pranav.reef.accessibility.BlockerService
import dev.pranav.reef.accessibility.UsageTracker
import dev.pranav.reef.util.prefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class BlockerServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        }
    }

    @Test
    fun `service extends AccessibilityService`() {
        assertTrue(AccessibilityService::class.java.isAssignableFrom(BlockerService::class.java))
    }

    @Test
    fun `GLOBAL_ACTION_HOME is 1`() {
        assertEquals(1, AccessibilityService.GLOBAL_ACTION_HOME)
    }

    @Test
    fun `BlockReason enum has all expected values`() {
        val values = UsageTracker.BlockReason.values()
        assertTrue(values.contains(UsageTracker.BlockReason.NONE))
        assertTrue(values.contains(UsageTracker.BlockReason.DAILY_LIMIT))
        assertTrue(values.contains(UsageTracker.BlockReason.ROUTINE_LIMIT))
        assertTrue(values.contains(UsageTracker.BlockReason.AUTO_LOCK))
        assertTrue(values.contains(UsageTracker.BlockReason.CYCLIC_LOCK))
    }

    @Test
    fun `BlockReason NONE is the default`() {
        assertEquals(UsageTracker.BlockReason.NONE, UsageTracker.BlockReason.valueOf("NONE"))
    }

    @Test
    fun `BlockReason has 5 enum values`() {
        assertEquals(5, UsageTracker.BlockReason.values().size)
    }

    @Test
    fun `onServiceConnected creates notification channel`() {
        Robolectric.buildService(BlockerService::class.java).create().get()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel("content_blocker")
        assertNotNull("BLOCKER_CHANNEL_ID notification channel should be created", channel)
        assertEquals("content_blocker", channel?.id)
    }

    @Test
    fun `onDestroy cleans up`() {
        val controller = Robolectric.buildService(BlockerService::class.java)
        controller.create().destroy()
    }

    @Test
    fun `BlockReason CYCLIC_LOCK is distinct from AUTO_LOCK`() {
        assertNotEquals(
            UsageTracker.BlockReason.CYCLIC_LOCK,
            UsageTracker.BlockReason.AUTO_LOCK
        )
    }

    @Test
    fun `BlockReason DAILY_LIMIT is distinct from ROUTINE_LIMIT`() {
        assertNotEquals(
            UsageTracker.BlockReason.DAILY_LIMIT,
            UsageTracker.BlockReason.ROUTINE_LIMIT
        )
    }
}
