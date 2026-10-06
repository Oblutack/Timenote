package com.oblutack.timenote.backup

import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.sync.SyncJson
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testNote
import com.oblutack.timenote.testutil.testTag
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** An in-memory archive standing in for the zip file, so the service is tested without any platform code. */
class MemoryArchive(private val localAudio: MutableSet<String> = mutableSetOf()) : BackupSink, BackupSource {
    val files = linkedMapOf<String, String>()
    val restored = mutableListOf<String>()

    override fun putText(path: String, text: String) { files[path] = text }
    override fun putAudio(name: String): Boolean {
        if (name !in localAudio) return false
        files["$AUDIO_DIR$name"] = "audio-bytes"
        return true
    }
    override fun readText(path: String) = files[path]
    override fun paths(prefix: String) = files.keys.filter { it.startsWith(prefix) }
    override fun restoreAudio(name: String): Boolean { restored += name; return true }
}

class BackupServiceTest {
    private var clock = 10_000L

    private fun TestScope.populated(dao: FakeTimenoteDao): SessionRepository {
        val repo = SessionRepository(dao, backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId("phone"), now = { clock })
        repo.saveTag(testTag("t1", "Focus"))
        repo.saveTag(testTag("t2", "Study"))
        repo.saveFolder(testFolder("f1", "Writing"))
        repo.saveTimenote(
            testNote("a", title = "Deep work", activeSeconds = 90, pauseSeconds = 30).copy(
                description = "# Plan\n- [x] one",
                duration = "00:02:00", // always active + pause, as the timer saves it
                folderId = "f1",
                tags = listOf(testTag("t1", "Focus"), testTag("t2", "Study")),
                voiceNotes = listOf("memo1.m4a"),
                timelineEvents = listOf(
                    TimelineEvent("e1", "Started", "00:00:00", EventType.START),
                    TimelineEvent("e2", "Note", "00:01:10", EventType.NOTE, audioPath = "memo1.m4a", isLastItem = true)
                )
            )
        )
        repo.saveTimenote(testNote("child", parent = "a"))
        repo.saveTimenote(testNote("trashed"))
        clock = 20_000L
        repo.deleteTimenote("trashed")
        repo.toggleTimenotePin("a")
        return repo
    }

    private fun service(dao: FakeTimenoteDao, device: String = "phone") =
        BackupService(dao, FakeDeviceId(device), now = { 99_000L })

    /** What the user can see: every note, folder and tag as the app would show it. */
    private suspend fun snapshot(dao: FakeTimenoteDao) = listOf(
        dao.getAllTimenotesOnce().sortedBy { it.id }.map { it.toDomain() to it.isDeleted },
        dao.getAllFoldersOnce().sortedBy { it.id }.map { it.toDomain() to it.isDeleted },
        dao.getAllTagsOnce().sortedBy { it.id }.map { it.toDomain() to it.isDeleted }
    )

    @Test fun exportWritesOneFilePerItemAndAManifestLast() = runAppTest {
        val dao = FakeTimenoteDao(); populated(dao)
        val archive = MemoryArchive(mutableSetOf("memo1.m4a"))

        val result = service(dao).export(archive)

        assertEquals(ExportResult(notes = 3, folders = 1, tags = 2, audioFiles = 1, missingAudio = 0), result)
        assertEquals(setOf("notes/a.json", "notes/child.json", "notes/trashed.json", "folders/f1.json", "tags/t1.json", "tags/t2.json", "audio/memo1.m4a", "manifest.json"), archive.files.keys)
        assertEquals("manifest.json", archive.files.keys.last())
        val manifest = SyncJson.decodeFromString<BackupManifest>(archive.files.getValue("manifest.json"))
        assertEquals(listOf(3, 1, 2, 1), listOf(manifest.notes, manifest.folders, manifest.tags, manifest.audio))
        assertEquals("phone", manifest.device)
    }

    @Test fun missingVoiceMemoFilesAreReportedNotFatal() = runAppTest {
        val dao = FakeTimenoteDao(); populated(dao)
        val result = service(dao).export(MemoryArchive(mutableSetOf()))
        assertEquals(1, result.missingAudio)
        assertEquals(3, result.notes)
    }

