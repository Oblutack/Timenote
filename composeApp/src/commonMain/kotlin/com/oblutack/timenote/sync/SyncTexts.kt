package com.oblutack.timenote.sync

/**
 * Whether the Google Drive sync section is shown in RELEASE builds. Debug builds always show it.
 *
 * On since the release that ships sync (1.1.0). Switching it off hides the section and nothing else, so it can also
 * serve as an emergency switch in a later release.
 */
object FeatureFlags {
    const val SYNC_UI_IN_RELEASE = true
}

private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

private fun theyOrIt(total: Int) = if (total == 1) "It" else "They"

private fun countsOf(notes: Int, folders: Int): String =
    if (folders > 0) "${plural(notes, "timenote")} and ${plural(folders, "folder")}" else plural(notes, "timenote")

/** What the user is told before a device is connected, so the first sync is never a surprise. */
fun describeLinkPreview(p: LinkPreview): String = when (p.kind) {
    LinkKind.NothingYet ->
        "There is nothing to sync yet. Everything you record from now on will be saved to your Google Drive."
    LinkKind.Backup ->
        "This device has ${countsOf(p.localNotes, p.localFolders)}. ${theyOrIt(p.localNotes + p.localFolders)} will be backed up to your Google Drive, and nothing on this device changes."
    LinkKind.Restore ->
        "Your Google Drive has ${countsOf(p.cloudNotes, p.cloudFolders)}. ${theyOrIt(p.cloudNotes + p.cloudFolders)} will be added to this device."
    LinkKind.Merge ->
        "This device has ${countsOf(p.localNotes, p.localFolders)} and your Google Drive has ${countsOf(p.cloudNotes, p.cloudFolders)}. " +
            "They will be combined: anything that exists in both is merged, everything else is added on both sides. Nothing is deleted."
}

/** "Last synced 5 minutes ago". */
fun describeLastSync(now: Long, last: Long?): String {
    if (last == null) return "Not synced yet"
    val minutes = ((now - last) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Last synced just now"
        minutes < 60 -> "Last synced ${plural(minutes.toInt(), "minute")} ago"
        minutes < 24 * 60 -> "Last synced ${plural((minutes / 60).toInt(), "hour")} ago"
        else -> "Last synced ${plural((minutes / (24 * 60)).toInt(), "day")} ago"
    }
}
