package pl.pulsebreath.wear.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import pl.pulsebreath.wear.presentation.theme.PulseBreathWearTheme
import pl.pulsebreath.wear.sensor.FakeSensorFrame
import pl.pulsebreath.wear.sensor.FakeSensorScenario
import pl.pulsebreath.wear.sensor.SensorSample
import pl.pulsebreath.wear.sensor.SensorSignalQuality
import pl.pulsebreath.wear.sensor.SensorSourceType
import pl.pulsebreath.wear.signal.HrvMetrics
import pl.pulsebreath.wear.signal.HrvWindowQuality
import pl.pulsebreath.wear.session.BreathingPhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FakeSensorDiagnosticsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun calmFrameIsClearlyMarkedAsSimulated() {
        setDiagnosticsContent(
            frame =
                FakeSensorFrame(
                    scenario = FakeSensorScenario.CALM,
                    sample =
                        SensorSample(
                            monotonicTimestampMillis = 1_000L,
                            beatsPerMinute = 60.0,
                            ibiMillis = listOf(1_000L),
                            quality = SensorSignalQuality.GOOD,
                            sourceType = SensorSourceType.SIMULATED,
                        ),
                ),
            phase = BreathingPhase.INHALE,
        )

        composeRule.onNodeWithText("DANE SYMULOWANE").assertIsDisplayed()
        composeRule.onNodeWithText("Spokojny rytm").assertIsDisplayed()
        composeRule.onNodeWithText("IBI 1000 ms").assertIsDisplayed()
        composeRule.onNodeWithText("Jakość: dobra").assertIsDisplayed()
        composeRule.onNodeWithText("Wdech").assertIsDisplayed()
    }

    @Test
    fun signalLossDoesNotDisplayInventedBpmOrIbi() {
        setDiagnosticsContent(
            frame =
                FakeSensorFrame(
                    scenario = FakeSensorScenario.SIGNAL_LOSS,
                    sample =
                        SensorSample(
                            monotonicTimestampMillis = 26_000L,
                            beatsPerMinute = null,
                            ibiMillis = emptyList(),
                            quality = SensorSignalQuality.SIGNAL_LOST,
                            sourceType = SensorSourceType.SIMULATED,
                        ),
                ),
            phase = BreathingPhase.EXHALE,
        )

        composeRule.onNodeWithText("Brak sygnału").assertIsDisplayed()
        composeRule.onNodeWithText("— BPM").assertIsDisplayed()
        composeRule.onNodeWithText("IBI —").assertIsDisplayed()
        composeRule.onNodeWithText("Jakość: brak").assertIsDisplayed()
        composeRule.onNodeWithText("Wydech").assertIsDisplayed()
    }

    @Test
    fun hrvWindowQualityIsDisplayedSeparatelyFromSourceQuality() {
        composeRule.setContent {
            PulseBreathWearTheme {
                FakeSensorDiagnosticsScreen(
                    frame =
                        FakeSensorFrame(
                            scenario = FakeSensorScenario.CALM,
                            sample =
                                SensorSample(
                                    monotonicTimestampMillis = 1_000L,
                                    beatsPerMinute = 60.0,
                                    ibiMillis = listOf(1_000L),
                                    quality = SensorSignalQuality.GOOD,
                                    sourceType = SensorSourceType.SIMULATED,
                                ),
                        ),
                    breathingPhase = BreathingPhase.INHALE,
                    metrics =
                        HrvMetrics(
                            analysisEndMillis = 1_000L,
                            sampleEventCount = 10,
                            goodSampleEventCount = 10,
                            validIbiEventCount = 10,
                            validIbiCount = 10,
                            invalidIbiCount = 0,
                            ibiEventCoveragePercent = 100.0,
                            meanBpm = 60.0,
                            rmssdMillis = 12.5,
                            quality = HrvWindowQuality.ADEQUATE,
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Jakość: dobra").assertIsDisplayed()
        composeRule.onNodeWithText("Okno HRV: wystarczające").assertIsDisplayed()
        composeRule.onNodeWithText("Pokrycie IBI: 100%").assertIsDisplayed()
        composeRule.onNodeWithText("RMSSD 12.5 ms").assertIsDisplayed()
    }

    private fun setDiagnosticsContent(
        frame: FakeSensorFrame,
        phase: BreathingPhase,
    ) {
        composeRule.setContent {
            PulseBreathWearTheme {
                FakeSensorDiagnosticsScreen(
                    frame = frame,
                    breathingPhase = phase,
                )
            }
        }
    }
}
