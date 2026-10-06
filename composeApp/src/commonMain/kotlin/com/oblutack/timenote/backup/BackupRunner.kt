package com.oblutack.timenote.backup

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/** What the backup section of Settings shows. */
sealed interface BackupStatus {
    data object Idle : BackupStatus
    data class Working(val label: String) : BackupStatus
    data class Finished(val title: String, val message: String, val success: Boolean) : BackupStatus
}

/**
 * Runs backups on the platform. A destination or source is an opaque string the platform understands
 * (a content Uri on Android), chosen by the user through the system file picker.
 * Runs in an app-wide scope, so leaving the screen does not cut a backup in half.
 */
interface BackupRunner {
    val status: StateFlow<BackupStatus>
    fun exportTo(destination: String)
    fun importFrom(source: String)
    fun dismiss()
}

/** Opens the system file picker for the user and hands the choice to the [BackupRunner]. */
interface BackupLauncher {
    fun export()
    fun import()
}

@Composable
expect fun rememberBackupLauncher(runner: BackupRunner): BackupLauncher

// --- wording shown to the user (kept here so it can be tested) ---

private fun count(n: Int, singular: String, plural: String = singular + "s") = "$n ${if (n == 1) singular else plural}"

fun describeExport(r: ExportResult): String = buildString {
    append("Saved ${count(r.notes, "timenote")}, ${count(r.folders, "folder")}, ${count(r.tags, "tag")}")
    append(" and ${count(r.audioFiles, "voice memo")}.")
    if (r.missingAudio > 0) {
        append("\n\n${count(r.missingAudio, "voice memo file")} could not be found on this device and ")
        append(if (r.missingAudio == 1) "is" else "are")
        append(" not in the backup.")
    }
    append("\n\nKeep the file somewhere safe, for example in Google Drive.")
}

fun describeImport(r: ImportResult): String = when (r) {
    ImportResult.NotABackup -> "This file is not a Timenote backup."
    ImportResult.NewerFormat -> "This backup was made by a newer version of Timenote. Update the app and try again. Nothing was changed."
    is ImportResult.Done -> {
        val added = r.notesAdded + r.foldersAdded + r.tagsAdded
        val updated = r.notesUpdated + r.foldersUpdated + r.tagsUpdated
        buildString {
            if (added == 0 && updated == 0) {
                append("Everything in this backup is already on this device. Nothing was changed.")
            } else {
                append("Added ${count(r.notesAdded, "timenote")}")
                if (r.foldersAdded > 0) append(", ${count(r.foldersAdded, "folder")}")
                if (r.tagsAdded > 0) append(", ${count(r.tagsAdded, "tag")}")
                append(".")
                if (updated > 0) append("\nUpdated $updated existing ${if (updated == 1) "item" else "items"} with newer changes from the backup.")
            }
            if (r.audioRestored > 0) append("\nRestored ${count(r.audioRestored, "voice memo")}.")
            if (r.skipped > 0) append("\n\n${count(r.skipped, "file")} in the backup could not be read and ${if (r.skipped == 1) "was" else "were"} skipped.")
        }
    }
}
