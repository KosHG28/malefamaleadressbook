package com.koshg.interlude.util

import android.content.Context
import androidx.annotation.ArrayRes
import com.koshg.interlude.R
import java.time.LocalDate

/** Several short, general-purpose suggestions per phase for a partner reading the selected day's
 *  phase -- deliberately generic (not tailored to this couple's own logged data), since it's
 *  meant as a quick nudge, not a diagnosis. Several phrases per phase, rather than one fixed
 *  line, so repeatedly checking the tip (or the phase legend, see CalendarScreen's PhaseLegend)
 *  doesn't always show the exact same wording. */
@ArrayRes
private fun CyclePhase.tipsArrayRes(): Int = when (this) {
    CyclePhase.MENSTRUAL -> R.array.phase_tips_menstrual
    CyclePhase.FOLLICULAR -> R.array.phase_tips_follicular
    CyclePhase.OVULATORY -> R.array.phase_tips_ovulatory
    CyclePhase.LUTEAL -> R.array.phase_tips_luteal
}

/** Deterministically picks one of the phrases for [phase] using [date] as the seed -- stable
 *  within a single day (so re-opening the tip or the legend mid-day doesn't shuffle the wording
 *  underfoot), but varies from day to day.
 *
 *  Each language's pool may hold a different number of phrases, so the index is taken modulo the
 *  pool that is actually loaded rather than a fixed count. */
fun Context.phaseTipForMen(phase: CyclePhase, date: LocalDate = LocalDate.now()): String {
    val pool = resources.getStringArray(phase.tipsArrayRes())
    if (pool.isEmpty()) return ""
    val seed = date.toEpochDay() + phase.ordinal
    val index = ((seed % pool.size) + pool.size) % pool.size
    return pool[index.toInt()]
}
