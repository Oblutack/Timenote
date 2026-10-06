package com.oblutack.timenote.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {
    private val accent = Color(0xFF4FA8F9)

    @Test fun plainTextIsUnchanged() =
        assertEquals("hello world", parseMarkdownToAnnotatedString("hello world", accent).text)

    @Test fun mentionIsAnnotatedWithTargetId() {
        val result = parseMarkdownToAnnotatedString("see @[Other note](abc123) now", accent)
        val annotations = result.getStringAnnotations("MENTION", 0, result.length)
        assertEquals(1, annotations.size)
        assertEquals("abc123", annotations.first().item)
    }

    @Test fun boldGetsBoldWeightAndAccentColor() {
        val result = parseMarkdownToAnnotatedString("a **bold** b", accent)
        assertTrue(result.spanStyles.any {
            it.item.fontWeight == FontWeight.Bold && it.item.color == accent
        })
    }

    @Test fun strikethroughSpanIsAdded() {
        val result = parseMarkdownToAnnotatedString("~~gone~~", accent)
        assertTrue(result.spanStyles.any { it.item.textDecoration != null })
    }
}
