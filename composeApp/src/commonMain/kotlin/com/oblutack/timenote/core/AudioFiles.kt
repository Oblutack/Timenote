package com.oblutack.timenote.core

private const val BACKSLASH = '\\'

/** The file name of a stored voice memo reference: a plain name stays as it is, a (legacy) path is cut to its name. */
fun audioFileName(ref: String): String = ref.substringAfterLast('/').substringAfterLast(BACKSLASH)

/**
 * Voice memos are stored in the database as a plain **file name** ("SessionMemo_x.m4a"), never as a path, because
 * a path only makes sense on the device that recorded it. Each device resolves the name to its own folder.
 *
 * Older versions stored absolute paths. Those keep working: [resolve] takes the file name from them.
 */
interface AudioFiles {
    /** The value to store in the database for a recorded file. */
    fun toRef(path: String): String

    /** The real file location on this device for a stored reference (a name or a legacy absolute path). */
    fun resolve(ref: String): String
}

class DirectoryAudioFiles(
    directory: String,
    private val exists: (String) -> Boolean = { false }
) : AudioFiles {
    private val directory = directory.trimEnd('/', BACKSLASH)

    override fun toRef(path: String): String = audioFileName(path)

    override fun resolve(ref: String): String {
        val inDirectory = "$directory/${toRef(ref)}"
        if (exists(inDirectory)) return inDirectory
        // A legacy absolute path whose file is still where it was recorded
        val isLegacyPath = ref.contains('/') || ref.contains(BACKSLASH)
        if (isLegacyPath && exists(ref)) return ref
        return inDirectory
    }
}
