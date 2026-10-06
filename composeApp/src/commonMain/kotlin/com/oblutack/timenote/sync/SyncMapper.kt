package com.oblutack.timenote.sync

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.core.audioFileName
import com.oblutack.timenote.core.formatDuration
import com.oblutack.timenote.core.parseRgbHex
import com.oblutack.timenote.core.toRgbHex
import com.oblutack.timenote.data.database.FieldNames
import com.oblutack.timenote.data.database.FieldVersionEntity
import com.oblutack.timenote.data.database.FolderEntity
import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.database.TagEntity
import com.oblutack.timenote.data.database.TimenoteEntity
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.feature_history.domain.TimenoteFolder
import com.oblutack.timenote.feature_timer.domain.EventType
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Conversion between what the database stores and the sync file format (see SyncFormat.kt).
 *
 * Both directions are lossless for everything the app shows, so "export, then import" gives back identical data.
 * Rows written before per-field tracking existed have no field versions; for them every field is stamped with the
 * row's own updatedAt and the exporting device, which is the best information available.
 */

/** "00:01:56" (or "01:56") to seconds. Anything unreadable counts as 0 rather than failing the whole export. */
fun parseDurationSeconds(text: String): Int {
    val parts = text.split(':').map { it.trim().toIntOrNull() ?: return 0 }
    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        1 -> parts[0]
        else -> 0
    }
}

private class Stamps(versions: List<FieldVersionEntity>, private val fallbackT: Long, private val deviceId: String) {
    private val byField = versions.associateBy { it.field }

    fun <T> field(name: String, value: T) =
        byField[name]?.let { SyncField(value, it.updatedAt, it.deviceId) } ?: SyncField(value, fallbackT, deviceId)

    fun entry(vararg names: String, present: Boolean): SyncSetEntry {
        val version = names.firstNotNullOfOrNull { byField[it] }
        return SyncSetEntry(present, version?.updatedAt ?: fallbackT, version?.deviceId ?: deviceId)
    }

    /** Elements that were removed: a version says "not present" for a name with this prefix. */
    fun removed(prefix: String): List<Pair<String, FieldVersionEntity>> =
        byField.values.filter { it.field.startsWith(prefix) && !it.present }.map { it.field.removePrefix(prefix) to it }
}

// ---------------------------------------------------------------- notes

fun TimenoteEntity.toSync(versions: List<FieldVersionEntity>, deviceId: String): SyncNote {
    val stamps = Stamps(versions, updatedAt, deviceId)
    val domain = toDomain()

    val tags = LinkedHashMap<String, SyncSetEntry>()
    domain.tags.forEach { tags[it.id] = stamps.entry(FieldNames.tag(it.id), present = true) }
    stamps.removed("tag:").filter { it.first !in tags }.sortedBy { it.first }.forEach { (id, v) ->
        tags[id] = SyncSetEntry(false, v.updatedAt, v.deviceId)
    }

    val voiceNotes = LinkedHashMap<String, SyncSetEntry>()
    domain.voiceNotes.forEach { ref ->
        val name = audioFileName(ref)
        voiceNotes[name] = stamps.entry(FieldNames.voice(ref), FieldNames.voice(name), present = true)
    }
    stamps.removed("voice:").map { audioFileName(it.first) to it.second }.filter { it.first !in voiceNotes }
        .sortedBy { it.first }.forEach { (name, v) -> voiceNotes[name] = SyncSetEntry(false, v.updatedAt, v.deviceId) }

    return SyncNote(
        id = id,
        createdAt = createdAt,
        fields = SyncNoteFields(
            title = stamps.field(FieldNames.TITLE, title),
            description = stamps.field(FieldNames.DESCRIPTION, description),
            folderId = stamps.field(FieldNames.FOLDER_ID, folderId),
            isPinned = stamps.field(FieldNames.IS_PINNED, isPinned),
            deletedAt = stamps.field(FieldNames.DELETED_AT, deletedAt.takeIf { isDeleted }),
            parentId = stamps.field(FieldNames.PARENT_ID, parentTimenoteId),
            parentWaypointId = stamps.field(FieldNames.PARENT_WAYPOINT_ID, parentWaypointId)
        ),
        tags = tags,
        voiceNotes = voiceNotes,
        session = SyncSession(
            activeSeconds = activeSeconds,
            pauseSeconds = pauseSeconds,
            events = domain.timelineEvents.map {
                SyncEvent(
                    id = it.id,
                    title = it.title,
                    type = it.type.name,
                    offsetSeconds = parseDurationSeconds(it.timestamp),
                    color = it.color?.toRgbHex(),
                    audio = it.audioPath?.let(::audioFileName)
                )
            }
        )
    )
}

