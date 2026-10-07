package com.oblutack.timenote.drive

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

private class FakeTokens(var next: () -> TokenResult = { TokenResult.Token("token-1") }) : AuthTokenProvider {
    val invalidated = mutableListOf<String>()
    var issued = 0
    override suspend fun accessToken(): TokenResult = next().also { issued++ }
    override fun invalidate(token: String) { invalidated += token }
}

class KtorDriveClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(
        tokens: AuthTokenProvider = FakeTokens(),
        retry: RetryPolicy = RetryPolicy(jitterMs = { 0 }),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ) = KtorDriveClient(
        HttpClient(MockEngine { request -> requests += request; handler(request) }),
        tokens, retry
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, JSON_HEADERS)

    private fun HttpRequestData.bodyText() = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private val fileJson = """{"id":"abc","name":"notes/n1.json","modifiedTime":"2026-10-07T10:00:00.000Z","size":"12","md5Checksum":"d41d8"}"""

    // ---------------------------------------------------------------- requests

    @Test fun listAsksForTheAppFolderAndReadsEveryPage() = runTest {
        val api = client { req ->
            if (req.url.parameters["pageToken"] == null)
                json("""{"nextPageToken":"p2","files":[$fileJson]}""")
            else
                json("""{"files":[{"id":"def","name":"folders/f1.json"}]}""")
        }
        val files = api.list()

        assertEquals(listOf("abc", "def"), files.map { it.id })
        assertEquals(12L, files[0].size)
        assertEquals("d41d8", files[0].md5)
        assertNull(files[1].size)
        assertEquals(2, requests.size)
        assertEquals("appDataFolder", requests[0].url.parameters["spaces"])
        assertEquals("Bearer token-1", requests[0].headers[HttpHeaders.Authorization])
        assertEquals("p2", requests[1].url.parameters["pageToken"])
    }

    @Test fun creatingAFileSendsMetadataAndContentInOneMultipartRequest() = runTest {
        val api = client { json(fileJson) }
        val created = api.upload("notes/n1.json", """{"v":1}""".encodeToByteArray())

        assertEquals("abc", created.id)
        val req = requests.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("/upload/drive/v3/files", req.url.encodedPath)
        assertEquals("multipart", req.url.parameters["uploadType"])
        val text = req.bodyText()
        assertTrue(text.contains(""""name":"notes/n1.json""""), text)
        assertTrue(text.contains(""""parents":["appDataFolder"]"""), text)
        assertTrue(text.contains("""{"v":1}"""), text)
        assertTrue(req.body.contentType.toString().startsWith("multipart/related; boundary="))
    }

    @Test fun updatingAFileReplacesItsContentInPlace() = runTest {
        val api = client { json(fileJson) }
        api.upload("ignored-for-update", byteArrayOf(1, 2, 3), existingId = "abc")

        val req = requests.single()
        assertEquals(HttpMethod.Patch, req.method)
        assertEquals("/upload/drive/v3/files/abc", req.url.encodedPath)
        assertEquals("media", req.url.parameters["uploadType"])
        assertContentEquals(byteArrayOf(1, 2, 3), (req.body as OutgoingContent.ByteArrayContent).bytes())
    }

    @Test fun binaryContentSurvivesTheMultipartEnvelope() = runTest {
        val audio = ByteArray(256) { it.toByte() }
        client { json(fileJson) }.upload("audio/a.m4a", audio)
        val bytes = (requests.single().body as OutgoingContent.ByteArrayContent).bytes()
        val start = bytes.indices.first { i -> (0 until 256).all { k -> i + k < bytes.size && bytes[i + k] == audio[k] } }
        assertContentEquals(audio, bytes.copyOfRange(start, start + 256))
    }

    @Test fun downloadReturnsTheRawBytes() = runTest {
        val api = client { respond(byteArrayOf(9, 8, 7), HttpStatusCode.OK) }
        assertContentEquals(byteArrayOf(9, 8, 7), api.download("abc"))
        assertEquals("media", requests.single().url.parameters["alt"])
        assertEquals("/drive/v3/files/abc", requests.single().url.encodedPath)
    }

    @Test fun deleteUsesTheDeleteVerb() = runTest {
        client { respond("", HttpStatusCode.NoContent) }.delete("abc")
        assertEquals(HttpMethod.Delete, requests.single().method)
        assertEquals("/drive/v3/files/abc", requests.single().url.encodedPath)
    }

    @Test fun theChangesFeedReportsRemovalsAndTheTokenForNextTime() = runTest {
        val api = client { req ->
            if (req.url.encodedPath.endsWith("startPageToken")) json("""{"startPageToken":"100"}""")
            else json("""{"newStartPageToken":"105","changes":[{"fileId":"abc","removed":false,"file":$fileJson},{"fileId":"gone","removed":true}]}""")
        }
        assertEquals("100", api.startPageToken())

        val page = api.changes("100")

        assertEquals(listOf("abc", "gone"), page.changes.map { it.fileId })
        assertEquals("notes/n1.json", page.changes[0].file?.name)
        assertTrue(page.changes[1].removed)
        assertNull(page.changes[1].file)
        assertNull(page.nextPageToken)
        assertEquals("105", page.newStartPageToken)
        val changesRequest = requests[1]
        assertEquals("appDataFolder", changesRequest.url.parameters["spaces"])
        assertEquals("true", changesRequest.url.parameters["includeRemoved"])
    }

    // ---------------------------------------------------------------- tokens

    @Test fun anExpiredTokenIsReplacedOnceAndTheRequestRepeated() = runTest {
        var n = 0
        val tokens = FakeTokens(next = { TokenResult.Token("token-${++n}") })
        val api = client(tokens) { req ->
            if (req.headers[HttpHeaders.Authorization] == "Bearer token-1") respond("", HttpStatusCode.Unauthorized)
            else json("""{"files":[]}""")
        }
        assertTrue(api.list().isEmpty())
        assertEquals(listOf("token-1"), tokens.invalidated)
        assertEquals(2, requests.size)
    }

    @Test fun aTokenThatIsRejectedTwiceGivesUpInsteadOfLooping() = runTest {
        val tokens = FakeTokens()
        val api = client(tokens) { respond("", HttpStatusCode.Unauthorized) }
        assertFailsWith<RemoteException.Unauthorized> { api.list() }
        assertEquals(2, requests.size)
    }

    @Test fun withoutSignInNothingIsSentAndTheUserIsToldToSignIn() = runTest {
        val api = client(FakeTokens(next = { TokenResult.NeedsSignIn })) { error("must not be called") }
        assertFailsWith<RemoteException.NeedsSignIn> { api.list() }
        assertTrue(requests.isEmpty())
    }

    // ---------------------------------------------------------------- errors and retries

    @Test fun tooManyRequestsWaitsForRetryAfterThenSucceeds() = runTest {
        var n = 0
        val api = client { if (++n == 1) respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "3")) else json("""{"files":[]}""") }
        api.list()
        assertEquals(2, requests.size)
        assertEquals(3_000L, currentTime, "waited exactly what the server asked for")
    }

    @Test fun serverErrorsBackOffExponentiallyAndEventuallyGiveUp() = runTest {
        val api = client { respond("", HttpStatusCode.ServiceUnavailable) }
        val error = assertFailsWith<RemoteException.Server> { api.list() }
        assertEquals(503, error.status)
        assertEquals(5, requests.size)
        assertEquals(1_000L + 2_000 + 4_000 + 8_000, currentTime)
    }

    @Test fun aRateLimitInsideA403IsRetriedLikeA429() = runTest {
        var n = 0
        val api = client {
            if (++n == 1) json("""{"error":{"code":403,"errors":[{"reason":"userRateLimitExceeded"}]}}""", HttpStatusCode.Forbidden)
            else json("""{"files":[]}""")
        }
        api.list()
        assertEquals(2, requests.size)
    }

    @Test fun aFullDriveIsReportedAtOnceBecauseWaitingWillNotHelp() = runTest {
        val api = client { json("""{"error":{"code":403,"errors":[{"reason":"storageQuotaExceeded"}]}}""", HttpStatusCode.Forbidden) }
        assertFailsWith<RemoteException.StorageFull> { api.upload("a", byteArrayOf(1)) }
        assertEquals(1, requests.size)
    }

    @Test fun otherForbiddenAnswersAreNotRetried() = runTest {
        val api = client { json("""{"error":{"code":403,"errors":[{"reason":"appNotAuthorizedToFile"}]}}""", HttpStatusCode.Forbidden) }
        val error = assertFailsWith<RemoteException.Forbidden> { api.list() }
        assertEquals("appNotAuthorizedToFile", error.reason)
        assertEquals(1, requests.size)
    }

    @Test fun aMissingFileIsNotFoundAndNotRetried() = runTest {
        val api = client { respond("", HttpStatusCode.NotFound) }
        assertFailsWith<RemoteException.NotFound> { api.download("nope") }
        assertEquals(1, requests.size)
    }

    @Test fun networkFailuresAreRetriedThenSucceed() = runTest {
        var n = 0
        val api = client { if (++n <= 2) throw RuntimeException("connection reset") else json("""{"files":[]}""") }
        api.list()
        assertEquals(3, requests.size)
        assertEquals(1_000L + 2_000, currentTime)
    }

    @Test fun anAnswerThatIsNotTheExpectedJsonIsAProtocolErrorNotACrash() = runTest {
        val api = client { json("<html>not json</html>") }
        assertFailsWith<RemoteException.Protocol> { api.list() }
        assertEquals(1, requests.size)
    }

    @Test fun jitterIsAddedToTheWait() = runTest {
        var n = 0
        val api = client(retry = RetryPolicy(jitterMs = { 100 })) { if (++n == 1) respond("", HttpStatusCode.BadGateway) else json("""{"files":[]}""") }
        api.list()
        assertEquals(1_100L, currentTime)
    }
}