    @Test fun exportThenImportIntoAnEmptyAppRestoresEverythingIdentically() = runAppTest {
        val source = FakeTimenoteDao(); populated(source)
        val archive = MemoryArchive(mutableSetOf("memo1.m4a"))
        service(source).export(archive)

        val fresh = FakeTimenoteDao()
        val result = service(fresh, device = "new-phone").import(archive)

        assertIs<ImportResult.Done>(result)
        assertEquals(3, result.notesAdded); assertEquals(1, result.foldersAdded); assertEquals(2, result.tagsAdded)
        assertEquals(listOf("memo1.m4a"), archive.restored)
        assertEquals(snapshot(source), snapshot(fresh))
    }

    @Test fun importingTheSameBackupTwiceChangesNothing() = runAppTest {
        val source = FakeTimenoteDao(); populated(source)
        val archive = MemoryArchive(mutableSetOf("memo1.m4a"))
        service(source).export(archive)

        val fresh = FakeTimenoteDao()
        service(fresh, "new").import(archive)
        val after1 = snapshot(fresh)
        val second = service(fresh, "new").import(archive)

        assertEquals(ImportResult.Done(0, 0, 0, 0, 0, 0, 1, 0), second)
        assertEquals(after1, snapshot(fresh))
    }

    @Test fun importingOntoTheSameDeviceChangesNothing() = runAppTest {
        val dao = FakeTimenoteDao(); populated(dao)
        val before = snapshot(dao)
        val archive = MemoryArchive(mutableSetOf("memo1.m4a"))
        service(dao).export(archive)

        val result = service(dao).import(archive) as ImportResult.Done

        assertEquals(listOf(0, 0, 0), listOf(result.notesAdded, result.foldersAdded, result.tagsAdded))
        assertEquals(0, result.notesUpdated)
        assertEquals(before, snapshot(dao))
    }

    @Test fun importNeverDeletesWhatIsAlreadyOnThisDevice() = runAppTest {
        val source = FakeTimenoteDao(); populated(source)
        val archive = MemoryArchive()
        service(source).export(archive)

        val other = FakeTimenoteDao()
        SessionRepository(other, backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId("tablet"), now = { 5_000L })
            .saveTimenote(testNote("only-here", title = "Local only"))

        service(other, "tablet").import(archive)

        val ids = other.getAllTimenotesOnce().map { it.id }.toSet()
        assertTrue("only-here" in ids)
        assertEquals(setOf("a", "child", "trashed", "only-here"), ids)
    }

    @Test fun eachFieldKeepsItsNewestEditWhenBothSidesChanged() = runAppTest {
        val phone = FakeTimenoteDao(); populated(phone)
        val archive = MemoryArchive()
        // the backup is taken, then the title is changed on the phone later and the tag set on the "tablet"
        clock = 50_000L
        SessionRepository(phone, backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId("phone"), now = { clock })
            .also { it.updateTimenoteTitle("a", "Phone title") }
        // the tablet's copy (older state) with a newer description
        val tablet = FakeTimenoteDao(); populated(tablet)
        clock = 60_000L
        val tabletRepo = SessionRepository(tablet, backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId("tablet"), now = { clock })
        tabletRepo.updateTimenoteDescription("a", "Tablet text")
        service(tablet, "tablet").export(archive)

        service(phone, "phone").import(archive)

        val merged = phone.getAllTimenotesOnce().single { it.id == "a" }
        assertEquals("Phone title", merged.title, "the phone edited the title later than the backup")
        assertEquals("Tablet text", merged.description, "the backup edited the text later than the phone")
    }

    @Test fun trashedItemsStayTrashed() = runAppTest {
        val source = FakeTimenoteDao(); populated(source)
        val archive = MemoryArchive()
        service(source).export(archive)
        val fresh = FakeTimenoteDao()
        service(fresh, "x").import(archive)
        val trashed = fresh.getAllTimenotesOnce().single { it.id == "trashed" }
        assertTrue(trashed.isDeleted)
        assertEquals(20_000L, trashed.deletedAt)
    }

