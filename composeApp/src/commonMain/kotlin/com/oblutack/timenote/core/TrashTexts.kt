package com.oblutack.timenote.core

/** How long deleted sessions and folders stay in the trash before they are removed for good. */
const val TRASH_RETENTION_DAYS = 30
const val TRASH_RETENTION_MS = TRASH_RETENTION_DAYS * 24L * 60 * 60 * 1000

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/**
 * "Deleted 3 days ago · 27 days left" for an item in the trash. [deletedAt] is null for rows that were put in the trash
 * before the deletion time was recorded; those get no countdown (and are never removed automatically).
 */
fun describeTrashItem(now: Long, deletedAt: Long?): String {
    if (deletedAt == null) return "In the trash"
    val elapsed = (now - deletedAt).coerceAtLeast(0)
    val ago = when {
        elapsed < MINUTE_MS -> "just now"
        elapsed < HOUR_MS -> "${elapsed / MINUTE_MS} min ago"
        elapsed < DAY_MS -> "${elapsed / HOUR_MS} h ago"
        elapsed < 2 * DAY_MS -> "yesterday"
        else -> "${elapsed / DAY_MS} days ago"
    }
    val daysLeft = TRASH_RETENTION_DAYS - (elapsed / DAY_MS).toInt()
    val left = when {
        daysLeft <= 0 -> "removed soon"
        daysLeft == 1 -> "1 day left"
        else -> "$daysLeft days left"
    }
    return "Deleted $ago · $left"
}
