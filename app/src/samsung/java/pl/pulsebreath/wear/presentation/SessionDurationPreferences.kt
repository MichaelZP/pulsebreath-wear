package pl.pulsebreath.wear.presentation

import android.content.Context
import pl.pulsebreath.wear.session.GuidedSessionDurations
import pl.pulsebreath.wear.session.SessionSeries

internal object SessionDurationPreferences {
    private const val PREFS_NAME = "guided_session_preferences"
    private const val KEY_LAST_SELECTED_DURATION_SEC = "lastSelectedDurationSec"
    private const val KEY_DYNAMIC_TUNING_ENABLED = "dynamicTuningEnabled"
    private const val KEY_DYNAMIC_TUNING_ALLOW_WEAK = "dynamicTuningAllowWeak"
    private const val KEY_SESSION_SERIES_COUNT = "sessionSeriesCount"

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

    fun loadDynamicTuningEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC_TUNING_ENABLED, true)

    fun saveDynamicTuningEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC_TUNING_ENABLED, enabled).apply()
    }

    fun loadDynamicTuningAllowsWeak(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC_TUNING_ALLOW_WEAK, true)

    fun saveDynamicTuningAllowsWeak(context: Context, allowed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC_TUNING_ALLOW_WEAK, allowed).apply()
    }

    fun loadSessionSeriesCount(context: Context): Int =
        SessionSeries.normalizeSelectedCount(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SESSION_SERIES_COUNT, SessionSeries.SINGLE_SESSION),
        )

    fun saveSessionSeriesCount(context: Context, count: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SESSION_SERIES_COUNT, SessionSeries.normalizeSelectedCount(count)).apply()
    }
}
