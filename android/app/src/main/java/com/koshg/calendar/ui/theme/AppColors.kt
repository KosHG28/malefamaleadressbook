package com.koshg.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
     *  colors so it stays recognizable against any of them, muted or vivid. */
    val orgasmStar: Color
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
    // Brighter/more saturated than the dark-theme phase palette below -- the original shared
    // values were tuned to pop against a near-black background and read as muddy/dark here
    // against the pale cream/pink gradient, even with "vivid colors" on.
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

@Composable
fun appColors(): AppColors = if (isSystemInDarkTheme()) DarkAppColors else LightAppColors

fun AppColors.colorFor(phase: CyclePhase): Color = when (phase) {
    CyclePhase.MENSTRUAL -> menstrual
    CyclePhase.FOLLICULAR -> follicular
    CyclePhase.LH_PEAK -> lhPeak
    CyclePhase.OVULATORY -> ovulatory
    CyclePhase.LUTEAL -> luteal
}

/** Cheap desaturation: blends this color toward its own perceived gray by [amount] (0 = unchanged,
 *  1 = fully gray), which reads as a calmer/less saturated version at roughly the same lightness. */
fun Color.desaturated(amount: Float): Color {
    val gray = 0.299f * red + 0.587f * green + 0.114f * blue
    return lerp(this, Color(gray, gray, gray, alpha), amount.coerceIn(0f, 1f))
}

/** How much [colorFor] is desaturated when "vivid colors" is off (the calmer default). */
private const val MUTED_PHASE_DESATURATION = 0.35f

/** The phase color to actually paint with -- full-saturation [colorFor] when [vivid] is on
 *  (the old, brighter look, opt-in), or a calmer desaturated variant by default. */
fun AppColors.phaseColor(phase: CyclePhase, vivid: Boolean): Color {
    val base = colorFor(phase)
    return if (vivid) base else base.desaturated(MUTED_PHASE_DESATURATION)
}

/** The day-cell fill color for [phase] at [progress] through it (0 = the phase's first day, 1 =
 *  its last), blended toward the *next* phase's color so adjacent days never jump abruptly at a
 *  phase boundary -- a phase's last day already reads almost as the next phase's color, and that
 *  next phase's first day (progress 0) continues from exactly there, same trick as
 *  [adaptiveAccent]. LH_PEAK is a single day and stays pure rather than blending, since it's
 *  meant to stand out as a spike, not a transition. Respects the vivid/muted choice like
 *  [phaseColor]. */
fun AppColors.blendedPhaseColor(phase: CyclePhase, progress: Float, vivid: Boolean): Color {
    if (phase == CyclePhase.LH_PEAK) return phaseColor(phase, vivid)
    val order = CyclePhase.entries
    val next = order[(phase.ordinal + 1) % order.size]
    return lerp(phaseColor(phase, vivid), phaseColor(next, vivid), progress.coerceIn(0f, 1f))
}

/** Slightly darker/lighter variant of this color, for a subtle within-run gradient across a
 *  contiguous span of same-phase calendar days -- fraction 0 = run start (darker), 1 = run end
 *  (lighter), so a run visually "builds" toward its end (e.g. approaching ovulation). */
fun Color.runGradientShade(fraction: Float): Color {
    val dark = lerp(this, Color.Black, 0.16f)
    val light = lerp(this, Color.White, 0.20f)
    return lerp(dark, light, fraction.coerceIn(0f, 1f))
}

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
