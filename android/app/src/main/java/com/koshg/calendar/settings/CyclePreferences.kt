package com.koshg.calendar.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.koshg.calendar.ui.theme.Palette
import com.koshg.calendar.util.DEFAULT_LUTEAL_PHASE_DAYS

private const val PREFS_NAME = "cycle_prefs"
private const val KEY_LUTEAL_PHASE_DAYS = "luteal_phase_days"
private const val KEY_ADAPTIVE_THEME = "adaptive_theme"
private const val KEY_PHASE_FILL_STYLE = "phase_fill_style"
private const val KEY_PALETTE = "palette"
private const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"
private const val KEY_SUGGESTION_DISMISSED_UNTIL = "suggestion_dismissed_until_epoch_day"

/** How a phase-colored day renders in the month grid. */
enum class PhaseFillStyle {
    /** Solid color, merged into one capsule across a contiguous same-phase run (current look). */
    FILLED,

    /** A dashed outline per day, no fill -- the older, lighter-weight look. */
    DASHED
}

/** User-overridable cycle-model parameters and appearance toggles, persisted across launches. */
class CyclePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Self-heals the launcher icon to match the stored palette on every process start, not
        // just when the user actively picks one in Settings -- e.g. after this feature's own
        // rollout, when the activity-alias components are brand new and default to the manifest's
        // enabled state (Wine) regardless of whatever palette was already saved.
        applyLauncherIcon(palette)
    }

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

    /** The app's overall accent/background color scheme, picked in Settings -- also drives which
     *  of the palette-matched launcher-icon activity-aliases is enabled (see [applyLauncherIcon]). */
    var palette: Palette
        get() = runCatching {
            Palette.valueOf(prefs.getString(KEY_PALETTE, null) ?: "")
        }.getOrDefault(Palette.WINE)
        set(value) {
            prefs.edit().putString(KEY_PALETTE, value.name).apply()
            applyLauncherIcon(value)
        }

    /** Enables the alias matching [palette] and disables the other four, so the home-screen icon
     *  follows the chosen color scheme. Wrapped in [runCatching] since toggling component state is
     *  a call into the OS package manager -- an external boundary a handful of OEM builds are
     *  known to reject in edge cases -- and a failure here should never break saving the palette
     *  itself, only silently leave the icon as it was. */
    private fun applyLauncherIcon(palette: Palette) {
        val packageManager = appContext.packageManager
        Palette.entries.forEach { candidate ->
            val alias = ComponentName(appContext, candidate.launcherAliasClassName())
            val state = if (candidate == palette) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                packageManager.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
            }
        }
    }

    private fun Palette.launcherAliasClassName(): String = when (this) {
        Palette.WINE -> "com.koshg.calendar.LauncherWine"
        Palette.MIDNIGHT -> "com.koshg.calendar.LauncherMidnight"
        Palette.FOREST -> "com.koshg.calendar.LauncherForest"
        Palette.PLUM -> "com.koshg.calendar.LauncherPlum"
        Palette.GRAPHITE -> "com.koshg.calendar.LauncherGraphite"
    }

    /** Whether the calendar screen may show a proactive, data-driven suggestion banner. */
    var suggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUGGESTIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SUGGESTIONS_ENABLED, value).apply()

    /** Epoch day until which the suggestion banner stays snoozed after the user dismisses it. */
    var suggestionDismissedUntilEpochDay: Long
        get() = prefs.getLong(KEY_SUGGESTION_DISMISSED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_SUGGESTION_DISMISSED_UNTIL, value).apply()
}
