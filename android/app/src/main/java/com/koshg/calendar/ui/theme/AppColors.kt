package com.koshg.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val period: Color,
    val periodContainer: Color,
    val fertile: Color,
    val fertileContainer: Color,
    val ovulation: Color,
    val intimacy: Color,
    val proposal: Color
)

private val LightAppColors = AppColors(
    period = Color(0xFFE0577A),
    periodContainer = Color(0xFFFCE1E9),
    fertile = Color(0xFF2FA88E),
    fertileContainer = Color(0xFFDCF3EC),
    ovulation = Color(0xFF2FA88E),
    intimacy = Color(0xFFE0577A),
    proposal = Color(0xFF9B6BD6)
)

private val DarkAppColors = AppColors(
    period = Color(0xFFF497B4),
    periodContainer = Color(0xFF4A2431),
    fertile = Color(0xFF6FD4B9),
    fertileContainer = Color(0xFF1C3A33),
    ovulation = Color(0xFF6FD4B9),
    intimacy = Color(0xFFF497B4),
    proposal = Color(0xFFCBA6F5)
)

@Composable
fun appColors(): AppColors = if (isSystemInDarkTheme()) DarkAppColors else LightAppColors
