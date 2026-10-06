package com.oblutack.timenote

import android.content.Context
import com.oblutack.timenote.core.logError
import com.oblutack.timenote.core.relocatedPath
import com.oblutack.timenote.data.database.TimenoteDao
import com.oblutack.timenote.feature_timer.domain.TimelineEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Early versions saved voice memos in cacheDir, which Android may clear at any time.
 * This copies any such memo that still exists into filesDir/voice_memos, points the database at the
 * new location, and only then deletes the cached original. Safe to run on every launch: once
 * nothing references the cache any more it does nothing.
 */
class VoiceMemoMigration(private val context: Context, private val dao: TimenoteDao) {

    suspend fun run() {
        try {
            val cacheDir = context.cacheDir.absolutePath
            val targetDir = File(context.filesDir, "voice_memos").apply { mkdirs() }
            val copiedOriginals = mutableListOf<File>()

            fun move(path: String): String {
                val newPath = relocatedPath(path, cacheDir, targetDir.absolutePath) ?: return path
                val source = File(path)
                if (!source.exists()) return path          // already gone, nothing to save
                val target = File(newPath)
                if (!target.exists()) source.copyTo(target)
                copiedOriginals += source
                return target.name // store the file name, not a path (see AudioFiles)
            }

            for (note in dao.getAllTimenotesOnce()) {
                val voiceNotes = Json.decodeFromString<List<String>>(note.voiceNotesJson)
                val newVoiceNotes = voiceNotes.map(::move)
                if (newVoiceNotes != voiceNotes) {
                    dao.updateTimenoteVoiceNotes(note.id, Json.encodeToString(newVoiceNotes), note.updatedAt)
                }

                val events = Json.decodeFromString<List<TimelineEvent>>(note.timelineEventsJson)
                val newEvents = events.map { event -> event.audioPath?.let { event.copy(audioPath = move(it)) } ?: event }
                if (newEvents != events) {
                    dao.updateTimenoteTimelineEvents(note.id, Json.encodeToString(newEvents))
                }
            }

            copiedOriginals.forEach { it.delete() }
        } catch (e: Exception) {
            logError("VoiceMemoMigration", "Could not move voice memos out of the cache", e)
        }
    }
}
