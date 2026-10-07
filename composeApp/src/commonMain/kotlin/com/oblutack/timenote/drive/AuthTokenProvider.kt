package com.oblutack.timenote.drive

sealed interface TokenResult {
    data class Token(val value: String) : TokenResult

    /** The user has not signed in or granted access yet, or revoked it. Needs UI. */
    data object NeedsSignIn : TokenResult

    /** Something else went wrong (no Google Play services, no network for the token request...). */
    data class Failure(val message: String, val cause: Throwable? = null) : TokenResult
}

/**
 * Supplies short-lived access tokens for the Drive API. The platform implements it
 * (Google Play services on Android), so the shared Drive client never touches platform sign-in code.
 */
interface AuthTokenProvider {
    /** A valid token. Refreshes silently when the user already granted access; never shows UI itself. */
    suspend fun accessToken(): TokenResult

    /** The Drive API rejected [token]; do not hand it out again. */
    fun invalidate(token: String)
}
