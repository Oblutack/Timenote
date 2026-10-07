package com.oblutack.timenote.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lets only one database writer work at a time. Every user edit holds it while it writes, and sync holds it while it
 * reads an item, merges the cloud copy into it and writes the result. Without it, a sync could read an item, the user
 * could edit it, and sync would then write back its stale copy over the edit.
 *
 * Not reentrant: code that holds the lock must not ask for it again.
 */
class WriteLock {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
