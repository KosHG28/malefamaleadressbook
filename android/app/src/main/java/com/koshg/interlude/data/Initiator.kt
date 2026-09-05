package com.koshg.interlude.data

import androidx.annotation.StringRes
import com.koshg.interlude.R

enum class Initiator(val storageValue: String, @StringRes val labelRes: Int) {
    ME("me", R.string.initiator_me),
    PARTNER("partner", R.string.initiator_partner);

    companion object {
        fun fromStorage(value: String): Initiator =
            entries.firstOrNull { it.storageValue == value } ?: ME
    }
}
