package com.oblutack.timenote.backup

import com.oblutack.timenote.core.audioFileName
import com.oblutack.timenote.data.database.FieldVersionEntity
import com.oblutack.timenote.data.repository.WriteLock
import com.oblutack.timenote.sync.SyncApplier
import com.oblutack.timenote.sync.ApplyChange
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
import com.oblutack.timenote.sync.SyncPaths

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
const val NOTES_DIR = SyncPaths.NOTES_DIR
const val FOLDERS_DIR = SyncPaths.FOLDERS_DIR
const val TAGS_DIR = SyncPaths.TAGS_DIR
const val AUDIO_DIR = SyncPaths.AUDIO_DIR

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
    private val now: () -> Long = ::getCurrentTimeMillis,
    writeLock: WriteLock = WriteLock()
) {
    private val applier = SyncApplier(dao, deviceIdSource, writeLock, now)

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

        var skipped = 0

        /** Reads and decodes every file under [prefix]; unreadable or too-new files are counted and left out. */
        fun <T> items(prefix: String, decode: (String) -> T): List<T> = source.paths(prefix).mapNotNull { path ->
            val text = source.readText(path)
            if (text == null || !isSupportedFormat(peekFormatVersion(text))) { skipped++; return@mapNotNull null }
            runCatching { decode(text) }.getOrElse { skipped++; null }
        }

        // 1. tags first: notes embed their tag details
        var tagsAdded = 0; var tagsUpdated = 0
        for (remote in items(TAGS_DIR) { SyncJson.decodeFromString<SyncTag>(it) }) {
            when (applier.applyTag(remote).change) {
                ApplyChange.ADDED -> tagsAdded++
                ApplyChange.UPDATED -> tagsUpdated++
                ApplyChange.UNCHANGED -> {}
            }
        }

        // 2. folders
        var foldersAdded = 0; var foldersUpdated = 0
        for (remote in items(FOLDERS_DIR) { SyncJson.decodeFromString<SyncFolder>(it) }) {
            when (applier.applyFolder(remote).change) {
                ApplyChange.ADDED -> foldersAdded++
                ApplyChange.UPDATED -> foldersUpdated++
                ApplyChange.UNCHANGED -> {}
            }
        }

        // 3. notes (tag details now come from the merged tag table)
        var notesAdded = 0; var notesUpdated = 0
        for (remote in items(NOTES_DIR) { SyncJson.decodeFromString<SyncNote>(it) }) {
            when (applier.applyNote(remote).change) {
                ApplyChange.ADDED -> notesAdded++
                ApplyChange.UPDATED -> notesUpdated++
                ApplyChange.UNCHANGED -> {}
            }
        }

        // 4. voice memos. Names come from the file, so only plain file names are accepted (no folders, no "..")
        var audioRestored = 0
        source.paths(AUDIO_DIR).map { it.removePrefix(AUDIO_DIR) }
            .filter { it.isNotEmpty() && it == audioFileName(it) && it != ".." && it != "." }
            .forEach { if (source.restoreAudio(it)) audioRestored++ }

        return ImportResult.Done(notesAdded, notesUpdated, foldersAdded, foldersUpdated, tagsAdded, tagsUpdated, audioRestored, skipped)
    }
}
