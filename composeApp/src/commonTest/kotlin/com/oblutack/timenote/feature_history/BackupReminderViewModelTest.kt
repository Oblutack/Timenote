package com.oblutack.timenote.feature_history

import com.oblutack.timenote.core.DirectoryAudioFiles
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

private class ReminderEnv(scope: TestScope) {
    val sessions = SessionRepository(FakeTimenoteDao(), scope.backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId(), now = { NOW })
    val settings = SettingsRepository(FakeDataStore())
    val vm = HistoryViewModel(sessions, FakeAudioPlayer(), FakeAudioRecorder(), DirectoryAudioFiles("/fake"), settings = settings, now = { NOW })

    init {
        // the reminder only runs while somebody looks at it
        scope.backgroundScope.launch { vm.backupReminder.collect { } }
    }
}

class BackupReminderViewModelTest {

    @Test fun anOldUnbackedUpPhoneGetsAReminder() = runAppTest {
        val env = ReminderEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 40 * DAY))

        assertNotNull(env.vm.backupReminder.value)
        assertTrue("not made a backup" in env.vm.backupReminder.value!!)
    }

    @Test fun aFreshInstallGetsNone() = runAppTest {
        val env = ReminderEnv(this)
        env.sessions.saveTimenote(testNote("new", createdAt = NOW - 3 * DAY))
        assertNull(env.vm.backupReminder.value)
    }

    @Test fun makingABackupRemovesIt() = runAppTest {
        val env = ReminderEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 40 * DAY))
        assertNotNull(env.vm.backupReminder.value)

        env.settings.setLastBackupAt(NOW)

        assertNull(env.vm.backupReminder.value)
    }

    @Test fun anOldBackupBringsItBackWithTheNumberOfDays() = runAppTest {
        val env = ReminderEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 100 * DAY))
        env.settings.setLastBackupAt(NOW - 45 * DAY)

        assertEquals("It has been 45 days since your last backup.", env.vm.backupReminder.value)
    }

    @Test fun notNowHidesItForThirtyDays() = runAppTest {
        val env = ReminderEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 40 * DAY))

        env.vm.dismissBackupReminder()

        assertNull(env.vm.backupReminder.value)
    }

    @Test fun cloudSyncMeansNoReminder() = runAppTest {
        val env = ReminderEnv(this)
        env.sessions.saveTimenote(testNote("old", createdAt = NOW - 40 * DAY))
        assertNotNull(env.vm.backupReminder.value)

        env.settings.setSyncEnabled(true)

        assertNull(env.vm.backupReminder.value)
    }
}
