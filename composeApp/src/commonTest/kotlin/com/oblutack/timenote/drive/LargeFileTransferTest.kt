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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MIB = 1024 * 1024
private val RESUME = HttpStatusCode(308, "Resume Incomplete")
private const val SESSION_URL = "https://upload.example/session/1"

private class Tokens : AuthTokenProvider {
    override suspend fun accessToken(): TokenResult = TokenResult.Token("t")
    override fun invalidate(token: String) {}
}

/** Header names as Google really sends them: lowercase (HTTP/2). */
private var lowercaseHeaders = false

/** A stand-in for Drive's resumable upload endpoint that can lose answers, refuse pieces or forget its session. */
private class UploadServer(private val total: Long) {
    var received = ByteArray(0)
    var sessionsOpened = 0
    val pieceRanges = mutableListOf<String>()
    var statusQueries = 0
    private var puts = 0

    /** PUT numbers (1-based, counting pieces only) whose answer is lost AFTER the server stored the piece. */
    val loseAnswerOfPiece = mutableSetOf<Int>()
    /** Piece numbers refused with 503 BEFORE anything is stored. */
    val refusePiece = mutableSetOf<Int>()
    /** Piece number after which the session is forgotten (404 on the next request). */
    var forgetSessionAfterPiece: Int? = null
    private var forgotten = false
    private var pieceNumber = 0

    private fun fileJson() = """{"id":"big1","name":"audio/memo.m4a","size":"$total"}"""

    fun handle(scope: MockRequestHandleScope, req: HttpRequestData): HttpResponseData = with(scope) {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        if (req.method != HttpMethod.Put) {
            sessionsOpened++; forgotten = false; received = ByteArray(0); pieceNumber = 0
            return respond("", HttpStatusCode.OK, headersOf(if (lowercaseHeaders) "location" else HttpHeaders.Location, SESSION_URL))
        }
        if (forgotten) return respond("", HttpStatusCode.NotFound)
        val range = req.headers[HttpHeaders.ContentRange]!!
        if (range.startsWith("bytes */")) {
            statusQueries++
            return statusAnswer(json)
        }
        pieceNumber++; puts++
        if (pieceNumber in refusePiece) return respond("", HttpStatusCode.ServiceUnavailable)
        pieceRanges += range
        val first = range.removePrefix("bytes ").substringBefore('-').toLong()
        val body = (req.body as OutgoingContent.ByteArrayContent).bytes()
        if (first == received.size.toLong()) received += body
        if (pieceNumber == forgetSessionAfterPiece) { forgotten = true; forgetSessionAfterPiece = null } // only the first session expires
        if (pieceNumber in loseAnswerOfPiece) throw RuntimeException("connection reset")
        return statusAnswer(json)
    }

    private fun MockRequestHandleScope.statusAnswer(json: io.ktor.http.Headers): HttpResponseData =
        when {
            received.size.toLong() == total -> respond(fileJson(), HttpStatusCode.OK, json)
            received.isEmpty() -> respond("", RESUME)
            else -> respond("", RESUME, headersOf(if (lowercaseHeaders) "range" else HttpHeaders.Range, "bytes=0-${received.size - 1}"))
        }
}

class LargeFileTransferTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorDriveClient(HttpClient(MockEngine { r -> requests += r; handler(r) }), Tokens(), RetryPolicy(jitterMs = { 0 }))

    private fun bytesOf(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }

    // ---------------------------------------------------------------- upload

    @Test fun aSmallFileGoesInOneRequestWithoutASession() = runTest {
        val api = client { respond("""{"id":"a","name":"audio/x.m4a","size":"4"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val progress = mutableListOf<Pair<Long, Long>>()

        api.uploadFrom("audio/x.m4a", ByteArraySource(byteArrayOf(1, 2, 3, 4))) { a, b -> progress += a to b }

        assertEquals(1, requests.size)
        assertEquals("multipart", requests.single().url.parameters["uploadType"])
        assertEquals(listOf(4L to 4L), progress)
    }

    @Test fun aLargeFileIsSentInPiecesThatArrivePerfectly() = runTest {
        val data = bytesOf(5 * MIB + 123)
        val server = UploadServer(data.size.toLong())
        val api = client { server.handle(this, it) }
        val progress = mutableListOf<Long>()

        val file = api.uploadFrom("audio/memo.m4a", ByteArraySource(data)) { sent, _ -> progress += sent }

        assertEquals("big1", file.id)
        assertContentEquals(data, server.received)
        assertEquals(1, server.sessionsOpened)
        assertEquals(listOf("bytes 0-2097151/${data.size}", "bytes 2097152-4194303/${data.size}", "bytes 4194304-${data.size - 1}/${data.size}"), server.pieceRanges)
        assertEquals(progress.sorted(), progress, "progress only moves forward")
        assertEquals(data.size.toLong(), progress.last())
        val open = requests.first()
        assertEquals("resumable", open.url.parameters["uploadType"])
        assertEquals("${data.size}", open.headers["X-Upload-Content-Length"])
        assertTrue((open.body as OutgoingContent.ByteArrayContent).bytes().decodeToString().contains(""""parents":["appDataFolder"]"""))
    }

    @Test fun headerNamesInLowercaseAreUnderstoodAsGoogleSendsThem() = runTest {
        lowercaseHeaders = true
        try {
            val data = bytesOf(5 * MIB + 5)
            val server = UploadServer(data.size.toLong()).also { it.loseAnswerOfPiece += 1 } // also exercises the lowercase Range header
            val api = client { server.handle(this, it) }

            api.uploadFrom("audio/memo.m4a", ByteArraySource(data))

            assertContentEquals(data, server.received)
            assertEquals(1, server.sessionsOpened)
        } finally {
            lowercaseHeaders = false
        }
    }

    @Test fun replacingAFileOpensTheSessionOnThatFile() = runTest {
        val data = bytesOf(6 * MIB)
        val server = UploadServer(data.size.toLong())
        val api = client { server.handle(this, it) }
        api.uploadFrom("audio/memo.m4a", ByteArraySource(data), existingId = "existing-id")
        val open = requests.first()
        assertEquals(HttpMethod.Patch, open.method)
        assertEquals("/upload/drive/v3/files/existing-id", open.url.encodedPath)
    }

    @Test fun aLostAnswerDoesNotCauseTheSamePieceToBeSentTwice() = runTest {
        val data = bytesOf(5 * MIB + 5)
        val server = UploadServer(data.size.toLong()).also { it.loseAnswerOfPiece += 2 }
        val api = client { server.handle(this, it) }

        api.uploadFrom("audio/memo.m4a", ByteArraySource(data))

        assertContentEquals(data, server.received)
        assertEquals(3, server.pieceRanges.size, "the stored piece was not sent again")
        assertEquals(1, server.statusQueries, "the client asked the session where it stood")
        assertEquals(1, server.sessionsOpened)
    }

    @Test fun aRefusedPieceIsSentAgainFromWhereTheSessionStands() = runTest {
        val data = bytesOf(5 * MIB + 5)
        val server = UploadServer(data.size.toLong()).also { it.refusePiece += 2 }
        val api = client { server.handle(this, it) }

        api.uploadFrom("audio/memo.m4a", ByteArraySource(data))

        assertContentEquals(data, server.received)
        assertEquals(1, server.sessionsOpened)
        assertEquals(1, server.statusQueries)
    }

    @Test fun anExpiredSessionIsReplacedAndTheUploadStartsOver() = runTest {
        val data = bytesOf(5 * MIB + 5)
        val server = UploadServer(data.size.toLong()).also { it.forgetSessionAfterPiece = 1 }
        val api = client { server.handle(this, it) }

        val file = api.uploadFrom("audio/memo.m4a", ByteArraySource(data))

        assertEquals("big1", file.id)
        assertContentEquals(data, server.received)
        assertEquals(2, server.sessionsOpened)
    }

    @Test fun anUploadThatKeepsFailingGivesUpWithTheError() = runTest {
        val api = client { respond("", HttpStatusCode.ServiceUnavailable) }
        assertFailsWith<RemoteException.Server> { api.uploadFrom("audio/memo.m4a", ByteArraySource(bytesOf(6 * MIB))) }
    }

    // ---------------------------------------------------------------- download

    private fun downloadServer(data: ByteArray, failOnPiece: Int? = null): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData {
        var piece = 0
        return { req ->
            if (req.url.parameters["alt"] == "media") {
                piece++
                if (piece == failOnPiece) throw RuntimeException("connection reset")
                val (a, b) = req.headers[HttpHeaders.Range]!!.removePrefix("bytes=").split('-').map { it.toInt() }
                respond(data.copyOfRange(a, minOf(data.size, b + 1)), HttpStatusCode.PartialContent)
            } else {
                respond("""{"size":"${data.size}"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
    }

    @Test fun aLargeFileIsDownloadedInOrderedPieces() = runTest {
        val data = bytesOf(5 * MIB + 77)
        val api = client(downloadServer(data))
        val sink = ByteArraySink()
        val progress = mutableListOf<Long>()

        api.downloadTo("big1", sink) { got, total -> progress += got; assertEquals(data.size.toLong(), total) }

        assertContentEquals(data, sink.bytes)
        assertTrue(sink.finished)
        assertFalse(sink.aborted)
        val ranges = requests.mapNotNull { it.headers[HttpHeaders.Range] }
        assertEquals(listOf("bytes=0-2097151", "bytes=2097152-4194303", "bytes=4194304-${data.size - 1}"), ranges)
        assertEquals(data.size.toLong(), progress.last())
    }

    @Test fun aDownloadThatFailsLeavesNothingBehind() = runTest {
        val data = bytesOf(5 * MIB)
        // every attempt at the second piece fails
        val server = downloadServer(data, failOnPiece = 2)
        val api = KtorDriveClient(HttpClient(MockEngine { r -> requests += r; server(this, r) }), Tokens(), RetryPolicy.None)
        val sink = ByteArraySink()

        assertFailsWith<RemoteException.Network> { api.downloadTo("big1", sink) }

        assertTrue(sink.aborted)
        assertFalse(sink.finished)
        assertEquals(0, sink.bytes.size)
    }

    @Test fun aSmallFileWithoutAReportedSizeIsAProtocolError() = runTest {
        val api = client { respond("""{}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val sink = ByteArraySink()
        assertFailsWith<RemoteException.Protocol> { api.downloadTo("x", sink) }
        assertTrue(sink.aborted)
    }

    @Test fun anEmptyFileDownloadsAsNothingAndStillFinishes() = runTest {
        val api = client { respond("""{"size":"0"}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val sink = ByteArraySink()
        api.downloadTo("x", sink)
        assertTrue(sink.finished)
        assertEquals(0, sink.bytes.size)
    }
}
