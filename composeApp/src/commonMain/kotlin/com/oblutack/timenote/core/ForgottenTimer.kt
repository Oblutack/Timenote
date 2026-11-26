package com.oblutack.timenote.core

/** What the app is asked to open when the "still working?" notification is tapped (see App.kt). */
const val STILL_WORKING_ROUTE = "still_working"

/** A session this long is always worth asking about. */
const val FORGOTTEN_MIN_MILLIS = 3 * 60 * 60 * 1000L

/** How many of the latest sessions say what is typical for this person. */
const val FORGOTTEN_HISTORY_SIZE = 20

/** After "Keep going" the question comes back this much later. */
const val FORGOTTEN_REPEAT_MILLIS = 2 * 60 * 60 * 1000L

/** Trimming is only offered when the last activity is at least this long ago (otherwise there is nothing to trim). */
const val FORGOTTEN_MIN_TRIM_SECONDS = 60

/**
 * When to ask "still working?": after 3 hours, or after twice the typical session when that is longer. The typical session
 * is the median of the latest ones ([recentTotalSeconds], newest first is not required), so a few long days do not move it
 * much, and someone who really works five-hour blocks is not asked at three.
 */
fun forgottenTimerThresholdMillis(recentTotalSeconds: List<Int>): Long {
    val lengths = recentTotalSeconds.filter { it > 0 }.sorted()
    if (lengths.isEmpty()) return FORGOTTEN_MIN_MILLIS
    val middle = lengths.size / 2
    val median = if (lengths.size % 2 == 1) lengths[middle].toDouble() else (lengths[middle - 1] + lengths[middle]) / 2.0
    return maxOf(FORGOTTEN_MIN_MILLIS, (median * 2 * 1000).toLong())
}

/** The "HH:MM:SS" of a timeline entry as seconds (hours may exceed 99), or null when the text is not a clock time. */
fun parseDurationSeconds(text: String): Int? {
    val parts = text.split(":")
    if (parts.size != 3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    if (numbers.any { it < 0 }) return null
    return numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
}

/** A length of time in words for a sentence: "45 min", "3 h", "5 h 12 min". */
fun humanDuration(totalSeconds: Int): String {
    val minutes = totalSeconds.coerceAtLeast(0) / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$minutes min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}
