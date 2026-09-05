package pl.pulsebreath.wear.presentation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import pl.pulsebreath.wear.presentation.theme.PulseBreathWearTheme
import pl.pulsebreath.wear.session.BreathingSessionConfig
import pl.pulsebreath.wear.session.BreathingSessionState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PulseBreathAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeShowsSessionParametersAndStartAction() {
        composeRule.setContent {
            PulseBreathApp()
        }

        composeRule.onNodeWithText("PulseBreath Wear").assertIsDisplayed()
        composeRule.onNodeWithText("2 min · 4,5 s / 5,5 s").assertIsDisplayed()
        composeRule
            .onNodeWithText("Start")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun runningScreenExposesPauseAndCancelActions() {
        var pauseClicked = false
        var cancelClicked = false
        val config = BreathingSessionConfig()
        val snapshot =
            BreathingSessionState()
                .start(nowMillis = 0L)
                .snapshot(nowMillis = 2_250L, config = config)

        composeRule.setContent {
            PulseBreathWearTheme {
                BreathingSessionScreen(
                    snapshot = snapshot,
                    onStart = {},
                    onPause = { pauseClicked = true },
                    onResume = {},
                    onCancel = { cancelClicked = true },
                    onReset = {},
                )
            }
        }

        composeRule.onNodeWithText("Wdech").assertIsDisplayed()
        composeRule.onNodeWithText("1:58").assertIsDisplayed()
        composeRule.onNodeWithText("Pauza").performClick()
        composeRule.onNodeWithText("Stop").performClick()

        composeRule.runOnIdle {
            assertTrue("Pause callback should be invoked", pauseClicked)
            assertTrue("Cancel callback should be invoked", cancelClicked)
        }
    }

    @Test
    fun pausedScreenExposesResumeAction() {
        var resumeClicked = false
        val config = BreathingSessionConfig()
        val snapshot =
            BreathingSessionState()
                .start(nowMillis = 0L)
                .pause(nowMillis = 3_000L, config = config)
                .snapshot(nowMillis = 30_000L, config = config)

        composeRule.setContent {
            PulseBreathWearTheme {
                BreathingSessionScreen(
                    snapshot = snapshot,
                    onStart = {},
                    onPause = {},
                    onResume = { resumeClicked = true },
                    onCancel = {},
                    onReset = {},
                )
            }
        }

        composeRule.onNodeWithText("Pauza").assertIsDisplayed()
        composeRule.onNodeWithText("Dalej").performClick()

        composeRule.runOnIdle {
            assertTrue("Resume callback should be invoked", resumeClicked)
        }
    }

    @Test
    fun shortConfiguredSessionReachesCompletedScreen() {
        composeRule.setContent {
            PulseBreathApp(
                config =
                    BreathingSessionConfig(
                        inhaleDurationMillis = 100L,
                        exhaleDurationMillis = 100L,
                        sessionDurationMillis = 300L,
                    ),
            )
        }

        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule
                .onAllNodesWithText("Sesja zakończona")
                .fetchSemanticsNodes()
                .size == 1
        }

        composeRule.onNodeWithText("Sesja zakończona").assertIsDisplayed()
        composeRule.onNodeWithText("Od nowa").assertIsDisplayed().assertHasClickAction()
    }
}
