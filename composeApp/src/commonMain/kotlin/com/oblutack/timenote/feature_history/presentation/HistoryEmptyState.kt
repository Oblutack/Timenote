package com.oblutack.timenote.feature_history.presentation

/** Why the list of sessions is empty: each reason says something different and offers a different way out. */
enum class HistoryEmpty { NothingYet, NothingOnThatDay, NoMatches }

/**
 * A picked day wins over a search (the day filter is the narrowest), then a search or tag filter, otherwise there simply
 * are no sessions yet.
 */
fun historyEmptyReason(dayPicked: Boolean, hasSearchOrFilter: Boolean): HistoryEmpty = when {
    dayPicked -> HistoryEmpty.NothingOnThatDay
    hasSearchOrFilter -> HistoryEmpty.NoMatches
    else -> HistoryEmpty.NothingYet
}
