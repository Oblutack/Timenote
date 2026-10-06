package com.oblutack.timenote.sync

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.data.database.FieldNames
import com.oblutack.timenote.data.database.FieldVersionEntity
import com.oblutack.timenote.data.database.FolderEntity
import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.database.TagEntity
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.data.database.toEntity
import com.oblutack.timenote.feature_history.domain.Timenote
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import com.oblutack.timenote.testutil.testNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncMapperTest {
    private val work = TimenoteFolder("1", "Work", null, 0, Color(0xFF4FA8F9))
    private val study = TimenoteFolder("2", "Study", "exams", 0, Color(0xFF4CAF50))
    private val lookup: (String) -> TimenoteFolder? = { id -> listOf(work, study).find { it.id == id } }

    private fun fullNote() = testNote("n1", createdAt = 1_790_000_000_000L).copy(
        title = "Deep work",
        description = "# Plan\n- [x] one",
        folderId = "f1",
        isPinned = true,
        voiceNotes = listOf("SessionMemo_1.m4a", "VoiceMemo_2.m4a"),
        activeSeconds = 90,
        pauseSeconds = 30,
        duration = "00:02:00",
        tags = listOf(study, work),
        parentTimenoteId = "parent",
        parentWaypointId = "wp",
        timelineEvents = listOf(
            TimelineEvent("e1", "Session Started", "00:00:00", EventType.START),
            TimelineEvent("e2", "Note: bug", "00:01:56", EventType.NOTE, color = Color(0xFF4FA8F9), audioPath = "VoiceMemo_2.m4a"),
            TimelineEvent("e3", "Session Ended", "00:02:00", EventType.END, isLastItem = true)
        )
    )

    private fun stamped(n: Timenote, updatedAt: Long = 1_790_000_500_000L) = n.toEntity(updatedAt)

    @Test fun durationTextParses() {
        assertEquals(116, parseDurationSeconds("00:01:56"))
        assertEquals(3_725, parseDurationSeconds("01:02:05"))
        assertEquals(116, parseDurationSeconds("01:56"))
        assertEquals(0, parseDurationSeconds("junk"))
        assertEquals(0, parseDurationSeconds(""))
    }

    @Test fun aNoteSurvivesExportAndImportExactly() {
        val original = stamped(fullNote())
        val sync = original.toSync(emptyList(), "dev")
        val (back, _) = sync.toEntity(lookup)
        assertEquals(original, back)
    }

    @Test fun theSyncFileHasPortableValues() {
        val sync = stamped(fullNote()).toSync(emptyList(), "dev")
        assertEquals("Deep work", sync.fields.title.value)
        assertEquals(setOf("1", "2"), sync.tags.keys)
        assertEquals(listOf("SessionMemo_1.m4a", "VoiceMemo_2.m4a"), sync.voiceNotes.keys.toList())
        val noteEvent = sync.session.events[1]
        assertEquals(116, noteEvent.offsetSeconds)
        assertEquals("#4FA8F9", noteEvent.color)
        assertEquals("VoiceMemo_2.m4a", noteEvent.audio)
        assertEquals("NOTE", noteEvent.type)
    }

    @Test fun legacyAbsoluteVoicePathsBecomeFileNamesInTheFile() {
        val legacy = fullNote().copy(
            voiceNotes = listOf("/data/user/0/old/files/voice_memos/SessionMemo_1.m4a"),
            timelineEvents = listOf(TimelineEvent("e", "n", "00:00:05", EventType.NOTE, audioPath = "C:\\x\\Memo.m4a"))
        )
        val sync = stamped(legacy).toSync(emptyList(), "dev")
        assertEquals(listOf("SessionMemo_1.m4a"), sync.voiceNotes.keys.toList())
        assertEquals("Memo.m4a", sync.session.events.single().audio)
    }

    @Test fun rowsWithoutFieldVersionsUseTheRowTimeAndTheExportingDevice() {
        val sync = stamped(fullNote(), updatedAt = 777).toSync(emptyList(), "dev")
        assertEquals(777, sync.fields.title.t)
        assertEquals("dev", sync.fields.title.d)
        assertEquals(777, sync.tags.getValue("1").t)
    }

    @Test fun recordedFieldVersionsAreCarriedIntoTheFile() {
        val versions = listOf(
            FieldVersionEntity(SyncKind.NOTE, "n1", FieldNames.TITLE, 5_000, "phone"),
            FieldVersionEntity(SyncKind.NOTE, "n1", FieldNames.tag("1"), 6_000, "tablet"),
            FieldVersionEntity(SyncKind.NOTE, "n1", FieldNames.tag("gone"), 7_000, "tablet", present = false),
            FieldVersionEntity(SyncKind.NOTE, "n1", FieldNames.voice("old.m4a"), 8_000, "phone", present = false)
        )
        val sync = stamped(fullNote()).toSync(versions, "dev")

        assertEquals(5_000L to "phone", sync.fields.title.t to sync.fields.title.d)
        assertEquals(6_000L to "tablet", sync.tags.getValue("1").t to sync.tags.getValue("1").d)
        assertEquals(false, sync.tags.getValue("gone").present, "removals travel as tombstones")
        assertEquals(false, sync.voiceNotes.getValue("old.m4a").present)
    }

    @Test fun importingRebuildsFieldVersionsSoLaterMergesStayCorrect() {
        val sync = stamped(fullNote()).toSync(
            listOf(FieldVersionEntity(SyncKind.NOTE, "n1", FieldNames.tag("gone"), 7_000, "tablet", present = false)), "dev"
        )
        val (entity, versions) = sync.toEntity(lookup)

        assertTrue(versions.any { it.field == FieldNames.TITLE && it.entityKind == SyncKind.NOTE })
        assertEquals(false, versions.single { it.field == FieldNames.tag("gone") }.present)
        assertTrue(entity.updatedAt >= versions.maxOf { it.updatedAt })
        assertTrue(entity.toDomain().tags.none { it.id == "gone" }, "a removed tag is not shown")
    }

    @Test fun aTrashedNoteStaysTrashed() {
        val trashed = fullNote().copy(isDeleted = true, deletedAt = 1_790_100_000_000L)
        val original = trashed.toEntity(1_790_100_000_000L)
        val (back, _) = original.toSync(emptyList(), "dev").toEntity(lookup)
        assertTrue(back.isDeleted)
        assertEquals(1_790_100_000_000L, back.deletedAt)
        assertEquals(original, back)
    }

    @Test fun anActiveNoteHasNoDeletedAtInTheFile() {
        assertNull(stamped(fullNote()).toSync(emptyList(), "dev").fields.deletedAt.value)
    }

    @Test fun aTagThatIsNotKnownYetIsKeptByIdInsteadOfDropped() {
        val sync = stamped(fullNote()).toSync(emptyList(), "dev")
        val (entity, _) = sync.toEntity { null }
        assertEquals(setOf("1", "2"), entity.toDomain().tags.map { it.id }.toSet())
    }

    @Test fun unknownEventTypesFromANewerAppBecomeNotes() {
        val sync = stamped(fullNote()).toSync(emptyList(), "dev")
        val odd = sync.copy(session = sync.session.copy(events = listOf(SyncEvent("x", "t", "TELEPORT", 1))))
        assertEquals(EventType.NOTE, odd.toEntity(lookup).first.toDomain().timelineEvents.single().type)
    }

    @Test fun foldersRoundTripIncludingTrashAndPin() {
        val folder = FolderEntity("f1", "Writing", "novel", Color(0xFF4CAF50).value.toLong(), 1_000, true, false, null, 5_000)
        val trashed = FolderEntity("f2", "Old", null, Color(0xFFFF9800).value.toLong(), 900, false, true, 8_000, 8_000)
        listOf(folder, trashed).forEach {
            assertEquals(it, it.toSync(emptyList(), "dev").toEntity().first)
        }
        assertEquals("#4CAF50", folder.toSync(emptyList(), "dev").fields.color.value)
    }

    @Test fun tagsRoundTripAndKeepTheirDeletion() {
        val tag = TagEntity("t1", "Focus", "deep", 3, Color(0xFF00E5FF).value.toLong(), 5_000, false, null)
        val gone = TagEntity("t2", "Old", null, 0, Color(0xFF4FA8F9).value.toLong(), 9_000, true, 9_000)
        assertEquals(tag.copy(sessionCount = 0), tag.toSync(emptyList(), "dev").toEntity().first.copy(updatedAt = 5_000))
        val (backGone, _) = gone.toSync(emptyList(), "dev").toEntity()
        assertTrue(backGone.isDeleted)
        assertEquals(9_000L, backGone.deletedAt)
    }

    @Test fun anInvalidColourFallsBackInsteadOfFailingTheImport() {
        val sync = FolderEntity("f", "n", null, Color(0xFF4CAF50).value.toLong(), 1, false, false, null, 1)
            .toSync(emptyList(), "d")
        val broken = sync.copy(fields = sync.fields.copy(color = SyncField("not-a-colour", 1, "d")))
        assertEquals(Color.Gray.value.toLong(), broken.toEntity().first.colorLong)
    }
}
