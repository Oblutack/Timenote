package com.oblutack.timenote.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun parseMarkdownToAnnotatedString(text: String, accentColor: Color): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        // 1. BOLD (**text**)
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        boldRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(fontWeight = FontWeight.Bold, color = accentColor), // Makes bold text pop with the tag color!
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 2. ITALIC (_text_)
        val italicRegex = Regex("_(.*?)_")
        italicRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(fontStyle = FontStyle.Italic),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 3. HEADERS (# text)
        val headerRegex = Regex("(?m)^# (.*)$")
        headerRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 4. STRIKETHROUGH (~~text~~)
        val strikeRegex = Regex("~~(.*?)~~")
        strikeRegex.findAll(text).forEach { match ->
            addStyle(
                style = SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // 5. NEW: @MENTIONS (@Tag)
        // Matches an '@' followed by any letters/numbers (stops at a space)
        val mentionRegex = Regex("@\\[(.*?)\\]\\((.*?)\\)")
        mentionRegex.findAll(text).forEach { match ->
            val id = match.groupValues[2]

            // THE FIX: Removed the ugly sharp background, just keep it Bold and Colored!
            addStyle(
                style = SpanStyle(color = accentColor, fontWeight = FontWeight.Bold),
                start = match.range.first,
                end = match.range.last + 1
            )

            addStringAnnotation(
                tag = "MENTION",
                annotation = id,
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // --- CLEANUP ---
        // Hide the actual markdown symbols (**, _, #) by making them transparent!
        val symbolsRegex = Regex("(\\*\\*|_|(?m)^# |~~)")
        symbolsRegex.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), match.range.first, match.range.last + 1)
        }

        // Hide the smart link brackets "@[" and "](ID)"
        val linkStartRegex = Regex("@\\[")
        linkStartRegex.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), match.range.first, match.range.last + 1)
        }
        val linkEndRegex = Regex("\\]\\(.*?\\)")
        linkEndRegex.findAll(text).forEach { match ->
            addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), match.range.first, match.range.last + 1)
        }
    }
}