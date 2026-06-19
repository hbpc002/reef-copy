package dev.pranav.reef

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.CyclicConfig
import dev.pranav.reef.util.Whitelist
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class AppLimitsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(Whitelist)
        every { Whitelist.isWhitelisted(any()) } returns false
        AppLimits.init(context)
    }

    @Test
    fun `isWhitelisted delegates to Whitelist`() {
        every { Whitelist.isWhitelisted("com.test.pkg") } returns true
        assertTrue(AppLimits.isWhitelisted("com.test.pkg"))
        verify { Whitelist.isWhitelisted("com.test.pkg") }

        every { Whitelist.isWhitelisted("com.other.pkg") } returns false
        assertFalse(AppLimits.isWhitelisted("com.other.pkg"))
    }

    @Test
    fun `setLimit and getLimit`() {
        AppLimits.setLimit("com.test.app", 30)
        assertEquals(30 * 60_000L, AppLimits.getLimit("com.test.app"))
    }

    @Test
    fun `getLimit returns 0 for unset pkg`() {
        assertEquals(0L, AppLimits.getLimit("com.nonexistent"))
    }

    @Test
    fun `setCyclicConfig and getCyclicConfig`() {
        val config = CyclicConfig(usageMinutes = 10, lockMinutes = 3)
        AppLimits.setCyclicConfig("com.test.app", config)

        val retrieved = AppLimits.getCyclicConfig("com.test.app")
        assertNotNull(retrieved)
        assertEquals(10, retrieved?.usageMinutes)
        assertEquals(3, retrieved?.lockMinutes)
    }

    @Test
    fun `setCyclicConfig with null removes config`() {
        AppLimits.setCyclicConfig("com.test.app", CyclicConfig(5, 2))
        assertNotNull(AppLimits.getCyclicConfig("com.test.app"))

        AppLimits.setCyclicConfig("com.test.app", null)
        assertNull(AppLimits.getCyclicConfig("com.test.app"))
    }

    @Test
    fun `getCyclicConfig returns null for unset pkg`() {
        assertNull(AppLimits.getCyclicConfig("com.nonexistent"))
    }

    @Test
    fun `setLockUntil and getLockUntilMs`() {
        val untilMs = System.currentTimeMillis() + 60_000L
        AppLimits.setLockUntil("com.test.app", untilMs)
        assertEquals(untilMs, AppLimits.getLockUntilMs("com.test.app"))
    }

    @Test
    fun `getLockUntilMs returns 0 for unset pkg`() {
        assertEquals(0L, AppLimits.getLockUntilMs("com.nonexistent"))
    }

    @Test
    fun `setLockDuration and getLockDurationMs`() {
        AppLimits.setLockDuration("com.test.app", 15)
        assertEquals(15 * 60_000L, AppLimits.getLockDurationMs("com.test.app"))
    }

    @Test
    fun `getLockDurationMs returns 0 for unset pkg`() {
        assertEquals(0L, AppLimits.getLockDurationMs("com.nonexistent"))
    }

    @Test
    fun `setCyclicCycleStartUsage and getCyclicCycleStartUsage`() {
        AppLimits.setCyclicCycleStartUsage("com.test.app", 5000L)
        assertEquals(5000L, AppLimits.getCyclicCycleStartUsage("com.test.app"))
    }

    @Test
    fun `getCyclicCycleStartUsage returns 0 for unset pkg`() {
        assertEquals(0L, AppLimits.getCyclicCycleStartUsage("com.nonexistent"))
    }

    @Test
    fun `setCyclicLockUntil and getCyclicLockUntilMs`() {
        val untilMs = System.currentTimeMillis() + 120_000L
        AppLimits.setCyclicLockUntil("com.test.app", untilMs)
        assertEquals(untilMs, AppLimits.getCyclicLockUntilMs("com.test.app"))
    }

    @Test
    fun `isInCyclicLockPhase returns true when lock is active`() {
        AppLimits.setCyclicLockUntil("com.test.app", System.currentTimeMillis() + 60_000L)
        assertTrue(AppLimits.isInCyclicLockPhase("com.test.app"))
    }

    @Test
    fun `isInCyclicLockPhase returns false when lock expired`() {
        AppLimits.setCyclicLockUntil("com.test.app", System.currentTimeMillis() - 1000L)
        assertFalse(AppLimits.isInCyclicLockPhase("com.test.app"))
    }

    @Test
    fun `removeLimit removes all entries for pkg`() {
        AppLimits.setLimit("com.test.app", 30)
        AppLimits.setLockDuration("com.test.app", 15)
        AppLimits.setLockUntil("com.test.app", System.currentTimeMillis() + 60_000L)
        AppLimits.setCyclicConfig("com.test.app", CyclicConfig(10, 3))
        AppLimits.setCyclicCycleStartUsage("com.test.app", 1000L)
        AppLimits.setCyclicLockUntil("com.test.app", System.currentTimeMillis() + 60_000L)

        AppLimits.removeLimit("com.test.app")

        assertEquals(0L, AppLimits.getLimit("com.test.app"))
        assertEquals(0L, AppLimits.getLockDurationMs("com.test.app"))
        assertEquals(0L, AppLimits.getLockUntilMs("com.test.app"))
        assertNull(AppLimits.getCyclicConfig("com.test.app"))
        assertEquals(0L, AppLimits.getCyclicCycleStartUsage("com.test.app"))
        assertEquals(0L, AppLimits.getCyclicLockUntilMs("com.test.app"))
    }

    @Test
    fun `clearExpiredLocks removes expired lock entries`() {
        AppLimits.setLockUntil("com.expired", System.currentTimeMillis() - 1000L)
        AppLimits.setLockUntil("com.active", System.currentTimeMillis() + 60_000L)

        AppLimits.clearExpiredLocks()

        assertEquals(0L, AppLimits.getLockUntilMs("com.expired"))
        assertTrue(AppLimits.getLockUntilMs("com.active") > 0L)
    }

    @Test
    fun `reminderSentToday returns true when reminder was sent today`() {
        AppLimits.markReminder("com.test.app")
        assertTrue(AppLimits.reminderSentToday("com.test.app"))
    }

    @Test
    fun `reminderSentToday returns false when no reminder sent`() {
        assertFalse(AppLimits.reminderSentToday("com.test.app"))
    }

    @Test
    fun `save persists limits`() {
        AppLimits.setLimit("com.test.app", 30)
        AppLimits.save()

        val newContext = ApplicationProvider.getApplicationContext<android.app.Application>()
        AppLimits.init(newContext)

        assertEquals(30 * 60_000L, AppLimits.getLimit("com.test.app"))
    }

    @Test
    fun `hasLimit returns true when limit is set`() {
        assertFalse(AppLimits.hasLimit("com.test.app"))

        AppLimits.setLimit("com.test.app", 30)
        assertTrue(AppLimits.hasLimit("com.test.app"))
    }
}