/**
 * The database rows for a synced note. [tagLookup] provides the tag details (name, colour) that a note embeds;
 * a tag that is not known yet keeps its id so the link is not lost.
 */
fun SyncNote.toEntity(tagLookup: (String) -> TimenoteFolder?): Pair<TimenoteEntity, List<FieldVersionEntity>> {
    val present = tags.filterValues { it.present }.keys.map { tagLookup(it) ?: TimenoteFolder(it, it, null, 0, Color.Gray) }
    val voice = voiceNotes.filterValues { it.present }.keys.toList()
    val events = session.events.mapIndexed { index, e ->
        TimelineEvent(
            id = e.id,
            title = e.title,
            timestamp = formatDuration(e.offsetSeconds),
            type = runCatching { EventType.valueOf(e.type) }.getOrDefault(EventType.NOTE),
            isLastItem = index == session.events.lastIndex,
            color = e.color?.let(::parseRgbHex),
            audioPath = e.audio
        )
    }
    val deletedAt = fields.deletedAt.value

    val versions = buildList {
        fun add(name: String, t: Long, d: String, isPresent: Boolean = true) =
            add(FieldVersionEntity(SyncKind.NOTE, id, name, t, d, isPresent))
        fields.title.let { add(FieldNames.TITLE, it.t, it.d) }
        fields.description.let { add(FieldNames.DESCRIPTION, it.t, it.d) }
        fields.folderId.let { add(FieldNames.FOLDER_ID, it.t, it.d) }
        fields.isPinned.let { add(FieldNames.IS_PINNED, it.t, it.d) }
        fields.deletedAt.let { add(FieldNames.DELETED_AT, it.t, it.d) }
        fields.parentId.let { add(FieldNames.PARENT_ID, it.t, it.d) }
        fields.parentWaypointId.let { add(FieldNames.PARENT_WAYPOINT_ID, it.t, it.d) }
        tags.forEach { (tagId, e) -> add(FieldNames.tag(tagId), e.t, e.d, e.present) }
        voiceNotes.forEach { (name, e) -> add(FieldNames.voice(name), e.t, e.d, e.present) }
    }
    val updatedAt = maxOf(createdAt, versions.maxOf { it.updatedAt })

    val entity = TimenoteEntity(
        id = id,
        folderId = fields.folderId.value,
        title = fields.title.value,
        description = fields.description.value,
        audioPath = null,
        voiceNotesJson = Json.encodeToString(voice),
        duration = formatDuration(session.activeSeconds + session.pauseSeconds),
        activeSeconds = session.activeSeconds,
        pauseSeconds = session.pauseSeconds,
        createdAt = createdAt,
        tagsJson = Json.encodeToString(present),
        timelineEventsJson = Json.encodeToString(events),
        parentTimenoteId = fields.parentId.value,
        parentWaypointId = fields.parentWaypointId.value,
        isPinned = fields.isPinned.value,
        isDeleted = deletedAt != null,
        deletedAt = deletedAt,
        updatedAt = updatedAt
    )
    return entity to versions
}

// ---------------------------------------------------------------- folders

