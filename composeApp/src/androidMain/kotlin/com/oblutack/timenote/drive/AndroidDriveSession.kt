package com.oblutack.timenote.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.oblutack.timenote.core.logError
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/**
 * Access tokens through Google Play services' Authorization API. Once the user has granted access, tokens are
 * refreshed silently (no UI); the consent screen is only needed the first time or after access was revoked.
 */
class GoogleAuthTokenProvider(context: Context) : AuthTokenProvider {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    /** Set when [accessToken] needed the user: launch this to show Google's consent screen. */
    @Volatile
    var consentIntent: PendingIntent? = null
        private set

    override suspend fun accessToken(): TokenResult {
        val result = try {
            authorize()
        } catch (e: Exception) {
            logError("Drive", "Authorization request failed", e)
            return TokenResult.Failure(e.message ?: "Authorization failed", e)
        }
        return toTokenResult(result)
    }

    /** Called with the result of the consent screen. */
    fun completeConsent(data: Intent?): TokenResult = try {
        toTokenResult(client.getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        logError("Drive", "Consent result could not be read", e)
        TokenResult.Failure(e.message ?: "Consent failed", e)
    }

    // Play services caches and refreshes tokens itself; asking again returns a fresh one.
    override fun invalidate(token: String) = Unit

    private fun toTokenResult(result: AuthorizationResult): TokenResult {
        if (result.hasResolution()) {
            consentIntent = result.pendingIntent
            return TokenResult.NeedsSignIn
        }
        consentIntent = null
        val token = result.accessToken ?: return TokenResult.NeedsSignIn
        return TokenResult.Token(token)
    }

    private suspend fun authorize(): AuthorizationResult = suspendCancellableCoroutine { cont ->
        client.authorize(request)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}

class AndroidDriveSession(context: Context) : DriveSession {
    val tokens = GoogleAuthTokenProvider(context.applicationContext)

    // Created lazily, so a user who never turns sync on pays nothing for it
    override val store: RemoteStore by lazy { KtorDriveClient(HttpClient(OkHttp), tokens) }

    private val _status = MutableStateFlow<DriveStatus>(DriveStatus.Unknown)
    override val status: StateFlow<DriveStatus> = _status.asStateFlow()

    override suspend fun check() {
        _status.value = statusOf(tokens.accessToken())
    }

    override suspend fun accountId(): String? = try {
        store.accountId()
    } catch (e: RemoteException) {
        null
    }

    fun onConsentResult(data: Intent?) {
        _status.value = statusOf(tokens.completeConsent(data))
    }

    private fun statusOf(result: TokenResult): DriveStatus = when (result) {
        is TokenResult.Token -> DriveStatus.Connected
        TokenResult.NeedsSignIn -> DriveStatus.SignedOut
        is TokenResult.Failure -> DriveStatus.Problem(result.message)
    }
}
