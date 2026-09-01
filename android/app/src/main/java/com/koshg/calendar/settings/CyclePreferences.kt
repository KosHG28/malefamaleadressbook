package com.koshg.calendar.settings

import android.content.Context
import com.koshg.calendar.util.DEFAULT_LUTEAL_PHASE_DAYS

private const val PREFS_NAME = "cycle_prefs"
private const val KEY_LUTEAL_PHASE_DAYS = "luteal_phase_days"
private const val KEY_ADAPTIVE_THEME = "adaptive_theme"
private const val KEY_GRADIENT_DAY_FILL = "gradient_day_fill"
private const val KEY_VIVID_COLORS = "vivid_colors"

/** User-overridable cycle-model parameters and appearance toggles, persisted across launches. */
class CyclePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lutealPhaseDays: Int
        get() = prefs.getInt(KEY_LUTEAL_PHASE_DAYS, DEFAULT_LUTEAL_PHASE_DAYS)
        set(value) = prefs.edit().putInt(KEY_LUTEAL_PHASE_DAYS, value).apply()

    /** Blends the FAB/selection accent color across cycle phases instead of a fixed accent. */
    var adaptiveTheme: Boolean
        get() = prefs.getBoolean(KEY_ADAPTIVE_THEME, false)
        set(value) = prefs.edit().putBoolean(KEY_ADAPTIVE_THEME, value).apply()

    /** Shades each day's fill lighter/darker across a contiguous run of the same phase. */
    var gradientDayFill: Boolean
        get() = prefs.getBoolean(KEY_GRADIENT_DAY_FILL, false)
        set(value) = prefs.edit().putBoolean(KEY_GRADIENT_DAY_FILL, value).apply()

    /** Off by default (calmer, desaturated phase colors); on restores the original vivid palette. */
    var vividColors: Boolean
        get() = prefs.getBoolean(KEY_VIVID_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_VIVID_COLORS, value).apply()
}
