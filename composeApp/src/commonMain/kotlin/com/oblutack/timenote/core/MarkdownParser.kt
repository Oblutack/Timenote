package com.oblutack.timenote.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

fun parseMarkdownToAnnotatedString(text: String, accentColor: Color): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        // 1. HEADERS (# H1, ## H2, ### H3)
        Regex("(?m)^# (.*)$").findAll(text).forEach { match ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White), match.range.first, match.range.last + 1)
        }
        Regex("(?m)^## (.*)$").findAll(text).forEach { match ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White), match.range.first, match.range.last + 1)
        }
        Regex("(?m)^### (.*)$").findAll(text).forEach { match ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White), match.range.first, match.range.last + 1)
        }

        // 2. BOLD & ITALIC & STRIKETHROUGH
        Regex("\\*\\*(.*?)\\*\\*").findAll(text).forEach { match ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor), match.range.first, match.range.last + 1)
        }
        Regex("_(.*?)_").findAll(text).forEach { match ->
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), match.range.first, match.range.last + 1)
        }
        Regex("~~(.*?)~~").findAll(text).forEach { match ->
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), match.range.first, match.range.last + 1)
        }

        // 3. SMART MENTIONS @[Title](ID)
        Regex("@\\[(.*?)\\]\\((.*?)\\)").findAll(text).forEach { match ->
            val id = match.groupValues[2]
            addStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            addStringAnnotation("MENTION", id, match.range.first, match.range.last + 1)
        }

        // --- CLEANUP (Hide syntax symbols) ---
        Regex("(\\*\\*|_|(?m)^# |(?m)^## |(?m)^### |~~)").findAll(text).forEach { match ->
            addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), match.range.first, match.range.last + 1)
        }
        Regex("@\\[").findAll(text).forEach { match ->
            addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), match.range.first, match.range.last + 1)
        }
        Regex("\\]\\(.*?\\)").findAll(text).forEach { match ->
            addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), match.range.first, match.range.last + 1)
        }
    }
}