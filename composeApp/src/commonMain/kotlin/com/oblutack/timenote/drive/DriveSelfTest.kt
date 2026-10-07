package com.oblutack.timenote.drive

private const val TEST_FILE = "debug/selftest.json"

/**
 * A round trip against any [RemoteStore] (the real Drive from the debug panel, or a fake in unit tests):
 * create a file, find it, read it back, change it, see both writes in the changes feed, delete it.
 * Cleans up after itself. Returns true when everything behaved; every step is reported through [log].
 */
suspend fun runDriveSelfTest(store: RemoteStore, log: (String) -> Unit): Boolean {
    suspend fun step(name: String, block: suspend () -> Boolean): Boolean {
        val ok = try {
            block()
        } catch (e: RemoteException) {
            log("FAIL $name: ${e.message}")
            return false
        }
        log(if (ok) "ok   $name" else "FAIL $name")
        return ok
    }

    try {
        // A leftover from an interrupted earlier run would make the checks below ambiguous
        store.list().filter { it.name == TEST_FILE }.forEach { store.delete(it.id) }
    } catch (e: RemoteException) {
        log("FAIL cleanup: ${e.message}")
        return false
    }

    var startToken = ""
    var fileId = ""
    val first = """{"v":1,"step":"created"}""".encodeToByteArray()
    val second = """{"v":1,"step":"updated"}""".encodeToByteArray()

    suspend fun changesSince(token: String): List<RemoteChange> {
        val all = mutableListOf<RemoteChange>()
        var next: String? = token
        while (next != null) {
            val page = store.changes(next)
            all += page.changes
            next = page.nextPageToken
        }
        return all
    }

    return step("get changes position") { startToken = store.startPageToken(); startToken.isNotEmpty() } &&
        step("create file") { fileId = store.upload(TEST_FILE, first).id; fileId.isNotEmpty() } &&
        step("file is listed") { store.list().any { it.id == fileId && it.name == TEST_FILE } } &&
        step("read it back") { store.download(fileId).contentEquals(first) } &&
        step("replace content") { store.upload(TEST_FILE, second, existingId = fileId); store.download(fileId).contentEquals(second) } &&
        step("changes feed shows the file") { changesSince(startToken).any { it.fileId == fileId && !it.removed } } &&
        step("delete file") { store.delete(fileId); store.list().none { it.id == fileId } } &&
        step("changes feed shows the removal") { changesSince(startToken).any { it.fileId == fileId && it.removed } }
}

/** Prefix of the files the debug panel creates. They are never real data. */
private const val DEBUG_PREFIX = "debug/"

/**
 * For the "can another app of the same Google project (a web app) see this folder?" check: leaves a small file
 * in the app folder so it can be looked at from outside. Replaces an earlier one with the same name.
 */
suspend fun leaveWebCheckFile(store: RemoteStore, now: Long): RemoteFile {
    store.list().filter { it.name == WEB_CHECK_FILE }.forEach { store.delete(it.id) }
    return store.upload(WEB_CHECK_FILE, """{"v":1,"from":"android","at":$now}""".encodeToByteArray())
}

const val WEB_CHECK_FILE = "debug/web-check.json"

/** What is in the app folder, one line per file (name and size), for the debug panel. */
suspend fun describeAppFolder(store: RemoteStore): List<String> =
    store.list().sortedBy { it.name }.map { "${it.name} (${it.size ?: "?"} bytes)" }

/** Removes every file the debug panel created (also ones created from elsewhere under "debug/"). Returns how many. */
suspend fun deleteDebugFiles(store: RemoteStore): Int {
    val files = store.list().filter { it.name.startsWith(DEBUG_PREFIX) }
    files.forEach { store.delete(it.id) }
    return files.size
}
