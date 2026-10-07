package com.oblutack.timenote.drive

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

sealed interface DriveStatus {
    /** Not checked yet. */
    data object Unknown : DriveStatus

    /** The user has not connected Google Drive (or revoked access). */
    data object SignedOut : DriveStatus

    data object Connected : DriveStatus

    data class Problem(val message: String) : DriveStatus
}

/**
 * The user's connection to their Google Drive: sign-in state plus the [RemoteStore] to use once connected.
 * Implemented per platform (Android today). Sync and the debug panel only see this interface.
 */
interface DriveSession {
    val status: StateFlow<DriveStatus>
    val store: RemoteStore

    /** Tries to get a token without showing any UI and updates [status]. */
    suspend fun check()

    /** Identifies the connected Google account (null when not connected or unknown). */
    suspend fun accountId(): String?

    /** The account's email address for display, when known. */
    suspend fun accountLabel(): String?
}

/** Returns a function that opens Google's consent screen so the user can connect their Drive. */
@Composable
expect fun rememberDriveConnect(session: DriveSession): () -> Unit

/** True in debug builds. Developer-only panels are shown only then. */
@Composable
expect fun isDebugBuild(): Boolean
