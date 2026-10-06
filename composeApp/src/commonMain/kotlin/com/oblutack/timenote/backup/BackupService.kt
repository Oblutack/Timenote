package com.oblutack.timenote.backup

import com.oblutack.timenote.core.audioFileName
import com.oblutack.timenote.data.database.FieldVersionEntity
import com.oblutack.timenote.data.database.SyncKind
import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.data.database.toDomain
import com.oblutack.timenote.data.repository.DeviceIdSource
import com.oblutack.timenote.getCurrentTimeMillis
import com.oblutack.timenote.sync.SYNC_FORMAT_VERSION
import com.oblutack.timenote.sync.SyncFolder
import com.oblutack.timenote.sync.SyncJson
import com.oblutack.timenote.sync.SyncNote
import com.oblutack.timenote.sync.SyncTag
import com.oblutack.timenote.sync.isSupportedFormat
import com.oblutack.timenote.sync.mergeFolders
import com.oblutack.timenote.sync.mergeNotes
import com.oblutack.timenote.sync.mergeTags
import com.oblutack.timenote.sync.peekFormatVersion
import com.oblutack.timenote.sync.toEntity
import com.oblutack.timenote.sync.toSync
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Where a backup is written to. The platform decides how (a zip file on Android); this class only says what goes in.
 * Paths are archive paths such as "notes/<id>.json".
 */
interface BackupSink {
    fun putText(path: String, text: String)

    /** Adds the voice memo with this file name from local storage. False if the file does not exist (any more). */
    fun putAudio(name: String): Boolean
}

/** Where a backup is read from. */
interface BackupSource {
    fun readText(path: String): String?

    /** Archive paths that start with [prefix], for example "notes/". */
    fun paths(prefix: String): List<String>

    /** Copies "audio/<name>" into local voice memo storage unless a file with that name is already there. */
    fun restoreAudio(name: String): Boolean
}

@Serializable
data class BackupManifest(
    val v: Int = SYNC_FORMAT_VERSION,
    val app: String = APP_NAME,
    val createdAt: Long,
    val device: String,
    val notes: Int,
    val folders: Int,
    val tags: Int,
    val audio: Int
)

const val APP_NAME = "timenote"
const val MANIFEST_PATH = "manifest.json"
const val NOTES_DIR = "notes/"
const val FOLDERS_DIR = "folders/"
const val TAGS_DIR = "tags/"
const val AUDIO_DIR = "audio/"

data class ExportResult(val notes: Int, val folders: Int, val tags: Int, val audioFiles: Int, val missingAudio: Int)

sealed class ImportResult {
    data class Done(
        val notesAdded: Int, val notesUpdated: Int,
        val foldersAdded: Int, val foldersUpdated: Int,
        val tagsAdded: Int, val tagsUpdated: Int,
        val audioRestored: Int,
        /** Files that were unreadable or written by a newer app version; they are left out, never guessed at. */
        val skipped: Int
    ) : ImportResult()

    /** Not a Timenote backup at all. */
    data object NotABackup : ImportResult()

    /** Made by a newer version of the app than this one. Nothing was imported. */
    data object NewerFormat : ImportResult()
}

/**
 * Export and import of everything the user has, as the same files the cloud sync will use.
 *
 * Import is a MERGE with the rules from SyncMerge.kt: nothing that is already on this device is deleted, and where
 * both sides have the item the newest edit of each field wins. Importing the same backup twice changes nothing.
 */
