package com.oblutack.timenote.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PlainTextTest {
    @Test fun headingsLoseTheirHashes() = assertEquals("Big heading and text", plainText("# Big heading\nand text"))

    @Test fun boldItalicAndStrikethroughMarksAreRemoved() =
        assertEquals("bold italic gone", plainText("**bold** _italic_ ~~gone~~"))

    @Test fun mentionsAndLinksAreJustTheirWords() =
        assertEquals("see Weekly review and the docs", plainText("see @[Weekly review](abc123) and [the docs](https://x.y)"))

    @Test fun bulletsAndNumbersAreDropped() = assertEquals("one two three", plainText("- one\n* two\n3. three"))

    @Test fun tickBoxesBecomeSymbols() = assertEquals("☐ buy milk ☑ call Sam", plainText("- [ ] buy milk\n- [x] call Sam"))

    @Test fun quotesLoseTheirMarker() = assertEquals("quoted words", plainText("> quoted words"))

    @Test fun underscoresInsideWordsStay() = assertEquals("keep my_file_name here", plainText("keep my_file_name _here_"))

    @Test fun linesAreJoinedAndSpacesCollapsed() = assertEquals("a b c", plainText("a\n\n  b   \n c"))

    @Test fun emptyStaysEmpty() = assertEquals("", plainText(""))
}
