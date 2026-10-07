package com.oblutack.timenote.sync

import com.oblutack.timenote.data.database.FieldNames
import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.testutil.FakeCheckpoint
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId
import com.oblutack.timenote.testutil.FakeRemoteStore
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testNote
import com.oblutack.timenote.testutil.testTag
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncMaintenanceTest {
    private fun TestScope.replica(name: String, cloud: FakeRemoteStore, clock: Clock) = Replica(name, backgroundScope, cloud, clock)

    private val day = 24L * 60 * 60 * 1000

    // ---------------------------------------------------------------- old deletion records

    @Test fun aTagDeletedLongAgoIsCleanedUpEverywhere() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTag(testTag("t1", "Old")); a.repo.saveTag(testTag("t2", "Keep"))
        a.sync()
        clock.tick(); a.repo.deleteTag("t1")
        a.sync() // the deletion reaches the cloud
        assertTrue("tags/t1.json" in cloud.snapshot().keys)

        clock.tick(181 * day)
        val stats = a.syncDone()

        assertEquals(1, stats.compacted)
        assertTrue("tags/t1.json" !in cloud.snapshot().keys, "removed from the cloud")
        assertEquals(listOf("t2"), a.dao.getAllTagsOnce().map { it.id }, "removed from this device too")
        assertTrue(a.dao.getAllSyncStates().none { it.entityId == "t1" })
    }

    @Test fun aDeviceThatWasAwayForAges_FollowsTheCleanUpInsteadOfBringingTheTagBack() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val stale = replica("stale", cloud, clock)
        a.repo.saveTag(testTag("t1", "Old"))
        a.sync(); stale.sync()
        clock.tick(); a.repo.deleteTag("t1"); a.sync()
        clock.tick(181 * day); a.sync() // cleaned up here and in the cloud

        stale.sync()

        assertTrue(stale.dao.getAllTagsOnce().isEmpty(), "the stale device does not resurrect it")
        assertTrue("tags/t1.json" !in cloud.snapshot().keys)
    }

    @Test fun aRecentlyDeletedTagIsKept() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTag(testTag("t1")); a.sync()
        clock.tick(); a.repo.deleteTag("t1"); a.sync()

        clock.tick(179 * day)
        assertEquals(0, a.syncDone().compacted)
        assertEquals(1, a.dao.getAllTagsOnce().size)
    }

    @Test fun aDeletionThatHasNotReachedTheCloudYetIsNotCleanedUp() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTag(testTag("t1")); a.sync()
        clock.tick(); a.repo.deleteTag("t1")
        clock.tick(200 * day) // the app was not used for a long time: the deletion was never uploaded

        // the run uploads the deletion; only a LATER run may clean it up, after other devices could have seen it
        assertEquals(0, a.syncDone().compacted)
        assertEquals(1, a.dao.getAllTagsOnce().size)
        clock.tick()
        assertEquals(1, a.syncDone().compacted)
    }

    @Test fun trashedNotesAreNeverCleanedUpAutomatically() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTimenote(testNote("n1")); a.sync()
        clock.tick(); a.repo.deleteTimenote("n1"); a.sync()

        clock.tick(400 * day)
        assertEquals(0, a.syncDone().compacted)
        assertTrue(a.dao.getAllTimenotesOnce().single().isDeleted)
        assertTrue("notes/n1.json" in cloud.snapshot().keys)
    }

    @Test fun oldRemovedFromNoteMarksAreDroppedButRecentOnesAndCurrentMembershipsStay() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock)
        a.repo.saveTag(testTag("t1")); a.repo.saveTag(testTag("t2"))
        a.repo.saveTimenote(testNote("n1").copy(tags = listOf(testTag("t1"), testTag("t2"))))
        a.sync()
        clock.tick(); a.repo.updateTimenoteTags("n1", listOf(testTag("t1"))) // t2 removed
        a.sync()

        clock.tick(10 * day)
        a.sync()
        assertTrue(a.dao.getFieldVersions(SyncKind.NOTE, "n1").any { it.field == FieldNames.tag("t2") && !it.present }, "recent: kept")

        clock.tick(200 * day)
        a.sync()
        val versions = a.dao.getFieldVersions(SyncKind.NOTE, "n1")
        assertTrue(versions.none { it.field == FieldNames.tag("t2") }, "old removal mark dropped")
        assertTrue(versions.any { it.field == FieldNames.tag("t1") && it.present }, "current membership untouched")
        a.sync() // the cloud copy follows the cleaned-up local data
        val cloudNote = SyncJson.decodeFromString<SyncNote>(cloud.snapshot().getValue("notes/n1.json").decodeToString())
        assertEquals(setOf("t1"), cloudNote.tags.keys)
    }

    // ---------------------------------------------------------------- which Google account

    private fun TestScope.accountReplica(cloud: FakeRemoteStore, clock: Clock, account: () -> String?): Triple<FakeTimenoteDao, FakeCheckpoint, SyncEngine> {
        val dao = FakeTimenoteDao(); val checkpoint = FakeCheckpoint()
        val repo = SessionRepository(dao, backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId("a"), now = { clock.now })
        repo.saveTimenote(testNote("n1", title = "Mine"))
        val engine = SyncEngine(dao, cloud, checkpoint, FakeDeviceId("a"), repo.writeLock, now = { clock.now }, accountId = { account() })
        return Triple(dao, checkpoint, engine)
    }

    @Test fun theFirstSyncRemembersTheAccountAndLaterSyncsWithItAreFine() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val (_, checkpoint, engine) = accountReplica(cloud, clock) { "alice" }
        assertIs<SyncResult.Done>(engine.sync())
        assertEquals("alice", checkpoint.account)
        assertIs<SyncResult.Done>(engine.sync())
    }

    @Test fun aDifferentAccountTouchesNothingAndAsksTheUser() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        var account: String? = "alice"
        val (dao, checkpoint, engine) = accountReplica(cloud, clock) { account }
        engine.sync()
        val cloudBefore = cloud.snapshot().keys
        val callsBefore = cloud.calls

        account = "bob"
        val result = engine.sync()

        assertEquals(SyncResult.AccountChanged(linked = "alice", current = "bob"), result)
        assertEquals(callsBefore, cloud.calls, "no request was sent to the other account")
        assertEquals(cloudBefore, cloud.snapshot().keys)
        assertEquals("alice", checkpoint.account)
        assertEquals(1, dao.getAllTimenotesOnce().size)
    }

    @Test fun switchingAccountMergesThisDevicesDataIntoTheNewCloud() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        var account: String? = "alice"
        val (dao, checkpoint, engine) = accountReplica(cloud, clock) { account }
        engine.sync()

        account = "bob"
        val newCloud = FakeRemoteStore()
        // a fresh engine on the same device, now talking to bob's (empty) Drive
        val repoEngine = SyncEngine(dao, newCloud, checkpoint, FakeDeviceId("a"), now = { clock.now }, accountId = { account })
        assertIs<SyncResult.AccountChanged>(repoEngine.sync())
        repoEngine.switchAccount()
        assertNull(checkpoint.token, "starts from scratch with the new account")
        assertEquals("bob", checkpoint.account)
        assertTrue(dao.getAllSyncStates().isEmpty())

        val result = repoEngine.sync()

        assertIs<SyncResult.Done>(result)
        assertEquals(1, dao.getAllTimenotesOnce().size, "nothing local was lost")
        assertTrue("notes/n1.json" in newCloud.snapshot().keys, "this device's data went to the new account")
    }

    @Test fun theGuardWorksWithTheAccountOfTheStoreItself() = runAppTest {
        val cloud = FakeRemoteStore().also { it.account = "alice" }; val clock = Clock()
        val dao = FakeTimenoteDao(); val checkpoint = FakeCheckpoint()
        val repo = SessionRepository(dao, backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId("a"), now = { clock.now })
        repo.saveTimenote(testNote("n1"))
        val engine = SyncEngine(dao, cloud, checkpoint, FakeDeviceId("a"), repo.writeLock, now = { clock.now }, accountId = { cloud.accountId() })

        assertIs<SyncResult.Done>(engine.sync())
        assertEquals("alice", checkpoint.account)

        cloud.account = "bob"
        assertEquals(SyncResult.AccountChanged("alice", "bob"), engine.sync())
    }

    @Test fun troubleWhileAskingForTheAccountIsReportedLikeAnyOtherSyncTrouble() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val dao = FakeTimenoteDao()
        val engine = SyncEngine(dao, cloud, FakeCheckpoint(), FakeDeviceId("a"), now = { clock.now }, accountId = { cloud.accountId() })

        cloud.failNext(com.oblutack.timenote.drive.RemoteException.NeedsSignIn())
        assertIs<SyncResult.NeedsSignIn>(engine.sync())
        cloud.failNext(com.oblutack.timenote.drive.RemoteException.Network(RuntimeException("offline")))
        assertIs<SyncResult.Failed>(engine.sync())
    }

    @Test fun whenTheAccountCannotBeToldTheGuardIsOff() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val (_, checkpoint, engine) = accountReplica(cloud, clock) { null }
        assertIs<SyncResult.Done>(engine.sync())
        assertNull(checkpoint.account)
    }
}

private suspend fun Replica.syncDone(): SyncStats = (sync() as SyncResult.Done).stats
