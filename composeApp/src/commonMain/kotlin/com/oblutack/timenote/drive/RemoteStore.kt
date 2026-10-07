package com.oblutack.timenote.drive

/** A file in the user's hidden Drive app folder. */
data class RemoteFile(
    val id: String,
    val name: String,
    /** RFC 3339 time as reported by Drive, e.g. "2026-10-07T10:15:30.123Z". */
    val modifiedTime: String? = null,
    val size: Long? = null,
    val md5: String? = null
)

/** One entry of the changes feed. When [removed] is true the file is gone and [file] is null. */
data class RemoteChange(val fileId: String, val removed: Boolean, val file: RemoteFile?)

/**
 * One page of the changes feed. Keep reading with [nextPageToken] until it is null, then store [newStartPageToken]
 * and use it for the next sync.
 */
data class ChangesPage(
    val changes: List<RemoteChange>,
    val nextPageToken: String?,
    val newStartPageToken: String?
)

/**
 * The remote side of sync: a flat set of named files. The real implementation talks to Google Drive
 * ([KtorDriveClient]); tests use an in-memory fake. File names are the sync paths ("notes/<id>.json").
 *
 * Every function can throw [RemoteException].
 */
interface RemoteStore {
    /** Every file in the app folder (pages are read internally). */
    suspend fun list(): List<RemoteFile>

    /** Creates the file, or replaces its content when [existingId] is given. */
    suspend fun upload(name: String, content: ByteArray, existingId: String? = null): RemoteFile

    suspend fun download(fileId: String): ByteArray

    suspend fun delete(fileId: String)

    /** The position "now" in the changes feed; changes made after this are reported by [changes]. */
    suspend fun startPageToken(): String

    suspend fun changes(pageToken: String): ChangesPage
}

/** What went wrong talking to the remote store, with enough detail to decide what to do next. */
sealed class RemoteException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Worth trying again later without any user action. */
    open val retryable: Boolean = false

    /** The server asked us to wait at least this long (Retry-After), in milliseconds. */
    open val retryAfterMs: Long? = null

    /** The user has to sign in or grant access again. Nothing can be done in the background. */
    class NeedsSignIn : RemoteException("Sign-in or consent is required")

    /** The access token was rejected (401). The client refreshes it once automatically. */
    class Unauthorized : RemoteException("The access token was rejected")

    /** Permanently refused (for example the app was not allowed to touch the file). */
    class Forbidden(val reason: String?) : RemoteException("Access refused (${reason ?: "no reason given"})")

    /** The user's Drive is full. Retrying will not help until they free space. */
    class StorageFull : RemoteException("Google Drive storage is full")

    /** Too many requests (429, or 403 rateLimitExceeded): back off and try again. */
    class RateLimited(override val retryAfterMs: Long? = null) : RemoteException("Too many requests, slow down") {
        override val retryable = true
    }

    class NotFound : RemoteException("The file does not exist (any more)")

    /** Google had a problem (5xx). */
    class Server(val status: Int) : RemoteException("Server error $status") {
        override val retryable = true
    }

    /** No connection, timeout, interrupted transfer. */
    class Network(cause: Throwable) : RemoteException("Network problem: ${cause.message}", cause) {
        override val retryable = true
    }

    /** An answer we do not understand. Not retried: repeating it would give the same answer. */
    class Protocol(message: String) : RemoteException(message)
}
