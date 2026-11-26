package com.oblutack.timenote.core

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionHeaderTest {
    private val utc = TimeZone.UTC
    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()
    private fun header(end: String, active: Int, pause: Int, year: Int = 2026) = sessionHeader(at(end), active, pause, utc, year)

    @Test fun theStartIsTheEndMinusFocusAndBreaks() {
        // 41 min 10 s of focus and 6 min 50 s of breaks: 48 minutes in all, ending 7:28 PM
        val h = header("2026-11-25T19:28:00Z", active = 41 * 60 + 10, pause = 6 * 60 + 50)
        assertEquals("Wed 25 Nov", h.dateText)
        assertEquals("6:40 – 7:28 PM", h.timeRangeText)
        assertEquals("Wed 25 Nov · 6:40 – 7:28 PM", h.oneLine)
    }

    @Test fun whenTheStartIsBeforeNoonAndTheEndAfterBothAreNamed() {
        val h = header("2026-11-25T12:10:00Z", active = 50 * 60, pause = 0)
        assertEquals("11:20 AM – 12:10 PM", h.timeRangeText)
    }

    @Test fun midnightAndNoonAreTwelve() {
        assertEquals("12:00 – 12:30 AM", header("2026-11-25T00:30:00Z", 30 * 60, 0).timeRangeText)
        assertEquals("12:00 – 12:45 PM", header("2026-11-25T12:45:00Z", 45 * 60, 0).timeRangeText)
    }

    @Test fun aSessionThatCrossedMidnightSaysSoAndIsDatedByItsStart() {
        val h = header("2026-11-26T00:10:00Z", active = 30 * 60, pause = 0)
        assertEquals("Wed 25 Nov", h.dateText)
        assertEquals("11:40 PM – 12:10 AM (next day)", h.timeRangeText)
    }

    @Test fun theYearIsShownOnlyWhenItIsNotThisYear() {
        assertEquals("Tue 25 Nov 2025", header("2025-11-25T19:28:00Z", 600, 0, year = 2026).dateText)
        assertEquals("Wed 25 Nov", header("2026-11-25T19:28:00Z", 600, 0, year = 2026).dateText)
    }

    @Test fun aSessionWithoutTimeStartsWhereItEnds() {
        assertEquals("7:28 – 7:28 PM", header("2026-11-25T19:28:00Z", 0, 0).timeRangeText)
    }

    @Test fun theLocalTimeZoneIsUsed() {
        val berlin = TimeZone.of("Europe/Berlin") // UTC+1 in November
        val h = sessionHeader(at("2026-11-25T19:28:00Z"), 48 * 60, 0, berlin, 2026)
        assertEquals("7:40 – 8:28 PM", h.timeRangeText)
    }
}
