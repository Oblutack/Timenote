package com.oblutack.timenote.sync

import com.oblutack.timenote.backup.BackupService
import com.oblutack.timenote.backup.MemoryArchive
import com.oblutack.timenote.testutil.FakeDeviceId
import com.oblutack.timenote.testutil.FakeRemoteStore
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testNote
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * When two devices change the same note's text, one text wins (the newer) and the other must not just vanish:
 * it is kept as an "other version". But ordinary editing, where one device simply passes its text on to the next,
 * must never produce one.
 */
class SyncConflictTest {
    private fun TestScope.replica(name: String, cloud: FakeRemoteStore, clock: Clock) = Replica(name, backgroundScope, cloud, clock)

    private suspend fun Replica.texts() = dao.getConflictsFor("n1").map { it.text }

    @Test fun twoDevicesEditingTheSameTextKeepTheLosingOneAsAnOtherVersion() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1")); a.repo.updateTimenoteDescription("n1", "start")
        a.sync(); b.sync()

        clock.tick(); a.repo.updateTimenoteDescription("n1", "written on a")
        clock.tick(); b.repo.updateTimenoteDescription("n1", "written on b (later)")
        a.sync(); b.sync(); a.sync(); b.sync()

        // the later text wins on both devices...
        listOf(a, b).forEach { assertEquals("written on b (later)", it.dao.getAllTimenotesOnce().single().description) }
        // ...and the earlier one is not lost
        assertEquals(listOf("written on a"), b.texts())
        assertEquals(emptyList(), a.texts(), "a already knew its own text; it was passed on, not lost")
    }

    @Test fun whenTheLocalTextLosesItIsKept() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1")); a.repo.updateTimenoteDescription("n1", "start")
        a.sync(); b.sync()

        clock.tick(); b.repo.updateTimenoteDescription("n1", "older text from b")
        clock.tick(); a.repo.updateTimenoteDescription("n1", "newer text from a")
        a.sync()
        b.sync() // b's text loses against what a already uploaded

        assertEquals("newer text from a", b.dao.getAllTimenotesOnce().single().description)
        assertEquals(listOf("older text from b"), b.texts())
    }

    @Test fun ordinaryEditingNeverCreatesOtherVersions() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1"))
        a.sync(); b.sync()

        repeat(5) { round ->
            clock.tick(); a.repo.updateTimenoteDescription("n1", "a-edit-$round"); a.sync(); b.sync()
            clock.tick(); b.repo.updateTimenoteDescription("n1", "b-edit-$round"); b.sync(); a.sync()
        }

        assertEquals(emptyList(), a.texts())
        assertEquals(emptyList(), b.texts())
        assertEquals("b-edit-4", a.dao.getAllTimenotesOnce().single().description)
    }

    @Test fun theSameLostTextIsOnlyKeptOnce() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1")); a.repo.updateTimenoteDescription("n1", "start")
        a.sync(); b.sync()
        clock.tick(); a.repo.updateTimenoteDescription("n1", "from a")
        clock.tick(); b.repo.updateTimenoteDescription("n1", "from b")
        a.sync(); b.sync()

        repeat(4) { a.sync(); b.sync() }

        assertEquals(1, b.texts().size)
    }

    @Test fun linkingDevicesThatBothAlreadyWroteDifferentTextsKeepsBoth() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        // both devices have the same note (same id) from before they were ever linked
        a.repo.saveTimenote(testNote("n1")); a.repo.updateTimenoteDescription("n1", "text on a")
        clock.tick()
        b.repo.saveTimenote(testNote("n1")); b.repo.updateTimenoteDescription("n1", "text on b")

        a.sync(); b.sync(); a.sync()

        assertEquals("text on b", a.dao.getAllTimenotesOnce().single().description)
        assertEquals(listOf("text on a"), b.texts())
    }

    @Test fun anEmptyTextThatLosesIsNotWorthKeeping() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1")); a.sync(); b.sync()
        clock.tick(); a.repo.updateTimenoteDescription("n1", "")
        clock.tick(); b.repo.updateTimenoteDescription("n1", "real text")
        a.sync(); b.sync(); a.sync()
        assertEquals(emptyList(), b.texts())
        assertEquals(emptyList(), a.texts())
    }

    // ---------------------------------------------------------------- restore, dismiss, cleanup

    @Test fun restoringAnOtherVersionSwapsThemSoNothingIsEverLost() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1")); a.repo.updateTimenoteDescription("n1", "start")
        a.sync(); b.sync()
        clock.tick(); a.repo.updateTimenoteDescription("n1", "from a")
        clock.tick(); b.repo.updateTimenoteDescription("n1", "from b")
        a.sync(); b.sync()
        val lost = b.repo.conflictsFor("n1").single()
        assertEquals("from a", lost.text)

        clock.tick(); b.repo.restoreTextConflict("n1", lost.textHash)

        assertEquals("from a", b.dao.getAllTimenotesOnce().single().description)
        assertEquals(listOf("from b"), b.texts(), "the text that was replaced is now the other version")
        // and the restored text travels to the other device
        b.sync(); a.sync()
        assertEquals("from a", a.dao.getAllTimenotesOnce().single().description)
    }

    @Test fun dismissingForgetsTheOtherVersion() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1")); a.repo.updateTimenoteDescription("n1", "start")
        a.sync(); b.sync()
        clock.tick(); a.repo.updateTimenoteDescription("n1", "from a")
        clock.tick(); b.repo.updateTimenoteDescription("n1", "from b")
        a.sync(); b.sync()

        b.repo.dismissTextConflict("n1", b.repo.conflictsFor("n1").single().textHash)

        assertEquals(emptyList(), b.texts())
        assertEquals("from b", b.dao.getAllTimenotesOnce().single().description)
    }

    @Test fun otherVersionsDisappearWithTheirNote() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val a = replica("a", cloud, clock); val b = replica("b", cloud, clock)
        a.repo.saveTimenote(testNote("n1")); a.repo.updateTimenoteDescription("n1", "start")
        a.sync(); b.sync()
        clock.tick(); a.repo.updateTimenoteDescription("n1", "from a")
        clock.tick(); b.repo.updateTimenoteDescription("n1", "from b")
        a.sync(); b.sync()
        assertEquals(1, b.texts().size)

        b.repo.deleteTimenote("n1"); b.repo.hardDeleteTimenote("n1")

        assertEquals(emptyList(), b.texts())
    }

    // ---------------------------------------------------------------- backup import

    private suspend fun exportOf(replica: Replica): MemoryArchive =
        MemoryArchive().also { BackupService(replica.dao, FakeDeviceId(replica.name)).export(it) }

    @Test fun importingANewerTextKeepsTheLocalTextAsAnOtherVersion() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val phone = replica("phone", cloud, clock); val other = replica("other", cloud, clock)
        phone.repo.saveTimenote(testNote("n1")); phone.repo.updateTimenoteDescription("n1", "old local text")
        clock.tick()
        other.repo.saveTimenote(testNote("n1")); other.repo.updateTimenoteDescription("n1", "newer text in the backup")
        val backup = exportOf(other)

        BackupService(phone.dao, FakeDeviceId("phone"), now = { clock.now }, writeLock = phone.repo.writeLock).import(backup)

        assertEquals("newer text in the backup", phone.dao.getAllTimenotesOnce().single().description)
        assertEquals(listOf("old local text"), phone.texts())
    }

    @Test fun importingAnOlderBackupDoesNotNagAboutTextsThatAreStillInTheFile() = runAppTest {
        val cloud = FakeRemoteStore(); val clock = Clock()
        val phone = replica("phone", cloud, clock); val other = replica("other", cloud, clock)
        other.repo.saveTimenote(testNote("n1")); other.repo.updateTimenoteDescription("n1", "old text in the backup")
        val backup = exportOf(other)
        clock.tick()
        phone.repo.saveTimenote(testNote("n1")); phone.repo.updateTimenoteDescription("n1", "newer local text")

        BackupService(phone.dao, FakeDeviceId("phone"), now = { clock.now }, writeLock = phone.repo.writeLock).import(backup)

        assertEquals("newer local text", phone.dao.getAllTimenotesOnce().single().description)
        assertTrue(phone.texts().isEmpty())
    }
}
