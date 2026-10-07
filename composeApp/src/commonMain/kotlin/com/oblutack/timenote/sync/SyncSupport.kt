package com.oblutack.timenote.sync

import com.oblutack.timenote.data.database.SyncKind

/** Where each kind of item lives in the cloud folder (and in a backup zip). Part of the file format. */
object SyncPaths {
    const val NOTES_DIR = "notes/"
    const val FOLDERS_DIR = "folders/"
    const val TAGS_DIR = "tags/"
    const val AUDIO_DIR = "audio/"

    fun of(kind: String, id: String): String = when (kind) {
        SyncKind.NOTE -> "$NOTES_DIR$id.json"
        SyncKind.FOLDER -> "$FOLDERS_DIR$id.json"
        SyncKind.TAG -> "$TAGS_DIR$id.json"
        else -> error("Unknown kind $kind")
    }

    /** "notes/<id>.json" to (kind, id); null for anything that is not an item file (debug files, audio, junk). */
    fun parse(name: String): Pair<String, String>? {
        val (kind, dir) = when {
            name.startsWith(NOTES_DIR) -> SyncKind.NOTE to NOTES_DIR
            name.startsWith(FOLDERS_DIR) -> SyncKind.FOLDER to FOLDERS_DIR
            name.startsWith(TAGS_DIR) -> SyncKind.TAG to TAGS_DIR
            else -> return null
        }
        if (!name.endsWith(".json")) return null
        val id = name.removePrefix(dir).removeSuffix(".json")
        return if (id.isNotEmpty() && '/' !in id) kind to id else null
    }
}

/**
 * Remembers how far through the cloud's changes feed this device has read. It must only be advanced after a sync
 * run has finished completely: a run that stops halfway simply repeats the same changes next time (which is safe,
 * because applying a change twice changes nothing).
 */
interface SyncCheckpoint {
    suspend fun pageToken(): String?
    suspend fun savePageToken(token: String?)
}

/** 64-bit FNV-1a over the text, as hex. Only used to notice that something changed, not for security. */
fun contentHash(text: String): String {
    var hash = -3750763034362895579L // 0xcbf29ce484222325
    for (b in text.encodeToByteArray()) {
        hash = hash xor (b.toLong() and 0xff)
        hash *= 1099511628211L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
