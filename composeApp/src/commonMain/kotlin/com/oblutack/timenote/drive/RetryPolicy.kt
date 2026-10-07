package com.oblutack.timenote.drive

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Retries a remote call that failed for a temporary reason, waiting longer each time (exponential backoff) and
 * respecting the server's Retry-After. A little random jitter keeps several devices from retrying in lockstep.
 * Permanent errors, and cancellation, are never retried.
 */
class RetryPolicy(
    val maxAttempts: Int = 5,
    private val baseDelayMs: Long = 1_000,
    private val maxDelayMs: Long = 60_000,
    private val jitterMs: () -> Long = { Random.nextLong(0, 250) }
) {
    init { require(maxAttempts >= 1) }

    suspend fun <T> run(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: RemoteException) {
                attempt++
                if (!e.retryable || attempt >= maxAttempts) throw e
                delay(waitBefore(attempt, e) + jitterMs())
            }
        }
    }

    /** The wait after [attempt] failed tries: Retry-After if given, otherwise 1s, 2s, 4s... capped. */
    internal fun waitBefore(attempt: Int, e: RemoteException): Long {
        e.retryAfterMs?.let { return it.coerceAtMost(maxDelayMs) }
        var wait = baseDelayMs
        repeat(attempt - 1) { wait = (wait * 2).coerceAtMost(maxDelayMs) }
        return wait
    }

    companion object {
        val None = RetryPolicy(maxAttempts = 1)
    }
}
