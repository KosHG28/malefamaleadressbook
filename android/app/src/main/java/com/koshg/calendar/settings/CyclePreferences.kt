package com.koshg.calendar.settings

import android.content.Context
import com.koshg.calendar.ui.theme.Palette
import com.koshg.calendar.util.DEFAULT_LUTEAL_PHASE_DAYS

private const val PREFS_NAME = "cycle_prefs"
private const val KEY_LUTEAL_PHASE_DAYS = "luteal_phase_days"
private const val KEY_ADAPTIVE_THEME = "adaptive_theme"
private const val KEY_PHASE_FILL_STYLE = "phase_fill_style"
private const val KEY_PALETTE = "palette"

/** How a phase-colored day renders in the month grid. */
enum class PhaseFillStyle {
    /** Solid color, merged into one capsule across a contiguous same-phase run (current look). */
    FILLED,

    /** A dashed outline per day, no fill -- the older, lighter-weight look. */
    DASHED
}

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

    var phaseFillStyle: PhaseFillStyle
        get() = runCatching {
            PhaseFillStyle.valueOf(prefs.getString(KEY_PHASE_FILL_STYLE, null) ?: "")
        }.getOrDefault(PhaseFillStyle.FILLED)
        set(value) = prefs.edit().putString(KEY_PHASE_FILL_STYLE, value.name).apply()

    /** The app's overall accent/background color scheme, picked in Settings. */
    var palette: Palette
        get() = runCatching {
            Palette.valueOf(prefs.getString(KEY_PALETTE, null) ?: "")
        }.getOrDefault(Palette.WINE)
        set(value) = prefs.edit().putString(KEY_PALETTE, value.name).apply()
}
