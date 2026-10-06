package com.oblutack.timenote.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {
    @Test fun zero() = assertEquals("00:00:00", formatDuration(0))
    @Test fun secondsMinutesHours() = assertEquals("01:01:01", formatDuration(3661))
    @Test fun hoursAreNotWrappedAt24() = assertEquals("100:00:00", formatDuration(360_000))
    @Test fun negativeIsClampedToZero() = assertEquals("00:00:00", formatDuration(-5))
}
