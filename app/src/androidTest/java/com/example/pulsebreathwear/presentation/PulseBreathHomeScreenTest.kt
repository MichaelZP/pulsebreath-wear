package com.example.pulsebreathwear.presentation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PulseBreathHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsAppNameAndClickableStartButton() {
        var startClicked = false

        composeRule.setContent {
            PulseBreathApp(onStart = { startClicked = true })
        }

        composeRule.onNodeWithText("PulseBreath Wear").assertIsDisplayed()
        composeRule
            .onNodeWithText("Start")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertTrue("Start callback should be invoked", startClicked)
        }
    }
}
