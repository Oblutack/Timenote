package com.oblutack.timenote.data

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.data.repository.SessionRepository
import com.oblutack.timenote.feature_history.domain.mockFolders
import com.oblutack.timenote.testutil.FakeTimenoteDao
import com.oblutack.timenote.testutil.runAppTest
import com.oblutack.timenote.testutil.testFolder
import com.oblutack.timenote.testutil.testNote
import com.oblutack.timenote.testutil.testTag
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.oblutack.timenote.testutil.FakeDefaultTagsState
import com.oblutack.timenote.testutil.FakeDeviceId

class SessionRepositoryTest {

    private fun TestScope.newRepository(
        dao: FakeTimenoteDao = FakeTimenoteDao(),
        defaultTags: FakeDefaultTagsState = FakeDefaultTagsState()
    ) = SessionRepository(dao, backgroundScope, defaultTags, FakeDeviceId(), now = { 5_000L })

    @Test fun seedsDefaultTagsOnFirstLaunch() = runAppTest {
        val repo = newRepository()
        assertEquals(mockFolders.map { it.name }.sorted(), repo.tags.value.map { it.name }.sorted())
    }

    @Test fun seedingIsRememberedSoDefaultsAreNotRecreated() = runAppTest {
        val flag = FakeDefaultTagsState()
        val repo = newRepository(defaultTags = flag)
        assertTrue(flag.seeded)

        // the user deletes every tag
        repo.tags.value.forEach { repo.deleteTag(it.id) }
        assertTrue(repo.tags.value.isEmpty())

        // ...and a new launch (a new repository over the same data and the same flag) must not bring them back
        val afterRestart = newRepository(FakeTimenoteDao(), flag)
        assertTrue(afterRestart.tags.value.isEmpty())
    }

    @Test fun anExistingUserWithTagsIsMarkedAsSeededWithoutAddingAnything() = runAppTest {
        val dao = FakeTimenoteDao()
        // tags created by an older version, before the flag existed
        SessionRepository(dao, backgroundScope, FakeDefaultTagsState(seeded = true), FakeDeviceId(), now = { 5_000L }).saveTag(testTag("mine", "Mine"))
        val flag = FakeDefaultTagsState(seeded = false)

        val repo = newRepository(dao, flag)

        assertTrue(flag.seeded)
        assertEquals(listOf("Mine"), repo.tags.value.map { it.name }, "the defaults must not be added next to existing tags")
    }

    @Test fun keepsExistingTagsInsteadOfSeeding() = runAppTest {
        val dao = FakeTimenoteDao()
        val first = newRepository(dao)
        first.saveTag(testTag("custom", "Custom"))
        // a brand new repository over the same data must not re-seed
        val second = newRepository(dao)
        assertTrue(second.tags.value.any { it.id == "custom" })
        assertEquals(mockFolders.size + 1, second.tags.value.size)
    }

    @Test fun savedTimenoteShowsUpNewestFirst() = runAppTest {
        val repo = newRepository()
        repo.saveTimenote(testNote("old", createdAt = 1_000))
        repo.saveTimenote(testNote("new", createdAt = 9_000))
        assertEquals(listOf("new", "old"), repo.timenotes.value.map { it.id })
    }

    @Test fun deleteMovesToTrashWithTimestamp() = runAppTest {
        val repo = newRepository()
        repo.saveTimenote(testNote("a"))
        repo.deleteTimenote("a")
        assertTrue(repo.timenotes.value.isEmpty())
        assertEquals(5_000L, repo.deletedTimenotes.value.single().deletedAt)
    }

    @Test fun restoreBringsBackFromTrash() = runAppTest {
        val repo = newRepository()
        repo.saveTimenote(testNote("a"))
        repo.deleteTimenote("a")
        repo.restoreTimenote("a")
        assertEquals(listOf("a"), repo.timenotes.value.map { it.id })
        assertNull(repo.timenotes.value.single().deletedAt)
        assertTrue(repo.deletedTimenotes.value.isEmpty())
    }

    @Test fun hardDeleteAndEmptyTrashRemovePermanently() = runAppTest {
        val repo = newRepository()
        repo.saveTimenote(testNote("a"))
        repo.saveTimenote(testNote("b"))
        repo.saveFolder(testFolder("f"))
        repo.deleteTimenote("a"); repo.deleteTimenote("b"); repo.deleteFolder("f")
        repo.hardDeleteTimenote("a")
        assertEquals(listOf("b"), repo.deletedTimenotes.value.map { it.id })
        repo.emptyTrash()
        assertTrue(repo.deletedTimenotes.value.isEmpty())
        assertTrue(repo.deletedFolders.value.isEmpty())
    }

