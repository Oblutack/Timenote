package com.oblutack.timenote.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val DAY = 24L * 60 * 60 * 1000
private const val NOW = 1_800_000_000_000L

class EncodeUriComponentTest {
    @Test fun lettersDigitsAndSafeMarksStayAsTheyAre() =
        assertEquals("Az09-_.~", encodeUriComponent("Az09-_.~"))

    @Test fun spacesNewlinesAndSymbolsArePercentEncoded() {
        assertEquals("a%20b", encodeUriComponent("a b"))
        assertEquals("a%0Ab", encodeUriComponent("a\nb"))
        assertEquals("%26%3D%3F%2F%23%25", encodeUriComponent("&=?/#%"))
    }

    @Test fun nonLatinTextIsEncodedAsUtf8() {
        assertEquals("%C3%A9", encodeUriComponent("é"))
        assertEquals("%E2%80%A2", encodeUriComponent("•"))
    }
}

class BugReportLinkTest {
    private val info = AppInfo("1.1.0", "Android 16 (API 36) · Google Pixel 8")

    @Test fun goesToTheSupportAddressWithASubject() {
        val link = bugReportLink(info)
        assertTrue(link.startsWith("mailto:$SUPPORT_EMAIL?subject=Timenote%20bug%20report&body="), link)
    }

    @Test fun theBodyNamesTheAppAndSystemVersion() {
        val body = bugReportLink(info).substringAfter("&body=")
        assertTrue(encodeUriComponent("Timenote 1.1.0") in body)
        assertTrue(encodeUriComponent("Android 16 (API 36) · Google Pixel 8") in body)
    }

    @Test fun theLinkContainsNoRawSpacesOrNewlines() {
        val link = bugReportLink(info)
        assertFalse(' ' in link || '\n' in link)
    }

    @Test fun anUnknownVersionIsSaidSo() {
        val body = bugReportLink(AppInfo("", "")).substringAfter("&body=")
        assertTrue(encodeUriComponent("(version unknown)") in body)
    }
}

class BackupReminderTest {
    private fun due(
        lastBackupAt: Long? = null,
        oldestTimenoteAt: Long? = NOW - 100 * DAY,
        dismissedAt: Long? = null,
        syncOn: Boolean = false,
        count: Int = 10
    ) = backupReminderDue(NOW, lastBackupAt, oldestTimenoteAt, dismissedAt, syncOn, count)

    @Test fun dueWhenTheLastBackupIsOverThirtyDaysOld() = assertTrue(due(lastBackupAt = NOW - 31 * DAY))

    @Test fun notDueWhenTheLastBackupIsRecent() = assertFalse(due(lastBackupAt = NOW - 29 * DAY))

    @Test fun dueExactlyAtThirtyDays() = assertTrue(due(lastBackupAt = NOW - 30 * DAY))

    @Test fun neverBackedUpCountsFromTheOldestTimenote() {
        assertTrue(due(lastBackupAt = null, oldestTimenoteAt = NOW - 31 * DAY))
        assertFalse(due(lastBackupAt = null, oldestTimenoteAt = NOW - 10 * DAY))
    }

    @Test fun neverDueWithCloudSyncOn() = assertFalse(due(lastBackupAt = NOW - 400 * DAY, syncOn = true))

    @Test fun neverDueWithNothingRecorded() {
        assertFalse(due(lastBackupAt = null, oldestTimenoteAt = null, count = 0))
        assertFalse(due(lastBackupAt = NOW - 400 * DAY, count = 0))
    }

    @Test fun dismissingKeepsItAwayForAnotherThirtyDays() {
        assertFalse(due(lastBackupAt = NOW - 90 * DAY, dismissedAt = NOW - 5 * DAY))
        assertTrue(due(lastBackupAt = NOW - 90 * DAY, dismissedAt = NOW - 31 * DAY))
    }

    @Test fun aBackupMadeAfterTheDismissalResetsEverything() {
        // dismissed long ago, backed up yesterday: not due
        assertFalse(due(lastBackupAt = NOW - 1 * DAY, dismissedAt = NOW - 60 * DAY))
    }

    @Test fun lastBackupIsDescribedInDays() {
        assertEquals("No backup made yet", describeLastBackup(NOW, null))
        assertEquals("Last backup: today", describeLastBackup(NOW, NOW - 3_600_000))
        assertEquals("Last backup: yesterday", describeLastBackup(NOW, NOW - DAY - 1_000))
        assertEquals("Last backup: 34 days ago", describeLastBackup(NOW, NOW - 34 * DAY))
        assertEquals("Last backup: today", describeLastBackup(NOW, NOW + 5_000), "a time in the future counts as now")
    }

    @Test fun theReminderTextSaysHowLongOrThatThereIsNoBackup() {
        assertEquals("It has been 34 days since your last backup.", describeBackupReminder(NOW, NOW - 34 * DAY))
        assertTrue("not made a backup" in describeBackupReminder(NOW, null))
    }
}