    @Test fun somethingThatIsNotABackupIsRejectedWithoutTouchingData() = runAppTest {
        val dao = FakeTimenoteDao(); populated(dao)
        val before = snapshot(dao)
        val svc = service(dao)

        assertIs<ImportResult.NotABackup>(svc.import(MemoryArchive()))
        assertIs<ImportResult.NotABackup>(svc.import(MemoryArchive().apply { files["manifest.json"] = "garbage" }))
        assertIs<ImportResult.NotABackup>(svc.import(MemoryArchive().apply {
            files["manifest.json"] = """{"v":1,"app":"other-app","createdAt":1,"device":"d","notes":0,"folders":0,"tags":0,"audio":0}"""
        }))
        assertEquals(before, snapshot(dao))
    }

    @Test fun aBackupFromANewerAppVersionIsRefusedNotMisread() = runAppTest {
        val dao = FakeTimenoteDao()
        val archive = MemoryArchive().apply {
            files["manifest.json"] = """{"v":2,"app":"timenote","createdAt":1,"device":"d","notes":1,"folders":0,"tags":0,"audio":0}"""
            files["notes/x.json"] = "whatever"
        }
        assertIs<ImportResult.NewerFormat>(service(dao).import(archive))
        assertTrue(dao.getAllTimenotesOnce().isEmpty())
    }

    @Test fun damagedOrTooNewFilesAreSkippedAndTheRestStillImports() = runAppTest {
        val source = FakeTimenoteDao(); populated(source)
        val archive = MemoryArchive()
        service(source).export(archive)
        archive.files["notes/a.json"] = "{ this is not json"
        archive.files["notes/future.json"] = """{"v":9,"id":"future"}"""
        archive.files.remove("tags/t2.json")

        val fresh = FakeTimenoteDao()
        val result = service(fresh, "x").import(archive) as ImportResult.Done

        assertEquals(2, result.skipped)
        assertEquals(setOf("child", "trashed"), fresh.getAllTimenotesOnce().map { it.id }.toSet())
        assertEquals(setOf("t1"), fresh.getAllTagsOnce().map { it.id }.toSet())
    }

    @Test fun voiceMemoNamesFromTheFileCannotEscapeTheMemoFolder() = runAppTest {
        val archive = MemoryArchive().apply {
            files["manifest.json"] = """{"v":1,"app":"timenote","createdAt":1,"device":"d","notes":0,"folders":0,"tags":0,"audio":3}"""
            files["audio/good.m4a"] = "x"
            files["audio/../../evil.m4a"] = "x"
            files["audio/sub/dir.m4a"] = "x"
            files["audio/.."] = "x"
        }
        service(FakeTimenoteDao()).import(archive)
        assertEquals(listOf("good.m4a"), archive.restored)
    }

    @Test fun aTagFromBeforeChangeTrackingBeatsTheDefaultOfAFreshInstall() = runAppTest {
        // The user renamed the default tag "Work" long ago; their tag has no per-field history (time 0)
        val old = FakeTimenoteDao()
        old.insertTag(com.oblutack.timenote.data.database.TagEntity("1", "Job", "my job", 0, 0L, updatedAt = 0L))
        val archive = MemoryArchive()
        service(old, "old-phone").export(archive)

        // A fresh install creates the default tags first, then the backup is imported
        val fresh = FakeTimenoteDao()
        SessionRepository(fresh, backgroundScope, FakeDefaultTagsState(), FakeDeviceId("zzz-new-phone"), now = { clock })
        assertEquals("Work", fresh.getAllTagsOnce().single { it.id == "1" }.name)

        service(fresh, "zzz-new-phone").import(archive)

        val tag = fresh.getAllTagsOnce().single { it.id == "1" }
        assertEquals("Job", tag.name, "the user's own tag must win, whatever the device ids are")
        assertEquals("my job", tag.description)
    }

    @Test fun anEmptyBackupOfAnEmptyAppRoundTrips() = runAppTest {
        val archive = MemoryArchive()
        assertEquals(ExportResult(0, 0, 0, 0, 0), service(FakeTimenoteDao()).export(archive))
        assertEquals(ImportResult.Done(0, 0, 0, 0, 0, 0, 0, 0), service(FakeTimenoteDao()).import(archive))
    }
}