    @Test fun cascadeDeleteTrashesAllDescendantsButNotSiblings() = runAppTest {
        val repo = newRepository()
        listOf(
            testNote("root"), testNote("child", "root"), testNote("grandchild", "child"),
            testNote("sibling", "root"), testNote("unrelated")
        ).forEach(repo::saveTimenote)

        repo.cascadeSoftDeleteTimenote("child")

        assertEquals(setOf("child", "grandchild"), repo.deletedTimenotes.value.map { it.id }.toSet())
        assertEquals(setOf("root", "sibling", "unrelated"), repo.timenotes.value.map { it.id }.toSet())
    }

    @Test fun deleteAndOrphanTurnsDirectChildrenIntoRoots() = runAppTest {
        val repo = newRepository()
        listOf(testNote("parent"), testNote("child", "parent"), testNote("grandchild", "child"))
            .forEach(repo::saveTimenote)

        repo.deleteAndOrphanChildren("parent")

        assertEquals(listOf("parent"), repo.deletedTimenotes.value.map { it.id })
        assertNull(repo.getTimenoteById("child")?.parentTimenoteId)
        assertEquals("child", repo.getTimenoteById("grandchild")?.parentTimenoteId)
    }

    @Test fun descendantLookupUsesLoadedNotes() = runAppTest {
        val repo = newRepository()
        listOf(testNote("a"), testNote("b", "a"), testNote("c", "b")).forEach(repo::saveTimenote)
        assertEquals(setOf("b", "c"), repo.getDescendantIds("a").toSet())
    }

    @Test fun inlineEditsOnlyTouchTheirOwnField() = runAppTest {
        val repo = newRepository()
        repo.saveTimenote(testNote("a", title = "Original"))
        val tag = testTag("t", "Focus")

        repo.updateTimenoteTitle("a", "Renamed")
        repo.updateTimenoteDescription("a", "- [ ] todo")
        repo.assignFolderToTimenote("a", "folder-1")
        repo.updateTimenoteTags("a", listOf(tag))

        val note = repo.getTimenoteById("a")!!
        assertEquals("Renamed", note.title)
        assertEquals("- [ ] todo", note.description)
        assertEquals("folder-1", note.folderId)
        assertEquals(listOf("Focus"), note.tags.map { it.name })
        assertEquals(Color(0xFF4FA8F9), note.tags.single().color)

        repo.assignFolderToTimenote("a", null)
        assertNull(repo.getTimenoteById("a")?.folderId)
    }

    @Test fun voiceNotesCanBeAddedAndRemoved() = runAppTest {
        val repo = newRepository()
        repo.saveTimenote(testNote("a"))
        repo.addVoiceNote("a", "/m/1.m4a")
        repo.addVoiceNote("a", "/m/2.m4a")
        assertEquals(listOf("/m/1.m4a", "/m/2.m4a"), repo.getTimenoteById("a")?.voiceNotes)
        repo.removeVoiceNote("a", "/m/1.m4a")
        assertEquals(listOf("/m/2.m4a"), repo.getTimenoteById("a")?.voiceNotes)
    }

    @Test fun voiceNoteOnMissingTimenoteIsIgnored() = runAppTest {
        val repo = newRepository()
        repo.addVoiceNote("missing", "/m/1.m4a")
        assertTrue(repo.timenotes.value.isEmpty())
    }

    @Test fun pinsToggle() = runAppTest {
        val repo = newRepository()
        repo.saveTimenote(testNote("a"))
        repo.saveFolder(testFolder("f"))

        repo.toggleTimenotePin("a"); repo.toggleFolderPin("f")
        assertTrue(repo.getTimenoteById("a")!!.isPinned)
        assertTrue(repo.getFolderById("f")!!.isPinned)

        repo.toggleTimenotePin("a"); repo.toggleFolderPin("f")
        assertEquals(false, repo.getTimenoteById("a")!!.isPinned)
        assertEquals(false, repo.getFolderById("f")!!.isPinned)
    }

    @Test fun foldersCanBeDeletedAndRestored() = runAppTest {
        val repo = newRepository()
        repo.saveFolder(testFolder("f", "Writing"))
        repo.deleteFolder("f")
        assertTrue(repo.folders.value.isEmpty())
        assertEquals("Writing", repo.deletedFolders.value.single().name)
        repo.restoreFolder("f")
        assertEquals(listOf("f"), repo.folders.value.map { it.id })
    }

    @Test fun tagsCanBeDeletedPermanently() = runAppTest {
        val repo = newRepository()
        repo.saveTag(testTag("x", "Temp"))
        repo.deleteTag("x")
        assertTrue(repo.tags.value.none { it.id == "x" })
    }
}
