package com.oblutack.timenote.drive

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * [RemoteStore] on Google Drive, using only the hidden application data folder (scope drive.appdata):
 * files in it are invisible to the user's normal Drive and to every other app.
 *
 * Platform independent (Ktor), so Android, iOS and later other targets share it. The platform only provides
 * an [AuthTokenProvider]. Temporary failures are retried with backoff ([RetryPolicy]); an expired token is
 * refreshed once automatically.
 */
class KtorDriveClient(
    private val http: HttpClient,
    private val tokens: AuthTokenProvider,
    private val retry: RetryPolicy = RetryPolicy(),
    private val baseUrl: String = "https://www.googleapis.com"
) : RemoteStore {

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------ RemoteStore

    override suspend fun list(): List<RemoteFile> {
        val all = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val page = call(HttpMethod.Get, "/drive/v3/files", query = buildMap {
                put("spaces", APP_FOLDER)
                put("pageSize", "1000")
                put("fields", "nextPageToken,files($FILE_FIELDS)")
                pageToken?.let { put("pageToken", it) }
            }).parse<FileListDto>()
            all += page.files.map { it.toFile() }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return all
    }

    override suspend fun upload(name: String, content: ByteArray, existingId: String?): RemoteFile {
        val response = if (existingId == null) {
            val boundary = "timenote-$BOUNDARY_SUFFIX"
            val metadata = json.encodeToString(CreateMetadata(name, listOf(APP_FOLDER)))
            call(
                HttpMethod.Post, "/upload/drive/v3/files",
                query = mapOf("uploadType" to "multipart", "fields" to FILE_FIELDS),
                body = multipart(boundary, metadata, content),
                contentType = ContentType.parse("multipart/related; boundary=$boundary")
            )
        } else {
            call(
                HttpMethod.Patch, "/upload/drive/v3/files/$existingId",
                query = mapOf("uploadType" to "media", "fields" to FILE_FIELDS),
                body = content,
                contentType = ContentType.Application.OctetStream
            )
        }
        return response.parse<FileDto>().toFile()
    }

    override suspend fun download(fileId: String): ByteArray =
        call(HttpMethod.Get, "/drive/v3/files/$fileId", query = mapOf("alt" to "media")).bytes

    override suspend fun delete(fileId: String) {
        call(HttpMethod.Delete, "/drive/v3/files/$fileId")
    }

    override suspend fun startPageToken(): String =
        call(HttpMethod.Get, "/drive/v3/changes/startPageToken").parse<StartTokenDto>().startPageToken

    override suspend fun changes(pageToken: String): ChangesPage {
        val page = call(HttpMethod.Get, "/drive/v3/changes", query = mapOf(
            "pageToken" to pageToken,
            "spaces" to APP_FOLDER,
            "includeRemoved" to "true",
            "pageSize" to "1000",
            "fields" to "nextPageToken,newStartPageToken,changes(fileId,removed,file($FILE_FIELDS))"
        )).parse<ChangeListDto>()
        return ChangesPage(
            changes = page.changes.map { RemoteChange(it.fileId, it.removed, it.file?.toFile()) },
            nextPageToken = page.nextPageToken,
            newStartPageToken = page.newStartPageToken
        )
    }

    override suspend fun uploadFrom(name: String, source: ByteSource, existingId: String?, onProgress: (Long, Long) -> Unit): RemoteFile {
        val total = source.size
        if (total <= SMALL_UPLOAD_LIMIT) {
            val bytes = source.read(0, total.toInt())
            return upload(name, bytes, existingId).also { onProgress(total, total) }
        }
        return resumableUpload(name, source, existingId, onProgress)
    }

    override suspend fun downloadTo(fileId: String, sink: ByteSink, onProgress: (Long, Long) -> Unit) {
        try {
            val total = call(HttpMethod.Get, "/drive/v3/files/$fileId", query = mapOf("fields" to "size")).parse<SizeDto>().size?.toLongOrNull()
                ?: throw RemoteException.Protocol("Drive did not report the file size")
            var offset = 0L
            while (offset < total) {
                val last = minOf(total, offset + DOWNLOAD_CHUNK) - 1
                val piece = call(
                    HttpMethod.Get, "/drive/v3/files/$fileId", query = mapOf("alt" to "media"),
                    headers = mapOf(HttpHeaders.Range to "bytes=$offset-$last")
                ).bytes
                if (piece.isEmpty()) throw RemoteException.Protocol("Drive returned an empty piece")
                sink.write(piece)
                offset += piece.size
                onProgress(offset, total)
            }
            sink.finish()
        } catch (e: Throwable) {
            sink.abort()
            throw e
        }
    }

    /**
     * Drive's resumable protocol: open a session, send the file in pieces, and after any interruption ask the
     * session how much arrived and carry on from there. A session that expired is replaced by a new one.
     */
    private suspend fun resumableUpload(name: String, source: ByteSource, existingId: String?, onProgress: (Long, Long) -> Unit): RemoteFile {
        val total = source.size
        var session: String? = null
        var offset = 0L
        var interrupted = false
        return retry.run {
            try {
                while (true) {
                    if (session == null) {
                        session = openUploadSession(name, total, existingId)
                        offset = 0
                    } else if (interrupted) {
                        val status = uploadStatus(session!!, total)
                        status.finished?.let { return@run it }
                        offset = status.received
                    }
                    interrupted = true // from now on any retry first asks the session where it stands
                    while (offset < total) {
                        val end = minOf(total, offset + UPLOAD_CHUNK)
                        val piece = source.read(offset, (end - offset).toInt())
                        val answer = putPiece(session!!, offset, end - 1, total, piece)
                        answer.finished?.let { onProgress(total, total); return@run it }
                        offset = answer.received
                        onProgress(offset, total)
                    }
                    // everything was sent but no final answer arrived: ask once more
                    val status = uploadStatus(session!!, total)
                    status.finished?.let { return@run it }
                    offset = status.received
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            } catch (e: RemoteException.NotFound) {
                session = null // the session expired or was deleted: start over with a new one
                interrupted = false
                throw RemoteException.Network(e) // retryable, so the policy tries again
            }
        }
    }

    private class UploadProgress(val received: Long, val finished: RemoteFile?)

    private suspend fun openUploadSession(name: String, total: Long, existingId: String?): String {
        val metadata = if (existingId == null) json.encodeToString(CreateMetadata(name, listOf(APP_FOLDER))) else "{}"
        val answer = callRaw(
            if (existingId == null) HttpMethod.Post else HttpMethod.Patch,
            if (existingId == null) "$baseUrl/upload/drive/v3/files" else "$baseUrl/upload/drive/v3/files/$existingId",
            query = mapOf("uploadType" to "resumable", "fields" to FILE_FIELDS),
            headers = mapOf("X-Upload-Content-Type" to "application/octet-stream", "X-Upload-Content-Length" to total.toString()),
            body = metadata.encodeToByteArray(),
            contentType = ContentType.parse("application/json; charset=UTF-8")
        )
        return answer.headers[HttpHeaders.Location] ?: throw RemoteException.Protocol("Drive did not return an upload session")
    }

    private suspend fun putPiece(session: String, first: Long, last: Long, total: Long, piece: ByteArray): UploadProgress {
        val answer = callRaw(
            HttpMethod.Put, session,
            headers = mapOf(HttpHeaders.ContentRange to "bytes $first-$last/$total"),
            body = piece, contentType = ContentType.Application.OctetStream, allow308 = true
        )
        return progressOf(answer)
    }

    private suspend fun uploadStatus(session: String, total: Long): UploadProgress {
        val answer = callRaw(
            HttpMethod.Put, session,
            headers = mapOf(HttpHeaders.ContentRange to "bytes */$total"),
            body = ByteArray(0), contentType = ContentType.Application.OctetStream, allow308 = true
        )
        return progressOf(answer)
    }

    private fun progressOf(answer: RawAnswer): UploadProgress {
        if (answer.status == 308) {
            // "Range: bytes=0-N" means the first N+1 bytes arrived; no Range header means nothing arrived yet
            val received = answer.headers[HttpHeaders.Range]?.substringAfter('-', "")?.toLongOrNull()?.plus(1) ?: 0L
            return UploadProgress(received, null)
        }
        val file = try {
            json.decodeFromString<FileDto>(answer.bytes.decodeToString()).toFile()
        } catch (e: Exception) {
            throw RemoteException.Protocol("Unexpected answer from Drive: ${e.message}")
        }
        return UploadProgress(file.size ?: 0L, file)
    }

    override suspend fun accountId(): String =
        call(HttpMethod.Get, "/drive/v3/about", query = mapOf("fields" to "user(permissionId)"))
            .parse<AboutDto>().user.permissionId

    // ------------------------------------------------------------------ plumbing

    private class Answer(val bytes: ByteArray)

    // Headers keeps names case-insensitive: Google sends "location" and "range" in lowercase
    private class RawAnswer(val status: Int, val headers: io.ktor.http.Headers, val bytes: ByteArray)

    /**
     * One request to an absolute address (an upload session), with the same token handling as [call].
     * With [allow308], "resume incomplete" is an ordinary answer instead of an error. Retrying is left to the caller,
     * because after a failure an upload must first ask where it stands.
     */
    private suspend fun callRaw(
        method: HttpMethod,
        url: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: ContentType? = null,
        allow308: Boolean = false
    ): RawAnswer {
        var refreshed = false
        while (true) {
            val token = when (val result = tokens.accessToken()) {
                is TokenResult.Token -> result.value
                TokenResult.NeedsSignIn -> throw RemoteException.NeedsSignIn()
                is TokenResult.Failure -> throw RemoteException.Network(result.cause ?: IllegalStateException(result.message))
            }
            try {
                val response: HttpResponse = try {
                    http.request(url) {
                        this.method = method
                        url { query.forEach { (k, v) -> parameters.append(k, v) } }
                        header(HttpHeaders.Authorization, "Bearer $token")
                        headers.forEach { (k, v) -> header(k, v) }
                        if (contentType != null) contentType(contentType)
                        if (body != null) setBody(body)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw RemoteException.Network(e)
                }
                val bytes = try {
                    response.body<ByteArray>()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw RemoteException.Network(e)
                }
                val status = response.status.value
                if (response.status.isSuccess() || (allow308 && status == 308)) {
                    return RawAnswer(status, response.headers, bytes)
                }
                throw errorFor(response, bytes.decodeToString())
            } catch (e: RemoteException.Unauthorized) {
                tokens.invalidate(token)
                if (refreshed) throw e
                refreshed = true
            }
        }
    }

    private inline fun <reified T> Answer.parse(): T = try {
        json.decodeFromString<T>(bytes.decodeToString())
    } catch (e: Exception) {
        throw RemoteException.Protocol("Unexpected answer from Drive: ${e.message}")
    }

    /** One logical request: gets a token, retries temporary failures, refreshes a rejected token once. */
    private suspend fun call(
        method: HttpMethod,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: ContentType? = null,
        headers: Map<String, String> = emptyMap()
    ): Answer = retry.run {
        var refreshed = false
        while (true) {
            val token = when (val result = tokens.accessToken()) {
                is TokenResult.Token -> result.value
                TokenResult.NeedsSignIn -> throw RemoteException.NeedsSignIn()
                is TokenResult.Failure -> throw RemoteException.Network(result.cause ?: IllegalStateException(result.message))
            }
            try {
                return@run send(method, path, query, body, contentType, token, headers)
            } catch (e: RemoteException.Unauthorized) {
                tokens.invalidate(token)
                if (refreshed) throw e
                refreshed = true
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private suspend fun send(
        method: HttpMethod,
        path: String,
        query: Map<String, String>,
        body: ByteArray?,
        contentType: ContentType?,
        token: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Answer {
        val response: HttpResponse = try {
            http.request(baseUrl + path) {
                this.method = method
                url { query.forEach { (k, v) -> parameters.append(k, v) } }
                header(HttpHeaders.Authorization, "Bearer $token")
                extraHeaders.forEach { (k, v) -> header(k, v) }
                if (contentType != null) contentType(contentType)
                if (body != null) setBody(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RemoteException.Network(e)
        }
        val bytes = try {
            response.body<ByteArray>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RemoteException.Network(e)
        }
        if (response.status.isSuccess()) return Answer(bytes)
        throw errorFor(response, bytes.decodeToString())
    }

    private fun errorFor(response: HttpResponse, text: String): RemoteException {
        val status = response.status.value
        val retryAfterMs = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.let { it * 1000 }
        return when {
            status == 401 -> RemoteException.Unauthorized()
            status == 403 -> {
                val reason = errorReason(text)
                when (reason) {
                    "storageQuotaExceeded" -> RemoteException.StorageFull()
                    "rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded", "sharingRateLimitExceeded" ->
                        RemoteException.RateLimited(retryAfterMs)
                    else -> RemoteException.Forbidden(reason)
                }
            }
            status == 404 -> RemoteException.NotFound()
            status == 429 -> RemoteException.RateLimited(retryAfterMs)
            status in 500..599 -> RemoteException.Server(status)
            else -> RemoteException.Protocol("Unexpected status $status")
        }
    }

    /** Drive reports the reason as error.errors[0].reason (and sometimes in error.status). */
    private fun errorReason(text: String): String? = runCatching {
        val error = json.parseToJsonElement(text).jsonObject["error"]?.jsonObject ?: return null
        val errors = error["errors"] as? JsonArray
        errors?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.content
            ?: error["status"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun multipart(boundary: String, metadataJson: String, content: ByteArray): ByteArray {
        val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadataJson\r\n" +
            "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n"
        val tail = "\r\n--$boundary--"
        return head.encodeToByteArray() + content + tail.encodeToByteArray()
    }

    // ------------------------------------------------------------------ wire format

    @Serializable private class CreateMetadata(val name: String, val parents: List<String>)

    @Serializable
    private class FileDto(
        val id: String,
        val name: String = "",
        val modifiedTime: String? = null,
        val size: String? = null,
        val md5Checksum: String? = null
    ) {
        fun toFile() = RemoteFile(id, name, modifiedTime, size?.toLongOrNull(), md5Checksum)
    }

    @Serializable private class FileListDto(val nextPageToken: String? = null, val files: List<FileDto> = emptyList())
    @Serializable private class StartTokenDto(val startPageToken: String)
    @Serializable private class SizeDto(val size: String? = null)
    @Serializable private class AboutUserDto(val permissionId: String)
    @Serializable private class AboutDto(val user: AboutUserDto)

    @Serializable
    private class ChangeDto(val fileId: String = "", val removed: Boolean = false, val file: FileDto? = null)

    @Serializable
    private class ChangeListDto(
        val nextPageToken: String? = null,
        val newStartPageToken: String? = null,
        val changes: List<ChangeDto> = emptyList()
    )

    private companion object {
        const val APP_FOLDER = "appDataFolder"
        const val FILE_FIELDS = "id,name,modifiedTime,size,md5Checksum"
        const val BOUNDARY_SUFFIX = "f3a9c1d7b2e44a1c"

        /** Up to this size a file is sent in one request; above it, as a resumable upload. */
        const val SMALL_UPLOAD_LIMIT = 5L * 1024 * 1024

        /** Piece sizes. Drive requires upload pieces to be a multiple of 256 KiB (except the last). */
        const val UPLOAD_CHUNK = 2L * 1024 * 1024
        const val DOWNLOAD_CHUNK = 2L * 1024 * 1024
    }
}
