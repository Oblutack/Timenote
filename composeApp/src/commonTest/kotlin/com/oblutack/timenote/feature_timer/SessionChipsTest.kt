package com.oblutack.timenote.feature_timer

import com.oblutack.timenote.feature_timer.presentation.SESSION_CHIPS_MAX_TAGS
import com.oblutack.timenote.feature_timer.presentation.visibleTagChips
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionChipsTest {
    @Test fun upToThreeTagsAreShownByName() {
        assertEquals(3, SESSION_CHIPS_MAX_TAGS)
        assertEquals(0 to 0, visibleTagChips(0))
        assertEquals(1 to 0, visibleTagChips(1))
        assertEquals(3 to 0, visibleTagChips(3))
    }

    @Test fun theRestAreCounted() {
        assertEquals(3 to 1, visibleTagChips(4))
        assertEquals(3 to 7, visibleTagChips(10))
    }
}
