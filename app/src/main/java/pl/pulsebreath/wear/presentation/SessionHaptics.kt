package pl.pulsebreath.wear.presentation

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import pl.pulsebreath.wear.session.BreathingPhase

/** Wear haptics: READY 55 ms; INHALE 25+25 ms (45 ms gap); EXHALE 85 ms. */
internal class SessionHaptics(context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun ready() = vibrate(longArrayOf(0, 55))
    fun inhale() = vibrate(longArrayOf(0, 25, 45, 25))
    fun exhale() = vibrate(longArrayOf(0, 85))
    fun sessionComplete() = vibrate(longArrayOf(0, 100, 90, 100))
    fun seriesComplete() = vibrate(longArrayOf(0, 220, 90, 220, 90, 320))
    fun cancel() { vibrator?.cancel() }

    private fun vibrate(pattern: LongArray) {
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}

internal class SessionHapticEdges {
    private var priorReady = false
    private var priorPhase: BreathingPhase? = null

    fun readyTransition(isReady: Boolean): Boolean = isReady && !priorReady.also { priorReady = isReady }
    fun phaseTransition(running: Boolean, phase: BreathingPhase): BreathingPhase? {
        if (!running) { priorPhase = null; return null }
        return phase.takeIf { it != priorPhase }.also { priorPhase = phase }
    }
}
