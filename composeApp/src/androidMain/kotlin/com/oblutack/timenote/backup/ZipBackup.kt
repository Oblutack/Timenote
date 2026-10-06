package com.oblutack.timenote.backup

import com.oblutack.timenote.core.audioFileName
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Writes a backup as a zip file. Voice memos are streamed from disk, never held in memory. */
class ZipBackupSink(output: OutputStream, private val audioDir: File) : BackupSink, Closeable {
    private val zip = ZipOutputStream(BufferedOutputStream(output))

    override fun putText(path: String, text: String) {
        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    override fun putAudio(name: String): Boolean {
        val file = File(audioDir, name)
        if (!file.isFile) return false
        zip.setLevel(Deflater.NO_COMPRESSION) // m4a is already compressed
        zip.putNextEntry(ZipEntry("$AUDIO_DIR$name"))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        return true
    }

    override fun close() = zip.close()
}

/**
 * Reads a backup zip. Entry names come from the file and are never used as file system paths:
 * a voice memo is written to the memo folder under a plain file name only (see [restoreAudio]).
 */
class ZipBackupSource(private val zip: ZipFile, private val audioDir: File) : BackupSource, Closeable {

    override fun readText(path: String): String? {
        val entry = zip.getEntry(path) ?: return null
        if (entry.isDirectory) return null
        val bytes = zip.getInputStream(entry).use { it.readNBytes(MAX_TEXT_BYTES + 1) }
        if (bytes.size > MAX_TEXT_BYTES) return null // far larger than any note: treat as damaged
        return bytes.toString(Charsets.UTF_8)
    }

    override fun paths(prefix: String): List<String> =
        zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith(prefix) }.map { it.name }.toList()

    override fun restoreAudio(name: String): Boolean {
        if (name != audioFileName(name) || name.isEmpty() || name == "." || name == "..") return false
        val entry = zip.getEntry("$AUDIO_DIR$name") ?: return false
        audioDir.mkdirs()
        val target = File(audioDir, name)
        if (target.exists()) return false // never overwrite a memo that is already here
        // Write beside the target first, so an interrupted import cannot leave a half-written memo under its real name
        val partial = File(audioDir, "$name.part")
        zip.getInputStream(entry).use { input -> partial.outputStream().use { input.copyTo(it) } }
        if (!partial.renameTo(target)) { partial.delete(); return false }
        return true
    }

    override fun close() = zip.close()

    private companion object {
        const val MAX_TEXT_BYTES = 16 * 1024 * 1024
    }
}
