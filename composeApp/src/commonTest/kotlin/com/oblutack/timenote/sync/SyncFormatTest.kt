package com.oblutack.timenote.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncFormatTest {

    /**
     * The contract other platforms rely on. If this test needs to change, the format changed: bump the version
     * and update the specification instead of editing the JSON quietly.
     */
    private val goldenNote = """
        {
          "v": 1,
          "id": "6f1c0c2e-1b7a-4c53-9a43-6d0c3f0b9a11",
          "createdAt": 1791297470000,
          "fields": {
            "title":            { "value": "Deep work", "t": 1791297999000, "d": "device-a" },
            "description":      { "value": "# Plan\n- [x] one", "t": 1791298100000, "d": "device-b" },
            "folderId":         { "value": "folder-1", "t": 1791297999000, "d": "device-a" },
            "isPinned":         { "value": false, "t": 1791297999000, "d": "device-a" },
            "deletedAt":        { "value": null, "t": 1791297999000, "d": "device-a" },
            "parentId":         { "value": null, "t": 1791297470000, "d": "device-a" },
            "parentWaypointId": { "value": null, "t": 1791297470000, "d": "device-a" }
          },
          "tags": {
            "tag-1": { "present": true,  "t": 1791297999000, "d": "device-a" },
            "tag-2": { "present": false, "t": 1791298200000, "d": "device-b" }
          },
          "voiceNotes": {
            "SessionMemo_1.m4a": { "present": true, "t": 1791298300000, "d": "device-a" }
          },
          "session": {
            "activeSeconds": 90,
            "pauseSeconds": 30,
            "events": [
              { "id": "evt-1", "title": "Session Started", "type": "START", "offsetSeconds": 0,   "color": null,      "audio": null },
              { "id": "evt-2", "title": "Note: bug",       "type": "NOTE",  "offsetSeconds": 116, "color": "#4FA8F9", "audio": "VoiceMemo_1.m4a" }
            ]
          }
        }
    """.trimIndent()

    private fun sampleNote() = SyncNoteFields(
        title = SyncField("Deep work", 10, "a"),
        description = SyncField("text", 11, "b"),
        folderId = SyncField(null, 12, "a"),
        isPinned = SyncField(true, 13, "a"),
        deletedAt = SyncField(null, 14, "a"),
        parentId = SyncField("parent", 15, "a"),
        parentWaypointId = SyncField("wp", 16, "a")
    ).let {
        SyncNote(
            id = "n1", createdAt = 5, fields = it,
            tags = mapOf("t1" to SyncSetEntry(true, 1, "a")),
            voiceNotes = mapOf("m.m4a" to SyncSetEntry(false, 2, "b")),
            session = SyncSession(60, 5, listOf(SyncEvent("e1", "Started", "START", 0)))
        )
    }

    @Test fun theGoldenNoteParsesToTheExpectedValues() {
        val note = SyncJson.decodeFromString<SyncNote>(goldenNote)
        assertEquals(1, note.v)
        assertEquals("Deep work", note.fields.title.value)
        assertEquals(1791298100000, note.fields.description.t)
        assertEquals("device-b", note.fields.description.d)
        assertNull(note.fields.deletedAt.value)
        assertEquals(false, note.tags.getValue("tag-2").present)
        assertEquals(setOf("SessionMemo_1.m4a"), note.voiceNotes.keys)
        assertEquals("#4FA8F9", note.session.events[1].color)
        assertEquals("VoiceMemo_1.m4a", note.session.events[1].audio)
    }

    @Test fun theGoldenNoteSurvivesAReadAndWriteUnchanged() {
        val parsed = SyncJson.decodeFromString<SyncNote>(goldenNote)
        val before: JsonElement = SyncJson.parseToJsonElement(goldenNote)
        val after: JsonElement = SyncJson.parseToJsonElement(SyncJson.encodeToString(parsed))
        assertEquals(before, after)
    }

    @Test fun aNoteRoundTrips() {
        val note = sampleNote()
        assertEquals(note, SyncJson.decodeFromString<SyncNote>(SyncJson.encodeToString(note)))
    }

    @Test fun nullValuesAreWrittenOutSoOtherPlatformsSeeTheField() {
        val text = SyncJson.encodeToString(sampleNote())
        assertTrue(text.contains("\"folderId\":{\"value\":null"), text)
    }

    @Test fun foldersAndTagsRoundTrip() {
        val folder = SyncFolder(
            id = "f1", createdAt = 7,
            fields = SyncFolderFields(
                name = SyncField("Writing", 1, "a"), description = SyncField(null, 1, "a"),
                color = SyncField("#4CAF50", 1, "a"), isPinned = SyncField(false, 1, "a"), deletedAt = SyncField(null, 1, "a")
            )
        )
        val tag = SyncTag(
            id = "t1",
            fields = SyncTagFields(
                name = SyncField("Focus", 1, "a"), description = SyncField("deep", 1, "a"),
                color = SyncField("#00E5FF", 1, "a"), deletedAt = SyncField(123L, 2, "b")
            )
        )
        assertEquals(folder, SyncJson.decodeFromString<SyncFolder>(SyncJson.encodeToString(folder)))
        assertEquals(tag, SyncJson.decodeFromString<SyncTag>(SyncJson.encodeToString(tag)))
    }

    @Test fun unknownFieldsFromANewerAppAreIgnored() {
        val withExtra = goldenNote.replaceFirst("\"v\": 1,", "\"v\": 1, \"somethingNew\": {\"a\": 1},")
        assertEquals("Deep work", SyncJson.decodeFromString<SyncNote>(withExtra).fields.title.value)
    }

    @Test fun theFormatVersionCanBeReadWithoutParsingTheWholeFile() {
        assertEquals(1, peekFormatVersion(goldenNote))
        assertEquals(7, peekFormatVersion("""{"v": 7, "anything": [1,2,3]}"""))
        assertNull(peekFormatVersion("not json"))
        assertNull(peekFormatVersion("""{"id": "no version"}"""))
        assertNull(peekFormatVersion("[1,2]"))
    }

    @Test fun newerFilesAreRecognisedAsUnsupportedSoTheyAreSkippedNotRewritten() {
        assertTrue(isSupportedFormat(1))
        assertFalse(isSupportedFormat(SYNC_FORMAT_VERSION + 1))
        assertFalse(isSupportedFormat(0))
        assertFalse(isSupportedFormat(null))
    }
}
