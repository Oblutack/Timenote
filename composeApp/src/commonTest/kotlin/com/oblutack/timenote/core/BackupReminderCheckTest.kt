package com.oblutack.timenote.core

import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.feature_history.presentation.HistoryViewModel
import com.oblutack.timenote.testutil.FakeAudioPlayer
import com.oblutack.timenote.testutil.FakeAudioRecorder
import com.oblutack.timenote.testutil.FakeDataStore
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testNote
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = 24L * 60 * 60 * 1000
private const val NOW = 1_800_000_000_000L

private class NoticeEnv(scope: TestScope) {
    val dao = FakeTimenoteDao()
    val sessions = SessionRepository(dao, scope.backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId(), now = { NOW })
    val settings = SettingsRepository(FakeDataStore())
    val check = BackupReminderCheck(dao, settings)
    val vm = HistoryViewModel(sessions, FakeAudioPlayer(), FakeAudioRecorder(), DirectoryAudioFiles("/fake"), settings = settings, now = { NOW })

    init {
        scope.backgroundScope.launch { vm.backupReminder.collect { } }
    }
}

/** The system notification about backing up: same rules as the card, but sent only once per 30 days. */
class BackupReminderCheckTest {

    @Test fun anOldUnbackedUpPhoneIsDue() = runAppTest {
        val env = NoticeEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 40 * DAY))

        val text = env.check.textIfDue(NOW)

        assertNotNull(text)
        assertTrue("not made a backup" in text)
    }

    @Test fun aFreshInstallIsNotDue() = runAppTest {
        val env = NoticeEnv(this)
        env.sessions.saveTimenote(testNote("new", createdAt = NOW - 3 * DAY))
        assertNull(env.check.textIfDue(NOW))
    }

    @Test fun nothingRecordedIsNotDue() = runAppTest {
        assertNull(NoticeEnv(this).check.textIfDue(NOW))
    }

    @Test fun onlyDeletedTimenotesDoNotCount() = runAppTest {
        val env = NoticeEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 40 * DAY))
        env.sessions.deleteTimenote("old")
        assertNull(env.check.textIfDue(NOW))
    }

    @Test fun afterNotifyingItStaysQuietForThirtyDaysThenReturns() = runAppTest {
        val env = NoticeEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 100 * DAY))
        assertNotNull(env.check.textIfDue(NOW))

        env.check.markNotified(NOW)

        assertNull(env.check.textIfDue(NOW + 1 * DAY), "the next daily check must not send it again")
        assertNull(env.check.textIfDue(NOW + 29 * DAY))
        assertNotNull(env.check.textIfDue(NOW + 31 * DAY))
    }

    @Test fun notNowOnTheCardAlsoSilencesTheNotification() = runAppTest {
        val env = NoticeEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 100 * DAY))

        env.vm.dismissBackupReminder()

        assertNull(env.check.textIfDue(NOW))
    }

    @Test fun theNotificationDoesNotHideTheCardInTheApp() = runAppTest {
        val env = NoticeEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 100 * DAY))

        env.check.markNotified(NOW)

        assertNotNull(env.vm.backupReminder.value, "someone who swiped the notification away still sees the card")
    }

    @Test fun aBackupMadeEndsTheReminder() = runAppTest {
        val env = NoticeEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 100 * DAY))
        env.settings.setLastBackupAt(NOW - 1 * DAY)
        assertNull(env.check.textIfDue(NOW))
    }

    @Test fun anOldBackupNamesTheDays() = runAppTest {
        val env = NoticeEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 100 * DAY))
        env.settings.setLastBackupAt(NOW - 45 * DAY)
        assertEquals("It has been 45 days since your last backup.", env.check.textIfDue(NOW))
    }

    @Test fun cloudSyncMeansNoNotification() = runAppTest {
        val env = NoticeEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 100 * DAY))
        env.settings.setSyncEnabled(true)
        assertNull(env.check.textIfDue(NOW))
    }
}
