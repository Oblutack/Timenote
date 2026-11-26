package com.oblutack.timenote.feature_history

import com.oblutack.timenote.feature_history.domain.DateFilter
import com.oblutack.timenote.feature_history.presentation.dateFilterLabel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterLabelTest {
    @Test fun theDateChipSaysWhatIsChosen() {
        assertEquals("Date", dateFilterLabel(DateFilter.Any))
        assertEquals("Today", dateFilterLabel(DateFilter.Today))
        assertEquals("Last 7 days", dateFilterLabel(DateFilter.Last7Days))
        assertEquals("Last 30 days", dateFilterLabel(DateFilter.Last30Days))
        assertEquals("This month", dateFilterLabel(DateFilter.ThisMonth))
        assertEquals("Nov 25", dateFilterLabel(DateFilter.Day(LocalDate(2026, 11, 25))))
    }
}
