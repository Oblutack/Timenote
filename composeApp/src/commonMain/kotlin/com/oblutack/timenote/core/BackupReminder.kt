package com.oblutack.timenote.core

/** After this long without a backup (and without cloud sync), the app suggests making one. */
const val BACKUP_REMINDER_DAYS = 30
private const val DAY_MS = 24L * 60 * 60 * 1000
private const val REMINDER_MS = BACKUP_REMINDER_DAYS * DAY_MS

/**
 * Whether to suggest exporting a backup now. Only for people who keep their timenotes on this device alone:
 * with cloud sync on they already have a copy, and with nothing recorded there is nothing to protect.
 *
 * The time that counts is the last backup; before the first backup it is the oldest timenote (the data has been
 * unprotected since then). After the user dismisses the suggestion it stays away for another [BACKUP_REMINDER_DAYS].
 */
fun backupReminderDue(
    now: Long,
    lastBackupAt: Long?,
    oldestTimenoteAt: Long?,
    dismissedAt: Long?,
    syncOn: Boolean,
    timenoteCount: Int
): Boolean {
    if (syncOn || timenoteCount == 0) return false
    val unprotectedSince = lastBackupAt ?: oldestTimenoteAt ?: return false
    if (now - unprotectedSince < REMINDER_MS) return false
    if (dismissedAt != null && now - dismissedAt < REMINDER_MS) return false
    return true
}

/** "Last backup: 34 days ago" for Settings. */
fun describeLastBackup(now: Long, lastBackupAt: Long?): String {
    if (lastBackupAt == null) return "No backup made yet"
    val elapsed = (now - lastBackupAt).coerceAtLeast(0)
    val days = (elapsed / DAY_MS).toInt()
    return when {
        days < 1 -> "Last backup: today"
        days == 1 -> "Last backup: yesterday"
        else -> "Last backup: $days days ago"
    }
}

/** The sentence in the reminder shown on the History screen. */
fun describeBackupReminder(now: Long, lastBackupAt: Long?): String =
    if (lastBackupAt == null) "Your timenotes are only on this phone, and you have not made a backup yet."
    else "It has been ${((now - lastBackupAt).coerceAtLeast(0) / DAY_MS).toInt()} days since your last backup."
