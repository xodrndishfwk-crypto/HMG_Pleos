package com.example.starlog2.starlog.music

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HttpAiMusicGatewayTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun crossOriginDownload_neverForwardsGatewayToken() {
        val audio = OneShotServer("audio/wav", waveBytes())
        val gateway = HttpAiMusicGateway("http://127.0.0.1:9", sessionToken = "gateway-secret")
        val target = temporary.newFile("song.wav")

        val result = gateway.download(result(audio.url, "audio/wav"), target)

        assertTrue(result is AiMusicGatewayResult.Success)
        assertTrue(audio.await())
        assertFalse(audio.headers.any { it.equals("X-StarLog-Demo-Token: gateway-secret", ignoreCase = true) })
    }

    @Test
    fun sameOriginDownload_sendsTokenAndValidatesWave() {
        val audio = OneShotServer("audio/wav", waveBytes())
        val gateway = HttpAiMusicGateway(audio.origin, sessionToken = "gateway-secret")

        val result = gateway.download(result("/audio.wav", "audio/wav"), temporary.newFile("same.wav"))

        assertTrue(result is AiMusicGatewayResult.Success)
        assertTrue(audio.await())
        assertTrue(audio.headers.any { it.equals("X-StarLog-Demo-Token: gateway-secret", ignoreCase = true) })
    }

    @Test
    fun missingMimeOrCorruptAudio_preservesExistingTarget() {
        val original = byteArrayOf(7, 8, 9)
        val target = temporary.newFile("existing.wav").apply { writeBytes(original) }
        val missingMime = OneShotServer(null, waveBytes())
        val first = HttpAiMusicGateway(missingMime.origin).download(result("/audio.wav", "audio/wav"), target)
        assertTrue(first is AiMusicGatewayResult.Failure)
        assertArrayEquals(original, target.readBytes())

        val corrupt = OneShotServer("audio/mpeg", byteArrayOf(1, 2, 3, 4))
        val second = HttpAiMusicGateway(corrupt.origin).download(result("/audio.mp3", "audio/mpeg"), target)
        assertTrue(second is AiMusicGatewayResult.Failure)
        assertArrayEquals(original, target.readBytes())
    }

    @Test
    fun gatewayOffline_returnsNetworkFailureAndPreservesExistingTarget() {
        val original = byteArrayOf(3, 2, 1)
        val target = temporary.newFile("offline.wav").apply { writeBytes(original) }
        val result = HttpAiMusicGateway(
            "http://127.0.0.1:1",
            connectTimeoutMs = 100,
            readTimeoutMs = 100,
        ).download(result("/audio.wav", "audio/wav"), target)

        assertTrue(result is AiMusicGatewayResult.Failure)
        assertTrue((result as AiMusicGatewayResult.Failure).code == AiMusicErrorCode.NETWORK)
        assertArrayEquals(original, target.readBytes())
    }

    private fun result(url: String, mime: String) = AiMusicResult(
        id = "result",
        audioUrl = url,
        localFilePath = null,
        mimeType = mime,
        durationMs = 1000,
        model = "mock-melody-synth-v1",
        createdAtMs = 1,
    )

    private fun waveBytes() = "RIFF".toByteArray() + byteArrayOf(4, 0, 0, 0) + "WAVEfmt ".toByteArray()

    private class OneShotServer(private val contentType: String?, private val body: ByteArray) : AutoCloseable {
        private val server = ServerSocket(0)
        private val done = CountDownLatch(1)
        val headers = mutableListOf<String>()
        val origin = "http://127.0.0.1:${server.localPort}"
        val url = "$origin/audio"

        init {
            Thread {
                server.use {
                    val socket = server.accept()
                    socket.use {
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            headers += line
                        }
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                        contentType?.let { output.write("Content-Type: $it\r\n".toByteArray()) }
                        output.write("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(body)
                        output.flush()
                    }
                }
                done.countDown()
            }.apply { isDaemon = true; start() }
        }

        fun await() = done.await(3, TimeUnit.SECONDS)
        override fun close() = server.close()
    }
}
