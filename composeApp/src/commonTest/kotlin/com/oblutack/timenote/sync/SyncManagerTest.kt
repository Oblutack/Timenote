package com.oblutack.timenote.sync

import com.oblutack.timenote.drive.DriveSession
import com.oblutack.timenote.drive.DriveStatus
import com.oblutack.timenote.drive.RemoteException
import com.oblutack.timenote.drive.RemoteStore
import com.oblutack.timenote.testutil.FakeRemoteStore
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSyncPrefs(enabled: Boolean = false) : SyncPrefs {
    val enabled = MutableStateFlow(enabled)
    val last = MutableStateFlow<Long?>(null)
    val wifi = MutableStateFlow(true)
    override val syncEnabledFlow = this.enabled
    override suspend fun setSyncEnabled(enabled: Boolean) { this.enabled.value = enabled }
    override val lastSyncAtFlow = last
    override suspend fun setLastSyncAt(time: Long?) { last.value = time }
    override val voiceWifiOnlyFlow = wifi
    override suspend fun setVoiceWifiOnly(enabled: Boolean) { wifi.value = enabled }
}

class FakeScheduler : SyncScheduler {
    var periodic = false
    val afterEditDelays = mutableListOf<Long>()
    override fun schedulePeriodic() { periodic = true }
    override fun scheduleAfterEdit(delayMs: Long) { afterEditDelays += delayMs }
    override fun cancelAll() { periodic = false; afterEditDelays.clear() }
}

class FakeDriveSession(val cloud: FakeRemoteStore, var signedIn: Boolean = true, var label: String? = "me@example.com") : DriveSession {
    private val _status = MutableStateFlow<DriveStatus>(DriveStatus.Unknown)
    override val status: StateFlow<DriveStatus> = _status
    override val store: RemoteStore get() = cloud
    override suspend fun check() { _status.value = if (signedIn) DriveStatus.Connected else DriveStatus.SignedOut }
    override suspend fun accountId(): String? = if (signedIn) cloud.accountId() else null
    override suspend fun accountLabel(): String? = label
}

class SyncManagerTest {
    private class Rig(val replica: Replica, val prefs: FakeSyncPrefs, val scheduler: FakeScheduler, val session: FakeDriveSession, val manager: SyncManager, val clock: Clock)

    private fun TestScope.rig(cloud: FakeRemoteStore = FakeRemoteStore(), enabled: Boolean = false, accountOf: (() -> String?)? = null): Rig {
        val clock = Clock()
        val replica = Replica("a", backgroundScope, cloud, clock)
        val engine = if (accountOf == null) replica.engine else SyncEngine(
            replica.dao, cloud, replica.checkpoint, com.oblutack.timenote.testutil.FakeDeviceId("a"), replica.repo.writeLock,
            now = { clock.now }, accountId = { accountOf() }
        )
        val prefs = FakeSyncPrefs(enabled)
        val scheduler = FakeScheduler()
        val session = FakeDriveSession(cloud)
        val manager = SyncManager(engine, session, prefs, scheduler, backgroundScope, now = { clock.now })
        return Rig(replica, prefs, scheduler, session, manager, clock)
    }

    // ---------------------------------------------------------------- turning sync on and off

