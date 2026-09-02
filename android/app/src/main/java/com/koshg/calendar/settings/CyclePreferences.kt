package com.koshg.calendar.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.koshg.calendar.reminders.ReminderScheduler
import com.koshg.calendar.ui.theme.Palette
import com.koshg.calendar.ui.theme.ThemeMode
import com.koshg.calendar.util.DEFAULT_LUTEAL_PHASE_DAYS

private const val PREFS_NAME = "cycle_prefs"
private const val KEY_LUTEAL_PHASE_DAYS = "luteal_phase_days"
private const val KEY_ADAPTIVE_THEME = "adaptive_theme"
private const val KEY_PHASE_FILL_STYLE = "phase_fill_style"
private const val KEY_PALETTE = "palette"
private const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"
private const val KEY_SUGGESTION_DISMISSED_UNTIL = "suggestion_dismissed_until_epoch_day"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_REMINDERS_ENABLED = "reminders_enabled"
private const val KEY_LAST_PERIOD_REMINDER_EPOCH_DAY = "last_period_reminder_epoch_day"
private const val KEY_LAST_OVULATION_REMINDER_EPOCH_DAY = "last_ovulation_reminder_epoch_day"

/** The one launcher alias AndroidManifest.xml ships with android:enabled="true"; the other four
 *  are declared disabled. Needed to resolve COMPONENT_ENABLED_STATE_DEFAULT, which means "as the
 *  manifest declares" rather than a state of its own. */
private val MANIFEST_ENABLED_PALETTE = Palette.WINE

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
        // No launcher-icon sync here: it can only run with the app off screen (see
        // [syncLauncherIcon]), and process start is the one moment it definitely isn't --
        // MainActivity drives it from onStop() instead.

        // Self-healing for reminders: re-syncs the WorkManager job to match the stored
        // setting (and current permission state) on every start, so an OS-level permission
        // revocation or an app upgrade never leaves a stale job running or a wanted one missing.
        ReminderScheduler.ensureChannel(appContext)
        ReminderScheduler.sync(appContext, remindersEnabled && hasNotificationPermission())
    }

    private fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

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
     *  of the palette-matched launcher-icon activity-aliases is enabled (see [syncLauncherIcon]).
     *  Saving it only writes the preference; the icon follows once the app leaves the screen. */
    var palette: Palette
        get() = runCatching {
            Palette.valueOf(prefs.getString(KEY_PALETTE, null) ?: "")
        }.getOrDefault(Palette.WINE)
        set(value) = prefs.edit().putString(KEY_PALETTE, value.name).apply()

    /**
     * Enables the alias matching the stored [palette] and disables the other four, so the
     * home-screen icon follows the chosen color scheme.
     *
     * Only safe to call with the app off screen, which is why MainActivity drives it from
     * onStop() rather than the palette setter calling it directly: MainActivity is not itself
     * exported, so the running task's root component IS one of these aliases, and disabling the
     * alias a task was launched from makes the system tear that task down. That looks exactly
     * like the app crashing mid-use, and [PackageManager.DONT_KILL_APP] doesn't prevent it --
     * it spares the process, not the activity.
     *
     * Writes only the aliases whose state actually differs, so the usual "palette unchanged"
     * pass touches the package manager not at all and leaves the backgrounded task alone. Each
     * write is wrapped in [runCatching] because this is a call into the OS package manager, an
     * external boundary a handful of OEM builds are known to reject in edge cases -- a failure
     * should leave the icon as it was, never propagate.
     */
    fun syncLauncherIcon() {
        val packageManager = appContext.packageManager
        val target = palette
        Palette.entries.forEach { candidate ->
            val alias = ComponentName(appContext, candidate.launcherAliasClassName())
            val shouldBeEnabled = candidate == target
            if (isAliasEnabled(packageManager, alias, candidate) == shouldBeEnabled) return@forEach
            val state = if (shouldBeEnabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                packageManager.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
            }
        }
    }

    /** One alias's current on-device state, resolving COMPONENT_ENABLED_STATE_DEFAULT to what the
     *  manifest declares. Null when the state can't be read at all, which callers treat as "not
     *  known to match" and so as worth writing. */
    private fun isAliasEnabled(
        packageManager: PackageManager,
        alias: ComponentName,
        candidate: Palette
    ): Boolean? = runCatching {
        when (packageManager.getComponentEnabledSetting(alias)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> candidate == MANIFEST_ENABLED_PALETTE
        }
    }.getOrNull()

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

    /** Overrides the system light/dark setting, or follows it (the default). */
    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: "")
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    /** Whether the daily reminder worker may post a period-approaching/ovulation-day notification.
     *  The setter (re-)syncs the WorkManager job immediately, same as [palette]'s launcher-icon
     *  side effect -- the caller (Settings) only needs to have already secured the POST_NOTIFICATIONS
     *  permission before flipping this on; [hasNotificationPermission] double-checks it here too. */
    var remindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, value).apply()
            ReminderScheduler.sync(appContext, value && hasNotificationPermission())
        }

    /** Epoch day the period-approaching reminder last fired, so a same-day worker re-run
     *  (retry, doze-window slip) never posts it twice. */
    var lastPeriodReminderEpochDay: Long
        get() = prefs.getLong(KEY_LAST_PERIOD_REMINDER_EPOCH_DAY, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PERIOD_REMINDER_EPOCH_DAY, value).apply()

    /** Same de-duplication as [lastPeriodReminderEpochDay], for the ovulation-day reminder. */
    var lastOvulationReminderEpochDay: Long
        get() = prefs.getLong(KEY_LAST_OVULATION_REMINDER_EPOCH_DAY, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_OVULATION_REMINDER_EPOCH_DAY, value).apply()
}
