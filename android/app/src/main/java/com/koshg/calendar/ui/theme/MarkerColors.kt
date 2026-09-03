package com.koshg.calendar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The kinds of day-cell marker a calendar ring can stand for. Distinct from the phase colors,
 * which carry fixed medical meaning -- these are the user's own logged activity, and their colors
 * are user-overridable in Settings (see [MarkerPresets]).
 */
enum class MarkerKind(val label: String) {
    SEX("Секс"),
    PROPOSAL_ACCEPTED("Предложение принято"),
    PROPOSAL_DECLINED("Предложение отклонено"),
    PROPOSAL_PENDING("Предложение ожидает"),
    SOLO("Соло")
}

/**
 * A marker color the user can pick, as a light/dark pair rather than one value.
 *
 * The ring is drawn on top of a phase capsule, and those capsules are near-pastel in light theme
 * but deep in dark theme -- a single value tuned for one reads wrong in the other (the first cut
 * of this used one value each, and the dark-tuned carmine/indigo/graphite landed as heavy ink
 * blots on the light theme's pale fills). The user still picks once: the pick is the *preset*,
 * and the theme decides which of its two values is painted.
 */
enum class MarkerPreset(private val light: Color, private val dark: Color) {
    CARMINE(Color(0xFFC2185B), Color(0xFFF0537A)),
    SCARLET(Color(0xFFE04A2F), Color(0xFFFF8163)),
    AMBER(Color(0xFFD98A00), Color(0xFFFFC14D)),
    YELLOW(Color(0xFFB79A00), Color(0xFFF0DC4E)),
    LIME(Color(0xFF5FA015), Color(0xFFA6E04A)),
    EMERALD(Color(0xFF16794A), Color(0xFF45C98A)),
    INDIGO(Color(0xFF3A4FB5), Color(0xFF8A9BFF)),
    MAGENTA(Color(0xFFB01A96), Color(0xFFF062D8)),
    GRAPHITE(Color(0xFF5A5A5A), Color(0xFFA8A8A8)),
    INK(Color(0xFF24262B), Color(0xFFD8D8D8));

    fun color(dark: Boolean): Color = if (dark) this.dark else this.light
}

/** Which preset each marker kind is set to -- the user's actual choice, independent of theme. */
@Immutable
data class MarkerPresets(
    val sex: MarkerPreset = MarkerPreset.CARMINE,
    val proposalAccepted: MarkerPreset = MarkerPreset.LIME,
    val proposalDeclined: MarkerPreset = MarkerPreset.GRAPHITE,
    val proposalPending: MarkerPreset = MarkerPreset.AMBER,
    val solo: MarkerPreset = MarkerPreset.INDIGO
) {
    /** The concrete colors to paint with under the current theme. */
    fun resolve(dark: Boolean): MarkerColors = MarkerColors(
        sex = sex.color(dark),
        proposalAccepted = proposalAccepted.color(dark),
        proposalDeclined = proposalDeclined.color(dark),
        proposalPending = proposalPending.color(dark),
        solo = solo.color(dark)
    )
}

fun MarkerPresets.presetFor(kind: MarkerKind): MarkerPreset = when (kind) {
    MarkerKind.SEX -> sex
    MarkerKind.PROPOSAL_ACCEPTED -> proposalAccepted
    MarkerKind.PROPOSAL_DECLINED -> proposalDeclined
    MarkerKind.PROPOSAL_PENDING -> proposalPending
    MarkerKind.SOLO -> solo
}

fun MarkerPresets.withPreset(kind: MarkerKind, preset: MarkerPreset): MarkerPresets = when (kind) {
    MarkerKind.SEX -> copy(sex = preset)
    MarkerKind.PROPOSAL_ACCEPTED -> copy(proposalAccepted = preset)
    MarkerKind.PROPOSAL_DECLINED -> copy(proposalDeclined = preset)
    MarkerKind.PROPOSAL_PENDING -> copy(proposalPending = preset)
    MarkerKind.SOLO -> copy(solo = preset)
}

/** The five marker colors already resolved for the current theme -- what the calendar, the marker
 *  legend and the day-agenda rows all paint from, so none of them can disagree. */
@Immutable
data class MarkerColors(
    val sex: Color = MarkerPreset.CARMINE.color(dark = false),
    val proposalAccepted: Color = MarkerPreset.LIME.color(dark = false),
    val proposalDeclined: Color = MarkerPreset.GRAPHITE.color(dark = false),
    val proposalPending: Color = MarkerPreset.AMBER.color(dark = false),
    val solo: Color = MarkerPreset.INDIGO.color(dark = false)
)

fun MarkerColors.colorFor(kind: MarkerKind): Color = when (kind) {
    MarkerKind.SEX -> sex
    MarkerKind.PROPOSAL_ACCEPTED -> proposalAccepted
    MarkerKind.PROPOSAL_DECLINED -> proposalDeclined
    MarkerKind.PROPOSAL_PENDING -> proposalPending
    MarkerKind.SOLO -> solo
}

/** Provided once at the top of the app (see CalendarScreen), already resolved for the active
 *  theme, so nothing downstream has to know about light/dark pairs. */
val LocalMarkerColors = compositionLocalOf { MarkerColors() }
