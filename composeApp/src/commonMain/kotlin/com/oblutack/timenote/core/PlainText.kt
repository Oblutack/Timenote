package com.oblutack.timenote.core

private val MENTION = Regex("@\\[(.*?)\\]\\((.*?)\\)")
private val LINK = Regex("\\[(.*?)\\]\\((.*?)\\)")
private val LINE_START = Regex("^\\s*(#{1,6}\\s+|>\\s+|[-*+]\\s+\\[[ xX]\\]\\s+|[-*+]\\s+|\\d+\\.\\s+)")

/** A tick box at the start of a line, as the small symbol it stands for. */
private fun tickBoxSymbol(line: String): String? {
    val match = Regex("^\\s*[-*+]\\s+\\[([ xX])\\]\\s+").find(line) ?: return null
    return if (match.groupValues[1] == " ") "☐ " else "☑ "
}

/**
 * Markdown as words, for a preview in a list: no `#`, `**`, `_`, `~~`, bullets or link syntax; a mention `@[Title](id)` or a
 * link `[text](url)` is just its words; a tick box is ☐ or ☑. Lines are joined with one space and spaces are collapsed.
 */
fun plainText(markdown: String): String =
    markdown.lines()
        .map { rawLine ->
            val symbol = tickBoxSymbol(rawLine)
            val withoutMarker = rawLine.replace(LINE_START, "")
            (symbol ?: "") + withoutMarker
        }
        .joinToString(" ") { line ->
            line
                .replace(MENTION) { it.groupValues[1] }
                .replace(LINK) { it.groupValues[1] }
                .replace("**", "")
                .replace("~~", "")
                .replace(Regex("(?<![\\p{L}\\p{N}])_+|_+(?![\\p{L}\\p{N}])"), "")
        }
        .replace(Regex("\\s+"), " ")
        .trim()
