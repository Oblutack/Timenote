package com.oblutack.timenote.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Portable colour text for the sync format: "#RRGGBB" in sRGB.
 *
 * Compose's own Color.value encoding (what the database stores today) only means something to
 * Compose, so it must never leak into data that other platforms, such as iOS or a web app, read.
 */
fun Color.toRgbHex(): String {
    val rgb = toArgb() and 0xFFFFFF
    return "#" + rgb.toString(16).uppercase().padStart(6, '0')
}

/** Parses "#RRGGBB" or "RRGGBB" (any case) into an opaque colour, or null if the text is not valid. */
fun parseRgbHex(text: String): Color? {
    val digits = text.removePrefix("#")
    if (digits.length != 6 || digits.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return Color(0xFF000000L or digits.toLong(16))
}
