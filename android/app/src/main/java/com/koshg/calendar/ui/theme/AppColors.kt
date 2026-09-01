package com.koshg.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.koshg.calendar.util.CyclePhase

@Immutable
data class AppColors(
    val accent: Color,
    val period: Color,
    val periodContainer: Color,
    val fertile: Color,
    val fertileContainer: Color,
    val ovulation: Color,
    val intimacy: Color,
    val proposalAccepted: Color,
    val proposalDeclined: Color,
    val solo: Color,
    val warmBackground: Color,
    val warmSurface: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val menstrual: Color,
    val follicular: Color,
    val lhPeak: Color,
    val ovulatory: Color,
    val luteal: Color,
    /** Explicit, theme-guaranteed text colors for content drawn on the gradient/warm surfaces —
     *  deliberately independent of MaterialTheme's dynamic (wallpaper-derived) color scheme,
     *  which can pick low-contrast tones on some devices. */
    val textPrimary: Color,
    val textSecondary: Color,
    val warning: Color
)

private val LightAppColors = AppColors(
    accent = Color(0xFFFF5C8A),
    period = Color(0xFFE0577A),
    periodContainer = Color(0xFFFCE1E9),
    fertile = Color(0xFF2FA88E),
    fertileContainer = Color(0xFFDCF3EC),
    ovulation = Color(0xFF2FA88E),
    intimacy = Color(0xFFE0577A),
    proposalAccepted = Color(0xFF9B6BD6),
    proposalDeclined = Color(0xFFB0A9A0),
    solo = Color(0xFF9B6BD6),
    warmBackground = Color(0xFFFBF3EA),
    warmSurface = Color(0xFFFFFDF9),
    gradientTop = Color(0xFFFFEFE1),
    gradientBottom = Color(0xFFFCE1EC),
    menstrual = Color(0xFFE0536B),
    follicular = Color(0xFF7C8CB8),
    lhPeak = Color(0xFFE0B400),
    ovulatory = Color(0xFF2F9DA6),
    luteal = Color(0xFF8B5FBF),
    textPrimary = Color(0xFF2A211C),
    textSecondary = Color(0xFF8A7A6E),
    warning = Color(0xFFB8722B)
)

private val DarkAppColors = AppColors(
    accent = Color(0xFFFF8FAE),
    period = Color(0xFFF497B4),
    periodContainer = Color(0xFF4A2431),
    fertile = Color(0xFF6FD4B9),
    fertileContainer = Color(0xFF1C3A33),
    ovulation = Color(0xFF6FD4B9),
    intimacy = Color(0xFFF497B4),
    proposalAccepted = Color(0xFFCBA6F5),
    proposalDeclined = Color(0xFF8A8078),
    solo = Color(0xFFCBA6F5),
    warmBackground = Color(0xFF201C18),
    warmSurface = Color(0xFF2A2420),
    gradientTop = Color(0xFF4A211A),
    gradientBottom = Color(0xFF120D0B),
    menstrual = Color(0xFFE0536B),
    follicular = Color(0xFF7C8CB8),
    lhPeak = Color(0xFFF7CB45),
    ovulatory = Color(0xFF2F9DA6),
    luteal = Color(0xFF8B5FBF),
    textPrimary = Color(0xFFF5EDE8),
    textSecondary = Color(0xFFB8A99C),
    warning = Color(0xFFE0A868)
)

@Composable
fun appColors(): AppColors = if (isSystemInDarkTheme()) DarkAppColors else LightAppColors

fun AppColors.colorFor(phase: CyclePhase): Color = when (phase) {
    CyclePhase.MENSTRUAL -> menstrual
    CyclePhase.FOLLICULAR -> follicular
    CyclePhase.LH_PEAK -> lhPeak
    CyclePhase.OVULATORY -> ovulatory
    CyclePhase.LUTEAL -> luteal
}
