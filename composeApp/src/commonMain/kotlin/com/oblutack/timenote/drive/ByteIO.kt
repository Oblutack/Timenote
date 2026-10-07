package com.oblutack.timenote.drive

/**
 * Something large that can be read piece by piece, so a voice memo never has to be held in memory as a whole.
 * Reads may be repeated: an interrupted upload asks for the same piece again.
 */
interface ByteSource {
    val size: Long

    /** Up to [length] bytes starting at [offset] (fewer only at the end). */
    suspend fun read(offset: Long, length: Int): ByteArray
}

/**
 * Where a download is written. Pieces arrive in order. Nothing is visible under the final name until [finish]
 * (platform code writes to a temporary file and renames), so an interrupted download never leaves a half-written
 * memo that would be played as if it were complete.
 */
interface ByteSink {
    suspend fun write(bytes: ByteArray)

    /** Everything arrived: make the result visible. */
    suspend fun finish()

    /** The download failed: discard what was written so far. */
    suspend fun abort()
}

class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()
    override suspend fun read(offset: Long, length: Int): ByteArray {
        val from = offset.toInt().coerceAtMost(bytes.size)
        return bytes.copyOfRange(from, (from + length).coerceAtMost(bytes.size))
    }
}

/** Collects a download in memory (tests, and small files). */
class ByteArraySink : ByteSink {
    private var buffer = ByteArray(0)
    var finished = false
        private set
    var aborted = false
        private set

    val bytes: ByteArray get() = buffer

    override suspend fun write(bytes: ByteArray) { buffer += bytes }
    override suspend fun finish() { finished = true }
    override suspend fun abort() { aborted = true; buffer = ByteArray(0) }
}
