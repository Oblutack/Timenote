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

    // ------------------------------------------------------------------ plumbing

    private class Answer(val bytes: ByteArray)

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
        contentType: ContentType? = null
    ): Answer = retry.run {
        var refreshed = false
        while (true) {
            val token = when (val result = tokens.accessToken()) {
                is TokenResult.Token -> result.value
                TokenResult.NeedsSignIn -> throw RemoteException.NeedsSignIn()
                is TokenResult.Failure -> throw RemoteException.Network(result.cause ?: IllegalStateException(result.message))
            }
            try {
                return@run send(method, path, query, body, contentType, token)
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
        token: String
    ): Answer {
        val response: HttpResponse = try {
            http.request(baseUrl + path) {
                this.method = method
                url { query.forEach { (k, v) -> parameters.append(k, v) } }
                header(HttpHeaders.Authorization, "Bearer $token")
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
    }
}
