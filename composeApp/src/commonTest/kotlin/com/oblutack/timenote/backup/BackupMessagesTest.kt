package com.oblutack.timenote.backup

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class BackupMessagesTest {
    @Test fun exportSummaryUsesCorrectSingularsAndPlurals() {
        val one = describeExport(ExportResult(notes = 1, folders = 1, tags = 1, audioFiles = 1, missingAudio = 0))
        assertContains(one, "1 timenote, 1 folder, 1 tag and 1 voice memo.")
        val many = describeExport(ExportResult(notes = 12, folders = 0, tags = 4, audioFiles = 3, missingAudio = 0))
        assertContains(many, "12 timenotes, 0 folders, 4 tags and 3 voice memos.")
        assertFalse(many.contains("could not be found"))
    }

    @Test fun exportSaysWhenVoiceMemoFilesWereMissing() {
        assertContains(describeExport(ExportResult(1, 0, 0, 0, 1)), "1 voice memo file could not be found on this device and is not in the backup.")
        assertContains(describeExport(ExportResult(1, 0, 0, 0, 2)), "2 voice memo files could not be found on this device and are not in the backup.")
    }

    @Test fun importExplainsEachOutcomeInPlainWords() {
        assertContains(describeImport(ImportResult.NotABackup), "not a Timenote backup")
        assertContains(describeImport(ImportResult.NewerFormat), "newer version")
        assertContains(describeImport(ImportResult.Done(0, 0, 0, 0, 0, 0, 0, 0)), "already on this device")
        val done = describeImport(ImportResult.Done(notesAdded = 2, notesUpdated = 1, foldersAdded = 1, foldersUpdated = 0, tagsAdded = 0, tagsUpdated = 0, audioRestored = 1, skipped = 1))
        assertContains(done, "Added 2 timenotes, 1 folder.")
        assertContains(done, "Updated 1 existing item")
        assertContains(done, "Restored 1 voice memo.")
        assertContains(done, "1 file in the backup could not be read and was skipped.")
    }
}