class BackupService(
    private val dao: TimenoteDao,
    private val deviceIdSource: DeviceIdSource,
    private val now: () -> Long = ::getCurrentTimeMillis
) {

    suspend fun export(sink: BackupSink): ExportResult {
        val device = deviceIdSource.deviceId()
        val versions = dao.getAllFieldVersions().groupBy { it.entityKind to it.entityId }
        fun versionsOf(kind: String, id: String) = versions[kind to id].orEmpty()

        val notes = dao.getAllTimenotesOnce()
        val folders = dao.getAllFoldersOnce()
        val tags = dao.getAllTagsOnce()

        val audioNames = LinkedHashSet<String>()
        notes.forEach { entity ->
            val sync = entity.toSync(versionsOf(SyncKind.NOTE, entity.id), device)
            sink.putText("$NOTES_DIR${entity.id}.json", SyncJson.encodeToString(sync))
            sync.voiceNotes.filterValues { it.present }.keys.forEach { audioNames += it }
            sync.session.events.mapNotNull { it.audio }.forEach { audioNames += it }
        }
        folders.forEach { sink.putText("$FOLDERS_DIR${it.id}.json", SyncJson.encodeToString(it.toSync(versionsOf(SyncKind.FOLDER, it.id), device))) }
        tags.forEach { sink.putText("$TAGS_DIR${it.id}.json", SyncJson.encodeToString(it.toSync(versionsOf(SyncKind.TAG, it.id), device))) }

        var written = 0
        var missing = 0
        audioNames.forEach { if (sink.putAudio(it)) written++ else missing++ }

        // The manifest goes in last: a backup without it is recognisably incomplete
        val manifest = BackupManifest(createdAt = now(), device = device, notes = notes.size, folders = folders.size, tags = tags.size, audio = written)
        sink.putText(MANIFEST_PATH, SyncJson.encodeToString(manifest))
        return ExportResult(notes.size, folders.size, tags.size, written, missing)
    }

    suspend fun import(source: BackupSource): ImportResult {
        val manifestText = source.readText(MANIFEST_PATH) ?: return ImportResult.NotABackup
        val manifest = runCatching { SyncJson.decodeFromString<BackupManifest>(manifestText) }.getOrNull()
            ?: return ImportResult.NotABackup
        if (manifest.app != APP_NAME) return ImportResult.NotABackup
        if (!isSupportedFormat(manifest.v)) return ImportResult.NewerFormat

        val device = deviceIdSource.deviceId()
        val versions = dao.getAllFieldVersions().groupBy { it.entityKind to it.entityId }
        fun versionsOf(kind: String, id: String) = versions[kind to id].orEmpty()
        var skipped = 0

        /** Reads and decodes every file under [prefix]; unreadable or too-new files are counted and left out. */
        fun <T> items(prefix: String, decode: (String) -> T): List<T> = source.paths(prefix).mapNotNull { path ->
            val text = source.readText(path)
            if (text == null || !isSupportedFormat(peekFormatVersion(text))) { skipped++; return@mapNotNull null }
            runCatching { decode(text) }.getOrElse { skipped++; null }
        }

        // 1. tags first: notes embed their tag details
        var tagsAdded = 0; var tagsUpdated = 0
        val localTags = dao.getAllTagsOnce().associateBy { it.id }
        for (remote in items(TAGS_DIR) { SyncJson.decodeFromString<SyncTag>(it) }) {
            val local = localTags[remote.id]
            val localSync = local?.toSync(versionsOf(SyncKind.TAG, remote.id), device)
            val merged = localSync?.let { mergeTags(it, remote) } ?: remote
            if (merged == localSync) continue
            val (entity, stamps) = merged.toEntity(sessionCount = local?.sessionCount ?: 0)
            dao.insertTag(entity)
            stamps.forEach { dao.upsertFieldVersion(it) }
            if (local == null) tagsAdded++ else tagsUpdated++
        }

        // 2. folders
        var foldersAdded = 0; var foldersUpdated = 0
        val localFolders = dao.getAllFoldersOnce().associateBy { it.id }
        for (remote in items(FOLDERS_DIR) { SyncJson.decodeFromString<SyncFolder>(it) }) {
            val local = localFolders[remote.id]
            val localSync = local?.toSync(versionsOf(SyncKind.FOLDER, remote.id), device)
            val merged = localSync?.let { mergeFolders(it, remote) } ?: remote
            if (merged == localSync) continue
            val (entity, stamps) = merged.toEntity()
            dao.insertFolder(entity)
            stamps.forEach { dao.upsertFieldVersion(it) }
            if (local == null) foldersAdded++ else foldersUpdated++
        }

        // 3. notes (tag details now come from the merged tag table)
        val tagDetails = dao.getAllTagsOnce().filter { !it.isDeleted }.associate { it.id to it.toDomain() }
        var notesAdded = 0; var notesUpdated = 0
        val localNotes = dao.getAllTimenotesOnce().associateBy { it.id }
        for (remote in items(NOTES_DIR) { SyncJson.decodeFromString<SyncNote>(it) }) {
            val local = localNotes[remote.id]
            val localSync = local?.toSync(versionsOf(SyncKind.NOTE, remote.id), device)
            val merged = localSync?.let { mergeNotes(it, remote) } ?: remote
            if (merged == localSync) continue
            val (entity, stamps) = merged.toEntity { tagDetails[it] }
            dao.insertTimenote(entity)
            stamps.forEach { dao.upsertFieldVersion(it) }
            if (local == null) notesAdded++ else notesUpdated++
        }

        // 4. voice memos. Names come from the file, so only plain file names are accepted (no folders, no "..")
        var audioRestored = 0
        source.paths(AUDIO_DIR).map { it.removePrefix(AUDIO_DIR) }
            .filter { it.isNotEmpty() && it == audioFileName(it) && it != ".." && it != "." }
            .forEach { if (source.restoreAudio(it)) audioRestored++ }

        return ImportResult.Done(notesAdded, notesUpdated, foldersAdded, foldersUpdated, tagsAdded, tagsUpdated, audioRestored, skipped)
    }
}
