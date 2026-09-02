package com.koshg.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    val warning: Color,
    /** The orgasm-marker star -- a fixed warm gold, deliberately independent of phase/marker
     *  colors so it stays recognizable against any of them. */
    val orgasmStar: Color
)

/** A named color scheme the user picks in Settings -- only the "skin" (accent + backgrounds)
 *  changes between palettes; phase/marker colors carry semantic meaning (menstrual = red,
 *  ovulation = teal, etc.) and stay the same across every palette. */
enum class Palette(val label: String) {
    WINE("Винная"),
    MIDNIGHT("Полночь"),
    FOREST("Лес"),
    PLUM("Слива"),
    GRAPHITE("Графит")
}

val LocalPalette = compositionLocalOf { Palette.WINE }

private data class PaletteSkin(
    val accent: Color,
    val gradientTop: Color,
    val gradientBottom: Color,
    val warmBackground: Color,
    val warmSurface: Color
)

private val LightPaletteSkins = mapOf(
    Palette.WINE to PaletteSkin(
        accent = Color(0xFFFF5C8A),
        gradientTop = Color(0xFFFFEFE1),
        gradientBottom = Color(0xFFFCE1EC),
        warmBackground = Color(0xFFFBF3EA),
        warmSurface = Color(0xFFFFFDF9)
    ),
    Palette.MIDNIGHT to PaletteSkin(
        accent = Color(0xFF5C7CFF),
        gradientTop = Color(0xFFE6ECFF),
        gradientBottom = Color(0xFFE1E8FC),
        warmBackground = Color(0xFFF3F5FB),
        warmSurface = Color(0xFFFDFDFF)
    ),
    Palette.FOREST to PaletteSkin(
        accent = Color(0xFF3FA66B),
        gradientTop = Color(0xFFE4F5E1),
        gradientBottom = Color(0xFFE1F0EC),
        warmBackground = Color(0xFFF2F8EF),
        warmSurface = Color(0xFFFDFFFB)
    ),
    Palette.PLUM to PaletteSkin(
        accent = Color(0xFFA15CFF),
        gradientTop = Color(0xFFF1E6FF),
        gradientBottom = Color(0xFFF5E1EC),
        warmBackground = Color(0xFFF9F3FB),
        warmSurface = Color(0xFFFFFDFF)
    ),
    Palette.GRAPHITE to PaletteSkin(
        accent = Color(0xFF6B7280),
        gradientTop = Color(0xFFECEDEF),
        gradientBottom = Color(0xFFE3E5E8),
        warmBackground = Color(0xFFF5F5F6),
        warmSurface = Color(0xFFFFFFFF)
    )
)

private val DarkPaletteSkins = mapOf(
    Palette.WINE to PaletteSkin(
        accent = Color(0xFFFF8FAE),
        gradientTop = Color(0xFF4A211A),
        gradientBottom = Color(0xFF120D0B),
        warmBackground = Color(0xFF201C18),
        warmSurface = Color(0xFF2A2420)
    ),
    Palette.MIDNIGHT to PaletteSkin(
        accent = Color(0xFF8FA6FF),
        gradientTop = Color(0xFF1A2244),
        gradientBottom = Color(0xFF0B0D12),
        warmBackground = Color(0xFF181A20),
        warmSurface = Color(0xFF202430)
    ),
    Palette.FOREST to PaletteSkin(
        accent = Color(0xFF6FCB93),
        gradientTop = Color(0xFF16321F),
        gradientBottom = Color(0xFF0C120D),
        warmBackground = Color(0xFF181D19),
        warmSurface = Color(0xFF212820)
    ),
    Palette.PLUM to PaletteSkin(
        accent = Color(0xFFC08FFF),
        gradientTop = Color(0xFF2E1A44),
        gradientBottom = Color(0xFF120B14),
        warmBackground = Color(0xFF1C1820),
        warmSurface = Color(0xFF26202C)
    ),
    Palette.GRAPHITE to PaletteSkin(
        accent = Color(0xFF9CA3AF),
        gradientTop = Color(0xFF262830),
        gradientBottom = Color(0xFF101114),
        warmBackground = Color(0xFF1A1B1E),
        warmSurface = Color(0xFF232428)
    )
)

/** The base field values (everything a palette doesn't override: phase/marker/text colors) --
 *  every palette's [AppColors] is this template with its own [PaletteSkin] copied in. */
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
    menstrual = Color(0xFFEF5D80),
    follicular = Color(0xFF6E8FDB),
    lhPeak = Color(0xFFFFC107),
    ovulatory = Color(0xFF1FB8AC),
    luteal = Color(0xFFA262E0),
    textPrimary = Color(0xFF2A211C),
    textSecondary = Color(0xFF8A7A6E),
    warning = Color(0xFFB8722B),
    orgasmStar = Color(0xFFE0A500)
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
    warning = Color(0xFFE0A868),
    orgasmStar = Color(0xFFFFC94D)
)

/** The swatch color shown for [palette] in the Settings picker, independent of the currently
 *  active palette. */
fun Palette.previewAccent(dark: Boolean): Color =
    (if (dark) DarkPaletteSkins else LightPaletteSkins).getValue(this).accent

@Composable
fun appColors(): AppColors {
    val palette = LocalPalette.current
    val dark = isSystemInDarkTheme()
    val base = if (dark) DarkAppColors else LightAppColors
    val skin = (if (dark) DarkPaletteSkins else LightPaletteSkins).getValue(palette)
    return base.copy(
        accent = skin.accent,
        gradientTop = skin.gradientTop,
        gradientBottom = skin.gradientBottom,
        warmBackground = skin.warmBackground,
        warmSurface = skin.warmSurface
    )
}

fun AppColors.colorFor(phase: CyclePhase): Color = when (phase) {
    CyclePhase.MENSTRUAL -> menstrual
    CyclePhase.FOLLICULAR -> follicular
    CyclePhase.LH_PEAK -> lhPeak
    CyclePhase.OVULATORY -> ovulatory
    CyclePhase.LUTEAL -> luteal
}

/** The phase color to actually paint the calendar with -- the full-saturation phase hue. */
fun AppColors.phaseColor(phase: CyclePhase): Color = colorFor(phase)

private fun AppColors.colorForNext(phase: CyclePhase): Color {
    val order = CyclePhase.entries
    return colorFor(order[(phase.ordinal + 1) % order.size])
}

/** The accent color for "adaptive theme": blends smoothly from the current cycle phase's color
 *  toward the next phase's color as the day progresses through the current phase, so the
 *  FAB/selection accent shifts gradually day to day instead of jumping at each phase boundary.
 *  Falls back to the static [AppColors.accent] with no phase data (e.g. no periods logged yet). */
fun AppColors.adaptiveAccent(todayPhaseProgress: Pair<CyclePhase, Float>?): Color {
    if (todayPhaseProgress == null) return accent
    val (phase, fraction) = todayPhaseProgress
    if (phase == CyclePhase.LH_PEAK) return lhPeak
    return lerp(colorFor(phase), colorForNext(phase), fraction.coerceIn(0f, 1f))
}
