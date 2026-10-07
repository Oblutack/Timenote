package com.oblutack.timenote.data

import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.testutil.FakeDataStore
import com.oblutack.timenote.testutil.runAppTest
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsRepositoryTest {
    private fun newRepository() = SettingsRepository(FakeDataStore())

    @Test fun defaultsAreAllEnabled() = runAppTest {
        val repo = newRepository()
        assertEquals(true, repo.enableBackgroundBlurFlow.first())
        assertEquals(true, repo.enableHapticsFlow.first())
        assertEquals(true, repo.useMonochromeNodesFlow.first())
        assertEquals(emptyList(), repo.customColorsFlow.first())
        assertNull(repo.activeSessionBackupFlow.first())
    }

    @Test fun togglesPersist() = runAppTest {
        val repo = newRepository()
        repo.setBackgroundBlur(false)
        repo.setHaptics(false)
        repo.setMonochromeNodes(false)
        assertEquals(false, repo.enableBackgroundBlurFlow.first())
        assertEquals(false, repo.enableHapticsFlow.first())
        assertEquals(false, repo.useMonochromeNodesFlow.first())
    }

    @Test fun customColorsAreStoredWithoutDuplicates() = runAppTest {
        val repo = newRepository()
        repo.addCustomColor(111L)
        repo.addCustomColor(222L)
        repo.addCustomColor(111L)
        assertEquals(listOf(111L, 222L), repo.customColorsFlow.first())
    }

    @Test fun customColorCanBeRemoved() = runAppTest {
        val repo = newRepository()
        repo.addCustomColor(111L)
        repo.addCustomColor(222L)
        repo.removeCustomColor(111L)
        assertEquals(listOf(222L), repo.customColorsFlow.first())
        repo.removeCustomColor(222L)
        assertEquals(emptyList(), repo.customColorsFlow.first())
    }

    @Test fun sessionBackupCanBeSavedAndCleared() = runAppTest {
        val repo = newRepository()
        repo.saveActiveSession("""{"x":1}""")
        assertEquals("""{"x":1}""", repo.activeSessionBackupFlow.first())
        repo.saveActiveSession(null)
        assertNull(repo.activeSessionBackupFlow.first())
    }

    @Test fun voiceMemosUploadOnWifiOnlyUnlessTheUserChangesIt() = runAppTest {
        val repo = newRepository()
        assertEquals(true, repo.voiceWifiOnly(), "on by default")
        repo.setVoiceWifiOnly(false)
        assertEquals(false, repo.voiceWifiOnly())
        repo.setVoiceWifiOnly(true)
        assertEquals(true, repo.voiceWifiOnlyFlow.first())
    }

    @Test fun theSyncCheckpointAndLinkedAccountPersist() = runAppTest {
        val repo = newRepository()
        assertNull(repo.pageToken()); assertNull(repo.linkedAccount())
        repo.savePageToken("42"); repo.saveLinkedAccount("acct-1")
        assertEquals("42", repo.pageToken()); assertEquals("acct-1", repo.linkedAccount())
        repo.savePageToken(null); repo.saveLinkedAccount(null)
        assertNull(repo.pageToken()); assertNull(repo.linkedAccount())
    }
}
