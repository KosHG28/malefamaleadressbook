package com.koshg.calendar.settings

import android.content.Context
import com.koshg.calendar.util.DEFAULT_LUTEAL_PHASE_DAYS

private const val PREFS_NAME = "cycle_prefs"
private const val KEY_LUTEAL_PHASE_DAYS = "luteal_phase_days"

/** User-overridable cycle-model parameters, persisted across launches. */
class CyclePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lutealPhaseDays: Int
        get() = prefs.getInt(KEY_LUTEAL_PHASE_DAYS, DEFAULT_LUTEAL_PHASE_DAYS)
        set(value) = prefs.edit().putInt(KEY_LUTEAL_PHASE_DAYS, value).apply()
}