fun FolderEntity.toSync(versions: List<FieldVersionEntity>, deviceId: String): SyncFolder {
    val stamps = Stamps(versions, updatedAt, deviceId)
    return SyncFolder(
        id = id,
        createdAt = createdAt,
        fields = SyncFolderFields(
            name = stamps.field(FieldNames.NAME, name),
            description = stamps.field(FieldNames.DESCRIPTION, description),
            color = stamps.field(FieldNames.COLOR, Color(colorLong.toULong()).toRgbHex()),
            isPinned = stamps.field(FieldNames.IS_PINNED, isPinned),
            deletedAt = stamps.field(FieldNames.DELETED_AT, deletedAt.takeIf { isDeleted })
        )
    )
}

fun SyncFolder.toEntity(): Pair<FolderEntity, List<FieldVersionEntity>> {
    val versions = listOf(
        FieldVersionEntity(SyncKind.FOLDER, id, FieldNames.NAME, fields.name.t, fields.name.d),
        FieldVersionEntity(SyncKind.FOLDER, id, FieldNames.DESCRIPTION, fields.description.t, fields.description.d),
        FieldVersionEntity(SyncKind.FOLDER, id, FieldNames.COLOR, fields.color.t, fields.color.d),
        FieldVersionEntity(SyncKind.FOLDER, id, FieldNames.IS_PINNED, fields.isPinned.t, fields.isPinned.d),
        FieldVersionEntity(SyncKind.FOLDER, id, FieldNames.DELETED_AT, fields.deletedAt.t, fields.deletedAt.d)
    )
    val deletedAt = fields.deletedAt.value
    val color = parseRgbHex(fields.color.value) ?: Color.Gray
    return FolderEntity(
        id = id,
        name = fields.name.value,
        description = fields.description.value,
        colorLong = color.value.toLong(),
        createdAt = createdAt,
        isPinned = fields.isPinned.value,
        isDeleted = deletedAt != null,
        deletedAt = deletedAt,
        updatedAt = maxOf(createdAt, versions.maxOf { it.updatedAt })
    ) to versions
}

// ---------------------------------------------------------------- tags

fun TagEntity.toSync(versions: List<FieldVersionEntity>, deviceId: String): SyncTag {
    val stamps = Stamps(versions, updatedAt, deviceId)
    return SyncTag(
        id = id,
        fields = SyncTagFields(
            name = stamps.field(FieldNames.NAME, name),
            description = stamps.field(FieldNames.DESCRIPTION, description),
            color = stamps.field(FieldNames.COLOR, Color(colorLong.toULong()).toRgbHex()),
            deletedAt = stamps.field(FieldNames.DELETED_AT, deletedAt.takeIf { isDeleted })
        )
    )
}

/** [sessionCount] is a local display counter, not synced data, so the existing local value is kept. */
fun SyncTag.toEntity(sessionCount: Int = 0): Pair<TagEntity, List<FieldVersionEntity>> {
    val versions = listOf(
        FieldVersionEntity(SyncKind.TAG, id, FieldNames.NAME, fields.name.t, fields.name.d),
        FieldVersionEntity(SyncKind.TAG, id, FieldNames.DESCRIPTION, fields.description.t, fields.description.d),
        FieldVersionEntity(SyncKind.TAG, id, FieldNames.COLOR, fields.color.t, fields.color.d),
        FieldVersionEntity(SyncKind.TAG, id, FieldNames.DELETED_AT, fields.deletedAt.t, fields.deletedAt.d)
    )
    val deletedAt = fields.deletedAt.value
    val color = parseRgbHex(fields.color.value) ?: Color.Gray
    return TagEntity(
        id = id,
        name = fields.name.value,
        description = fields.description.value,
        sessionCount = sessionCount,
        colorLong = color.value.toLong(),
        updatedAt = versions.maxOf { it.updatedAt },
        isDeleted = deletedAt != null,
        deletedAt = deletedAt
    ) to versions
}
