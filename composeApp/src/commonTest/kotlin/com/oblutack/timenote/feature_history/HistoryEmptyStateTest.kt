package com.oblutack.timenote.feature_history

import com.oblutack.timenote.feature_history.presentation.HistoryEmpty
import com.oblutack.timenote.feature_history.presentation.historyEmptyReason
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryEmptyStateTest {
    @Test fun noFilterAtAllMeansNoSessionsYet() =
        assertEquals(HistoryEmpty.NothingYet, historyEmptyReason(dayPicked = false, hasSearchOrFilter = false))

    @Test fun aSearchOrTagFilterMeansNoMatches() =
        assertEquals(HistoryEmpty.NoMatches, historyEmptyReason(dayPicked = false, hasSearchOrFilter = true))

    @Test fun aPickedDayIsTheNarrowestFilterAndWins() {
        assertEquals(HistoryEmpty.NothingOnThatDay, historyEmptyReason(dayPicked = true, hasSearchOrFilter = false))
        assertEquals(HistoryEmpty.NothingOnThatDay, historyEmptyReason(dayPicked = true, hasSearchOrFilter = true))
    }
}
