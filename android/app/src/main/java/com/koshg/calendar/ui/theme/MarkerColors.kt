package com.koshg.calendar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The kinds of day-cell marker a calendar ring can stand for. Distinct from the phase colors,
 * which carry fixed medical meaning -- these are the user's own logged activity, and their colors
 * are user-overridable in Settings (see [MarkerColors]).
 */
enum class MarkerKind(val label: String) {
    SEX("Секс"),
    PROPOSAL_ACCEPTED("Предложение принято"),
    PROPOSAL_DECLINED("Предложение отклонено"),
    PROPOSAL_PENDING("Предложение ожидает"),
    SOLO("Соло")
}

// Defaults. The ring is drawn on top of a phase capsule whose color changes day to day, so what
// matters most isn't hue but *lightness*: each of these is clearly darker or clearly brighter than
// all four phase fills (pink/red menstrual, blue follicular, teal ovulatory, purple luteal), and
// none sits in a phase's own hue lane.
val MARKER_DEFAULT_SEX = Color(0xFFB0143C) // кармин
val MARKER_DEFAULT_ACCEPTED = Color(0xFF7CC020) // лайм
val MARKER_DEFAULT_DECLINED = Color(0xFF5A5A5A) // графит
val MARKER_DEFAULT_PENDING = Color(0xFFE8A020) // янтарь
val MARKER_DEFAULT_SOLO = Color(0xFF2A3E9E) // индиго

/**
 * The five marker colors currently in force. One value per kind rather than a light/dark pair:
 * every option is chosen to read on any phase fill in either theme, so the user's pick doesn't
 * silently become something else when the theme flips.
 */
@Immutable
data class MarkerColors(
    val sex: Color = MARKER_DEFAULT_SEX,
    val proposalAccepted: Color = MARKER_DEFAULT_ACCEPTED,
    val proposalDeclined: Color = MARKER_DEFAULT_DECLINED,
    val proposalPending: Color = MARKER_DEFAULT_PENDING,
    val solo: Color = MARKER_DEFAULT_SOLO
)

fun MarkerColors.colorFor(kind: MarkerKind): Color = when (kind) {
    MarkerKind.SEX -> sex
    MarkerKind.PROPOSAL_ACCEPTED -> proposalAccepted
    MarkerKind.PROPOSAL_DECLINED -> proposalDeclined
    MarkerKind.PROPOSAL_PENDING -> proposalPending
    MarkerKind.SOLO -> solo
}

fun MarkerColors.withColor(kind: MarkerKind, color: Color): MarkerColors = when (kind) {
    MarkerKind.SEX -> copy(sex = color)
    MarkerKind.PROPOSAL_ACCEPTED -> copy(proposalAccepted = color)
    MarkerKind.PROPOSAL_DECLINED -> copy(proposalDeclined = color)
    MarkerKind.PROPOSAL_PENDING -> copy(proposalPending = color)
    MarkerKind.SOLO -> copy(solo = color)
}

/** The swatches offered in Settings. Deliberately a curated list rather than a free color wheel:
 *  every entry here clears the phase fills by lightness, so no pick can end up invisible on the
 *  calendar. Teal, mid-blue and lavender are absent on purpose -- those are the phases' own. */
val MARKER_COLOR_PRESETS: List<Color> = listOf(
    Color(0xFFB0143C), // кармин
    Color(0xFFE8402A), // алый
    Color(0xFFE8A020), // янтарь
    Color(0xFFD8C81E), // жёлтый
    Color(0xFF7CC020), // лайм
    Color(0xFF1E8A4C), // изумруд
    Color(0xFF2A3E9E), // индиго
    Color(0xFFC2189E), // маджента
    Color(0xFF5A5A5A), // графит
    Color(0xFF1A1A1A) // почти чёрный
)

/** Provided once at the top of the app (see CalendarScreen) so day cells and the marker legend
 *  resolve the same, possibly user-customized, colors without threading them through every call. */
val LocalMarkerColors = compositionLocalOf { MarkerColors() }
