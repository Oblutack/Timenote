package com.oblutack.timenote.core

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdsAndColorTest {
    @Test fun idsAreUuidShaped() {
        val id = newId()
        assertEquals(36, id.length)
        assertEquals(listOf(8, 4, 4, 4, 12), id.split("-").map { it.length })
    }

    @Test fun idsDoNotRepeat() {
        assertEquals(1_000, List(1_000) { newId() }.toSet().size)
    }

    @Test fun colorToHex() {
        assertEquals("#4FA8F9", Color(0xFF4FA8F9).toRgbHex())
        assertEquals("#000000", Color.Black.toRgbHex())
        assertEquals("#FFFFFF", Color.White.toRgbHex())
        assertEquals("#0000FF", Color(0xFF0000FF).toRgbHex(), "leading zeros are kept")
    }

    @Test fun hexRoundTrips() {
        listOf(0xFF4FA8F9, 0xFFE53935, 0xFF00E5FF, 0xFF000000, 0xFFFFFFFF).forEach {
            val color = Color(it)
            assertEquals(color, parseRgbHex(color.toRgbHex()))
        }
    }

    @Test fun hexParsingAcceptsEitherCaseAndOptionalHash() {
        assertEquals(Color(0xFF4FA8F9), parseRgbHex("4fa8f9"))
        assertEquals(Color(0xFF4FA8F9), parseRgbHex("#4FA8F9"))
    }

    @Test fun invalidHexIsRejected() {
        assertNull(parseRgbHex(""))
        assertNull(parseRgbHex("#12"))
        assertNull(parseRgbHex("#GGGGGG"))
        assertNull(parseRgbHex("#1234567"))
        assertNull(parseRgbHex("#FF4FA8F9"), "alpha is not part of the portable format")
    }

    @Test fun differentColorsGiveDifferentText() {
        assertNotEquals(Color.Red.toRgbHex(), Color.Blue.toRgbHex())
        assertTrue(Color.Red.toRgbHex().startsWith("#"))
    }
}
