package com.koshg.calendar.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Matches themes.xml's light/dark `calendar_background`. Kept in sync with [ThemeMode] here
 *  rather than left to the static `values-night` resource, which only ever follows the system
 *  setting and would otherwise ignore a manual override. */
private const val STATUS_BAR_LIGHT = 0xFFFFEFE1.toInt()
private const val STATUS_BAR_DARK = 0xFF120D0B.toInt()

/**
 * minSdk is 36 (Android 16), so per-app dynamic color (API 31+) is always available —
 * no legacy static color-scheme fallback to maintain.
 */
@Composable
fun CalendarAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = context as? Activity
        SideEffect {
            val window = activity?.window ?: return@SideEffect
            window.statusBarColor = if (darkTheme) STATUS_BAR_DARK else STATUS_BAR_LIGHT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