    @Test fun turningSyncOnRunsTheFirstSyncAndSchedulesTheRest() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud)
        r.replica.repo.saveTimenote(testNote("n1"))

        r.manager.enable(); advanceUntilIdle()

        assertTrue(r.manager.state.value.enabled)
        assertTrue(r.scheduler.periodic)
        assertNotNull(r.manager.state.value.lastSyncAt)
        assertTrue("notes/n1.json" in cloud.snapshot().keys)
        assertNull(r.manager.state.value.problem)
        assertFalse(r.manager.state.value.syncing)
    }

    @Test fun nothingSyncsWhileSyncIsOff() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud)
        r.replica.repo.saveTimenote(testNote("n1"))

        r.manager.syncNow(); r.manager.onAppForeground(); r.manager.onLocalEdit(); advanceUntilIdle()
        val background = r.manager.runBackgroundSync()

        assertEquals(0, cloud.calls)
        assertTrue(r.scheduler.afterEditDelays.isEmpty())
        assertEquals(SyncResult.Done(SyncStats()), background)
    }

    @Test fun turningSyncOffKeepsEverythingAndForgetsTheLink() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud, accountOf = { "alice" })
        r.replica.repo.saveTimenote(testNote("n1"))
        r.manager.enable(); advanceUntilIdle()
        assertEquals("alice", r.replica.checkpoint.account)

        r.manager.disable(); advanceUntilIdle()

        assertFalse(r.manager.state.value.enabled)
        assertFalse(r.scheduler.periodic)
        assertNull(r.replica.checkpoint.account)
        assertNull(r.replica.checkpoint.token)
        assertTrue(r.replica.dao.getAllSyncStates().isEmpty())
        assertEquals(1, r.replica.dao.getAllTimenotesOnce().size, "the notes on this device are untouched")
        assertTrue("notes/n1.json" in cloud.snapshot().keys, "the cloud copy is not touched either")
    }

    @Test fun connectingAgainAfterTurningOffMergesInsteadOfDuplicating() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud)
        r.replica.repo.saveTimenote(testNote("n1"))
        r.manager.enable(); advanceUntilIdle()
        r.manager.disable(); advanceUntilIdle()

        r.manager.enable(); advanceUntilIdle()

        assertEquals(1, cloud.snapshot().keys.count { it == "notes/n1.json" })
        assertEquals(1, r.replica.dao.getAllTimenotesOnce().size)
    }

    @Test fun deletingTheCloudDataEmptiesTheCloudAndKeepsTheNotesHere() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud)
        r.replica.repo.saveTimenote(testNote("n1"))
        r.manager.enable(); advanceUntilIdle()
        assertTrue(cloud.snapshot().isNotEmpty())

        r.manager.deleteCloudDataAndDisconnect(); advanceUntilIdle()

        assertTrue(cloud.snapshot().isEmpty())
        assertFalse(r.manager.state.value.enabled)
        assertEquals(1, r.replica.dao.getAllTimenotesOnce().size)
        assertNull(r.manager.state.value.problem)
    }

    @Test fun ifTheCloudDataCannotBeDeletedNothingChangesAndTheUserIsTold() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud)
        r.replica.repo.saveTimenote(testNote("n1"))
        r.manager.enable(); advanceUntilIdle()

        cloud.failNext(RemoteException.Network(RuntimeException("offline")))
        r.manager.deleteCloudDataAndDisconnect(); advanceUntilIdle()

        assertTrue(r.manager.state.value.enabled, "still on: the data is still there")
        assertTrue(cloud.snapshot().isNotEmpty())
        assertTrue(r.manager.state.value.problem!!.text.contains("could not be removed"))
    }

    // ---------------------------------------------------------------- when syncs happen

    @Test fun editsScheduleOneSyncSoonAndOnlyWhenSyncIsOn() = runAppTest {
        val r = rig(enabled = true)
        r.manager.onLocalEdit(); r.manager.onLocalEdit(); advanceUntilIdle()
        assertEquals(listOf(30_000L, 30_000L), r.scheduler.afterEditDelays, "each edit moves the same pending sync back")
    }

    @Test fun cominBackToTheAppSyncsUnlessItJustDid() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud, enabled = true)

        r.manager.onAppForeground(); advanceUntilIdle()
        val callsAfterFirst = cloud.calls
        assertTrue(callsAfterFirst > 0)

        r.clock.tick(10_000)
        r.manager.onAppForeground(); advanceUntilIdle()
        assertEquals(callsAfterFirst, cloud.calls, "synced 10 seconds ago: nothing to do")

        r.clock.tick(120_000)
        r.manager.onAppForeground(); advanceUntilIdle()
        assertTrue(cloud.calls > callsAfterFirst)
    }

    // ---------------------------------------------------------------- what the user is told

    @Test fun lostAccessIsExplainedAndClearedByTheNextGoodSync() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud, enabled = true)

        cloud.failNext(RemoteException.NeedsSignIn())
        r.manager.syncNow(); advanceUntilIdle()
        val problem = r.manager.state.value.problem!!
        assertTrue(problem.needsUser)
        assertTrue(problem.text.contains("Connect again"))

        r.manager.syncNow(); advanceUntilIdle()
        assertNull(r.manager.state.value.problem)
    }

    @Test fun aFullDriveIsExplained() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud, enabled = true)
        cloud.failNext(RemoteException.StorageFull())
        r.manager.syncNow(); advanceUntilIdle()
        assertTrue(r.manager.state.value.problem!!.text.contains("Google Drive is full"))
        assertTrue(r.manager.state.value.problem!!.needsUser)
    }

    @Test fun beingOfflineIsNotAnEmergency() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud, enabled = true)
        cloud.failNext(RemoteException.Network(RuntimeException("offline")))
        r.manager.syncNow(); advanceUntilIdle()
        val problem = r.manager.state.value.problem!!
        assertFalse(problem.needsUser)
        assertTrue(problem.text.contains("safe on this device"))
        assertNull(r.manager.state.value.lastSyncAt, "a failed sync is not recorded as a success")
    }

    @Test fun memosThatCouldNotBeUploadedAreMentioned() {
        assertTrue(problemAfterSuccess(SyncStats(audioFailed = 1))!!.text.startsWith("1 voice memo could"))
        assertTrue(problemAfterSuccess(SyncStats(audioFailed = 3))!!.text.startsWith("3 voice memos could"))
        assertNull(problemAfterSuccess(SyncStats()))
    }

    // ---------------------------------------------------------------- accounts

    @Test fun aDifferentAccountIsReportedAndNothingHappensUntilTheUserDecides() = runAppTest {
        val cloud = FakeRemoteStore()
        var account: String? = "alice"
        val r = rig(cloud, enabled = true, accountOf = { account })
        r.replica.repo.saveTimenote(testNote("n1"))
        r.manager.syncNow(); advanceUntilIdle()
        val callsBefore = cloud.calls

        account = "bob"
        r.manager.syncNow(); advanceUntilIdle()

        assertEquals(AccountChange("alice", "bob"), r.manager.state.value.accountChange)
        assertEquals(callsBefore, cloud.calls)

        r.manager.dismissAccountChange()
        assertNull(r.manager.state.value.accountChange)
    }

    @Test fun usingTheNewAccountMergesThisDevicesDataIntoIt() = runAppTest {
        val cloud = FakeRemoteStore()
        var account: String? = "alice"
        val r = rig(cloud, enabled = true, accountOf = { account })
        r.replica.repo.saveTimenote(testNote("n1"))
        r.manager.syncNow(); advanceUntilIdle()

        account = "bob"; cloud.account = "bob"
        r.manager.syncNow(); advanceUntilIdle()
        assertNotNull(r.manager.state.value.accountChange)
        // the new account's Drive is empty
        cloud.snapshot().keys.toList().forEach { name -> cloud.list().first { it.name == name }.also { cloud.delete(it.id) } }

        r.manager.useNewAccount(); advanceUntilIdle()

        assertNull(r.manager.state.value.accountChange)
        assertEquals("bob", r.replica.checkpoint.account)
        assertTrue("notes/n1.json" in cloud.snapshot().keys, "this device's notes went to the new account")
        assertEquals(1, r.replica.dao.getAllTimenotesOnce().size)
    }

    @Test fun theAccountsReadableNameIsFetchedWhenConnected() = runAppTest {
        val r = rig()
        r.session.label = "me@example.com"
        r.manager.refreshConnection(); advanceUntilIdle()
        assertEquals(DriveStatus.Connected, r.manager.state.value.connection)
        assertEquals("me@example.com", r.manager.state.value.accountLabel)

        r.session.signedIn = false
        r.manager.refreshConnection(); advanceUntilIdle()
        assertEquals(DriveStatus.SignedOut, r.manager.state.value.connection)
        assertNull(r.manager.state.value.accountLabel)
    }

    // ---------------------------------------------------------------- linking preview

    @Test fun thePreviewSaysWhatConnectingWillDo() = runAppTest {
        val cloud = FakeRemoteStore()
        val a = rig(cloud)
        val b = Replica("b", backgroundScope, cloud, a.clock)

        assertEquals(LinkKind.NothingYet, a.manager.preview().kind)

        a.replica.repo.saveTimenote(testNote("n1")); a.replica.repo.saveFolder(testFolder("f1"))
        val backup = a.manager.preview()
        assertEquals(LinkKind.Backup, backup.kind)
        assertEquals(1, backup.localNotes); assertEquals(1, backup.localFolders); assertEquals(0, backup.cloudNotes)

        a.replica.syncDone()
        val bManager = SyncManager(b.engine, FakeDriveSession(cloud), FakeSyncPrefs(), FakeScheduler(), backgroundScope)
        val restore = bManager.preview()
        assertEquals(LinkKind.Restore, restore.kind)
        assertEquals(1, restore.cloudNotes); assertEquals(1, restore.cloudFolders); assertEquals(0, restore.localNotes)

        b.repo.saveTimenote(testNote("other"))
        assertEquals(LinkKind.Merge, bManager.preview().kind)
    }

    @Test fun ifDriveCannotBeReachedThePreviewSaysSoInsteadOfCrashing() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud)
        cloud.failNext(RemoteException.Network(RuntimeException("offline")))

        val preview = r.manager.linkPreview()

        assertNull(preview)
        assertTrue(r.manager.state.value.problem!!.text.contains("Could not reach Google Drive"))
        assertFalse(r.manager.state.value.enabled, "nothing was turned on")
    }

    @Test fun theWifiOnlyChoiceIsRememberedAndShown() = runAppTest {
        val r = rig()
        assertTrue(r.manager.state.value.voiceWifiOnly, "on by default")
        r.manager.setVoiceWifiOnly(false); advanceUntilIdle()
        assertFalse(r.manager.state.value.voiceWifiOnly)
        assertFalse(r.prefs.wifi.value)
    }

    @Test fun onlyTemporaryFailuresAreRetriedByTheBackgroundWorker() {
        assertTrue(SyncResult.Failed(RemoteException.Network(RuntimeException())).shouldRetryLater())
        assertTrue(SyncResult.Failed(RemoteException.Server(503)).shouldRetryLater())
        assertFalse(SyncResult.Failed(RemoteException.Protocol("odd")).shouldRetryLater())
        assertFalse(SyncResult.NeedsSignIn.shouldRetryLater())
        assertFalse(SyncResult.StorageFull.shouldRetryLater())
        assertFalse(SyncResult.Done(SyncStats()).shouldRetryLater())
        assertFalse(SyncResult.AccountChanged("a", "b").shouldRetryLater())
    }

    @Test fun lookingAtThePreviewChangesNothing() = runAppTest {
        val cloud = FakeRemoteStore()
        val r = rig(cloud)
        r.replica.repo.saveTimenote(testNote("n1"))
        r.manager.preview()
        assertTrue(cloud.snapshot().isEmpty())
        assertNull(r.replica.checkpoint.token)
        assertNull(r.replica.checkpoint.account)
    }
}

private suspend fun Replica.syncDone() { sync() }

private suspend fun SyncManager.preview(): LinkPreview = linkPreview()!!
