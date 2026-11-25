package com.oblutack.timenote.feature_history

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_history.domain.identityColor
import com.oblutack.timenote.ui.theme.DarkTnColors
import com.oblutack.timenote.ui.theme.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdentityColorTest {
    private fun tag(id: String, color: Color) = TimenoteFolder(id, id, null, 0, color)
    private val grey = Color(0xFF123456)

    @Test fun aSessionTakesTheColourOfItsFirstTag() {
        assertEquals(Palette.Green, listOf(tag("a", Palette.Green), tag("b", Palette.Red)).identityColor(grey))
    }

    @Test fun theOrderOfTheTagsDecides() {
        assertEquals(Palette.Red, listOf(tag("b", Palette.Red), tag("a", Palette.Green)).identityColor(grey))
    }

    @Test fun withoutATagItIsTheNeutralColour() {
        assertEquals(grey, emptyList<TimenoteFolder>().identityColor(grey))
    }

    @Test fun theNeutralAndPauseColoursAreNeitherTheAccentNorEachOther() {
        val c = DarkTnColors
        assertNotEquals(c.accent, c.untagged, "an untagged session must not look like an action")
        assertNotEquals(c.accent, c.pause)
        assertNotEquals(c.untagged, c.pause)
        // a pause must not be mistaken for the default Fitness tag either
        assertNotEquals(Palette.Orange, c.pause)
    }
}
