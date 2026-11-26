package com.oblutack.timenote.feature_history.domain

/** How much one session takes in the list. */
enum class HistoryDensity(val key: String) {
    /** One line of title and one of facts: a busy week fits on a screen. */
    Compact("compact"),
    /** The large cards with a preview of the description. */
    Comfortable("comfortable");

    companion object {
        fun fromKey(key: String?): HistoryDensity = entries.firstOrNull { it.key == key } ?: Compact
    }
}
