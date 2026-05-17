package dev.pranav.reef

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pranav.reef.screens.hashPin
import dev.pranav.reef.ui.lock.AppLockScreen
import dev.pranav.reef.util.prefs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var testContext: Context

    @Before
    fun setup() {
        testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val deviceContext = testContext.createDeviceProtectedStorageContext()
        prefs = deviceContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @After
    fun cleanup() {
        prefs.edit().clear().apply()
    }

    @Test
    fun lockScreen_showsTitle() {
        prefs.edit()
            .putString("app_lock_pin_hash", hashPin("1234"))
            .apply()

        composeTestRule.setContent {
            AppLockScreen(onUnlock = {})
        }

        composeTestRule.onNodeWithText("Reef").assertExists()
        composeTestRule.onNodeWithText("Enter PIN to unlock").assertExists()
    }

    @Test
    fun lockScreen_showsDigitButtons() {
        prefs.edit()
            .putString("app_lock_pin_hash", hashPin("1234"))
            .apply()

        composeTestRule.setContent {
            AppLockScreen(onUnlock = {})
        }

        for (digit in 0..9) {
            composeTestRule.onNodeWithText(digit.toString()).assertExists()
        }
    }

    @Test
    fun lockScreen_showsDeleteKey() {
        prefs.edit()
            .putString("app_lock_pin_hash", hashPin("1234"))
            .apply()

        composeTestRule.setContent {
            AppLockScreen(onUnlock = {})
        }

        composeTestRule.onNodeWithText("⌫").assertExists()
    }

    @Test
    fun lockScreen_showsErrorOnWrongPinAndClears() {
        prefs.edit()
            .putString("app_lock_pin_hash", hashPin("1234"))
            .apply()

        var unlocked = false
        composeTestRule.setContent {
            AppLockScreen(onUnlock = { unlocked = true })
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()
        composeTestRule.onNodeWithText("5").performClick()
        composeTestRule.onNodeWithText("6").performClick()

        composeTestRule.onNodeWithText("Incorrect PIN").assertExists()
        assertFalse(unlocked)
    }

    @Test
    fun lockScreen_unlocksOnCorrectPin() {
        prefs.edit()
            .putString("app_lock_pin_hash", hashPin("1234"))
            .apply()

        var unlocked = false
        composeTestRule.setContent {
            AppLockScreen(onUnlock = { unlocked = true })
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()

        assertTrue(unlocked)
    }

    @Test
    fun lockScreen_deleteKeyRemovesLastDigit() {
        prefs.edit()
            .putString("app_lock_pin_hash", hashPin("1234"))
            .apply()

        composeTestRule.setContent {
            AppLockScreen(onUnlock = {})
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("⌫").performClick()
        composeTestRule.onNodeWithText("⌫").performClick()

        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()

        composeTestRule.onNodeWithText("Incorrect PIN").assertExists()
    }

    @Test
    fun lockScreen_deletesAllowsRetryAfterError() {
        prefs.edit()
            .putString("app_lock_pin_hash", hashPin("5678"))
            .apply()

        var unlocked = false
        composeTestRule.setContent {
            AppLockScreen(onUnlock = { unlocked = true })
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()
        composeTestRule.onNodeWithText("5").performClick()
        composeTestRule.onNodeWithText("6").performClick()
        composeTestRule.onNodeWithText("Incorrect PIN").assertExists()

        composeTestRule.onNodeWithText("5").performClick()
        composeTestRule.onNodeWithText("6").performClick()
        composeTestRule.onNodeWithText("7").performClick()
        composeTestRule.onNodeWithText("8").performClick()

        assertTrue(unlocked)
    }

    @Test
    fun lockScreen_skipsWhenNoHash() {
        prefs.edit().clear().apply()

        var unlocked = false
        composeTestRule.setContent {
            AppLockScreen(onUnlock = { unlocked = true })
        }

        assertTrue(unlocked)
    }
}
