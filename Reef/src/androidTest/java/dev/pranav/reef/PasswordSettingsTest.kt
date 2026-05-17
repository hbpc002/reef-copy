package dev.pranav.reef

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pranav.reef.screens.PasswordSettingsContent
import dev.pranav.reef.screens.hashPin
import dev.pranav.reef.util.prefs
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordSettingsTest {

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
    fun passwordSettings_showsToggle() {
        composeTestRule.setContent {
            PasswordSettingsContent(onBackPressed = {})
        }

        composeTestRule.onNodeWithText("App Lock").assertExists()
        composeTestRule.onNodeWithText("Require a PIN to open Reef").assertExists()
    }

    @Test
    fun passwordSettings_toggleOn_showsPinFields() {
        composeTestRule.setContent {
            PasswordSettingsContent(onBackPressed = {})
        }

        val switch = composeTestRule.onNodeWithText("App Lock").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Set PIN").assertExists()
        composeTestRule.onNodeWithText("New PIN").assertExists()
        composeTestRule.onNodeWithText("Confirm PIN").assertExists()
        composeTestRule.onNodeWithText("Save PIN").assertExists()
    }

    @Test
    fun passwordSettings_savePin_succeeds() {
        composeTestRule.setContent {
            PasswordSettingsContent(onBackPressed = {})
        }

        composeTestRule.onNodeWithText("App Lock").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("New PIN").performTextInput("1234")
        composeTestRule.onNodeWithText("Confirm PIN").performTextInput("1234")
        composeTestRule.onNodeWithText("Save PIN").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("PIN saved successfully").assertExists()
    }

    @Test
    fun passwordSettings_mismatchedPins_showsError() {
        composeTestRule.setContent {
            PasswordSettingsContent(onBackPressed = {})
        }

        composeTestRule.onNodeWithText("App Lock").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("New PIN").performTextInput("1234")
        composeTestRule.onNodeWithText("Confirm PIN").performTextInput("5678")
        composeTestRule.onNodeWithText("Save PIN").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("PINs do not match").assertExists()
    }

    @Test
    fun passwordSettings_shortPin_showsError() {
        composeTestRule.setContent {
            PasswordSettingsContent(onBackPressed = {})
        }

        composeTestRule.onNodeWithText("App Lock").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("New PIN").performTextInput("12")
        composeTestRule.onNodeWithText("Confirm PIN").performTextInput("12")
        composeTestRule.onNodeWithText("Save PIN").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("PIN must be at least 4 digits").assertExists()
    }

    @Test
    fun passwordSettings_afterSaving_showsChangePinTitle() {
        prefs.edit()
            .putBoolean("app_lock_enabled", true)
            .putString("app_lock_pin_hash", hashPin("1234"))
            .apply()

        composeTestRule.setContent {
            PasswordSettingsContent(onBackPressed = {})
        }

        composeTestRule.onNodeWithText("Change PIN").assertExists()
        composeTestRule.onNodeWithText("Remove PIN").assertExists()
    }

    @Test
    fun passwordSettings_removePin_clearsHash() {
        prefs.edit()
            .putBoolean("app_lock_enabled", true)
            .putString("app_lock_pin_hash", hashPin("1234"))
            .apply()

        composeTestRule.setContent {
            PasswordSettingsContent(onBackPressed = {})
        }

        composeTestRule.onNodeWithText("Remove PIN").performClick()
        composeTestRule.waitForIdle()

        assertEquals(null, prefs.getString("app_lock_pin_hash", null))
        assertEquals(false, prefs.getBoolean("app_lock_enabled", true))
    }

    @Test
    fun passwordSettings_toggleOff_disablesLock() {
        prefs.edit()
            .putBoolean("app_lock_enabled", true)
            .putString("app_lock_pin_hash", hashPin("1234"))
            .apply()

        composeTestRule.setContent {
            PasswordSettingsContent(onBackPressed = {})
        }

        composeTestRule.onNodeWithText("App Lock").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("App lock disabled").assertExists()
        assertEquals(false, prefs.getBoolean("app_lock_enabled", true))
    }

    @Test
    fun passwordSettings_backButton_works() {
        var backPressed = false
        composeTestRule.setContent {
            PasswordSettingsContent(onBackPressed = { backPressed = true })
        }

        composeTestRule.onNodeWithText("Back").performClick()
        assertTrue(backPressed)
    }
}
