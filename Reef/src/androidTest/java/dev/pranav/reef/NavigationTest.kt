package dev.pranav.reef

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pranav.reef.util.prefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deviceContext = context.createDeviceProtectedStorageContext()
        prefs = deviceContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @Test
    fun bottomNav_hasAllFourTabs() {
        composeTestRule.onNodeWithText("Home").assertExists()
        composeTestRule.onNodeWithText("Stats").assertExists()
        composeTestRule.onNodeWithText("Focus").assertExists()
        composeTestRule.onNodeWithText("Settings").assertExists()
    }

    @Test
    fun bottomNav_navigateToFocusTab() {
        composeTestRule.onNodeWithText("Focus").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Timer").assertExists()
    }

    @Test
    fun bottomNav_navigateToSettings() {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Pomodoro").assertExists()
    }

    @Test
    fun bottomNav_navigateToStatsTab() {
        composeTestRule.onNodeWithText("Stats").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("App Usage").assertExists()
    }

    @Test
    fun bottomNav_returnToHome() {
        composeTestRule.onNodeWithText("Focus").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Focus Mode").assertExists()
    }
}
