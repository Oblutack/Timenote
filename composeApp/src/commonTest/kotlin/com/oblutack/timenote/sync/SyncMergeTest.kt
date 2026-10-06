package com.oblutack.timenote.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncMergeTest {

    private fun note(
        title: Pair<String, Long> = "T" to 10L,
        description: Pair<String, Long> = "D" to 10L,
        deletedAt: Pair<Long?, Long> = null to 10L,
        device: String = "a",
        tags: Map<String, SyncSetEntry> = emptyMap(),
        voice: Map<String, SyncSetEntry> = emptyMap(),
        events: List<SyncEvent> = listOf(SyncEvent("e1", "Started", "START", 0)),
        createdAt: Long = 5L
    ) = SyncNote(
        id = "n", createdAt = createdAt,
        fields = SyncNoteFields(
            title = SyncField(title.first, title.second, device),
            description = SyncField(description.first, description.second, device),
            folderId = SyncField(null, 10, device),
            isPinned = SyncField(false, 10, device),
            deletedAt = SyncField(deletedAt.first, deletedAt.second, device),
            parentId = SyncField(null, 10, device),
            parentWaypointId = SyncField(null, 10, device)
        ),
        tags = tags, voiceNotes = voice,
        session = SyncSession(60, 0, events)
    )

    private fun on(t: Long, device: String, present: Boolean = true) = SyncSetEntry(present, t, device)

    @Test fun eachFieldTakesItsNewestWriteSoNeitherDevicesEditIsLost() {
        val phone = note(title = "Phone title" to 30L, description = "old" to 10L, device = "phone")
        val tablet = note(title = "old" to 10L, description = "Tablet text" to 40L, device = "tablet")

        val merged = mergeNotes(phone, tablet)

        assertEquals("Phone title", merged.fields.title.value)
        assertEquals("Tablet text", merged.fields.description.value)
    }

    @Test fun equalTimesAreDecidedByDeviceIdSoEveryDevicePicksTheSameWinner() {
        val a = note(title = "from a" to 10L, device = "a")
        val b = note(title = "from b" to 10L, device = "b")
        assertEquals("from b", mergeNotes(a, b).fields.title.value)
        assertEquals("from b", mergeNotes(b, a).fields.title.value)
    }

    @Test fun mergingIsSymmetricAndIdempotent() {
        val a = note(title = "x" to 11L, device = "a", tags = mapOf("t1" to on(5, "a")), voice = mapOf("m" to on(5, "a")))
        val b = note(title = "y" to 12L, device = "b", tags = mapOf("t2" to on(6, "b")), voice = mapOf("n" to on(6, "b")))
        assertEquals(mergeNotes(a, b).toComparable(), mergeNotes(b, a).toComparable())
        assertEquals(a, mergeNotes(a, a))
        val ab = mergeNotes(a, b)
        assertEquals(ab, mergeNotes(ab, a))
        assertEquals(ab, mergeNotes(ab, b))
    }

    /** Map order is not part of the result, so compare sets of entries. */
    private fun SyncNote.toComparable() = copy(tags = tags.toSortedMap(), voiceNotes = voiceNotes.toSortedMap())

    @Test fun addingATagOnOneDeviceAndAnotherOnTheOtherKeepsBoth() {
        val a = note(tags = mapOf("t1" to on(20, "a")))
        val b = note(tags = mapOf("t2" to on(21, "b")))
        assertEquals(setOf("t1", "t2"), mergeNotes(a, b).tags.filterValues { it.present }.keys)
    }

    @Test fun aNewerRemovalBeatsAnOlderAddAndTheOtherWayAround() {
        val added = note(tags = mapOf("t1" to on(20, "a", present = true)))
        val removedLater = note(tags = mapOf("t1" to on(30, "b", present = false)))
        assertFalse(mergeNotes(added, removedLater).tags.getValue("t1").present)

        val removedEarlier = note(tags = mapOf("t1" to on(10, "b", present = false)))
        assertTrue(mergeNotes(added, removedEarlier).tags.getValue("t1").present, "re-adding after a removal wins")
    }

    @Test fun voiceNotesMergeLikeTagsAndNothingIsDropped() {
        val a = note(voice = mapOf("one.m4a" to on(20, "a")))
        val b = note(voice = mapOf("one.m4a" to on(10, "b"), "two.m4a" to on(25, "b")))
        assertEquals(listOf("one.m4a", "two.m4a"), mergeNotes(a, b).voiceNotes.keys.toList())
    }

    @Test fun aNewerDeleteWinsButANewerEditRestoresTheNote() {
        val deleted = note(deletedAt = 100L to 50L, device = "a")
        val editedBefore = note(deletedAt = null to 10L, title = "edited" to 20L, device = "b")
        assertEquals(100L, mergeNotes(deleted, editedBefore).fields.deletedAt.value)

        val restoredAfter = note(deletedAt = null to 60L, device = "b")
        assertEquals(null, mergeNotes(deleted, restoredAfter).fields.deletedAt.value)
    }

    @Test fun theRecordedTimelineIsUnitedByEventIdAndNeverShrinks() {
        val start = SyncEvent("e1", "Started", "START", 0)
        val note1 = SyncEvent("e2", "Note", "NOTE", 30)
        val end = SyncEvent("e3", "Ended", "END", 60)
        val a = note(events = listOf(start, note1, end))
        val b = note(events = listOf(start, end))
        assertEquals(listOf("e1", "e2", "e3"), mergeNotes(a, b).session.events.map { it.id })
        assertEquals(listOf("e1", "e2", "e3"), mergeNotes(b, a).session.events.map { it.id })
    }

    @Test fun differentTimelinesOfTheSameSizeStillMergeTheSameInBothDirections() {
        val start = SyncEvent("e1", "Started", "START", 0)
        val a = note(events = listOf(start, SyncEvent("x", "From a", "NOTE", 10)))
        val b = note(events = listOf(start, SyncEvent("y", "From b", "NOTE", 20)))
        assertEquals(mergeNotes(a, b).session, mergeNotes(b, a).session)
        assertEquals(setOf("e1", "x", "y"), mergeNotes(a, b).session.events.map { it.id }.toSet())
    }

    @Test fun creationTimeIsTheEarliestOne() {
        assertEquals(3L, mergeNotes(note(createdAt = 3), note(createdAt = 9)).createdAt)
    }

    @Test fun mergingDifferentNotesIsRejected() {
        val other = note().copy(id = "other")
        var failed = false
        try { mergeNotes(note(), other) } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }

    @Test fun foldersAndTagsMergePerField() {
        fun folder(name: Pair<String, Long>, color: Pair<String, Long>, d: String) = SyncFolder(
            id = "f", createdAt = 1,
            fields = SyncFolderFields(
                name = SyncField(name.first, name.second, d), description = SyncField(null, 1, d),
                color = SyncField(color.first, color.second, d), isPinned = SyncField(false, 1, d), deletedAt = SyncField(null, 1, d)
            )
        )
        val merged = mergeFolders(folder("New name" to 9, "#000000" to 1, "a"), folder("old" to 1, "#FF0000" to 8, "b"))
        assertEquals("New name", merged.fields.name.value)
        assertEquals("#FF0000", merged.fields.color.value)

        fun tag(name: Pair<String, Long>, deleted: Pair<Long?, Long>, d: String) = SyncTag(
            id = "t",
            fields = SyncTagFields(SyncField(name.first, name.second, d), SyncField(null, 1, d), SyncField("#4FA8F9", 1, d), SyncField(deleted.first, deleted.second, d))
        )
        val mergedTag = mergeTags(tag("Focus" to 5, null to 1, "a"), tag("old" to 1, 77L to 6, "b"))
        assertEquals("Focus", mergedTag.fields.name.value)
        assertEquals(77L, mergedTag.fields.deletedAt.value)
    }
}
