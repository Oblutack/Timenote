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
