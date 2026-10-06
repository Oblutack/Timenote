package com.oblutack.timenote.core

private const val BACKSLASH = '\\'

/**
 * If [path] points at a file directly inside [fromDir], returns the same file name inside [toDir];
 * otherwise null. Used to move voice memos out of the OS-clearable cache directory.
 */
fun relocatedPath(path: String, fromDir: String, toDir: String): String? {
    val from = fromDir.trimEnd('/', BACKSLASH)
    val to = toDir.trimEnd('/', BACKSLASH)
    val separator = when {
        path.startsWith("$from/") -> '/'
        path.startsWith("$from$BACKSLASH") -> BACKSLASH
        else -> return null
    }
    val fileName = path.removePrefix("$from$separator")
    if (fileName.isEmpty() || fileName.contains('/') || fileName.contains(BACKSLASH)) return null
    return "$to/$fileName"
}
