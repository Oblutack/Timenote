package com.oblutack.timenote.sync

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SyncTextsTest {
    @Test fun eachLinkingSituationIsExplainedInPlainWords() {
        assertContains(describeLinkPreview(LinkPreview(0, 0, 0, 0)), "nothing to sync yet")
        assertContains(describeLinkPreview(LinkPreview(3, 1, 0, 0)), "This device has 3 timenotes and 1 folder. They will be backed up")
        assertContains(describeLinkPreview(LinkPreview(0, 0, 12, 0)), "Your Google Drive has 12 timenotes. They will be added to this device")
        val merge = describeLinkPreview(LinkPreview(2, 0, 5, 1))
        assertContains(merge, "This device has 2 timenotes and your Google Drive has 5 timenotes and 1 folder")
        assertContains(merge, "Nothing is deleted")
    }

    @Test fun singularsAreRight() {
        assertContains(describeLinkPreview(LinkPreview(1, 0, 0, 0)), "has 1 timenote. It will be backed up")
        assertContains(describeLinkPreview(LinkPreview(0, 0, 1, 0)), "has 1 timenote. It will be added")
        assertContains(describeLinkPreview(LinkPreview(2, 0, 0, 0)), "has 2 timenotes. They will be backed up")
        assertFalse(describeLinkPreview(LinkPreview(1, 1, 0, 0)).contains("1 timenotes"))
    }

    @Test fun theKindFollowsWhatExistsWhere() {
        assertEquals(LinkKind.NothingYet, LinkPreview(0, 0, 0, 0).kind)
        assertEquals(LinkKind.Backup, LinkPreview(0, 2, 0, 0).kind)
        assertEquals(LinkKind.Restore, LinkPreview(0, 0, 0, 1).kind)
        assertEquals(LinkKind.Merge, LinkPreview(1, 0, 1, 0).kind)
    }

    @Test fun lastSyncReadsNaturally() {
        val now = 10_000_000_000L
        assertEquals("Not synced yet", describeLastSync(now, null))
        assertEquals("Last synced just now", describeLastSync(now, now - 20_000))
        assertEquals("Last synced 1 minute ago", describeLastSync(now, now - 60_000))
        assertEquals("Last synced 45 minutes ago", describeLastSync(now, now - 45 * 60_000))
        assertEquals("Last synced 3 hours ago", describeLastSync(now, now - 3 * 3_600_000))
        assertEquals("Last synced 2 days ago", describeLastSync(now, now - 2 * 86_400_000))
        assertEquals("Last synced just now", describeLastSync(now, now + 5_000), "a clock that is slightly off never shows a negative time")
    }
}
