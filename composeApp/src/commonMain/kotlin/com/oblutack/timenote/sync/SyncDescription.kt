package com.oblutack.timenote.sync

/** One line per outcome, for the debug panel and for logs. */
fun describeSyncResult(result: SyncResult): List<String> = when (result) {
    is SyncResult.Done -> {
        val s = result.stats
        listOf(
            "Sync finished.",
            "notes etc.: ${s.downloaded} downloaded, ${s.uploaded} uploaded, ${s.removedLocally} removed here, ${s.deletedRemotely} removed in the cloud",
            "voice memos: ${s.audioUploaded} uploaded, ${s.audioFailed} failed, ${s.audioRemovedFromCloud} removed from the cloud",
            "skipped files: ${s.skipped}, cleaned up: ${s.compacted}"
        ) + listOfNotNull(s.audioError?.let { "first voice memo problem: $it" })
    }
    SyncResult.NeedsSignIn -> listOf("Not signed in to Google Drive (or access was revoked).")
    SyncResult.StorageFull -> listOf("Your Google Drive is full.")
    SyncResult.AlreadyRunning -> listOf("A sync is already running.")
    is SyncResult.AccountChanged -> listOf("Another Google account is signed in (was ${result.linked}, now ${result.current}). Nothing was changed.")
    is SyncResult.Failed -> listOf("Sync failed: ${result.error.message}. It will continue where it stopped next time.")
}
