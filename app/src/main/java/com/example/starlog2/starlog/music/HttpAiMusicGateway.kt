package com.example.starlog2.starlog.music

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class HttpAiMusicGateway(
    baseUrl: String,
    private val sessionToken: String? = null,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
) : AiMusicGateway {
    private val baseUrl = baseUrl.trimEnd('/').also(::validateBaseUrl)

    override fun submit(request: AiMusicRequest): AiMusicGatewayResult<AiMusicJob> = executeJson(
        method = "POST",
        path = "/v1/music-jobs",
        body = AiMusicJson.encodeRequest(request),
    )

    override fun get(providerJobId: String): AiMusicGatewayResult<AiMusicJob> = executeJson(
        method = "GET",
        path = "/v1/music-jobs/${encodePath(providerJobId)}",
        body = null,
    )

    fun download(result: AiMusicResult, target: File): AiMusicGatewayResult<File> {
        val audioUrl = result.audioUrl
            ?: return AiMusicGatewayResult.Failure(
                AiMusicErrorCode.INVALID_RESPONSE,
                "Result has no downloadable audio URL",
                false,
            )
        if (result.mimeType !in SUPPORTED_AUDIO_MIME_TYPES) {
            return invalidResponse("Unsupported audio type: ${result.mimeType}")
        }
        val connection = try {
            open(resolveUrl(audioUrl), "GET")
        } catch (error: Exception) {
            return networkFailure(error)
        }
        val temporary = File(target.parentFile, "${target.name}.part")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                return AiMusicJson.decodeFailure(readBody(connection, false), code)
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_AUDIO_BYTES) {
                return AiMusicGatewayResult.Failure(
                    AiMusicErrorCode.INVALID_RESPONSE,
                    "Audio file exceeds $MAX_AUDIO_BYTES bytes",
                    false,
                )
            }
            val responseType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (responseType != null && responseType !in SUPPORTED_DOWNLOAD_MIME_TYPES) {
                return invalidResponse("Unexpected audio response type: $responseType")
            }
            target.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException("Download cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_AUDIO_BYTES) throw IOException("Audio file exceeds size limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (temporary.length() == 0L) return invalidResponse("Downloaded audio file is empty")
            if (result.mimeType == "audio/wav" && !hasWaveHeader(temporary)) {
                return invalidResponse("Downloaded WAV file has an invalid header")
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            AiMusicGatewayResult.Success(target)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            AiMusicGatewayResult.Failure(AiMusicErrorCode.NETWORK, "Audio download cancelled", true)
        } catch (error: Exception) {
            networkFailure(error)
        } finally {
            temporary.delete()
            connection.disconnect()
        }
    }

    private fun executeJson(method: String, path: String, body: String?): AiMusicGatewayResult<AiMusicJob> {
        val connection = try {
            open("$baseUrl$path", method)
        } catch (error: Exception) {
            return networkFailure(error)
        }
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = connection.responseCode
            val raw = readBody(connection, code in 200..299)
            if (code in 200..299) {
                runCatching { AiMusicJson.decodeJob(raw) }
                    .fold(
                        onSuccess = { AiMusicGatewayResult.Success(it) },
                        onFailure = { invalidResponse("Music gateway returned an invalid job response") },
                    )
            } else {
                AiMusicJson.decodeFailure(raw, code)
            }
        } catch (error: Exception) {
            networkFailure(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            useCaches = false
            setRequestProperty("Accept", "application/json, audio/wav, audio/mpeg")
            sessionToken?.takeIf { it.isNotBlank() }?.let { setRequestProperty("X-StarLog-Demo-Token", it) }
        }

    private fun readBody(connection: HttpURLConnection, success: Boolean): String {
        val stream = if (success) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
            val value = reader.readText()
            require(value.length <= MAX_JSON_CHARS) { "Music gateway response too large" }
            value
        }.orEmpty()
    }

    private fun resolveUrl(value: String): String = when {
        value.startsWith("https://") || value.startsWith("http://") -> value
        value.startsWith('/') -> "$baseUrl$value"
        else -> "$baseUrl/$value"
    }

    private fun networkFailure(error: Exception) = AiMusicGatewayResult.Failure(
        code = if (error is java.net.SocketTimeoutException) AiMusicErrorCode.TIMEOUT else AiMusicErrorCode.NETWORK,
        message = error.message ?: error.javaClass.simpleName,
        retryable = true,
    )

    private fun invalidResponse(message: String) = AiMusicGatewayResult.Failure(
        code = AiMusicErrorCode.INVALID_RESPONSE,
        message = message,
        retryable = false,
    )

    private fun hasWaveHeader(file: File): Boolean = file.inputStream().use { input ->
        val header = ByteArray(12)
        input.read(header) == header.size &&
            header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WAVE"
    }

    private fun validateBaseUrl(value: String) {
        val uri = URI(value)
        require(uri.scheme == "https" || uri.scheme == "http") { "Music backend must use HTTP(S)" }
        require(!uri.host.isNullOrBlank()) { "Music backend host is required" }
        if (uri.scheme == "http") {
            require(uri.host in LOCAL_HTTP_HOSTS) { "Cleartext music backend is allowed only for local demo hosts" }
        }
    }

    private fun encodePath(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        const val MAX_JSON_CHARS = 1_000_000
        const val MAX_AUDIO_BYTES = 50L * 1024L * 1024L
        val LOCAL_HTTP_HOSTS = setOf("10.0.2.2", "127.0.0.1", "localhost")
        val SUPPORTED_AUDIO_MIME_TYPES = setOf("audio/wav", "audio/mpeg")
        val SUPPORTED_DOWNLOAD_MIME_TYPES = SUPPORTED_AUDIO_MIME_TYPES + "application/octet-stream"
    }
}
