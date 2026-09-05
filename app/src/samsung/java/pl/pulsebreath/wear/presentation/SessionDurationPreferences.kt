package pl.pulsebreath.wear.presentation

import android.content.Context
import pl.pulsebreath.wear.session.GuidedSessionDurations

internal object SessionDurationPreferences {
    private const val PREFS_NAME = "guided_session_preferences"
    private const val KEY_LAST_SELECTED_DURATION_SEC = "lastSelectedDurationSec"

    fun loadLastSelectedDurationMillis(context: Context): Long {
        val seconds = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_SELECTED_DURATION_SEC, 120)
        val durationMillis = seconds * 1_000L
        return if (GuidedSessionDurations.isAllowedSessionDuration(durationMillis)) {
            durationMillis
        } else {
            120_000L
        }
    }

    fun saveLastSelectedDurationMillis(context: Context, durationMillis: Long) {
        if (!GuidedSessionDurations.isAllowedSessionDuration(durationMillis)) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_SELECTED_DURATION_SEC, (durationMillis / 1_000L).toInt())
            .apply()
    }
}
