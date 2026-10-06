package com.oblutack.timenote.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * The language-neutral format of the files stored in the cloud (see docs/cloud-sync-plan.md, section 5).
 * Other platforms (iOS, a web app) read and write exactly this, so keep it independent of Room and Compose:
 * times are epoch milliseconds in UTC, colours are "#RRGGBB", audio is referenced by file name.
 *
 * Every file carries its format version [v]. A file with a higher version than [SYNC_FORMAT_VERSION] must be
 * skipped, never rewritten, so an older app cannot damage data written by a newer one.
 */
const val SYNC_FORMAT_VERSION = 1

val SyncJson = Json {
    ignoreUnknownKeys = true   // newer fields written by newer apps are ignored, not fatal
    encodeDefaults = true
    explicitNulls = true       // "value": null is written out, so other platforms see the field exists
    prettyPrint = false
}

/** A single value together with when ([t], epoch millis) and by which device ([d]) it was last written. */
@Serializable
data class SyncField<T>(val value: T, val t: Long, val d: String)

/** One element of a set (a tag on a note, a voice memo on a note): present or removed, with when and by whom. */
@Serializable
data class SyncSetEntry(val present: Boolean, val t: Long, val d: String)

@Serializable
data class SyncEvent(
    val id: String,
    val title: String,
    val type: String,                 // START, PAUSE, RESUME, END, NOTE
    val offsetSeconds: Int,           // seconds since the session started
    val color: String? = null,        // "#RRGGBB"
    val audio: String? = null         // voice memo file name
)

/** The recorded session. Immutable once the session has ended. */
@Serializable
data class SyncSession(
    val activeSeconds: Int,
    val pauseSeconds: Int,
    val events: List<SyncEvent>
)

@Serializable
data class SyncNoteFields(
    val title: SyncField<String>,
    val description: SyncField<String>,
    val folderId: SyncField<String?>,
    val isPinned: SyncField<Boolean>,
    val deletedAt: SyncField<Long?>,
    val parentId: SyncField<String?>,
    val parentWaypointId: SyncField<String?>
)

@Serializable
data class SyncNote(
    val v: Int = SYNC_FORMAT_VERSION,
    val id: String,
    val createdAt: Long,
    val fields: SyncNoteFields,
    val tags: Map<String, SyncSetEntry>,         // tag id -> membership
    val voiceNotes: Map<String, SyncSetEntry>,   // file name -> membership
    val session: SyncSession
)

@Serializable
data class SyncFolderFields(
    val name: SyncField<String>,
    val description: SyncField<String?>,
    val color: SyncField<String>,
    val isPinned: SyncField<Boolean>,
    val deletedAt: SyncField<Long?>
)

@Serializable
data class SyncFolder(
    val v: Int = SYNC_FORMAT_VERSION,
    val id: String,
    val createdAt: Long,
    val fields: SyncFolderFields
)

@Serializable
data class SyncTagFields(
    val name: SyncField<String>,
    val description: SyncField<String?>,
    val color: SyncField<String>,
    val deletedAt: SyncField<Long?>
)

@Serializable
data class SyncTag(
    val v: Int = SYNC_FORMAT_VERSION,
    val id: String,
    val fields: SyncTagFields
)

/** The format version declared by a file, or null if the text is not a JSON object with a numeric "v". */
fun peekFormatVersion(json: String): Int? = runCatching {
    (SyncJson.parseToJsonElement(json) as JsonObject)["v"]?.jsonPrimitive?.int
}.getOrNull()

/** True if this app understands files of that version. Newer files must be skipped, not rewritten. */
fun isSupportedFormat(version: Int?): Boolean = version != null && version in 1..SYNC_FORMAT_VERSION
