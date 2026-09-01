package com.koshg.calendar.data

enum class Initiator(val storageValue: String, val label: String) {
    ME("me", "Я"),
    PARTNER("partner", "Партнёр");

    companion object {
        fun fromStorage(value: String): Initiator =
            entries.firstOrNull { it.storageValue == value } ?: ME
    }
}
