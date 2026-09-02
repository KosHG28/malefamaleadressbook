package com.koshg.calendar.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Self-rolled width breakpoints mirroring Material's window-size-class thresholds (600dp /
 * 840dp), derived from a [androidx.compose.foundation.layout.BoxWithConstraints] read rather than
 * a platform WindowSizeClass API -- that already reacts correctly to rotation and to a Fold's
 * hinge state changing (folded vs. unfolded) without an Activity reference to plumb through.
 */
internal enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

internal fun windowWidthClassOf(widthDp: Int): WindowWidthClass = when {
    widthDp < 600 -> WindowWidthClass.COMPACT
    widthDp < 840 -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}

/**
 * Full-bleed list screens (Settings/History/Year overview) read fine edge-to-edge on a phone but
 * sprawl uncomfortably wide once unfolded or on a tablet -- caps the content column at a
 * comfortable reading width instead. The caller centers it (e.g. inside a [Box] with
 * `contentAlignment = Alignment.TopCenter`); the header row above stays full-width either way.
 */
internal fun Modifier.adaptiveContentWidth(): Modifier = this.widthIn(max = 640.dp)
