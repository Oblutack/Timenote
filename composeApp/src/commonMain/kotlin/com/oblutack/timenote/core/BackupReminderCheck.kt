package com.oblutack.timenote.core

import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * The background side of the backup reminder: decides whether the system notification should be sent now.
 * It follows the same rules as the card on the History screen ([backupReminderDue]); "Not now" on the card and an
 * earlier notification both keep the next notification away for another [BACKUP_REMINDER_DAYS].
 */
class BackupReminderCheck(
    private val dao: TimenoteDao,
    private val settings: SettingsRepository
) {
    /** The notification text if one is due, else null. Nothing is recorded until [markNotified]. */
    suspend fun textIfDue(now: Long): String? {
        val lastBackupAt = settings.lastBackupAtFlow.first()
        val quietSince = listOfNotNull(
            settings.backupReminderDismissedAtFlow.first(),
            settings.backupReminderNotifiedAtFlow.first()
        ).maxOrNull()
        val notes = dao.getAllTimenotesOnce().filter { !it.isDeleted }
        val oldest = notes.map { it.createdAt }.filter { it > 0 }.minOrNull()
        val due = backupReminderDue(now, lastBackupAt, oldest, quietSince, settings.syncEnabledFlow.first(), notes.size)
        return if (due) describeBackupReminder(now, lastBackupAt) else null
    }

    suspend fun markNotified(now: Long) = settings.markBackupReminderNotified(now)
}
