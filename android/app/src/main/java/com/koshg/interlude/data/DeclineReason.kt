package com.koshg.interlude.data

import androidx.annotation.StringRes
import com.koshg.interlude.R

/**
 * Why a proposal was turned down, as a stored code rather than the words on screen.
 *
 * It used to be whatever text the chip happened to say, and the suggestion engine looked for the
 * substring "устал" to spot a fatigue pattern. That already missed anything phrased differently,
 * and once the app speaks more than one language it cannot work at all: a reason logged in
 * English would never match a Russian keyword. Storing the code keeps the pattern reliable in
 * any language, and lets an entry logged in one language read correctly after the user switches.
 *
 * [ProposalEntry.declineReason] holds either one of these [storageValue]s or, when the user typed
 * their own wording, that free text -- [fromStorage] returns null for the latter.
 */
enum class DeclineReason(val storageValue: String, @StringRes val labelRes: Int) {
    FATIGUE("fatigue", R.string.decline_reason_fatigue),
    MOOD("mood", R.string.decline_reason_mood),
    WELLBEING("wellbeing", R.string.decline_reason_wellbeing);

    companion object {
        fun fromStorage(value: String): DeclineReason? =
            entries.firstOrNull { it.storageValue == value }
    }
}
